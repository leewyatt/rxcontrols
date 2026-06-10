package io.github.leewyatt.rxcontrols.lrc;

/**
 * Categorizes non-fatal issues recorded during lenient LRC parsing.
 */
public enum RXLrcWarningCode {

    /**
     * A timestamp tag was malformed or had seconds outside the supported range.
     */
    INVALID_TIMESTAMP,

    /**
     * A metadata tag was malformed and could not be parsed.
     */
    INVALID_METADATA,

    /**
     * The LRC offset metadata value was not a signed integer millisecond value.
     */
    INVALID_OFFSET,

    /**
     * A non-blank text line had no usable timestamp tag.
     */
    UNTIMED_TEXT,

    /**
     * Two or more parsed lyric lines share the same normalized timestamp.
     */
    DUPLICATE_TIMESTAMP,

    /**
     * The input contained content but produced no timed lyric lines.
     */
    NO_TIMED_LINES
}
