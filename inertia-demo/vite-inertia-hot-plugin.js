import fs from 'node:fs';
import path from 'node:path';

/**
 * inertia-laravel `public/hot` parity: while `npm run dev` is running this tiny plugin
 * TOUCHES `.hot-inertia` next to the demo root; the Spring adapter checks it per render
 * and switches every page to Vite dev mode (HMR). The file is removed when the dev
 * server shuts down (kill -9 leaves it behind: delete it manually then).
 *
 * Shared by all three demo front-ends (imported from each vite.config.js).
 */
export function inertiaHotPlugin() {
    const hotFile = path.resolve(import.meta.dirname, '.hot-inertia');
    return {
        name: 'inertia-hot-file',
        configureServer(server) {
            fs.writeFileSync(hotFile, String(server.config.server?.port ?? 5173));
            server.httpServer?.on?.('close', () => fs.rmSync(hotFile, { force: true }));
        },
    };
}
