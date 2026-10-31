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
 │  npm run dev                  │   │  mvn -pl inertia-demo spring-boot:run     │
 │  (Vite on :5173, HMR)         │   │  (Tomcat on :8800)                        │
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

## The hot file plugin (shipped with the demo front-ends)

`inertia-demo/vite-inertia-hot-plugin.js` — ~15 lines, importable by every front-end:

```js
import { inertiaHotPlugin } from '../vite-inertia-hot-plugin.js';

export default defineConfig({
    plugins: [inertiaHotPlugin(), vue()], // react() / svelte() for the others
    server: { cors: { origin: 'http://localhost:8800' } },
    // ...
});
```

It writes `.hot-inertia` when the dev server boots and deletes it on shutdown
(Laravel parity). If a dev process is killed with `kill -9`, remove the file manually.

## Configuration (all under `inertia.*`)

| Property | Default | Meaning |
|---|---|---|
| `inertia.vite-dev-hot` | `.hot-inertia` | Hot flag file path (relative to the back-end working directory). Empty disables dev mode entirely |
| `inertia.vite-dev-url` | `http://localhost:5173` | Vite dev server origin |
| `inertia.vite-entry` | _(empty)_ | Dev entry source path (e.g. `src/app.js`); empty probes `src/app.js` then `src/app.jsx` against the dev server on first render |
| `inertia.vite-react-refresh` | `false` | Emit the React **Fast Refresh** preamble in dev mode (needed only when developing the React front-end) |
| `inertia.vite-manifest` | _(empty)_ | Build manifest — used in build mode (and as version source when nothing else is configured) |

## Running the demo

Two terminals, any front-end folder:

```bash
# terminal 1 (front-end, HMR)
cd inertia-demo/frontend-demo-vue      # demo-react | demo-svelte
npm install && npm run dev             # writes .hot-inertia

# terminal 2 (back-end)
mvn -pl inertia-demo spring-boot:run   # → http://localhost:8800
```

React specifics (Fast Refresh needs the preamble + the .jsx entry):

```bash
# force the react-refresh preamble + dev entry on the Spring side:
mvn -pl inertia-demo spring-boot:run \
  -Dspring-boot.run.arguments="--inertia.vite-react-refresh=true --inertia.vite-entry=src/app.jsx"
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
  reads the configured `inertia.vite-dev-hot` path (default `.hot-inertia`, written by
  the shipped plugin).
- The React refresh preamble + dev client emission is driven by the
  `inertia.vite-react-refresh` property instead of the known-file-type heuristic.
- **Version guard**: same parity — while hot, the page version is `null` and no `409`
  version checks fire (the Laravel adapter's dev mode precedent). Stale-asset reloads
  are a build-mode concern.
