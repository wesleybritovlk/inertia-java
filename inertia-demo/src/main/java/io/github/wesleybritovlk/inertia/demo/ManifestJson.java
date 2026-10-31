package io.github.wesleybritovlk.inertia.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON object reader for the Vite manifest (demo-only, zero dependencies). */
final class ManifestJson {

    private ManifestJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(String json) {
        Parser parser = new Parser(json);
        Object value = parser.readValue();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Vite manifest must be a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String json;
        private int index;

        Parser(String json) {
            this.json = json;
        }

        Object readValue() {
            skipWhitespace();
            return switch (peek()) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> consumeWord("true");
                case 'f' -> consumeWord("false");
                case '-' -> readNumber();
                default -> Character.isDigit(peek()) ? readNumber() : failCurrent();
            };
        }

        private Object failCurrent() {
            throw new IllegalArgumentException("Unsupported JSON value at " + index);
        }

        private Object consumeWord(String word) {
            if (!json.startsWith(word, index)) {
                throw new IllegalArgumentException("Expected " + word + " at " + index);
            }
            index += word.length();
            return Boolean.parseBoolean(word);
        }

        private Object readNumber() {
            int start = index;
            while (index < json.length() && "+-0123456789.eE".indexOf(peek()) >= 0) {
                index++;
            }
            String raw = json.substring(start, index);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
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

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private Map<String, Object> readObject() {
            expect('{');
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return out;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                out.put(key, readValue());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return out;
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return out;
            }
            while (true) {
                out.add(readValue());
                skipWhitespace();
                char next = next();
                if (next == ']') {
                    return out;
                }
            }
        }

        private String readString() {
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
                        default -> escape;
                    });
                } else {
                    out.append(c);
                }
            }
        }
    }
}
