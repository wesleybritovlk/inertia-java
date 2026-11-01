package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Resolves one {@link InertiaPage} into an {@link InertiaResult} against the current
 * {@link InertiaRequest}. Semantics mirror {@code inertia-laravel}'s {@code PropsResolver} +
 * {@code Response}:
 *
 * <ul>
 *   <li>Partial reload filtering on dot-paths (exact, descendant or lead-to), {@code except}
 *       wins over {@code only}, and container prop values are walked so requested
 *       sub-paths can be returned alone;</li>
 *   <li>shared props merged first, page props override by key;</li>
 *   <li>{@code errors} shared prop is always included ({@code Always} semantics) and shaped
 *       by {@code X-Inertia-Error-Bag};</li>
 *   <li>deferred props excluded on full visits ({@code deferredProps} metadata) and resolved
 *       on partial reloads whose {@code only} matches;</li>
 *   <li>merge metadata ({@code mergeProps}/{@code prependProps}/{@code deepMergeProps}/
 *       {@code matchPropsOn}) collected for included props only, skipped when the path is in
 *       {@code X-Inertia-Reset} (exact match);</li>
 *   <li>once props skipped on full visits when the client remembered the once key
 *       ({@code X-Inertia-Except-Once-Props}) unless {@code fresh} forces a refresh — the
 *       {@code onceProps} metadata is emitted either way; partial reloads ignore that header
 *       and always resolve requested once props;</li>
 *   <li>scroll props (infinite scroll) resolved + labeled for merging under
 *       {@code <path>.<wrapper>} and accompanied by the {@code scrollProps} cursor (with
 *       {@code reset: true} when the path is in {@code X-Inertia-Reset}, which keeps the
 *       merge label so the client replaces instead of appending); deferred scroll props
 *       announce only, carrying the cursor on the resolving partial;</li>
 *   <li>flash + error data and {@code clearHistory}/{@code preserveFragment} flags pulled
 *       from the flash store (survive redirects, consumed by the next rendered page);</li>
 *   <li>{@code X-Inertia} visits answer JSON; other visits render the root template.</li>
 * </ul>
 */
final class PageResolver {

    private static final String DEFAULT_GROUP = "default";

    private PageResolver() {
    }

    static InertiaResult resolve(
            VersionProvider versionProvider,
            PageSerializer serializer,
            InertiaRequest request,
            LinkedHashMap<String, Prop> sharedProps,
            LinkedHashMap<String, Object> sharedValues,
            InertiaPage page,
            Consumer<Throwable> errorHandler) {

        Ctx ctx = new Ctx(request, page, errorHandler);
        State out = new State();

        // one ordered map: shared props first (errors is the framework-owned Always prop),
        // user shared entries next, page props last (page keys win, order positions kept)
        InertiaFlashStore flash = request.flash();
        LinkedHashMap<String, Prop> props = new LinkedHashMap<>();
        props.put("errors", new Prop.Always(() -> resolveErrors(flash, ctx.errorBag)));
        for (Map.Entry<String, Object> shared : sharedValues.entrySet()) {
            Object value = shared.getValue();
            props.put(shared.getKey(), new Prop.Plain(() -> value));
        }
        props.putAll(sharedProps);
        props.putAll(page.props());

        // resolve every prop (dot keys expanded to paths first)
        for (Map.Entry<String, Prop> entry : DotPaths.flatten(DotPaths.nest(props)).entrySet()) {
            resolveProp(entry.getKey(), entry.getValue(), ctx, out, false);
        }

        return buildResult(versionProvider, serializer, request, flash, page, sharedValues.keySet(), sharedProps.keySet(), ctx, out);
    }

    private static void resolveProp(String path, Prop prop, Ctx ctx, State out, boolean bypassChildren) {
        if (prop instanceof Prop.Always) {
            // bypasses only/except; subtrees bypass too (Laravel parentWasResolved semantics)
            include(path, ((Prop.Always) prop).value().get(), true, ctx, out);
        } else if (prop instanceof Prop.Plain) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Plain) prop).value().get(), bypassChildren, ctx, out);
            }
        } else if (prop instanceof Prop.Lazy) {
            if (ctx.partial && DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Lazy) prop).value().get(), bypassChildren, ctx, out);
            }
        } else if (prop instanceof Prop.Deferred) {
            if (ctx.partial) {
                // behaves like a plain prop on partial reloads; no deferred metadata here
                if (DotPaths.matches(path, ctx.only, ctx.except)) {
                    includeResolveDefer(path, (Prop.Deferred) prop, ctx, out);
                }
            } else {
                announceDeferred(path, ((Prop.Deferred) prop).group(), out);
            }
        } else if (prop instanceof Prop.Merge) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Merge) prop).value().get(), bypassChildren, ctx, out);
                collectMergeMetadata(path, (Prop.Merge) prop, ctx, out);
            }
        } else if (prop instanceof Prop.Once) {
            resolveOnce(path, (Prop.Once) prop, ctx, out, bypassChildren);
        } else if (prop instanceof Prop.Scroll) {
            resolveScroll(path, (Prop.Scroll) prop, ctx, out);
        }
    }

    /**
     * Includes one resolved value: whole on full visits; on partial reloads the value is
     * included as-is unless it is a JSON object needing per-child filtering.
     */
    private static void include(String path, Object value, boolean bypass, Ctx ctx, State out) {
        if (!ctx.partial || bypass || !(value instanceof Map<?, ?>)) {
            out.values.put(path, value);
            return;
        }
        walkChildren(path, (Map<?, ?>) value, bypass, ctx, out);
    }

    private static void walkChildren(String path, Map<?, ?> map, boolean bypass, Ctx ctx, State out) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String childPath = path + '.' + String.valueOf(entry.getKey());
            if (!bypass && !DotPaths.matches(childPath, ctx.only, ctx.except)) {
                continue;
            }
            Object child = entry.getValue();
            if (!ctx.partial || bypass || !(child instanceof Map<?, ?>)) {
                out.values.put(childPath, child);
            } else {
                walkChildren(childPath, (Map<?, ?>) child, bypass, ctx, out);
            }
        }
    }

    private static void includeResolveDefer(String path, Prop.Deferred deferred, Ctx ctx, State out) {
        if (!deferred.rescue()) {
            include(path, deferred.value().get(), false, ctx, out);
            return;
        }
        try {
            include(path, deferred.value().get(), false, ctx, out);
        } catch (RuntimeException failure) {
            ctx.errorHandler.accept(failure);
            out.rescued.add(path);
        }
    }

    private static void announceDeferred(String path, String group, State out) {
        out.deferredGroups
                .computeIfAbsent(group == null || group.trim().isEmpty() ? DEFAULT_GROUP : group, key -> new ArrayList<>())
                .add(path);
    }

    // ---------------------------------------------------------------- once props

    /**
     * Once resolution: the {@code onceProps} entry is emitted on every visit (full, partial,
     * resolved, skipped — the client re-hydrates remembered values from it), while the wrapped
     * value resolves normally unless a full visit's {@code X-Inertia-Except-Once-Props} lists
     * the key and {@code fresh} does not force a refresh. Skipped entries carry no value and
     * no merge label (the client keeps its copy); a still-deferred inner prop keeps announcing
     * its group so the client can resolve it explicitly later.
     */
    private static void resolveOnce(String path, Prop.Once once, Ctx ctx, State out, boolean bypassChildren) {
        boolean skipped = !ctx.partial && !once.fresh() && ctx.exceptOnce.contains(once.key());
        out.onceProps.put(once.key(), onceEntry(path, once));
        if (skipped) {
            if (!ctx.partial && once.inner() instanceof Prop.Deferred) {
                announceDeferred(path, ((Prop.Deferred) once.inner()).group(), out);
            }
            return;
        }
        resolveOnceInner(path, once.inner(), ctx, out, bypassChildren);
    }

    private static Map<String, Object> onceEntry(String path, Prop.Once once) {
        Long expiresAt = once.expiresAtFixed();
        if (expiresAt == null && once.expiresAfter() != null) {
            expiresAt = System.currentTimeMillis() + once.expiresAfter().toMillis();
        }
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("prop", path);
        entry.put("expiresAt", expiresAt);
        return entry;
    }

    /** Prop-kind dispatch of the wrapped once value (mirrors the top-level branches). */
    private static void resolveOnceInner(String path, Prop inner, Ctx ctx, State out, boolean bypassChildren) {
        if (inner instanceof Prop.Always) {
            include(path, ((Prop.Always) inner).value().get(), true, ctx, out);
        } else if (inner instanceof Prop.Plain) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Plain) inner).value().get(), bypassChildren, ctx, out);
            }
        } else if (inner instanceof Prop.Lazy) {
            if (ctx.partial && DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Lazy) inner).value().get(), bypassChildren, ctx, out);
            }
        } else if (inner instanceof Prop.Deferred) {
            if (ctx.partial) {
                if (DotPaths.matches(path, ctx.only, ctx.except)) {
                    includeResolveDefer(path, (Prop.Deferred) inner, ctx, out);
                }
            } else {
                announceDeferred(path, ((Prop.Deferred) inner).group(), out);
            }
        } else if (inner instanceof Prop.Merge) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, ((Prop.Merge) inner).value().get(), bypassChildren, ctx, out);
                collectMergeMetadata(path, (Prop.Merge) inner, ctx, out);
            }
        }
    }

    // ---------------------------------------------------------------- scroll props

    /**
     * Scroll resolution: exactly like a merge prop, plus the cursor. The merge label at
     * {@code <path>.<wrapper>} is re-emitted on EVERY response that carries the value —
     * including {@code X-Inertia-Reset} requests, whose {@code reset: true} cursor tells the
     * client to replace its accumulated data instead of appending. Deferral defers both the
     * value and its cursor to the resolving partial reload.
     */
    private static void resolveScroll(String path, Prop.Scroll scroll, Ctx ctx, State out) {
        if (scroll.deferredGroup() != null && !ctx.partial) {
            announceDeferred(path, scroll.deferredGroup(), out);
            return;
        }
        if (ctx.partial && !DotPaths.matches(path, ctx.only, ctx.except)) {
            return;
        }
        include(path, scrollValue(scroll), false, ctx, out);
        collectScrollMetadata(path, scroll, ctx, out);
    }

    /** Bare lists are wrapped as {@code {<wrapper>: [...]}}; maps pass through untouched. */
    private static Object scrollValue(Prop.Scroll scroll) {
        Object value = scroll.value().get();
        if (value instanceof List<?>) {
            LinkedHashMap<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put(scroll.wrapper(), (List<?>) value);
            return wrapped;
        }
        return value;
    }

    private static void collectScrollMetadata(String path, Prop.Scroll scroll, Ctx ctx, State out) {
        ScrollMetadata metadata = scroll.metadata() == null
                ? derivedScrollMetadata(scroll, ctx)
                : scroll.metadata();
        LinkedHashMap<String, Object> cursor = new LinkedHashMap<>();
        cursor.put("pageName", metadata.pageName());
        cursor.put("previousPage", metadata.previousPage());
        cursor.put("nextPage", metadata.nextPage());
        cursor.put("currentPage", metadata.currentPage());
        cursor.put("reset", DotPaths.isReset(path, ctx.reset));
        out.scrollProps.put(path, cursor);
        out.mergeProps.add(path + '.' + scroll.wrapper());
    }

    /**
     * Request-derived cursor defaults, Laravel paginator parity: the current page comes from
     * the {@code pageName} query parameter (integer when numeric — the raw value otherwise,
     * e.g. opaque cursor strings), falling back to the page fixed by the controller and then
     * to page 1. Numeric cursors derive previous/next; {@code hasMore} must be declared by
     * the controller, since the core cannot know a page exists.
     */
    private static ScrollMetadata derivedScrollMetadata(Prop.Scroll scroll, Ctx ctx) {
        String pageName = scroll.pageName();
        String raw = queryParam(ctx, pageName);
        Object current;
        if (raw != null) {
            current = parsePage(raw);
        } else {
            current = scroll.explicitPage() == null ? Integer.valueOf(1) : scroll.explicitPage();
        }
        Object previous = null;
        Object next = null;
        if (current instanceof Integer) {
            Integer currentInt = (Integer) current;
            if (currentInt > 1) {
                previous = currentInt - 1;
            }
            if (scroll.hasMore()) {
                next = currentInt + 1;
            }
        }
        return new FixedScrollMetadata(pageName, previous, next, current);
    }

    private static Object parsePage(String raw) {
        try {
            return Integer.valueOf(Integer.parseInt(raw));
        } catch (NumberFormatException notNumeric) {
            return raw;
        }
    }

    private static String queryParam(Ctx ctx, String name) {
        String query = ctx.request.query();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            if (name.equals(URL_DECODER.apply(key))) {
                String value = split < 0 ? "" : pair.substring(split + 1);
                return URL_DECODER.apply(value);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- output

    private static InertiaResult buildResult(
            VersionProvider versionProvider,
            PageSerializer serializer,
            InertiaRequest request,
            InertiaFlashStore flash,
            InertiaPage page,
            java.util.Set<String> sharedValueKeys,
            java.util.Set<String> sharedPropKeys,
            Ctx ctx,
            State out) {

        // shared prop keys as metadata, truncated at the first dot (Laravel parity, deduped)
        List<String> sharedKeys = new ArrayList<>();
        for (String key : sharedValueKeys) {
            sharedKeys.add(topKey(key));
        }
        for (String key : sharedPropKeys) {
            sharedKeys.add(topKey(key));
        }
        List<String> distinctSharedKeys = sharedKeys.stream().distinct().collect(Collectors.toList());

        LinkedHashMap<String, Object> pageJson = new LinkedHashMap<>();
        pageJson.put(PageKeys.COMPONENT, page.component());
        pageJson.put(PageKeys.PROPS, DotPaths.unflatten(out.values));
        pageJson.put(PageKeys.URL, request.url());
        pageJson.put(PageKeys.VERSION, versionProvider == null ? null : versionProvider.version());

        // metadata, in inertia-laravel order (only non-empty lists)
        if (!distinctSharedKeys.isEmpty()) {
            pageJson.put(PageKeys.SHARED_PROPS, distinctSharedKeys);
        }
        if (!out.mergeProps.isEmpty()) {
            pageJson.put(PageKeys.MERGE_PROPS, out.mergeProps);
        }
        if (!out.prependProps.isEmpty()) {
            pageJson.put(PageKeys.PREPEND_PROPS, out.prependProps);
        }
        if (!out.deepMergeProps.isEmpty()) {
            pageJson.put(PageKeys.DEEP_MERGE_PROPS, out.deepMergeProps);
        }
        if (!out.matchPropsOn.isEmpty()) {
            pageJson.put(PageKeys.MATCH_PROPS_ON, out.matchPropsOn);
        }
        if (!out.scrollProps.isEmpty()) {
            pageJson.put(PageKeys.SCROLL_PROPS, out.scrollProps);
        }
        if (!out.onceProps.isEmpty()) {
            pageJson.put(PageKeys.ONCE_PROPS, out.onceProps);
        }
        if (!out.deferredGroups.isEmpty()) {
            pageJson.put(PageKeys.DEFERRED_PROPS, out.deferredGroups);
        }
        if (!out.rescued.isEmpty()) {
            pageJson.put(PageKeys.RESCUED_PROPS, out.rescued);
        }

        // flags + flash, derived from the page and the flash store (nothing emitted when false)
        if (Boolean.TRUE.equals(page.isPreserveBigIntegers())) {
            pageJson.put(PageKeys.PRESERVE_BIG_INTEGERS, Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(flash.pull(FlashKeys.CLEAR_HISTORY))) {
            pageJson.put(PageKeys.CLEAR_HISTORY, Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(page.isEncryptHistory())) {
            pageJson.put(PageKeys.ENCRYPT_HISTORY, Boolean.TRUE);
        }
        Object flashData = flash.pull(FlashKeys.FLASH);
        if (flashData instanceof Map<?, ?> && !((Map<?, ?>) flashData).isEmpty()) {
            pageJson.put(PageKeys.FLASH, (Map<?, ?>) flashData);
        }
        if (Boolean.TRUE.equals(flash.pull(FlashKeys.PRESERVE_FRAGMENT))) {
            pageJson.put(PageKeys.PRESERVE_FRAGMENT, Boolean.TRUE);
        }

        String json = serializer.serialize(pageJson);
        return ctx.visit ? InertiaResult.pageJson(json) : InertiaResult.pageHtml(json);
    }

    private static String topKey(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    /**
     * Shared {@code errors} prop shape (inertia-laravel parity): the selected bag under
     * {@code X-Inertia-Error-Bag} wrapped as {@code {bag: {...}}}; without a bag header the
     * {@code "default"} bag is sent unwrapped; when no default bag exists all bags are sent.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveErrors(InertiaFlashStore flash, String errorBag) {
        Object stored = flash.pull(FlashKeys.ERRORS);
        if (!(stored instanceof Map<?, ?>)) {
            return Collections.emptyMap();
        }
        Map<String, Object> values = (Map<String, Object>) stored;
        boolean hasDefault = values.containsKey(DEFAULT_GROUP);
        if (hasDefault && errorBag != null && !errorBag.trim().isEmpty()) {
            return Collections.singletonMap(errorBag, values.get(DEFAULT_GROUP));
        }
        if (hasDefault) {
            return (Map<String, Object>) values.get(DEFAULT_GROUP);
        }
        return values;
    }

    // ---------------------------------------------------------------- merge metadata

    private static void collectMergeMetadata(String path, Prop.Merge merge, Ctx ctx, State out) {
        if (DotPaths.isReset(path, ctx.reset)) {
            return; // X-Inertia-Reset drops all merge metadata for the path
        }
        boolean partialMetadata = !ctx.partial
                || (DotPaths.matchesOnly(path, ctx.only) && !DotPaths.matchesAny(path, ctx.except));
        if (!partialMetadata) {
            return;
        }
        boolean atRoot = merge.appendsAtPaths().isEmpty() && merge.prependsAtPaths().isEmpty();
        if (merge.deep()) {
            out.deepMergeProps.add(path); // deep takes priority over append/prepend
        } else if (atRoot && merge.append()) {
            out.mergeProps.add(path);
        } else if (atRoot) {
            out.prependProps.add(path);
        } else {
            for (String subPath : merge.appendsAtPaths()) {
                out.mergeProps.add(path + '.' + subPath);
            }
            for (String subPath : merge.prependsAtPaths()) {
                out.prependProps.add(path + '.' + subPath);
            }
        }
        for (String matchKey : merge.matchOn()) {
            out.matchPropsOn.add(path + '.' + matchKey);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static final class Ctx {
        final InertiaRequest request;
        final boolean visit;
        final boolean partial;
        final List<String> only;
        final List<String> except;
        final List<String> reset;
        final List<String> exceptOnce;
        final String errorBag;
        final Consumer<Throwable> errorHandler;

        Ctx(InertiaRequest request, InertiaPage page, Consumer<Throwable> errorHandler) {
            this.request = request;
            this.visit = request.isInertiaVisit();
            String partialComponent = request.header(InertiaHeaders.PARTIAL_COMPONENT);
            this.partial = visit && partialComponent != null && partialComponent.equals(page.component());
            this.only = partial ? request.headerList(InertiaHeaders.PARTIAL_DATA) : Collections.<String>emptyList();
            this.except = partial ? request.headerList(InertiaHeaders.PARTIAL_EXCEPT) : Collections.<String>emptyList();
            this.reset = request.headerList(InertiaHeaders.RESET);
            this.exceptOnce = request.headerList(InertiaHeaders.EXCEPT_ONCE_PROPS);
            this.errorBag = request.header(InertiaHeaders.ERROR_BAG);
            this.errorHandler = errorHandler == null ? failure -> {
            } : errorHandler;
        }
    }

    private static final class State {
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        final LinkedHashMap<String, List<String>> deferredGroups = new LinkedHashMap<>();
        final List<String> mergeProps = new ArrayList<>();
        final List<String> prependProps = new ArrayList<>();
        final List<String> deepMergeProps = new ArrayList<>();
        final List<String> matchPropsOn = new ArrayList<>();
        final LinkedHashMap<String, Map<String, Object>> scrollProps = new LinkedHashMap<>();
        final LinkedHashMap<String, Map<String, Object>> onceProps = new LinkedHashMap<>();
        final List<String> rescued = new ArrayList<>();
    }

    /** A controller/user supplied cursor set, emitted verbatim (no request derivation). */
    private static final class FixedScrollMetadata implements ScrollMetadata {

        private final String pageName;
        private final Object previousPage;
        private final Object nextPage;
        private final Object currentPage;

        FixedScrollMetadata(String pageName, Object previousPage, Object nextPage, Object currentPage) {
            this.pageName = pageName;
            this.previousPage = previousPage;
            this.nextPage = nextPage;
            this.currentPage = currentPage;
        }

        @Override
        public String pageName() {
            return pageName;
        }

        @Override
        public Object previousPage() {
            return previousPage;
        }

        @Override
        public Object nextPage() {
            return nextPage;
        }

        @Override
        public Object currentPage() {
            return currentPage;
        }
    }

    private static final java.util.function.UnaryOperator<String> URL_DECODER = raw -> {
        try {
            return java.net.URLDecoder.decode(raw, "UTF-8");
        } catch (java.io.UnsupportedEncodingException never) {
            throw new java.lang.AssertionError(never); // UTF-8 is guaranteed on every Java platform
        }
    };
}
