package io.github.wesleybritovlk.inertia.core;

/**
 * Neutral flash/session store used for one-shot {@link Inertia#flash(java.util.Map) flash},
 * validation {@code errors} and consumed page flags. The core never assumes a servlet
 * session: each adapter wires its own backing store (e.g. {@code HttpSession}).
 *
 * <p>Flash semantics follow the Laravel-based flow: values are written from user code or
 * validation, survive redirects, and are consumed ( {@linkplain #pull pulled}) when the next
 * rendered page adopts them &mdash; which means a redirect chain keeps the data alive.</p>
 */
public interface InertiaFlashStore {

    /** Read-and-remove one flash key. */
    Object pull(String key);

    /** Write one flash key, replacing any previous value. */
    void put(String key, Object value);

    /** Peek without consuming. */
    Object peek(String key);

    /** Adapters without session support return the {@link #NONE} store. */
    static InertiaFlashStore none() {
        return NONE;
    }

    InertiaFlashStore NONE = new InertiaFlashStore() {
        @Override
        public Object pull(String key) {
            return null;
        }

        @Override
        public void put(String key, Object value) {
        }

        @Override
        public Object peek(String key) {
            return null;
        }
    };
}
