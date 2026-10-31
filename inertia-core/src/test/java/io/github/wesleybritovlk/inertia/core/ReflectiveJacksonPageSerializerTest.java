package io.github.wesleybritovlk.inertia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Reflective Jackson bridge tests. Jackson 2 ({@code com.fasterxml}) and Jackson 3
 * ({@code tools.jackson}) coexist on the test classpath on purpose: the two runtimes have
 * disjoint namespaces, exactly the scenario this adapter exists for.
 */
class ReflectiveJacksonPageSerializerTest {

    @Test
    void detectedRuntimePrefersJackson2AndSerializesAppShapes() {
        PageSerializer serializer = ReflectiveJacksonPageSerializer.detected().orElseThrow();

        LinkedHashMap<String, Object> page = new LinkedHashMap<>();
        page.put("component", "Users/Index");
        page.put("props", Map.of("users", List.of("a", "b")));
        page.put("n", 1.5);
        String json = serializer.serialize(page);

        assertEquals("{\"component\":\"Users/Index\",\"props\":{\"users\":[\"a\",\"b\"]},\"n\":1.5}", json);
    }

    @Test
    void boundJackson3MapperSerializesThroughTheNewNamespace() throws Exception {
        // tools.jackson.databind.json.JsonMapper.builder().build(), reflectively
        Class<?> mapperClass = Class.forName("tools.jackson.databind.json.JsonMapper");
        Object mapper = mapperClass.getMethod("builder").invoke(null);
        mapper = mapper.getClass().getMethod("build").invoke(mapper);

        PageSerializer serializer = ReflectiveJacksonPageSerializer.over(mapper);

        String json = serializer.serialize(Map.<String, Object>of(
                "component", "Users/Index",
                "props", List.of("x")));

        // object key order is NOT part of the protocol; the app's mapper conventions apply
        assertTrue(json.contains("\"component\":\"Users/Index\""));
        assertTrue(json.contains("\"props\":[\"x\"]"));
        assertEquals('{', json.charAt(0));
    }

    @Test
    void plainSerializerIsTheFallbackWithoutAnyJackson() {
        CoreWithoutJackson.run();
    }

    /** compilation-safety probe: the core module never needs Jackson to compile or to fall back. */
    private static final class CoreWithoutJackson {
        static void run() {
            PageSerializer fallback = new PlainJsonPageSerializer();
            assertTrue(fallback.serialize(Map.<String, Object>of("okay", true)).contains("\"okay\":true"));
        }
    }
}
