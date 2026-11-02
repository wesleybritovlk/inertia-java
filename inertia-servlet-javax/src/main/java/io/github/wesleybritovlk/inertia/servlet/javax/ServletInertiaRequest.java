package io.github.wesleybritovlk.inertia.servlet.javax;

import io.github.wesleybritovlk.inertia.core.InertiaFlashStore;
import io.github.wesleybritovlk.inertia.core.InertiaRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * {@link InertiaRequest} port over {@code javax.servlet.http.HttpServletRequest} (the legacy
 * javax twin of the Jakarta adapter). The path keeps the context path (matches Laravel's
 * URL-after-origin, which includes subdirectory deployments) and the flash store is the
 * servlet session (inertia-laravel parity).
 */
public final class ServletInertiaRequest implements InertiaRequest {

    private final HttpServletRequest request;

    private ServletInertiaRequest(HttpServletRequest request) {
        this.request = request;
    }

    public static ServletInertiaRequest of(HttpServletRequest request) {
        return new ServletInertiaRequest(request);
    }

    @Override
    public String method() {
        return request.getMethod();
    }

    @Override
    public String header(String name) {
        return request.getHeader(name);
    }

    @Override
    public String path() {
        return request.getRequestURI();
    }

    @Override
    public String query() {
        String query = request.getQueryString();
        return query == null ? "" : query;
    }

    @Override
    public InertiaFlashStore flash() {
        return new SessionFlashStore(request);
    }

    private static final class SessionFlashStore implements InertiaFlashStore {

        private final HttpServletRequest request;

        private SessionFlashStore(HttpServletRequest request) {
            this.request = request;
        }

        @Override
        public Object pull(String key) {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return null;
            }
            Object value = session.getAttribute(key);
            if (value != null) {
                session.removeAttribute(key);
            }
            return value;
        }

        @Override
        public void put(String key, Object value) {
            if (value != null) {
                request.getSession().setAttribute(key, value);
            }
        }

        @Override
        public Object peek(String key) {
            HttpSession session = request.getSession(false);
            return session == null ? null : session.getAttribute(key);
        }
    }
}
