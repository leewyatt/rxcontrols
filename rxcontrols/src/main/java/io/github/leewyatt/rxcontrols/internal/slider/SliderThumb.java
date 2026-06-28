package io.github.leewyatt.rxcontrols.internal.slider;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Bundles one slider thumb with its Material feedback: a focus-traversable
 * {@code thumb} {@link StackPane} (accessible {@code THUMB} role answering its
 * own value), an unbounded {@link StateLayer} halo painted below it, and an
 * optional bounded press {@link RippleLayer ink} on its face. The owning skin
 * places the thumb, halo, and ink each layout pass and drives the feedback.
 *
 * <p>Used by the range-slider skin for each of its two thumbs.</p>
 */
public final class SliderThumb {

    private final StackPane thumb;
    private final StateLayer halo = new StateLayer();
    private final RippleLayer ink = new RippleLayer();
    private final RippleBehavior inkBehavior;

    /**
     * Creates a thumb bundle.
     *
     * @param value   supplies the thumb's value for the accessibility query
     * @param fill    supplies the ink fill
     * @param opacity supplies the ink peak opacity
     * @param side    the side pseudo-class ({@code lower} / {@code upper})
     */
    public SliderThumb(DoubleSupplier value, Supplier<Paint> fill, DoubleSupplier opacity, PseudoClass side) {
        thumb = new StackPane() {
            @Override
            public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
                if (attribute == AccessibleAttribute.VALUE) {
                    return value.getAsDouble();
                }
                return super.queryAccessibleAttribute(attribute, parameters);
            }
        };
        thumb.getStyleClass().add("thumb");
        thumb.setAccessibleRole(AccessibleRole.THUMB);
        thumb.setFocusTraversable(true);
        thumb.pseudoClassStateChanged(side, true);

        halo.setClipMode(StateLayer.ClipMode.CIRCLE, null);
        thumb.getChildren().add(ink);
        inkBehavior = new RippleBehavior(ink, fill, opacity);
    }

    /**
     * Returns the thumb node.
     *
     * @return the thumb node
     */
    public StackPane getThumb() {
        return thumb;
    }

    /**
     * Returns the unbounded halo overlay (placed below the thumb by the skin).
     *
     * @return the halo
     */
    public StateLayer getHalo() {
        return halo;
    }

    /**
     * Returns the bounded press ink layer (a child of the thumb).
     *
     * @return the ink layer
     */
    public RippleLayer getInk() {
        return ink;
    }

    /**
     * Sets the halo fill.
     *
     * @param fill the halo fill, or {@code null}
     */
    public void setFill(Paint fill) {
        halo.setFill(fill);
    }

    /**
     * Drives the halo toward the highest-priority active tier.
     *
     * @param hover   whether the pointer is over the thumb
     * @param focus   whether the thumb is focused
     * @param pressed whether the thumb is pressed
     * @param dragged whether the thumb's value is changing
     */
    public void setState(boolean hover, boolean focus, boolean pressed, boolean dragged) {
        halo.setState(hover, focus, pressed, dragged);
    }

    /**
     * Starts a centered press ink at the thumb center.
     *
     * @param centerX the thumb-local center x
     * @param centerY the thumb-local center y
     */
    public void pressInk(double centerX, double centerY) {
        inkBehavior.press(centerX, centerY, true);
    }

    /**
     * Releases the press ink.
     */
    public void releaseInk() {
        inkBehavior.release();
    }

    /**
     * Clears all live ink.
     */
    public void clearInk() {
        inkBehavior.clear();
    }

    /**
     * Releases the halo, clears the ink, and drops the ink clip.
     */
    public void dispose() {
        halo.reset();
        inkBehavior.clear();
        ink.clearClip();
    }
}
