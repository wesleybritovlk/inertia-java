# inertia-java-bean

Framework-agnostic server adapter for [Inertia.js](https://inertiajs.com) applications.

Inertia lets you build single-page apps without a separate API: controllers return page metadata, the client-side router renders, and the Inertia protocol carries page objects over plain HTTP. **inertia-java-bean** implements the server side the way [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) does — by *manipulating the HTTP requests and the HTML* — but built for the Java ecosystem: no embedded server, no framework lock-in. The library is a plain, registerable bean that adapts to whatever serves the HTTP requests.

## Why another Java adapter?

The existing Java adapter ([inertia4j](https://github.com/inertia4j/inertia4j)) couples its public API to `ResponseEntity`, reads the current request from a Spring `RequestContextHolder` thread-local, and compiles against Jackson 2 — all of which break on Spring Boot 4 (Jackson 3, new auto-configuration model, Servlet 6.1). `inertia-java-bean` was designed against those failure modes from day one:

| Coupling trap | How this library avoids it |
|---|---|
| Framework return types (`ResponseEntity<String>`) | The core exchanges only neutral types (`InertiaPage`, `InertiaResult`); adapters map them to the native response |
| Implicit request access (thread-locals) | A neutral `InertiaRequest` port; the request-scoped engine is born from the *current* request inside the integration, never searched by the core |
| Jackson 2 compiled in, Jackson 3 ships with Boot 4 | The core is dependency-free; a reflection adapter prefers *your* Jackson (any major), or falls back to a bundled zero-dependency serializer |
| Hard-coded auto-configuration classes | `@AutoConfiguration` with name-based ordering, works on Boot 3.x and 4.x |

## Modules

| Artifact | Purpose |
|---|---|
| `io.github.wesleybritovlk.inertia:inertia-core` | The Inertia protocol engine. **Zero dependencies** (JDK 17 only). Any framework can plug in via the ports. |
| `io.github.wesleybritovlk.inertia:inertia-spring-boot-starter` | Spring Boot integration (3.x and 4.x): request-scoped engine, protocol filter, return value support and the Thymeleaf root template. |

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Only the pieces you need: use `inertia-core` alone when bringing your own framework adapter (Quarkus, plain servlets, WS).

## Quick start (Spring Boot)

Controllers return an `InertiaPage`; the starter decides JSON vs HTML per the protocol:

```java
@Controller
class UsersController {

    private final Inertia inertia; // request-scoped bean from the starter

    UsersController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/users")
    InertiaPage index() {
        return inertia.render("Users/Index")
            .with("users", () -> repository.findAll())          // evaluated per response
            .withLazy("companies", () -> repository.companies()) // never sent on full visits;
                                                                 // sent when a partial reload asks for it
            .withAlways("auth.user", this::currentUser);         // bypasses partial filtering
    }

    @PostMapping("/users")
    InertiaResult create(UserForm form, BindingResult binding) {
        if (binding.hasErrors()) {
            ValidationErrors.store(inertia, binding, "default");
            return inertia.redirect("/users/create");
        }
        // ...
        return inertia.redirect("/users");
    }
}
```

- `X-Inertia` visits get `application/json` with the page object and the standard `X-Inertia: true` / `Vary: X-Inertia` headers.
- Plain browser visits render the **root page** — the adapter equivalent of Laravel's `app.blade.php` + `@inertia` directive:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
  </head>
  <body>
    <div id="app"></div>
    <script data-page="app" type="application/json">{"component": ...}</script>
  </body>
</html>
```

The bundled Thymeleaf template ships inside the starter; override it with your own layout by creating `templates/inertia.html` in your application (`inertia.template-path`). Variables available: `inertiaPageJson`, `inertiaRootId`, plus any page-level `withViewData` values.

### Validation errors

```java
ValidationErrors.store(inertia, bindingResult, "default"); // survives the redirect via session flash
```

The next rendered page (arbitrary GET, after a redirect) carries the always-present `errors` prop:

```json
{ "props": { "errors": { "name": "is required" }, "...": "..." } }
```

Requests declaring `X-Inertia-Error-Bag: create` reshapes it as `{ "create": { "name": "is required" } }` — Laravel parity.

### Shared props

```java
@Bean
InertiaSharedPropsCustomizer globalProps() {
    return inertia -> inertia.shared("banner", "Connected to inertia-java-bean")
                             .sharedAlways("auth.user", this::currentUser);
}
```

Page props override shared props with the same key.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `inertia.version` | `"1"` | Static asset version (string/hash) compared against `X-Inertia-Version` |
| `inertia.version-file` | _(empty)_ | Path of a build manifest; its SHA-256 becomes the version (recommended — stale assets then trigger the standard 409 full-visit reload) |
| `inertia.root-id` | `app` | DOM id the client mounts into (matches the client configuration) |
| `inertia.template-path` | `classpath:templates/inertia.html` | Classpath resource for your root template override |
| `inertia.filter-order` | `Ordered.HIGHEST_PRECEDENCE + 50` | Filter registration order (outermost by default so Spring Security redirects also get upgraded) |

## Prop types

| Builder | Behavior |
|---|---|
| `with(key, value / supplier)` | Standard: sent on full visits, matched per dot-path on partial reloads |
| `withLazy(key, supplier)` | Excluded from full visits (no evaluation); partial reloads resolve it when matched |
| `withAlways(key, value / supplier)` | Always included — bypasses `only`/`except` partial filtering |
| `withDeferred(key, supplier)` / `withDeferred(key, group, supplier)` | Excluded here; the client re-requests per group (`deferredProps` metadata); `withDeferredRescued(...)` drops failing props into `rescuedProps` instead of failing the page |
| `withMerged(key, supplier, options)` | Client-side merge with `append()/prepend()/deep()/matchOn(...)/appendAt(...)` |
| `withViewData(key, value)` | Template-only variables (never in the page JSON) |

Partial reload honoring follows the Inertia protocol: `X-Inertia-Partial-Component` must match the rendered component (otherwise the request degrades to a full response), `X-Inertia-Partial-Data`/`X-Inertia-Partial-Except` accept dot-paths (only matching subtrees of object props are sent), `X-Inertia-Reset` disables merge metadata per path.

## Serialization strategy (hybrid)

The core never ships a JSON library. On the starter classpath this order applies:

1. A user-defined `PageSerializer` bean,
2. the ObjectMapper your context already provides (Spring Boot / Quarkus — your modules and annotations apply),
3. a fresh instance of whatever Jackson runtime exists on the classpath — Jackson 2 (`com.fasterxml`) first, then Jackson 3 (`tools.jackson`),
4. the bundled `PlainJsonPageSerializer` (records, POJO getters, maps/iterables, `java.time` ISO-8601, `Optional`, cycles guard, `{"$bigint": ...}` wrapping with `preserveBigIntegers`).

Notes: JSON object key order is not part of the Inertia protocol; only the bundled serializer guarantees insertion order. With a Jackson backend, the JS-safe-integer wrapping is emitted by *your* mapper's conventions instead.

## Protocol status

| Feature | State |
|---|---|
| Page JSON (`component`/`props`/`url`+query/`version`) | ✅ |
| Asset versioning + 409 full-page-visit escalation | ✅ |
| Partial reloads (`only`/`except`/`reset`, component match) | ✅ |
| Redirects: 302, method upgrades to 303, external 409 `X-Inertia-Location`, fragment 409 `X-Inertia-Redirect` | ✅ |
| Shared props + always/lazy/deferred (groups + rescue) + merge metadata | ✅ |
| Flash + validation error bags over session | ✅ |
| `clearHistory` / `preserveFragment` / `encryptHistory` | ✅ |
| Root template (Thymeleaf, overridable) | ✅ |
| Once props (`X-Inertia-Except-Once-Props`) | roadmap |
| Scroll props (infinite pagination) | roadmap |
| SSR (Node render server client + `@inertiaHead` fragment) | roadmap (módulo separado) |
| Quarkus integration | roadmap (same core ports) |

## Building from source

```bash
mvn test          # 46 tests: 36 core + 10 starter integration
mvn install       # installs both artifacts locally
```

Each behavioral rule was implemented and then verified against the actual sources of `inertia-laravel` and `@inertiajs/core` (v3.10) — see the test names in `inertia-core` for the mapping (`InertiaProtocolTest`).

## Credits

- [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) — the canonical server adapter; the behavioral reference for this port.
- [`inertia4j`](https://github.com/inertia4j/inertia4j) — prior Java art; the framework-coupling lessons that shaped this design.
- [`@inertiajs/core`](https://github.com/inertiajs/inertia) sources + [inertia docs](https://inertiajs.com/docs/v2/the-protocol) — the client-side protocol contract.
