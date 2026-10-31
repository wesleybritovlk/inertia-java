package io.github.wesleybritovlk.inertia.spring;

import java.io.IOException;

import io.github.wesleybritovlk.inertia.core.InertiaRequest;
import io.github.wesleybritovlk.inertia.core.InertiaResponseShell;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.core.InertiaResponseUpgrade;
import io.github.wesleybritovlk.inertia.core.InertiaVersionGuard;
import io.github.wesleybritovlk.inertia.core.VersionProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adapter equivalent of the Laravel Inertia middleware:
 *
 * <ul>
 *   <li>before the chain: asset-version guard (409 + {@code X-Inertia-Location} on stale
 *       GET visits), so controllers never run for stale assets;</li>
 *   <li>after the chain: {@code Vary: X-Inertia}, redirect upgrades (302 &rarr; 303 for
 *       {@code PUT/PATCH/DELETE/QUERY}) and fragment-redirect escalation (409 +
 *       {@code X-Inertia-Redirect}).</li>
 * </ul>
 */
public final class InertiaFilter extends OncePerRequestFilter {

    private final VersionProvider versionProvider;

    public InertiaFilter(VersionProvider versionProvider) {
        this.versionProvider = versionProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        InertiaRequest adapter = ServletInertiaRequest.of(request);

        InertiaResult conflict = InertiaVersionGuard.check(adapter, versionProvider);
        if (conflict != null) {
            InertiaResultWriter.write(conflict, response);
            return;
        }
        chain.doFilter(request, response);
        if (!response.isCommitted()) {
            InertiaResponseUpgrade.apply(adapter, new ServletInertiaResponse(response));
        }
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
