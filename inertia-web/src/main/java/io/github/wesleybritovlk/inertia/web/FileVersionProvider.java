package io.github.wesleybritovlk.inertia.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
            return hex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for asset version hashing", e);
        }
    }

    /** Hand-rolled hex ( {@code java.util.HexFormat} is runtime 17, the module rides release 8). */
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
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
        Path path = Paths.get(location.startsWith("file:") ? location.substring(5) : location);
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
