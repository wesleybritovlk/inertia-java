package io.github.wesleybritovlk.inertia.servlet;

import java.util.ArrayList;
import java.util.List;

/** Test-support filter chain recording the invocations (no framework mock dependencies). */
final class MockFilterChain implements jakarta.servlet.FilterChain {

    final List<Object[]> dispatched = new ArrayList<>();
    private final Runnable action;

    MockFilterChain() {
        this(() -> {
        });
    }

    MockFilterChain(Runnable action) {
        this.action = action;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
            throws java.io.IOException, jakarta.servlet.ServletException {
        dispatched.add(new Object[] { request, response });
        action.run();
    }
}
