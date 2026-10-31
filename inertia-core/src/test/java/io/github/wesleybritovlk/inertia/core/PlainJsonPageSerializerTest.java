package io.github.wesleybritovlk.inertia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Behavioral tests of the dependency-free {@link PlainJsonPageSerializer}. */
class PlainJsonPageSerializerTest {

    private final PlainJsonPageSerializer serializer = new PlainJsonPageSerializer();

    @Test
    void serializesScalarPrimitives() {
        LinkedHashMap<String, Object> page = new LinkedHashMap<>();
        page.put("a", 1);
        page.put("b", 2.5);
        page.put("c", true);
        page.put("d", false);
        page.put("e", "text");
        page.put("f", null);

        assertEquals("{\"a\":1,\"b\":2.5,\"c\":true,\"d\":false,\"e\":\"text\",\"f\":null}", serializer.serialize(page));
    }

    @Test
    void escapesStringsForRootTemplates() {
        String json = serializer.serialize(Map.<String, Object>of("s", "<script>alert('x')</script>", "q", "a\"b\\c\nline"));

        // tag-sensitive code points are unicode-escaped (JSON_HEX_TAG parity), so the
        // serialized payload can never terminate a surrounding <script> context
        assertTrue(json.contains("<") == false);
        assertTrue(json.contains(">") == false);
        assertTrue(json.contains("\\u003Cscript\\u003Ealert('x')\\u003C/script\\u003E"));
        assertTrue(json.contains("\"a\\\"b\\\\c\\nline\""));
    }

    @Test
    void serializesNestedMapsAndListsInOrder() {
        LinkedHashMap<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", 1);
        inner.put("a", 2);
        LinkedHashMap<String, Object> page = new LinkedHashMap<>();
        page.put("users", List.of(inner, 7, "x"));
        page.put("meta", Map.of("nested", Map.of("deep", true)));

        String json = serializer.serialize(page);

        assertEquals("{\"users\":[{\"z\":1,\"a\":2},7,\"x\"],\"meta\":{\"nested\":{\"deep\":true}}}", json);
    }

    @Test
    void serializesRecordsThroughComponents() {
        record User(String name, int age) {
        }

        String json = serializer.serialize(Map.<String, Object>of("user", new User("Ana", 30)));

        assertEquals("{\"user\":{\"name\":\"Ana\",\"age\":30}}", json);
    }

    @Test
    void serializesPojoThroughPublicGetters() {
        String json = serializer.serialize(Map.<String, Object>of("bean", new ExampleBean()));

        // getters sort by method name: getFullName before isActive; nested under the map key
        assertEquals("{\"bean\":{\"fullName\":\"Ada\",\"active\":true}}", json);
    }

    @Test
    void unwrapsOptionalAndTemporalValues() {
        LinkedHashMap<String, Object> page = new LinkedHashMap<>();
        page.put("maybe", java.util.Optional.of("v"));
        page.put("empty", java.util.Optional.empty());
        page.put("day", LocalDate.of(2026, 1, 2));
        page.put("at", Instant.parse("2026-01-02T03:04:05Z"));

        String json = serializer.serialize(page);

        assertEquals("{\"maybe\":\"v\",\"empty\":null,\"day\":\"2026-01-02\",\"at\":\"2026-01-02T03:04:05Z\"}", json);
    }

    @Test
    void wrapsChromeUnsafeIntegersWhenEnabled() {
        LinkedHashMap<String, Object> page = new LinkedHashMap<>();
        page.put("preserveBigIntegers", true);
        page.put("big", new BigInteger("12345678901234567890123"));
        page.put("longBig", 9007199254740993L);
        page.put("safe", 42L);

        String json = serializer.serialize(page);

        assertTrue(json.contains("\"big\":{\"$bigint\":\"12345678901234567890123\"}"));
        assertTrue(json.contains("\"longBig\":{\"$bigint\":\"9007199254740993\"}"));
        assertTrue(json.contains("\"safe\":42"));
    }

    @Test
    void keepsRawBigNumbersWithoutFlag() {
        String json = serializer.serialize(Map.<String, Object>of("big", new BigInteger("12345678901234567890123")));

        assertEquals("{\"big\":12345678901234567890123}", json);
    }

    @Test
    void detectsPropCycles() {
        Map<String, Object> self = new LinkedHashMap<>();
        self.put("self", self);

        assertThrows(IllegalStateException.class, () -> serializer.serialize(self));
    }

    @Test
    void escapesControlCodePoints() {
        String json = serializer.serialize(Map.<String, Object>of("ctrl", "\u2028line\u2029"));

        assertEquals("{\"ctrl\":\"\\u2028line\\u2029\"}", json);
    }

    static class ExampleBean {
        public String getFullName() {
            return "Ada";
        }

        public boolean isActive() {
            return true;
        }
    }
}
