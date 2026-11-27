package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.scene.layout.Region;

/**
 * A fixed-size icon box.
 *
 * <p>The icon is a {@link Region} whose shape, size and color come from CSS:
 * {@code -fx-shape} gives the outline, {@code -fx-pref-width} and
 * {@code -fx-pref-height} give the box, and {@code -fx-background-color} fills
 * the shape. The minimum and maximum sizes follow the preferred size, so a
 * parent short of space does not squeeze the icon and a parent with spare space
 * does not stretch it. An explicit min or max size, set in code or with
 * {@code -fx-min-*} / {@code -fx-max-*}, still overrides this.</p>
 *
 * <pre>{@code
 * RXIcon arrow = new RXIcon();
 * arrow.getStyleClass().add("arrow");
 * }</pre>
 * <pre>{@code
 * .my-control .arrow {
 *     -fx-pref-width: 8px;
 *     -fx-pref-height: 5px;
 *     -fx-shape: "M 0 0 L 8 0 L 4 5 Z";
 *     -fx-background-color: -fx-text-base-color;
 * }
 * }</pre>
 *
 * <p>The icon is not mouse transparent by default. An icon inside a clickable
 * wrapper should usually be made mouse transparent so the wrapper receives the
 * events.</p>
 */
public class RXIcon extends Region {

    private static final String DEFAULT_STYLE_CLASS = "rx-icon";

    // ==================== Constructors ====================

    /**
     * Creates an icon with no shape. Its shape and size come from CSS.
     */
    public RXIcon() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Sizing ====================

    /**
     * Returns the preferred width, so the icon is not squeezed.
     *
     * @param height the height to compute the width for, or -1
     * @return the minimum width
     */
    @Override
    protected double computeMinWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the preferred height, so the icon is not squeezed.
     *
     * @param width the width to compute the height for, or -1
     * @return the minimum height
     */
    @Override
    protected double computeMinHeight(double width) {
        return prefHeight(width);
    }

    /**
     * Returns the preferred width, so the icon is not stretched.
     *
     * @param height the height to compute the width for, or -1
     * @return the maximum width
     */
    @Override
    protected double computeMaxWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the preferred height, so the icon is not stretched.
     *
     * @param width the width to compute the height for, or -1
     * @return the maximum height
     */
    @Override
    protected double computeMaxHeight(double width) {
        return prefHeight(width);
    }
}
