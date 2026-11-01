package io.github.wesleybritovlk.inertia.servlet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.wesleybritovlk.inertia.core.VersionProvider;

/**
 * Asset version provider hashing the configured resource (Vite/Rollup manifest), inertia-laravel
 * parity ({@code hash_file}). Supports {@code classpath:} locations (immune to the working
 * directory) and filesystem paths. Missing resource disables versioning (returns {@code null});
 * read failures on readable-but-broken locations surface as IllegalStateException.
 */
public final class FileVersionProvider implements VersionProvider {

    private final String location;

    public FileVersionProvider(String location) {
        this.location = location;
    }

    static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for asset version hashing", e);
        }
    }

    @Override
    public String version() {
        if (location.startsWith("classpath:")) {
            try {
                return hash(InertiaResources.read(location));
            } catch (java.io.IOException missing) {
                return null; // absent classpath resource disables versioning
            }
        }
        Path path = Path.of(location.startsWith("file:") ? location.substring(5) : location);
        if (!Files.isRegularFile(path)) {
            return null; // missing file (or a directory): versioning disabled
        }
        try {
            return hash(Files.readAllBytes(path));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to hash asset version resource: " + location, e);
        }
    }
}
