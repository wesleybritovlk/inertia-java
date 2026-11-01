package io.github.wesleybritovlk.inertia.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.wesleybritovlk.inertia.core.VersionProvider;

/**
 * Asset version provider that goes dark during the Vite dev workflow: while any hot
 * flag candidate exists, the version is {@code null} (Laravel parity &mdash; no
 * versioned 409s should fire for pages driven by HMR); otherwise the wrapped source's
 * version is returned unchanged.
 */
public final class HotAwareVersionProvider implements VersionProvider {

    private final VersionProvider source;
    private final List<Path> hotFiles;

    public HotAwareVersionProvider(VersionProvider source, List<Path> hotFiles) {
        this.source = source;
        // unmodifiable defensive copy ( {@code List.copyOf} is runtime 10, the module rides release 8)
        this.hotFiles = Collections.unmodifiableList(new ArrayList<>(hotFiles));
    }

    @Override
    public String version() {
        return hotFiles.stream().anyMatch(Files::exists) ? null : source.version();
    }
}
