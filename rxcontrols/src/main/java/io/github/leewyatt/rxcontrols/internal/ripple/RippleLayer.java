package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import javafx.beans.property.DoubleProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles plus a composed
 * {@link StateLayer} for the steady-state hover / pressed tint.
 *
 * <p>The bounded clip follows the host region's shape or background geometry
 * via {@link BoundedClipSupport}; see that class for the exact clip contract.
 * The same clip trims both the ripple circles and the embedded state overlay,
 * so the overlay runs in {@link StateLayer.ClipMode#NONE} (it does not install
 * a clip of its own). The state overlay is attached only while shown, so an
 * idle layer holds only ripple circles.</p>
 */
public final class RippleLayer extends Region {

    private static final String STYLE_CLASS = "ripple-layer";

    /**
     * Sentinel ripple radius selecting the automatic {@code hypot}
     * center-to-bled-corner radius; any other non-negative value is used as the
     * explicit ripple radius.
     */
    public static final double AUTO_RIPPLE_RADIUS = -1.0;

    private final BoundedClipSupport boundedClip = new BoundedClipSupport(this);
    private final StateLayer stateOverlay = new StateLayer();

    private Insets bleed = Insets.EMPTY;

    /**
     * Creates an unmanaged, mouse-transparent ripple layer.
     */
    public RippleLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
        // The overlay attaches only while shown and detaches once it fades out;
        // the embedded overlay shares this layer's clip, so it never clips itself.
        stateOverlay.setOnHidden(() -> getChildren().remove(stateOverlay));
    }

    void addRipple(Circle ripple) {
        getChildren().add(ripple);
    }

    void removeRipple(Circle ripple) {
        getChildren().remove(ripple);
    }

    void clearRipples() {
        getChildren().removeIf(node -> node != stateOverlay);
    }

    /**
     * Updates the bounded clip from the host's shape or background geometry and
     * resizes the embedded state overlay to the layer bounds.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     */
    public void updateClipFor(Region host, double width, double height) {
        updateClipFor(host, width, height, null, null);
    }

    /**
     * Updates the bounded clip with extra insets and resizes the state overlay
     * so it always covers the clip region, letting the inset clip shape it.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     * @param insets extra insets applied to the clip geometry (may be
     *               negative), or {@code null} to follow the host border edge
     */
    public void updateClipFor(Region host, double width, double height, Insets insets) {
        updateClipFor(host, width, height, insets, null);
    }

    /**
     * Updates the bounded clip with extra insets and explicit corner radii, and
     * resizes the state overlay so it always covers the clip region, letting the
     * inset clip shape it. The overlay is expanded outward to match any
     * negative-inset bleed: a clip can only trim, not enlarge, so an overlay
     * left at the layer bounds would keep its square corners and original size
     * when the clip extends past them.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     * @param insets extra insets applied to the clip geometry (may be
     *               negative), or {@code null} to follow the host border edge
     * @param radius explicit corner radii turning the clip into a single
     *               rounded rectangle, or {@code null} to mirror the host
     *               background geometry
     */
    public void updateClipFor(Region host, double width, double height,
                              Insets insets, CornerRadii radius) {
        boundedClip.updateClipFor(host, width, height, insets, radius);
        double bleedTop = insets == null ? 0.0 : Math.max(0.0, -insets.getTop());
        double bleedRight = insets == null ? 0.0 : Math.max(0.0, -insets.getRight());
        double bleedBottom = insets == null ? 0.0 : Math.max(0.0, -insets.getBottom());
        double bleedLeft = insets == null ? 0.0 : Math.max(0.0, -insets.getLeft());
        bleed = new Insets(bleedTop, bleedRight, bleedBottom, bleedLeft);
        stateOverlay.resizeRelocate(-bleedLeft, -bleedTop,
                width + bleedLeft + bleedRight, height + bleedTop + bleedBottom);
    }

    /**
     * Clears the bounded clip and tears down the state overlay.
     */
    public void clearClip() {
        boundedClip.clearClip();
        bleed = Insets.EMPTY;
        stateOverlay.reset();
        getChildren().remove(stateOverlay);
    }

    /**
     * Returns the outward bleed (per side, all components {@code >= 0}) produced
     * by negative ripple insets, so the ripple radius can reach the bled clip
     * corner instead of stopping at the layer bounds.
     *
     * @return the ripple bleed insets
     */
    Insets getRippleBleed() {
        return bleed;
    }

    // ==================== State Overlay ====================

    /**
     * Sets the fill of the embedded state overlay.
     *
     * @param fill the overlay fill, or {@code null} for no overlay
     */
    public void setOverlayFill(Paint fill) {
        stateOverlay.setFill(fill);
    }

    /**
     * Drives the state overlay toward the hover / pressed tier. The overlay node
     * is attached only while visible and below the ripple circles, so an idle
     * layer keeps only its ripple children.
     *
     * @param pressed whether the host is pressed (deepest tint)
     * @param hovered whether the pointer is inside (light tint)
     */
    public void setOverlayState(boolean pressed, boolean hovered) {
        setOverlayState(hovered, false, pressed, false);
    }

    /**
     * Drives the state overlay toward the highest-priority active tier and
     * attaches the overlay node when a tier becomes visible. Detachment is
     * handled by the overlay's fade-out hook.
     *
     * @param hover   whether the pointer is inside
     * @param focus   whether the host is focus-visible
     * @param pressed whether the host is pressed
     * @param dragged whether the host is being dragged
     */
    public void setOverlayState(boolean hover, boolean focus, boolean pressed, boolean dragged) {
        boolean justAttached = false;
        if ((hover || focus || pressed || dragged) && !getChildren().contains(stateOverlay)) {
            // Attach without resizing: the overlay keeps the bounds set by the
            // last updateClipFor (which resizes it even while detached), so it
            // stays expanded for any negative-inset bleed; resizing here would
            // reset that to the plain layer bounds, and a tier change does not
            // trigger a fresh layout pass.
            getChildren().add(0, stateOverlay);
            // Resolve CSS on the just-attached overlay before its tier opacities
            // are read below, so a themed / author-overridden .state-overlay tier
            // applies on the first show instead of one interaction late (the node
            // is attached on demand, so it is otherwise unstyled at first read).
            if (stateOverlay.getScene() != null) {
                stateOverlay.applyCss();
            }
            justAttached = true;
        }
        stateOverlay.setState(hover, focus, pressed, dragged);
        if (justAttached && stateOverlay.getTargetOpacity() == 0.0) {
            // The resolved tier opacity is 0 (e.g. a CSS tier explicitly set to
            // 0): setState started no fade and fires no hidden hook, so detach
            // the overlay we just attached to keep the idle layer to ripple
            // circles only. Guarded by justAttached so an in-flight fade-out
            // (which runs while still attached) is never cut short.
            getChildren().remove(stateOverlay);
        }
    }

    /**
     * Returns the opacity the state overlay is animating toward: a positive
     * level while shown, {@code 0} while hidden. Independent of the in-flight
     * fade animation.
     *
     * @return the target state-overlay opacity
     */
    public double getOverlayTargetOpacity() {
        return stateOverlay.getTargetOpacity();
    }

    // ==================== Ripple Radius ====================

    private final DoubleProperty rippleRadius = new SimpleStyleableDoubleProperty(
            StyleableProperties.RIPPLE_RADIUS, this, "rippleRadius", AUTO_RIPPLE_RADIUS);

    /**
     * Returns the explicit ripple radius, or {@link #AUTO_RIPPLE_RADIUS} when
     * the automatic radius applies.
     *
     * @return the ripple radius
     */
    double getRippleRadius() {
        return rippleRadius.get();
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RippleLayer, Number> RIPPLE_RADIUS =
                new CssMetaData<>("-rx-ripple-radius",
                        SizeConverter.getInstance(), AUTO_RIPPLE_RADIUS) {
                    @Override
                    public boolean isSettable(RippleLayer layer) {
                        return !layer.rippleRadius.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RippleLayer layer) {
                        return (StyleableProperty<Number>) layer.rippleRadius;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(RIPPLE_RADIUS);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
