package io.github.wesleybritovlk.inertia.quarkus;

import java.util.List;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;

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
    public static class BannerCustomizer implements io.github.wesleybritovlk.inertia.core.InertiaSharedPropsCustomizer {

        @Override
        public void customize(Inertia inertia) {
            inertia.shared("banner", "quarkus");
        }
    }

    private final InertiaProvider inertiaProvider;

    @Inject
    QuarkusTestResource(InertiaProvider inertiaProvider) {
        this.inertiaProvider = inertiaProvider;
    }

    @GET
    @Path("/")
    public InertiaPage index() {
        return inertiaProvider.current().render("Home/Index");
    }

    @GET
    @Path("/users")
    public InertiaPage users() {
        return inertiaProvider.current().render("Users/Index", p -> p
                .with("users", List.of("ana", "wei"))
                .withLazy("secret", () -> "only-on-demand"));
    }

    @GET
    @Path("/feed")
    public InertiaPage feed() {
        return inertiaProvider.current().render("Feed", p -> p
                .withOnce("plans", () -> List.of("basic", "pro"))
                .withScroll("posts", () -> List.of(), o -> o.page(1, true)));
    }

    @Path("/users-flash")
    @POST
    public InertiaResult create() {
        Inertia inertia = inertiaProvider.current();
        ValidationErrors.store(inertia, Map.of("name", "is required"), "default");
        return inertia.redirect("/users");
    }

    @Path("/external")
    @GET
    public InertiaResult external() {
        return inertiaProvider.current().location("https://sso.example/sign");
    }

    @Path("/fragment")
    @GET
    public InertiaResult fragment() {
        return inertiaProvider.current().redirect("/docs#pricing");
    }

    @Path("/update")
    @PUT
    public InertiaResult update() {
        return inertiaProvider.current().redirect("/users");
    }
}
