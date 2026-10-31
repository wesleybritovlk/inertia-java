import fs from 'node:fs';
import path from 'node:path';

/**
 * inertia-laravel public/hot parity for inertia-java-bean.
 *
 * While `npm run dev` is running this Vite plugin touches the hot flag file
 * (default `.hot-inertia`, relative to the Vite project root); the Spring adapter
 * (inertia-java-bean) checks its EXISTENCE on every render and switches the root
 * template rendering between build mode (hashed manifest assets + version guard)
 * and dev mode (source modules straight from the dev server, HMR socket live).
 *
 * The file is deleted when the dev server shuts down gracefully — `kill -9` leaves
 * it behind, so remove it manually whenever a dev process dies hard.
 *
 * Options:
 *   `inertiaHotFile()`                 → writes `.hot-inertia` next to the Vite project root
 *   `inertiaHotFile('../.hot-inertia')` → custom path (relative to the Vite root)
 *   `inertiaHotFile({ file: ... })`    → same as the string form, object shape
 *
 * Spring-side pairing (application.properties):
 *   inertia.vite-manifest=src/main/resources/static/manifest.json   (build mode)
 *   inertia.vite-dev-hot=.hot-inertia                               (default, dev flag)
 */
export function inertiaHotFile(options = {}) {
    const hotFile = path.resolve(
        typeof options === 'string' ? options : options.file ?? '.hot-inertia'
    );
    return {
        name: 'inertia-hot-file',
        configureServer(server) {
            fs.writeFileSync(hotFile, String(server.config.server?.port ?? 5173));
            server.httpServer?.on?.('close', () => fs.rmSync(hotFile, { force: true }));
        },
    };
}

export default inertiaHotFile;
