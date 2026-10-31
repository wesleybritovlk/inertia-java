package io.github.wesleybritovlk.inertia.spring;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root template renderer with Vite asset support (the starter's {@code @vite} equivalent):
 * wraps the bundled Thymeleaf renderer and injects {@code viteAppJs}/{@code viteCss}
 * variables resolved live from the Vite manifest, so every render answers with the
 * latest hashed asset URLs and rebuilds are picked up by the asset version guard.
 */
public final class ViteInertiaRootTemplateRenderer implements InertiaRootTemplateRenderer {

    private final InertiaRootTemplateRenderer delegate;
    private final Path manifestPath;
    private final String base;
    private final String entry;

    public ViteInertiaRootTemplateRenderer(
            InertiaRootTemplateRenderer delegate, Path manifestPath, String base, String entry) {
        this.delegate = delegate;
        this.manifestPath = manifestPath;
        this.base = base;
        this.entry = entry;
    }

    @Override
    public String render(String pageJson, Map<String, Object> viewData) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>(viewData);
        variables.putAll(ViteManifest.read(manifestPath).templateVariables(entry, base));
        return delegate.render(pageJson, variables);
    }
}
