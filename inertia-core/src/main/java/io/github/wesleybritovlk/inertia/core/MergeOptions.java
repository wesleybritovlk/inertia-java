package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Merge configuration for {@link InertiaPage#withMerged(String, java.util.function.Supplier, Consumer)},
 * mirroring the {@code MergesProps} trait of inertia-laravel: a single root-family flag
 * ({@code append}, default true, flipped by {@link #prepend()}), independent
 * {@code appendAt/prependAt} sub-path lists, {@code deep()} merging and item identity through
 * {@code matchOn(...)}.
 */
public final class MergeOptions {

    boolean append = true;
    final List<String> appendsAtPaths = new ArrayList<>();
    final List<String> prependsAtPaths = new ArrayList<>();
    boolean deep;
    final List<String> matchOn = new ArrayList<>();

    /** Root append; the default behavior (also re-enables it after {@link #prepend()}). */
    public MergeOptions append() {
        this.append = true;
        return this;
    }

    /** Appends into the nested array at {@code "prop." + subPath} (root family flag untouched). */
    public MergeOptions appendAt(String subPath) {
        this.appendsAtPaths.add(subPath);
        return this;
    }

    /** Root prepend: flips the root family off-append (Laravel {@code prepend(true)} parity). */
    public MergeOptions prepend() {
        this.append = false;
        return this;
    }

    /** Prepends into the nested array at {@code "prop." + subPath} (root family flag untouched). */
    public MergeOptions prependAt(String subPath) {
        this.prependsAtPaths.add(subPath);
        return this;
    }

    /** Client deep-merges the value (objects merge recursively, arrays concatenate). */
    public MergeOptions deep() {
        this.deep = true;
        return this;
    }

    /**
     * Item identity fields used when merging arrays of objects, so repeated visits replace
     * existing items instead of duplicating them.
     */
    public MergeOptions matchOn(String... keys) {
        this.matchOn.addAll(Arrays.asList(keys));
        return this;
    }
}
