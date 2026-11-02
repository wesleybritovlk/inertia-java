package io.github.wesleybritovlk.inertia.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Value semantics of the Vite settings objects: the pre-release-8 line exposed
 * ManifestSource/HotMode as records, so equals/hashCode/toString parity (content-based,
 * record-shaped strings) and the defensive snapshot of {@code hotFiles} are API parity.
 */
class WebValueTest {

    @TempDir
    Path tempDir;

    @Test
    void manifestSourceValueSemantics() {
        ViteInertiaRootTemplateRenderer.ManifestSource source =
                new ViteInertiaRootTemplateRenderer.ManifestSource("classpath:m.json", "src/app.js", "/");
        assertEquals(source, new ViteInertiaRootTemplateRenderer.ManifestSource("classpath:m.json", "src/app.js", "/"));
        assertEquals(source.hashCode(),
                new ViteInertiaRootTemplateRenderer.ManifestSource("classpath:m.json", "src/app.js", "/").hashCode());
        assertNotEquals(source, new ViteInertiaRootTemplateRenderer.ManifestSource("other.json", "src/app.js", "/"));
        assertNotEquals(source, new ViteInertiaRootTemplateRenderer.ManifestSource("classpath:m.json", "src/vendor.js", "/"));
        assertNotEquals(source, new ViteInertiaRootTemplateRenderer.ManifestSource("classpath:m.json", "src/app.js", "/other"));
        assertNotEquals(source, null);
        assertFalse(source.equals(new ViteInertiaRootTemplateRenderer.HotMode(List.of(), "", "", "")),
                "different settings kinds never compare equal");
        assertTrue(source.toString().startsWith("ManifestSource[manifestLocation="));
        assertEquals("classpath:m.json", source.manifestLocation());
        assertEquals("src/app.js", source.entry());
        assertEquals("/", source.base());
        assertEquals(new ViteInertiaRootTemplateRenderer.ManifestSource(null, null, null),
                new ViteInertiaRootTemplateRenderer.ManifestSource(null, null, null), "records tolerate null components");
    }

    @Test
    void hotModeValueSemanticsSnapshotsTheFlags() throws Exception {
        Path flag = tempDir.resolve(".hot-inertia");
        Files.writeString(flag, "5173");

        ViteInertiaRootTemplateRenderer.HotMode hot =
                new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5173", "src/app.js", "auto");

        assertEquals(hot, new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5173", "src/app.js", "auto"));
        assertEquals(hot.hashCode(),
                new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5173", "src/app.js", "auto").hashCode());
        assertNotEquals(hot, new ViteInertiaRootTemplateRenderer.HotMode(List.of(), "http://localhost:5173", "src/app.js", "auto"));
        assertNotEquals(hot,
                new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5174", "src/app.js", "auto"));
        assertNotEquals(hot, new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5173", "src/vendor.js", "auto"));
        assertNotEquals(hot, new ViteInertiaRootTemplateRenderer.HotMode(List.of(flag), "http://localhost:5173", "src/app.js", "false"));
        assertTrue(hot.toString().startsWith("HotMode[hotFiles="));
        assertEquals(List.of(flag), hot.hotFiles());
        assertEquals("src/app.js", hot.entry());
        assertEquals("auto", hot.reactRefresh());

        assertThrows(UnsupportedOperationException.class, () -> hot.hotFiles().add(flag),
                "the flag snapshot stays unmodifiable (List.copyOf parity)");
        assertTrue(hot.isEnabled(), "an existing flag enables hot mode");
        assertTrue(new ViteInertiaRootTemplateRenderer.HotMode(List.of(tempDir.resolve("absent")), "", "", "").isEnabled() == false,
                "absent flags keep hot mode disabled");
    }
}
