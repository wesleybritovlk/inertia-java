package io.github.wesleybritovlk.inertia.quarkus;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import io.github.wesleybritovlk.inertia.core.InertiaResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

/**
 * RESTEasy writer for {@code InertiaResult} resource returns: redirects (302 / upgraded
 * kinds), external 409 page-visits and pre-rendered results. Exactly typed so the selection
 * beats the generic Jackson provider. Status and headers ride on the servlet response; the
 * result carries no body by protocol, so the entity stream stays empty.
 */
@Provider
@ApplicationScoped
public class InertiaResultMessageBodyWriter implements MessageBodyWriter<InertiaResult> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return InertiaResult.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(InertiaResult result, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        HttpServletResponse response = CurrentInertia.current().response();
        for (var header : result.headers().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        response.setStatus(result.status());
        // results carry the protocol control output only (no entity body)
    }
}
