package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;


/**
 * End-to-end Quarkus adapter test over the real Undertow + RESTEasy classic stack: filter
 * rules (guard 409, Vary/redirect upgrades), the request-attached engine access from
 * resources, the JAX-RS writer kinds and the once/scroll wire shapes.
 */
@QuarkusTest
@TestProfile(QuarkusAdapterTest.DevHotOffProfile.class)
class QuarkusAdapterTest {

    /**
     * Deterministic adapter behavior for the suite: version hashing + guard rules must not
     * depend on whether a Vite dev session happens to be running on this machine (its hot
     * flag would put the version provider to dark, killing the 409 expectations).
     */
    public static class DevHotOffProfile implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("inertia.vite-dev-hot", "off");
        }
    }

    @TestHTTPResource
    URI baseUri;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(Redirect.NEVER)
            .cookieHandler(new CookieManager()) // sessions carry the flash store between requests
            .build();

    private HttpResponse<String> exchange(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path));
        for (int i = 0; i < headers.length; i += 2) {
            builder = builder.header(headers[i], headers[i + 1]);
        }
        return exchange(builder.GET().build());
    }

    private static String locationOf(HttpResponse<String> response, String header) {
        return response.headers().firstValue(header).orElse("");
    }

    // ---------------------------------------------------------------- visits

    @Test
    void inertiaVisitsAnswerJsonAfterTheFilterAndTheWriter() throws Exception {
        HttpResponse<String> response = get("/users",
                "X-Inertia", "true",
                "X-Inertia-Version", "v-test");
        assertEquals(200, response.statusCode());
        assertEquals("true", response.headers().firstValue("X-Inertia").orElse(""));
        assertEquals("X-Inertia", response.headers().firstValue("Vary").orElse(""));
        String json = response.body();
        assertTrue(json.contains("\"component\":\"Users/Index\""));
        assertTrue(json.contains("\"users\":[\"ana\",\"wei\"]"));
        assertTrue(json.contains("\"banner\":\"quarkus\""), "the shared customizer CDI hook ran");
        assertFalse(json.contains("only-on-demand"), "lazy props stay out of full visits");
    }

    @Test
    void plainVisitsRenderTheBundledRootTemplate() throws Exception {
        HttpResponse<String> response = get("/users");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("<!DOCTYPE html>"),
                "non-Inertia visits get HTML: " + response.body().substring(0, 60));
        assertTrue(response.body().contains("data-page=\"app\""), "bundled Thymeleaf root + page payload");
        assertTrue(response.body().contains("Users/Index"));
    }

    @Test
    void staleVersionsEscalateToA409FullPageVisit() throws Exception {
        HttpResponse<String> response = get("/users",
                "X-Inertia", "true",
                "X-Inertia-Version", "stale");
        assertEquals(409, response.statusCode());
        assertEquals("/users", locationOf(response, "X-Inertia-Location"));
        assertEquals("v-test", response.headers().firstValue("X-Inertia-Version").orElse(""));
    }

    @Test
    void partialReloadsResolveLazyPropsThroughTheWriter() throws Exception {
        HttpResponse<String> response = get("/users",
                "X-Inertia", "true",
                "X-Inertia-Version", "v-test",
                "X-Inertia-Partial-Component", "Users/Index",
                "X-Inertia-Partial-Data", "secret");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("only-on-demand"), "lazy prop resolved via the JAX-RS writer");
        assertFalse(response.body().contains("\"users\""), "only the requested prop walked through");
    }

    // ---------------------------------------------------------------- once + scroll

    @Test
    void onceAndScrollPropsRideThroughTheQuarkusAdapter() throws Exception {
        HttpResponse<String> response = get("/feed", "X-Inertia", "true", "X-Inertia-Version", "v-test");
        assertEquals(200, response.statusCode());
        String json = response.body();
        assertTrue(json.contains("\"onceProps\":{\"plans\":{\"prop\":\"plans\""), "once metadata kept");
        assertTrue(json.contains("\"basic\""));
        assertTrue(json.contains("\"scrollProps\":{\"posts\":{\"pageName\""));
        assertTrue(json.contains("\"currentPage\":1"));
        assertTrue(json.contains("\"mergeProps\":[\"posts.data\"]"));

        HttpResponse<String> remembered = get("/feed",
                "X-Inertia", "true",
                "X-Inertia-Version", "v-test",
                "X-Inertia-Except-Once-Props", "plans");
        assertFalse(remembered.body().contains("\"basic\""), "remembered once keys skip the value");
        assertTrue(remembered.body().contains("\"onceProps\":{\"plans\""), "metadata stays for rehydration");
    }

    // ---------------------------------------------------------------- redirects + flash

    @Test
    void redirectsUpgradeTo303AndFragmentRedirectsEscalate() throws Exception {
        HttpResponse<String> put = exchange(HttpRequest.newBuilder(baseUri.resolve("/update"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v-test")
                .build());
        assertEquals(303, put.statusCode(), "PUT redirect upgrades to see-other");
        assertEquals("/users", locationOf(put, "Location"));

        HttpResponse<String> fragment = get("/fragment", "X-Inertia", "true", "X-Inertia-Version", "v-test");
        assertEquals(409, fragment.statusCode());
        assertEquals("/docs#pricing", locationOf(fragment, "X-Inertia-Redirect"));

        HttpResponse<String> external = get("/external", "X-Inertia", "true", "X-Inertia-Version", "v-test");
        assertEquals(409, external.statusCode(), "external redirects 409 for Inertia visits");
        assertEquals("https://sso.example/sign", locationOf(external, "X-Inertia-Location"));
    }

    @Test
    void validationErrorsRoundTripThroughTheFlashStore() throws Exception {
        HttpResponse<String> store = exchange(HttpRequest.newBuilder(baseUri.resolve("/users-flash"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v-test")
                .build());
        assertEquals(302, store.statusCode(), "POST redirect stays 302 (only the PUT family upgrades)");
        assertEquals("/users", locationOf(store, "Location"));

        HttpResponse<String> rendered = get("/users",
                "X-Inertia", "true",
                "X-Inertia-Version", "v-test");
        assertTrue(rendered.body().contains("is required"),
                "the errors bag adopts the shared errors prop, got: " + rendered.body());
    }
}
