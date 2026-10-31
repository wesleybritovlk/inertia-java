import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import fs from 'node:fs';

// .hot-inertia = the flag the Spring adapter reads PER RENDER to switch the root
// template to dev mode (HMR) - inertia-laravel public/hot parity; written on
// `npm run dev`, removed on shutdown (kill -9 leaves it: delete manually).
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
export default defineConfig({
    plugins: [inertiaHotFile(), svelte()],
    server: {
        cors: { origin: 'http://localhost:8800' },
    },
    build: {
        manifest: 'manifest.json',
        outDir: '../src/main/resources/static',
        emptyOutDir: true,
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
