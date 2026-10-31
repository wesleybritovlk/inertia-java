package io.github.wesleybritovlk.inertia.core;

import java.util.List;
import java.util.function.Supplier;

/**
 * Prop wrappers driving how each value participates in resolution and partial reloads.
 * The default ({@link #plain(Object)}) is evaluated and included on standard visits and
 * filtered by partial reload matching; {@link Lazy}, {@link Always}, {@link Deferred} and
 * {@link Merge} change one particular rule, mirroring {@code inertia-laravel}.
 */
public sealed interface Prop
        permits Prop.Plain, Prop.Lazy, Prop.Always, Prop.Deferred, Prop.Merge {

    /**
     * Standard prop: always included on full visits; on partial reloads only when its
     * dot-path matches the requested {@code only} list ( {@link Always} bypasses that filter).
     */
    record Plain(Supplier<?> value) implements Prop {}

    /**
     * Never sent on full visits (cost-free: the supplier is not evaluated); sent on partial
     * reloads only when its dot-path matches.
     */
    record Lazy(Supplier<?> value) implements Prop {}

    /** Bypasses partial filtering and is always included and evaluated. */
    record Always(Supplier<?> value) implements Prop {}

    /**
     * Excluded from this page's props on every visit; the client later re-requests each
     * deferred group as a partial reload. Tracked under {@link PageKeys#DEFERRED_PROPS}.
     * When {@code rescue} is set and the supplier fails, the prop is dropped and reported
     * under {@link PageKeys#RESCUED_PROPS} instead of aborting the response.
     */
    record Deferred(Supplier<?> value, String group, boolean rescue) implements Prop {}

    /**
     * Merged by the client on subsequent visits instead of replaced. The raw merge metadata
     * shapes are collected by the resolver under {@link PageKeys#MERGE_PROPS},
     * {@link PageKeys#PREPEND_PROPS}, {@link PageKeys#DEEP_MERGE_PROPS} and
     * {@link PageKeys#MATCH_PROPS_ON}.
     */
    record Merge(
            Supplier<?> value,
            boolean appendRoot,
            List<String> appendsAtPaths,
            boolean prependRoot,
            List<String> prependsAtPaths,
            boolean deep,
            List<String> matchOn) implements Prop {}
}
