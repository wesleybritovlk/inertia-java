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

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
