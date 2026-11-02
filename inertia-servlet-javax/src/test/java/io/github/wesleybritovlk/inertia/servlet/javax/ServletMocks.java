package io.github.wesleybritovlk.inertia.servlet.javax;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.InertiaHeaders;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Proxy-based servlet mocks (test support only — no framework mock dependencies): covers the
 * surface the javax adapter reads and writes; every other call falls back to a return-type
 * default (zero/false/null), so interface evolution cannot break the mocks.
 */
final class ServletMocks {

    /** Mutable state the tests observe, shared by the request/response proxies. */
    final Map<String, String> requestHeaders = new LinkedHashMap<>();
    final Map<String, Object> sessionAttributes = new LinkedHashMap<>();
    final Map<String, String> responseHeaders = new LinkedHashMap<>();
    final StringBuffer recordedBody = new StringBuffer();
    boolean sessionCreated;
    int status;
    boolean committed;
    String characterEncoding = "UTF-8";

    HttpServletRequest request;
    HttpServletResponse response;
    HttpSession session;

    ServletMocks(String method, String path, String query, DispatcherType type) {
        request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> {
                    Object handled = readRequest(method, path, query, type, m, args);
                    return handled != UNHANDLED ? handled : methodDefault(m);
                });
        response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, m, args) -> {
                    Object handled = writeResponse(m, args);
                    return handled != UNHANDLED ? handled : methodDefault(m);
                });
        session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] { HttpSession.class }, (proxy, m, args) -> {
                    Object handled = handleSession(m, args);
                    return handled != UNHANDLED ? handled : methodDefault(m);
                });
    }

    /** mutable sentinel: "not a method this mock knows" (never a returned value type) */
    private static final Object UNHANDLED = new Object();

    Object readRequest(String method, String path, String query, DispatcherType type, Method m, Object[] args) {
        return switch (m.getName()) {
            case "getMethod" -> method;
            case "getHeader" -> requestHeaders.get((String) args[0]);
            case "getRequestURI" -> path;
            case "getQueryString" -> query;
            case "getDispatcherType" -> type;
            case "getRequestURL" -> new StringBuffer("http://mock" + path
                    + (query == null || query.isEmpty() ? "" : "?" + query));
            case "getServletPath" -> path;
            case "getSession" -> {
                boolean create = args == null || (args.length > 0 && Boolean.TRUE.equals(args[0]));
                if (sessionCreated || create) {
                    sessionCreated = true;
                    yield session;
                }
                yield null;
            }
            case "isRequestedSessionIdValid" -> sessionCreated;
            case "getLocale" -> Locale.US;
            case "getScheme" -> "http";
            case "getProtocol" -> "HTTP/1.1";
            case "getServerName" -> "localhost";
            case "getRemoteAddr", "getLocalAddr", "getRemoteHost", "getLocalName" -> "127.0.0.1";
            case "getCharacterEncoding" -> characterEncoding;
            default -> UNHANDLED;
        };
    }

    Object writeResponse(Method m, Object[] args) {
        return switch (m.getName()) {
            case "setHeader" -> {
                responseHeaders.put((String) args[0], (String) args[1]);
                yield null;
            }
            case "getHeader" -> responseHeaders.get((String) args[0]);
            case "setStatus" -> {
                status = (Integer) args[0];
                yield null;
            }
            case "getStatus" -> status;
            case "getWriter", "getOutputStream" -> {
                committed = true;
                yield new javax.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        recordedBody.append((char) (b & 0xFF));
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        recordedBody.append(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8));
                    }

                    @Override
                    public void close() {
                        committed = true;
                    }

                    @Override
                    public void flush() {
                        committed = true;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(javax.servlet.WriteListener listener) {
                        // synchronous mock: listeners are never consulted
                    }
                };
            }
            case "flushBuffer", "flush" -> {
                committed = true;
                yield null;
            }
            case "isCommitted" -> committed;
            case "setCharacterEncoding" -> {
                characterEncoding = (String) args[0];
                yield null;
            }
            case "getCharacterEncoding" -> characterEncoding;
            default -> UNHANDLED;
        };
    }

    Object handleSession(Method m, Object[] args) {
        return switch (m.getName()) {
            case "setAttribute" -> {
                if (args[1] != null) {
                    sessionAttributes.put((String) args[0], args[1]);
                } else {
                    sessionAttributes.remove(args[0]);
                }
                yield null;
            }
            case "removeAttribute" -> {
                sessionAttributes.remove(args[0]);
                yield null;
            }
            case "getAttribute" -> sessionAttributes.get(args[0]);
            case "invalidate" -> {
                sessionAttributes.clear();
                sessionCreated = false;
                yield null;
            }
            case "isNew" -> Boolean.TRUE;
            case "getId" -> "mock-session";
            case "getValue" -> sessionAttributes.get(args[0]);
            case "putValue" -> {
                if (args[1] != null) {
                    sessionAttributes.put((String) args[0], args[1]);
                }
                yield null;
            }
            case "removeValue" -> {
                sessionAttributes.remove(args[0]);
                yield null;
            }
            case "getCreationTime", "getLastAccessedTime" -> 0L;
            case "getMaxInactiveInterval" -> 0;
            default -> UNHANDLED;
        };
    }

    private static Object methodDefault(Method m) {
        Class<?> type = m.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == byte.class || type == short.class) {
            return (byte) 0;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return (float) 0f;
        }
        if (type == char.class) {
            return '\0';
        }
        return null; // reference types: null is what the adapter branches on
    }

    // ---------------------------------------------------------------- factories

    ServletMocks inertia() {
        requestHeaders.put(InertiaHeaders.INERTIA, "true");
        return this;
    }

    static ServletMocks get(String path, String query) {
        return new ServletMocks("GET", path, query == null ? "" : query, DispatcherType.REQUEST);
    }

    static ServletMocks get(String path) {
        return get(path, "");
    }

    static ServletMocks forward(String path) {
        return new ServletMocks("GET", path, "", DispatcherType.FORWARD);
    }

    static ServletMocks request(String method, String path, String query) {
        return new ServletMocks(method, path, query, DispatcherType.REQUEST);
    }
}
