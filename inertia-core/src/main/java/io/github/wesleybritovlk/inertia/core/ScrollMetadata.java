package io.github.wesleybritovlk.inertia.core;

/**
 * Pagination cursor facts for a scroll prop (inertia-laravel {@code ProvidesScrollMetadata}
 * parity). Page identifiers may be integers ({@code ?page=3} style) or opaque strings
 * (cursor pagination); {@code null} marks the absence of a previous/next page. Built-in
 * implementations derive the values in {@link ScrollOptions#page(int, boolean)} style from
 * the request; implement this interface for full control (cursors, custom wrappers).
 */
public interface ScrollMetadata {

    /** Request parameter carrying the cursor, e.g. {@code page}. */
    String pageName();

    /** Cursor of the page resolved by this response. */
    Object currentPage();

    /** Cursor of the page AFTER this one; {@code null} when there is none. */
    Object nextPage();

    /** Cursor of the page BEFORE this one; {@code null} when there is none. */
    Object previousPage();
}
