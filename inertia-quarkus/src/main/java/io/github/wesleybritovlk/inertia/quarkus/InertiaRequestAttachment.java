package io.github.wesleybritovlk.inertia.quarkus;

import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaSharedPropsCustomizer;
import io.github.wesleybritovlk.inertia.servlet.InertiaFilter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter attachment that turns each request dispatch into a scoped {@link Inertia} engine
 * (shared props customizers included). Spring scoping via the container; Quarkus scope here:
 * the adapter wires the engine itself and hands it (and the servlet response) to the writers.
 */
@ApplicationScoped
class InertiaRequestAttachment implements InertiaFilter.InertiaRequestAttachment {

    private final io.github.wesleybritovlk.inertia.core.VersionProvider versionProvider;
    private final io.github.wesleybritovlk.inertia.core.PageSerializer serializer;
    private final List<InertiaSharedPropsCustomizer> customizers;

    @Inject
    InertiaRequestAttachment(io.github.wesleybritovlk.inertia.core.VersionProvider versionProvider,
            io.github.wesleybritovlk.inertia.core.PageSerializer serializer,
            jakarta.enterprise.inject.Instance<InertiaSharedPropsCustomizer> customizers) {
        this.versionProvider = versionProvider;
        this.serializer = serializer;
        this.customizers = customizers.stream().toList();
    }

    @Override
    public void bind(HttpServletRequest request, HttpServletResponse response,
            io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
        Inertia engine = new Inertia(versionProvider, serializer, adapter);
        for (InertiaSharedPropsCustomizer customizer : customizers) {
            customizer.customize(engine);
        }
        CurrentInertia.bind(response, engine);
    }

    @Override
    public void unbind(HttpServletRequest request, HttpServletResponse response,
            io.github.wesleybritovlk.inertia.core.InertiaRequest adapter) {
        CurrentInertia.unbind();
    }
}
