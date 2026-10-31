package io.github.wesleybritovlk.inertia.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request port mock for core tests. */
final class MockRequest implements InertiaRequest {

    final String method;
    final String path;
    final String query;
    final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    final LinkedHashMap<String, Object> flashData = new LinkedHashMap<>();
    private final InertiaFlashStore flashOverride;

    MockRequest(String method, String path, String query) {
        this(method, path, query, null);
    }

    /** Request wired to an explicit flash store (e.g. the {@code NONE} degenerate store). */
    MockRequest(String method, String path, String query, InertiaFlashStore flashOverride) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.flashOverride = flashOverride;
    }

    static MockRequest noneFlash(String path) {
        return new MockRequest("GET", path, "", InertiaFlashStore.none()).inertia();
    }

    static MockRequest get(String path) {
        return new MockRequest("GET", path, "");
    }

    static MockRequest get(String path, String query) {
        return new MockRequest("GET", path, query);
    }

    MockRequest header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    MockRequest inertia() {
        return header(InertiaHeaders.INERTIA, "true");
    }

    MockRequest flash(String key, Object value) {
        flashData.put(key, value);
        return this;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String header(String name) {
        return headers.get(name);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public InertiaFlashStore flash() {
        if (flashOverride != null) {
            return flashOverride;
        }
        return new InertiaFlashStore() {
            @Override
            public Object pull(String key) {
                return flashData.remove(key);
            }

            @Override
            public void put(String key, Object value) {
                flashData.put(key, value);
            }

            @Override
            public Object peek(String key) {
                return flashData.get(key);
            }
        };
    }

    /** Convenience: an example flash-backed errors bag. */
    static Map<String, Object> defaultBag(String field, String message) {
        LinkedHashMap<String, Object> bag = new LinkedHashMap<>();
        bag.put(field, List.of(message));
        LinkedHashMap<String, Object> bags = new LinkedHashMap<>();
        bags.put("default", bag);
        return bags;
    }
}
