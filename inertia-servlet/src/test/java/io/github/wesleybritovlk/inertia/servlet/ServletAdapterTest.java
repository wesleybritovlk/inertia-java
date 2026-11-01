package io.github.wesleybritovlk.inertia.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaHeaders;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;
import io.github.wesleybritovlk.inertia.core.VersionProvider;

/**
 * Servlet glue of the adapter: request port + flash session store, the protocol filter (guard
 * 409, post-chain upgrade, attachments, non-request dispatches) and the result/page writers.
 */
class ServletAdapterTest {

    private static final VersionProvider VERSION = () -> "v1";

    private static Inertia engine(ServletInertiaRequest adapter) {
        return new Inertia(VERSION, new PlainJsonPageSerializer(), adapter);
    }

    // --------------------------------------------------------- request port

    @Test
    void requestPortReadsTheServletSurface() {
        ServletMocks mocks = ServletMocks.get("/users", "tab=x&r=2").inertia();
        ServletInertiaRequest adapter = ServletInertiaRequest.of(mocks.request);

        assertEquals("GET", adapter.method());
        assertEquals("/users", adapter.path());
        assertEquals("tab=x&r=2", adapter.query());
        assertEquals("/users?tab=x&r=2", adapter.url());
        assertEquals("true", adapter.header(InertiaHeaders.INERTIA));
        assertNotNull(adapter.flash(), "a store is always available");
    }

    @Test
    void flashStoreCreatesTheSessionOnlyOnRealWrites() {
        ServletMocks mocks = ServletMocks.get("/x");
        var store = ServletInertiaRequest.of(mocks.request).flash();
        store.put("empty", null); // null writes are no-ops
        assertNull(store.peek("empty"));
        assertNull(store.pull("empty"));
        assertTrue(!mocks.sessionCreated, "no session must exist before real data");

        store.put("valid", List.of(1));
        assertTrue(mocks.sessionCreated, "non-null put creates one");
        assertEquals(List.of(1), store.pull("valid"));
        assertNull(store.peek("valid"), "pull consumes");
    }

    // --------------------------------------------------------- filter

    @Test
    void staleVersionsFireThe409GuardBeforeTheChain() throws Exception {
        ServletMocks mocks = ServletMocks.get("/users", "x=1").inertia();
        mocks.requestHeaders.put(InertiaHeaders.VERSION, "stale");
        var chain = new MockFilterChain();

        new InertiaFilter(() -> "fresh").doFilter(mocks.request, mocks.response, chain);

        assertTrue(chain.dispatched.isEmpty(), "guard rejects: the chain must not run");
        assertEquals(409, mocks.status);
        assertEquals("/users?x=1", mocks.responseHeaders.get(InertiaHeaders.LOCATION));
        assertEquals("fresh", mocks.responseHeaders.get(InertiaHeaders.VERSION));
        assertNull(mocks.responseHeaders.get("Vary"),
                "the guard short-circuits before the upgrade pass");
    }

    @Test
    void guardRunsTheChainOnNonInertiaVisitsAndMatchingVersions() throws Exception {
        ServletMocks staleWithoutMarker = ServletMocks.get("/users", "x=1");
        staleWithoutMarker.requestHeaders.put(InertiaHeaders.VERSION, "stale"); // no X-Inertia

        var chain = new MockFilterChain();
        new InertiaFilter(() -> "fresh").doFilter(staleWithoutMarker.request, staleWithoutMarker.response, chain);
        assertEquals(1, chain.dispatched.size());
        assertEquals(0, staleWithoutMarker.status);
        assertNull(staleWithoutMarker.responseHeaders.get(InertiaHeaders.LOCATION));

        ServletMocks matching = ServletMocks.get("/users").inertia();
        matching.requestHeaders.put(InertiaHeaders.VERSION, "v1");
        var matchingChain = new MockFilterChain();
        new InertiaFilter(() -> "v1").doFilter(matching.request, matching.response, matchingChain);
        assertEquals(1, matchingChain.dispatched.size());
    }

    @Test
    void redirectResponsesUpgradeAndAttachmentsCloseBothWays() throws Exception {
        ServletMocks mocks = ServletMocks.request("PUT", "/form", "").inertia();
        AtomicInteger binds = new AtomicInteger();
        AtomicInteger unbinds = new AtomicInteger();
        var filter = new InertiaFilter(VERSION, new InertiaFilter.InertiaRequestAttachment() {
            @Override
            public void bind(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
                binds.incrementAndGet();
            }

            @Override
            public void unbind(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
                unbinds.incrementAndGet();
            }
        });
        var chain = new MockFilterChain(() -> mocks.response.setStatus(302));

        filter.doFilter(mocks.request, mocks.response, chain);

        assertEquals(1, binds.get());
        assertEquals(1, unbinds.get(), "unbind runs even when the chain throws");
        assertEquals(303, mocks.status, "PUT redirects upgrade to see-other");
        assertEquals(InertiaHeaders.INERTIA, mocks.responseHeaders.get("Vary"));
    }

    @Test
    void attachmentUnbindRunsAfterChainExceptions() throws Exception {
        ServletMocks mocks = ServletMocks.get("/fail").inertia();
        AtomicInteger unbinds = new AtomicInteger();
        var filter = new InertiaFilter(VERSION, new InertiaFilter.InertiaRequestAttachment() {
            @Override
            public void bind(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
                // no binding state needed
            }

            @Override
            public void unbind(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
                unbinds.incrementAndGet();
            }
        });
        var chain = new MockFilterChain(() -> {
            throw new IllegalStateException("handler blew up");
        });

        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(mocks.request, mocks.response, chain));
        assertEquals(1, unbinds.get());
    }

    @Test
    void committedResponsesSkipThePostChainUpgrade() throws Exception {
        ServletMocks mocks = ServletMocks.get("/done").inertia();
        var chain = new MockFilterChain(() -> { try { mocks.response.flushBuffer(); } catch (java.io.IOException e) { throw new AssertionError(e); } });

        new InertiaFilter(VERSION).doFilter(mocks.request, mocks.response, chain);

        assertTrue(mocks.committed);
        assertNull(mocks.responseHeaders.get("Vary"),
                "no page headers were written by the post-chain pass");
    }

    @Test
    void forwardDispatchesPassThroughTheFilterUntouched() throws Exception {
        ServletMocks mocks = ServletMocks.forward("/nested");
        var chain = new MockFilterChain();

        new InertiaFilter(VERSION).doFilter(mocks.request, mocks.response, chain);

        assertEquals(1, chain.dispatched.size(), "chain runs");
        assertEquals(0, mocks.status, "no guard nor upgrade touched the response");
    }

    // --------------------------------------------------------- writers

    @Test
    void resultWriterAppliesHeadersStatusAndJsonBody() throws Exception {
        ServletMocks mocks = ServletMocks.get("/x").inertia();
        Inertia engine = engine(ServletInertiaRequest.of(mocks.request));
        InertiaResult result = engine.result(engine.render("X").with("n", 1));
        assertEquals(InertiaResult.Kind.PAGE_JSON, result.kind());

        InertiaResultWriter.write(result, mocks.response);

        assertEquals(200, mocks.status);
        assertEquals("true", mocks.responseHeaders.get(InertiaHeaders.INERTIA));
        assertEquals("application/json", mocks.responseHeaders.get("Content-Type"));
        assertTrue(mocks.recordedBody.toString().contains("\"component\":\"X\""));
    }

    @Test
    void writerCoversJsonHtmlAndRedirectKindsAndRejectsForeignValues() throws Exception {
        ServletMocks inertiaVisit = ServletMocks.get("/x").inertia();
        Inertia inertia = engine(ServletInertiaRequest.of(inertiaVisit.request));
        InertiaPageWriter writer = new InertiaPageWriter(() -> inertia, (json, view) -> "<html>" + json + "</html>");

        writer.write(inertia.result(inertia.render("Writer/Json").with("n", 1)), inertiaVisit.response);
        assertTrue(inertiaVisit.recordedBody.toString().contains("\"n\":1"));

        ServletMocks plainVisit = ServletMocks.get("/x");
        Inertia plain = engine(ServletInertiaRequest.of(plainVisit.request));
        writer.write(plain.result(plain.render("Writer/Html").with("n", 1)), plainVisit.response);
        assertTrue(plainVisit.recordedBody.toString().contains("Writer/Html"));

        ServletMocks redirectResponse = ServletMocks.get("/x").inertia();
        writer.write(inertia.redirect("/after"), redirectResponse.response);
        assertEquals(302, redirectResponse.status);
        assertEquals("/after", redirectResponse.responseHeaders.get("Location"));
        assertEquals("", redirectResponse.recordedBody.toString(), "redirects carry no body");

        assertThrows(IllegalStateException.class, () -> writer.write("not-inertia", redirectResponse.response));
        assertThrows(IllegalStateException.class, () -> writer.write(null, redirectResponse.response));
    }
}
