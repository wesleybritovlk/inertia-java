import { createInertiaApp } from '@inertiajs/react';
import { createRoot } from 'react-dom/client';

// vite-native lazy page mapping (per-page chunks from the Pages folder);
// glob entries are loaders, so the resolver must invoke the matching one
const pages = import.meta.glob('./Pages/**/*.jsx');

await createInertiaApp({
    title: (title) => (title ? `${title} — inertia-java demo` : 'inertia-java demo'),
    resolve: (name) => {
        const loader = pages[`./Pages/${name}.jsx`];
        if (loader === undefined) {
            return Promise.reject(new Error('Page not found: ' + name));
        }
        return loader();
    },
    setup({ el, App, props }) {
        createRoot(el).render(<App {...props} />);
    },
    progress: { color: '#4338ca' },
});
