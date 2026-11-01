package io.github.wesleybritovlk.inertia.core;

/**
 * Post-handler protocol application, shared by every adapter (the "middleware" side of the
 * Inertia flow): adds {@code Vary: X-Inertia}, upgrades generated redirects from 302 to 303
 * on non-GET methods, and escalates redirects carrying a {@code #fragment} into
 * {@code 409 + X-Inertia-Redirect} (fragment re-alignment without losing the flag).
 */
public final class InertiaResponseUpgrade {

    private InertiaResponseUpgrade() {
    }

    public static void apply(InertiaRequest request, InertiaResponseShell response) {
        response.header(InertiaHeaders.VARY, InertiaHeaders.INERTIA);
        int status = response.status();
        if (status != 302 || !request.isInertiaVisit()) {
            return;
        }
        String method = request.method().toUpperCase(java.util.Locale.ROOT);
        switch (method) {
            case "PUT":
            case "PATCH":
            case "DELETE":
            case "QUERY":
                response.status(303);
                return;
            default:
                String location = response.location();
                if (location != null && location.contains("#")) {
                    response.status(409);
                    response.header(InertiaHeaders.REDIRECT, location);
                }
        }
    }
}
