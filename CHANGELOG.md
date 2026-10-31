# Changelog

All notable changes of this project are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
