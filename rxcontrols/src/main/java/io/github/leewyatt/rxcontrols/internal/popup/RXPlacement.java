package io.github.leewyatt.rxcontrols.internal.popup;

/**
 * Preferred placement of an anchored popup relative to its anchor node.
 *
 * <p>The twelve values pair a primary side (top / bottom / left / right of the
 * anchor) with a secondary-axis alignment ({@code START} / center / {@code END}).
 * For the vertical family ({@code TOP_*} / {@code BOTTOM_*}) the secondary axis is
 * horizontal; for the side family ({@code LEFT_*} / {@code RIGHT_*}) it is
 * vertical. For the vertical family, {@code START} / {@code END} are resolved
 * against the anchor's effective node orientation, so they mirror under
 * right-to-left; the side family's vertical alignment is physical and does not
 * mirror.
 *
 * <p>Dropdown / suggestion popups use only the vertical family; the side family
 * is defined ahead of a future arrow-popover consumer and is positioned but not
 * a first-class V1 path. Kept internal until an anchored-popup public API
 * stabilizes.
 */
public enum RXPlacement {

    TOP_START, TOP, TOP_END,
    BOTTOM_START, BOTTOM, BOTTOM_END,
    LEFT_START, LEFT, LEFT_END,
    RIGHT_START, RIGHT, RIGHT_END;

    /**
     * Returns whether this placement belongs to the vertical family (the popup
     * opens above or below the anchor). The side family opens to the left or
     * right instead.
     *
     * @return {@code true} for {@code TOP_*} / {@code BOTTOM_*}
     */
    public boolean isVertical() {
        return this == TOP_START || this == TOP || this == TOP_END
                || this == BOTTOM_START || this == BOTTOM || this == BOTTOM_END;
    }

    /**
     * Returns whether the popup prefers the "after" side of the anchor along its
     * primary axis: below for the vertical family, right for the side family.
     * When the preferred side lacks room the resolver flips to the opposite side.
     *
     * @return {@code true} for {@code BOTTOM_*} / {@code RIGHT_*}
     */
    public boolean prefersAfter() {
        return this == BOTTOM_START || this == BOTTOM || this == BOTTOM_END
                || this == RIGHT_START || this == RIGHT || this == RIGHT_END;
    }

    /**
     * Returns the secondary-axis alignment as a sign: {@code -1} for
     * {@code START}, {@code 0} for center, {@code +1} for {@code END}. Callers
     * flip the sign under right-to-left to honor {@code START} / {@code END}.
     *
     * @return {@code -1}, {@code 0}, or {@code +1}
     */
    public int alignSign() {
        switch (this) {
            case TOP_START:
            case BOTTOM_START:
            case LEFT_START:
            case RIGHT_START:
                return -1;
            case TOP_END:
            case BOTTOM_END:
            case LEFT_END:
            case RIGHT_END:
                return 1;
            default:
                return 0;
        }
    }
}
