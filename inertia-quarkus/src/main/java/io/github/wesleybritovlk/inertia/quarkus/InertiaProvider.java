package io.github.wesleybritovlk.inertia.quarkus;

import io.github.wesleybritovlk.inertia.core.Inertia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The request-scoped engine accessor for Quarkus resources:
 *
 * <pre>{@code
 * @Path("/users")
 * public class UsersController {
 *     @Inject InertiaProvider inertiaProvider;   // resolves the CURRENT request's engine
 *
 *     @GET
 *     public InertiaPage index() {
 *         return inertiaProvider.current().render("Users/Index", p -> p.with("users", ...));
 *     }
 * }</pre>
 *
 * <p>The engine instance lives per request (built by the protocol filter's attachment so
 * shared props customizers and flash state run through it, exactly like Spring's
 * {@code @RequestScope} bean). {@code current()} resolves it (the registry the filter fills);
 * calling it outside the filter chain fails fast with a clear IllegalStateException.</p>
 */
@ApplicationScoped
public class InertiaProvider {

    @Inject
    InertiaRequestAttachment attachment;

    /** The current request's Inertia engine (never {@code null} inside the filter chain). */
    public Inertia current() {
        return CurrentInertia.current().engine();
    }
}
