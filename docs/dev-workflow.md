# Development workflow (npm run dev + Boot)

The most important loop of an Inertia application: the front-end served by the **Vite
dev server** (Live Reload/HMR) while the **Spring Boot process** serves the pages. The
starter recognizes dev mode exactly the way the Laravel adapter does — by the presence
of a hot flag file (the adapter equivalent of Laravel's `public/hot`, named here
`.hot-inertia`).

> Back-end-side hot reloading (spring-boot-devtools) is orthogonal — see
> [§ spring-boot-devtools](#spring-boot-devtools-back-end).

## How it works

```
 ┌── Terminal 1 ─────────────────┐   ┌── Terminal 2 ─────────────────────────────┐
 │  npm run dev                  │   │  mvn -pl inertia-demo-spring spring-boot:run     │
 │  (Vite on :5173, HMR)         │   │  (Tomcat on :8080)                        │
 │  writes .hot-inertia on boot, │   │                                           │
 │  deletes it on shutdown       │   │  per render: .hot-inertia exists?         │
 └───────────────────────────────┘   │    yes → dev mode (modules from :5173)    │
                                     │    no  → build mode (hashed manifest)     │
                                     └───────────────────────────────────────────┘
```

- **Dev mode page**: the root template gets the Vite HMR client
  (`http://localhost:5173/@vite/client`), the **source** entry module
  (`http://localhost:5173/src/app.js`) and — for React — the Fast Refresh preamble. The
  browser therefore loads your *source files* transformed on the fly, HMR socket opened.
  **The page version is `null`** in dev mode (the hot-aware version provider goes dark —
  no versioned `409`s for pages driven by HMR; nothing needs a prior `npm run build`).
- **Build mode page**: the manifest-driven hashed assets of the last `npm run build`
  plus the SHA-256 version guard (stale visits get `409` + full reload).
- The same back-end serves both without changing any code — only the hot file changes
  hands between the two workflows. The `@vite client` establishes the HMR socket to the
  dev server even though the HTML is rendered by Spring (`localhost:5173` origin).

## The hot file (inline in the boilerplate vite.config.js)

The dev loop needs something to tell the Spring process that the Vite dev server is
running — the Laravel adapter equivalent of `laravel-vite-plugin` writing `public/hot`.
The canonical way here is the **inline plugin** inside the boilerplate
[`vite.config.js`](getting-started.md#4b-the-viteconfigjs-boilerplate-includes-dev-mode)
(~10 lines, zero dependencies, written on dev boot, removed on shutdown):

```js
const inertiaHotFile = () => ({
    name: 'inertia-hot-file',
    configureServer(server) {
        fs.writeFileSync('node_modules/.hot-inertia', String(server.config.server?.port ?? 5173));
        server.httpServer?.on?.('close', () => fs.rmSync('node_modules/.hot-inertia', { force: true }));
    },
});
```

Default location **`node_modules/.hot-inertia`**: `node_modules` is universally
git-ignored, so the flag never shows in `git status` and cannot leak into a packaged
Java artifact. The Spring side needs nothing: the empty `inertia.vite-dev-hot` default
**auto-scans** the working directory for the flag inside each project's own
`node_modules` (up to three levels deep), so `mvn` may run from the repository root,
from the demo folder or anywhere else — both sides are already paired. Set
`inertia.vite-dev-hot` to one or more comma-separated paths (relative to the working
directory) only for exotic layouts (e.g. one shared flag file for the whole repo), and
`inertia.vite-dev-hot=off` to disable dev-mode detection entirely. For teams maintaining
several projects, the same plugin ships as a reusable npm package in this repository
(`npm/inertia-vite-hot-file`, `@wesleybritovlk/inertia-vite-hot-file` — `npm i` +
`inertiaHotFile()`), consumable from any front-end to avoid copy-paste drift.

It writes `.hot-inertia` on dev server boot and deletes it when the process closes
gracefully. If a dev process dies hard (`kill -9`), remove the file manually.

## Configuration (all under `inertia.*`)

| Property | Default | Meaning |
|---|---|---|
| `inertia.vite-dev-hot` | _(auto-scan)_ | Hot flag detection. Empty (default) scans the working directory for `node_modules/.hot-inertia` (per front-end, three levels deep). One or more comma-separated paths override the scan; the value `off` disables dev-mode detection entirely |
| `inertia.vite-dev-url` | `http://localhost:5173` | Vite dev server origin |
| `inertia.vite-entry` | _(empty)_ | Dev entry source path (e.g. `src/app.js`); empty probes `src/app.js` then `src/app.jsx` against the dev server on first render |
| `inertia.vite-react-refresh` | `auto` | React **Fast Refresh** preamble in dev mode. `auto` probes the dev server for `/@react-refresh` (only `@vitejs/plugin-react` mounts it) — the React front needs no property; `true`/`false` force it on and off |
| `inertia.vite-manifest` | _(empty)_ | Build manifest — used in build mode (and as version source when nothing else is configured) |

## Running the demo

Two terminals, any front-end folder:

```bash
# terminal 1 (front-end, HMR)
cd inertia-demo/frontend-demo-vue      # demo-react | demo-svelte
npm install && npm run dev             # writes node_modules/.hot-inertia

# terminal 2 (back-end — may run from the repo root or demo dir, detection is automatic)
mvn -pl inertia-demo-spring spring-boot:run   # → http://localhost:8080
```

React specifics (Fast Refresh):

```bash
# react dev needs NO extra arguments — the preamble is auto-detected when the
# react dev server is up (@vitejs/plugin-react mounts /@react-refresh);
# force it with --inertia.vite-react-refresh=true if you prefer explicitness
mvn -pl inertia-demo-spring spring-boot:run
```

Everything validated live (Boot 3.3.12): Vue page updated through HMR without reload,
React page updated through Fast Refresh, Svelte page updated through HMR.

## spring-boot-devtools (back-end)

Adding `spring-boot-devtools` to your application gives the back-end its live-reload
(compact auto-restart on classpath changes) that pairs with the front HMR loop:

`mvn spring-boot:run` picks up template/resource changes automatically inside an IDE
(Ctrl+F9); a full JVM restart is never needed for front-end edits because the hot-mode
renderer resolves assets per render.

## Security notes

- Choose the dev origins intentionally (`server.cors` in the front config) — only the
  port your Spring app serves from.
- Keep dev mode out of production builds: the hot file only exists while `npm run dev`
  runs; CI images never contain it.

## Differences vs the Laravel adapter

- Flag file name: Laravel writes `public/hot` (the laravel-vite-plugin); this adapter
  detects `node_modules/.hot-inertia` (written by the shipped plugin) via the
  `inertia.vite-dev-hot` setting — empty by default, meaning the auto-scan; `off`
  disables detection and one or more paths override it.
- The React refresh preamble is auto-detected from the dev server (`/ @react-refresh`
  module probe, `inertia.vite-react-refresh` as `auto`/`true`/`false` override) instead
  of a known-file-type heuristic.
- **Version guard**: same parity — while hot, the page version is `null` and no `409`
  version checks fire (the Laravel adapter's dev mode precedent). Stale-asset reloads
  are a build-mode concern.
