package io.github.wesleybritovlk.inertia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Behavioral tests of the protocol engine ({@link Inertia} + {@link PageResolver} + guards). */
class InertiaProtocolTest {

    private static final VersionProvider VERSION = () -> "v1";
    private static final PlainJsonPageSerializer SERIALIZER = new PlainJsonPageSerializer();

    private static Inertia engine(InertiaRequest request) {
        return new Inertia(VERSION, SERIALIZER, request);
    }

    private static Inertia engineNoVersion(InertiaRequest request) {
        return new Inertia(() -> null, SERIALIZER, request);
    }

    // ---------------------------------------------------------------- full visits

    @Test
    void nonInertiaVisitRendersRootTemplatePayload() {
        MockRequest request = MockRequest.get("/users?page=2");
        InertiaResult result = engine(request).result(new InertiaPage("Users/Index").with("page", 2));

        assertSame(InertiaResult.Kind.PAGE_HTML, result.kind());
        assertEquals(200, result.status());
        assertTrue(result.json().isPresent());
        String json = result.json().orElseThrow();
        assertTrue(json.contains("\"component\":\"Users/Index\""));
        assertTrue(json.contains("\"props\":{\"errors\":{},\"page\":2}"));
        assertTrue(json.contains("\"url\":\"/users?page=2\""));
        assertTrue(json.contains("\"version\":\"v1\""));
    }

    @Test
    void inertiaVisitAnswersPageJsonWithHeaders() {
        MockRequest request = MockRequest.get("/users").inertia();
        InertiaResult result = engine(request).result(new InertiaPage("Users/Index").with("a", 1));

        assertSame(InertiaResult.Kind.PAGE_JSON, result.kind());
        assertEquals(Map.of(
                InertiaHeaders.INERTIA, "true",
                "Content-Type", "application/json"), result.headers());
        assertTrue(result.json().orElseThrow().contains("\"props\":{\"errors\":{},\"a\":1}"));
    }

    @Test
    void lazyPropsAreExcludedFromFullVisits() {
        MockRequest request = MockRequest.get("/dashboard").inertia();
        InertiaPage page = engine(request).render("Dashboard", p -> p
                .with("count", () -> 1)
                .withLazy("report", () -> "heavy"));

        String json = engine(request).result(page).json().orElseThrow();
        assertTrue(json.contains("\"count\":1"));
        assertTrue(json.contains("\"report\"") == false);
    }

    // ---------------------------------------------------------------- partial reloads

    @Test
    void partialReloadReturnsOnlyMatchedPropsButKeepsAlways() {
        MockRequest request = MockRequest.get("/events").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Events")
                .header(InertiaHeaders.PARTIAL_DATA, "events");
        InertiaPage page = engine(request).render("Events", p -> p
                .with("events", () -> List.of("e1"))
                .with("categories", () -> "not-sent")
                .withAlways("auth.user", () -> "wesley")
                .withLazy("stats", () -> "only-when-asked"));

        String json = engine(request).result(page).json().orElseThrow();

        assertTrue(json.contains("\"events\":[\"e1\"]"));
        assertTrue(json.contains("\"auth\":{\"user\":\"wesley\"}"));// always bypasses the partial filter
        assertTrue(json.contains("\"categories\"") == false);
        assertTrue(json.contains("\"errors\":{}"));
    }

    @Test
    void partialReloadIgnoresHeaderWhenComponentDiffers() {
        MockRequest request = MockRequest.get("/events").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Other")
                .header(InertiaHeaders.PARTIAL_DATA, "events");
        InertiaPage page = engine(request).render("Events", p -> p
                .with("events", () -> List.of("e1"))
                .with("categories", () -> "included"));

        String json = engine(request).result(page).json().orElseThrow();

        assertTrue(json.contains("\"events\":[\"e1\"]"));
        assertTrue(json.contains("\"categories\":\"included\""));
    }

    @Test
    void exceptHeaderWinsOverOnly() {
        MockRequest request = MockRequest.get("/p").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "P")
                .header(InertiaHeaders.PARTIAL_DATA, "a,b")
                .header(InertiaHeaders.PARTIAL_EXCEPT, "b");
        InertiaPage page = engine(request).render("P", p -> p
                .with("a", 1)
                .with("b", 2)
                .with("c", 3));

        String json = engine(request).result(page).json().orElseThrow();
        assertTrue(json.contains("\"a\":1"));
        assertTrue(json.contains("\"b\":2") == false);
        assertTrue(json.contains("\"c\":3") == false); // only-list excludes unrequested props
    }

    @Test
    void partialWithSubPathCutsInsideContainerValues() {
        MockRequest request = MockRequest.get("/u").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "U")
                .header(InertiaHeaders.PARTIAL_DATA, "users.name");
        InertiaPage page = engine(request).render("U", p -> p.with("users", () -> Map.of("name", "wei", "age", 31)));

        String json = engine(request).result(page).json().orElseThrow();

        assertTrue(json.contains("\"name\":\"wei\""));
        assertTrue(json.contains("\"age\"") == false);
    }

    @Test
    void dotKeysOfPropsUnpackIntoNesting() {
        MockRequest request = MockRequest.get("/d").inertia();
        InertiaPage page = engine(request).render("D", p -> p.with("auth.user", () -> "wei"));

        String json = engine(request).result(page).json().orElseThrow();

        assertTrue(json.contains("\"auth\":{\"user\":\"wei\"}"));
    }

    // ---------------------------------------------------------------- deferred props

    @Test
    void deferredPropsEmitGroupMetadataOnInitialVisits() {
        MockRequest request = MockRequest.get("/r").inertia();
        InertiaPage page = engine(request).render("R", p -> p
                .with("core", 1)
                .withDeferred("permissions", () -> "later")
                .withDeferred("analytics", "slow-group", () -> "later"));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(Map.of(
                "default", List.of("permissions"),
                "slow-group", List.of("analytics")), parsed.get(PageKeys.DEFERRED_PROPS));
        assertTrue(((Map<?, ?>) parsed.get(PageKeys.PROPS)).containsKey("core"));
        assertTrue(((Map<?, ?>) parsed.get(PageKeys.PROPS)).containsKey("permissions") == false);
    }

    @Test
    void deferredPropsResolveOnPartialWhenRequested() {
        MockRequest request = MockRequest.get("/r").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "R")
                .header(InertiaHeaders.PARTIAL_DATA, "permissions");
        InertiaPage page = engine(request).render("R", p -> p.withDeferred("permissions", () -> "resolved-now"));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());

        assertEquals("resolved-now", ((Map<?, ?>) parsed.get(PageKeys.PROPS)).get("permissions"));
        assertNull(parsed.get(PageKeys.DEFERRED_PROPS));
    }

    @Test
    void rescuedDeferredPropsDropFromPropsAndReport() {
        MockRequest request = MockRequest.get("/r").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "R")
                .header(InertiaHeaders.PARTIAL_DATA, "bad");
        InertiaPage page = engine(request).render("R", p -> p
                .withDeferredRescued("bad", () -> {
                    throw new IllegalStateException("boom");
                }));

        Map<String, Object> parsed = engine(request).result(page).json().map(PageJson::parse).orElseThrow();

        assertEquals(List.of("bad"), parsed.get(PageKeys.RESCUED_PROPS));
        assertTrue(((Map<?, ?>) parsed.get(PageKeys.PROPS)).containsKey("bad") == false);
    }

    // ---------------------------------------------------------------- merge props

    @Test
    void mergeMetadataFollowsAppendPrependDeepAndMatchOn() {
        MockRequest request = MockRequest.get("/m").inertia();
        InertiaPage page = engine(request).render("M", p -> p
                .withMerged("messages", () -> List.of("m1"))
                .withMerged("feed", () -> List.of("f1"), o -> o.prepend())
                .withMerged("nested", () -> Map.of("values", List.of("n1")), o -> o.deep())
                .withMerged("users", () -> List.of(), o -> o.matchOn("id")));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());

        assertEquals(List.of("messages", "users"), parsed.get(PageKeys.MERGE_PROPS));
        assertEquals(List.of("feed"), parsed.get(PageKeys.PREPEND_PROPS));
        assertEquals(List.of("nested"), parsed.get(PageKeys.DEEP_MERGE_PROPS));
        assertEquals(List.of("users.id"), parsed.get(PageKeys.MATCH_PROPS_ON));
    }

    @Test
    void resetHeaderDropsMergeMetadataForMatchingPath() {
        MockRequest request = MockRequest.get("/m").inertia()
                .header(InertiaHeaders.RESET, "messages");
        InertiaPage page = engine(request).render("M", p -> p.withMerged("messages", () -> List.of("m1")));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());

        assertNull(parsed.get(PageKeys.MERGE_PROPS));
    }

    // ---------------------------------------------------------------- shared props

    @Test
    void pagePropsOverrideSharedProps() {
        MockRequest request = MockRequest.get("/s").inertia();
        Inertia engine = engine(request)
                .shared("role", "shared-from-request-scope");
        InertiaPage page = engine.render("S", p -> p.with("role", "page-overrides"));

        Map<String, Object> parsed = PageJson.parse(engine.result(page).json().orElseThrow());

        assertEquals("page-overrides", ((Map<?, ?>) parsed.get(PageKeys.PROPS)).get("role"));
    }

    @Test
    void sharedPropsMetadataTruncatesKeysAtFirstDot() {
        MockRequest request = MockRequest.get("/s").inertia();
        Inertia engine = engine(request);
        engine.shared("auth.user", () -> "wei")
                .shared("other", "v");
        InertiaPage page = engine.render("S");

        Map<String, Object> parsed = PageJson.parse(engine.result(page).json().orElseThrow());

        assertEquals(List.of("other", "auth"), parsed.get(PageKeys.SHARED_PROPS));
        assertTrue(((Map<?, ?>) parsed.get(PageKeys.PROPS)).get("auth").equals(Map.of("user", "wei")));
    }

    // ---------------------------------------------------------------- flash, errors, flags

    @Test
    void flashDataAdoptsThenConsumes() {
        MockRequest request = MockRequest.get("/f").inertia().flash(FlashKeys.FLASH, Map.of("status", "saved"));
        Inertia engine = engine(request);
        engine.render("F"); // warming nothing; flash consumed only by a rendered page result
        Map<String, Object> parsed = PageJson.parse(engine.result(new InertiaPage("F")).json().orElseThrow());

        assertEquals(Map.of("status", "saved"), parsed.get(PageKeys.FLASH));
        // consumed: a second render produces no flash key
        Map<String, Object> again = PageJson.parse(engine.result(new InertiaPage("F")).json().orElseThrow());
        assertTrue(again.containsKey(PageKeys.FLASH) == false);
    }

    @Test
    void clearHistoryFlagIsConsumedOnNextPage() {
        MockRequest request = MockRequest.get("/c").inertia();
        Inertia engine = engine(request);
        engine.clearHistory();

        Map<String, Object> parsed = PageJson.parse(engine.result(new InertiaPage("C")).json().orElseThrow());
        assertEquals(true, parsed.get(PageKeys.CLEAR_HISTORY));
        Map<String, Object> again = PageJson.parse(engine.result(new InertiaPage("C")).json().orElseThrow());
        assertTrue(again.containsKey(PageKeys.CLEAR_HISTORY) == false);
    }

    @Test
    void pageFlagsEmitOnlyWhenTrue() {
        MockRequest request = MockRequest.get("/e").inertia();
        InertiaPage page = engine(request).render("E", p -> p.encryptHistory().preserveBigIntegers());

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(true, parsed.get(PageKeys.ENCRYPT_HISTORY));
        assertEquals(true, parsed.get(PageKeys.PRESERVE_BIG_INTEGERS));
    }

    @Test
    void defaultErrorsBagIsUnwrappedAndBagHeaderRenamesIt() {
        // default bag, no bag header -> error fields sent direct (no wrapper)
        MockRequest noBag = MockRequest.get("/e").inertia().flash(FlashKeys.ERRORS, MockRequest.defaultBag("name", "required"));
        Map<String, Object> direct = PageJson.parse(engine(noBag).result(new InertiaPage("E")).json().orElseThrow());
        assertEquals(Map.of("name", List.of("required")), ((Map<?, ?>) direct.get(PageKeys.PROPS)).get("errors"));

        // bag header selects/re-names the default bag
        MockRequest withBag = MockRequest.get("/e").inertia().flash(FlashKeys.ERRORS, MockRequest.defaultBag("name", "required"))
                .header(InertiaHeaders.ERROR_BAG, "createUser");
        Map<String, Object> wrapped = PageJson.parse(engine(withBag).result(new InertiaPage("E")).json().orElseThrow());
        assertEquals(Map.of("createUser", Map.of("name", List.of("required"))), ((Map<?, ?>) wrapped.get(PageKeys.PROPS)).get("errors"));

        // no errors stored -> empty object always present
        Map<String, Object> empty = PageJson.parse(engine(MockRequest.get("/e").inertia()).result(new InertiaPage("E")).json().orElseThrow());
        assertEquals(Map.of(), ((Map<?, ?>) empty.get(PageKeys.PROPS)).get("errors"));
    }

    // ---------------------------------------------------------------- redirects + guard

    @Test
    void locationRedirectsInertiaVisitsWith409AndPlainVisitsWith302() {
        MockRequest visit = MockRequest.get("/somewhere").inertia();
        InertiaResult inertia = engine(visit).location("https://external.example");

        assertSame(InertiaResult.Kind.EXTERNAL_LOCATION_409, inertia.kind());
        assertEquals(409, inertia.status());
        assertEquals(Map.of(InertiaHeaders.LOCATION, "https://external.example"), inertia.headers());

        MockRequest plain = MockRequest.get("/somewhere");
        InertiaResult redirect = engine(plain).location("https://external.example");
        assertSame(InertiaResult.Kind.REDIRECT_302, redirect.kind());
        assertEquals(302, redirect.status());
    }

    @Test
    void versionGuardEscalatesGetInertiaVisits() {
        MockRequest request = MockRequest.get("/users?page=2").inertia()
                .header(InertiaHeaders.VERSION, "stale");
        InertiaResult conflict = InertiaVersionGuard.check(request, () -> "fresh");

        assertSame(InertiaResult.Kind.EXTERNAL_LOCATION_409, conflict.kind());
        assertEquals(409, conflict.status());
        assertEquals(Map.of(
                InertiaHeaders.LOCATION, "/users?page=2",
                InertiaHeaders.VERSION, "fresh"), conflict.headers());
    }

    @Test
    void versionGuardIgnoresMissingHeaderNonGetAndNullProvider() {
        assertNull(InertiaVersionGuard.check(MockRequest.get("/x").inertia(), VERSION));                     // no requirement
        assertNull(InertiaVersionGuard.check(MockRequest.get("/x").inertia().header(InertiaHeaders.VERSION, "v1"), VERSION)); // match
        assertNull(InertiaVersionGuard.check(MockRequest.get("/x").inertia(), () -> null));                  // disabled
        assertNull(InertiaVersionGuard.check(new MockRequest("POST", "/x", "").header(InertiaHeaders.VERSION, "stale").inertia(), VERSION));
        // null-version engine renders version:null instead of failing page building
        Map<String, Object> parsed = PageJson.parse(engineNoVersion(MockRequest.get("/x").inertia()).result(new InertiaPage("X")).json().orElseThrow());
        assertNull(parsed.get(PageKeys.VERSION));
    }

    @Test
    void responseUpgradeAddsVaryUpgradesNonGetAndEscalatesFragmentRedirects() {
        MockShell shell = new MockShell(302).location("/after");

        InertiaResponseUpgrade.apply(new MockRequest("PUT", "/x", "").inertia(), shell);
        assertEquals(303, shell.status);
        assertEquals(InertiaHeaders.INERTIA, shell.headers.get(InertiaHeaders.VARY));

        MockShell fragment = new MockShell(302).location("/docs#pricing");
        InertiaResponseUpgrade.apply(MockRequest.get("/x").inertia(), fragment);
        assertEquals(409, fragment.status);
        assertEquals("/docs#pricing", fragment.headers.get(InertiaHeaders.REDIRECT));
    }
}
