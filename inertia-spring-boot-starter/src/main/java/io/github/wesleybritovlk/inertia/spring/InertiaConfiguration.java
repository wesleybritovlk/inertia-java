package io.github.wesleybritovlk.inertia.spring;

import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.PageSerializer;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;
import io.github.wesleybritovlk.inertia.core.ReflectiveJacksonPageSerializer;
import io.github.wesleybritovlk.inertia.core.VersionProvider;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot auto-configuration (Boot 3 and 4):
 *
 * <ul>
 *   <li>{@link Inertia} request-scoped bean adapters create the current
 *       {@code HttpServletRequest} at scope realization time (no thread-local leaks into the
 *       core);</li>
 *   <li>protocol filter with the middleware rules;</li>
 *   <li>MVC return value handling for {@code InertiaPage}/{@code InertiaResult};</li>
 *   <li>isolated Thymeleaf root template renderer.</li>
 * </ul>
 */
@AutoConfiguration(afterName = {
        // Jackson 2 default (Boot 3) and Jackson 3 location (Boot 4); unknown names are
        // ignored, so this stays safe across Boot majors
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InertiaProperties.class)
public class InertiaConfiguration {

    @Bean
    @ConditionalOnMissingBean(VersionProvider.class)
    VersionProvider inertiaVersionProvider(InertiaProperties properties) {
        if (properties.getVersionFile() == null || properties.getVersionFile().isBlank()) {
            String version = properties.getVersion();
            return () -> version;
        }
        return new FileVersionProvider(properties.getVersionFile());
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

    @Bean
    @ConditionalOnMissingBean(InertiaRootTemplateRenderer.class)
    InertiaRootTemplateRenderer inertiaRootTemplateRenderer(InertiaProperties properties) {
        return new ThymeleafInertiaRootTemplateRenderer(properties.getRootId(), properties.getTemplatePath());
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
    FilterRegistrationBean<InertiaFilter> inertiaFilter(InertiaProperties properties, VersionProvider versionProvider) {
        FilterRegistrationBean<InertiaFilter> registration = new FilterRegistrationBean<>(new InertiaFilter(versionProvider));
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
