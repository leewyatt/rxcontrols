package io.github.leewyatt.rxcontrols;

import javafx.scene.paint.Color;

import java.util.List;

/**
 * A group of preset colors shown by {@link RXColorView} and
 * {@link RXColorPicker}, such as brand colors. A group with a label is shown
 * under a header that the user can collapse; a group without one is shown as a
 * plain row of swatches.
 *
 * <p>A group is an immutable description, so one instance can be shared by
 * several views. Whether a view shows it expanded is that view's own state:
 * {@code defaultExpanded} only sets how it starts, and a view keeps the user's
 * choice for as long as the same group instance stays in its list.
 *
 * <pre>{@code
 * RXColorPresetGroup brand = new RXColorPresetGroup("Brand",
 *         Color.web("#1769AA"), Color.web("#F57C00"));
 * view.getPresets().add(brand);
 * }</pre>
 *
 * @param label           the header text, or {@code null} (or blank) for no header
 * @param colors          the colors in display order; copied, and must not contain {@code null}
 * @param defaultExpanded whether the group starts expanded
 */
public record RXColorPresetGroup(String label, List<Color> colors, boolean defaultExpanded) {

    /**
     * Creates a group, copying the colors.
     *
     * @param label           the header text, or {@code null} (or blank) for no header
     * @param colors          the colors in display order
     * @param defaultExpanded whether the group starts expanded
     * @throws NullPointerException if {@code colors} or any color is {@code null}
     */
    public RXColorPresetGroup {
        colors = List.copyOf(colors);
    }

    /**
     * Creates a group that starts expanded.
     *
     * @param label  the header text, or {@code null} (or blank) for no header
     * @param colors the colors in display order
     * @throws NullPointerException if any color is {@code null}
     */
    public RXColorPresetGroup(String label, Color... colors) {
        this(label, List.of(colors), true);
    }
}
