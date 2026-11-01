# Getting started — the concepts

`inertia-java` is the server adapter of the [Inertia.js](https://inertiajs.com) protocol
for the JVM. Your controllers return **page objects**, the client-side router renders
them, and plain HTTP carries everything — no API layer, no server hosted by the library.
The adapter is a plain, registerable bean: **Spring Boot MVC** and **Quarkus** are the
shipped integrations, with Thymeleaf playing the role of Laravel's Blade.

This page explains how everything fits together and owns the front-end boilerplate
(identical for every stack). The per-stack guides then take a **new project** from an
empty folder to a packaged app:

- [Getting started with Spring Boot](getting-started-spring.md)
- [Getting started with Quarkus](getting-started-quarkus.md)
- A ready-to-run proof of both flavors lives in the [demo tree](../inertia-demo/README.md)

## How an Inertia request flows

1. **Before the handler** — the protocol filter compares `X-Inertia-Version` against the
   current asset version. A stale browser visit gets `409` + `X-Inertia-Location` so the
   client performs a full page visit with fresh assets.
2. **Your controller** — builds an `InertiaPage` (`inertia.render("Users/Index")` plus
   props). The request-scoped engine resolves props against the request: partial
   filtering, deferred groups, merge metadata, shared props, flash and error bags.
3. **The response** — two shapes, same URL:
   - a plain browser visit receives the **root HTML page**: the layout (bundled inside
     the library) embeds the page object in a `<script data-page="app" type="application/json">`
     block — the `@inertia` Blade directive equivalent — and loads your app script;
   - an `X-Inertia: true` visit from the Inertia client receives the **page object as
     JSON** (`component`, `props`, `url`, `version` + optional metadata) with
     `X-Inertia: true` + `Vary: X-Inertia` headers.
4. **After the handler** — the filter adds `Vary: X-Inertia` and upgrades redirects
   (`302 → 303` on PUT/PATCH/DELETE, fragment redirects to `409` + `X-Inertia-Redirect`).

The client never re-renders the whole document after boot: navigation happens through
its own router, and the server answers each visit with just the page object.

## The modules

| Module | What it is |
|---|---|
| `inertia-core` | The protocol engine. **Zero dependencies** (JDK 17 only): page/props resolution, partial matching, redirects, version guard, the serializer SPI. |
| `inertia-servlet` | The shared Jakarta Servlet glue: request adapter, protocol filter, result/page writers, the Thymeleaf + Vite root renderers, version/hot providers and the bundled root template. |
| `inertia-spring-boot` | The Spring Boot integration (3.x and 4.x): auto-configuration, request-scoped `Inertia` bean, return-value bridges, properties. |
| `inertia-quarkus` | The Quarkus integration: CDI wiring over the same glue, Undertow registration, RESTEasy writers, SmallRye config. |
| `inertia-demo` | A runnable demo (Hello World + CRUD Todo) with three canonical front-ends served by both back-ends. |

Protocol details live in their own pages — every prop builder in
[pages-and-props.md](pages-and-props.md), the partial rules in
[partial-reloads.md](partial-reloads.md), redirects/versioning in
[redirects-and-versioning.md](redirects-and-versioning.md), flash and validation in
[flash-and-validation.md](flash-and-validation.md), shared props in
[shared-props.md](shared-props.md), JSON binding in [serialization.md](serialization.md).

## The root template (usually you do not need one)

The layout ships **inside the library** (`inertia-servlet`): the head machinery (Vite
entry script, lazy-chunk css, dev-mode blocks), the root `<div id="app">` mount and the
`<script data-page>` payload all live in the bundled
`META-INF/inertia/templates/root.html`. No template file, no boilerplate: the served
page already works in build mode AND dev mode.

When the application wants its own head bits (title, meta, fonts, CDN scripts), ship a
**head fragment** — one or two lines, nothing else — at
`src/main/resources/templates/inertia.html`:

```html
<title>Task Log</title>
<!-- fonts / CDN libraries / meta tags, as plain head content: -->
<!-- <link rel="stylesheet" href="https://cdn.example/fonts.css" /> -->
```

The fragment (detected because it does not compose a full `<html>` document) renders
with the same variables as the layout and is injected inside the bundled `<head>` —
the machinery stays untouched. Advanced escape hatch: a **whole document** (a file with
`<html>`) replaces the bundled layout and wires the machinery variables itself.

Variables available in both modes:

| Variable | Meaning |
|---|---|
| `inertiaPageJson` | The serialized page object (already tag-safe escaped) |
| `inertiaRootId` | The `inertia.root-id` property value |
| `inertiaUserHead` | The fragment's processed output (empty when absent) |
| `viteDevMode` / `viteClient` / `viteAppJs` / `viteCss` / `viteReactRefresh` | The Vite wiring (dev client, hashed entry, chunks css, react preamble) |
| *any extra* | Whatever your controller added with `InertiaPage.withViewData(...)` |

After hydration the client replaces the tab title through its own `<Head>` component
(per framework), so the fragment's `<title>` only paints the pre-hydration instant.
Full details: [configuration.md](configuration.md#root-template-override).

## The front-end boilerplate (any stack)

The front-end is a standard [Vite](https://vite.dev) application — the same files,
commands and layout for Spring Boot and Quarkus, and the same structure for Vue, React
or Svelte. For a new project:

```
your-app/
├── pom.xml                       # your stack's build (per-stack guide)
├── src/main/resources/
│   └── static/                   # ← npm run build lands here (hashed assets + manifest)
├── src/main/java/...             # your controllers (per-stack guide)
└── src/frontend/
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── app.js                # the @inertiajs entrypoint
        └── Pages/
            └── Hello/Index.vue   # one folder per component name
```

Install (Vue variant; React: `@inertiajs/react` + `@vitejs/plugin-react` + `app.jsx`;
Svelte: `@inertiajs/svelte` + `@sveltejs/vite-plugin-svelte` + `.svelte` pages):

```bash
npm init -y
npm i vite @vitejs/plugin-vue @inertiajs/vue3 vue
```

The only non-standard bit is the **hot-file plugin** — small enough to be copied
**inline** into `vite.config.js`. It is what makes dev mode work (next section):

```js
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import fs from 'node:fs';

// .hot-inertia = the flag the Java adapter reads PER RENDER to switch the root
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
    server: { cors: { origin: 'http://localhost:8080' } }, // where your back-end listens
    build: {
        manifest: 'manifest.json',                     // drives hashing + versioning
        outDir: '../src/main/resources/static',        // the back-end serves this folder
        emptyOutDir: true,
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
```

The entrypoint is the canonical `@inertiajs` setup (Vue shown):

```js
import { createApp, h } from 'vue';
import { createInertiaApp } from '@inertiajs/vue3';

createInertiaApp({
    id: 'app', // must match inertia.root-id (default: app)
    resolve: (name) => import(`./Pages/${name}.vue`),
    setup({ el, App, props, plugin }) {
        createApp({ render: () => h(App, props) }).use(plugin).mount(el);
    },
});
```

No path pairing is ever needed between the plugin and the back-end: the adapter
**auto-scans** for `node_modules/.hot-inertia` around its working directory. If you
maintain several projects, the same plugin ships as a reusable package
(`npm/inertia-vite-hot-file` in this repository,
`@wesleybritovlk/inertia-vite-hot-file` once published).

## Dev mode vs build mode

The two programs — your JVM process and the Vite dev server — talk through one cheap
channel: the flag file the plugin above writes.

- **Dev mode** (`npm run dev` running): the adapter detects the flag per render and
  serves pages with the Vite client + your **source** entry — HMR socket live, edits
  appear without reloads. The page version goes `null` (Laravel parity: no versioned
  `409`s while HMR owns the changes; nothing needs a prior build).
- **Build mode** (no flag): the adapter resolves the hashed assets of the last
  `npm run build` from the manifest and hashes that manifest into the asset version —
  stale clients get the standard `409` full-page-visit flow.

The same back-end serves both; nothing changes in code or properties. The full loop —
commands, React Fast Refresh specifics, security notes — is in
[dev-workflow.md](dev-workflow.md). In production (and native builds), set
`inertia.vite-dev-hot=off` to disable detection entirely.

## Pick your stack

- [Spring Boot 3.x / 4.x — from empty folder to packaged app](getting-started-spring.md)
- [Quarkus — from empty folder to packaged app](getting-started-quarkus.md)
