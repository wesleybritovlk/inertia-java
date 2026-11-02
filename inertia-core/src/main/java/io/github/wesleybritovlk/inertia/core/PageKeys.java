package io.github.wesleybritovlk.inertia.core;

/**
 * JSON keys of the Inertia page object. Metadata keys are top-level (never inside
 * {@code props}), mirroring {@code inertia-laravel}.
 */
public final class PageKeys {

    public static final String COMPONENT = "component";
    public static final String PROPS = "props";
    public static final String URL = "url";
    public static final String VERSION = "version";

    /** Map of deferred group &rarr; prop paths excluded from this response. */
    public static final String DEFERRED_PROPS = "deferredProps";

    /** Prop paths the client appends (not replaces) on merge. */
    public static final String MERGE_PROPS = "mergeProps";

    /** Prop paths the client prepends (not replaces) on merge. */
    public static final String PREPEND_PROPS = "prependProps";

    /** Prop paths deep-merged by the client instead of appended. */
    public static final String DEEP_MERGE_PROPS = "deepMergeProps";

    /** Item identity key used when merging arrays ({@code testProp.onKey} paths). */
    public static final String MATCH_PROPS_ON = "matchPropsOn";

    /** Cursor metadata of scroll (infinite scroll) props, keyed by prop path. */
    public static final String SCROLL_PROPS = "scrollProps";

    /** Once-prop entries keyed by once key: {@code {prop, expiresAt}}. */
    public static final String ONCE_PROPS = "onceProps";

    /** Top-level shared prop keys, truncated at their first dot. */
    public static final String SHARED_PROPS = "sharedProps";

    /** Prop paths whose deferred callbacks failed and were rescued. */
    public static final String RESCUED_PROPS = "rescuedProps";

    /** One-shot flash payload adopted by this page. */
    public static final String FLASH = "flash";

    /** Emitted {@code true} only. Clears the client history buffer. */
    public static final String CLEAR_HISTORY = "clearHistory";

    /** Emitted {@code true} only. Preserves the URL fragment across client navigation. */
    public static final String PRESERVE_FRAGMENT = "preserveFragment";

    /** Emitted {@code true} only. Encrypts client-side page history entries. */
    public static final String ENCRYPT_HISTORY = "encryptHistory";

    /** Emitted {@code true} only; JSON numbers outside the JS safe integer range are wrapped as {@code {"$bigint": "..."}}. */
    public static final String PRESERVE_BIG_INTEGERS = "preserveBigIntegers";

    private PageKeys() {
    }
}
