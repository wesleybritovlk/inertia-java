package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader for protocol assertions (test support only). */
final class PageJson {

    private PageJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing content after JSON value");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String json;
        private int index;

        Parser(String json) {
            this.json = json;
        }

        boolean atEnd() {
            return index >= json.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private char next() {
            char c = json.charAt(index++);
            return c;
        }

        private char peek() {
            return json.charAt(index);
        }

        private void expect(char c) {
            char read = next();
            if (read != c) {
                throw new IllegalArgumentException("Expected " + c + " but read " + read + " at " + (index - 1));
            }
        }

        Object readValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> { consumeWord("true"); yield Boolean.TRUE; }
                case 'f' -> { consumeWord("false"); yield Boolean.FALSE; }
                case 'n' -> { consumeWord("null"); yield null; }
                default -> readNumber();
            };
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
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or } at " + (index - 1));
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
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or ] at " + (index - 1));
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

        private void consumeWord(String word) {
            if (!json.startsWith(word, index)) {
                throw new IllegalArgumentException("Expected " + word + " at " + index);
            }
            index += word.length();
        }

        private Number readNumber() {
            int start = index;
            while (!atEnd() && "+-0123456789.eE".indexOf(peek()) >= 0) {
                index++;
            }
            String raw = json.substring(start, index);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                return new java.math.BigInteger(raw);
            }
        }
    }
}
