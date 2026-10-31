package io.github.wesleybritovlk.inertia.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vite manifest reader for the {@code @vite}-equivalent root template variables
 * ({@code viteAppJs} + {@code viteCss}), including the CSS of lazily imported chunks —
 * the server-side part of Laravel's {@code @vite} directive, zero dependencies.
 *
 * <p>Entry resolution: an explicit entry key, or the first {@code "isEntry": true} entry of
 * the manifest when none is configured. Asset URLs get the configured base prefix, mirroring
 * the frontend {@code base} setting.</p>
 */
final class ViteManifest {

    private final Path file;
    private final Map<String, Object> entries;

    private ViteManifest(Path file, Map<String, Object> entries) {
        this.file = file;
        this.entries = entries;
    }

    static ViteManifest read(Path file) {
        try {
            return new ViteManifest(file, parse(Files.readString(file)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unreadable Vite manifest " + file + " (run the frontend build, e.g. `npm run build`)", e);
        }
    }

    /**
     * Template variables for the configured entry: {@code viteAppJs} and {@code viteCss}.
     */
    Map<String, Object> templateVariables(String entryKey, String base) {
        String selected = entryKey == null || entryKey.isBlank() ? firstEntry() : entryKey;
        if (!(entries.get(selected) instanceof Map<?, ?> entry)) {
            throw new IllegalStateException("Vite manifest " + file + " has no entry " + selected);
        }
        List<String> css = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        visited.add(selected);
        collectCss(selected, entry, css, visited);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("viteAppJs", prefix(base, entry.get("file")));
        variables.put("viteCss", css.stream().map(path -> prefix(base, path)).collect(java.util.stream.Collectors.toUnmodifiableList()));
        return variables;
    }

    private static String prefix(String base, Object file) {
        if (file == null) {
            throw new IllegalStateException("Vite manifest entry has no \"file\"");
        }
        return base == null ? String.valueOf(file) : base + file;
    }

    private void collectCss(String key, Map<?, ?> entry, List<String> css, Set<String> visited) {
        if (entry.get("css") instanceof List<?> files) {
            for (Object file : files) {
                if (file instanceof String s) {
                    css.add(s);
                }
            }
        }
        if (entry.get("imports") instanceof List<?> imports) {
            for (Object imported : imports) {
                if (imported instanceof String key0 && visited.add(key0)
                        && entries.get(key0) instanceof Map<?, ?> importedEntry) {
                    collectCss(key0, importedEntry, css, visited);
                }
            }
        }
    }

    private String firstEntry() {
        // auto-detect: the first manifest entry flagged "isEntry": true, manifest order kept
        for (Map.Entry<String, Object> candidate : entries.entrySet()) {
            if (candidate.getValue() instanceof Map<?, ?> entry
                    && Boolean.TRUE.equals(entry.get("isEntry"))) {
                return candidate.getKey();
            }
        }
        return "src/app.js";
    }

    // ------------------------------------------------------------------ json reading

    private static Map<String, Object> parse(String json) {
        Mapper parser = new Mapper(json);
        Object value = parser.value();
        return value instanceof Map<?, ?> entries ? (Map<String, Object>) entries : Map.of();
    }

    private static final class Mapper {
        private final String json;
        private int index;

        Mapper(String json) {
            this.json = json;
        }

        Object value() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Empty JSON at " + index);
            }
            return switch (peek()) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> word("true");
                case 'f' -> word("false");
                case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> number();
                default -> throw new IllegalArgumentException("Unsupported JSON at " + index);
            };
        }

        private Map<String, Object> object() {
            expect('{');
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) {
                return out;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                out.put(key, value());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return out;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or } at " + (index - 1));
                }
            }
        }

        private List<Object> array() {
            expect('[');
            ArrayList<Object> out = new ArrayList<>();
            skipWhitespace();
            if (take(']')) {
                return out;
            }
            while (true) {
                out.add(value());
                skipWhitespace();
                char next = next();
                if (next == ']') {
                    return out;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or ] at " + (index - 1));
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char escape = next();
                    out.append(switch (escape) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> (char) Integer.parseInt(json.substring(index, index + 4), 16);
                        default -> throw new IllegalArgumentException("Unknown escape \\" + escape);
                    });
                    if (escape == 'u') {
                        index += 4;
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private Object word(String literal) {
            if (!json.startsWith(literal, index)) {
                throw new IllegalArgumentException("Expected " + literal + " at " + index);
            }
            index += literal.length();
            return Boolean.parseBoolean(literal);
        }

        private Number number() {
            int start = index;
            while (!atEnd() && "+-0123456789.eE".indexOf(peek()) >= 0) {
                index++;
            }
            String raw = json.substring(start, index);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        }

        private boolean atEnd() {
            return index >= json.length();
        }

        private char peek() {
            return json.charAt(index);
        }

        private char next() {
            return json.charAt(index++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("Expected " + c + " at " + (index - 1));
            }
        }

        private boolean take(char c) {
            if (!atEnd() && peek() == c) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}
