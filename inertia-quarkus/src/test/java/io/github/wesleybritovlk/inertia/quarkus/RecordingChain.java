package io.github.wesleybritovlk.inertia.quarkus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Recording filter chain for the adapter tests. */
final class RecordingChain implements jakarta.servlet.FilterChain {

    final List<Object[]> dispatched = new ArrayList<>();

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
            throws IOException, jakarta.servlet.ServletException {
        dispatched.add(new Object[] { request, response });
    }
}
