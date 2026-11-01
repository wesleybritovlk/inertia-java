package io.github.wesleybritovlk.inertia.core;

/**
 * Options for one scroll prop, mirroring the inertia-laravel {@code Inertia::scroll()}
 * arguments: simple numeric pagination ({@code page(current, hasMore)}), the query
 * parameter naming ({@code pageName}), the data wrapper key ({@code wrapper}), full-control
 * cursor metadata ({@code metadata(provider)}, Laravel {@code metadata:}) and deferral
 * ({@code deferred(...)}) — Laravel composes {@code scroll()} onto {@code Inertia::defer()}.
 */
public final class ScrollOptions {

    ScrollMetadata metadataProvider;
    String pageName = "page";
    String wrapper = "data";
    Integer explicitPage;
    boolean hasMore;
    String deferredGroup;

    /**
     * Simple numeric pagination: the current page plus whether a next one exists. The
     * client-driven {@link ScrollOptions#pageName pageName} query parameter still overrides
     * the current value (Laravel paginator parity); both only initialize first visits.
     */
    public ScrollOptions page(int current, boolean hasMore) {
        this.explicitPage = current;
        this.hasMore = hasMore;
        return this;
    }

    /** Query parameter carrying the cursor; defaults to {@code page}. */
    public ScrollOptions pageName(String name) {
        this.pageName = name;
        return this;
    }

    /**
     * Inner array key of the prop value (defaults to {@code data}); the merge label becomes
     * {@code <path>.<wrapper>}. A bare list value is wrapped automatically; a map value must
     * contain the key itself.
     */
    public ScrollOptions wrapper(String wrapper) {
        this.wrapper = wrapper;
        return this;
    }

    /**
     * Full-control cursor facts (Laravel {@code metadata:}) — wins over the derived values;
     * required for cursor pagination (opaque string cursors).
     */
    public ScrollOptions metadata(ScrollMetadata provider) {
        this.metadataProvider = provider;
        return this;
    }

    /** Defers the prop (group {@code "default"}): announced on full visits, resolved by the follow-up partial. */
    public ScrollOptions deferred() {
        return deferred("default");
    }

    /** Defers the prop in an explicit deferred group. */
    public ScrollOptions deferred(String group) {
        this.deferredGroup = group;
        return this;
    }
}
