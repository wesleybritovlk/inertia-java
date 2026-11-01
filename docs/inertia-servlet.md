# The servlet glue (inertia-servlet)

`inertia-servlet` is the framework-neutral Jakarta Servlet layer of the project: ALL the
transport machinery lives here (protocol filter, request adapter, result/page writers),
and both shipped adapters
([inertia-spring-boot](getting-started-spring.md), [inertia-quarkus](getting-started-quarkus.md))
are thin configuration layers on top of it. The **document machinery — Thymeleaf + Vite
root renderers, version/hot providers, the bundled `root.html` template — lives in
[inertia-web](inertia-web.md)** (Java 8, servlet-free); any servlet-based stack —
**plain Tomcat, Jetty, Undertow, Jakarta EE 9+ WAR applications** — reuses the whole
chain with zero framework code, via [the pure-servlet guide](getting-started-servlet.md).

## What the module contains

| Class | Role |
|---|---|
| `InertiaFilter` | The protocol middleware: asset-version guard **before** the chain (stale GET → `409` + `X-Inertia-Location`), `Vary: X-Inertia` and redirect upgrades **after** it (`302 → 303` on PUT/PATCH/DELETE, fragment → `409` + `X-Inertia-Redirect`). A plain `jakarta.servlet.Filter`. |
| `InertiaFilter.InertiaRequestAttachment` | Optional hook the filter calls per request — `bind(request, response, adapter)` / `unbind(...)` — so the host can scope the engine per request (the Quarkus module builds its CDI engine here). |
| `ServletInertiaRequest` | The `InertiaRequest` port over `HttpServletRequest` (path + query string, headers, visit detection) — **including the session-backed `InertiaFlashStore`** (flash data, error bags, page flags ride `HttpSession`). |
| `InertiaPageWriter` | The one-call response writer: give it an `InertiaPage` (it resolves the page and renders JSON for Inertia visits / the root template for browsers) or an `InertiaResult` (redirects, locations) — headers, status and body are written for you. Root-page rendering is whatever `InertiaRootTemplateRenderer` you hand it — the bundled [inertia-web](inertia-web.md) renderers behind the adapters. |
| `InertiaResultWriter` | Writes bare `InertiaResult`s (filter-level results) without rendering. |

## What comes from inertia-web

The root-page machinery below is **not in this module** — it is the Java-8,
servlet-free [inertia-web](inertia-web.md) layer that this glue (and every other
servlet-based stack) reuses unchanged: `ThymeleafInertiaRootTemplateRenderer` (the
bundled `root.html` layout with the `data-page` payload), `ViteInertiaRootTemplateRenderer`
(the `@vite` equivalent: manifest hashing, hot-flag dev mode, React preamble),
`FileVersionProvider` (SHA-256 of the manifest), `HotAwareVersionProvider` (version goes
`null` during HMR — Laravel parity), `HotFileScanner` (`.hot-inertia` flag discovery) and
`InertiaResources` (jar-safe `classpath:` reading).

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
