package io.github.wesleybritovlk.inertia.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link PageSerializer} bound to a Jackson runtime via reflection — the library never
 * compiles against Jackson, and this adapter lives under the {@code PageSerializer} SPI, so
 * a Jackson major switch (2 {@code com.fasterxml.*} vs 3 {@code tools.jackson.*}) cannot
 * break the library. Jackson 2 and 3 have disjoint namespaces and may coexist on the
 * classpath; detection prefers the existing instance (for example the mapper an application
 * context builds, modules included) over a fresh one.
 *
 * <p>Prop values are serialized by the application's own Jackson conventions: annotations
 * ({@code @JsonFormat}, {@code @JsonIgnore}), {@code java.time} modules and mixin
 * customizations all apply. Trade-off: values relying on library-specific handling (such as
 * the JS-safe-integer {@code "$bigint"} wrapping of {@link PlainJsonPageSerializer}) are
 * emitted raw by Jackson — plug a custom {@link PageSerializer} when exact that behavior is
 * needed.</p>
 */
public final class ReflectiveJacksonPageSerializer implements PageSerializer {

    /** Jackson 2 mapper FQN (still the default inside Spring Boot 3 / Quarkus). */
    public static final String JACKSON_2 = "com.fasterxml.jackson.databind.ObjectMapper";

    /** Jackson 3 mapper FQN (the default inside Spring Boot 4). */
    public static final String JACKSON_3 = "tools.jackson.databind.ObjectMapper";

    private static final List<String> DETECTION_ORDER = List.of(JACKSON_2, JACKSON_3);

    private final Object mapper;
    private final Method writer;

    private ReflectiveJacksonPageSerializer(Object mapper, Method writer) {
        this.mapper = mapper;
        this.writer = writer;
    }

    /**
     * Binds an existing mapper instance (the best option when the application already
     * provides one: Spring Boot's configured {@code ObjectMapper} bean, Quarkus' mapper, the
     * user's own instance).
     */
    public static PageSerializer over(Object mapper) {
        Objects.requireNonNull(mapper, "Jackson ObjectMapper instance is required");
        return new ReflectiveJacksonPageSerializer(mapper, locateWriter(mapper.getClass()));
    }

    /**
     * Builds a fresh Jackson mapper from the first runtime present on the classpath
     * (Jackson 2 first, then Jackson 3). Empty when neither exists — callers must fall back
     * to {@link PlainJsonPageSerializer}.
     */
    public static Optional<PageSerializer> detected() {
        for (String mapperClass : DETECTION_ORDER) {
            try {
                Object mapper = construct(Class.forName(mapperClass));
                return Optional.of(over(mapper));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // runtime absent, or not constructible: try the next Jackson generation
            }
        }
        return Optional.empty();
    }

    /** The Jackson mapper class names in detection order. */
    public static List<String> detectionOrder() {
        return DETECTION_ORDER;
    }

    @Override
    public String serialize(Map<String, Object> page) {
        try {
            return (String) writer.invoke(mapper, page);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Inertia page serialization failed through the Jackson runtime", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime; // keep Jackson's own exception types visible to applications
            }
            throw new IllegalStateException("Inertia page serialization failed through the Jackson runtime", cause);
        }
    }

    private static Object construct(Class<?> mapperClass) throws ReflectiveOperationException {
        try {
            return mapperClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            // e.g. Jackson 3's protected ObjectMapper constructor: fall back to builder().build()
            Object builder = mapperClass.getMethod("builder").invoke(null);
            return builder.getClass().getMethod("build").invoke(builder);
        }
    }

    /**
     * Finds {@code String writeValueAsString(Object)}-shaped methods on the mapper type,
     * tolerating version-specific renames rather than depending on one exact signature.
     */
    private static Method locateWriter(Class<?> mapperType) {
        String preferred = "writeValueAsString";
        Method fallback = null;
        for (Method method : mapperType.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                    || String.class != method.getReturnType() || method.isSynthetic()) {
                continue;
            }
            if (method.getName().equals(preferred)) {
                return method;
            }
            if (fallback == null && method.getName().startsWith("writeValue")) {
                fallback = method;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(
                "No String returning writeValue(Object) method found on " + mapperType.getName()
                        + ": is this a Jackson ObjectMapper?");
    }
}
