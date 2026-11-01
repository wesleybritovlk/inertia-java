# The servlet glue (inertia-servlet)

`inertia-servlet` is the framework-neutral Jakarta Servlet layer of the project: ALL the
request/response machinery lives here, and both shipped adapters
([inertia-spring-boot](getting-started-spring.md), [inertia-quarkus](getting-started-quarkus.md))
are thin configuration layers on top of it. Any servlet-based stack — **plain Tomcat,
Jetty, Undertow, Jakarta EE 9+ WAR applications** — reuses the same glue with zero
framework code, via [the pure-servlet guide](getting-started-servlet.md).

## What the module contains

| Class | Role |
|---|---|
| `InertiaFilter` | The protocol middleware: asset-version guard **before** the chain (stale GET → `409` + `X-Inertia-Location`), `Vary: X-Inertia` and redirect upgrades **after** it (`302 → 303` on PUT/PATCH/DELETE, fragment → `409` + `X-Inertia-Redirect`). A plain `jakarta.servlet.Filter`. |
| `InertiaFilter.InertiaRequestAttachment` | Optional hook the filter calls per request — `bind(request, response, adapter)` / `unbind(...)` — so the host can scope the engine per request (the Quarkus module builds its CDI engine here). |
| `ServletInertiaRequest` | The `InertiaRequest` port over `HttpServletRequest` (path + query string, headers, visit detection) — **including the session-backed `InertiaFlashStore`** (flash data, error bags, page flags ride `HttpSession`). |
| `InertiaPageWriter` | The one-call response writer: give it an `InertiaPage` (it resolves the page and renders JSON for Inertia visits / the root template for browsers) or an `InertiaResult` (redirects, locations) — headers, status and body are written for you. |
| `InertiaResultWriter` | Writes bare `InertiaResult`s (filter-level results) without rendering. |
| `ThymeleafInertiaRootTemplateRenderer` | The root template renderer over an isolated Thymeleaf engine: the bundled `META-INF/inertia/templates/root.html` layout (or your override path) with the `data-page` payload (`@inertia` Blade equivalent). |
| `ViteInertiaRootTemplateRenderer` | Wraps the Thymeleaf renderer with the `@vite` equivalent: build mode resolves the hashed entry + lazy-chunk css from the manifest; dev mode (hot flag present) injects the Vite client + source entry + React preamble. Configured with a `ManifestSource(manifestLocation, entry, base)` and a `HotMode(hotFiles, devUrl, entry, reactRefresh)`. |
| `FileVersionProvider` | Asset version = SHA-256 of a file (the Vite manifest). |
| `HotAwareVersionProvider` | Wraps a source and goes **dark** (version `null`) while the hot flag exists — Laravel parity: no versioned `409`s during HMR. |
| `HotFileScanner` | Locates the `.hot-inertia` flags (auto-scan three levels inside the working directory + one bounded parent level, per front-end `node_modules`; `off` and explicit paths supported). |
| `InertiaResources` | Location reader for `classpath:` (jar-safe, cwd-independent) and filesystem paths. |

## What stays out of it

The module never touches configuration, dependency injection or validation frameworks —
that is the adapters' job:

| Concern | Spring Boot adapter | Quarkus adapter | Pure servlet (you) |
|---|---|---|---|
| Config (`inertia.*`) | `InertiaProperties` | SmallRye `@ConfigMapping` | your own constants/file |
| Engine scoping | request-scoped bean | CDI attachment + `@Inject` | the filter's attachment hook (~10 lines) |
| Return-value bridging | MVC handler + converter | JAX-RS writers | one `writer.write(page, response)` call |
| Validation helper | `ValidationErrors.store(BindingResult)` | `ValidationErrors.store(violations)` | flash the `FlashKeys.ERRORS` map directly |

Serializer rule in raw usage: construct the engine with `PlainJsonPageSerializer`
(zero-dependency, bundled) or wrap your Jackson via `ReflectiveJacksonPageSerializer.over(mapper)`.

Deeper behaviors (prop types, partials, redirects, flash) are engine-level and documented
in their own pages — identical across every stack.
