package io.github.leewyatt.rxcontrols.internal.color;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.IntConsumer;

/**
 * The saturation / brightness area of a color view: saturation grows to the
 * right, brightness upward. Three stacked layers paint it exactly: the pure
 * hue, a white-to-clear gradient across and a clear-to-black gradient down
 * (both gradients come from CSS). A round handle marks the current point; its
 * center reaches every edge, so it overhangs the area by half its size.
 *
 * <p>The node only renders and maps coordinates; the color view's skin handles
 * input. It is a Tab stop with the {@link AccessibleRole#SLIDER} role, whose
 * value is the brightness percentage.
 */
public final class ColorArea extends Region {

    private static final double DEFAULT_WIDTH = 234.0;
    private static final double DEFAULT_HEIGHT = 160.0;
    private static final int PERCENT = 100;

    private final StackPane layers = new StackPane();
    private final Region hueLayer = new Region();
    private final Rectangle layersClip = new Rectangle();
    private final Region handle = new Region();

    private double saturation;
    private double brightness = 1.0;
    private double hue = -1.0;
    private IntConsumer accessibleStep;

    /**
     * Creates the area.
     */
    public ColorArea() {
        getStyleClass().add("area");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.SLIDER);
        // Saturation grows rightward in every orientation: it is a color scale,
        // not reading order.
        setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        hueLayer.getStyleClass().add("hue-layer");
        Region saturationLayer = new Region();
        saturationLayer.getStyleClass().add("saturation-layer");
        Region brightnessLayer = new Region();
        brightnessLayer.getStyleClass().add("brightness-layer");
        layers.getStyleClass().add("layers");
        layers.getChildren().addAll(hueLayer, saturationLayer, brightnessLayer);
        // One rounded clip on the layers, so the hue fill set from code follows the
        // CSS corner radius; the handle sits outside it and may overhang the edges.
        layers.setClip(layersClip);
        layers.setMouseTransparent(true);

        handle.getStyleClass().add("handle");
        handle.setManaged(false);
        handle.setMouseTransparent(true);

        getChildren().addAll(layers, handle);
        setHue(0.0);
    }

    // ==================== State ====================

    /**
     * Sets the hue painted under the gradients.
     *
     * @param value the hue in degrees
     */
    public void setHue(double value) {
        double clean = ColorMath.cleanHue(value);
        if (clean == hue) {
            return;
        }
        hue = clean;
        hueLayer.setBackground(new Background(new BackgroundFill(
                ColorMath.hsb(clean, 1.0, 1.0, 1.0), CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /**
     * Places the handle and fills it.
     *
     * @param saturationValue the saturation in {@code [0, 1]}
     * @param brightnessValue the brightness in {@code [0, 1]}
     * @param fill            the handle color
     */
    public void setPoint(double saturationValue, double brightnessValue, Color fill) {
        saturation = ColorMath.clean01(saturationValue);
        brightness = ColorMath.clean01(brightnessValue);
        handle.setBackground(new Background(new BackgroundFill(fill, new CornerRadii(0.5, true), Insets.EMPTY)));
        placeHandle();
        notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
        notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    /**
     * Sets the callback for the accessibility increment / decrement actions.
     *
     * @param step receives {@code +1} or {@code -1}; {@code null} ignores the actions
     */
    public void setAccessibleStep(IntConsumer step) {
        accessibleStep = step;
    }

    // ==================== Geometry ====================

    /**
     * Returns the saturation at an x coordinate in this node, clamped.
     *
     * @param x the x coordinate
     * @return the saturation in {@code [0, 1]}
     */
    public double saturationAt(double x) {
        double width = areaWidth();
        return width > 0.0 ? ColorMath.clean01((x - snappedLeftInset()) / width) : saturation;
    }

    /**
     * Returns the brightness at a y coordinate in this node, clamped.
     *
     * @param y the y coordinate
     * @return the brightness in {@code [0, 1]}
     */
    public double brightnessAt(double y) {
        double height = areaHeight();
        return height > 0.0 ? ColorMath.clean01(1.0 - (y - snappedTopInset()) / height) : brightness;
    }

    /**
     * Returns how far the handle can reach past this node's layout bounds: half
     * its painted size, effects included.
     *
     * @return the overhang in pixels
     */
    public double overhang() {
        Bounds bounds = handle.getBoundsInLocal();
        return Math.max(bounds.getWidth(), bounds.getHeight()) / 2.0;
    }

    private double areaWidth() {
        return getWidth() - snappedLeftInset() - snappedRightInset();
    }

    private double areaHeight() {
        return getHeight() - snappedTopInset() - snappedBottomInset();
    }

    private void placeHandle() {
        double w = handle.prefWidth(-1);
        double h = handle.prefHeight(-1);
        handle.resize(w, h);
        double x = snappedLeftInset() + saturation * Math.max(0.0, areaWidth());
        double y = snappedTopInset() + (1.0 - brightness) * Math.max(0.0, areaHeight());
        handle.relocate(x - w / 2.0, y - h / 2.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double w = Math.max(0.0, areaWidth());
        double h = Math.max(0.0, areaHeight());
        layers.resizeRelocate(x, y, w, h);
        layersClip.setWidth(w);
        layersClip.setHeight(h);
        double radius = cornerRadius(layers);
        layersClip.setArcWidth(radius * 2.0);
        layersClip.setArcHeight(radius * 2.0);
        placeHandle();
    }

    private static double cornerRadius(Region region) {
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return 0.0;
        }
        return background.getFills().get(0).getRadii().getTopLeftHorizontalRadius();
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + DEFAULT_WIDTH + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + DEFAULT_HEIGHT + snappedBottomInset();
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + snappedBottomInset();
    }

    // ==================== Accessibility ====================

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case VALUE:
                return (double) ColorMath.toPercent(brightness);
            case MIN_VALUE:
                return 0.0;
            case MAX_VALUE:
                return (double) PERCENT;
            case TEXT:
                return "Saturation " + ColorMath.toPercent(saturation) + "%, Brightness "
                        + ColorMath.toPercent(brightness) + "%";
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (accessibleStep != null && action == AccessibleAction.INCREMENT) {
            accessibleStep.accept(1);
        } else if (accessibleStep != null && action == AccessibleAction.DECREMENT) {
            accessibleStep.accept(-1);
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }
}
