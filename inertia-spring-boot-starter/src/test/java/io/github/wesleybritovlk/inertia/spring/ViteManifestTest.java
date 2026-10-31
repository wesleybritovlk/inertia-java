package io.github.wesleybritovlk.inertia.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Vite asset support: @vite-equivalent variables, entry auto-detection, import css
 * resolution and the renderer delegation.
 */
class ViteManifestTest {

    @TempDir
    Path tempDir;

    private static final Path FIXTURE = Path.of("src/test/resources/vite-test/manifest.json");

    @Test
    void autoDetectsTheEntryAndResolvesImportCss() {
        Map<String, Object> vars = ViteManifest.read(FIXTURE).templateVariables("", "/");

        assertEquals("/assets/app-Hx987.js", vars.get("viteAppJs"));
        // own css + lazy chunk css + imported vendor css, deduplicated and order kept
        assertEquals(List.of("/assets/app-def456.css", "/assets/Index-vW234.css", "/assets/vendor-abc123.css"),
                vars.get("viteCss"));
    }

    @Test
    void explicitEntryAndCustomBasePrefix() {
        Map<String, Object> vars = ViteManifest.read(FIXTURE).templateVariables("src/vendor.js", "/build/");

        assertEquals("/build/assets/vendor-Xy123.js", vars.get("viteAppJs"));
        assertEquals(List.of("/build/assets/vendor-abc123.css"), vars.get("viteCss"));
    }

    @Test
    void missingEntryIsAClearStartupError() {
        var manifest = ViteManifest.read(FIXTURE);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manifest.templateVariables("src/nope.js", "/"));

        assertTrue(failure.getMessage().contains("src/nope.js"));
    }

    @Test
    void missingManifestFileHintsAtTheFrontendBuild() {
        Path absent = tempDir.resolve("nope/manifest.json");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ViteManifest.read(absent));

        assertTrue(failure.getMessage().contains("npm run build"));
    }

    @Test
    void parsesNumbersAndEscapesAndRejectsNonObjectManifests() throws Exception {
        Path numbers = tempDir.resolve("numbers.json");
        java.nio.file.Files.writeString(numbers, """
                {
                  "app": { "file": "a-\\u00B71.js", "isEntry": true, "answer": 42 },
                  "legacy": ["old", "shape"]
                }
                """);

        Map<String, Object> vars = ViteManifest.read(numbers).templateVariables(null, null);

        assertEquals("a-\u00B71.js", vars.get("viteAppJs")); // null base = raw file path

        Path array = tempDir.resolve("array.json");
        java.nio.file.Files.writeString(array, "[1, 2]");
        assertThrows(IllegalStateException.class, () -> ViteManifest.read(array).templateVariables(null, "/"));

        Path garbage = tempDir.resolve("garbage.json");
        java.nio.file.Files.writeString(garbage, "{ nope");
        assertThrows(IllegalStateException.class, () -> ViteManifest.read(garbage));
    }

    @Test
    void noEntryFlaggedFallsBackToAppJsAndMissingFileEntryFails() throws Exception {
        Path noFlag = tempDir.resolve("noflag.json");
        java.nio.file.Files.writeString(noFlag, "{ \"main.js\": { \"file\": \"assets/main.js\" } }");
        assertThrows(IllegalStateException.class, () -> ViteManifest.read(noFlag).templateVariables(null, "/"));

        Path noFile = tempDir.resolve("nofile.json");
        java.nio.file.Files.writeString(noFile, "{ \"src/app.js\": { \"css\": [\"a.css\"] , \"isEntry\": true } }");
        assertThrows(IllegalStateException.class, () -> ViteManifest.read(noFile).templateVariables(null, "/"));
    }

    @Test
    void importCyclesTerminateGracefully() throws Exception {
        Path cycle = tempDir.resolve("cycle.json");
        java.nio.file.Files.writeString(cycle, """
                {
                  "a.js": { "file": "assets/a.js", "imports": ["b.js"], "css": ["a.css"], "isEntry": true },
                  "b.js": { "file": "assets/b.js", "imports": ["b.js"], "css": ["b.css"] }
                }
                """);

        Map<String, Object> vars = ViteManifest.read(cycle).templateVariables(null, "/");

        assertEquals(List.of("/a.css", "/b.css"), vars.get("viteCss"));
    }

    @Test
    void rendererPassesViteVariablesToTheDelegate() throws Exception {
        LinkedHashMap<String, Object> captured = new LinkedHashMap<>();
        InertiaRootTemplateRenderer delegate = (pageJson, viewData) -> {
            captured.putAll(viewData);
            return "<html/>";
        };
        ViteInertiaRootTemplateRenderer renderer =
                new ViteInertiaRootTemplateRenderer(delegate, new ViteInertiaRootTemplateRenderer.ManifestSource(FIXTURE, "", "/"), null);

        String html = renderer.render("{}", Map.of("custom", "x"));

        assertEquals("<html/>", html);
        assertEquals("x", captured.get("custom"));
        assertEquals("/assets/app-Hx987.js", captured.get("viteAppJs"));
    }

    @Test
    void hotFlagFileSwitchesToViteDevModeWithProbeAndReactPreamble() throws Exception {
        // tiny JDK http server emulating the Vite dev entry + @react-refresh
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 5173), 0);
        server.createContext("/src/app.js", exchange -> {
            byte[] body = "export default {}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Path hot = tempDir.resolve(".hot-inertia");
            java.nio.file.Files.writeString(hot, "5173");
            LinkedHashMap<String, Object> captured = new LinkedHashMap<>();
            ViteInertiaRootTemplateRenderer renderer = new ViteInertiaRootTemplateRenderer(
                    (pageJson, viewData) -> {
                        captured.putAll(viewData);
                        return "<html/>";
                    },
                    new ViteInertiaRootTemplateRenderer.ManifestSource(FIXTURE, "", "/"),
                    new ViteInertiaRootTemplateRenderer.HotMode(hot, "http://localhost:5173", "", true));

            String html = renderer.render("{}", Map.of());

            assertEquals("<html/>", html);
            assertEquals(Boolean.TRUE, captured.get("viteDevMode"));
            assertEquals("http://localhost:5173/@vite/client", captured.get("viteClient"));
            assertEquals("http://localhost:5173/src/app.js", captured.get("viteAppJs")); // probe cache hit
            assertEquals("http://localhost:5173/@react-refresh", captured.get("viteReactRefresh"));

            // second render without the flag: back to build mode (manifest variables)
            java.nio.file.Files.delete(hot);
            captured.clear();
            renderer.render("{}", Map.of());
            assertNull(captured.get("viteDevMode"));
            assertEquals("/assets/app-Hx987.js", captured.get("viteAppJs"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledHotModeKeepsBuildBranchEvenWithFlagPresent() throws java.io.IOException {
        LinkedHashMap<String, Object> captured = new LinkedHashMap<>();
        ViteInertiaRootTemplateRenderer renderer = new ViteInertiaRootTemplateRenderer(
                (pageJson, viewData) -> {
                    captured.putAll(viewData);
                    return "<html/>";
                },
                new ViteInertiaRootTemplateRenderer.ManifestSource(FIXTURE, "", "/"),
                null);

        Path present = tempDir.resolve(".hot-inertia");
        try (var out = java.nio.file.Files.newOutputStream(present)) {
            out.write(0);
        }

        renderer.render("{}", Map.of());
        assertNull(captured.get("viteDevMode"));
        assertEquals("/assets/app-Hx987.js", captured.get("viteAppJs"));
    }
}
