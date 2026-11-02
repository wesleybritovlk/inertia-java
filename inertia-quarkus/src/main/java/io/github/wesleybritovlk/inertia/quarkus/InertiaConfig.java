package io.github.wesleybritovlk.inertia.quarkus;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Adapter configuration (SmallRye {@code ConfigMapping}, prefix {@code inertia}): the same
 * names, defaults and semantics as the Spring Boot starter's {@code inertia.*} properties.
 * The Spring side exposes every entry here through its {@code Properties} class.
 */
@ConfigMapping(prefix = "inertia")
public interface InertiaConfig {

    /** Static asset version used when {@code versionFile} and {@code viteManifest} are blank. */
    @WithDefault("1")
    String version();

    /** SHA-256 of this resource becomes the asset version ({@code classpath:} or file path). */
    Optional<String> versionFile();

    /** DOM id the client mounts into (root template {@code data-page}/id). */
    @WithDefault("app")
    String rootId();

    /** Root template override ({@code classpath:} resource); falls back to the bundled template. */
    @WithDefault("classpath:templates/inertia.html")
    String templatePath();

    /** Vite manifest location; enabling it also drives the asset version by default. */
    Optional<String> viteManifest();

    /** URL prefix for Vite asset files (frontend {@code base}). */
    @WithDefault("/")
    String viteBase();

    /** Vite entry key override; default resolves the first {@code isEntry:true}. */
    Optional<String> viteEntry();

    /** Vite dev server base URL used while the hot flag exists. */
    @WithDefault("http://localhost:5173")
    String viteDevUrl();

    /**
     * How the dev hot flag is located: empty scans (default), comma-separated paths override,
     * {@code off} disables detection entirely.
     */
    Optional<String> viteDevHot();

    /** React Fast Refresh preamble: {@code auto} (default), {@code true}, {@code false}. */
    @WithDefault("auto")
    String viteReactRefresh();
}
