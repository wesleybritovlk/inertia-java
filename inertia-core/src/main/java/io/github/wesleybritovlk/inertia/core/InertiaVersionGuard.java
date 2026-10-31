package io.github.wesleybritovlk.inertia.core;

/**
 * Asset-version guard, run <em>before</em> the request handler (adapter filter/interceptor
 * level): on GET Inertia visits whose {@code X-Inertia-Version} no longer matches the current
 * {@link VersionProvider}, the response must be a full-page-visit redirect (409 +
 * {@code X-Inertia-Location}) so the client reloads with fresh assets &mdash; Laravel
 * middleware parity, {@code Htmlable} url including query string.
 */
public final class InertiaVersionGuard {

    private InertiaVersionGuard() {
    }

    /**
     * @return the conflict result to send, or empty when the request may proceed.
     */
    public static InertiaResult check(InertiaRequest request, VersionProvider versionProvider) {
        if (!request.isInertiaVisit()) {
            return null;
        }
        String requested = request.header(InertiaHeaders.VERSION);
        if (requested == null || !"GET".equalsIgnoreCase(request.method())) {
            return null;
        }
        String current = versionProvider.version();
        if (current == null || current.equals(requested)) {
            return null;
        }
        return InertiaResult.versionConflict(request.url(), current);
    }
}
