import { createInertiaApp } from '@inertiajs/svelte';
import { mount } from 'svelte';

// vite-native lazy page mapping (per-page chunks from the Pages folder);
// glob entries are loaders, so the resolver must invoke the matching one
const pages = import.meta.glob('./Pages/**/*.svelte');

createInertiaApp({
    title: (title) => (title ? `${title} — inertia-java-bean demo` : 'inertia-java-bean demo'),
    resolve: (name) => {
        const loader = pages[`./Pages/${name}.svelte`];
        if (loader === undefined) {
            return Promise.reject(new Error('Page not found: ' + name));
        }
        return loader();
    },
    setup({ el, App, props }) {
        mount(App, { target: el, props });
    },
    progress: { color: '#4338ca' },
});
