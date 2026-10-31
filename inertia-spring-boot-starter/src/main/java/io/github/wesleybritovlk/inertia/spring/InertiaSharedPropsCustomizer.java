package io.github.wesleybritovlk.inertia.spring;

import io.github.wesleybritovlk.inertia.core.Inertia;
import org.springframework.context.ApplicationContext;

/**
 * Hook implemented by beans to configure shared props on every request-scoped
 * {@link Inertia} instance (the adapter equivalent of the Laravel middleware share call).
 */
@FunctionalInterface
public interface InertiaSharedPropsCustomizer {

    void customize(Inertia inertia);
}
