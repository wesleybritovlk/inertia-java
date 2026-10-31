# inertia-demo

Hello-world application exercising the full Inertia flow with the canonical
**Vite + Vue 3** front-end layout (the one the Inertia docs prescribe, minus Laravel):

```
inertia-demo/
├── frontend/                         # Vite + Vue (@inertiajs/vue3)
│   ├── src/app.js                    # createInertiaApp + import.meta.glob page map
│   ├── src/Pages/Hello/Index.vue     # SFC page (lazy chunk)
│   └── vite.config.js                # manifest build straight into src/main/resources/static
└── src/main/...                      # Spring Boot back-end (controller + the @vite bridge)
```

## Run it

```bash
# 1. front-end (once, or after editing Pages)
cd inertia-demo/frontend
npm install
npm run build
# → writes static/assets/app-<hash>.js (+ per-page chunks) and static/manifest.json

# 2. back-end
cd ..
mvn install
mvn -pl inertia-demo spring-boot:run          # Boot 3.3.12
mvn -Pboot4 -pl inertia-demo spring-boot:run  # Boot 4.0.5
# open http://localhost:8800
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
- **the `@vite` equivalent** server-side: `DemoConfiguration.viteAwareRenderer` reads the
  Vite manifest and injects the entry script (`<script type="module" src="/assets/app-<hash>.js">`)
  plus any CSS — so hashed, content-addressed assets by default;
- **live asset versioning**: the Vite manifest doubles as `inertia.version-file`
  (`FileVersionProvider` SHA-256 of it). Every `npm run build` changes the version; the
  next stale Inertia visit receives `409` + `X-Inertia-Location` and reloads with the
  fresh bundle — the whole flow is live with `/^(GET)/` Inertia requests.

The compiled assets are committed so the demo runs without Node installed; rebuild with
`npm run build` (Node 18+ needed) whenever the Pages change.
