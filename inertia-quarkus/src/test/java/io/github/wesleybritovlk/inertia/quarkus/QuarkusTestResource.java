package io.github.wesleybritovlk.inertia.quarkus;

import java.util.List;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.core.InertiaSharedPropsCustomizer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

/** Test resource exercising the Quarkus adapter over the Inertia protocol. */
@Path("/")
@ApplicationScoped
public class QuarkusTestResource {

    /**
     * Shared-props customizer bean: exercises the CDI hook of the request-attached engine
     * (spring customizer parity).
     */
    @ApplicationScoped
    public static class BannerCustomizer implements InertiaSharedPropsCustomizer {

        @Override
        public void customize(Inertia inertia) {
            inertia.shared("banner", "quarkus");
        }
    }

    /** Request-scoped resource: exercises the request-scoped {@code Inertia} injection. */
    @Inject
    Inertia inertia;

    @GET
    @Path("/")
    public InertiaPage index() {
        return inertia.render("Home/Index");
    }

    @GET
    @Path("/users")
    public InertiaPage users() {
        return inertia.render("Users/Index", p -> p
                .with("users", List.of("ana", "wei"))
                .withLazy("secret", () -> "only-on-demand"));
    }

    @GET
    @Path("/feed")
    public InertiaPage feed() {
        return inertia.render("Feed", p -> p
                .withOnce("plans", () -> List.of("basic", "pro"))
                .withScroll("posts", () -> List.of(), o -> o.page(1, true)));
    }

    @Path("/users-flash")
    @POST
    public InertiaResult create() {
        Inertia inertia = this.inertia;
        ValidationErrors.store(inertia, Map.of("name", "is required"), "default");
        return inertia.redirect("/users");
    }

    @Path("/external")
    @GET
    public InertiaResult external() {
        return inertia.location("https://sso.example/sign");
    }

    @Path("/fragment")
    @GET
    public InertiaResult fragment() {
        return inertia.redirect("/docs#pricing");
    }

    @Path("/update")
    @PUT
    public InertiaResult update() {
        return inertia.redirect("/users");
    }
}
