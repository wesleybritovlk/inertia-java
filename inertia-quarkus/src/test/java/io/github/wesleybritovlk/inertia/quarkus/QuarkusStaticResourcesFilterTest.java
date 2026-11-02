package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Static asset serving on the RESTEasy-classic/Undertow pathway (META-INF/resources). */
class QuarkusStaticResourcesFilterTest {

    @Test
    void servesClasspathAssetsWithTheRightTypeAndFallsThroughForUnknownPaths() throws Exception {
        var filter = new QuarkusStaticResourcesFilter();
        var mock = ServletTestDoubles.get("/assets/test-fixture.js");
        var chain = new RecordingChain();

        filter.doFilter(mock.request, mock.response, chain);

        assertTrue(chain.dispatched.isEmpty(), "the fixture asset is served directly");
        assertEquals("application/javascript", mock.responseHeaders.get("Content-Type"));
        assertTrue(mock.recordedBody.toString().contains("static-filter-fixture"), "the fixture body streamed");

        // unknown assets keep the default behavior (transparent to the app)
        var unknown = ServletTestDoubles.get("/assets/absent.js");
        var unknownChain = new RecordingChain();
        filter.doFilter(unknown.request, unknown.response, unknownChain);
        assertEquals(1, unknownChain.dispatched.size(), "missing assets pass through to the app");
        assertTrue(unknown.recordedBody.isEmpty(), "no body was written for the missing asset");
    }

    @Test
    void contentTypesCoverTheUsualViteOutputsAndNonGetPassesThrough() throws Exception {
        var filter = new QuarkusStaticResourcesFilter();

        // every content-type branch of the mapping table
        for (var entry : java.util.Map.<String, String>ofEntries(
                java.util.Map.entry("test.css", "text/css;charset=UTF-8"),
                java.util.Map.entry("index.json", "application/json"),
                java.util.Map.entry("page.map", "application/json"),
                java.util.Map.entry("doc.svg", "image/svg+xml"),
                java.util.Map.entry("pic.png", "image/png"),
                java.util.Map.entry("pic.jpg", "image/jpeg"),
                java.util.Map.entry("pic.webp", "image/webp"),
                java.util.Map.entry("icon.ico", "image/x-icon"),
                java.util.Map.entry("font.woff", "font/woff"),
                java.util.Map.entry("font.woff2", "font/woff2"),
                java.util.Map.entry("font.ttf", "font/ttf"),
                java.util.Map.entry("text.txt", "text/plain;charset=UTF-8"),
                java.util.Map.entry("data.xml", "application/xml"),
                java.util.Map.entry("FILE.NoExt", "application/octet-stream")).entrySet()) {
            var mock = ServletTestDoubles.get("/assets/" + entry.getKey());
            filter.doFilter(mock.request, mock.response, new RecordingChain());
            assertEquals(entry.getValue(), mock.responseHeaders.get("Content-Type"),
                    "content type for " + entry.getKey());
        }

        // POSTs are never intercepted: transparent pass-through
        var post = new ServletTestDoubles("POST", "/assets/test-fixture.js");
        var postChain = new RecordingChain();
        filter.doFilter(post.request, post.response, postChain);
        assertEquals(1, postChain.dispatched.size(), "non-GET passes through");
        assertTrue(post.recordedBody.isEmpty());

        // HEAD served with headers but no body
        var head = new ServletTestDoubles("HEAD", "/assets/test-fixture.js");
        filter.doFilter(head.request, head.response, new RecordingChain());
        assertEquals("application/javascript", head.responseHeaders.get("Content-Type"));
        assertTrue(head.recordedBody.isEmpty(), "HEAD writes no body");
    }
}
