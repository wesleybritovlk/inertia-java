import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { inertiaHotPlugin } from '../vite-inertia-hot-plugin.js';

// Inertia canonical layout for non-Laravel stacks: the Spring static folder receives
// the hashed build plus the manifest, so the backend can (a) resolve assets and
// (b) hash the manifest into the asset version.
// build: `npm run build`  |  dev: `npm run dev` (writes .hot-inertia -> HMR mode)
// cors: the page is served by Spring on localhost:8800 but the modules come from here.
export default defineConfig({
    plugins: [inertiaHotPlugin(), vue()],
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
