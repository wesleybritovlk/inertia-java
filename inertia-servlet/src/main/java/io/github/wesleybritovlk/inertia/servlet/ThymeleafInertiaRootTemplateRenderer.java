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
 * Root template renderer (the adapter's "{@code app.blade.php + @inertia}"): the bundled
 * layout carries ALL the machinery (vite dev blocks, the hash entry script, lazy-chunk css
 * and the {@code data-page} payload script), so applications usually do NOT ship a template
 * at all. When they do (classpath {@code templates/inertia.html}), the file can be:
 *
 * <ul>
 *   <li>a <strong>head fragment</strong> &mdash; the simple case: only the application's own
 *       head content (title, meta, fonts, analytics), rendered with the same variables and
 *       injected into the bundled layout's {@code <head>};</li>
 *   <li>a <strong>whole document</strong> &mdash; advanced case: replaces the entire layout,
 *       wiring the same variables itself ({@code inertiaPageJson}, {@code inertiaRootId},
 *       {@code inertiaUserHead}, {@code viteDevMode}, {@code viteClient}, {@code viteAppJs},
 *       {@code viteCss}, {@code viteReactRefresh} plus every {@code withViewData} entry).</li>
 * </ul>
 */
public final class ThymeleafInertiaRootTemplateRenderer implements InertiaRootTemplateRenderer {

    static final String BUNDLED_TEMPLATE = "META-INF/inertia/templates/root.html";

    private final String rootId;
    private final String layoutTemplate;
    private final String headFragment;
    private final TemplateEngine engine;

    public ThymeleafInertiaRootTemplateRenderer(String rootId, String overrideTemplatePath) {
        this.rootId = rootId;
        String override = loadTemplate(overrideTemplatePath);
        if (override == null) {
            this.layoutTemplate = readResource("classpath:" + BUNDLED_TEMPLATE);
            this.headFragment = null;
            if (layoutTemplate == null) {
                throw new IllegalStateException("Inertia bundled root template missing: " + BUNDLED_TEMPLATE);
            }
        } else if (isWholeDocument(override)) {
            this.layoutTemplate = override;
            this.headFragment = null;
        } else {
            this.layoutTemplate = readResource("classpath:" + BUNDLED_TEMPLATE);
            this.headFragment = override;
        }
        this.engine = createEngine();
    }

    @Override
    public String render(String pageJson, Map<String, Object> viewData) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("inertiaPageJson", pageJson);
        variables.put("inertiaRootId", rootId);
        if (viewData != null) {
            variables.putAll(viewData);
        }
        variables.put("inertiaUserHead", headFragment == null ? "" : process(headFragment, variables));
        Context context = new Context(Locale.getDefault());
        context.setVariables(variables);
        StringWriter out = new StringWriter();
        // StringTemplateResolver treats the process() name as the template source content
        engine.process(layoutTemplate, context, out);
        return out.toString();
    }

    private String process(String templateSource, Map<String, Object> variables) {
        Context context = new Context(Locale.getDefault());
        context.setVariables(variables);
        StringWriter out = new StringWriter();
        engine.process(templateSource, context, out);
        return out.toString();
    }

    /** Whole document when it composes the full page ({@code <html>}/doctype); otherwise a head fragment. */
    private static boolean isWholeDocument(String override) {
        String lowered = override.toLowerCase(Locale.ROOT);
        return lowered.contains("<!doctype html") || lowered.contains("<html");
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
        return readResource(override);
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
