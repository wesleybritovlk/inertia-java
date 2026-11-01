# @wesleybritovlk/inertia-vite-hot-file

The `public/hot` parity plugin for **[inertia-java](../../README.md)**.

It gives your Vite dev server the ability to **tell the Spring Boot process that you
are developing right now**: while `npm run dev` is running, the plugin touches the
`.hot-inertia` flag file; the Spring adapter checks its existence per render and serves
your pages in **dev mode** — modules streamed straight from the dev server with the
HMR socket live (no rebuild, no reloads). When the dev server shuts down, the flag is
removed and the adapter goes back to **build mode** (hashed manifest assets + versioned
`409` stale reloads).

This is the exact mechanics of the Laravel adapter (`laravel-vite-plugin` writes
`public/hot`; `Vite::isRunningHot()` reads it).

## Usage

```bash
npm i @wesleybritovlk/inertia-vite-hot-file
```

```js
// vite.config.js
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { inertiaHotFile } from '@wesleybritovlk/inertia-vite-hot-file';

export default defineConfig({
    plugins: [inertiaHotFile(), vue()],
    // ...
});
```

Spring side (`application.properties`) — the manifest line turns on the whole pair:

```properties
inertia.vite-manifest=classpath:static/manifest.json
```

Dev-mode detection needs NO property: the empty `inertia.vite-dev-hot` default
auto-scans the working directory for `node_modules/.hot-inertia` of each front-end
(the `inertiaHotFile()` default), so both sides are already paired and `mvn` may run
from the repo root. `inertia.vite-dev-hot=off` disables detection entirely; one or
more comma-separated paths override the scan (exotic layouts only).

Vue, React and Svelte work the same way. For React Fast Refresh nothing is needed:
when the React dev server is running, the starter detects the plugin's
`/@react-refresh` module automatically; `inertia.vite-react-refresh=true|false`
forces it explicitly (details in `docs/dev-workflow.md`).

## Options

| Form | Meaning |
|---|---|
| `inertiaHotFile()` | flag file `node_modules/.hot-inertia` (git-ignored; found by the starter's auto-scan) |
| `inertiaHotFile('../.hot-inertia')` | custom path (monorepos: one shared flag for every front-end — pair the same value in `inertia.vite-dev-hot`) |
| `inertiaHotFile({ file })` | object form of the above |

## Notes

- `kill -9` on the dev server leaves the flag behind (no graceful shutdown) — delete it
  manually or the adapter stays in dev mode (a dead dev server self-heals back to build
  mode within a second, the stale flag is just noise in node_modules).
- `inertia.vite-dev-hot=off` on the Spring side disables dev-mode detection entirely.
