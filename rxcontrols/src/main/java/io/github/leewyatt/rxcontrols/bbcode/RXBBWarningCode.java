package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Categorizes non-fatal issues recorded during lenient BBCode parsing.
 */
public enum RXBBWarningCode {

    /** A tag was left open at end of input and was auto-closed. */
    UNCLOSED_TAG,

    /** A close tag had no matching open tag and was dropped. */
    MISMATCHED_CLOSE,

    /** A tag name was not recognized and was unwrapped or echoed. */
    UNKNOWN_TAG,

    /** A colour value failed validation and was dropped. */
    INVALID_COLOR,

    /** A size value failed validation and was dropped. */
    INVALID_SIZE,

    /** A font family failed structural validation and was dropped. */
    INVALID_FONT,

    /** A link URL was malformed or missing a scheme and was dropped. */
    INVALID_URL,

    /** A link URL used a blocked scheme and was dropped. */
    BLOCKED_URL,

    /** An image URL was not an allow-listed image source and was dropped. */
    INVALID_IMAGE_URL,

    /** A stray table cell was wrapped in a synthesized table row. */
    IMPLICIT_TABLE_ROW,

    /** Stray list content was wrapped in a synthesized first list item. */
    IMPLICIT_LIST_ITEM,

    /** An open tag exceeded the maximum nesting depth and was not pushed. */
    MAX_DEPTH_EXCEEDED
}
