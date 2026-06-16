package io.github.leewyatt.rxcontrols.enums;

/**
 * Semantic color level for a timeline item, mapped to a pseudo-class on the
 * rendered item so the actual color stays in CSS.
 *
 * <p>A {@code null} type means "no semantic level": no type pseudo-class is set
 * and the item falls back to the view's default dot color. There is
 * deliberately no {@code NONE} constant, which would duplicate the {@code null}
 * meaning.
 */
public enum TimelineItemType {
    /**
     * Primary level, the default accent color.
     */
    PRIMARY,
    /**
     * Success level, typically green.
     */
    SUCCESS,
    /**
     * Warning level, typically amber.
     */
    WARNING,
    /**
     * Danger level, typically red.
     */
    DANGER,
    /**
     * Informational level, typically a muted gray.
     */
    INFO
}
