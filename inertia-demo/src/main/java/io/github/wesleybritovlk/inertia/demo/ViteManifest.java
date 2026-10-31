package io.github.wesleybritovlk.inertia.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * Minimal Vite manifest reader (demo-only): maps an entry key to the URLs the root
 * template injects ({@code viteAppJs} + {@code viteCss}), equivalent to what Laravel's
 * {@code @vite} directive does server-side.
 */
final class ViteManifest {

    private final Path file;
    private final Map<String, Object> entries;

    private ViteManifest(Path file, Map<String, Object> entries) {
        this.file = file;
        this.entries = entries;
    }

    static ViteManifest load(Path file) {
        try {
            String json = Files.readString(file);
            return new ViteManifest(file, ManifestJson.readObject(json));
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable Vite manifest: " + file, e);
        }
    }

    /**
     * Template variables for one entry: {@code viteAppJs} (module script) and
     * {@code viteCss} (link list). URLs honor the Vite {@code base} prefix.
     */
    Map<String, Object> assets(String entryKey, String base) {
        Object entry = entries.get(entryKey);
        if (!(entry instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Vite manifest has no entry " + entryKey + " in " + file);
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("viteAppJs", StringUtils.hasText(base) ? base + map.get("file") : map.get("file"));
        List<String> css = new ArrayList<>();
        collectFiles(map, "css", css);
        collectFiles(map, "imports", css); // lazy chunks keep needing their css
        vars.put("viteCss", css.stream().map(suffix -> StringUtils.hasText(base) ? base + suffix : suffix).toList());
        return vars;
    }

    private static void collectFiles(Map<?, ?> entry, String key, List<String> into) {
        Object value = entry.get(key);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    into.add(s);
                }
            }
        }
    }
}
