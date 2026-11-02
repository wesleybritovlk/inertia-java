# inertia-demo

The canonical demo of `inertia-java` in one folder: **three** Vite front-ends (Vue 3,
React 19, Svelte 5) shared by **three** back-end apps — the Spring Boot adapter demo
(`inertia-demo-spring`), its legacy Spring Boot 2 twin (`inertia-demo-spring-boot2`)
and the Quarkus demo (`inertia-demo-quarkus`). The lists/pages/controllers mirror each
other: same components (`Hello/Index`, `SimpleFormTodo/Index`), same props, same routes,
one build output.

```
inertia-demo/
├── frontend-demo-vue/                 # Vue 3 SFC pages (@inertiajs/vue3)
├── frontend-demo-react/               # React 19 pages (@inertiajs/react)
├── frontend-demo-svelte/              # Svelte 5 pages (@inertiajs/svelte)
│   └── (each: src/app.(js|jsx) + src/Pages/*/ + vite.config.js)
├── frontend-assets/                   # `npm run build` output (git-ignored) — feeds ALL apps
├── inertia-demo-spring/               # Spring Boot back-end (adapter demo; mvn spring-boot:run)
├── inertia-demo-spring-boot2/         # same demo on Spring Boot 2.7 / javax / Java 8
└── inertia-demo-quarkus/              # Quarkus back-end (adapter demo; mvn quarkus:dev)
```

Each `vite.config.js` carries the dev-mode flag plugin **inline** — the copy-paste
boilerplate from the [getting started](../docs/getting-started.md) (~10 lines, zero
dependencies, written on dev boot, removed on shutdown).

## Run it

**The dev loop (no build needed):**

```bash
cd inertia-demo/frontend-demo-vue    # demo-react | demo-svelte
npm install && npm run dev           # Vite on :5173, writes node_modules/.hot-inertia
```

```bash
# pick ONE back-end (all live on the default port 8080 and share the same pages):
mvn -pl :inertia-demo-spring spring-boot:run       # Spring Boot (Tomcat; devtools reload)
mvn -pl :inertia-demo-spring-boot2 spring-boot:run # same app on Spring Boot 2.7 / javax / Java 8
mvn -pl :inertia-demo-quarkus quarkus:dev          # Quarkus (Undertow/RESTEasy)
# → http://localhost:8080
```

**All three at once with Docker (compose, 8080/8081/8082):**

```bash
cd inertia-demo && docker compose up --build
# demo-spring          → 8080  (Boot 3.3 native binary, GraalVM CE in-image)
# demo-quarkus         → 8081  (Quarkus native runner, GraalVM CE in-image)
# demo-spring-boot2    → 8082  (Boot 2.7 fat jar on a Liberica Java 8 JRE alpine — no
#                                native possible: Boot 2 has no AOT engine)
```

The Svelte front-end is rebuilt inside EVERY image build (the assets bake into each binary),
so `docker compose up --build` is self-sufficient. Mechanics and traps:
[docs/compile-native.md](../docs/compile-native.md).

Edits in the front-end source update the running page through HMR — no reloads, no
builds. In dev mode the page version is `null` (the versioned 409 flow belongs to
build mode). Full loop details: [../docs/dev-workflow.md](../docs/dev-workflow.md).

**Production-like runs (`npm run build`):**

```bash
npm run build
# → writes ../frontend-assets/assets/app-<hash>.js (+ per-page chunks) and
#   ../frontend-assets/manifest.json — ONE output both demo apps mount as classpath
#   resources (builds are NOT committed; every framework rebuilds the same folder)

mvn -pl :inertia-demo-spring spring-boot:run    # Boot 3.3.12 / 4.0.5 via -Pboot4
mvn -pl :inertia-demo-spring-boot2 spring-boot:run # Boot 2.7.18 demo (same assets, javax line)
mvn -pl :inertia-demo-quarkus quarkus:dev       # add a leading `clean` if the demo seems stale (e.g. after npm builds)
```

**GraalVM native builds (both modern demos carry the profiles):**

```bash
npm run build                                                  # front first: the assets are BAKED into the image
mvn -pl :inertia-demo-spring -Pnative package                  # Boot: AOT + native-image (plugin pinned)
./inertia-demo/inertia-demo-spring/target/inertia-demo-spring  # the ELF boot in milliseconds (~107 MB RSS)
mvn -pl :inertia-demo-quarkus package -Dnative                 # Quarkus native
./inertia-demo/inertia-demo-quarkus/target/inertia-demo-quarkus-runner
```

Full mechanics — the ownership table, the reflective-serializer registration that makes
Inertia visits work in the binary, the resource registration that keeps the bundled root
template alive, the `Todo` record's `@RegisterForReflection` story and both toolchain
traps: [docs/compile-native.md](../docs/compile-native.md).

The whole back-end configuration for Vite is **one property**
(`application.properties` in each demo):

```properties
inertia.vite-manifest=classpath:META-INF/resources/manifest.json
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
