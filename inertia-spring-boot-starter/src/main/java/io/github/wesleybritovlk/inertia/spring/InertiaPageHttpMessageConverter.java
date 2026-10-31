package io.github.wesleybritovlk.inertia.spring;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpResponse;

/**
 * Custom message converter for {@code InertiaPage}/{@code InertiaResult} controller returns.
 *
 * <p>This exists because Spring's annotation-based return value processors
 * ( {@code RequestResponseBodyMethodProcessor}, activated on every {@code @RestController}
 * method) intentionally run BEFORE custom return value handlers &mdash; a plain custom
 * handler gets bypassed. Message converters are picked by type instead, so a converter
 * registered first wins cleanly for both {@code @RestController} and {@code @Controller}
 * methods.</p>
 *
 * <p>The protocol never negotiates based on Accept: Inertia visits carry their own response
 * shape (JSON page object) and root renders are HTML, mirroring {@code inertia-laravel}.</p>
 */
final class InertiaPageHttpMessageConverter implements HttpMessageConverter<Object> {

    private static final List<MediaType> SUPPORTED = List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML);

    private final InertiaPageWriter writer;

    InertiaPageHttpMessageConverter(InertiaPageWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return writer.supports(clazz);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return SUPPORTED;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
        return SUPPORTED;
    }

    @Override
    public Object read(Class<?> clazz, org.springframework.http.HttpInputMessage inputMessage) {
        throw new UnsupportedOperationException("Inertia pages are write-only protocol payloads");
    }

    @Override
    public void write(Object value, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        if (!(outputMessage instanceof ServletServerHttpResponse servlet)) {
            throw new IllegalStateException("Inertia page output requires the servlet stack");
        }
        writer.write(value, servlet.getServletResponse());
    }
}
