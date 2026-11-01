# Documentation index

`inertia-java` is a framework-agnostic server adapter for [Inertia.js](https://inertiajs.com).
It never hosts a server: integrations register it as a plain bean — Spring Boot MVC and
Quarkus are shipped adapters, and the core manipulates only the HTTP request/response
exchange and the HTML root page — the same architecture as [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel),
with Thymeleaf playing the role of Laravel's Blade.

## Start here

| Document | What it covers |
|---|---|
| [Getting started — the concepts](getting-started.md) | How the Inertia protocol maps to this adapter: the request flow, the modules, the root template, the front-end boilerplate (any stack) and dev vs build mode |
| [Getting started (Spring Boot)](getting-started-spring.md) | A new project from an empty folder to a packaged app: pom, application class, controller, front-end, dev loop, build mode, validation, packaging |
| [Getting started (Quarkus)](getting-started-quarkus.md) | The same zero-to-packaged path on Quarkus: pom, controller with `@Inject Inertia`, config, dev loop, build mode, validation, packaging |
| [Development workflow](dev-workflow.md) | `npm run dev` + the back-end pairing (HMR): hot flag file, Vite client injection, Fast Refresh, spring-boot-devtools |
| [Pages and props](pages-and-props.md) | `inertia.render`, every prop builder (`with`, `withLazy`, `withAlways`, `withDeferred`, `withDeferredRescued`, `withMerged`, `withOnce`, `withScroll`), dot-key nesting and `withViewData` |
| [Partial reloads](partial-reloads.md) | `X-Inertia-Partial-*` semantics, subtree cutting inside object props, deferred groups, merge metadata and `X-Inertia-Reset` |
| [Redirects and versioning](redirects-and-versioning.md) | Internal 302/303 rules, external `X-Inertia-Location` 409s, fragment redirects, and the asset-version guard |
| [Flash and validation](flash-and-validation.md) | One-shot session flash, `ValidationErrors.store`, error-bag renaming, and page flags (`clearHistory`, `preserveFragment`) |
| [Shared props](shared-props.md) | Request-scoped shared props (`shared`/`sharedAlways`/`sharedOnce`), customizer beans, `sharedProps` page metadata |
| [Serialization](serialization.md) | The hybrid serializer chain (user bean → application mapper → Jackson 2/3 → bundled zero-dependency writer) and the `PageSerializer` SPI |
| [Configuration](configuration.md) | Every `inertia.*` property, root template override, Thymeleaf variables, filter ordering |
| [Quarkus adapter](inertia-quarkus.md) | `inertia-quarkus`: CDI wiring over the shared servlet glue, Undertow + RESTEasy, SmallRye config, validation, overrides |
| [Other frameworks](other-frameworks.md) | Running the core without Spring: the shared `inertia-servlet` glue, the request/response ports, a raw-servlet example |
| [Differences from inertia-laravel](differences-from-inertia-laravel.md) | Every behavior that is not a 1:1 port, and why |

## The adapter in one request

1. **Before the handler** — the filter compares `X-Inertia-Version` against the current asset version; a stale GET is answered with `409` + `X-Inertia-Location` so the client performs a full page visit (fresh assets).
2. **Your controller** — returns an `InertiaPage` (or `InertiaResult` for redirects). The request-scoped engine resolves props against the request: partial filtering, deferred groups, merge metadata, shared props, flash and error bags.
3. **Response** — `X-Inertia` visits answer `application/json` with the page object and `X-Inertia: true`; other visits render the root Thymeleaf template with the page embedded in a `<script data-page="app" type="application/json">` block (the `@inertia` Blade equivalent).
4. **After the handler** — the filter adds `Vary: X-Inertia`, upgrades redirect statuses (302 → 303 for method changes, fragment redirects to `409` + `X-Inertia-Redirect`).

## Try the demo

`inertia-demo/` is a runnable Hello World + CRUD Todo app in **two back-end flavors**
sharing your framework of choice without source duplication — canonical **Vite**
layouts for **Vue 3**, **React 19** and **Svelte 5** (hashed assets, asset versioning
live from the Vite manifest):

```bash
cd inertia-demo/frontend-demo-vue && npm install && npm run build   # or frontend-demo-react | frontend-demo-svelte
mvn install
mvn -pl :inertia-demo-spring spring-boot:run   # Spring Boot demo (Boot 3.3.12; -Pboot4 for 4.0.5)
mvn -pl :inertia-demo-quarkus quarkus:dev      # Quarkus demo (Undertow + RESTEasy)
# → http://localhost:8080
```

See [inertia-demo/README.md](inertia-demo/README.md) for the per-framework notes.

## Non-Servlet environments

The core is pure Java 17 with zero dependencies. [other-frameworks.md](other-frameworks.md)
documents the two ports you implement (`InertiaRequest` plus a flash store) and how to turn
raw requests into Inertia responses in ~10 lines.
