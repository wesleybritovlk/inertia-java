# Changelog

All notable changes of this project are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- **`inertia-core` and the web glue compile on Java 8**: the baseline aimed at reuse by
  legacy servlet containers and future Boot 2 adapters. `Prop`'s seven record kinds and
  the Vite settings records became hand-written parity classes (record-shaped
  `equals`/`hashCode`/`toString`), records resolve reflectively behind a `java.lang.Record`
  gate (`Records8`), remaining 9-17 syntax in the two modules was flattened — behavior,
  wire format and the public API are unchanged; the same test suite passes on JDK 17 and
  on the Boot 4 benchmark.
- **Documentation restructured around two stack guides**: `getting-started` became the
  concepts page (protocol flow, module map, root template, the any-stack front-end
  boilerplate, dev vs build mode) and two zero-to-package guides were added —
  `getting-started-spring` and `getting-started-quarkus` (pom, application/controller,
  front-end, dev loop, build mode, validation, packaging, SPA catch-all). The shared
  servlet glue gained its own module page (`inertia-servlet.md`) and a pure-servlet
  getting-started (`getting-started-servlet.md`) for Jakarta EE 9+ / plain Tomcat/Jetty
  deployments: bootstrap listener, protocol filter with the engine-scoping attachment
  hook, page servlets and WAR packaging. The README became a minimal card (pitch, module
  map, dependency snippet, doc links, build) like the inertia4j reference — the quick
  start, property/prop/serialization tables and the protocol-status matrix moved into
  the documentation (`docs/index.md` keeps the protocol status).
- **Quarkus engine ergonomics = Spring/laravel parity**: resources inject the engine
  directly — `@Inject Inertia inertia` (a request-scoped CDI bean produced by the
  adapter; CDI commands the client proxy) — replacing the `InertiaProvider` wrapper.
  The core `Inertia` class gains a protected no-arg constructor for the Arc proxying.
- **Root template override became optional and tiny**: the bundled layout inside
  `inertia-web` internalizes ALL the machinery (vite script/css, dev blocks, root
  mount, data-page payload). The optional override is a **head fragment** — title,
  meta, fonts, CDN scripts — injected into the bundled head (`inertiaUserHead`);
  a whole-document template remains supported for full control. Demos ship the
  two-line fragment.

### Added — `inertia-core`
- **Once props** and **Scroll props** (infinite scroll): protocol parity for the Inertia.js
  v3 once + scroll metadata, wire shapes verified against the v3 docs and the
  `@inertiajs` 3.10 client (composer options `as()/until()/fresh()/deferred()/merged()`
  and `page()/pageName()/wrapper()/metadata()/deferred()`), available on both adapters.

### Added — architecture
- **`inertia-servlet` module**: the framework-neutral Jakarta Servlet glue previously
  buried inside the Spring starter (request adapter, protocol filter, result/page
  writers, Thymeleaf/Vite root renderers, version/hot providers, bundled root template).
  The Spring starter delegates to it; every servlet-based stack can too.
- **`inertia-web` module**: the servlet-free web glue split out of `inertia-servlet` —
  root-template renderers (Thymeleaf/Vite), version providers, the hot-reload scanner
  and the bundled root template/head fragment — so every servlet-based stack shares
  exactly the same rendering machinery (now at Java 8 level); `inertia-servlet`
  keeps only the Jakarta Servlet-specific pieces and delegates the rest.

### Added — demos
- `inertia-demo` became an aggregator with **two adapter flavors sharing ONE copy of the
  canonical front-ends**: `inertia-demo-spring` (the Spring Boot app) and
  `inertia-demo-quarkus` (the Quarkus app). The three Vite front-ends (Vue 3, React 19,
  Svelte 5) build once into the shared `frontend-assets/` folder that both apps mount as
  classpath resources — one `npm run build` feeds both — and both run on the default
  port 8080 (they are never run together). Beans of `inertia-quarkus` are discoverable
  by external Quarkus applications via a `META-INF/beans.xml` bean-archive marker.

### Fixed — demo SPA + dev mode on both stacks
- The Svelte demo navbar used plain `<a href>` (`Link` was imported but never rendered):
  every page change was a full document load; it now uses inertia's `Link` (`/form-todo`
  navigates in-place like Vue/React).
- Quarkus page/response `Content-Type` negotiation now produces `text/html` /
  `application/json` correctly (the writers declare their `@Produces` and route the
  protocol result's own `Content-Type` through `setContentType`), instead of the
  octet-stream default RESTEasy applied when no provider declared media types.
- Hot-flag discovery covers the layout `quarkus:dev` runs in (fork from the module dir):
  the auto-scan also descends one bounded parent level (`../`); the Spring demo pins
  `spring-boot:run` at the repository root via the plugin's `workingDirectory`.
- Demo quarkus tests pin `inertia.vite-dev-hot=off` via a test profile so the suite
  cannot be broken by a live dev session on the develop machine (hot flags darken the
  asset version by design). Test isolation is now standard practice in both adapters.

### Added — `inertia-quarkus`
- Full Quarkus adapter over the same protocol and the same shared glue: CDI producers
  (version provider → hot-aware, hybrid serializer, Thymeleaf+Vite renderer), the protocol
  filter registered through Undertow (`@WebFilter` CDI bean with a request attachment that
  scopes the engine per request), typed RESTEasy `MessageBodyWriter`s for `InertiaPage`/
  `InertiaResult` returns, SmallRye config mapping mirroring every Spring `inertia.*`
  property, and a `ValidationErrors` helper on jakarta Bean Validation.

### Added — `inertia-spring-boot`
- **Vite dev mode (HMR)** parity with the Laravel `artisan serve + npm run dev` loop:
  while the hot flag file (`.hot-inertia`, written by the shipped vite plugin) exists,
  pages render with the Vite client + source entry module; React Fast Refresh preamble
  via `inertia.vite-react-refresh`; dev URL and flag file configurable
  (`inertia.vite-dev-url`, `inertia.vite-dev-hot`).
- Built-in Vite asset support: `inertia.vite-manifest` once wires the `@vite` equivalent
  renderer (`viteAppJs`/`viteCss`, entry auto-detection via `"isEntry"`, lazy-chunk css)
  and, by default, the asset version (manifest SHA-256).

## 0.1.0-SNAPSHOT — 2026-10-31

Initial protocol engine and Spring Boot integration.

### Added — `inertia-core`
- Request-scoped `Inertia` engine: `render`, `redirect`, `location`, `flash`, shared props.
- Laravel-faithful page resolution: Always/Lazy/Deferred (groups + rescue)/Merge props,
  partial reload dot-path matching (exact, descendant, lead-to; `except` over `only`),
  container subtree cuts, merge metadata (`mergeProps`, `prependProps`, `deepMergeProps`,
  `matchPropsOn`), `deferredProps` per group, `rescuedProps`, `sharedProps` truncation,
  `clearHistory`/`preserveFragment`/`encryptHistory` flags, `flash` and validation error bags.
- Middleware-equivalent rules usable by any adapter: asset-version guard (409 +
  `X-Inertia-Location` + `X-Inertia-Version`), `Vary: X-Inertia`, 302→303 on
  PUT/PATCH/DELETE/QUERY, fragment redirects escalated to 409 + `X-Inertia-Redirect`.
- Dependency-free `PlainJsonPageSerializer` plus the `PageSerializer` SPI.
- Ports: `InertiaRequest`, `InertiaFlashStore`, `InertiaResponseShell`, `VersionProvider`.

### Added — `inertia-spring-boot`
- `@AutoConfiguration` compatible with Spring Boot 3.x and 4.x (Jakarta Servlet 6.x).
- Request-scoped `Inertia` bean (no thread-locals inside the core), shared props customizers.
- `InertiaFilter`: version guard before the chain, `Vary`/303/fragment upgrades after it.
- Return value support: custom handler for plain `@Controller` methods and a first-in-class
  `HttpMessageConverter` for `@RestController` methods (annotation-based processors run
  first by design in Spring MVC).
- Isolated Thymeleaf root template renderer with a bundled `@inertia`-equivalent template,
  overridable via `inertia.template-path`.
- `ValidationErrors.store` for Spring MVC `BindingResult`, persisted through the session flash.
- Hybrid serialization: user bean → application-provided mapper → classpath Jackson (2/3) →
  bundled serializer.

### Changed
- `MergeOptions` follows the exact inertia-laravel `MergesProps` trait semantics: one root
  family flag (`append`, flipped to prepend by `prepend()`), `appendAt/prependAt` sub-path
  lists independent of the flag, deep-merge priority over both.
- Mutable-exposure fixes flagged by SpotBugs at `effort Max`: unmodifiable views for the
  page view data and result headers, defensive list copies inside the `Merge` prop record,
  `Boolean.TRUE.equals(...)` comparisons for flash flags.

### Tooling
- Spotless (formatting gate), SpotBugs (`effort Max`/`threshold Normal`) and JaCoCo
  coverage (75% instruction/branch per bundle) wired into the Maven build; repository
  pre-commit hook runs `mvn verify on every commit (enable with
  `git config core.hooksPath .githooks`).
- PMD dead-code gate (PMD 7 via `maven-pmd-plugin`): the unused-declaration rules
  proven absent from the SpotBugs analysis (a never-called private method passed a
  `threshold low`/`effort max` SpotBugs run) — unused private methods/fields and local
  variables, main sources of the published modules, added to the same verify/pre-commit
  chain.
- Starter unit tests for version hashing, template overrides, validation bag merging and
  writer/converter branches (67 total tests).
