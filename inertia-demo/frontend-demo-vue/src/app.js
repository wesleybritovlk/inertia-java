import { createApp, h } from 'vue';
import { createInertiaApp } from '@inertiajs/vue3';

// vite-native lazy page mapping (per-page chunks from the Pages folder);
// glob entries are loaders, so the resolver must invoke the matching one
const pages = import.meta.glob('./Pages/**/*.vue');

createInertiaApp({
    title: (title) => (title ? `${title} — inertia-java demo` : 'inertia-java demo'),
    resolve: (name) => {
        const loader = pages[`./Pages/${name}.vue`];
        if (loader === undefined) {
            return Promise.reject(new Error('Page not found: ' + name));
        }
        return loader();
    },
    setup({ el, App, props, plugin }) {
        createApp({ render: () => h(App, props) }).use(plugin).mount(el);
    },
    progress: { color: '#4338ca' },
});
