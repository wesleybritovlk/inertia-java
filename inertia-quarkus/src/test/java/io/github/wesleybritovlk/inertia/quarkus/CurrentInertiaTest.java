package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaFlashStore;
import io.github.wesleybritovlk.inertia.core.InertiaHeaders;
import io.github.wesleybritovlk.inertia.core.InertiaRequest;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;

import jakarta.servlet.http.HttpServletResponse;

/** Registry semantics of the request-scoped (engine, response) pair (fail-fast outside the chain). */
class CurrentInertiaTest {

    private static Inertia engine() {
        return new Inertia(() -> "v", new PlainJsonPageSerializer(), new InertiaRequest() {
            @Override
            public String method() {
                return "GET";
            }

            @Override
            public String header(String name) {
                return InertiaHeaders.INERTIA;
            }

            @Override
            public String path() {
                return "/";
            }

            @Override
            public String query() {
                return "";
            }

            @Override
            public InertiaFlashStore flash() {
                return InertiaFlashStore.none();
            }
        });
    }

    private static HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, m, args) -> null);
    }

    @Test
    void currentWithoutBindFailsFast() {
        CurrentInertia.unbind();
        assertThrows(IllegalStateException.class, CurrentInertia::current,
                "outside the filter chain the registry is empty and the accessor fails loudly");
    }

    @Test
    void unbindWithoutBindIsANoOp() {
        CurrentInertia.unbind();
        CurrentInertia.unbind(); // idempotent
        assertThrows(IllegalStateException.class, CurrentInertia::current);
    }

    @Test
    void bindMakesThePairAvailableUntilUnbind() {
        HttpServletResponse response = response();
        Inertia bound = engine();

        CurrentInertia.bind(response, bound);
        var state = CurrentInertia.current();
        assertSame(bound, state.engine(), "the registry resolves exactly the bound engine");
        assertSame(response, state.response());
        CurrentInertia.unbind();
        assertThrows(IllegalStateException.class, CurrentInertia::current, "unbind clears the pair");
    }
}
