package io.github.leewyatt.rxcontrols.internal.icon;

import java.util.Objects;

/**
 * Rules for icon names: a pack prefix of lowercase letters and digits, a hyphen,
 * then the icon's own name.
 */
public final class IconNames {

    /** Separates the pack prefix from the rest of an icon name. */
    public static final char SEPARATOR = '-';

    private IconNames() {
    }

    /**
     * Checks a pack prefix.
     *
     * @param prefix the prefix
     * @return the prefix
     * @throws NullPointerException     if the prefix is {@code null}
     * @throws IllegalArgumentException if the prefix is empty or has characters other than
     *                                  lowercase letters and digits
     */
    public static String checkPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.isEmpty() || !prefix.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
            throw new IllegalArgumentException("An icon pack prefix has only lowercase letters and digits: " + prefix);
        }
        return prefix;
    }

    /**
     * Returns the pack prefix of a full icon name.
     *
     * @param name the full icon name
     * @return the part before the first hyphen, or {@code null} if there is no hyphen
     */
    public static String prefixOf(String name) {
        int index = name.indexOf(SEPARATOR);
        return index <= 0 ? null : name.substring(0, index);
    }
}
