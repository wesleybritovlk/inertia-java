package io.github.wesleybritovlk.inertia.quarkus;

import java.util.Set;
import java.util.logging.Logger;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.PageSerializer;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;
import io.github.wesleybritovlk.inertia.core.ReflectiveJacksonPageSerializer;
import io.github.wesleybritovlk.inertia.core.VersionProvider;
import io.github.wesleybritovlk.inertia.servlet.FileVersionProvider;
import io.github.wesleybritovlk.inertia.servlet.HotAwareVersionProvider;
import io.github.wesleybritovlk.inertia.servlet.HotFileScanner;
import io.github.wesleybritovlk.inertia.servlet.InertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.servlet.ThymeleafInertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.servlet.ViteInertiaRootTemplateRenderer;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

/**
 * CDI producers of the adapter internals (the Quarkus control plane, mirroring the Spring
 * auto-configuration): asset version provider (version file → Vite manifest → static, then
 * the hot-aware wrapper), page serializer (application mapper bean → classpath Jackson →
 * bundled dependency-free), root template renderer (bundled Thymeleaf + optional
 * {@code @vite}-equivalent Vite renderer over the manifest).
 *
 * <p>Applications override any of them with their own CDI beans annotated {@code @Alternative}
 * + {@code @Priority}; the {@link Inertia} instance itself is request-scoped work and lives
 * behind the request-scoped CDI bean <code>Inertia</code> (see the producers).</p>
 */
@ApplicationScoped
class InertiaBeans {

    private static final Logger LOG = Logger.getLogger(InertiaBeans.class.getName());

    private final InertiaConfig config;
    private final BeanManager beanManager;

    @Inject
    InertiaBeans(InertiaConfig config, BeanManager beanManager) {
        this.config = config;
        this.beanManager = beanManager;
    }

    @Produces
    @ApplicationScoped
    VersionProvider inertiaVersionProvider() {
        VersionProvider source;
        if (notBlank(config.versionFile().orElse(""))) {
            source = new FileVersionProvider(config.versionFile().orElse(""));
        } else if (notBlank(config.viteManifest().orElse(""))) {
            source = new FileVersionProvider(config.viteManifest().orElse(""));
        } else {
            String version = config.version();
            source = () -> version;
        }
        if (HotFileScanner.off(config.viteDevHot().orElse(""))) {
            return source; // dev mode off: no hot awareness needed
        }
        return new HotAwareVersionProvider(source, HotFileScanner.candidates(config.viteDevHot().orElse("")));
    }

    @Produces
    @ApplicationScoped
    PageSerializer inertiaPageSerializer() {
        // hybrid strategy, spring parity: the application-provided Jackson mapper first (its
        // modules and annotations apply), then a fresh classpath runtime, then the bundled
        // dependency-free serializer
        for (String mapperClassName : ReflectiveJacksonPageSerializer.detectionOrder()) {
            Class<?> mapperClass = classOrNull(mapperClassName);
            if (mapperClass == null) {
                continue;
            }
            Object provided = providedMapper(mapperClass);
            if (provided != null) {
                LOG.fine("Inertia serializer over the application mapper " + mapperClassName);
                return ReflectiveJacksonPageSerializer.over(provided);
            }
        }
        return ReflectiveJacksonPageSerializer.detected().orElseGet(PlainJsonPageSerializer::new);
    }

    @Produces
    @ApplicationScoped
    InertiaRootTemplateRenderer inertiaRootTemplateRenderer() {
        InertiaRootTemplateRenderer base = new ThymeleafInertiaRootTemplateRenderer(
                config.rootId(), config.templatePath());
        if (!notBlank(config.viteManifest().orElse(""))) {
            return base;
        }
        ViteInertiaRootTemplateRenderer.ManifestSource manifest = new ViteInertiaRootTemplateRenderer.ManifestSource(
                config.viteManifest().orElse(""), config.viteEntry().orElse(""), config.viteBase());
        ViteInertiaRootTemplateRenderer.HotMode hot = HotFileScanner.off(config.viteDevHot().orElse("")) ? null
                : new ViteInertiaRootTemplateRenderer.HotMode(HotFileScanner.candidates(config.viteDevHot().orElse("")),
                        config.viteDevUrl(), config.viteEntry().orElse(""), config.viteReactRefresh());
        return new ViteInertiaRootTemplateRenderer(base, manifest, hot);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /**
     * The application-provided Jackson mapper (exactly one CDI bean of that type), spring
     * parity for the {@code ObjectProvider} lookup; none or ambiguous → {@code null} and the
     * fallback chain continues.
     */
    private Object providedMapper(Class<?> mapperClass) {
        Set<jakarta.enterprise.inject.spi.Bean<?>> beans = beanManager.getBeans(mapperClass);
        if (beans.size() != 1) {
            return null; // none/ambiguous: the fallback chain covers it
        }
        jakarta.enterprise.inject.spi.Bean<?> bean = beans.iterator().next();
        var context = beanManager.createCreationalContext(bean);
        // the reference stays valid while this context exists; retained by the serializer
        return beanManager.getReference(bean, mapperClass, context);
    }

    /**
     * THE engine resources inject — the same name and ergonomics as the Spring bean and
     * inertia-laravel's {@code Inertia}: request-scoped, built by the protocol filter's
     * attachment so the shared props customizers and flash state run through it. CDI injects
     * normal-scoped beans as client proxies, so the field works on any resource scope and
     * resolves the CURRENT request's engine per invocation; outside the filter chain it
     * fails fast (the registry throws).
     */
    @Produces
    @RequestScoped
    Inertia inertia() {
        return CurrentInertia.current().engine();
    }
}
