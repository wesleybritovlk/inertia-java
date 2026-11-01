package io.github.wesleybritovlk.inertia.spring;

import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.PageSerializer;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;
import io.github.wesleybritovlk.inertia.core.ReflectiveJacksonPageSerializer;
import io.github.wesleybritovlk.inertia.core.InertiaSharedPropsCustomizer;
import io.github.wesleybritovlk.inertia.core.VersionProvider;

import io.github.wesleybritovlk.inertia.web.FileVersionProvider;
import io.github.wesleybritovlk.inertia.web.HotAwareVersionProvider;
import io.github.wesleybritovlk.inertia.web.HotFileScanner;
import io.github.wesleybritovlk.inertia.servlet.javax.InertiaFilter;
import io.github.wesleybritovlk.inertia.servlet.javax.InertiaPageWriter;
import io.github.wesleybritovlk.inertia.web.InertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.servlet.javax.ServletInertiaRequest;
import io.github.wesleybritovlk.inertia.web.ThymeleafInertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.web.ViteInertiaRootTemplateRenderer;

import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot 2 auto-configuration (registered via {@code META-INF/spring.factories},
 * the mechanism Boot 2.x reads — the Jakarta twin uses {@code @AutoConfiguration} on the
 * newer lines). Same beans and behavior as the Jakarta adapter, on the legacy javax glue:
 *
 * <ul>
 *   <li>{@link Inertia} request-scoped bean: adapters create the current
 *       {@code HttpServletRequest} at scope realization time (no thread-local leaks into the
 *       core);</li>
 *   <li>protocol filter with the middleware rules;</li>
 *   <li>MVC return value handling for {@code InertiaPage}/{@code InertiaResult};</li>
 *   <li>isolated Thymeleaf root template renderer.</li>
 * </ul>
 */
@Configuration
@AutoConfigureAfter(name = {
        // the Jackson 2 auto-configuration of the Boot 2 line (Jackson 3 lives on the
        // modern line only; unknown names are safe: Boot guards unknown afterName strings)
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"})
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InertiaProperties.class)
public class InertiaConfiguration {

    @Bean
    @ConditionalOnMissingBean(VersionProvider.class)
    VersionProvider inertiaVersionProvider(InertiaProperties properties) {
        // priority: explicit version file > the Vite manifest (live versioning by default
        // when it drives templates) > static version; while the Vite dev hot flag is
        // present the version goes null (HMR drives changes; no stale 409s in dev)
        VersionProvider source;
        if (properties.getVersionFile() != null && !properties.getVersionFile().trim().isEmpty()) {
            source = new FileVersionProvider(properties.getVersionFile());
        } else if (properties.getViteManifest() != null && !properties.getViteManifest().trim().isEmpty()) {
            source = new FileVersionProvider(properties.getViteManifest());
        } else {
            String version = properties.getVersion();
            source = () -> version;
        }
        return wrapWithHotAware(properties, source);
    }

    private static VersionProvider wrapWithHotAware(InertiaProperties properties, VersionProvider source) {
        if (devHotOff(properties)) {
            return source; // dev mode off: no hot awareness needed
        }
        return new HotAwareVersionProvider(source, hotCandidates(properties));
    }

    @Bean
    @ConditionalOnMissingBean(PageSerializer.class)
    PageSerializer inertiaPageSerializer(ConfigurableListableBeanFactory beanFactory) {
        // hybrid strategy: the application-provided mapper first (Spring Boot/Quarkus/user,
        // with its modules), then a fresh Jackson instance from the classpath runtime, then
        // the bundled dependency-free serializer
        for (String mapperClassName : ReflectiveJacksonPageSerializer.detectionOrder()) {
            Class<?> mapperClass = classOrNull(mapperClassName);
            if (mapperClass == null) {
                continue;
            }
            Object provided = providedMapper(beanFactory, mapperClass);
            if (provided != null) {
                return ReflectiveJacksonPageSerializer.over(provided);
            }
        }
        return ReflectiveJacksonPageSerializer.detected().orElseGet(PlainJsonPageSerializer::new);
    }

    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object providedMapper(ConfigurableListableBeanFactory beanFactory, Class<?> mapperClass) {
        try {
            return beanFactory.getBeanProvider((Class<Object>) mapperClass).getIfAvailable();
        } catch (RuntimeException ambiguous) {
            return null; // no unique mapper bean: the user wires a PageSerializer instead
        }
    }

    /**
     * Hot flag candidates come from the shared scanner: empty (default) runs the auto-scan
     * (frontends keep the flag inside their OWN node_modules — universally git-ignored);
     * one or more comma-separated paths override the scan; the value {@code off} yields no
     * candidates (dev-mode detection disabled).
     */
    static List<java.nio.file.Path> hotCandidates(InertiaProperties properties) {
        return HotFileScanner.candidates(properties.getViteDevHot());
    }

    /** {@code devHotOff} mirrors the value {@code off}: the whole detection stack is skipped. */
    static boolean devHotOff(InertiaProperties properties) {
        return HotFileScanner.off(properties.getViteDevHot());
    }

    @Bean
    @ConditionalOnMissingBean(InertiaRootTemplateRenderer.class)
    InertiaRootTemplateRenderer inertiaRootTemplateRenderer(InertiaProperties properties) {
        InertiaRootTemplateRenderer base =
                new ThymeleafInertiaRootTemplateRenderer(properties.getRootId(), properties.getTemplatePath());
        if (properties.getViteManifest() == null || properties.getViteManifest().trim().isEmpty()) {
            return base;
        }
        ViteInertiaRootTemplateRenderer.ManifestSource manifest = new ViteInertiaRootTemplateRenderer.ManifestSource(
                properties.getViteManifest(), properties.getViteEntry(), properties.getViteBase());
        ViteInertiaRootTemplateRenderer.HotMode hot = devHotOff(properties) ? null
                : new ViteInertiaRootTemplateRenderer.HotMode(hotCandidates(properties),
                        properties.getViteDevUrl(), properties.getViteEntry(), properties.getViteReactRefresh());
        return new ViteInertiaRootTemplateRenderer(base, manifest, hot);
    }

    @Bean
    @RequestScope
    @ConditionalOnMissingBean(Inertia.class)
    Inertia inertia(PageSerializer serializer, VersionProvider versionProvider,
            List<InertiaSharedPropsCustomizer> customizers) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        Inertia inertia = new Inertia(versionProvider, serializer, ServletInertiaRequest.of(request));
        for (InertiaSharedPropsCustomizer customizer : customizers) {
            customizer.customize(inertia);
        }
        return inertia;
    }

    @Bean
    @ConditionalOnMissingBean(InertiaFilter.class)
    InertiaFilter inertiaFilter(VersionProvider versionProvider) {
        return new InertiaFilter(versionProvider);
    }

    @Bean
    FilterRegistrationBean<InertiaFilter> inertiaFilterRegistration(InertiaFilter filter, InertiaProperties properties) {
        FilterRegistrationBean<InertiaFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getFilterOrder());
        registration.addUrlPatterns("/*");
        registration.setName("inertiaFilter");
        return registration;
    }

    @Bean
    InertiaWebMvcSupport inertiaWebMvcSupport(ObjectProvider<Inertia> inertiaObject, InertiaRootTemplateRenderer renderer) {
        return new InertiaWebMvcSupport(new InertiaPageWriter(inertiaObject::getObject, renderer));
    }

    static final class InertiaWebMvcSupport implements WebMvcConfigurer {

        private final InertiaPageWriter writer;

        private InertiaWebMvcSupport(InertiaPageWriter writer) {
            this.writer = writer;
        }

        @Override
        public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
            // plain @Controller methods (annotation-based built-ins run before custom
            // handlers on purpose) — @RestController returns go through the converter below
            handlers.add(new InertiaPageReturnValueHandler(writer));
        }

        @Override
        public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            // always first: per type precedence for @RestController Inertia returns
            converters.add(0, new InertiaPageHttpMessageConverter(writer));
        }
    }
}
