package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Whether a {@link RXLinkNode} is a plain URL link or an email link.
 */
public enum RXLinkKind {

    /** A hyperlink whose {@link RXLinkNode#href()} is an allow-listed URL. */
    URL,

    /** An email link whose {@link RXLinkNode#href()} is a {@code mailto:} URI. */
    EMAIL
}
