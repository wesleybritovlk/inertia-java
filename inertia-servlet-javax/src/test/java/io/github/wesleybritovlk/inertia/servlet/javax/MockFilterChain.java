package io.github.wesleybritovlk.inertia.servlet.javax;

import java.util.ArrayList;
import java.util.List;

/** Test-support filter chain recording the invocations (no framework mock dependencies). */
final class MockFilterChain implements javax.servlet.FilterChain {

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
    public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response)
            throws java.io.IOException, javax.servlet.ServletException {
        dispatched.add(new Object[] { request, response });
        action.run();
    }
}
