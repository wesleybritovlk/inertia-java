package io.github.wesleybritovlk.inertia.quarkus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.wesleybritovlk.inertia.core.InertiaHeaders;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Static asset serving for Quarkus RESTEasy-classic deployments. The Undertow HTTP pathway
 * does not mount a default servlet for jar static resources (the Vert.x frontend does,
 * plain RESTEasy-filter deployments do not), so the adapter serves them itself: the Vite
 * build lives on the classpath at the Servlet-spec web-resources location
 * {@code META-INF/resources} (the demo mounts {@code ../frontend-assets} there) and this
 * filter maps {@code /assets/*} onto it hashed files, fonts and chunk css included.
 *
 * <p>Registered like every other bean through the library's {@code META-INF/beans.xml}
 * marker — on a Vert.x-based Quarkus app the built-in serving already covers this location,
 * adding this filter is only needed for the Undertow servlet deployment.</p>
 */
@WebFilter(urlPatterns = "/assets/*", filterName = "inertiaStatic")
@ApplicationScoped
public class QuarkusStaticResourcesFilter implements Filter {

    private static final String WEB_RESOURCES = "META-INF/resources";

    private static String contentType(String classpath) {
        int dot = classpath.lastIndexOf('.');
        String extension = dot < 0 ? "" : classpath.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return switch (extension) {
            // the vite build outputs (hashed assets/fonts/chunk css)
            case "", "bin" -> "application/octet-stream";
            case "js", "mjs" -> "application/javascript";
            case "css" -> "text/css;charset=UTF-8";
            case "json", "map" -> "application/json";
            case "html", "htm" -> "text/html;charset=UTF-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "txt" -> "text/plain;charset=UTF-8";
            case "xml" -> "application/xml";
            default -> "application/octet-stream";
        };
    }

    @Override
    public void doFilter(ServletRequest raw, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) raw;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(raw, rawResponse);
            return;
        }
        ClassLoader loader = QuarkusStaticResourcesFilter.class.getClassLoader();
        String classpath = request.getRequestURI().substring(request.getContextPath() == null ? 0
                : request.getContextPath().length());
        try (InputStream resource = loader.getResourceAsStream(WEB_RESOURCES + classpath)) {
            if (resource == null) {
                chain.doFilter(raw, rawResponse); // not ours: keep the default behavior (404)
                return;
            }
            response.setContentType(contentType(classpath));
            response.setHeader(InertiaHeaders.VARY, "Accept-Encoding");
            response.setHeader("Cache-Control", "public, max-age=3600");
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            copy(resource, response.getOutputStream());
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        in.transferTo(out);
        out.flush();
    }
}
