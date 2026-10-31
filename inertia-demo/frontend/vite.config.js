import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Inertia canonical layout for non-Laravel stacks: the Spring static folder receives
// the hashed build plus the manifest, so the backend can (a) resolve assets and
// (b) hash the manifest into the asset version.
// base '' keeps manifest.file paths ('assets/app-<hash>.js') matching the static handler
// that serves /assets/**.
export default defineConfig({
    plugins: [vue()],
    build: {
        manifest: 'manifest.json',
        outDir: '../src/main/resources/static',
        emptyOutDir: true,
        // no index.html in a served-by-Java project: the entry is the app module
        rollupOptions: { input: { app: 'src/app.js' } },
    },
});
