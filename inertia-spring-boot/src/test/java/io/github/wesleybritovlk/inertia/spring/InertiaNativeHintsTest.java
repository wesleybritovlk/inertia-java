package io.github.wesleybritovlk.inertia.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

/**
 * The native-image registrar covers BOTH Jackson generations by name (the AOT drops what is
 * missing on the classpath) — the exact failure the native lab reproduced was the stripped
 * ObjectMapper on a compiled binary.
 */
class InertiaNativeHintsTest {

    @Test
    void registersBothMapperGenerationsWithInvokableSurface() {
        RuntimeHints hints = new RuntimeHints();
        new InertiaNativeHints().registerHints(hints, getClass().getClassLoader());

        List<String> names = InertiaNativeHints.registeredTypes(hints).filter(
                name -> name.endsWith("ObjectMapper")).collect(Collectors.toList());

        assertTrue(names.contains("com.fasterxml.jackson.databind.ObjectMapper"), "jackson 2 mapper");
        assertTrue(names.contains("tools.jackson.databind.ObjectMapper"), "jackson 3 mapper");
        assertEquals(2, names.size(), "exactly the two registered mappers");

        // the bundled root template resource travels inside inertia-web; without the
        // pattern the compiled binary boots with "bundled root template missing". BARE
        // STAR (no ".*"): Spring's conversion quotes the dot literally and no file
        // contains "templates/."
        List<String> resourcePatterns = hints.resources().resourcePatternHints()
                .flatMap(hint -> hint.getIncludes().stream())
                .map(include -> include.getPattern())
                .collect(Collectors.toList());
        assertTrue(resourcePatterns.contains("META-INF/inertia/templates/*"),
                "bundled template resource must be registered (bare star)");
    }
}
