package io.github.wesleybritovlk.inertia.quarkus;

import io.github.wesleybritovlk.inertia.core.Inertia;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request registry of the engine + servlet response pair, filled by the protocol filter's
 * attachment while the chain runs. Thread-scoped on purpose: synchronous Undertow +
 * RESTEasy-classic shipping means the same worker thread executes the filter, the RESTEasy
 * handler and the return value writer of one request — Spring solves the same sharing with
 * its request scope (RequestContextHolder); this registry is the adapter-side equivalent.
 * The share happens strictly inside the filter chain (bind before it, unbind
 * unconditionally after it), so worker-pool reuse stays safe.
 */
final class CurrentInertia {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    record State(HttpServletResponse response, Inertia engine) {
    }

    private CurrentInertia() {
    }

    static void bind(HttpServletResponse response, Inertia engine) {
        CURRENT.set(new State(response, engine));
    }

    /** @throws IllegalStateException outside the Inertia filter chain (attachment not executing). */
    static State current() {
        State state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException(
                    "No Inertia engine attached to the current thread. The filter must run before controllers "
                            + "(register it on '/*'): the page writers need the engine the controller built.");
        }
        return state;
    }

    static void unbind() {
        CURRENT.remove();
    }
}
