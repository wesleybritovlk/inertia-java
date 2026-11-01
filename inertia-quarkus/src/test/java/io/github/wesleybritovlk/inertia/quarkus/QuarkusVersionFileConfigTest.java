package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Config-driven version-file behavior: an explicit {@code inertia.version-file} wins over the
 * static version, and dev-hot {@code off} skips the hot-awareness lookup entirely.
 */
@QuarkusTest
@TestProfile(QuarkusVersionFileConfigTest.VersionFileProfile.class)
public class QuarkusVersionFileConfigTest {

    public static class VersionFileProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "inertia.version", "ignored-when-file",
                    "inertia.version-file", "src/test/resources/vite-test/manifest.json",
                    "inertia.vite-dev-hot", "off");
        }
    }

    private static String shaOf(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @TestHTTPResource
    URI baseUri;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Test
    void theFileHashIsTheLiveVersion() throws Exception {
        String sha = shaOf(Files.readAllBytes(Path.of("src/test/resources/vite-test/manifest.json")));

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("/users"))
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", sha)
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "matching version = no 409");
    }
}
