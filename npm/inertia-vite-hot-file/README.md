# @wesleybritovlk/inertia-vite-hot-file

The `public/hot` parity plugin for **[inertia-java-bean](../../README.md)**.

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

Spring side (`application.properties`) — one line turns on the whole pair:

```properties
inertia.vite-manifest=path/to/your/manifest.json
inertia.vite-dev-hot=.hot-inertia      # default; set to empty to disable dev mode
```

Vue, React and Svelte work the same way; for React Fast Refresh also set
`inertia.vite-react-refresh=true` and `inertia.vite-entry=src/app.jsx` on the Spring
side (details in `docs/dev-workflow.md`).

## Options

| Form | Meaning |
|---|---|
| `inertiaHotFile()` | flag file `.hot-inertia` at the Vite project root |
| `inertiaHotFile('../.hot-inertia')` | custom path (monorepos: one shared flag for every front-end) |
| `inertiaHotFile({ file })` | object form of the above |

## Notes

- `kill -9` on the dev server leaves the flag behind (no graceful shutdown) — delete it
  manually or the adapter stays in dev mode.
- `inertia.vite-dev-hot=` (empty) on the Spring side disables dev-mode detection
  entirely.
