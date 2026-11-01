package io.github.wesleybritovlk.inertia.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fluent page builder produced by {@link Inertia#render(String...)}: the component name plus
 * ordered props. Resolved into an {@link InertiaResult} lazily by the adapter (or
 * {@link Inertia#result(InertiaPage) result(...)}) against the current request.
 */
public final class InertiaPage {

    private final String component;
    private final LinkedHashMap<String, Prop> props = new LinkedHashMap<>();
    private Boolean encryptHistory;
    private Boolean preserveBigIntegers;
    private final LinkedHashMap<String, Object> viewData = new LinkedHashMap<>();

    InertiaPage(String component) {
        this.component = component;
    }

    public String component() {
        return component;
    }

    LinkedHashMap<String, Prop> props() {
        return props;
    }

    Boolean isEncryptHistory() {
        return encryptHistory;
    }

    Boolean isPreserveBigIntegers() {
        return preserveBigIntegers;
    }

    LinkedHashMap<String, Object> viewData() {
        return viewData;
    }

    /** Template-scoped variables (never in the page JSON). Unmodifiable snapshot. */
    public Map<String, Object> view() {
        return java.util.Collections.unmodifiableMap(viewData);
    }

    /**
     * Standard prop: value may be any JSON-serializable value or a supplier.
     * On partial reloads the value is only evaluated/returned when its dot-path matches.
     */
    public InertiaPage with(String key, Object value) {
        return put(key, new Prop.Plain(() -> value));
    }

    /** Standard prop with lazy evaluation (evaluated only when the response actually needs it). */
    public InertiaPage with(String key, Supplier<?> supplier) {
        return put(key, new Prop.Plain(supplier));
    }

    /** Optional prop: never sent on full visits; sent only when explicitly requested by a partial reload. */
    public InertiaPage withLazy(String key, Supplier<?> supplier) {
        return put(key, new Prop.Lazy(supplier));
    }

    /** Always included (and evaluated), even on partial reloads that did not request it. */
    public InertiaPage withAlways(String key, Object value) {
        return put(key, new Prop.Always(() -> value));
    }

    /** Always included (and evaluated), even on partial reloads that did not request it. */
    public InertiaPage withAlways(String key, Supplier<?> supplier) {
        return put(key, new Prop.Always(supplier));
    }

    /** Deferred prop in the {@code "default"} group: excluded here, resolved later by the client. */
    public InertiaPage withDeferred(String key, Supplier<?> supplier) {
        return withDeferred(key, "default", supplier, false);
    }

    /** Deferred prop in an explicit group. */
    public InertiaPage withDeferred(String key, String group, Supplier<?> supplier) {
        return withDeferred(key, group, supplier, false);
    }

    /**
     * Deferred prop guarded with rescue: when the supplier fails the prop is dropped and
     * reported under {@link PageKeys#RESCUED_PROPS} instead of failing the whole page.
     */
    public InertiaPage withDeferredRescued(String key, Supplier<?> supplier) {
        return withDeferred(key, "default", supplier, true);
    }

    public InertiaPage withDeferredRescued(String key, String group, Supplier<?> supplier) {
        return withDeferred(key, group, supplier, true);
    }

    private InertiaPage withDeferred(String key, String group, Supplier<?> supplier, boolean rescue) {
        return put(key, new Prop.Deferred(supplier, group == null || group.isBlank() ? "default" : group, rescue));
    }

    /**
     * Client-merged prop (root append by default). Use the options consumer for
     * {@code prepend()}, {@code deep()}, {@code matchOn(...)}, {@code appendAt(...)}.
     */
    public InertiaPage withMerged(String key, Supplier<?> supplier, Consumer<MergeOptions> options) {
        MergeOptions o = new MergeOptions();
        if (options != null) {
            options.accept(o);
        }
        return put(key, new Prop.Merge(
                supplier,
                o.append,
                o.appendsAtPaths,
                o.prependsAtPaths,
                o.deep,
                o.matchOn));
    }

    /** Client-merged prop (root append). */
    public InertiaPage withMerged(String key, Supplier<?> supplier) {
        return withMerged(key, supplier, null);
    }

    /**
     * Once prop (inertia-laravel {@code Inertia::once()}): resolved a single time per client
     * and remembered by it; later full visits carrying the remembered key
     * ({@code X-Inertia-Except-Once-Props}) skip resolution and leave the value out of the
     * response (the {@code onceProps} metadata stays). Customize with the options consumer:
     * {@code as(key)}, {@code until(...)}, {@code fresh()}, and wrapping other prop kinds
     * ({@code lazy()}, {@code deferred(...)}, {@code merged()}).
     */
    public InertiaPage withOnce(String key, Supplier<?> supplier) {
        return withOnce(key, supplier, null);
    }

    /** Once prop with customization, see {@link #withOnce(String, Supplier)}. */
    public InertiaPage withOnce(String key, Supplier<?> supplier, Consumer<OnceOptions> options) {
        OnceOptions o = new OnceOptions();
        if (options != null) {
            options.accept(o);
        }
        Prop inner = o.wrappedProp(supplier);
        String onceKey = o.onceKey == null ? key : o.onceKey;
        Long fixedExpiry = o.expiresAt == null ? null : o.expiresAt.toEpochMilli();
        return put(key, new Prop.Once(inner, onceKey, fixedExpiry, o.expiresAfter, o.fresh));
    }

    /**
     * Scroll prop (inertia-laravel {@code Inertia::scroll()}): infinite scroll — the value is
     * labeled for client-side merging ({@code mergeProps: [<path>.<wrapper>]}) and the page
     * object carries the paginator cursor ({@code scrollProps}). Bare list values are wrapped
     * as {@code {<wrapper>: [...]}}. Configure numeric pagination with
     * {@code page(current, hasMore)}, cursor pagination with {@code metadata(provider)},
     * the parameter/wrapper names, and deferral with {@code deferred(...)}.
     */
    public InertiaPage withScroll(String key, Supplier<?> supplier) {
        return withScroll(key, supplier, null);
    }

    /** Scroll prop with customization, see {@link #withScroll(String, Supplier)}. */
    public InertiaPage withScroll(String key, Supplier<?> supplier, Consumer<ScrollOptions> options) {
        ScrollOptions o = new ScrollOptions();
        if (options != null) {
            options.accept(o);
        }
        return put(key, new Prop.Scroll(
                supplier,
                o.metadataProvider,
                o.pageName,
                o.wrapper,
                o.explicitPage,
                o.hasMore,
                o.deferredGroup));
    }

    /** Encrypts this page's client history entry ({@link PageKeys#ENCRYPT_HISTORY}). */
    public InertiaPage encryptHistory() {
        this.encryptHistory = Boolean.TRUE;
        return this;
    }

    /** Wraps JS-unsafe big integers as {@code {"$bigint": "..."}} ({@link PageKeys#PRESERVE_BIG_INTEGERS}). */
    public InertiaPage preserveBigIntegers() {
        this.preserveBigIntegers = Boolean.TRUE;
        return this;
    }

    /** Adds a template-scoped view variable (root template rendering only, never in props). */
    public InertiaPage withViewData(String key, Object value) {
        viewData.put(key, value);
        return this;
    }

    /** Adds several template-scoped view variables. */
    public InertiaPage withViewData(Map<String, Object> viewData) {
        this.viewData.putAll(viewData);
        return this;
    }

    private InertiaPage put(String key, Prop prop) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Inertia prop key must not be blank");
        }
        props.put(key, prop);
        return this;
    }
}
