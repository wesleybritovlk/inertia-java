package io.github.wesleybritovlk.inertia.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Starter properties, prefix {@code inertia}.
 */
@ConfigurationProperties("inertia")
public class InertiaProperties {

    /** Static asset version used when {@code versionFile} is empty. */
    private String version = "1";

    /** Optional file whose SHA-256 hash becomes the asset version (Vite/Rollup manifest). */
    private String versionFile = "";

    /** DOM id the client mounts into; shared by the root template {@code data-page}/id. */
    private String rootId = "app";

    /** User root template override (classpath resource); falls back to the bundled template. */
    private String templatePath = "classpath:templates/inertia.html";

    /** Optional Vite manifest path; setting it enables the {@code @vite}-equivalent renderer and, by default, also drives the asset version. */
    private String viteManifest = "";

    /** URL prefix for Vite asset files (the frontend {@code base} config); default "/". */
    private String viteBase = "/";

    /** Vite entry key override; default resolves the first {@code "isEntry": true} entry. */
    private String viteEntry = "";

    /** Vite dev server base URL used while the hot flag file exists; default "http://localhost:5173". */
    private String viteDevUrl = "http://localhost:5173";

    /**
     * How the dev-mode hot flag ({@code node_modules/.hot-inertia} written by the companion
     * vite plugin while {@code npm run dev} runs) is located. Empty (default) scans the
     * working directory for it inside each project's own {@code node_modules} (up to three
     * levels deep &mdash; node_modules is universally git-ignored, so the flag never shows
     * in {@code git status} nor leaks into packaged artifacts). One or more comma-separated
     * filesystem paths override the scan (relative to the working directory). The value
     * {@code off} disables dev-mode detection entirely.
     */
    private String viteDevHot = "";

    /** Emits the React refresh preamble while in dev mode (needed only for React HMR). */
    private boolean viteReactRefresh = false;

    /** Inertia filter order (outermost by default, so security redirects also upgrade). */
    private int filterOrder = org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 50;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionFile() {
        return versionFile;
    }

    public void setVersionFile(String versionFile) {
        this.versionFile = versionFile;
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getViteManifest() {
        return viteManifest;
    }

    public void setViteManifest(String viteManifest) {
        this.viteManifest = viteManifest;
    }

    public String getViteBase() {
        return viteBase;
    }

    public void setViteBase(String viteBase) {
        this.viteBase = viteBase;
    }

    public String getViteEntry() {
        return viteEntry;
    }

    public void setViteEntry(String viteEntry) {
        this.viteEntry = viteEntry;
    }

    public String getViteDevUrl() {
        return viteDevUrl;
    }

    public void setViteDevUrl(String viteDevUrl) {
        this.viteDevUrl = viteDevUrl;
    }

    public String getViteDevHot() {
        return viteDevHot;
    }

    public void setViteDevHot(String viteDevHot) {
        this.viteDevHot = viteDevHot;
    }

    public boolean isViteReactRefresh() {
        return viteReactRefresh;
    }

    public void setViteReactRefresh(boolean viteReactRefresh) {
        this.viteReactRefresh = viteReactRefresh;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
