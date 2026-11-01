# inertia-demo

The canonical demo of `inertia-java` in one folder: **three** Vite front-ends (Vue 3,
React 19, Svelte 5) shared by **two** back-end apps — the Spring Boot adapter demo
(`inertia-demo-spring`) and the Quarkus demo (`inertia-demo-quarkus`). The lists/pages/
controllers mirror each other: same components (`Hello/Index`, `SimpleFormTodo/Index`),
same props, same routes, one build output.

```
inertia-demo/
├── frontend-demo-vue/                 # Vue 3 SFC pages (@inertiajs/vue3)
├── frontend-demo-react/               # React 19 pages (@inertiajs/react)
├── frontend-demo-svelte/              # Svelte 5 pages (@inertiajs/svelte)
│   └── (each: src/app.(js|jsx) + src/Pages/*/ + vite.config.js)
├── frontend-assets/                   # `npm run build` output (git-ignored) — feeds BOTH apps
├── inertia-demo-spring/               # Spring Boot back-end (adapter demo; mvn spring-boot:run)
└── inertia-demo-quarkus/              # Quarkus back-end (adapter demo; mvn quarkus:dev)
```

Each `vite.config.js` carries the dev-mode flag plugin **inline** (the copy-paste
boilerplate from the [getting started](../docs/getting-started.md)); the npm package
`npm/inertia-vite-hot-file` is the same code ready to publish.

## Run it

**The dev loop (no build needed):**

```bash
cd inertia-demo/frontend-demo-vue    # demo-react | demo-svelte
npm install && npm run dev           # Vite on :5173, writes node_modules/.hot-inertia
```

```bash
# pick ONE back-end (both live on the default port 8080 and share the same pages):
mvn -pl inertia-demo-spring spring-boot:run    # Spring Boot (Tomcat; devtools reload)
mvn -pl inertia-demo-quarkus quarkus:dev       # Quarkus (Undertow/RESTEasy)
# → http://localhost:8080
```

Edits in the front-end source update the running page through HMR — no reloads, no
builds. In dev mode the page version is `null` (the versioned 409 flow belongs to
build mode). Full loop details: [../docs/dev-workflow.md](../docs/dev-workflow.md).

**Production-like runs (`npm run build`):**

```bash
npm run build
# → writes ../frontend-assets/assets/app-<hash>.js (+ per-page chunks) and
#   ../frontend-assets/manifest.json — ONE output both demo apps mount as classpath
#   resources (builds are NOT committed; every framework rebuilds the same folder)

mvn -pl inertia-demo-spring spring-boot:run    # Boot 3.3.12 / 4.0.5 via -Pboot4
mvn -pl inertia-demo-quarkus quarkus:dev       # add a leading `clean` if the demo seems stale (e.g. after npm builds)
```

The whole back-end configuration for Vite is **one property**
(`application.properties` in each demo):

```properties
inertia.vite-manifest=classpath:static/manifest.json
```

With it the adapter wires the `@vite` equivalent (hashed entry script + css from the
manifest) AND the asset version hashing — no configuration classes.

## Pages + routes exercised

| Route | What it shows | Method |
|---|---|---|
| `/` | `Hello/Index` hello page (all three front-ends, both back-ends) | GET |
| `/form-todo` | `SimpleFormTodo/Index` CRUD list, in-memory | GET |
| `/form-todo` | create with validation (empty title → shared `errors` bag) | POST |
| `/form-todo/{id}` | toggle done — **the adapter filter upgrades this redirect to 303** | PATCH |
| `/form-todo/{id}` | delete — same 303 upgrade | DELETE |

The navbar renders through the Inertia `<Link>` in each framework, so navigating is
SPA-internal (no full reloads) — including the 303 follow-ups after POST/PATCH/DELETE.
All flows were validated live in the browser: create → toggle (303) → delete,
empty-title error round trip, navbar navigation.

The CRUD back-end is a single controller (one per demo flavor) with an in-memory map —
no service layer, it is a demo.

### Dev mode rewards

`npm run dev` (Vite) + either back-end pairs exactly like `artisan serve` + Vite in
Laravel: while the hot flag exists inside the front-end's own
`node_modules/.hot-inertia` (written by the vite plugin, auto-detected by the adapter
from any working directory), every render answers with the dev modules and the HMR
socket — your front edits update the running page without reloads, and the back-end
keeps its own process. Full details: [../docs/dev-workflow.md](../docs/dev-workflow.md).

### Framework notes (Inertia v3 client-side setup parity)

| Front-end | Entry | Page props | Title |
|---|---|---|---|
| Vue | `app.js` + `createInertiaApp` + `h(App, props)` + `.use(plugin)` | SFC `defineProps` / `$page.props` | `<Head title>` component |
| React | `app.jsx` + `createRoot(el).render(<App {...props} />)` | `usePage().props` | `<Head title>` component |
| Svelte | `app.js` + `mount(App, { target: el, props })` | `usePage().props` (runes `$state`, reactive) | native `<svelte:head><title>` (the Svelte adapter has no `Head` component) |

All three populate the identical page JSON from either back-end — proving the adapter
is client-framework agnostic exactly like the Inertia protocol intends, and the
`Hello/Index`/`SimpleFormTodo/Index` component contracts work unchanged when you switch
the back-end flavor.
