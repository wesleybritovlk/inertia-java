package io.github.wesleybritovlk.inertia.core;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Prop wrappers driving how each value participates in resolution and partial reloads.
 * The default ({@link #plain(Object)}) is evaluated and included on standard visits and
 * filtered by partial reload matching;{@link Lazy}, {@link Always}, {@link Deferred},
 * {@link Merge}, {@link Once} and {@link Scroll} change one particular rule, mirroring
 * {@code inertia-laravel}.
 */
public interface Prop {

    /**
     * Standard prop: always included on full visits; on partial reloads only when its
     * dot-path matches the requested {@code only} list ( {@link Always} bypasses that filter).
     */
    final class Plain implements Prop {

        private final Supplier<?> value;

        public Plain(Supplier<?> value) {
            this.value = value;
        }

        public Supplier<?> value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Plain)) {
                return false;
            }
            return java.util.Objects.equals(value, ((Plain) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Plain[value=" + value + "]";
        }
    }

    /**
     * Never sent on full visits (cost-free: the supplier is not evaluated); sent on partial
     * reloads only when its dot-path matches.
     */
    final class Lazy implements Prop {

        private final Supplier<?> value;

        public Lazy(Supplier<?> value) {
            this.value = value;
        }

        public Supplier<?> value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Lazy)) {
                return false;
            }
            return java.util.Objects.equals(value, ((Lazy) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Lazy[value=" + value + "]";
        }
    }

    /** Bypasses partial filtering and is always included and evaluated. */
    final class Always implements Prop {

        private final Supplier<?> value;

        public Always(Supplier<?> value) {
            this.value = value;
        }

        public Supplier<?> value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Always)) {
                return false;
            }
            return java.util.Objects.equals(value, ((Always) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "Always[value=" + value + "]";
        }
    }

    /**
     * Excluded from this page's props on every visit; the client later re-requests each
     * deferred group as a partial reload. Tracked under {@link PageKeys#DEFERRED_PROPS}.
     * When {@code rescue} is set and the supplier fails, the prop is dropped and reported
     * under {@link PageKeys#RESCUED_PROPS} instead of aborting the response.
     */
    final class Deferred implements Prop {

        private final Supplier<?> value;
        private final String group;
        private final boolean rescue;

        public Deferred(Supplier<?> value, String group, boolean rescue) {
            this.value = value;
            this.group = group;
            this.rescue = rescue;
        }

        public Supplier<?> value() {
            return value;
        }

        public String group() {
            return group;
        }

        public boolean rescue() {
            return rescue;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Deferred)) {
                return false;
            }
            Deferred that = (Deferred) other;
            return value.equals(that.value)
                    && Objects.equals(group, that.group)
                    && rescue == that.rescue;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, group, rescue);
        }

        @Override
        public String toString() {
            return "Deferred[value=" + value + ", group=" + group + ", rescue=" + rescue + "]";
        }
    }

    /**
     * Merged by the client on subsequent visits instead of replaced. Metadata shapes are
     * collected by the resolver under {@link PageKeys#MERGE_PROPS},
     * {@link PageKeys#PREPEND_PROPS}, {@link PageKeys#DEEP_MERGE_PROPS} and
     * {@link PageKeys#MATCH_PROPS_ON}. The {@code append} flag selects the root family
     * (Laravel {@code MergesProps::$append}); list fields are defensively copied.
     */
    final class Merge implements Prop {

        private final Supplier<?> value;
        private final boolean append;
        private final List<String> appendsAtPaths;
        private final List<String> prependsAtPaths;
        private final boolean deep;
        private final List<String> matchOn;

        public Merge(
                Supplier<?> value,
                boolean append,
                List<String> appendsAtPaths,
                List<String> prependsAtPaths,
                boolean deep,
                List<String> matchOn) {
            this.value = value;
            this.append = append;
            this.appendsAtPaths = unmodifiable(appendsAtPaths);
            this.prependsAtPaths = unmodifiable(prependsAtPaths);
            this.deep = deep;
            this.matchOn = unmodifiable(matchOn);
        }

        /** List.copyOf parity without the runtime-10 API null-element strictness gap. */
        private static List<String> unmodifiable(List<String> values) {
            return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
        }

        public Supplier<?> value() {
            return value;
        }

        public boolean append() {
            return append;
        }

        public List<String> appendsAtPaths() {
            return appendsAtPaths;
        }

        public List<String> prependsAtPaths() {
            return prependsAtPaths;
        }

        public boolean deep() {
            return deep;
        }

        public List<String> matchOn() {
            return matchOn;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Merge)) {
                return false;
            }
            Merge that = (Merge) other;
            return value.equals(that.value)
                    && append == that.append
                    && appendsAtPaths.equals(that.appendsAtPaths)
                    && prependsAtPaths.equals(that.prependsAtPaths)
                    && deep == that.deep
                    && matchOn.equals(that.matchOn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, append, appendsAtPaths, prependsAtPaths, deep, matchOn);
        }

        @Override
        public String toString() {
            return "Merge[value=" + value + ", append=" + append + ", appendsAtPaths=" + appendsAtPaths
                    + ", prependsAtPaths=" + prependsAtPaths + ", deep=" + deep + ", matchOn=" + matchOn + "]";
        }
    }

    /**
     * Once prop (inertia-laravel {@code Inertia::once()}): resolved a single time per client
     * and reused on later pages carrying the same once key. Composes by wrapping any other
     * prop kind — {@link Plain}, {@link Lazy}, {@link Deferred}, {@link Merge} (Laravel
     * chains {@code once()} onto deferred/merge/optional props). On full visits the
     * remembered keys ({@code X-Inertia-Except-Once-Props}) skip resolution unless
     * {@code fresh} forces a refresh; partial reloads always resolve requested once props.
     * The {@code key} identifies the prop across pages (defaults to the prop key; custom
     * via {@link OnceOptions#as}); {@code expiresAtFixed} is an absolute expiry in epoch
     * milliseconds and {@code expiresAfter} a per-response relative one &mdash; both
     * nullable, both emitted as {@code expiresAt}. Metadata goes to
     * {@link PageKeys#ONCE_PROPS}.
     */
    final class Once implements Prop {

        private final Prop inner;
        private final String key;
        private final Long expiresAtFixed;
        private final Duration expiresAfter;
        private final boolean fresh;

        public Once(
                Prop inner,
                String key,
                Long expiresAtFixed,
                Duration expiresAfter,
                boolean fresh) {
            this.inner = inner;
            this.key = key;
            this.expiresAtFixed = expiresAtFixed;
            this.expiresAfter = expiresAfter;
            this.fresh = fresh;
        }

        public Prop inner() {
            return inner;
        }

        public String key() {
            return key;
        }

        public Long expiresAtFixed() {
            return expiresAtFixed;
        }

        public Duration expiresAfter() {
            return expiresAfter;
        }

        public boolean fresh() {
            return fresh;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Once)) {
                return false;
            }
            Once that = (Once) other;
            return inner.equals(that.inner)
                    && key.equals(that.key)
                    && Objects.equals(expiresAtFixed, that.expiresAtFixed)
                    && Objects.equals(expiresAfter, that.expiresAfter)
                    && fresh == that.fresh;
        }

        @Override
        public int hashCode() {
            return Objects.hash(inner, key, expiresAtFixed, expiresAfter, fresh);
        }

        @Override
        public String toString() {
            return "Once[inner=" + inner + ", key=" + key + ", expiresAtFixed=" + expiresAtFixed
                    + ", expiresAfter=" + expiresAfter + ", fresh=" + fresh + "]";
        }
    }

    /**
     * Scroll prop (infinite scroll, inertia-laravel {@code Inertia::scroll()}): built on
     * merging &mdash; the resolved value is labeled for client append/prepend at
     * {@code <path>.<wrapper>} and the page object carries the cursor under
     * {@link PageKeys#SCROLL_PROPS}. A bare list value is wrapped as
     * {@code {<wrapper>: [...]}}; maps pass through and must contain the wrapper key.
     * The cursor resolves per request: the {@link ScrollMetadata} provider wins, otherwise
     * the current page comes from the {@code pageName} query parameter (Laravel paginator
     * parity) falling back to {@code explicitPage}; {@code hasMore} declares whether a next
     * page exists. The optional {@code deferredGroup} defers it exactly like
     * {@link Deferred} (cursor metadata only when the value actually resolves, on the
     * follow-up partial).
     */
    final class Scroll implements Prop {

        private final Supplier<?> value;
        private final ScrollMetadata metadata;
        private final String pageName;
        private final String wrapper;
        private final Object explicitPage;
        private final boolean hasMore;
        private final String deferredGroup;

        public Scroll(
                Supplier<?> value,
                ScrollMetadata metadata,
                String pageName,
                String wrapper,
                Object explicitPage,
                boolean hasMore,
                String deferredGroup) {
            this.value = value;
            this.metadata = metadata;
            this.pageName = pageName;
            this.wrapper = wrapper;
            this.explicitPage = explicitPage;
            this.hasMore = hasMore;
            this.deferredGroup = deferredGroup;
        }

        public Supplier<?> value() {
            return value;
        }

        public ScrollMetadata metadata() {
            return metadata;
        }

        public String pageName() {
            return pageName;
        }

        public String wrapper() {
            return wrapper;
        }

        public Object explicitPage() {
            return explicitPage;
        }

        public boolean hasMore() {
            return hasMore;
        }

        public String deferredGroup() {
            return deferredGroup;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Scroll)) {
                return false;
            }
            Scroll that = (Scroll) other;
            return value.equals(that.value)
                    && Objects.equals(metadata, that.metadata)
                    && Objects.equals(pageName, that.pageName)
                    && Objects.equals(wrapper, that.wrapper)
                    && Objects.equals(explicitPage, that.explicitPage)
                    && hasMore == that.hasMore
                    && Objects.equals(deferredGroup, that.deferredGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, metadata, pageName, wrapper, explicitPage, hasMore, deferredGroup);
        }

        @Override
        public String toString() {
            return "Scroll[value=" + value + ", metadata=" + metadata + ", pageName=" + pageName
                    + ", wrapper=" + wrapper + ", explicitPage=" + explicitPage + ", hasMore=" + hasMore
                    + ", deferredGroup=" + deferredGroup + "]";
        }
    }
}
