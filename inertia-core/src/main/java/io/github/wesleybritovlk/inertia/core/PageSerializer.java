package io.github.wesleybritovlk.inertia.core;

import java.util.Map;

/**
 * Serializes the resolved page object to the JSON string shipped on Inertia responses (and
 * embedded in the root template for HTML renders). The default implementation,
 * {@link PlainJsonPageSerializer}, is dependency-free; replace it when the application needs
 * richer value handling (for example a Jackson-based serializer from the versions your own
 * app already ships).
 */
@FunctionalInterface
public interface PageSerializer {

    /** @param page ordered page object, see {@link PageKeys keys}. */
    String serialize(Map<String, Object> page);
}
