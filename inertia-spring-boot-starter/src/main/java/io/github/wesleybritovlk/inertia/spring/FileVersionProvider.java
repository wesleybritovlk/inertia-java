package io.github.wesleybritovlk.inertia.spring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.wesleybritovlk.inertia.core.VersionProvider;

/**
 * Asset version provider hashing a build manifest (Vite/Rollup style), inertia-laravel
 * parity ({@code hash_file}). Missing file disables versioning (returns {@code null}).
 */
final class FileVersionProvider implements VersionProvider {

    private final Path file;

    FileVersionProvider(String location) {
        this.file = resolve(location);
    }

    private static Path resolve(String location) {
        String path = location.startsWith("file:") ? location.substring("file:".length()) : location;
        return Path.of(path);
    }

    @Override
    public String version() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for asset version hashing", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to hash asset version file: " + file, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
