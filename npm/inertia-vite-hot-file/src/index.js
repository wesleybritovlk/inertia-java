import fs from 'node:fs';
import path from 'node:path';

/**
 * inertia-laravel public/hot parity for inertia-java.
 *
 * While `npm run dev` is running this Vite plugin touches the hot flag file
 * (default `node_modules/.hot-inertia` — node_modules is universally git-ignored and
 * exists by construction at dev time, so the flag never shows in `git status` and can
 * never leak into a Java artifact); the Spring adapter checks its EXISTENCE on every
 * render and switches the root template rendering between build mode (hashed manifest
 * assets + version guard) and dev mode (source modules straight from the dev server,
 * HMR socket live).
 *
 * The file is deleted when the dev server shuts down gracefully — `kill -9` leaves it
 * behind, so remove it manually whenever a dev process dies hard.
 *
 * Options:
 *   `inertiaHotFile()`                    → writes `node_modules/.hot-inertia` (universal gitignore)
 *   `inertiaHotFile('../.hot-inertia')`   → custom path (relative to the Vite root; monorepos
 *                                            may share ONE flag across several front-ends)
 *   `inertiaHotFile({ file: ... })`       → same as the string form, object shape
 *
 * Spring-side detection (application.properties) — nothing to configure for this default:
 *   inertia.vite-dev-hot=          (empty; the default AUTO-SCANS the working directory for
 *                                   node_modules/.hot-inertia of each front-end, so both
 *                                   sides are already paired — custom flag paths are the
 *                                   only case needing the same value on both sides)
 *   inertia.vite-dev-hot=off       disables dev-mode detection entirely
 */
export function inertiaHotFile(options = {}) {
    const hotFile = path.resolve(
        typeof options === 'string' ? options : options.file ?? 'node_modules/.hot-inertia'
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
