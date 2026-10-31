# inertia-java-bean

A Spring Boot adapter for [Inertia.js] with canonical **Vite** front-ends in any
framework — Vue 3, React 19 or Svelte 5 (the layouts the Inertia docs prescribe, minus
Laravel):

```
inertia-demo/
├── frontend-demo-vue/                 # Vue 3 SFC pages (@inertiajs/vue3)
├── frontend-demo-react/               # React 19 pages (@inertiajs/react)
├── frontend-demo-svelte/              # Svelte 5 pages (@inertiajs/svelte)
│   └── (each: src/app.(js|jsx) + src/Pages/Hello/Index.* + vite.config.js)
└── src/main/...                       # Spring Boot back-end (controller only)
```

The three front-ends produce the SAME page (`Hello/Index`) — the Spring Boot
back-end is shared, so pick the framework, build it, run. Each `vite.config.js`
carries the dev-mode flag plugin **inline** (the copy-paste boilerplate from the
[getting started](../docs/getting-started.md)); teams reusing it across projects +
prefer the ready-to-publish package in `npm/inertia-vite-hot-file`.

## Run it

**The dev loop (no build needed):**

```bash
cd inertia-demo/frontend-demo-vue    # demo-react | demo-svelte
npm install
npm run dev                          # Vite on :5173, writes .hot-inertia
```

```bash
cd ..
mvn -pl inertia-demo spring-boot:run # → http://localhost:8800 (pages serve in DEV MODE)
```

Edits in the front-end source update the running page through HMR — no reloads, no
builds. In dev mode the page version is `null` (the versioned 409 flow belongs to
build mode). Full loop details: [../docs/dev-workflow.md](../docs/dev-workflow.md).

**Production-like runs (`npm run build`):**

```bash
npm run build
# → writes static/assets/app-<hash>.js (+ per-page chunks) and static/manifest.json
#   (builds are NOT committed — every framework rebuilds into the same folder)

mvn -pl inertia-demo spring-boot:run          # Boot 3.3.12
mvn -Pboot4 -pl inertia-demo spring-boot:run  # Boot 4.0.5
```

The whole back-end configuration for Vite is **one property**
(`application.properties`):

```properties
server.port=8800
inertia.vite-manifest=src/main/resources/static/manifest.json
```

With it the starter wires the `@vite` equivalent (hashed entry script + css injected
from the manifest) AND the asset version hashing — no configuration classes.

## What the demo demonstrates

- **`inertia.render("Hello/Index")`** on a plain `@Controller` returning `InertiaPage`;
- **`data-page` root template**: `templates/inertia.html` carries the page payload the
  client boots from (Laravel `@inertia` equivalent);
- **the `@vite` equivalent** server-side: `inertia.vite-manifest` makes the starter
  resolve the entry script + css from the manifest every render (hashed, content-addressed
  assets by default);
- **live asset versioning (build mode)**: the Vite manifest doubles as
  `inertia.version-file` (`FileVersionProvider` SHA-256 of it). Every `npm run build`
  changes the version; the next stale Inertia visit receives `409` +
  `X-Inertia-Location` and reloads with the fresh bundle. In **dev mode** the version
  goes `null` (hot-aware provider — Laravel parity).

### Dev mode rewards

`npm run dev` (Vite) + Spring Boot pair exactly like `artisan serve` + Vite in Laravel:
while `.hot-inertia` exists (written by the shipped vite plugin running `npm run dev`),
every render answers with the dev modules and the HMR socket — your front edits update
the running page without reloads, and the back-end keeps its own process. Full details:
[dev-workflow.md](dev-workflow.md).

### Framework notes (Inertia v3 client-side setup parity)

| Front-end | Entry | Page props | Title |
|---|---|---|---|
| Vue | `app.js` + `createInertiaApp` + `h(App, props)` + `.use(plugin)` | SFC `defineProps` / `$page.props` | `<Head title>` component |
| React | `app.jsx` + `createRoot(el).render(<App {...props} />)` | `usePage().props` | `<Head title>` component |
| Svelte | `app.js` + `mount(App, { target: el, props })` | `usePage().props` (runes `$state`, reactive) | native `<svelte:head><title>` (the Svelte adapter has no `Head` component) |

All three populate the identical page JSON from the back-end — proving the adapter is
client-framework agnostic exactly like the Inertia protocol intends.
