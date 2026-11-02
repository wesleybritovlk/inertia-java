package io.github.wesleybritovlk.inertia.servlet.javax;

import java.io.IOException;

import io.github.wesleybritovlk.inertia.core.InertiaRequest;
import io.github.wesleybritovlk.inertia.core.InertiaResponseShell;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.core.InertiaResponseUpgrade;
import io.github.wesleybritovlk.inertia.core.InertiaVersionGuard;
import io.github.wesleybritovlk.inertia.core.VersionProvider;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Adapter-equivalent of the Laravel Inertia middleware on the legacy javax line: the exact
 * middleware {@code inertia-servlet} implements against the Jakarta Servlet API, ported to
 * {@code javax.servlet} (the last javax line, 4.0.1 — Java EE 8 / pre-Jakarta containers,
 * e.g. Tomcat 9 and Spring Boot 2 era, JVM 8 deployments). A WAR always adopts exactly one
 * servlet namespace, so this and the Jakarta twin never coexist at runtime — neither
 * imports the other; the shared protocol machinery (guards, upgrades) and the whole web
 * layer ([inertia-web]) live below both.
 *
 * <ul>
 *   <li>before the chain: asset-version guard (409 + {@code X-Inertia-Location} on stale
 *       GET visits), so controllers never run for stale assets;</li>
 *   <li>after the chain: {@code Vary: X-Inertia}, redirect upgrades (302 &rarr; 303 for
 *       {@code PUT/PATCH/DELETE/QUERY}) and fragment-redirect escalation (409 +
 *       {@code X-Inertia-Redirect}).</li>
 * </ul>
 *
 * <p>Non-{@code REQUEST} dispatches (forward/include/error) pass through untouched — register
 * the filter on request dispatches only, which is the default of Spring Boot 2's
 * {@code FilterRegistrationBean} and of {@code @WebFilter}. The optional
 * {@link InertiaRequestAttachment} lets an adapter keep per-request state around the chain
 * (for example a request-scoped engine the controllers and writers share).</p>
 */
public final class InertiaFilter implements Filter {

    private final VersionProvider versionProvider;
    private final InertiaRequestAttachment attachment;

    public InertiaFilter(VersionProvider versionProvider) {
        this(versionProvider, null);
    }

    public InertiaFilter(VersionProvider versionProvider, InertiaRequestAttachment attachment) {
        this.versionProvider = versionProvider;
        this.attachment = attachment;
    }

    @Override
    public void doFilter(ServletRequest raw, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) raw;
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(raw, rawResponse);
            return;
        }
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        InertiaRequest adapter = ServletInertiaRequest.of(request);
        if (attachment != null) {
            attachment.bind(request, response, adapter);
        }
        try {
            InertiaResult conflict = InertiaVersionGuard.check(adapter, versionProvider);
            if (conflict != null) {
                InertiaResultWriter.write(conflict, response);
                return;
            }
            chain.doFilter(request, response);
            if (!response.isCommitted()) {
                InertiaResponseUpgrade.apply(adapter, new ServletInertiaResponse(response));
            }
        } finally {
            if (attachment != null) {
                attachment.unbind(request, response, adapter);
            }
        }
    }

    /**
     * Per-request hook owned by the host adapter (the filter itself stays stateless). Called
     * around the chain on request dispatches only, catches nothing: exceptions propagate and
     * {@link #unbind} always runs in a {@code finally}.
     */
    public interface InertiaRequestAttachment {

        /** Called before the version guard, once per request-dispatch. */
        void bind(HttpServletRequest request, HttpServletResponse response, InertiaRequest adapter);

        /** Called after the chain/upgrade (also on exceptions), once per request-dispatch. */
        void unbind(HttpServletRequest request, HttpServletResponse response, InertiaRequest adapter);
    }

    private static final class ServletInertiaResponse implements InertiaResponseShell {

        private final HttpServletResponse response;

        private ServletInertiaResponse(HttpServletResponse response) {
            this.response = response;
        }

        @Override
        public int status() {
            return response.getStatus();
        }

        @Override
        public String location() {
            return response.getHeader("Location");
        }

        @Override
        public void status(int statusCode) {
            response.setStatus(statusCode);
        }

        @Override
        public void header(String name, String value) {
            response.setHeader(name, value);
        }
    }
}
