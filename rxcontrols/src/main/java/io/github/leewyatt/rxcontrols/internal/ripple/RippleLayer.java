package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles and a hover state
 * overlay.
 *
 * <p>The bounded clip follows the host region's shape or background geometry
 * via {@link BoundedClipSupport}; see that class for the exact clip contract.
 * The hover state overlay is a low-opacity tint attached only while shown, so
 * an idle layer holds only ripple circles, and it shares the same bounded
 * clip because it lives inside this layer.</p>
 */
public final class RippleLayer extends Region {

    private static final String STYLE_CLASS = "ripple-layer";
    private static final String OVERLAY_STYLE_CLASS = "state-overlay";

    /**
     * Peak opacity of the hover state overlay: a fixed Material-style level,
     * independent of the ripple opacity and not exposed for styling.
     */
    private static final double HOVER_OVERLAY_OPACITY = 0.08;

    private static final Duration OVERLAY_FADE_DURATION = Duration.millis(150.0);

    private final BoundedClipSupport boundedClip = new BoundedClipSupport(this);
    private final Region stateOverlay = new Region();

    private Timeline overlayFade;
    private double overlayTarget;
    private Insets bleed = Insets.EMPTY;

    /**
     * Creates an unmanaged, mouse-transparent ripple layer.
     */
    public RippleLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
        stateOverlay.getStyleClass().add(OVERLAY_STYLE_CLASS);
        stateOverlay.setManaged(false);
        stateOverlay.setMouseTransparent(true);
        stateOverlay.setOpacity(0.0);
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
     * resizes the hover state overlay to the layer bounds.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     */
    public void updateClipFor(Region host, double width, double height) {
        updateClipFor(host, width, height, null, null);
    }

    /**
     * Updates the bounded clip with extra insets and resizes the hover state
     * overlay so it always covers the clip region, letting the inset clip
     * shape it.
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
     * resizes the hover state overlay so it always covers the clip region,
     * letting the inset clip shape it. The overlay is expanded outward to match
     * any negative-inset bleed: a clip can only trim, not enlarge, so an overlay
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
     * Clears the bounded clip and tears down the hover state overlay.
     */
    public void clearClip() {
        boundedClip.clearClip();
        bleed = Insets.EMPTY;
        resetOverlay();
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
     * Sets the fill of the hover state overlay. The overlay paints this fill at
     * a low fixed opacity; the layer clip rounds it to the host geometry.
     *
     * @param fill the overlay fill, or {@code null} for no overlay
     */
    public void setOverlayFill(Paint fill) {
        stateOverlay.setBackground(fill == null ? null
                : new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /**
     * Shows or hides the hover state overlay, fading toward its target opacity.
     * The overlay node is attached only while shown and below the ripple
     * circles, so an idle layer keeps only its ripple children.
     *
     * @param active whether the overlay should be visible
     */
    public void setOverlayState(boolean active) {
        double target = active ? HOVER_OVERLAY_OPACITY : 0.0;
        if (target == overlayTarget) {
            return;
        }
        overlayTarget = target;
        if (active && !getChildren().contains(stateOverlay)) {
            // The overlay keeps the bounds set by the last updateClipFor (which
            // resizes it even while detached), so it stays expanded for any
            // negative-inset bleed; resizing here would reset that to the plain
            // layer bounds, and hovering does not trigger a fresh layout pass.
            getChildren().add(0, stateOverlay);
        }
        stop(overlayFade);
        overlayFade = new Timeline(new KeyFrame(OVERLAY_FADE_DURATION,
                new KeyValue(stateOverlay.opacityProperty(), target, Interpolator.EASE_BOTH)));
        overlayFade.setOnFinished(event -> {
            if (overlayTarget == 0.0) {
                getChildren().remove(stateOverlay);
            }
        });
        overlayFade.play();
    }

    /**
     * Returns the opacity the state overlay is animating toward: a positive
     * level while shown, {@code 0} while hidden. Independent of the in-flight
     * fade animation.
     *
     * @return the target state-overlay opacity
     */
    public double getOverlayTargetOpacity() {
        return overlayTarget;
    }

    private void resetOverlay() {
        stop(overlayFade);
        overlayFade = null;
        overlayTarget = 0.0;
        stateOverlay.setOpacity(0.0);
        getChildren().remove(stateOverlay);
    }

    private static void stop(Animation animation) {
        if (animation != null) {
            animation.stop();
        }
    }
}
