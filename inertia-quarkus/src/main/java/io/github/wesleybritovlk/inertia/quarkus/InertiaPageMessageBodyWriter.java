package io.github.wesleybritovlk.inertia.quarkus;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.web.InertiaRootTemplateRenderer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

/**
 * RESTEasy writer for {@code InertiaPage} resource returns (typed exactly so it wins the
 * writer selection against the generic Jackson provider): the JAX-RS equivalent of the
 * Spring adapter's return value handler + message converter. The page resolves against the
 * engine the protocol filter attached for the current request (the same instance its
 * controller used, shared props state consistent), HTML renders go through the root template
 * renderer, JSON page kinds follow the core result.
 *
 * <p>Status and headers ride on the servlet response (RESTEasy classic runs inside Undertow
 * and lets writers reach it); the payload goes to the JAX-RS entity stream.</p>
 */
@Provider
@ApplicationScoped
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
public class InertiaPageMessageBodyWriter implements MessageBodyWriter<InertiaPage> {

    private final InertiaRootTemplateRenderer renderer;

    @Inject
    InertiaPageMessageBodyWriter(InertiaRootTemplateRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return InertiaPage.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(InertiaPage entity, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        HttpServletResponse response = CurrentInertia.current().response();
        Map<String, Object> viewData = entity.view();
        InertiaResult result = CurrentInertia.current().engine().result(entity);
        for (var header : result.headers().entrySet()) {
            if ("Content-Type".equalsIgnoreCase(header.getKey())) {
                response.setContentType(header.getValue());
            } else {
                response.setHeader(header.getKey(), header.getValue());
            }
        }
        response.setStatus(result.status());
        if (result.kind() == InertiaResult.Kind.PAGE_JSON) {
            entityStream.write(result.json().orElseThrow().getBytes(StandardCharsets.UTF_8));
        } else if (result.kind() == InertiaResult.Kind.PAGE_HTML) {
            entityStream.write(renderer.render(result.pageJson().orElseThrow(), viewData).getBytes(StandardCharsets.UTF_8));
        }
        // other kinds do not exist for page returns (redirects ride InertiaResult);
        // status + headers are already applied either way
    }
}
