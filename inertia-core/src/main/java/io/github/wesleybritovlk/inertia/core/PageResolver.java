package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Resolves one {@link InertiaPage} into an {@link InertiaResult} against the current
 * {@link InertiaRequest}: partial reload filtering ( dot-path matching like
 * {@code PropsResolver} in inertia-laravel), deferred/merge metadata, shared props merge
 * (page wins), flash + error bag adoption, and the {@code X-Inertia} JSON / root-template
 * decision.
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

        boolean visit = request.isInertiaVisit();
        String partialComponent = request.header(InertiaHeaders.PARTIAL_COMPONENT);
        boolean partial = visit && partialComponent != null && partialComponent.equals(page.component());
        List<String> only = partial ? request.headerList(InertiaHeaders.PARTIAL_DATA) : List.of();
        List<String> except = partial ? request.headerList(InertiaHeaders.PARTIAL_EXCEPT) : List.of();
        List<String> reset = request.headerList(InertiaHeaders.RESET);
        String errorBag = request.header(InertiaHeaders.ERROR_BAG);

        LinkedHashMap<String, Prop> props = new LinkedHashMap<>();
        InertiaFlashStore flash = request.flash();

        // shared props first; page props merged over them (page keys win, order preserved)
        LinkedHashMap<String, Object> sharedMerged = new LinkedHashMap<>(sharedValues);
        props.put("errors", new Prop.Always(() -> resolveErrors(flash, errorBag)));
        for (Map.Entry<String, Object> value : sharedMerged.entrySet()) {
            Object value0 = value.getValue();
            props.put(value.getKey(), new Prop.Plain(() -> value0));
        }
        for (Map.Entry<String, Prop> shared : sharedProps.entrySet()) {
            props.put(shared.getKey(), shared.getValue());
        }
        props.putAll(page.props());

        LinkedHashMap<String, Prop> flattened = DotPaths.flatten(DotPaths.nest(props));

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> deferredGroups = new LinkedHashMap<>();
        List<String> mergeProps = new ArrayList<>();
        List<String> prependProps = new ArrayList<>();
        List<String> deepMergeProps = new ArrayList<>();
        List<String> matchPropsOn = new ArrayList<>();
        List<String> rescued = new ArrayList<>();

        for (Map.Entry<String, Prop> entry : flattened.entrySet()) {
            String path = entry.getKey();
            Prop prop = entry.getValue();

            if (prop instanceof Prop.Always always) {
                values.put(path, always.value().get());
            } else if (prop instanceof Prop.Plain plain) {
                if (!partial || DotPaths.matches(path, only, except)) {
                    values.put(path, plain.value().get());
                }
            } else if (prop instanceof Prop.Lazy lazy) {
                if (partial && DotPaths.matches(path, only, except)) {
                    values.put(path, lazy.value().get());
                }
            } else if (prop instanceof Prop.Deferred deferred) {
                if (partial && DotPaths.matches(path, only, except)) {
                    if (deferred.rescue()) {
                        try {
                            values.put(path, deferred.value().get());
                        } catch (RuntimeException failure) {
                            errorHandler.accept(failure);
                            rescued.add(path);
                        }
                    } else {
                        values.put(path, deferred.value().get());
                    }
                } else {
                    deferredGroups
                            .computeIfAbsent(deferred.group() == null ? DEFAULT_GROUP : deferred.group(), key -> new ArrayList<>())
                            .add(path);
                }
            } else if (prop instanceof Prop.Merge merge) {
                if (!partial || DotPaths.matches(path, only, except)) {
                    values.put(path, merge.value().get());
                    if (!DotPaths.isReset(path, reset)) {
                        collectMergeMetadata(path, merge, mergeProps, prependProps, deepMergeProps, matchPropsOn);
                    }
                }
            }
        }

        LinkedHashMap<String, Object> pageJson = new LinkedHashMap<>();
        pageJson.put(PageKeys.COMPONENT, page.component());
        pageJson.put(PageKeys.PROPS, DotPaths.unflatten(values));

        Object flashData = flash.pull(FlashKeys.FLASH);
        if (flashData instanceof Map<?, ?> flashMap && !flashMap.isEmpty()) {
            pageJson.put(PageKeys.FLASH, flashMap);
        }
        pageJson.put(PageKeys.URL, request.url());
        pageJson.put(PageKeys.VERSION, versionProvider.version());

        if (!deferredGroups.isEmpty()) {
            pageJson.put(PageKeys.DEFERRED_PROPS, deferredGroups);
        }
        if (!mergeProps.isEmpty()) {
            pageJson.put(PageKeys.MERGE_PROPS, mergeProps);
        }
        if (!prependProps.isEmpty()) {
            pageJson.put(PageKeys.PREPEND_PROPS, prependProps);
        }
        if (!deepMergeProps.isEmpty()) {
            pageJson.put(PageKeys.DEEP_MERGE_PROPS, deepMergeProps);
        }
        if (!matchPropsOn.isEmpty()) {
            pageJson.put(PageKeys.MATCH_PROPS_ON, matchPropsOn);
        }
        List<String> sharedKeys = topKeysOf(sharedMerged, sharedProps);
        if (!sharedKeys.isEmpty()) {
            pageJson.put(PageKeys.SHARED_PROPS, sharedKeys);
        }
        if (!rescued.isEmpty()) {
            pageJson.put(PageKeys.RESCUED_PROPS, rescued);
        }
        if (flash.pull(FlashKeys.CLEAR_HISTORY) == Boolean.TRUE) {
            pageJson.put(PageKeys.CLEAR_HISTORY, Boolean.TRUE);
        }
        if (flash.pull(FlashKeys.PRESERVE_FRAGMENT) == Boolean.TRUE) {
            pageJson.put(PageKeys.PRESERVE_FRAGMENT, Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(page.isEncryptHistory())) {
            pageJson.put(PageKeys.ENCRYPT_HISTORY, Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(page.isPreserveBigIntegers())) {
            pageJson.put(PageKeys.PRESERVE_BIG_INTEGERS, Boolean.TRUE);
        }

        String json = serializer.serialize(pageJson);
        return visit ? InertiaResult.pageJson(json) : InertiaResult.pageHtml(json);
    }

    /** Shared prop keys listed as metadata, truncated at their first dot (Laravel parity). */
    private static List<String> topKeysOf(Map<String, Object> sharedValues, LinkedHashMap<String, Prop> sharedProps) {
        List<String> keys = new ArrayList<>();
        for (String key : sharedValues.keySet()) {
            keys.add(topKey(key));
        }
        for (String key : sharedProps.keySet()) {
            keys.add(topKey(key));
        }
        return keys;
    }

    private static String topKey(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    /**
     * Shared {@code errors} prop: selected bag under {@code X-Inertia-Error-Bag}, otherwise
     * the {@code "default"} bag; empty object when nothing was stored.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveErrors(InertiaFlashStore flash, String errorBag) {
        Object stored = flash.pull(FlashKeys.ERRORS);
        if (!(stored instanceof Map<?, ?> bags) || bags.isEmpty()) {
            return Map.of();
        }
        String bagName = errorBag != null && !errorBag.isBlank() ? errorBag : DEFAULT_GROUP;
        Object bag = ((Map<String, Object>) bags).get(bagName);
        return bag instanceof Map<?, ?> entries ? (Map<String, Object>) entries : Map.of();
    }

    private static void collectMergeMetadata(
            String path,
            Prop.Merge merge,
            List<String> mergeProps,
            List<String> prependProps,
            List<String> deepMergeProps,
            List<String> matchPropsOn) {

        if (merge.deep()) {
            deepMergeProps.add(path);
        } else {
            if (merge.prependRoot()) {
                prependProps.add(path);
            }
            for (String subPath : merge.prependsAtPaths()) {
                prependProps.add(path + '.' + subPath);
            }
            if (merge.appendRoot() && !merge.prependRoot() && merge.prependsAtPaths().isEmpty()) {
                mergeProps.add(path);
            }
            for (String subPath : merge.appendsAtPaths()) {
                mergeProps.add(path + '.' + subPath);
            }
        }
        for (String matchKey : merge.matchOn()) {
            matchPropsOn.add(path + '.' + matchKey);
        }
    }
}
