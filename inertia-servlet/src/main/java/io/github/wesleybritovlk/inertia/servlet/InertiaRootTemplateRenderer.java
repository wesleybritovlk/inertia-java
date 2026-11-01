package io.github.wesleybritovlk.inertia.servlet;

import java.util.Map;

/**
 * Root page renderer: turns the serialized page JSON into the SPA root HTML document.
 */
public interface InertiaRootTemplateRenderer {

    /**
     * @param pageJson serialized Inertia page object
     * @param viewData template-scoped variables added via {@code InertiaPage.withViewData}
     */
    String render(String pageJson, Map<String, Object> viewData);
}
