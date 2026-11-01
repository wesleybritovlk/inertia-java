package io.github.wesleybritovlk.inertia.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.wesleybritovlk.inertia.core.VersionProvider;

/** Asset version providers + the shared hot scanner + the root template renderers. */
class ServletUnitsTest {

    @TempDir
    Path tempDir;

    // --------------------------------------------------------- version providers

    @Test
    void fileVersionProviderHashesContentsAndSupportsLocations() throws Exception {
        Path versioned = tempDir.resolve("manifest.json");
        Files.writeString(versioned, "{\"app.js\": \"hash\"}");

        String first = new FileVersionProvider(versioned.toString()).version();
        assertEquals(64, first.length(), "sha-256 hex digest");
        assertEquals(first, new FileVersionProvider(versioned.toString()).version());
        assertEquals(first, new FileVersionProvider("file:" + versioned).version());

        assertNull(new FileVersionProvider(tempDir.resolve("absent.json").toString()).version(),
                "missing files disable versioning");
        assertNull(new FileVersionProvider(tempDir.toString()).version(),
                "directories disable versioning");
    }

    @Test
    void fileVersionProviderAcceptsClasspathLocations() {
        FileVersionProvider classpath = new FileVersionProvider("classpath:vite-test/manifest.json");
        assertEquals(new FileVersionProvider("src/test/resources/vite-test/manifest.json").version(),
                classpath.version(), "classpath and local path resolve the same bytes");
    }

    @Test
    void hotAwareVersionProviderGoesDarkWhileAnyFlagExists() throws Exception {
        VersionProvider source = () -> "v-src";
        Path flag = tempDir.resolve(".hot-inertia");
        Files.writeString(flag, "5173");

        assertNull(new HotAwareVersionProvider(source, List.of(flag)).version(),
                "HMR drives changes: no versioned 409s while hot");
        assertNull(new HotAwareVersionProvider(source, List.of(tempDir.resolve("absent"), flag)).version());

        Files.delete(flag);
        assertEquals("v-src", new HotAwareVersionProvider(source, List.of(flag)).version());
    }

    @Test
    void hotScannerResolvesThePropertyValueIntoCandidates() {
        assertTrue(HotFileScanner.candidates(null).contains(Path.of("node_modules", ".hot-inertia")),
                "empty property auto-scans");
        assertTrue(HotFileScanner.autoScan().contains(Path.of("node_modules", ".hot-inertia")));
        List<Path> explicit = HotFileScanner.candidates(" a/.hot-inertia , b/c/.hot-inertia ");
        assertEquals(List.of(Path.of("a", ".hot-inertia"), Path.of("b", "c", ".hot-inertia")), explicit);
        assertTrue(HotFileScanner.candidates("off").isEmpty(), "off disables detection");
        assertTrue(HotFileScanner.off(" off "), "the off value is case-insensitive");
        assertTrue(HotFileScanner.off("off"));
        assertTrue(!HotFileScanner.off(" hot flag "));
    }

    // --------------------------------------------------------- root template renderers

    @Test
    void viteManifestResolvesEntriesCollectsImportedCssAndFailsLoudly() {
        var manifest = ViteManifest.read("src/test/resources/vite-test/manifest-nested.json");
        java.util.Map<String, Object> variables = manifest.templateVariables(null, "/a/");

        assertEquals("/a/assets/app-x.js", variables.get("viteAppJs"), "first isEntry wins; base prefix applies");
        assertEquals(List.of("/a/assets/vendor.css"), variables.get("viteCss"),
                "the css walk reaches lazily imported chunks and skips non-string entries");

        java.util.Map<String, Object> explicit = manifest.templateVariables("vendor.js", "");
        assertEquals("assets/vendor-x.js", explicit.get("viteAppJs"));

        assertThrows(IllegalStateException.class,
                () -> manifest.templateVariables("missing-key", "/"));
        assertThrows(IllegalStateException.class,
                () -> ViteManifest.read("src/test/resources/vite-test/manifest-entryless.json")
                        .templateVariables(null, "/"));
        assertThrows(IllegalStateException.class,
                () -> ViteManifest.read("src/test/resources/vite-test/absent-manifest.json"),
                "unreadable manifests fail with a build hint");
    }

    @Test
    void inertiaResourcesReadsFileLocationsAndExistence() throws Exception {
        Path file = tempDir.resolve("data.json");
        Files.writeString(file, "{\"a\":1}");
        assertTrue(new String(InertiaResources.read(file.toString()), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"a\":1"), "plain filesystem paths read");
        assertTrue(new String(InertiaResources.read("file:" + file), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"a\":1"), "file: prefixed paths read");
        assertTrue(InertiaResources.exists(file.toString()));
        assertTrue(InertiaResources.exists("classpath:vite-test/manifest.json"));
        assertTrue(!InertiaResources.exists("classpath:vite-test/absent.json"));
        assertTrue(!InertiaResources.exists(tempDir.resolve("absent.json").toString()));
        try {
            InertiaResources.read("classpath:vite-test/absent.json");
            org.junit.jupiter.api.Assertions.fail("missing classpath resources must raise");
        } catch (java.io.IOException expected) {
            // contract: NoSuchFileException surfaces
        }
    }

    // --------------------------------------------------------- root template renderers

    @Test
    void thymeleafRendererBundlesTheRootTemplateAndSupportsOverridesAndViewData() {
        String bundled = new ThymeleafInertiaRootTemplateRenderer("mount", "classpath:does/not/exist.html")
                .render("{\"component\":\"X\"}", null);
        assertTrue(bundled.contains("data-page=\"mount\""));
        assertTrue(bundled.contains("<div id=\"mount\">") || bundled.contains("id=\"mount\""));
        assertTrue(bundled.contains("{\"component\":\"X\"}"), "the page JSON lands in the payload script");

        String override = new ThymeleafInertiaRootTemplateRenderer("mount", "classpath:root-test.html")
                .render("{\"component\":\"X\"}", java.util.Map.of("greeting", "hey"));
        assertTrue(override.contains("<title>root-test-override</title>"), "the override wins");
        assertTrue(override.contains("data-override=\"yes\""));
    }

    @Test
    void headFragmentOverrideKeepsTheMachineryAndInjectsTheApplicationHead() {
        String html = new ThymeleafInertiaRootTemplateRenderer("mount", "classpath:head-fragment.html")
                .render("{\"component\":\"X\"}", java.util.Map.of("greeting", "hey"));

        // the application's own head bits from the minimal fragment
        assertTrue(html.contains("<title>fragment-demo</title>"));
        assertTrue(html.contains("content=\"head-fragment-test\""));
        // the bundled machinery is untouched: payload script + root mount
        assertTrue(html.contains("data-page=\"mount\""));
        assertTrue(html.contains("{\"component\":\"X\"}"), "page payload from the bundled layout");
        assertTrue(html.contains("id=\"mount\""));
    }


}
