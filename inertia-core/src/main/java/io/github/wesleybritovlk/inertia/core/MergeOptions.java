package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Merge configuration for {@link InertiaPage#withMerged(String, java.util.function.Supplier, Consumer)}.
 * Defaults to a root append exactly like {@code Inertia::merge(...)} from the Laravel adapter.
 */
public final class MergeOptions {

    boolean appendRoot = true;
    final List<String> appendsAtPaths = new ArrayList<>();
    boolean prependRoot;
    final List<String> prependsAtPaths = new ArrayList<>();
    boolean deep;
    final List<String> matchOn = new ArrayList<>();

    /** Client appends the received items to the array prop (default behavior). */
    public MergeOptions append() {
        this.appendRoot = true;
        this.prependRoot = false;
        return this;
    }

    /** Client appends into the nested array at {@code "prop." + subPath}. */
    public MergeOptions appendAt(String subPath) {
        this.appendsAtPaths.add(subPath);
        this.appendRoot = false;
        return this;
    }

    /** Client prepends the received items to the array prop. */
    public MergeOptions prepend() {
        this.prependRoot = true;
        this.appendRoot = false;
        return this;
    }

    /** Client prepends into the nested array at {@code "prop." + subPath}. */
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
