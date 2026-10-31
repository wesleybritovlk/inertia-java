package io.github.wesleybritovlk.inertia.spring;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletResponse;

/**
 * MVC return value handler claiming {@link InertiaPage} / {@link InertiaResult} returns
 * (the adapter equivalent of Laravel's {@code Responsable}). Registered before the default
 * handlers, so regular controllers can simply return an {@code InertiaPage}.
 */
final class InertiaPageReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final ObjectProvider<Inertia> inertia;
    private final InertiaRootTemplateRenderer renderer;

    InertiaPageReturnValueHandler(ObjectProvider<Inertia> inertia, InertiaRootTemplateRenderer renderer) {
        this.inertia = inertia;
        this.renderer = renderer;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> type = returnType.getParameterType();
        return InertiaPage.class.isAssignableFrom(type) || InertiaResult.class.isAssignableFrom(type);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest) throws Exception {
        mavContainer.setRequestHandled(true);

        InertiaResult result;
        LinkedHashMap<String, Object> viewData;
        if (returnValue instanceof InertiaPage page) {
            result = inertia.getObject().result(page);
            viewData = new LinkedHashMap<>(page.view());
        } else {
            result = (InertiaResult) returnValue;
            viewData = new LinkedHashMap<>();
        }

        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
        InertiaResultWriter.write(result, response);
        if (result.kind() == InertiaResult.Kind.PAGE_HTML) {
            String html = renderer.render(result.pageJson().orElseThrow(), viewData);
            response.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
        }
    }
}
