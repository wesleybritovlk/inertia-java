package io.github.wesleybritovlk.inertia.core;

/**
 * Keys used inside the {@link InertiaFlashStore} session store.
 */
public final class FlashKeys {

    /** Arbitrary one-shot payload shared with the next rendered page ({@code "flash"} page key). */
    public static final String FLASH = "inertia.flash";

    /** Validation error bags: {@code Map<String, Map<String, Object>>} of bagName &rarr; field &rarr; messages. */
    public static final String ERRORS = "inertia.errors";

    /** Boolean flag consumed by the next rendered page to emit {@code "clearHistory": true}. */
    public static final String CLEAR_HISTORY = "inertia.clear_history";

    /** Boolean flag consumed by the next rendered page to emit {@code "preserveFragment": true}. */
    public static final String PRESERVE_FRAGMENT = "inertia.preserve_fragment";

    private FlashKeys() {
    }
}
