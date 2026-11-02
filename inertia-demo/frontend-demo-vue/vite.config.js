import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import fs from 'node:fs';

// .hot-inertia = the flag the Spring adapter reads PER RENDER to switch the root
// template to dev mode (HMR) - inertia-laravel public/hot parity; written on
// `npm run dev`, removed on shutdown (kill -9 leaves it: delete manually).
// Default location: node_modules/.hot-inertia of THIS project (node_modules is
// universally git-ignored, and the starter auto-scans for it, so `mvn` may run
// from the repo root or here — both work). Pass a path for exotic setups.
const inertiaHotFile = (file = 'node_modules/.hot-inertia') => ({
    name: 'inertia-hot-file',
    configureServer(server) {
        fs.writeFileSync(file, String(server.config.server?.port ?? 5173));
        server.httpServer?.on?.('close', () => fs.rmSync(file, { force: true }));
    },
});

// Inertia canonical layout for non-Laravel stacks: the static folder receives
// the hashed build plus the manifest, so the backend can (a) resolve assets and
// (b) hash the manifest into the asset version.
// cors: the page is served by the demo back-end on localhost:8080 but the modules come from here.
export default defineConfig({
    plugins: [inertiaHotFile(), vue()],
    server: {
        cors: { origin: 'http://localhost:8080' }, // the demos run on the default port
    },
    build: {
        manifest: 'manifest.json',
        outDir: '../frontend-assets', // ONE build feeds both demo apps (spring + quarkus)
        emptyOutDir: true,
        // no index.html in a served-by-Java project: the entry is the app module
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
