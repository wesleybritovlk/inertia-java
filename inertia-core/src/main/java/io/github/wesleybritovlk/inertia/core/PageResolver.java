package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
        if (prop instanceof Prop.Always always) {
            // bypasses only/except; subtrees bypass too (Laravel parentWasResolved semantics)
            include(path, always.value().get(), true, ctx, out);
        } else if (prop instanceof Prop.Plain plain) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, plain.value().get(), bypassChildren, ctx, out);
            }
        } else if (prop instanceof Prop.Lazy lazy) {
            if (ctx.partial && DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, lazy.value().get(), bypassChildren, ctx, out);
            }
        } else if (prop instanceof Prop.Deferred deferred) {
            if (ctx.partial) {
                // behaves like a plain prop on partial reloads; no deferred metadata here
                if (DotPaths.matches(path, ctx.only, ctx.except)) {
                    includeResolveDefer(path, deferred, ctx, out);
                }
            } else {
                out.deferredGroups
                        .computeIfAbsent(deferred.group() == null || deferred.group().isBlank() ? DEFAULT_GROUP : deferred.group(), key -> new ArrayList<>())
                        .add(path);
            }
        } else if (prop instanceof Prop.Merge merge) {
            if (!ctx.partial || DotPaths.matches(path, ctx.only, ctx.except)) {
                include(path, merge.value().get(), bypassChildren, ctx, out);
                collectMergeMetadata(path, merge, ctx, out);
            }
        }
    }

    /**
     * Includes one resolved value: whole on full visits; on partial reloads the value is
     * included as-is unless it is a JSON object needing per-child filtering.
     */
    private static void include(String path, Object value, boolean bypass, Ctx ctx, State out) {
        if (!ctx.partial || bypass || !(value instanceof Map<?, ?> map)) {
            out.values.put(path, value);
            return;
        }
        walkChildren(path, map, bypass, ctx, out);
    }

    private static void walkChildren(String path, Map<?, ?> map, boolean bypass, Ctx ctx, State out) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String childPath = path + '.' + String.valueOf(entry.getKey());
            if (!bypass && !DotPaths.matches(childPath, ctx.only, ctx.except)) {
                continue;
            }
            Object child = entry.getValue();
            if (!ctx.partial || bypass || !(child instanceof Map<?, ?> childMap)) {
                out.values.put(childPath, child);
            } else {
                walkChildren(childPath, childMap, bypass, ctx, out);
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
        List<String> distinctSharedKeys = sharedKeys.stream().distinct().toList();

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
        if (flashData instanceof Map<?, ?> flashMap && !flashMap.isEmpty()) {
            pageJson.put(PageKeys.FLASH, flashMap);
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
        if (!(stored instanceof Map<?, ?> bags) || bags.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> values = (Map<String, Object>) bags;
        boolean hasDefault = values.containsKey(DEFAULT_GROUP);
        if (hasDefault && errorBag != null && !errorBag.isBlank()) {
            return Map.of(errorBag, values.get(DEFAULT_GROUP));
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
        final String errorBag;
        final Consumer<Throwable> errorHandler;

        Ctx(InertiaRequest request, InertiaPage page, Consumer<Throwable> errorHandler) {
            this.request = request;
            this.visit = request.isInertiaVisit();
            String partialComponent = request.header(InertiaHeaders.PARTIAL_COMPONENT);
            this.partial = visit && partialComponent != null && partialComponent.equals(page.component());
            this.only = partial ? request.headerList(InertiaHeaders.PARTIAL_DATA) : List.of();
            this.except = partial ? request.headerList(InertiaHeaders.PARTIAL_EXCEPT) : List.of();
            this.reset = request.headerList(InertiaHeaders.RESET);
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
        final List<String> rescued = new ArrayList<>();
    }
}
