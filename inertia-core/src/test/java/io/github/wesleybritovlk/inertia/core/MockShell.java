package io.github.wesleybritovlk.inertia.core;

import java.util.LinkedHashMap;

/** Response shell mock for core tests. */
final class MockShell implements InertiaResponseShell {

    int status;
    final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    MockShell(int status) {
        this.status = status;
    }

    MockShell location(String location) {
        headers.put("Location", location);
        return this;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String location() {
        return headers.get("Location");
    }

    @Override
    public void status(int statusCode) {
        this.status = statusCode;
    }

    @Override
    public void header(String name, String value) {
        headers.put(name, value);
    }
}
