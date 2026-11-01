package io.github.wesleybritovlk.inertia.spring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Resource locations for the starter: supports {@code classpath:<path>} (resolved via
 * the classloader — works from any working directory and inside packaged jars) and
 * plain filesystem paths ({@code file:} prefix optional). Demo projects use classpath
 * for build outputs (immune to where {@code mvn} was invoked) and filesystem paths for
 * dev-time flag files (written by the node process).
 */
final class StarterResources {

    private StarterResources() {
    }

    static byte[] read(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            return readClasspath(location.substring("classpath:".length()));
        }
        return Files.readAllBytes(Path.of(location.startsWith("file:") ? location.substring(5) : location));
    }

    static boolean exists(String location) {
        try {
            if (location.startsWith("classpath:")) {
                return threadLoader().getResourceAsStream(location.substring("classpath:".length())) != null
                        || StarterResources.class.getClassLoader().getResourceAsStream(location.substring("classpath:".length())) != null;
            }
            return Files.exists(Path.of(location.startsWith("file:") ? location.substring(5) : location));
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static byte[] readClasspath(String path) throws IOException {
        try (InputStream in = threadLoader().getResourceAsStream(path)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        try (InputStream in = StarterResources.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        throw new NoSuchFileException("classpath:" + path);
    }

    private static ClassLoader threadLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? StarterResources.class.getClassLoader() : loader;
    }
}
