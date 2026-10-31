# Documentation index

`inertia-java-bean` is a framework-agnostic server adapter for [Inertia.js](https://inertiajs.com).
It never hosts a server: integrations register it as a plain bean (Spring Boot MVC today,
Quarkus via the same SPI later), and the core manipulates only the HTTP request/response
exchange and the HTML root page — the same architecture as [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel),
with Thymeleaf playing the role of Laravel's Blade.

## Start here

| Document | What it covers |
|---|---|
| [Getting started (Spring Boot)](getting-started.md) | Everything needed to run Inertia for the first time: dependencies, a working controller, the root template, the front-end entrypoint, validation flow, and a catch-all for SPA routes |
| [Pages and props](pages-and-props.md) | `inertia.render`, every prop builder (`with`, `withLazy`, `withAlways`, `withDeferred`, `withDeferredRescued`, `withMerged`), dot-key nesting and `withViewData` |
| [Partial reloads](partial-reloads.md) | `X-Inertia-Partial-*` semantics, subtree cutting inside object props, deferred groups, merge metadata and `X-Inertia-Reset` |
| [Redirects and versioning](redirects-and-versioning.md) | Internal 302/303 rules, external `X-Inertia-Location` 409s, fragment redirects, and the asset-version guard |
| [Flash and validation](flash-and-validation.md) | One-shot session flash, `ValidationErrors.store`, error-bag renaming, and page flags (`clearHistory`, `preserveFragment`) |
| [Shared props](shared-props.md) | Request-scoped shared props, customizer beans, `sharedProps` page metadata |
| [Serialization](serialization.md) | The hybrid serializer chain (user bean → application mapper → Jackson 2/3 → bundled zero-dependency writer) and the `PageSerializer` SPI |
| [Configuration](configuration.md) | Every `inertia.*` property, root template override, Thymeleaf variables, filter ordering |
| [Other frameworks](other-frameworks.md) | Running the core without Spring: the request/response ports, a raw-servlet example, and what the Quarkus module will plug into |
| [Differences from inertia-laravel](differences-from-inertia-laravel.md) | Every behavior that is not a 1:1 port, and why |

## The adapter in one request

1. **Before the handler** — the filter compares `X-Inertia-Version` against the current asset version; a stale GET is answered with `409` + `X-Inertia-Location` so the client performs a full page visit (fresh assets).
2. **Your controller** — returns an `InertiaPage` (or `InertiaResult` for redirects). The request-scoped engine resolves props against the request: partial filtering, deferred groups, merge metadata, shared props, flash and error bags.
3. **Response** — `X-Inertia` visits answer `application/json` with the page object and `X-Inertia: true`; other visits render the root Thymeleaf template with the page embedded in a `<script data-page="app" type="application/json">` block (the `@inertia` Blade equivalent).
4. **After the handler** — the filter adds `Vary: X-Inertia`, upgrades redirect statuses (302 → 303 for method changes, fragment redirects to `409` + `X-Inertia-Redirect`).

## Non-Servlet environments

The core is pure Java 17 with zero dependencies. [other-frameworks.md](other-frameworks.md)
documents the two ports you implement (`InertiaRequest` plus a flash store) and how to turn
raw requests into Inertia responses in ~10 lines.
