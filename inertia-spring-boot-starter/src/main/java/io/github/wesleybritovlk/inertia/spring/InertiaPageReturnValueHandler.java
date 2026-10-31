package io.github.wesleybritovlk.inertia.spring;

import io.github.wesleybritovlk.inertia.core.InertiaPage;

import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletResponse;

/**
 * MVC return value handler claiming {@link InertiaPage} / {@link InertiaResult} returns for
 * plain {@code @Controller} methods (no {@code @ResponseBody}). For {@code @RestController}
 * methods Spring's annotation-based processors take precedence, and the
 * {@code InertiaPageHttpMessageConverter} handles those returns instead.
 */
final class InertiaPageReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final InertiaPageWriter writer;

    InertiaPageReturnValueHandler(InertiaPageWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return writer.supports(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest) throws Exception {
        mavContainer.setRequestHandled(true);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
        writer.write(returnValue, response);
    }
}
