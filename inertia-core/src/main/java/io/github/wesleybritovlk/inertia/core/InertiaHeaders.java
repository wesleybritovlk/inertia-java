package io.github.wesleybritovlk.inertia.core;

/**
 * Header names used by the Inertia protocol.
 *
 * @see <a href="https://inertiajs.com/docs/the-protocol">The Inertia protocol</a>
 */
public final class InertiaHeaders {

    /** Marks the request as an Inertia visit (XHR) and must be echoed on every Inertia JSON response. */
    public static final String INERTIA = "X-Inertia";

    /** Asset version carried by Inertia visits; compared against the current {@link VersionProvider} value. */
    public static final String VERSION = "X-Inertia-Version";

    /** Component name being partially reloaded; partial reloads only apply when it matches the rendered component. */
    public static final String PARTIAL_COMPONENT = "X-Inertia-Partial-Component";

    /** Comma-separated dot-paths of props requested by a partial reload. */
    public static final String PARTIAL_DATA = "X-Inertia-Partial-Data";

    /** Comma-separated dot-paths to exclude; takes precedence over {@link #PARTIAL_DATA}. */
    public static final String PARTIAL_EXCEPT = "X-Inertia-Partial-Except";

    /** Comma-separated prop keys whose client-side merge state must be reset. */
    public static final String RESET = "X-Inertia-Reset";

    /** Validation error bag name to scope the shared {@code errors} prop. */
    public static final String ERROR_BAG = "X-Inertia-Error-Bag";

    /** Comma-separated once-prop keys the client already holds and must not receive again. */
    public static final String EXCEPT_ONCE_PROPS = "X-Inertia-Except-Once-Props";

    /**
     * Client intent for infinite-scroll partial reloads ({@code append} or {@code prepend}),
     * telling the server which side the client will merge the returned data onto.
     * Informational: the resolved page metadata does not depend on it.
     */
    public static final String INFINITE_SCROLL_MERGE_INTENT = "X-Inertia-Infinite-Scroll-Merge-Intent";

    /** External redirect target; any such 409 response makes the client perform a full-page visit. */
    public static final String LOCATION = "X-Inertia-Location";

    /** Server-driven redirect the client re-executes as a new Inertia visit (409 responses). */
    public static final String REDIRECT = "X-Inertia-Redirect";

    /** Standard HTTP {@code Vary} header; Inertia responses must vary by {@link #INERTIA}. */
    public static final String VARY = "Vary";

    private InertiaHeaders() {
    }
}
