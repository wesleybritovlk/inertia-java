import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';

// .hot-inertia = the flag the Spring adapter reads PER RENDER to switch the root
// template to dev mode (HMR) - inertia-laravel public/hot parity; written on
// `npm run dev`, removed on shutdown (kill -9 leaves it: delete manually).
// Default: node_modules/.hot-inertia (git-ignored, starter auto-scans it from any
// working directory).
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
export default defineConfig({
    plugins: [inertiaHotFile(), react()],
    server: {
        cors: { origin: 'http://localhost:8080' }, // the demos run on the default port
    },
    build: {
        manifest: 'manifest.json',
        outDir: '../frontend-assets', // ONE build feeds both demo apps (spring + quarkus)
        emptyOutDir: true,
        rollupOptions: { input: { app: 'src/app.jsx' } },
    },
});
