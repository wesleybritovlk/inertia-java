package io.github.wesleybritovlk.inertia.quarkus;

import java.util.logging.Logger;

import io.github.wesleybritovlk.inertia.core.VersionProvider;
import io.github.wesleybritovlk.inertia.servlet.InertiaFilter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;

/**
 * The protocol filter registered on every request dispatch through Undertow (the
 * {@code @WebFilter} CDI bean registration of the Quarkus Undertow extension). Delegates to
 * the shared, plain-servlet {@link InertiaFilter} so the Spring Boot adapter and this one run
 * verbatim the same middleware rules: version guard 409 + {@code X-Inertia-Location} before
 * the chain, {@code Vary: X-Inertia}/redirect upgrades after it — and this attachment binds
 * the request-scoped engine the resources and the return value writer share.
 */
@WebFilter(urlPatterns = "/*", filterName = "inertiaFilter", dispatcherTypes = DispatcherType.REQUEST, asyncSupported = true)
@ApplicationScoped
public class QuarkusInertiaFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(QuarkusInertiaFilter.class.getName());

    private final VersionProvider versionProvider;
    private final InertiaFilter delegate;

    @Inject
    QuarkusInertiaFilter(VersionProvider versionProvider, InertiaRequestAttachment attachment) {
        this.versionProvider = versionProvider;
        this.delegate = new InertiaFilter(versionProvider, attachment);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.fine("Inertia servlet filter registered over Undertow");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, java.io.IOException {
        delegate.doFilter(request, response, chain);
    }
}
