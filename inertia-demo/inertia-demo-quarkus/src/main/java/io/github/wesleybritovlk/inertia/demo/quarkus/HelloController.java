package io.github.wesleybritovlk.inertia.demo.quarkus;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Hello world through the adapter: a RESTEasy resource returning an Inertia page. The
 * engine is request-scoped ({@code @Inject Inertia inertia} — same ergonomics as the
 * Spring demo), backed by the protocol filter's request attachment.
 */
@Path("/")
@ApplicationScoped
public class HelloController {

    private final Inertia inertia;

    @Inject
    HelloController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GET
    public InertiaPage hello() {
        return inertia.render("Hello/Index")
                .with("greeting", "Hello World from inertia-java")
                .with("builtWith", "Quarkus + Undertow + Vite + Inertia");
    }
}
