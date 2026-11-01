package io.github.wesleybritovlk.inertia.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Root template renderer (the adapter's "{@code app.blade.php + @inertia}"): its own isolated
 * Thymeleaf engine with a bundled template, overridable by a classpath resource so the
 * application can ship its own root HTML and keep using Thymeleaf features (fragments,
 * layouts, whatever).
 *
 * <p>The bundled template exposes two variables: {@code inertiaPageJson} (serialized page
 * object) and {@code inertiaRootId}, plus every {@code withViewData} variable of the page.</p>
 */
public final class ThymeleafInertiaRootTemplateRenderer implements InertiaRootTemplateRenderer {

    static final String BUNDLED_TEMPLATE = "META-INF/inertia/templates/root.html";

    private final String rootId;
    private final String cachedTemplate;
    private final TemplateEngine engine;

    public ThymeleafInertiaRootTemplateRenderer(String rootId, String overrideTemplatePath) {
        this.rootId = rootId;
        this.cachedTemplate = loadTemplate(overrideTemplatePath);
        this.engine = createEngine();
    }

    @Override
    public String render(String pageJson, Map<String, Object> viewData) {
        Context context = new Context(Locale.getDefault());
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("inertiaPageJson", pageJson);
        variables.put("inertiaRootId", rootId);
        if (viewData != null) {
            variables.putAll(viewData);
        }
        context.setVariables(variables);
        StringWriter out = new StringWriter();
        // StringTemplateResolver treats the process() name as the template source content
        engine.process(cachedTemplate, context, out);
        return out.toString();
    }

    private static TemplateEngine createEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String loadTemplate(String overrideTemplatePath) {
        String override = overrideTemplatePath == null || overrideTemplatePath.isBlank()
                ? "classpath:templates/inertia.html"
                : overrideTemplatePath;
        String content = readResource(override);
        if (content != null) {
            return content;
        }
        content = readResource("classpath:" + BUNDLED_TEMPLATE);
        if (content != null) {
            return content;
        }
        throw new IllegalStateException(
                "Inertia root template not found: override " + override + " and bundled " + BUNDLED_TEMPLATE);
    }

    private static String readResource(String classpathLocation) {
        String path = classpathLocation.startsWith("classpath:") ? classpathLocation.substring("classpath:".length()) : classpathLocation;
        InputStream in = ThymeleafInertiaRootTemplateRenderer.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable Inertia root template: " + classpathLocation, e);
        }
    }
}
