package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port through which the core protocol reads the current HTTP request, expressed free of any
 * framework types. Adapters implement it (Spring MVC wraps {@code HttpServletRequest}, a
 * Quarkus filter wraps the reactive exchange, and so on).
 *
 * <p>The Inertia protocol also inspects session-scoped flash data; adapters without session
 * support return {@link InertiaFlashStore#none()}.</p>
 */
public interface InertiaRequest {

    /** HTTP method, e.g. {@code "GET"}. */
    String method();

    /** First header value, or {@code null} when absent. */
    String header(String name);

    /**
     * Request path <em>without</em> the query string, as received (versioned form &mdash; the
     * core never re-decodes it, mirrors Laravel's {@code REQUEST_URI} handling).
     */
    String path();

    /** Raw query string, or empty string when absent. */
    String query();

    /** Flash/session store, or {@link InertiaFlashStore#none()}. */
    InertiaFlashStore flash();

    default boolean isInertiaVisit() {
        return header(InertiaHeaders.INERTIA) != null;
    }

    /**
     * Page {@code url} value required by the protocol: path plus query string (Laravel sends
     * the full URL minus origin, which matches the servlet {@code requestURI + queryString}).
     */
    default String url() {
        String q = query();
        return q == null || q.isEmpty() ? path() : path() + "?" + q;
    }

    /** Comma-separated header split into trimmed non-empty parts. */
    default List<String> headerList(String name) {
        String value = header(name);
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return Collections.unmodifiableList(parts);
    }
}
