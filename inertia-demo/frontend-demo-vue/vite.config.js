import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import fs from 'node:fs';

// .hot-inertia = the flag the Spring adapter reads PER RENDER to switch the root
// template to dev mode (HMR) - inertia-laravel public/hot parity; written on
// `npm run dev`, removed on shutdown (kill -9 leaves it: delete manually).
// THIS demo shares ONE flag for all three front-ends: '../.hot-inertia' lives in the
// demo root and is git-ignored there (user projects default to node_modules/.hot-inertia,
// which never shows in git status anyway).
const inertiaHotFile = () => ({
    name: 'inertia-hot-file',
    configureServer(server) {
        fs.writeFileSync('../.hot-inertia', String(server.config.server?.port ?? 5173));
        server.httpServer?.on?.('close', () => fs.rmSync('../.hot-inertia', { force: true }));
    },
});

// Inertia canonical layout for non-Laravel stacks: the Spring static folder receives
// the hashed build plus the manifest, so the backend can (a) resolve assets and
// (b) hash the manifest into the asset version.
// cors: the page is served by Spring on localhost:8800 but the modules come from here.
export default defineConfig({
    plugins: [inertiaHotFile(), vue()],
    server: {
        cors: { origin: 'http://localhost:8800' },
    },
    build: {
        manifest: 'manifest.json',
        outDir: '../src/main/resources/static',
        emptyOutDir: true,
        // no index.html in a served-by-Java project: the entry is the app module
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
