/**
 * Spring Boot integration for the Inertia protocol (auto-configuration on Boot 3 and 4).
 *
 * <p>The {@code InertiaConfiguration} wires a request-scoped engine, the protocol filter
 * and the return value/converter bridge. The root page template — the adapter's
 * {@code @inertia} equivalent — ships in {@code META-INF/inertia/templates/root.html} and is
 * overridable via {@code inertia.template-path}.</p>
 */
package io.github.wesleybritovlk.inertia.spring;
