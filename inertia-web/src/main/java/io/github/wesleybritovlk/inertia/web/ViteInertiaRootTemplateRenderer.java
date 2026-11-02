package io.github.wesleybritovlk.inertia.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private static final long PROBE_TTL_MS = 1000;

    /** JUL replaces {@code System.Logger} (runtime 9+); the module rides release 8. */
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(ViteInertiaRootTemplateRenderer.class.getName());

    private final InertiaRootTemplateRenderer delegate;
    private final ManifestSource manifests;
    private final HotMode hot;
    /** last negative probe result — self-heals stale flags (dead dev server left the file). */
    private volatile long lastFailedProbeAt;

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
        if (hot != null && hotActive()) {
            variables.putAll(hotVariables());
        } else {
            try {
                variables.putAll(ViteManifest.read(manifests.manifestLocation())
                        .templateVariables(manifests.entry(), manifests.base()));
            } catch (IllegalStateException unreadable) {
                // no build output yet: render the page without the app script instead of 500
                LOGGER.warning(unreadable.getMessage());
            }
        }
        return delegate.render(pageJson, variables);
    }

    /**
     * Laravel {@code isRunningHot} parity plus one guard Laravel does not need: the flag
     * file alone is not enough — a dead dev server that never removed it would leave the
     * app serving dead dev URLs. So the flag must exist <em>and</em> the dev server must
     * answer the HMR client endpoint — the one URL dev mode actually requires (Inertia
     * layouts ship no {@code index.html}: the Vite root answers 404, so probing {@code /}
     * would reject a healthy dev server). Failed probes are remembered for
     * {@value #PROBE_TTL_MS} ms and retried on the next render (self-healing stale flags).
     */
    private boolean hotActive() {
        if (!hot.isEnabled()) {
            return false;
        }
        if (System.currentTimeMillis() - lastFailedProbeAt < PROBE_TTL_MS) {
            return false;
        }
        if (!probe(locateDevServer().devUrl() + "/@vite/client")) {
            lastFailedProbeAt = System.currentTimeMillis();
            return false;
        }
        return true;
    }

    private Map<String, Object> hotVariables() {
        DevServer server = locateDevServer();
        String entry = resolveDevEntry(server);
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("viteDevMode", Boolean.TRUE);
        variables.put("viteClient", server.devUrl() + "/@vite/client");
        variables.put("viteAppJs", server.devUrl() + "/" + entry);
        if (reactRefreshOn(server)) {
            variables.put("viteReactRefresh", server.devUrl() + "/@react-refresh");
        }
        return variables;
    }

    /**
     * The dev server actually in use: the hot flag content wins over the configured
     * {@code devUrl} — the companion plugin stores either the real dev port (Vite
     * auto-increments when the configured port is busy, so front-ends each get their
     * own) or a full URL, Laravel {@code public/hot} style. The {@code identity} pins
     * the matched flag file plus its content: front-end switches (another front's flag
     * serving another entry/plugin on the same origin) must invalidate the per-process
     * probe caches.
     */
    private static final class DevServer {

        private final String devUrl;
        private final String identity;

        DevServer(String devUrl, String identity) {
            this.devUrl = devUrl;
            this.identity = identity;
        }

        String devUrl() {
            return devUrl;
        }

        String identity() {
            return identity;
        }
    }

    private DevServer locateDevServer() {
        for (Path candidate : hot.hotFiles()) {
            if (!java.nio.file.Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                String content = new String(java.nio.file.Files.readAllBytes(candidate), StandardCharsets.UTF_8).trim();
                if (content.isEmpty()) {
                    continue;
                }
                String url;
                if (content.startsWith("http")) {
                    url = content.endsWith("/") ? content.substring(0, content.length() - 1) : content;
                } else {
                    Integer.parseInt(content); // a bare port: overlay it on the configured origin
                    url = hot.devUrl().replaceFirst(":\\d+", ":" + content);
                }
                return new DevServer(url, candidate + "#" + content);
            } catch (java.io.IOException | NumberFormatException unreadable) {
                break; // missing/unparsable flag: the configured devUrl stands
            }
        }
        return new DevServer(hot.devUrl(), "(no flag)");
    }

    /**
     * React Fast Refresh preamble decision: forced by {@code true}/{@code false}; the
     * {@code auto} default probes the dev server for the {@code /@react-refresh} module
     * that only {@code @vitejs/plugin-react} mounts (404 from any other front-end), so
     * the React page gets its preamble with zero configuration. Cached per dev-server
     * identity (the plugin presence does not flip mid-session of the same front).
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> reactRefreshCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private boolean reactRefreshOn(DevServer server) {
        String setting = hot.reactRefresh();
        if (setting != null) {
            setting = setting.trim().toLowerCase();
        }
        if ("true".equals(setting)) {
            return true;
        }
        if ("false".equals(setting)) {
            return false;
        }
        return reactRefreshCache.computeIfAbsent(
                server.identity(), id -> probe(server.devUrl() + "/@react-refresh"));
    }

    /** Zero-config dev entry: use the configured key when set; otherwise probe the common Inertia entry names on the dev server (first 200 wins, cached for the process). */
    private final java.util.concurrent.ConcurrentHashMap<String, String> devEntryCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final String[] DEV_ENTRY_CANDIDATES = { "src/app.js", "src/app.jsx" };

    /**
     * Zero-config dev entry: use the configured key when set; otherwise probe the common
     * Inertia entry names on THIS dev server (first 200 wins). Cached per dev-server
     * identity — switching front-ends (React entry {@code .jsx} vs Vue/Svelte
     * {@code .js}), or the same front restarting on an auto-incremented port, must not
     * reuse the previous server's entry.
     */
    private String resolveDevEntry(DevServer server) {
        String configured = hot.entry();
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        return devEntryCache.computeIfAbsent(server.identity(), id -> {
            for (String candidate : DEV_ENTRY_CANDIDATES) {
                if (probe(server.devUrl() + '/' + candidate)) {
                    return candidate;
                }
            }
            return "src/app.js"; // the vite console will surface any 404 loudly
        });
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

    /** Manifest settings for build (production) mode. */
    public static final class ManifestSource {

        private final String manifestLocation;
        private final String entry;
        private final String base;

        public ManifestSource(String manifestLocation, String entry, String base) {
            this.manifestLocation = manifestLocation;
            this.entry = entry;
            this.base = base;
        }

        public String manifestLocation() {
            return manifestLocation;
        }

        public String entry() {
            return entry;
        }

        public String base() {
            return base;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ManifestSource)) {
                return false;
            }
            ManifestSource that = (ManifestSource) other;
            return Objects.equals(manifestLocation, that.manifestLocation)
                    && Objects.equals(entry, that.entry)
                    && Objects.equals(base, that.base);
        }

        @Override
        public int hashCode() {
            return Objects.hash(manifestLocation, entry, base);
        }

        @Override
        public String toString() {
            return "ManifestSource[manifestLocation=" + manifestLocation + ", entry=" + entry + ", base=" + base + "]";
        }
    }

    /**
     * Dev-mode settings. {@code hotFiles()} — flag candidates checked per render (Laravel
     * {@code public/hot} parity); {@code devUrl()} — configured origin, overridden by the
     * flag content (port or full URL) at render time; {@code entry()} — configured entry,
     * otherwise probed; {@code reactRefresh} — {@code true}/{@code false} force, anything
     * else probes the {@code @react-refresh} module (auto). Existence is checked per
     * render; the renderer probes the dev server so a stale flag (dev server dead) falls
     * back to build mode.
     */
    public static final class HotMode {

        private final List<Path> hotFiles;
        private final String devUrl;
        private final String entry;
        private final String reactRefresh;

        public HotMode(List<Path> hotFiles, String devUrl, String entry, String reactRefresh) {
            // unmodifiable defensive copy ( {@code List.copyOf} is runtime 10, the module rides release 8)
            this.hotFiles = Collections.unmodifiableList(new ArrayList<>(hotFiles));
            this.devUrl = devUrl;
            this.entry = entry;
            this.reactRefresh = reactRefresh;
        }

        public List<Path> hotFiles() {
            return hotFiles;
        }

        public String devUrl() {
            return devUrl;
        }

        public String entry() {
            return entry;
        }

        public String reactRefresh() {
            return reactRefresh;
        }

        boolean isEnabled() {
            return hotFiles.stream().anyMatch(java.nio.file.Files::exists);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof HotMode)) {
                return false;
            }
            HotMode that = (HotMode) other;
            return Objects.equals(hotFiles, that.hotFiles)
                    && Objects.equals(devUrl, that.devUrl)
                    && Objects.equals(entry, that.entry)
                    && Objects.equals(reactRefresh, that.reactRefresh);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hotFiles, devUrl, entry, reactRefresh);
        }

        @Override
        public String toString() {
            return "HotMode[hotFiles=" + hotFiles + ", devUrl=" + devUrl + ", entry=" + entry + ", reactRefresh="
                    + reactRefresh + "]";
        }
    }
}
