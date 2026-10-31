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

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
