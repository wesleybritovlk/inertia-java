package io.github.wesleybritovlk.inertia.servlet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the Vite dev-mode hot flag ({@code node_modules/.hot-inertia}, written by the
 * companion vite plugin while {@code npm run dev} runs). The default scan starts at the
 * working directory and descends three levels, always checking each project's own
 * {@code node_modules} folder — universally git-ignored, so the flag never shows in
 * {@code git status} nor leaks into packaged artifacts. Explicit comma-separated paths
 * override the scan; the value {@code off} yields no candidates (dev-mode detection
 * disabled) — both decisions belong to the adapter configuration, this class only scans.
 */
public final class HotFileScanner {

    private static final int MAX_DEPTH = 3;

    private static final List<String> SKIP_DIRS =
            List.of("node_modules", "target", "build", "dist", ".git", ".idea", "src", "docs");

    private HotFileScanner() {
    }

    /** Default auto-scan candidates (working directory three levels deep + one parent level). */
    public static List<Path> autoScan() {
        List<Path> auto = new ArrayList<>();
        auto.add(Path.of("node_modules", ".hot-inertia"));
        auto.add(Path.of(".hot-inertia"));
        auto.add(Path.of("..", "node_modules", ".hot-inertia"));
        collect(Path.of(""), MAX_DEPTH, auto);
        // one bounded parent level: runtime forks (quarkus:dev etc) may execute from the
        // MODULE dir while the front-ends live in the PARENT folder — covers the sibling
        // layout without roaming the machine
        collect(Path.of(".."), MAX_DEPTH - 1, auto);
        return List.copyOf(auto);
    }

    /** Resolves the value of an adapter's hot property into candidates (empty &rarr; auto-scan). */
    public static List<Path> candidates(String property) {
        if (property == null || property.isBlank()) {
            return autoScan();
        }
        if ("off".equalsIgnoreCase(property.trim())) {
            return List.of();
        }
        List<Path> explicit = new ArrayList<>();
        for (String part : property.split(",")) {
            if (!part.isBlank()) {
                explicit.add(Path.of(part.trim()));
            }
        }
        return explicit.isEmpty() ? autoScan() : List.copyOf(explicit);
    }

    /** {@code true} when the hot property disables the whole detection stack ({@code off}). */
    public static boolean off(String property) {
        return property != null && "off".equalsIgnoreCase(property.trim());
    }

    private static void collect(Path dir, int depth, List<Path> out) {
        if (depth <= 0) {
            return;
        }
        try (var stream = Files.newDirectoryStream(dir)) {
            for (java.util.Iterator<Path> it = stream.iterator();; ) {
                Path child;
                try {
                    child = it.next();
                } catch (java.util.NoSuchElementException exhausted) {
                    break;
                }
                if (child == null || child.getFileName() == null) {
                    continue; // directory stream contract allows null entries
                }
                if (SKIP_DIRS.contains(child.getFileName().toString())) {
                    continue;
                }
                out.add(child.resolve("node_modules").resolve(".hot-inertia"));
                if (Files.isDirectory(child)) {
                    collect(child, depth - 1, out);
                }
            }
        } catch (IOException ignored) {
            // not a readable directory: stop scanning this branch
        }
    }
}
