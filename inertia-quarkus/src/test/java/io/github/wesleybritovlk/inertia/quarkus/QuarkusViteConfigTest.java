package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Config-driven Vite behavior: the manifest (dev hot off) drives both the version hash in the
 * 409 guard and the {@code @vite}-equivalent root template variables.
 */
@QuarkusTest
@TestProfile(QuarkusViteConfigTest.ViteManifestProfile.class)
public class QuarkusViteConfigTest {

    public static class ViteManifestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "inertia.vite-manifest", "src/test/resources/vite-test/manifest.json",
                    "inertia.vite-dev-hot", "off");
        }
    }

    private static String shaOf(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void manifestHashDrivesThe409GuardAndViteTemplateVariables() throws Exception {
        String sha = shaOf(Files.readAllBytes(Path.of("src/test/resources/vite-test/manifest.json")));

        HttpResponse<String> stale = client.send(HttpRequest.newBuilder(baseUri.resolve("/users"))
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "stale")
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, stale.statusCode(), "the manifest hash drives the 409 guard");
        assertEquals(sha, stale.headers().firstValue("X-Inertia-Version").orElse(""));

        HttpResponse<String> html = client.send(
                HttpRequest.newBuilder(baseUri.resolve("/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(html.body().contains("src=\"/assets/app-Hx987.js\""),
                "@vite-equivalent app script reached the template");
        assertTrue(html.body().contains("/assets/vendor-abc123.css"),
                "the css walk reached lazily imported chunks");
        assertTrue(html.body().contains("/assets/Index-vW234.css"),
                "transitive import css reached the template");
    }

    @TestHTTPResource
    URI baseUri;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
}
