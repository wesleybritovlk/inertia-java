package io.github.wesleybritovlk.inertia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
    void flashMergeAccumulatesOverwritesAndSkipsNullKeys() {
        MockRequest request = MockRequest.get("/f-flash").inertia();
        Inertia engine = engine(request)
                .flash(Map.of("first", 1))
                .flash(null)
                .flash(Map.of("second", 2, "first", 99));

        LinkedHashMap<String, Object> third = new LinkedHashMap<>();
        third.put(null, "skipped");
        third.put("third", 3);
        engine.flash(third);

        InertiaResult result = engine.result(new InertiaPage("F"));
        Map<String, Object> parsed = result.json().map(PageJson::parse).orElseThrow();

        assertEquals(Map.of("first", 99L, "second", 2L, "third", 3L), parsed.get(PageKeys.FLASH));
        // consumed once: the second page render carries no flash data
        Map<String, Object> again = engine.result(new InertiaPage("F")).json().map(PageJson::parse).orElseThrow();
        assertTrue(again.containsKey(PageKeys.FLASH) == false);
    }

    @Test
    void noneFlashStoreDegradesGracefully() {
        MockRequest noneFlash = MockRequest.noneFlash("/f-none");
        Inertia engine = engine(noneFlash)
                .flash(Map.of("ignored", true))
                .clearHistory()
                .preserveFragment();
        InertiaResult result = engine.result(new InertiaPage("X"));

        Map<String, Object> parsed = result.json().map(PageJson::parse).orElseThrow();
        assertTrue(parsed.containsKey(PageKeys.FLASH) == false);
        assertTrue(parsed.containsKey(PageKeys.CLEAR_HISTORY) == false);
        assertTrue(parsed.containsKey(PageKeys.PRESERVE_FRAGMENT) == false);
        assertEquals(Map.of(), ((Map<?, ?>) parsed.get(PageKeys.PROPS)).get("errors"));
    }

    @Test
    void renderConsumerAndPropFailureSinkWork() {
        // deferred props resolve only on partial reloads that request them
        MockRequest request = MockRequest.get("/f-hook").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "R")
                .header(InertiaHeaders.PARTIAL_DATA, "boom");
        List<Throwable> failures = new ArrayList<>();
        Inertia engine = engine(request)
                .onPropFailure(failures::add);

        InertiaPage page = engine.render("R", p -> p
                .with("name", "render-consumer")
                .withDeferredRescued("boom", () -> {
                    throw new RuntimeException("rescue-me");
                }));
        Map<String, Object> parsed = engine.result(page).json().map(PageJson::parse).orElseThrow();

        assertEquals(1, failures.size());
        assertEquals("rescue-me", failures.get(0).getMessage());
        assertEquals(List.of("boom"), parsed.get(PageKeys.RESCUED_PROPS));
        assertSame(request, engine.request());
    }

    @Test
    void renderRejectsBlankComponentsAndBlankKeys() {
        MockRequest request = MockRequest.get("/x").inertia();
        assertThrows(IllegalArgumentException.class, () -> engine(request).render("  "));
        assertThrows(IllegalArgumentException.class, () -> engine(request).render("X", p -> p.with(" ", 1)));
    }

    @Test
    void flagGettersDefaultToUnset() {
        InertiaPage page = engine(MockRequest.get("/x").inertia()).render("X");
        assertNull(page.isEncryptHistory());
        assertNull(page.isPreserveBigIntegers());
        page.withViewData(Map.of("a", 1)).withViewData("b", 2);
        assertEquals(Map.of("a", 1, "b", 2), page.view());
        assertTrue(page.component().equals("X"));
    }

    @Test
    void mergeOptionBuildersCoverAppendAtPrependAtAndModeSwitching() {
        MockRequest request = MockRequest.get("/m-cols").inertia();
        InertiaPage page = engine(request).render("M", p -> p
                .withMerged("a", () -> List.of(), o -> o.appendAt("sub"))
                .withMerged("b", () -> List.of(), o -> o.prependAt("tail"))
                .withMerged("c", () -> List.of(), o -> o.prepend().append())
                .withMerged("d", () -> List.of(), o -> o.append().prepend()));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());

        assertEquals(List.of("a.sub", "c"), parsed.get(PageKeys.MERGE_PROPS));
        assertEquals(List.of("b.tail", "d"), parsed.get(PageKeys.PREPEND_PROPS));
    }

    // ---------------------------------------------------------------- once props

    @SuppressWarnings("unchecked")
    private static Map<String, Object> onceEntry(Map<String, Object> parsed, String key) {
        Object metadata = parsed.get(PageKeys.ONCE_PROPS);
        if (!(metadata instanceof Map<?, ?> entries) ) {
            throw new AssertionError("missing onceProps metadata");
        }
        Object entry = ((Map<String, Object>) entries).get(key);
        if (!(entry instanceof Map<?, ?>)) {
            throw new AssertionError("missing onceProps entry: " + key);
        }
        return (Map<String, Object>) entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> prop(Map<String, Object> parsed, String path) {
        Map<String, Object> props = (Map<String, Object>) parsed.get(PageKeys.PROPS);
        return (Map<String, Object>) props.get(path);
    }

    @SuppressWarnings("unchecked")
    private static Object propValue(Map<String, Object> parsed, String key) {
        Map<String, Object> props = (Map<String, Object>) parsed.get(PageKeys.PROPS);
        return props.get(key);
    }

    @Test
    void oncePropsResolveWithMetadataOnFullVisits() {
        MockRequest request = MockRequest.get("/billing").inertia();
        InertiaPage page = engine(request).render("Billing", p -> p
                .withOnce("plans", () -> List.of("basic", "pro")));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(List.of("basic", "pro"), propValue(parsed, "plans"));
        Map<String, Object> entry = onceEntry(parsed, "plans");
        assertEquals("plans", entry.get("prop"));
        assertNull(entry.get("expiresAt"));
    }

    @Test
    void rememberedOnceKeysSkipResolutionButStillEmitMetadata() {
        MockRequest request = MockRequest.get("/upgrade").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "plans");
        InertiaPage page = engine(request).render("Upgrade", p -> p
                .withOnce("plans", () -> {
                    throw new IllegalStateException("must not resolve when remembered");
                })
                .with("current", "starter"));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertNull(propValue(parsed, "plans"), "remembered once props stay out of props");
        assertEquals("starter", propValue(parsed, "current"));
        Map<String, Object> entry = onceEntry(parsed, "plans");
        assertEquals("plans", entry.get("prop"));
        assertNull(entry.get("expiresAt"));
    }

    @Test
    void partialReloadsIgnoreTheRememberedKeysHeader() {
        MockRequest request = MockRequest.get("/billing").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Billing")
                .header(InertiaHeaders.PARTIAL_DATA, "plans")
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "plans");
        InertiaPage page = engine(request).render("Billing", p -> p
                .withOnce("plans", () -> List.of("basic")));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(List.of("basic"), propValue(parsed, "plans"));
    }

    @Test
    void partialReloadsStillEmitOnceMetadataForUnrequestedProps() {
        MockRequest request = MockRequest.get("/billing").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Billing")
                .header(InertiaHeaders.PARTIAL_DATA, "current");
        InertiaPage page = engine(request).render("Billing", p -> p
                .withOnce("plans", () -> List.of("basic"))
                .with("current", "starter"));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals("starter", propValue(parsed, "current"));
        assertNull(propValue(parsed, "plans"));
        assertEquals("plans", onceEntry(parsed, "plans").get("prop"));
    }

    @Test
    void customOnceKeysLinkPropsAcrossPages() {
        // page A: prop "memberRoles"; page B: prop "availableRoles"; both once key "roles"
        MockRequest memberPage = MockRequest.get("/team").inertia();
        Map<String, Object> memberParsed = PageJson.parse(engine(memberPage).result(engine(memberPage).render("Team/Index", p -> p
                .withOnce("memberRoles", () -> List.of("admin"), o -> o.as("roles")))).json().orElseThrow());
        assertEquals(List.of("admin"), propValue(memberParsed, "memberRoles"));
        assertEquals("memberRoles", onceEntry(memberParsed, "roles").get("prop"));

        // the invite page carries the same key: the remembered value is not re-sent
        MockRequest invitePage = MockRequest.get("/team/invite").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "roles");
        Map<String, Object> inviteParsed = PageJson.parse(engine(invitePage).result(engine(invitePage).render("Team/Invite", p -> p
                .withOnce("availableRoles", () -> List.of("admin"), o -> o.as("roles")))).json().orElseThrow());
        assertNull(propValue(inviteParsed, "availableRoles"));
        assertEquals("availableRoles", onceEntry(inviteParsed, "roles").get("prop"));
    }

    @Test
    void untilEmitsEpochExpiryMilliseconds() {
        MockRequest request = MockRequest.get("/dashboard").inertia();
        InertiaPage page = engine(request).render("Dashboard", p -> p
                .withOnce("rates", () -> List.of("usd"), o -> o.until(java.time.Duration.ofSeconds(60))));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        Object expiresAt = onceEntry(parsed, "rates").get("expiresAt");
        long expectedFloor = System.currentTimeMillis() + 55_000L;
        assertTrue(expiresAt instanceof Long expires && expires >= expectedFloor, "expiresAt in the future: " + expiresAt);
    }

    @Test
    void freshForcesRefreshDespiteTheRememberedKey() {
        MockRequest request = MockRequest.get("/billing").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "plans");
        InertiaPage page = engine(request).render("Billing", p -> p
                .withOnce("plans", () -> List.of("basic", "pro"), o -> o
                        .fresh()
                        .until(java.time.Instant.ofEpochMilli(1_800_000_000_000L))));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(List.of("basic", "pro"), propValue(parsed, "plans"));
        assertEquals(1_800_000_000_000L, onceEntry(parsed, "plans").get("expiresAt"));
    }

    @Test
    void onceOverDeferredAnnouncesThenResolvesOnTheFollowUpPartial() {
        MockRequest full = MockRequest.get("/dashboard").inertia();
        Map<String, Object> fullParsed = PageJson.parse(engine(full).result(engine(full).render("Dashboard", p -> p
                .withOnce("groups", () -> List.of("devs"), o -> o.deferred("stats")))).json().orElseThrow());
        assertNull(propValue(fullParsed, "groups"));
        assertEquals(Map.of("stats", List.of("groups")), fullParsed.get(PageKeys.DEFERRED_PROPS));
        assertEquals("groups", onceEntry(fullParsed, "groups").get("prop"));

        MockRequest partial = MockRequest.get("/dashboard").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Dashboard")
                .header(InertiaHeaders.PARTIAL_DATA, "groups")
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "groups");
        Map<String, Object> partialParsed = PageJson.parse(engine(partial).result(engine(partial).render("Dashboard", p -> p
                .withOnce("groups", () -> List.of("devs"), o -> o.deferred("stats")))).json().orElseThrow());
        assertEquals(List.of("devs"), propValue(partialParsed, "groups"));
        assertNull(partialParsed.get(PageKeys.DEFERRED_PROPS));
    }

    @Test
    void onceOverMergeSkipsValueAndMergeLabelTogether() {
        MockRequest resolved = MockRequest.get("/activity").inertia();
        Map<String, Object> resolvedParsed = PageJson.parse(engine(resolved).result(engine(resolved).render("Activity", p -> p
                .withOnce("timeline", () -> List.of("event"), o -> o.merged().matchOn("id")))).json().orElseThrow());
        assertEquals(List.of("event"), propValue(resolvedParsed, "timeline"));
        assertEquals(List.of("timeline"), resolvedParsed.get(PageKeys.MERGE_PROPS));
        assertEquals(List.of("timeline.id"), resolvedParsed.get(PageKeys.MATCH_PROPS_ON));

        MockRequest skipped = MockRequest.get("/activity").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "timeline");
        Map<String, Object> skippedParsed = PageJson.parse(engine(skipped).result(engine(skipped).render("Activity", p -> p
                .withOnce("timeline", () -> List.of("event"), o -> o.merged().matchOn("id")))).json().orElseThrow());
        assertNull(propValue(skippedParsed, "timeline"));
        assertNull(skippedParsed.get(PageKeys.MERGE_PROPS), "no new data, no merge label");
        assertNull(skippedParsed.get(PageKeys.MATCH_PROPS_ON));
        assertEquals("timeline", onceEntry(skippedParsed, "timeline").get("prop"));
    }

    @Test
    void sharedOncePropsJoinSharedPropsMetadataAndSkipOnRememberedKeys() {
        Inertia engine = new Inertia(VERSION, SERIALIZER, MockRequest.get("/x").inertia());
        engine.sharedOnce("countries", () -> List.of("br"));
        Map<String, Object> parsed = PageJson.parse(engine.result(engine.render("X", p -> p.with("a", 1))).json().orElseThrow());
        assertEquals(List.of("br"), propValue(parsed, "countries"));
        assertEquals(List.of("countries"), parsed.get(PageKeys.SHARED_PROPS));
        assertEquals("countries", onceEntry(parsed, "countries").get("prop"));

        Inertia remembered = new Inertia(VERSION, SERIALIZER, MockRequest.get("/x").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "countries"));
        remembered.sharedOnce("countries", () -> List.of("br"));
        Map<String, Object> rememberedParsed = PageJson.parse(remembered.result(remembered.render("X", p -> p.with("a", 1))).json().orElseThrow());
        assertNull(propValue(rememberedParsed, "countries"));
        assertEquals("countries", onceEntry(rememberedParsed, "countries").get("prop"));
    }

    @Test
    void freshFalseKeepsTheRememberedKeysSkipping() {
        MockRequest request = MockRequest.get("/billing").inertia()
                .header(InertiaHeaders.EXCEPT_ONCE_PROPS, "plans");
        InertiaPage page = engine(request).render("Billing", p -> p
                .withOnce("plans", () -> List.of("basic"), o -> o.fresh(false)));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertNull(propValue(parsed, "plans"));
    }

    // ---------------------------------------------------------------- scroll props

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scrollCursor(Map<String, Object> parsed, String path) {
        Object metadata = parsed.get(PageKeys.SCROLL_PROPS);
        if (!(metadata instanceof Map<?, ?> entries)) {
            throw new AssertionError("missing scrollProps metadata");
        }
        Object cursor = ((Map<String, Object>) entries).get(path);
        if (!(cursor instanceof Map<?, ?>)) {
            throw new AssertionError("missing scrollProps entry: " + path);
        }
        return (Map<String, Object>) cursor;
    }

    @Test
    void scrollPropsCarryCursorAndMergeLabelOnFullVisits() {
        MockRequest request = MockRequest.get("/posts").inertia();
        InertiaPage page = engine(request).render("Posts", p -> p
                .withScroll("posts", () -> List.of(Map.of("id", 1))));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        Object wrappedValue = prop(parsed, "posts").get("data");
        List<?> data = wrappedValue instanceof List<?> items ? items : List.of();
        assertEquals(1L, data.isEmpty() ? 0L : ((java.util.Map<?, ?>) data.get(0)).get("id"));
        assertEquals(List.of("posts.data"), parsed.get(PageKeys.MERGE_PROPS));
        Map<String, Object> cursor = scrollCursor(parsed, "posts");
        assertEquals("page", cursor.get("pageName"));
        assertEquals(1L, cursor.get("currentPage"));
        assertNull(cursor.get("previousPage"));
        assertNull(cursor.get("nextPage"));
        assertEquals(Boolean.FALSE, cursor.get("reset"));
    }

    @Test
    void scrollCursorFollowsThePageQueryParameter() {
        MockRequest request = MockRequest.get("/posts", "page=2").inertia();
        InertiaPage page = engine(request).render("Posts", p -> p
                .withScroll("posts", () -> List.of(), o -> o.page(1, true)));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        Map<String, Object> cursor = scrollCursor(parsed, "posts");
        assertEquals(2L, cursor.get("currentPage"), "the client-driven query parameter wins");
        assertEquals(1L, cursor.get("previousPage"));
        assertEquals(3L, cursor.get("nextPage"), "hasMore from the reload options");
    }

    @Test
    void scrollOptionsInitializeFirstVisitsAndHasMoreDrivesNext() {
        MockRequest request = MockRequest.get("/posts").inertia();
        InertiaPage page = engine(request).render("Posts", p -> p
                .withScroll("posts", () -> List.of(), o -> o.page(3, true)));

        Map<String, Object> cursor = scrollCursor(PageJson.parse(engine(request).result(page).json().orElseThrow()), "posts");
        assertEquals(3L, cursor.get("currentPage"));
        assertEquals(2L, cursor.get("previousPage"));
        assertEquals(4L, cursor.get("nextPage"));
    }

    @Test
    void scrollMetadataProviderWinsWithCursorStrings() {
        MockRequest request = MockRequest.get("/chat", "cursor=ignored").inertia();
        InertiaPage page = engine(request).render("Chat", p -> p
                .withScroll("messages", () -> List.of(), o -> o
                        .pageName("cursor")
                        .metadata(new ScrollMetadata() {
                            @Override
                            public String pageName() {
                                return "cursor";
                            }

                            @Override
                            public Object currentPage() {
                                return "c2";
                            }

                            @Override
                            public Object nextPage() {
                                return "c3";
                            }

                            @Override
                            public Object previousPage() {
                                return "c1";
                            }
                        })));

        Map<String, Object> cursor = scrollCursor(PageJson.parse(engine(request).result(page).json().orElseThrow()), "messages");
        assertEquals("cursor", cursor.get("pageName"));
        assertEquals("c2", cursor.get("currentPage"));
        assertEquals("c1", cursor.get("previousPage"));
        assertEquals("c3", cursor.get("nextPage"));
    }

    @Test
    void scrollResetRequestsFlagTheCursorWithoutDroppingTheLabel() {
        MockRequest request = MockRequest.get("/posts").inertia()
                .header(InertiaHeaders.RESET, "posts");
        InertiaPage page = engine(request).render("Posts", p -> p
                .withScroll("posts", () -> List.of()));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(List.of("posts.data"), parsed.get(PageKeys.MERGE_PROPS), "the label survives resets");
        assertEquals(Boolean.TRUE, scrollCursor(parsed, "posts").get("reset"));
    }

    @Test
    void customScrollWrappersLabelTheirOwnKey() {
        MockRequest request = MockRequest.get("/feed").inertia();
        InertiaPage page = engine(request).render("Feed", p -> p
                .withScroll("feed", () -> java.util.Map.of("items", List.of("a")), o -> o.wrapper("items")));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        assertEquals(List.of("a"), prop(parsed, "feed").get("items"));
        assertEquals(List.of("feed.items"), parsed.get(PageKeys.MERGE_PROPS));
    }

    @Test
    void deferredScrollAnnouncesOnlyThenResolvesWithCursorOnThePartial() {
        Prop pageFactory = null;
        MockRequest full = MockRequest.get("/timeline").inertia();
        InertiaPage fullPage = engine(full).render("Timeline", p -> p
                .withScroll("posts", () -> List.of("post"), o -> o.deferred("feed")));
        Map<String, Object> fullParsed = PageJson.parse(engine(full).result(fullPage).json().orElseThrow());
        assertNull(propValue(fullParsed, "posts"));
        assertEquals(Map.of("feed", List.of("posts")), fullParsed.get(PageKeys.DEFERRED_PROPS));
        assertNull(fullParsed.get(PageKeys.SCROLL_PROPS));
        assertNull(fullParsed.get(PageKeys.MERGE_PROPS), "no value resolved, no label");

        MockRequest partial = MockRequest.get("/timeline").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Timeline")
                .header(InertiaHeaders.PARTIAL_DATA, "posts");
        InertiaPage partialPage = engine(partial).render("Timeline", p -> p
                .withScroll("posts", () -> List.of("post"), o -> o.deferred("feed")));
        Map<String, Object> partialParsed = PageJson.parse(engine(partial).result(partialPage).json().orElseThrow());
        assertEquals(List.of("post"), prop(partialParsed, "posts").get("data"));
        assertEquals(List.of("posts.data"), partialParsed.get(PageKeys.MERGE_PROPS));
        assertEquals(1L, scrollCursor(partialParsed, "posts").get("currentPage"));
    }

    @Test
    void scrollPartialSubPathFilteringWalksTheValue() {
        MockRequest request = MockRequest.get("/posts").inertia()
                .header(InertiaHeaders.PARTIAL_COMPONENT, "Posts")
                .header(InertiaHeaders.PARTIAL_DATA, "posts.data");
        InertiaPage page = engine(request).render("Posts", p -> p
                .withScroll("posts", () -> java.util.Map.of(
                        "data", List.of("a"),
                        "meta", java.util.Map.of("page", 2))));

        Map<String, Object> parsed = PageJson.parse(engine(request).result(page).json().orElseThrow());
        Map<String, Object> posts = prop(parsed, "posts");
        assertEquals(List.of("a"), posts.get("data"));
        assertNull(posts.get("meta"), "only the requested sub-path walks through");
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

    @Test
    void responseUpgradeCoversRemainingMethodAndStatusBranches() {
        // QUERY/PATCH/DELETE all upgrade to see-other
        for (String method : List.of("QUERY", "PATCH", "DELETE")) {
            MockShell shell = new MockShell(302).location("/after-" + method.toLowerCase());
            InertiaResponseUpgrade.apply(new MockRequest(method, "/x", "").inertia(), shell);
            assertEquals(303, shell.status, method);
        }

        // non-Inertia redirect and non-302 statuses only get the Vary header
        MockShell plain = new MockShell(302).location("/after");
        InertiaResponseUpgrade.apply(MockRequest.get("/x"), plain);
        assertEquals(302, plain.status);

        MockShell page200 = new MockShell(200);
        InertiaResponseUpgrade.apply(MockRequest.get("/x").inertia(), page200);
        assertEquals(200, page200.status);
        assertEquals(InertiaHeaders.INERTIA, page200.headers.get(InertiaHeaders.VARY));
    }
}
