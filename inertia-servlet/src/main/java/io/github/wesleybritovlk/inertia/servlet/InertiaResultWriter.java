package io.github.wesleybritovlk.inertia.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.wesleybritovlk.inertia.core.InertiaResult;

import jakarta.servlet.http.HttpServletResponse;

/** Writes a core {@link InertiaResult} into the servlet response (status + headers + JSON payload). */
public final class InertiaResultWriter {

    private InertiaResultWriter() {
    }

    public static void write(InertiaResult result, HttpServletResponse response) throws IOException {
        for (var entry : result.headers().entrySet()) {
            response.setHeader(entry.getKey(), entry.getValue());
        }
        response.setStatus(result.status());
        if (result.kind() == InertiaResult.Kind.PAGE_JSON) {
            String json = result.json().orElseThrow();
            response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
