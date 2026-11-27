package io.github.leewyatt.rxcontrols;

import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import java.util.Objects;

/**
 * The immutable data of one icon: a name, a view box and an SVG path.
 *
 * <p>The path is drawn in the coordinate space of the view box, as in an SVG
 * file's {@code viewBox}. {@link RXIcon} sizes its box from the view box and
 * fills the path with its background color, so the space the view box leaves
 * around the path is kept.</p>
 *
 * <pre>{@code
 * RXIconData star = new RXIconData("app-star", 24, 24,
 *         "M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z");
 * RXIcon icon = new RXIcon(star);
 * }</pre>
 */
public final class RXIconData {

    private final String name;
    private final double minX;
    private final double minY;
    private final double width;
    private final double height;
    private final String path;
    private final FillRule fillRule;

    private volatile SVGPath shape;

    // ==================== Constructors ====================

    /**
     * Creates icon data whose view box starts at the origin and whose path is
     * filled with the non-zero rule.
     *
     * @param name   the icon name
     * @param width  the view box width, greater than zero
     * @param height the view box height, greater than zero
     * @param path   the SVG path data
     * @throws NullPointerException     if {@code name} or {@code path} is {@code null}
     * @throws IllegalArgumentException if the width or height is not a positive finite number
     */
    public RXIconData(String name, double width, double height, String path) {
        this(name, 0.0, 0.0, width, height, path, FillRule.NON_ZERO);
    }

    /**
     * Creates icon data.
     *
     * @param name     the icon name
     * @param minX     the left edge of the view box
     * @param minY     the top edge of the view box
     * @param width    the view box width, greater than zero
     * @param height   the view box height, greater than zero
     * @param path     the SVG path data
     * @param fillRule the rule that decides which areas of the path are filled
     * @throws NullPointerException     if {@code name}, {@code path} or {@code fillRule} is {@code null}
     * @throws IllegalArgumentException if the view box is not finite or its width or height
     *                                  is not greater than zero
     */
    public RXIconData(String name, double minX, double minY, double width, double height,
                      String path, FillRule fillRule) {
        this.name = Objects.requireNonNull(name, "name");
        this.path = Objects.requireNonNull(path, "path");
        this.fillRule = Objects.requireNonNull(fillRule, "fillRule");
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) {
            throw new IllegalArgumentException("The view box origin must be finite: " + minX + ", " + minY);
        }
        if (!(width > 0.0) || !(height > 0.0) || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("The view box size must be positive: " + width + " x " + height);
        }
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the registered icon with the given name. FXML uses this to turn an
     * attribute such as {@code icon="mi-search"} into icon data.
     *
     * @param name the icon name
     * @return the icon, or {@code null} if no registered pack or icon has this
     *         name (a warning is logged once per name)
     */
    public static RXIconData valueOf(String name) {
        return RXIcons.resolve(name);
    }

    // ==================== Accessors ====================

    /**
     * Returns the icon name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the left edge of the view box.
     *
     * @return the view box x origin
     */
    public double getMinX() {
        return minX;
    }

    /**
     * Returns the top edge of the view box.
     *
     * @return the view box y origin
     */
    public double getMinY() {
        return minY;
    }

    /**
     * Returns the view box width.
     *
     * @return the width, greater than zero
     */
    public double getWidth() {
        return width;
    }

    /**
     * Returns the view box height.
     *
     * @return the height, greater than zero
     */
    public double getHeight() {
        return height;
    }

    /**
     * Returns the SVG path data.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the rule that decides which areas of the path are filled.
     *
     * @return the fill rule
     */
    public FillRule getFillRule() {
        return fillRule;
    }

    /**
     * Returns the shape that draws this icon, created on first use and shared by
     * every icon that shows this data. Its bounds are the whole view box: the path
     * starts with two empty sub-paths at opposite corners of the view box, which
     * draw nothing but keep the space around the icon when a region scales the
     * shape to its size.
     *
     * <p>The shape is shared: do not change it or add it to a scene.</p>
     *
     * @return the shared shape
     */
    public SVGPath getShape() {
        SVGPath result = shape;
        if (result == null) {
            synchronized (this) {
                result = shape;
                if (result == null) {
                    result = new SVGPath();
                    result.setContent(anchoredPath());
                    result.setFillRule(fillRule);
                    shape = result;
                }
            }
        }
        return result;
    }

    private String anchoredPath() {
        return "M" + format(minX) + " " + format(minY) + "h0"
                + "M" + format(minX + width) + " " + format(minY + height) + "h0"
                + path;
    }

    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    // ==================== Object ====================

    /**
     * Two icon data objects are equal when their name, view box, path and fill
     * rule are equal.
     *
     * @param other the object to compare with
     * @return whether the two describe the same icon
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RXIconData data)) {
            return false;
        }
        return Double.compare(minX, data.minX) == 0
                && Double.compare(minY, data.minY) == 0
                && Double.compare(width, data.width) == 0
                && Double.compare(height, data.height) == 0
                && name.equals(data.name)
                && path.equals(data.path)
                && fillRule == data.fillRule;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, minX, minY, width, height, path, fillRule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "RXIconData[" + name + ", viewBox=" + format(minX) + " " + format(minY) + " "
                + format(width) + " " + format(height) + ", " + fillRule + "]";
    }
}
