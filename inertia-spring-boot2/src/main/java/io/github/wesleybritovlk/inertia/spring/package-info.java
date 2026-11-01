/**
 * Spring Boot 2 integration for the Inertia protocol (auto-configuration via
 * {@code META-INF/spring.factories}, the registration every Boot 2.x line reads).
 *
 * <p>The {@code InertiaConfiguration} wires a request-scoped engine, the protocol filter
 * and the return value/converter bridge, on the legacy javax servlet glue. The root page
 * template — the adapter's {@code @inertia} equivalent — ships in
 * {@code META-INF/inertia/templates/root.html} (from {@code inertia-web}) and is
 * overridable via {@code inertia.template-path}.</p>
 */
package io.github.wesleybritovlk.inertia.spring;
