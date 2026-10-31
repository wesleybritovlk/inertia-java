package io.github.wesleybritovlk.inertia.spring;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.wesleybritovlk.inertia.core.VersionProvider;

/**
 * Asset version provider that goes dark during the Vite dev workflow: while the hot
 * flag file exists, the version is {@code null} (Laravel parity &mdash; no versioned
 * 409s should fire for pages driven by HMR), otherwise the wrapped source's version
 * is returned unchanged.
 */
final class HotAwareVersionProvider implements VersionProvider {

    private final VersionProvider source;
    private final Path hotFile;

    HotAwareVersionProvider(VersionProvider source, Path hotFile) {
        this.source = source;
        this.hotFile = hotFile;
    }

    @Override
    public String version() {
        return Files.exists(hotFile) ? null : source.version();
    }
}
