package io.github.wesleybritovlk.inertia.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaHeaders;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.web.FileVersionProvider;
import io.github.wesleybritovlk.inertia.servlet.InertiaFilter;
import io.github.wesleybritovlk.inertia.servlet.InertiaPageWriter;
import io.github.wesleybritovlk.inertia.web.InertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.servlet.ServletInertiaRequest;
import io.github.wesleybritovlk.inertia.web.ThymeleafInertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.web.ViteInertiaRootTemplateRenderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plain unit coverage for the starter classes that the MockMvc flow does not reach:
 * version hashing, template overrides, validation bag merging, and converter contracts.
 */
class StarterUnitsTest {

    private static final String FIXTURE = "src/test/resources/vite-test/manifest.json";

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------- FileVersionProvider

    @Test
    void versionFileHashesContentsAndMissingFilesReturnNull() throws Exception {
        Path versioned = tempDir.resolve("manifest.json");
        Files.writeString(versioned, "{\"app.js\": \"hash\"}");

        FileVersionProvider provider = new FileVersionProvider(versioned.toString());
        String version = provider.version();
        assertEquals(64, version.length());
        assertEquals(version, provider.version()); // deterministic

        FileVersionProvider missing = new FileVersionProvider(tempDir.resolve("nope.json").toString());
        assertNull(missing.version());

        // classpath locations version from the classloader (cwd-independent)
        FileVersionProvider fromClasspath = new FileVersionProvider("classpath:vite-test/manifest.json");
        assertEquals(new FileVersionProvider(FIXTURE).version(), fromClasspath.version());
    }

    @Test
    void fileVersionProviderAcceptsFilePrefixedLocations() throws Exception {
        Path versioned = tempDir.resolve("manifest-file.json");
        java.nio.file.Files.writeString(versioned, "{\"assets\": {}}");

        FileVersionProvider provider = new FileVersionProvider("file:" + versioned.toString());
        assertEquals(new FileVersionProvider(versioned.toString()).version(), provider.version());
    }

    // ---------------------------------------------------------- root template renderer

    @Test
    void rendererFallsBackToBundledTemplateAndSupportsOverridesAndViewData() {
        String bundled = new ThymeleafInertiaRootTemplateRenderer("mount", "classpath:does/not/exist.html")
                .render("{\"component\":\"X\"}", null);
        assertTrue(bundled.contains("data-page=\"mount\""));
        assertTrue(bundled.contains("<div id=\"mount\">"));
        assertTrue(bundled.contains("{\"component\":\"X\"}"));

        String override = new ThymeleafInertiaRootTemplateRenderer("mount", "classpath:inertia-test-templates/root-override.html")
                .render("{\"component\":\"X\"}", Map.of("greeting", "hey"));
        assertTrue(override.contains("<div data-override=\"yes\">"));
        assertTrue(override.contains("\"component\":\"X\""));
        assertTrue(override.contains("hey"));
    }

    // ---------------------------------------------------------- ValidationErrors

    @Test
    void validationErrorsStoreFieldAndGlobalMessagesInBags() {
        Inertia inertia = newStarterTestInertia();
        org.springframework.validation.MapBindingResult binding =
                new org.springframework.validation.MapBindingResult(Map.of(), "user");
        binding.rejectValue("name", "NotBlank", "is required");
        binding.reject("global", "form invalid");

        ValidationErrors.store(inertia, binding, "default");

        Object stored = inertia.request().flash().peek("inertia.errors");
        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) stored;
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) bags.get("default");
        assertEquals("is required", bag.get("name"));
        assertEquals("form invalid", bag.get("user")); // global errors key on the object name
    }

    @Test
    void validationErrorsMergeIntoExistingBags() {
        Inertia inertia = newStarterTestInertia();
        inertia.request().flash().put("inertia.errors", new LinkedHashMap<>(Map.of(
                "existing", Map.of("field", "previous"))));

        org.springframework.validation.MapBindingResult binding =
                new org.springframework.validation.MapBindingResult(Map.of(), "thing");
        binding.rejectValue("name", "NotBlank", "is required");
        ValidationErrors.store(inertia, binding, "another");

        Object stored = inertia.request().flash().peek("inertia.errors");
        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) stored;
        assertEquals("previous", ((Map<?, ?>) bags.get("existing")).get("field"));
        assertEquals("is required", ((Map<?, ?>) bags.get("another")).get("name"));
    }

    // ---------------------------------------------------------- converter contract

    @Test
    void converterReadsNothingAndWritesOnlyInertiaTypes() {
        Inertia inertia = newStarterTestInertia();
        InertiaPageHttpMessageConverter converter = new InertiaPageHttpMessageConverter(
                new InertiaPageWriter(() -> inertia, DefaultRenderer));

        assertTrue(converter.canWrite(InertiaResult.class, null));
        assertTrue(converter.canWrite(InertiaPage.class, null));
        assertFalse(converter.canWrite(String.class, null));
        assertFalse(converter.canRead(InertiaPage.class, null));
        assertEquals(List.of(org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.MediaType.TEXT_HTML), converter.getSupportedMediaTypes(InertiaPage.class));
        assertThrows(UnsupportedOperationException.class, () -> converter.read(InertiaPage.class, null));
    }

    // ---------------------------------------------------------- writer + result writer branches

    @Test
    void writerAcceptsRedirectResultsAndRejectsForeignValues() throws java.io.IOException {
        Inertia inertia = newStarterTestInertia();
        InertiaPageWriter writer = new InertiaPageWriter(() -> inertia, DefaultRenderer);

        var response = new org.springframework.mock.web.MockHttpServletResponse();
        InertiaResult redirect = inertia.redirect("/after");
        writer.write(redirect, response);
        assertEquals(302, response.getStatus());
        assertEquals("/after", response.getHeader("Location"));

        assertThrows(IllegalStateException.class, () -> writer.write(null, response));
        assertThrows(IllegalStateException.class, () -> writer.write("not-an-inertia-type", response));
    }

    // ---------------------------------------------------------- servlet request stores

    @Test
    void sessionStoreWithoutSessionNeverCreatesOne() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/x");
        var adapter = ServletInertiaRequest.of(request);

        var store = adapter.flash();
        store.put("k", null);            // null put is a no-op, no session created
        assertNull(store.peek("k"));     // no session: peek is null
        assertNull(store.pull("k"));     // no session: pull is null
        assertNull(request.getSession(false), "no session must be created by peek/pull/null-put");

        adapter.flash().put("real", 1);
        assertNotNull(request.getSession(false), "non-null put creates the session");
        assertEquals(1, request.getSession(false).getAttribute("real"));
    }

    // ---------------------------------------------------------- filter internals

    @Test
    void committedResponsesSkipThePostChainUpgrade() throws Exception {
        var filter = new InertiaFilter(() -> null);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/x");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain chain = new org.springframework.mock.web.MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                ((org.springframework.mock.web.MockHttpServletResponse) res).flushBuffer();
            }
        };

        filter.doFilter(request, response, chain);

        assertTrue(response.isCommitted());
        assertNull(response.getHeader(InertiaHeaders.INERTIA), "no page headers were written by the post-chain pass");
    }

    // ---------------------------------------------------------- configuration factory methods

    @Test
    void versionProviderBeanHonorsFileAndStaticSources() {
        InertiaProperties properties = new InertiaProperties();
        properties.setVersionFile(tempDir.resolve("any.json").toString());
        var fromFile = new InertiaConfiguration().inertiaVersionProvider(properties);
        // (hot-aware wrapper when dev mode is enabled; semantics are the file hash)
        assertEquals(new FileVersionProvider(properties.getVersionFile()).version(), fromFile.version());

        InertiaProperties staticProps = new InertiaProperties();
        staticProps.setVersion("static-v");
        staticProps.setViteDevHot("off"); // no ambient flags in this assertion: static version wins
        var fromStatic = new InertiaConfiguration().inertiaVersionProvider(staticProps);
        assertEquals("static-v", fromStatic.version());
    }

    @Test
    void viteManifestDrivesVersioningWhenNothingExplicitIsConfigured() {
        // one property: the Vite manifest becomes BOTH the template source and the version
        InertiaProperties viteOnly = new InertiaProperties();
        viteOnly.setViteManifest("src/test/resources/vite-test/manifest.json");
        viteOnly.setViteDevHot("off"); // no ambient flags in this assertion
        var fromVite = new InertiaConfiguration().inertiaVersionProvider(viteOnly);
        var expected = new FileVersionProvider("src/test/resources/vite-test/manifest.json");
        assertEquals(expected.version(), fromVite.version());

        // an explicit version file still wins over the Vite manifest
        InertiaProperties explicitFile = new InertiaProperties();
        explicitFile.setViteManifest("src/test/resources/vite-test/manifest.json");
        explicitFile.setViteDevHot("off");
        explicitFile.setVersionFile(tempDir.resolve("explicit.json").toString());
        var fromExplicit = new InertiaConfiguration().inertiaVersionProvider(explicitFile);
        var explicitExpected = new FileVersionProvider(tempDir.resolve("explicit.json").toString());
        assertEquals(explicitExpected.version(), fromExplicit.version());

        // null sources fall through to the static version
        InertiaProperties nulls = new InertiaProperties();
        nulls.setVersionFile(null);
        nulls.setViteManifest(null);
        nulls.setViteEntry(null);
        nulls.setViteBase(null);
        nulls.setViteDevHot("off"); // ambient flags must not flip the static version
        assertEquals("1", new InertiaConfiguration().inertiaVersionProvider(nulls).version());
        assertEquals(null, nulls.getViteManifest());
        assertEquals(null, nulls.getViteBase());
        assertEquals(null, nulls.getViteEntry());
    }

    @Test
    void hotFlagFileDisablesVersioningLaravelStyle() throws Exception {
        // during the vite dev loop the version must be null (no versioned 409s for pages
        // driven by HMR); without the flag the manifest hash is back
        InertiaProperties properties = new InertiaProperties();
        properties.setViteManifest("src/test/resources/vite-test/manifest.json");
        properties.setViteDevHot(tempDir.resolve(".hot-inertia").toString());
        var provider = new InertiaConfiguration().inertiaVersionProvider(properties);

        java.nio.file.Files.writeString(java.nio.file.Path.of(properties.getViteDevHot()), "5173");
        assertNull(provider.version());

        java.nio.file.Files.delete(java.nio.file.Path.of(properties.getViteDevHot()));
        assertEquals(new FileVersionProvider(properties.getViteManifest()).version(), provider.version());
    }

    @Test
    void offStopsDevHotDetectionEntirely() {
        assertTrue(InertiaConfiguration.devHotOff(propsWithViteDevHot("OFF")),
                "the off value is case-insensitive");
        assertFalse(InertiaConfiguration.devHotOff(propsWithViteDevHot(" hot flag ")),
                "only the exact off value stops detection");
        // default: detection on via the auto-scan
        assertFalse(InertiaConfiguration.devHotOff(new InertiaProperties()));
        // off yields no candidates: nothing ever flips to dev mode
        List<Path> candidates = InertiaConfiguration.hotCandidates(propsWithViteDevHot(" off "));
        assertTrue(candidates.isEmpty());
    }

    private static InertiaProperties propsWithViteDevHot(String value) {
        InertiaProperties properties = new InertiaProperties();
        properties.setViteDevHot(value);
        return properties;
    }

    @Test
    void writerAndConverterCoverBothOutputKinds() throws java.io.IOException {
        Inertia inertia = newStarterTestInertia();
        InertiaPageWriter writer = new InertiaPageWriter(() -> inertia, DefaultRenderer);

        var response = new org.springframework.mock.web.MockHttpServletResponse();
        // redirect kind: JSON body skipped, status + Location written
        writer.write(inertia.redirect("/after-redirect"), response);
        assertEquals(302, response.getStatus());

        InertiaResult jsonPage = inertia.result(
                inertia.render("Writer/Json").with("n", 1));
        var jsonResponse = new org.springframework.mock.web.MockHttpServletResponse();
        writer.write(jsonPage, jsonResponse);
        assertTrue(new String(jsonResponse.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"n\":1"));

        // converter: media types getter + samsung null output guard
        InertiaPageHttpMessageConverter converter = new InertiaPageHttpMessageConverter(
                new InertiaPageWriter(() -> inertia, DefaultRenderer));
        assertEquals(List.of(org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.MediaType.TEXT_HTML), converter.getSupportedMediaTypes());
        assertThrows(IllegalStateException.class, () -> converter.write(jsonPage, null,
                new org.springframework.http.HttpOutputMessage() {
                    @Override
                    public org.springframework.http.HttpHeaders getHeaders() {
                        return new org.springframework.http.HttpHeaders();
                    }

                    @Override
                    public java.io.OutputStream getBody() {
                        return java.io.OutputStream.nullOutputStream();
                    }
                }));
    }

    @Test
    void hashOfDirectoriesJustDisablesVersioningAndUnreadableFilesFail() throws Exception {
        FileVersionProvider directory = new FileVersionProvider(tempDir.toString());
        assertNull(directory.version()); // not a regular file: versioning disabled
    }

    @Test
    void validationErrorsToleratesNullBagsAndNullMessages() {
        Inertia inertia = newStarterTestInertia();
        org.springframework.validation.MapBindingResult binding =
                new org.springframework.validation.MapBindingResult(Map.of(), "thing");
        binding.rejectValue("silent", "code"); // no default message -> skipped
        binding.rejectValue("spoken", "NotBlank", "has message");

        ValidationErrors.store(inertia, binding, null); // null bag -> "default"

        Object stored = inertia.request().flash().peek("inertia.errors");
        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) stored;
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) bags.get("default");
        assertEquals("has message", bag.get("spoken"));
        assertTrue(bag.containsKey("silent") == false);
    }

    @Test
    void rendererBeanActivatesViteSupportWhenManifestIsConfigured() {
        InertiaProperties withoutVite = new InertiaProperties();
        var plain = new InertiaConfiguration().inertiaRootTemplateRenderer(withoutVite);
        assertTrue(plain instanceof ThymeleafInertiaRootTemplateRenderer);

        InertiaProperties withVite = new InertiaProperties();
        withVite.setViteManifest("src/test/resources/vite-test/manifest.json");
        var vite = new InertiaConfiguration().inertiaRootTemplateRenderer(withVite);
        assertTrue(vite instanceof ViteInertiaRootTemplateRenderer);
    }

    @Test
    void pageSerializerFallsThroughDetectionWhenNoMapperBeanIsDefined() throws Exception {
        // registration uses whichever Jackson generation sits on the test classpath
        Class<?> mapperClass = knownMapperClassOrNull();
        org.junit.jupiter.api.Assumptions.assumeTrue(mapperClass != null, "no Jackson runtime in test scope");

        // no UNIQUE mapper bean: the ambiguous lookup degrades to the runtime detection path
        var ambiguousFactory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        ambiguousFactory.registerSingleton("m1", mapperClass.getDeclaredConstructor().newInstance());
        ambiguousFactory.registerSingleton("m2", mapperClass.getDeclaredConstructor().newInstance());
        var fromAmbiguous = new InertiaConfiguration().inertiaPageSerializer(ambiguousFactory);
        assertTrue(fromAmbiguous instanceof io.github.wesleybritovlk.inertia.core.ReflectiveJacksonPageSerializer);

        // javax-free path: no mapper bean defined anywhere — class detection constructs one
        var emptyFactory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var runtimeSerializer = new InertiaConfiguration().inertiaPageSerializer(emptyFactory);
        assertTrue(runtimeSerializer instanceof io.github.wesleybritovlk.inertia.core.ReflectiveJacksonPageSerializer);
    }

    private static Class<?> knownMapperClassOrNull() {
        for (String name : List.of("com.fasterxml.jackson.databind.ObjectMapper", "tools.jackson.databind.ObjectMapper")) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // next generation
            }
        }
        return null;
    }

    // ---------------------------------------------------------- test support

    private static final InertiaRootTemplateRenderer DefaultRenderer = new InertiaRootTemplateRenderer() {
        @Override
        public String render(String pageJson, Map<String, Object> viewData) {
            return pageJson;
        }
    };

    private static Inertia newStarterTestInertia() {
        return new Inertia(() -> null, new io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer(), TestRequest.get("/x"));
    }

    private static final class TestRequest implements io.github.wesleybritovlk.inertia.core.InertiaRequest {

        private final String path;
        private final LinkedHashMap<String, Object> flashData = new LinkedHashMap<>();

        private TestRequest(String path) {
            this.path = path;
        }

        static TestRequest get(String path) {
            return new TestRequest(path);
        }

        @Override
        public String method() {
            return "GET";
        }

        @Override
        public String header(String name) {
            return null;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String query() {
            return "";
        }

        @Override
        public io.github.wesleybritovlk.inertia.core.InertiaFlashStore flash() {
            return new io.github.wesleybritovlk.inertia.core.InertiaFlashStore() {
                @Override
                public Object pull(String key) {
                    return flashData.remove(key);
                }

                @Override
                public void put(String key, Object value) {
                    flashData.put(key, value);
                }

                @Override
                public Object peek(String key) {
                    return flashData.get(key);
                }
            };
        }
    }
}
