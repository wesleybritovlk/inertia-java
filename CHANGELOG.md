# Changelog

All notable changes of this project are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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

### Added — `inertia-quarkus`
- Full Quarkus adapter over the same protocol and the same shared glue: CDI producers
  (version provider → hot-aware, hybrid serializer, Thymeleaf+Vite renderer), the protocol
  filter registered through Undertow (`@WebFilter` CDI bean with a request attachment that
  scopes the engine per request), typed RESTEasy `MessageBodyWriter`s for `InertiaPage`/
  `InertiaResult` returns, SmallRye config mapping mirroring every Spring `inertia.*`
  property, and a `ValidationErrors` helper on jakarta Bean Validation.

### Added — `inertia-spring-boot-starter`
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

### Added — `inertia-spring-boot-starter`
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
- Starter unit tests for version hashing, template overrides, validation bag merging and
  writer/converter branches (67 total tests).
