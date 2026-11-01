# Getting started (Spring Boot)

This guide builds a complete Inertia application with Spring Boot MVC: backed-up pages,
dev-time partial reloads and a validation round trip. Everything here works on **Boot 3.x
and 4.x** (Servlet 6.x, Thymeleaf 3.1).

## 1. Dependencies

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The starter brings:

- `inertia-core` (the protocol engine, zero third-party dependencies),
- `inertia-servlet` (the shared Jakarta Servlet glue: filter, request adapter, writers,
  Thymeleaf root template and the Vite/versioning providers — `org.thymeleaf:thymeleaf`
  rides only inside this glue; the core never sees it),
- a `FilterRegistrationBean`, a request-scoped `Inertia` bean, a return-value bridge for
  controllers, and the root template renderer.

Nothing else is required. If your application already uses Spring Boot's Jackson
(`spring-boot-starter-web` does), the starter reuses **your** mapper for serialization —
see [serialization.md](serialization.md).

## 2. Minimal application

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

`inertia` (lowercase `app` and version default to sane values; see
[configuration.md](configuration.md) for the complete list):

```yaml
inertia:
  version: b39c07f            # any value that changes when frontend assets change
  root-id: app                # must match the client-side createInertiaApp id
  filter-order: -2147483547   # optional: outermost by default, so security redirects also upgrade
```

> For real applications prefer `inertia.version-file` pointing at your build manifest
> (Vite/Rollup): its SHA-256 becomes the version, so stale assets trigger the standard
> 409 full-page-visit flow automatically.

## 3. Controller

Both `@Controller` and `@RestController` work. Convention here mirrors Laravel's
`Inertia::render` return style:

```java
@Controller
class DashboardController {

    private final Inertia inertia; // request-scoped bean from the starter

    DashboardController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/dashboard")
    InertiaPage dashboard() {
        return inertia.render("Dashboard/Index")
            .with("stats", () -> statsService.today())          // evaluated per response
            .withLazy("history", () -> statsService.history())  // partial reloads only
            .withDeferred("insights", () -> insights.calc());   // client resolves later
    }
}
```

What each method does per HTTP call:

- `GET /dashboard` from a browser (no `X-Inertia` header): the resolved page object is
  embedded into the root template and returned as `text/html;charset=UTF-8` with
  `Vary: X-Inertia`.
- `GET /dashboard` from the Inertia client (`X-Inertia: true`, `Accept:
  text/html, application/xhtml+xml`): `application/json` page object with
  `X-Inertia: true` + `Vary: X-Inertia` headers.
- `GET /dashboard` requesting `users` for the same component (`X-Inertia-Partial-Data:
  stats`, `X-Inertia-Partial-Component: Dashboard/Index`): only `stats` (plus the always
  and deferred-matching props) are evaluated and returned.

## 4. The root template

The bundled template is intentionally minimal — the payload hook with no frontend
scripts. Override it once your SPA has assets:

`src/main/resources/templates/inertia.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <script type="module" src="/assets/app.js"></script>
  </head>
  <body>
    <div th:id="${inertiaRootId}"></div>
    <script th:attr="data-page=${inertiaRootId}" type="application/json"
            th:utext="${inertiaPageJson}"></script>
  </body>
</html>
```

Variables available inside every render (yours or the bundled one):

| Variable | Meaning |
|---|---|
| `inertiaPageJson` | The serialized page object (already tag-safe escaped) |
| `inertiaRootId` | The `inertia.root-id` property value |
| *any extra* | Whatever your controller added with `InertiaPage.withViewData(...)` |

The `<script data-page>` block is what `@inertiajs/*` reads on boot — this is the
`@inertia` Blade directive equivalent. Keep exactly one, and keep `data-page` equal to
`root-id`.

## 4b. The vite.config.js boilerplate (includes dev mode)

Front-end production builds AND the live dev loop need the flag plugin; it is small
enough to be copied **inline** — this is the exact `vite.config.js` the demo uses
(Vue variant; React: `react()` + `'src/app.jsx'`; Svelte: `{ svelte }` + `.svelte`):

```js
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import fs from 'node:fs';

// .hot-inertia = the flag the Spring adapter reads PER RENDER to switch the root
// template to dev mode (HMR) - inertia-laravel public/hot parity; written on
// `npm run dev`, removed on shutdown (kill -9 leaves it: delete manually).
// node_modules/.hot-inertia never shows up in git status (node_modules is ignored).
const inertiaHotFile = () => ({
    name: 'inertia-hot-file',
    configureServer(server) {
        fs.writeFileSync('node_modules/.hot-inertia', String(server.config.server?.port ?? 5173));
        server.httpServer?.on?.('close', () => fs.rmSync('node_modules/.hot-inertia', { force: true }));
    },
});

export default defineConfig({
    plugins: [inertiaHotFile(), vue()],
    server: { cors: { origin: 'http://localhost:8800' } },
    build: {
        manifest: 'manifest.json',                    // drives hashing + versioning
        outDir: '../src/main/resources/static',       // served by Spring static handler
        emptyOutDir: true,
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
```

The flag writes `node_modules/.hot-inertia` and the Spring side auto-scans for it
(its `inertia.vite-dev-hot` default is empty = auto-scan), so no path pairing is
required at all — node_modules is git-ignored, so the flag never shows in `git status`
either. The dev loop then pairs `npm run dev` (writes the flag) with `spring-boot:run`
(answers pages in dev mode, HMR live) — details in
[dev-workflow.md](dev-workflow.md). If you maintain several projects, the same plugin
ships as a reusable package: `npm/inertia-vite-hot-file` in this repository
(`npm i @wesleybritovlk/inertia-vite-hot-file` once published). Exotic layouts only:
point the plugin at a custom path (`inertiaHotFile('../.hot-inertia')`) and mirror it
on the Spring side with the same `inertia.vite-dev-hot` value relative to the
`mvn` working directory — or turn detection off with `inertia.vite-dev-hot=off`.

## 5. Front-end entrypoint

Standard `@inertiajs` setup (Vue 3 example):

```js
import { createApp, h } from 'vue';
import { createInertiaApp } from '@inertiajs/vue3';

createInertiaApp({
  id: 'app', // must match inertia.root-id
  resolve: (name) => import(`./Pages/${name}.vue`),
  setup({ el, App, props, plugin }) {
    createApp({ render: () => h(App, props) })
      .use(plugin)
      .mount(el);
  },
}).then(({ resolveComponent }) => resolveComponent);
```

> Prefer the **live dev loop** (`npm run dev` + HMR while Spring serves the pages)? That
> is the documented [development workflow](dev-workflow.md) — one property, one hot-file
> flag, exactly the `artisan serve` + `npm run dev` pairing of the Laravel adapter.

## 6. SPA routing (catch-all endpoint)

When the SPA has client-side routes, back them with one server mapping so first visits
render the right component:

```java
@GetMapping("/{*path}")
InertiaPage anyPath(@PathVariable("path") String path) {
    return inertia.render(pageComponentFor(path))
        .with("path", path);
}
```

Register specific controllers as needed — the catch-all must not swallow them (Spring
resolves exact mappings before capture-all patterns, so explicit `@GetMapping("/users")`
always wins).

## 7. Validation round trip

```java
@PostMapping("/users")
InertiaResult create(UserForm form, BindingResult binding) {
    if (binding.hasErrors()) {
        ValidationErrors.store(inertia, binding, "default");
        return inertia.redirect("/users/create");
    }
    service.create(form);
    return inertia.redirect("/users");
}
```

On the redirect target next render the `errors` prop carries the bag; the client
resolves it (Laravel-compatible shape). Detailed behaviors — bag renaming through
`X-Inertia-Error-Bag`, first-message-per-field defaults, persistence across redirects —
are in [flash-and-validation.md](flash-and-validation.md).

## 8. Running

```bash
mvn install        # installs the (local) artifacts
mvn spring-boot:run -f your-app-module
```

Quality gates run on every commit once you enable the repository hook:

```bash
git config core.hooksPath .githooks
```

`mvn verify` then enforces Spotless formatting, JaCoCo coverage (75% instruction/branch)
and a zero-findings SpotBugs analysis (effort Max). `mvn spotless:apply` fixes formatting
before a commit.

## 9. What to read next

- Every prop builder: [pages-and-props.md](pages-and-props.md)
- The redirect/version behavior: [redirects-and-versioning.md](redirects-and-versioning.md)
- Running this outside Spring Boot: [other-frameworks.md](other-frameworks.md)
- What is *not* a 1:1 port of the Laravel adapter: [differences-from-inertia-laravel.md](differences-from-inertia-laravel.md)
