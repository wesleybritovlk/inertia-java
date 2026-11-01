# inertia-java

Server-side [Inertia.js](https://inertiajs.com) adapter for the Java ecosystem, built the
way [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) is: controllers return
page objects, the protocol carries them over plain HTTP, and the client-side router
renders — no API layer, no embedded server. The library is a plain, registerable bean
that manipulates only the HTTP exchange and the HTML root page, with Thymeleaf playing
the role of Laravel's Blade.

Works on **Spring Boot 3.x and 4.x**, **Quarkus**, and **any Jakarta EE 9+ servlet
container** (plain Tomcat/Jetty included) — including GraalVM-native-ready Boot 4 with
Jackson 3, the case that breaks other Java adapters.

## Modules

| Artifact | Purpose |
|---|---|
| `inertia-core` | The Inertia protocol engine. **Zero dependencies** (Java 8+). |
| `inertia-web` | Framework-neutral web glue (no servlet dependency): Thymeleaf + Vite renderers, version/hot providers, bundled root template — Java 8+. |
| `inertia-servlet` | Jakarta Servlet glue: protocol filter, request adapter, writers (delegates to `inertia-web`). |
| `inertia-spring-boot` | Spring Boot integration (3.x and 4.x). |
| `inertia-quarkus` | Quarkus integration (Undertow + RESTEasy + CDI). |
| `inertia-demo` | Runnable demo: three canonical Vite front-ends (Vue 3, React 19, Svelte 5) served by both back-ends. |

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

(Quarkus: `inertia-quarkus` · raw servlet/Jakarta EE: `inertia-servlet`.)

## Documentation

| Doc | Topic |
|---|---|
| [Getting started — concepts](docs/getting-started.md) | the protocol, the modules, the root template, the front-end boilerplate (any stack), dev vs build mode |
| [Getting started (Spring Boot)](docs/getting-started-spring.md) | zero-to-package: pom, controller, front-end, dev loop, validation |
| [Getting started (Quarkus)](docs/getting-started-quarkus.md) | the same path on Quarkus (`@Inject Inertia`, `quarkus:dev`) |
| [Getting started (pure servlet)](docs/getting-started-servlet.md) | WAR on any Jakarta EE 9+ container |
| [The servlet glue](docs/inertia-servlet.md) | the `inertia-servlet` module, class by class |
| [Development workflow](docs/dev-workflow.md) | `npm run dev` + the back-end (HMR, Fast Refresh, hot flag) |
| [Pages and props](docs/pages-and-props.md) | every prop builder: lazy, deferred, merged, once, scroll |
| [Partial reloads](docs/partial-reloads.md) | the `X-Inertia-Partial-*` protocol |
| [Redirects and versioning](docs/redirects-and-versioning.md) | 303/409 rules, asset-version guard |
| [Flash and validation](docs/flash-and-validation.md) | session flash, error bags |
| [Shared props](docs/shared-props.md) | request-scoped shared props |
| [Serialization](docs/serialization.md) | Jackson 2/3 hybrid chain + the `PageSerializer` SPI |
| [Configuration](docs/configuration.md) | every `inertia.*` property and bean override |
| [Quarkus adapter](docs/inertia-quarkus.md) | CDI wiring, config mapping, overrides |
| [Other frameworks](docs/other-frameworks.md) | the core ports without any servlet layer |
| [Differences from inertia-laravel](docs/differences-from-inertia-laravel.md) | every behavior that is not a 1:1 port |

A runnable proof of the whole flow lives in [`inertia-demo/`](inertia-demo/README.md).

## Building from source

```bash
git config core.hooksPath .githooks   # one-time: enables the pre-commit gates
mvn test                              # the full suite (157 tests)
mvn install                           # installs all modules locally
mvn verify -Pboot4                    # the whole reactor on Spring Boot 4 (Jackson 3)
```

The pre-commit hook enforces Spotless formatting, 75% instruction/branch coverage
(JaCoCo) and zero SpotBugs findings (`effort Max`) on every commit.

Each behavioral rule was implemented and then verified against the actual sources of
`inertia-laravel` and `@inertiajs/core` (v3.10) — see the test names in `inertia-core`
(`InertiaProtocolTest`).

## Credits

- [`inertia-laravel`](https://github.com/inertiajs/inertia-laravel) — the canonical server adapter; the behavioral reference for this port.
- [`inertia4j`](https://github.com/inertia4j/inertia4j) — the prior Java adapter; the SPI/core split that inspired the ports design here.
- [`@inertiajs/core`](https://github.com/inertiajs/inertia) sources + [inertia docs](https://inertiajs.com/docs/v2/the-protocol) — the client-side protocol contract.
