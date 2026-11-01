package io.github.wesleybritovlk.inertia.core;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Prop wrappers driving how each value participates in resolution and partial reloads.
 * The default ({@link #plain(Object)}) is evaluated and included on standard visits and
 * filtered by partial reload matching;{@link Lazy}, {@link Always}, {@link Deferred},
 * {@link Merge}, {@link Once} and {@link Scroll} change one particular rule, mirroring
 * {@code inertia-laravel}.
 */
public sealed interface Prop
        permits Prop.Plain, Prop.Lazy, Prop.Always, Prop.Deferred, Prop.Merge, Prop.Once, Prop.Scroll {

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
     * Merged by the client on subsequent visits instead of replaced. Metadata shapes are
     * collected by the resolver under {@link PageKeys#MERGE_PROPS},
     * {@link PageKeys#PREPEND_PROPS}, {@link PageKeys#DEEP_MERGE_PROPS} and
     * {@link PageKeys#MATCH_PROPS_ON}. The {@code append} flag selects the root family
     * (Laravel {@code MergesProps::$append}); list fields are defensively copied.
     */
    record Merge(
            Supplier<?> value,
            boolean append,
            List<String> appendsAtPaths,
            List<String> prependsAtPaths,
            boolean deep,
            List<String> matchOn) implements Prop {

        public Merge {
            appendsAtPaths = List.copyOf(appendsAtPaths);
            prependsAtPaths = List.copyOf(prependsAtPaths);
            matchOn = List.copyOf(matchOn);
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
    record Once(
            Prop inner,
            String key,
            Long expiresAtFixed,
            Duration expiresAfter,
            boolean fresh) implements Prop {
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
    record Scroll(
            Supplier<?> value,
            ScrollMetadata metadata,
            String pageName,
            String wrapper,
            Object explicitPage,
            boolean hasMore,
            String deferredGroup) implements Prop {
    }
}
