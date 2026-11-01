package io.github.wesleybritovlk.inertia.quarkus;

import java.lang.reflect.Proxy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Proxy-based servlet mocks for the quarkus adapter tests (zero framework dependencies). */
final class ServletTestDoubles {

    final java.util.Map<String, String> requestHeaders = new java.util.LinkedHashMap<>();
    final java.util.Map<String, String> responseHeaders = new java.util.LinkedHashMap<>();
    final StringBuilder recordedBody = new StringBuilder();
    int status;

    HttpServletRequest request;
    HttpServletResponse response;

    ServletTestDoubles(String method, String uri) {
        request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getDispatcherType" -> DispatcherType.REQUEST;
                    case "getRequestURL" -> new StringBuffer("http://localhost" + uri);
                    default -> null;
                });
        response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "setHeader" -> {
                        responseHeaders.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    case "setContentType" -> {
                        responseHeaders.put("Content-Type", (String) args[0]);
                        yield null;
                    }
                    case "getHeader" -> responseHeaders.get((String) args[0]);
                    case "setStatus" -> {
                        status = (Integer) args[0];
                        yield null;
                    }
                    case "getStatus" -> status;
                    case "getWriter", "getOutputStream" -> {
                        yield new jakarta.servlet.ServletOutputStream() {
                            @Override
                            public void write(int b) {
                                recordedBody.append((char) (b & 0xFF));
                            }

                            @Override
                            public void write(byte[] b, int off, int len) {
                                recordedBody.append(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8));
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setWriteListener(jakarta.servlet.WriteListener listener) {
                                // synchronous mock
                            }
                        };
                    }
                    case "flushBuffer", "flush" -> null;
                    case "isCommitted" -> false;
                    default -> null;
                });
    }

    static ServletTestDoubles get(String uri) {
        return new ServletTestDoubles("GET", uri);
    }
}
