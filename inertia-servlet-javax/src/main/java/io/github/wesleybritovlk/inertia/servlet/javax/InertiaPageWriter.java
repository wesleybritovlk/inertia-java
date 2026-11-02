package io.github.wesleybritovlk.inertia.servlet.javax;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.web.InertiaRootTemplateRenderer;

import java.util.function.Supplier;

import javax.servlet.http.HttpServletResponse;

/**
 * Shared writer resolving an {@link InertiaPage}/ {@link InertiaResult} against the
 * request-scoped {@link Inertia} engine and producing the servlet response (JSON page,
 * root template render, redirect or 409 escalation) — the legacy javax twin of the
 * Jakarta adapter's writer.
 *
 * <p>Used by both integration points on Spring Boot 2: the custom return value handler
 * (plain {@code @Controller} methods) and the {@code HttpMessageConverter}
 * ({@code @RestController} methods, where Spring's annotation-based processors take
 * precedence over custom handlers).</p>
 */
public final class InertiaPageWriter {

    private final Supplier<Inertia> inertia;
    private final InertiaRootTemplateRenderer renderer;

    /**
     * @param inertia resolves the CURRENT request-scoped engine each write (Spring wires
     *                {@code inertiaProvider::getObject})
     */
    public InertiaPageWriter(Supplier<Inertia> inertia, InertiaRootTemplateRenderer renderer) {
        this.inertia = inertia;
        this.renderer = renderer;
    }

    public void write(Object returnValue, HttpServletResponse response) throws IOException {
        InertiaResult result;
        LinkedHashMap<String, Object> viewData;
        if (returnValue instanceof InertiaPage) {
            InertiaPage page = (InertiaPage) returnValue;
            result = inertia.get().result(page);
            viewData = new LinkedHashMap<>(page.view());
        } else if (returnValue instanceof InertiaResult) {
            result = (InertiaResult) returnValue;
            viewData = new LinkedHashMap<>();
        } else {
            throw new IllegalStateException(
                    "Unsupported Inertia return value: " + (returnValue == null ? "null" : returnValue.getClass().getName()));
        }

        for (Map.Entry<String, String> header : result.headers().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        response.setStatus(result.status());

        if (result.kind() == InertiaResult.Kind.PAGE_JSON) {
            response.getOutputStream().write(result.json().get().getBytes(StandardCharsets.UTF_8));
        } else if (result.kind() == InertiaResult.Kind.PAGE_HTML) {
            String html = renderer.render(result.pageJson().get(), viewData);
            response.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean supports(Class<?> type) {
        return InertiaPage.class.isAssignableFrom(type) || InertiaResult.class.isAssignableFrom(type);
    }
}
