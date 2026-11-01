# inertia-java

Framework-agnostic server adapter for [Inertia.js](https://inertiajs.com) applications.

Inertia lets you build single-page apps without a separate API: controllers return page metadata, the client-side router renders, and the Inertia protocol carries page objects over plain HTTP. **inertia-java** implements the server side the way [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) does — by *manipulating the HTTP requests and the HTML* — but built for the Java ecosystem: no embedded server, no framework lock-in. The library is a plain, registerable bean that adapts to whatever serves the HTTP requests.

## Why another Java adapter?

Most Java tooling grows convenience APIs that end up holding web-framework types in its
core. This library takes the opposite route from the first commit, and the comparer for
every design decision is the Laravel adapter's protocol semantics:

| Design principle | How this library does it |
|---|---|
| Framework-neutral core | The core exchanges only neutral types (`InertiaPage`, `InertiaResult`); integrations map them to the native response |
| Request access through ports | A neutral `InertiaRequest` port; the request-scoped engine is born from the *current* request inside the integration |
| Any JSON runtime | The core is dependency-free; a reflection adapter prefers *your* Jackson (any major), or falls back to a bundled zero-dependency serializer |
| Modern Boot, present and next | `@AutoConfiguration` with name-based ordering, Jakarta Servlet 6.x compile-only — tested on Boot 3.x and 4.x |

The SPI split in [`inertia4j`](https://github.com/inertia4j/inertia4j) (core + framework
adapters) confirmed this direction is viable — this project pushes it further by keeping
even the serialization and request ports runtime-neutral.

## Modules

| Artifact | Purpose |
|---|---|
| `io.github.wesleybritovlk.inertia:inertia-core` | The Inertia protocol engine. **Zero dependencies** (JDK 17 only). Any framework can plug in via the ports. |
| `io.github.wesleybritovlk.inertia:inertia-servlet` | The shared Jakarta Servlet glue: request adapter, protocol filter, result/page writers, Thymeleaf + Vite root renderers and version providers. |
| `io.github.wesleybritovlk.inertia:inertia-spring-boot` | Spring Boot integration (3.x and 4.x): request-scoped engine, protocol filter, return value support and the Thymeleaf root template. |
| `io.github.wesleybritovlk.inertia:inertia-quarkus` | Quarkus integration: CDI wiring over the same servlet glue, Undertow filter registration, RESTEasy return value writer, SmallRye config. |
| `io.github.wesleybritovlk.inertia:inertia-demo` | Demos: three canonical Vite front-ends (Vue 3, React 19, Svelte 5) serving the same pages from **both** back-ends — `inertia-demo-spring` (Spring Boot, `mvn spring-boot:run`) and `inertia-demo-quarkus` (Quarkus, `mvn quarkus:dev`); one `npm run build` feeds both — see [inertia-demo/README.md](inertia-demo/README.md). |

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Only the pieces you need: use `inertia-core` (or the servlet glue) alone when bringing
your own framework adapter, or the Quarkus adapter
([docs/inertia-quarkus.md](docs/inertia-quarkus.md)).

## Spring Boot 4

The whole reactor compiles, tests and runs on **Spring Boot 4** (`mvn -Pboot4 verify`) —
Framework 7.0.6, Tomcat 11, Jackson 3 runtime picked automatically by the hybrid
serializer chain ([docs/serialization.md](docs/serialization.md)). The demo runs live on
both Boot 3.3.12 and Boot 4.0.5 with the same source tree.

## Documentation

Complete usage documentation lives in [`docs/`](docs/index.md):

| Doc | Topic |
|---|---|
| [Getting started](docs/getting-started.md) | First run on Spring Boot end to end (controller, root template, front-end wiring, validation flow, catch-all SPA routes) |
| [Pages and props](docs/pages-and-props.md) | `render` + every prop builder incl. deferred groups, merge, once and scroll options |
| [Partial reloads](docs/partial-reloads.md) | the `X-Inertia-Partial-*` protocol, subtree cutting, `X-Inertia-Reset` |
| [Redirects and versioning](docs/redirects-and-versioning.md) | 303/409 redirect rules and the asset-version guard |
| [Flash and validation](docs/flash-and-validation.md) | session flash, `ValidationErrors.store`, error-bag renaming |
| [Shared props](docs/shared-props.md) | request-scoped shared props + customizer beans |
| [Serialization](docs/serialization.md) | the hybrid Jackson 2/3/bundled strategy and the `PageSerializer` SPI |
| [Configuration](docs/configuration.md) | all `inertia.*` properties and bean overrides |
| [Other frameworks](docs/other-frameworks.md) | the shared `inertia-servlet` glue + raw-servlet usage of `inertia-core` |
| [Quarkus adapter](docs/inertia-quarkus.md) | `inertia-quarkus`: CDI setup, config mapping, RESTEasy return handling, Vite parity |
| [Differences from inertia-laravel](docs/differences-from-inertia-laravel.md) | every behavior that is not a 1:1 port |

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

The bundled Thymeleaf root template ships inside the library — machinery (vite script/css,
dev blocks, root mount, data-page payload) all internalized. Override by creating
`templates/inertia.html` in your application as a **head fragment** (title, meta, fonts,
CDN scripts — injected into the bundled head) or, advanced, a whole document wiring the
same variables yourself. Variables available: `inertiaPageJson`, `inertiaRootId`,
the Vite wiring and any page-level `withViewData` values — details in
[docs/configuration.md](docs/configuration.md#root-template-override).

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
    return inertia -> inertia.shared("banner", "Connected to inertia-java")
                             .sharedAlways("auth.user", this::currentUser);
}
```

Page props override shared props with the same key.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `inertia.version` | `"1"` | Static asset version (string/hash) compared against `X-Inertia-Version` |
| `inertia.version-file` | _(empty)_ | Path of a build manifest; its SHA-256 becomes the version (recommended — stale assets then trigger the standard 409 full-visit reload) |
| `inertia.vite-manifest` | _(empty)_ | Vite manifest location (`classpath:` paths are jar-safe); enables the `@vite` equivalent renderer and — when no explicit version source is set — the asset version from the same manifest |
| `inertia.vite-base` | `/` | URL prefix for Vite asset files |
| `inertia.vite-entry` | _(empty)_ | Vite entry key override; default resolves the first `"isEntry": true` entry |
| `inertia.vite-dev-url` | `http://localhost:5173` | Vite dev server origin used in dev mode (the hot flag's written port overrides it) |
| `inertia.vite-dev-hot` | _(auto-scan)_ | Hot-flag discovery for dev mode: empty scans the working directory three levels deep plus one bounded parent level per front-end `node_modules`; `off` disables detection (recommended for production) |
| `inertia.vite-react-refresh` | `auto` | React Fast Refresh preamble in dev mode (`auto` probes `/@react-refresh`; `true`/`false` force it) |
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
| `withOnce(key, supplier, options)` | `Inertia::once()` parity: resolved a single time per client; remembered keys (`X-Inertia-Except-Once-Props`) skip it on full visits (`onceProps` metadata stays); `as()/until()/fresh()/deferred()/merged()/lazy()` compose |
| `withScroll(key, supplier, options)` | `Inertia::scroll()` parity: value wrapped `{data: [...]}`, merge label `<path>.<wrapper>`, cursor in `scrollProps` (request parameter or `metadata()` provider); `wrapper()/pageName()/page(current, hasMore)/deferred()` compose |
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
| Once props (`X-Inertia-Except-Once-Props`, `fresh`, `until`, `as`, compose) | ✅ |
| Scroll props (infinite scroll: `mergeProps` label + `scrollProps` cursor, reset, deferred) | ✅ |
| Flash + validation error bags over session | ✅ |
| `clearHistory` / `preserveFragment` / `encryptHistory` | ✅ |
| Root template (Thymeleaf, overridable) | ✅ |
| SSR (Node render server client + `@inertiaHead` fragment) | roadmap (módulo separado) |
| Quarkus integration (CDI + Undertow + RESTEasy over the shared glue) | ✅ |

## Building from source

```bash
# one-time per clone: enables the repository pre-commit hook
git config core.hooksPath .githooks

mvn test          # the full suite: 70 core + 36 servlet glue + 32 spring + 19 quarkus, 157 total
mvn install       # installs all modules locally (core, servlet glue, both adapters + demo apps)
```

The pre-commit hook (`mvn verify`) enforces three quality gates on every commit:

| Gate | Rule |
|---|---|
| **Spotless** | formatting check (`removeUnusedImports`, trailing whitespace, final newline) fails the validate phase — run `mvn spotless:apply` to auto-fix before committing |
| **JaCoCo** | **75% instruction and branch** coverage per bundle |
| **SpotBugs** | bug-pattern analysis at `effort Max` / `threshold Normal`; zero findings (justified exclusions live in `spotbugs-exclude.xml` with a rationale comment) |

Each behavioral rule was implemented and then verified against the actual sources of `inertia-laravel` and `@inertiajs/core` (v3.10) — see the test names in `inertia-core` for the mapping (`InertiaProtocolTest`).

## Credits

- [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) — the canonical server adapter; the behavioral reference for this port.
- [`inertia4j`](https://github.com/inertia4j/inertia4j) — the prior Java adapter; the SPI/core split that inspired the ports design here.
- [`@inertiajs/core`](https://github.com/inertiajs/inertia) sources + [inertia docs](https://inertiajs.com/docs/v2/the-protocol) — the client-side protocol contract.
