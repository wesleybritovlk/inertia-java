package io.github.wesleybritovlk.inertia.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

/**
 * Resource locations for the adapter resources: supports {@code classpath:<path>} (resolved via
 * the classloader — works from any working directory and inside packaged jars) and
 * plain filesystem paths ({@code file:} prefix optional). Demo projects use classpath
 * for build outputs (immune to where {@code mvn} was invoked) and filesystem paths for
 * dev-time flag files (written by the node process).
 */
final class InertiaResources {

    private InertiaResources() {
    }

    static byte[] read(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            return readClasspath(location.substring("classpath:".length()));
        }
        return Files.readAllBytes(Paths.get(location.startsWith("file:") ? location.substring(5) : location));
    }

    static boolean exists(String location) {
        try {
            if (location.startsWith("classpath:")) {
                return threadLoader().getResourceAsStream(location.substring("classpath:".length())) != null
                        || InertiaResources.class.getClassLoader().getResourceAsStream(location.substring("classpath:".length())) != null;
            }
            return Files.exists(Paths.get(location.startsWith("file:") ? location.substring(5) : location));
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static byte[] readClasspath(String path) throws IOException {
        try (InputStream in = threadLoader().getResourceAsStream(path)) {
            if (in != null) {
                return readAll(in);
            }
        }
        try (InputStream in = InertiaResources.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                return readAll(in);
            }
        }
        throw new NoSuchFileException("classpath:" + path);
    }

    /** Java-8-safe read-all for the streams the classloader hands back ( {@code InputStream.readAllBytes} is 9+). */
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int read; (read = in.read(chunk)) != -1; ) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static ClassLoader threadLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? InertiaResources.class.getClassLoader() : loader;
    }
}
