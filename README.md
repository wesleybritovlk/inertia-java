# inertia-java-bean

Framework-agnostic server adapter for [Inertia.js](https://inertiajs.com) applications.

Inertia.js is a client adapter that turns your backend API into a single-page app without a separate API: controllers return page metadata, the client router renders pages, and the Inertia protocol handles the rest. To use Inertia on the server side, your framework must handle **requests** (partial reload headers, page metadata, asset versioning) and **responses** (JSON page object vs HTML root page).

Existing Java adapters couple Inertia to a single framework / web stack and break against alternate locks. **inertia-java-bean takes the `inertia-laravel` design goal and implements it for Java**: everything lives inside a plain, registerable Java bean. You don't get a servlet (or IDE) boot: your own Spring MVC stack, Quarkus, or any framework serves the HTTP part; the adapter owns only the protocol.

## Design goals

- **Zero coupling on the core**: `inertia-core` has **zero third-party dependencies** (JSR-free, only JDK 17). The Inertia protocol lives here: page metadata, property types, partial reloads, asset versioning and redirect rules.
- **No Jackson 2/3 trap**: `inertia4j`'s hard Jackson coupling breaks on Spring Boot 4 (Jackson 3). By default, `inertia-java-bean` serializes with a custom JSON writer. If you need a custom serializer, swap the `PageResolver` SPI.
- **No `RequestContextHolder` / `ThreadLocal` traps**: request access uses a neutral `InertiaRequest` port, created per request by the integration module (`@RequestScope`/`@RequestScoped` bean).
- **No `ResponseEntity` leaking**: the core produces `InertiaResult` (status + headers + payload); each integration maps it to the native response type. Nothing framework-specific crosses the core boundary.
- **Thymeleaf over Blade**: the root page template (Blade's `app.blade.php` + `@inertia`) ships as a Thymeleaf template. You can override it by name. Thymeleaf lives only inside the Spring module, never the core.

## Modules

| Module | Contents |
|---|---|
| `inertia-core` | Inertia protocol engine (page object, prop resolution, partial reloads, asset versioning, redirects, flash + error bag SPI). Zero dependencies. |
| `inertia-spring-boot-starter` | Auto-configuration for Spring Boot (3.x and 4.x): `OncePerRequestFilter` mapping, `InertiaRequest` bean, page/response writer, Thymeleaf root template, `InertiaProps` return value support, flash via `HttpSession`. |

## Usage

Compile, then add only the starter artifact:

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Use the `Inertia` bean inside controllers:

```java
@Controller
class UsersController {

    private final Inertia inertia;

    UsersController(Inertia inertia) { // request-scoped
        this.inertia = inertia;
    }

    @GetMapping("/users")
    InertiaPage index() {
        return inertia.render("Users/Index")
            .with("users", this::users)
            .withLazy("companies", this::companies)
            .withAlways("auth.user", this::currentUser)
            .withDeferred("permissions", this::permissions);
    }
}
```

The starter handles the rest: on `X-Inertia` visits it responds with the JSON page object and standard Inertia headers; non-Inertia visits render the root Thymeleaf template with the page embedded (`data-page`), same as Laravel's `@inertia` directive.

## Status

Early development:
- [x] Project scaffold (Maven, Java 17)
- [x] `inertia-core` protocol engine
- [ ] `inertia-spring-boot-starter` (Spring Boot 3/4 support, Quarkus planned)
- [ ] End-to-end contract tests against the Inertia client protocol

## Credits & prior art

- [inertia-laravel](https://github.com/inertiajs/inertia-laravel) — the canonical server adapter, source of protocol semantics here.
- [inertia4j](https://github.com/inertia4j/inertia4j) — the Java context. `inertia-java-bean` deliberately diverges to stay framework-agnostic with first-class Spring Boot 4 support.
- [inertia.js docs MCP](https://inertiajs.com/docs) — protocol reference.
