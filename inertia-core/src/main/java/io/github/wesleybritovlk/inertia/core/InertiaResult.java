package io.github.wesleybritovlk.inertia.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Final protocol output of one request: kind + status + ordered headers + optional JSON
 * payload. Adapters write this to the native response type; nothing here references any web
 * framework.
 */
public final class InertiaResult {

    public enum Kind {
        /** Inertia visit response: {@code application/json} page object with {@code X-Inertia: true}. */
        PAGE_JSON,
        /** Non-Inertia visit: root page rendering owns the JSON via the template. */
        PAGE_HTML,
        /** Normal HTTP redirect (302); adapters may upgrade to 303 on method change. */
        REDIRECT_302,
        /** External redirect: 409 + {@code X-Inertia-Location} makes the client do a full page visit. */
        EXTERNAL_LOCATION_409
    }

    private final Kind kind;
    private final int status;
    private final LinkedHashMap<String, String> headers;
    private final String json;

    private InertiaResult(Kind kind, int status, LinkedHashMap<String, String> headers, String json) {
        this.kind = kind;
        this.status = status;
        this.headers = headers;
        this.json = json;
    }

    static InertiaResult pageJson(String json) {
        LinkedHashMap<String, String> h = new LinkedHashMap<>();
        h.put(InertiaHeaders.INERTIA, "true");
        h.put("Content-Type", "application/json");
        return new InertiaResult(Kind.PAGE_JSON, 200, h, json);
    }

    static InertiaResult pageHtml(String json) {
        LinkedHashMap<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/html;charset=UTF-8");
        return new InertiaResult(Kind.PAGE_HTML, 200, h, json);
    }

    static InertiaResult redirect(String location) {
        LinkedHashMap<String, String> h = new LinkedHashMap<>();
        h.put("Location", location);
        return new InertiaResult(Kind.REDIRECT_302, 302, h, null);
    }

    static InertiaResult externalLocation(String location) {
        LinkedHashMap<String, String> h = new LinkedHashMap<>();
        h.put(InertiaHeaders.LOCATION, location);
        return new InertiaResult(Kind.EXTERNAL_LOCATION_409, 409, h, null);
    }

    static InertiaResult versionConflict(String location, String currentVersion) {
        LinkedHashMap<String, String> h = new LinkedHashMap<>();
        h.put(InertiaHeaders.LOCATION, location);
        h.put(InertiaHeaders.VERSION, currentVersion);
        return new InertiaResult(Kind.EXTERNAL_LOCATION_409, 409, h, null);
    }

    public Kind kind() {
        return kind;
    }

    public int status() {
        return status;
    }

    /** Ordered response headers as sent by the adapter. Unmodifiable view. */
    public Map<String, String> headers() {
        return java.util.Collections.unmodifiableMap(headers);
    }

    /** Serialized page object; present only for page kinds, {@code null} for redirects. */
    public Optional<String> json() {
        return Optional.ofNullable(json);
    }

    /** Page JSON used to render the root template (HTML kind). */
    public Optional<String> pageJson() {
        return Optional.ofNullable(json);
    }
}
