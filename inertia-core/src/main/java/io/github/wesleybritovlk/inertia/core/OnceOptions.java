package io.github.wesleybritovlk.inertia.core;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Options for one once prop, mirroring the inertia-laravel chains on {@code Inertia::once()}:
 * {@code as(key)}, {@code until(...)} expiry, {@code fresh()}, and composition of the
 * underlying prop kind ({@code lazy()}, {@code deferred(...)}, {@code merged()}).
 */
public final class OnceOptions {

    enum Mode {
        PLAIN, LAZY, DEFERRED, MERGED
    }

    String onceKey;
    Instant expiresAt;
    Duration expiresAfter;
    boolean fresh;
    Mode mode = Mode.PLAIN;
    String deferredGroup = "default";
    final MergeOptions mergeOptions = new MergeOptions();

    /**
     * Once key identifying the prop across pages (Laravel {@code as(...)}) — makes props
     * with different names share one remembered value.
     */
    public OnceOptions as(String key) {
        this.onceKey = key;
        return this;
    }

    /** Absolute expiry (Laravel {@code until(DateTimeInterface)}); emitted as epoch milliseconds. */
    public OnceOptions until(Instant at) {
        this.expiresAt = at;
        return this;
    }

    /**
     * Relative expiry (Laravel {@code until(DateInterval)}; seconds =
     * {@code Duration.ofSeconds(n)}), recomputed per response into epoch milliseconds.
     */
    public OnceOptions until(Duration after) {
        this.expiresAfter = after;
        return this;
    }

    /** Ignores the client's remembered value and sends a fresh copy (Laravel {@code fresh()}). */
    public OnceOptions fresh() {
        return fresh(true);
    }

    /** Conditional force-refresh (Laravel {@code fresh($condition)}). */
    public OnceOptions fresh(boolean on) {
        this.fresh = on;
        return this;
    }

    /** Wraps an optional prop (never sent on full visits, partial-only) — Laravel {@code optional()->once()}. */
    public OnceOptions lazy() {
        this.mode = Mode.LAZY;
        return this;
    }

    /** Wraps a deferred prop in the {@code "default"} group (Laravel {@code defer()->once()}). */
    public OnceOptions deferred() {
        return deferred("default");
    }

    /** Wraps a deferred prop in an explicit group. */
    public OnceOptions deferred(String group) {
        this.mode = Mode.DEFERRED;
        this.deferredGroup = group;
        return this;
    }

    /**
     * Wraps a merged prop (Laravel {@code merge()->once()}) and returns its
     * {@link MergeOptions} for {@code deep()}/{@code prepend()}/{@code matchOn(...)} chaining.
     */
    public MergeOptions merged() {
        this.mode = Mode.MERGED;
        return mergeOptions;
    }

    /** Builds the wrapped prop this configuration describes. */
    Prop wrappedProp(Supplier<?> supplier) {
        switch (mode) {
            case PLAIN:
                return new Prop.Plain(supplier);
            case LAZY:
                return new Prop.Lazy(supplier);
            case DEFERRED:
                return new Prop.Deferred(supplier, deferredGroup, false);
            case MERGED:
                return new Prop.Merge(
                        supplier,
                        mergeOptions.append,
                        mergeOptions.appendsAtPaths,
                        mergeOptions.prependsAtPaths,
                        mergeOptions.deep,
                        mergeOptions.matchOn);
            default:
                throw new IllegalStateException("Unexpected once prop mode: " + mode);
        }
    }
}
