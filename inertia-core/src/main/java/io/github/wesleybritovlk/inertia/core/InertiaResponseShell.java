package io.github.wesleybritovlk.inertia.core;

/**
 * Neutral view over the response being finalized, used to apply the post-handler protocol
 * rules (adapters wrap their native response; e.g. {@code HttpServletResponse} for Spring).
 */
public interface InertiaResponseShell {

    /** Current response status. */
    int status();

    /** Current {@code Location} header value, or {@code null}. */
    String location();

    /** Replaces the response status. */
    void status(int statusCode);

    /** Sets (replaces) one response header. */
    void header(String name, String value);
}
