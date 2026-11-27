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
import javafx.scene.paint.Paint;

import java.util.function.IntConsumer;

/**
 * A horizontal color slider of a color view (hue or opacity): a rounded track
 * the full width of the node and a round thumb whose center reaches both ends.
 * The track is painted by CSS; an optional overlay painted from code sits on
 * it (the opacity slider's clear-to-color gradient over a checkerboard).
 *
 * <p>The node only renders and maps coordinates; the color view's skin handles
 * input. It is a Tab stop with the {@link AccessibleRole#SLIDER} role.
 */
public final class ColorSlider extends Region {

    private static final double DEFAULT_TRACK_HEIGHT = 8.0;
    private static final double DEFAULT_WIDTH = 120.0;

    private final Region track = new Region();
    private final Region overlay = new Region();
    private final Region thumb = new Region();

    private double fraction;
    private Paint[] overlayPaints;
    private CornerRadii overlayRadii;
    private double accessibleValue;
    private double accessibleMax;
    private String accessibleText = "";
    private IntConsumer accessibleStep;

    /**
     * Creates a slider.
     *
     * @param styleClass the slider's style class, such as {@code "hue-slider"}
     */
    public ColorSlider(String styleClass) {
        getStyleClass().add(styleClass);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.SLIDER);
        // Color scales run left to right in every orientation.
        setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        track.getStyleClass().add("track");
        track.setMouseTransparent(true);
        overlay.getStyleClass().add("overlay");
        overlay.setMouseTransparent(true);
        thumb.getStyleClass().add("thumb");
        thumb.setManaged(false);
        thumb.setMouseTransparent(true);
        getChildren().addAll(track, overlay, thumb);
    }

    // ==================== State ====================

    /**
     * Moves the thumb.
     *
     * @param value the position in {@code [0, 1]}
     */
    public void setFraction(double value) {
        fraction = ColorMath.clean01(value);
        placeThumb();
    }

    /**
     * Fills the thumb.
     *
     * @param fill the thumb color
     */
    public void setThumbFill(Paint fill) {
        thumb.setBackground(new Background(new BackgroundFill(fill, new CornerRadii(0.5, true), Insets.EMPTY)));
    }

    /**
     * Paints the overlay above the CSS track, with the track's shape; the fills
     * are stacked in order.
     *
     * @param fills the overlay paints; none clears the overlay
     */
    public void setOverlayFills(Paint... fills) {
        overlayPaints = fills.length == 0 ? null : fills.clone();
        paintOverlay();
    }

    // The overlay copies the CSS track's corner radii, which are known only once
    // CSS has run; layout repaints it when they change.
    private void paintOverlay() {
        overlayRadii = trackRadii();
        if (overlayPaints == null) {
            overlay.setBackground(null);
            return;
        }
        BackgroundFill[] layers = new BackgroundFill[overlayPaints.length];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new BackgroundFill(overlayPaints[i], overlayRadii, Insets.EMPTY);
        }
        overlay.setBackground(new Background(layers));
    }

    /**
     * Sets what assistive technologies read.
     *
     * @param value the value
     * @param max   the maximum (the minimum is {@code 0})
     * @param text  the spoken text, such as {@code "Hue 210°"}
     */
    public void setAccessibleState(double value, double max, String text) {
        accessibleValue = value;
        accessibleMax = max;
        accessibleText = text;
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
     * Returns the position at an x coordinate in this node, clamped.
     *
     * @param x the x coordinate
     * @return the position in {@code [0, 1]}
     */
    public double fractionAt(double x) {
        double width = trackWidth();
        return width > 0.0 ? ColorMath.clean01((x - snappedLeftInset()) / width) : fraction;
    }

    /**
     * Returns how far the thumb can reach past this node's layout bounds
     * sideways: half its painted width, effects included.
     *
     * @return the overhang in pixels
     */
    public double overhang() {
        Bounds bounds = thumb.getBoundsInLocal();
        return bounds.getWidth() / 2.0;
    }

    private double trackWidth() {
        return getWidth() - snappedLeftInset() - snappedRightInset();
    }

    private CornerRadii trackRadii() {
        Background background = track.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return CornerRadii.EMPTY;
        }
        return background.getFills().get(0).getRadii();
    }

    private void placeThumb() {
        double w = thumb.prefWidth(-1);
        double h = thumb.prefHeight(-1);
        thumb.resize(w, h);
        double x = snappedLeftInset() + fraction * Math.max(0.0, trackWidth());
        double y = snappedTopInset() + Math.max(0.0, getHeight() - snappedTopInset() - snappedBottomInset()) / 2.0;
        thumb.relocate(x - w / 2.0, y - h / 2.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double w = Math.max(0.0, trackWidth());
        double contentHeight = Math.max(0.0, getHeight() - snappedTopInset() - snappedBottomInset());
        double trackHeight = Math.min(contentHeight, trackHeight());
        double y = snappedTopInset() + (contentHeight - trackHeight) / 2.0;
        track.resizeRelocate(x, y, w, trackHeight);
        overlay.resizeRelocate(x, y, w, trackHeight);
        if (overlayPaints != null && !trackRadii().equals(overlayRadii)) {
            paintOverlay();
        }
        placeThumb();
    }

    private double trackHeight() {
        double pref = track.prefHeight(-1);
        return pref > 0.0 ? pref : DEFAULT_TRACK_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + DEFAULT_WIDTH + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double content = Math.max(trackHeight(), thumb.prefHeight(-1));
        return snappedTopInset() + content + snappedBottomInset();
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    // ==================== Accessibility ====================

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case VALUE:
                return accessibleValue;
            case MIN_VALUE:
                return 0.0;
            case MAX_VALUE:
                return accessibleMax;
            case TEXT:
                return accessibleText;
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
