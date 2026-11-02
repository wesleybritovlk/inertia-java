/**
 * Dependency-free Inertia.js protocol engine.
 *
 * <p>The flow for every adapter: build (or receive) a request-scoped {@link
 * io.github.wesleybritovlk.inertia.core.Inertia} over the {@link
 * io.github.wesleybritovlk.inertia.core.InertiaRequest} port, produce an {@link
 * io.github.wesleybritovlk.inertia.core.InertiaPage} in your controllers, resolve it with
 * {@link io.github.wesleybritovlk.inertia.core.Inertia#result(
 * io.github.wesleybritovlk.inertia.core.InertiaPage)} and write the {@link
 * io.github.wesleybritovlk.inertia.core.InertiaResult} to the native response. Middleware
 * rules ({@link io.github.wesleybritovlk.inertia.core.InertiaVersionGuard} before the chain,
 * {@link io.github.wesleybritovlk.inertia.core.InertiaResponseUpgrade} after) complete the
 * protocol.</p>
 */
package io.github.wesleybritovlk.inertia.core;
