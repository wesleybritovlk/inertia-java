package io.github.wesleybritovlk.inertia.core;

/**
 * Asset version of the application, compared against the {@code X-Inertia-Version} header on
 * every GET visit (asset versioning, 409 conflict flow).
 *
 * <p>Any string that changes when frontend assets change works &mdash; a Vite/Rollup manifest
 * hash, a build timestamp, a git short hash. Returning {@code null} disables version checks
 * entirely.</p>
 */
@FunctionalInterface
public interface VersionProvider {

    /** Current asset version, or {@code null} to disable versioning. */
    String version();
}
