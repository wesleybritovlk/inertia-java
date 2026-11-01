package io.github.wesleybritovlk.inertia.web;

import java.util.ArrayList;
import java.util.Collections;
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
 * <p>Locations support {@code classpath:} (immune to the working directory — recommended
 * for build outputs) and plain filesystem paths. Entry resolution: an explicit entry
 * key, or the first {@code "isEntry": true} entry of the manifest when none is
 * configured. Asset URLs get the configured base prefix, mirroring the frontend
 * {@code base} setting.</p>
 */
public final class ViteManifest {

    private final String location;
    private final Map<String, Object> entries;

    private ViteManifest(String location, Map<String, Object> entries) {
        this.location = location;
        this.entries = entries;
    }

    public static ViteManifest read(String location) {
        try {
            return new ViteManifest(location, parse(new String(InertiaResources.read(location), java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unreadable Vite manifest " + location + " (run the frontend build, e.g. `npm run build`)", e);
        }
    }

    /**
     * Template variables for the configured entry: {@code viteAppJs} and {@code viteCss}.
     */
    public Map<String, Object> templateVariables(String entryKey, String base) {
        String selected = entryKey == null || entryKey.trim().isEmpty() ? firstEntry() : entryKey;
        Object selectedValue = entries.get(selected);
        if (!(selectedValue instanceof Map<?, ?>)) {
            throw new IllegalStateException("Vite manifest " + location + " has no entry " + selected);
        }
        Map<?, ?> entry = (Map<?, ?>) selectedValue;
        List<String> css = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        visited.add(selected);
        collectCss(selected, entry, css, visited);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("viteAppJs", prefix(base, entry.get("file")));
        variables.put("viteCss", css.stream().map(path -> prefix(base, path)).collect(
                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                        Collections::unmodifiableList)));
        return variables;
    }

    private static String prefix(String base, Object file) {
        if (file == null) {
            throw new IllegalStateException("Vite manifest entry has no \"file\"");
        }
        return base == null ? String.valueOf(file) : base + file;
    }

    private void collectCss(String key, Map<?, ?> entry, List<String> css, Set<String> visited) {
        Object cssFiles = entry.get("css");
        if (cssFiles instanceof List<?>) {
            for (Object file : (List<?>) cssFiles) {
                if (file instanceof String) {
                    css.add((String) file);
                }
            }
        }
        Object importedKeys = entry.get("imports");
        if (importedKeys instanceof List<?>) {
            for (Object imported : (List<?>) importedKeys) {
                if (!(imported instanceof String)) {
                    continue;
                }
                String key0 = (String) imported;
                if (!visited.add(key0)) {
                    continue;
                }
                Object importedValue = entries.get(key0);
                if (importedValue instanceof Map<?, ?>) {
                    collectCss(key0, (Map<?, ?>) importedValue, css, visited);
                }
            }
        }
    }

    private String firstEntry() {
        // auto-detect: the first manifest entry flagged "isEntry": true, manifest order kept
        for (Map.Entry<String, Object> candidate : entries.entrySet()) {
            if (candidate.getValue() instanceof Map<?, ?>) {
                Map<?, ?> entry = (Map<?, ?>) candidate.getValue();
                if (Boolean.TRUE.equals(entry.get("isEntry"))) {
                    return candidate.getKey();
                }
            }
        }
        return "src/app.js";
    }

    // ------------------------------------------------------------------ json reading

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        Mapper parser = new Mapper(json);
        Object value = parser.value();
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
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
            switch (peek()) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    return word("true");
                case 'f':
                    return word("false");
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return number();
                default:
                    throw new IllegalArgumentException("Unsupported JSON at " + index);
            }
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
                    char decoded;
                    switch (escape) {
                        case '"':
                        case '\\':
                        case '/':
                            decoded = escape;
                            break;
                        case 'b':
                            decoded = '\b';
                            break;
                        case 'f':
                            decoded = '\f';
                            break;
                        case 'n':
                            decoded = '\n';
                            break;
                        case 'r':
                            decoded = '\r';
                            break;
                        case 't':
                            decoded = '\t';
                            break;
                        case 'u':
                            decoded = (char) Integer.parseInt(json.substring(index, index + 4), 16);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown escape \\" + escape);
                    }
                    out.append(decoded);
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
