package io.github.wesleybritovlk.inertia.spring;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root template renderer with Vite asset support (the starter's {@code @vite} equivalent):
 * wraps the bundled Thymeleaf renderer and injects {@code viteAppJs}/{@code viteCss}
 * variables resolved live from the Vite manifest, so every render answers with the
 * latest hashed asset URLs and rebuilds are picked up by the asset version guard.
 *
 * <p><b>Dev mode</b> (the {@code npm run dev} workflow, Laravel {@code public/hot}
 * parity): while the hot flag file ( {@link HotMode}) exists, renders switch to the Vite
 * dev server &mdash; the template receives {@code viteDevMode} plus {@code viteClient},
 * the (opaque, unhashed) entry module URL and, only for React, the refresh preamble URL.
 * This is how Inertia development pairs a Spring process with live HMR.</p>
 */
public final class ViteInertiaRootTemplateRenderer implements InertiaRootTemplateRenderer {

    private final InertiaRootTemplateRenderer delegate;
    private final ManifestSource manifests;
    private final HotMode hot;

    public ViteInertiaRootTemplateRenderer(
            InertiaRootTemplateRenderer delegate,
            ManifestSource manifests,
            HotMode hot) {
        this.delegate = delegate;
        this.manifests = manifests;
        this.hot = hot;
    }

    @Override
    public String render(String pageJson, Map<String, Object> viewData) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>(viewData);
        if (hot != null && hot.isEnabled()) {
            variables.putAll(hotVariables());
        } else {
            variables.putAll(ViteManifest.read(manifests.manifestPath()).templateVariables(manifests.entry(), manifests.base()));
        }
        return delegate.render(pageJson, variables);
    }

    private Map<String, Object> hotVariables() {
        String devUrl = hot.devUrl();
        String entry = resolveDevEntry();
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("viteDevMode", Boolean.TRUE);
        variables.put("viteClient", devUrl + "/@vite/client");
        variables.put("viteAppJs", devUrl + "/" + entry);
        if (hot.reactRefresh()) {
            variables.put("viteReactRefresh", devUrl + "/@react-refresh");
        }
        return variables;
    }

    /**
     * Zero-config dev entry: use the configured key when set; otherwise probe the common
     * Inertia entry names on the dev server (first 200 wins, cached for the process).
     */
    private volatile String cachedDevEntry;
    private static final String[] DEV_ENTRY_CANDIDATES = { "src/app.js", "src/app.jsx" };

    private String resolveDevEntry() {
        if (cachedDevEntry != null) {
            return cachedDevEntry;
        }
        String configured = hot.entry();
        if (configured != null && !configured.isBlank()) {
            cachedDevEntry = configured;
            return configured;
        }
        for (String candidate : DEV_ENTRY_CANDIDATES) {
            if (probe(devUrl() + '/' + candidate)) {
                cachedDevEntry = candidate;
                return candidate;
            }
        }
        cachedDevEntry = "src/app.js";
        return cachedDevEntry; // the vite console will surface any 404 loudly
    }

    private boolean probe(String url) {
        java.net.HttpURLConnection connection;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URI(url).toURL().openConnection();
        } catch (java.net.URISyntaxException | java.io.IOException malformed) {
            return false;
        }
        try {
            connection.setConnectTimeout(300);
            connection.setReadTimeout(300);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == 200;
        } catch (java.io.IOException io) {
            return false;
        }
    }

    private String devUrl() {
        return hot.devUrl();
    }

    /** Manifest settings for build (production) mode. */
    public record ManifestSource(Path manifestPath, String entry, String base) {
    }

    /**
     * Dev-mode settings. {@code hotFile()} relative to the process working directory;
     * {@code enabled()} checks existence per render (Laravel {@code public/hot} parity).
     */
    public record HotMode(Path hotFile, String devUrl, String entry, boolean reactRefresh) {

        boolean isEnabled() {
            return hotFile != null && java.nio.file.Files.exists(hotFile);
        }
    }
}
