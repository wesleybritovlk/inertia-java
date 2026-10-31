import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { inertiaHotPlugin } from '../vite-inertia-hot-plugin.js';

// Inertia canonical layout for non-Laravel stacks: the Spring static folder receives
// the hashed build plus the manifest, so the backend can (a) resolve assets and
// (b) hash the manifest into the asset version.
// build: `npm run build`  |  dev: `npm run dev` (writes .hot-inertia -> HMR mode;
// pair it with inertia.vite-react-refresh=true + inertia.vite-entry=src/app.jsx
// on the Spring side for Fast Refresh).
export default defineConfig({
    plugins: [inertiaHotPlugin(), react()],
    server: {
        cors: { origin: 'http://localhost:8800' },
    },
    build: {
        manifest: 'manifest.json',
        outDir: '../src/main/resources/static',
        emptyOutDir: true,
        // no index.html in a served-by-Java project: the entry is the app module
        rollupOptions: { input: { app: 'src/app.jsx' } },
    },
});
