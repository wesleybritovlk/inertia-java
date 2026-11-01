package io.github.wesleybritovlk.inertia.demo.quarkus;

import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.quarkus.InertiaProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Hello world through the adapter: a RESTEasy resource returning an Inertia page. The
 * engine is the request-attached one ({@link InertiaProvider#current()}), the Quarkus
 * equivalent of the Spring demo's request-scoped bean.
 */
@Path("/")
@ApplicationScoped
public class HelloController {

    private final InertiaProvider inertiaProvider;

    @Inject
    HelloController(InertiaProvider inertiaProvider) {
        this.inertiaProvider = inertiaProvider;
    }

    @GET
    public InertiaPage hello() {
        return inertiaProvider.current().render("Hello/Index")
                .with("greeting", "Hello World from inertia-java")
                .with("builtWith", "Quarkus + Undertow + Vite + Inertia");
    }
}
