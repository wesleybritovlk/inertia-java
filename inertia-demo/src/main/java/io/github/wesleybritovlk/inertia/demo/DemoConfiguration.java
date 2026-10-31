package io.github.wesleybritovlk.inertia.demo;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wesleybritovlk.inertia.spring.InertiaProperties;
import io.github.wesleybritovlk.inertia.spring.InertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.spring.ThymeleafInertiaRootTemplateRenderer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Demo wiring: the {@code @vite} equivalent. The root template receives {@code viteAppJs}
 * and {@code viteCss} read from the Vite manifest, and the asset version is hashed from
 * the same manifest file (configured via {@code inertia.version-file}).
 */
@Configuration
class DemoConfiguration {

    static final String ENTRY = "src/app.js";
    static final String ASSET_BASE = "/";

    @Bean
    @Primary
    InertiaRootTemplateRenderer viteAwareRenderer(InertiaProperties properties, ViteManifest manifest) {
        InertiaRootTemplateRenderer base =
                new ThymeleafInertiaRootTemplateRenderer(properties.getRootId(), properties.getTemplatePath());
        return (pageJson, viewData) -> {
            Map<String, Object> variables = new LinkedHashMap<>(viewData);
            variables.putAll(manifest.assets(ENTRY, ASSET_BASE));
            return base.render(pageJson, variables);
        };
    }

    @Bean
    ViteManifest viteManifest(InertiaProperties properties) {
        return ViteManifest.load(Path.of(properties.getVersionFile()));
    }
}
