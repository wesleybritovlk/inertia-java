# Quarkus adapter (inertia-quarkus)

The same protocol on Quarkus: a CDI adapter over the shared servlet glue
([inertia-servlet](other-frameworks.md)), running on Undertow + RESTEasy.

## Getting started

Dependencies (with the Quarkus platform BOM on your app, versions are managed):

```xml
<!-- the adapter: core + servlet glue in one artifact -->
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-quarkus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

That is everything: the adapter brings Undertow servlet registration via CDI beans, the
RESTEasy provider (return value writer) and the CDI config mapping. Controllers look like
Spring's side — a bean + `render(...)`:

```java
@Path("/users")
public class UsersController {

    // the request-scoped engine, exactly the Spring bean's ergonomics
    @Inject
    Inertia inertia;

    @GET
    public InertiaPage index() {
        return inertia.render("Users/Index", page -> page
                .with("users", () -> repository.findAll())
                .withLazy("stats", () -> repository.stats()));
    }

    @POST
    public InertiaResult create(@Valid User user) {
        ValidationErrors.store(inertia, violations, "default");
        return inertia.redirect("/users");
    }
}
```

Resource methods may return `InertiaPage` (pages + root renders) or `InertiaResult`
(redirects, external locations) — the JAX-RS writer produces the page object JSON for
Inertia visits and the root HTML for plain visits. The protocol middleware rules identical
to the Spring side (the same `InertiaFilter` runs under both): version 409s, `Vary`
header and redirect upgrades.

The injected `Inertia` is a request-scoped CDI bean (the protocol filter's attachment
builds it, shared props customizers included) — CDI injects it through a client proxy,
so the field resolves the CURRENT request's engine on an `@ApplicationScoped` resource
just like Spring's request-scoped bean resolves per request.

## Configuration

SmallRye config mapping, prefix `inertia` — same names + defaults as the Spring
properties ([configuration.md](configuration.md)):

```properties
inertia.version=1
# inertia.version-file=classpath:META-INF/resources/manifest.json   # file-hash versioning
inertia.root-id=app
# inertia.vite-manifest=classpath:META-INF/resources/manifest.json  # enables the @vite-equivalent root
# inertia.vite-dev-url=http://localhost:5173
# inertia.vite-dev-hot=                                  # blank = auto-scan; off disables
# inertia.vite-react-refresh=auto                        # auto | true | false
```

Vite dev mode works identically to Spring: the companion plugin keeps writing
`node_modules/.hot-inertia`, the shared glue's hot-aware versioning (Laravel parity)
and the React preamble auto-detection (`/@react-refresh` probing) apply as documented
in [dev-workflow.md](dev-workflow.md).

## Overrides (CDI style)

Every ecosystem bean is a normal producer — override any with `@Alternative` +
`@Priority`:

- `VersionProvider` (your build stamp),
- `PageSerializer` (your Jackson settings are picked up from the app-provided mapper
  bean **first** — Jackson 2 (Quarkus) then Jackson 3 detection then the bundled
  serializer),
- root template renderer (bundled Thymeleaf + the Vite manifest layers).

```java
@Alternative
@Priority(1)
@ApplicationScoped
public class MyVersion implements VersionProvider {
    public String version() {
        return "b3af";
    }
}
```

## Validation

```java
@POST
public InertiaResult create(@Valid UserForm form) {
    Set<ConstraintViolation<UserForm>> violations = validator.validate(form);
    if (!violations.isEmpty()) {
        ValidationErrors.store(inertia, violations, "default");
        return inertia.redirect("/users");
    }
    // ...
    return inertia.redirect("/users");
}
```

`ValidationErrors` (this module) writes through the shared `errors` prop via the session
flash store, same contract as [flash-and-validation.md](flash-and-validation.md). There
is also a plain-map overload for any other validation backend.

## Architecture

| Concern | Where |
|---|---|
| Protocol engine (the ports `Inertia`/`InertiaPage`/etc. hang on) | `inertia-core` (zero deps) |
| Servlet glue (filter/request adapter/writers/renderers) | `inertia-servlet` (Jakarta Servlet only) |
| Quarkus wiring (CDI beans, config, Undertow registration, RESTEasy writer) | `inertia-quarkus` (this module) |

Everything else — getting started content, prop types, partial reloads — is identical
between the Spring and Quarkus sides; this adapter simply reuses them. A runnable proof
of the whole flow lives in the demo tree: `inertia-demo/inertia-demo-quarkus` serves the
same canonical pages the Spring demo does, from one front-end build — setup and run
commands in [the demo README](../inertia-demo/README.md).
