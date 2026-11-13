package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.animation.burst.BurstFrame;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.List;

/**
 * Skin for {@link RXReactionButton}: the {@link RXToggleButtonSkin} (standard
 * toggle button plus ripple) with an icon pop and a burst canvas behind the
 * icon and text.
 *
 * <p>The burst canvas is an unmanaged child centered on the icon, hidden
 * between bursts so that it never adds to the button's bounds. Each burst is
 * a linear {@link Transition} that clears the canvas and lets the
 * {@link BurstAnimation} draw the frame; the pop is a {@link Scale} appended
 * to the graphic's transforms for its duration, so the graphic's own scale
 * properties are never touched.</p>
 */
public class RXReactionButtonSkin extends RXToggleButtonSkin {

    private static final Duration SELECT_POP_DURATION = Duration.millis(400.0);
    private static final Duration DESELECT_POP_DURATION = Duration.millis(200.0);
    private static final double MIN_BURST_SCALE = 0.25;
    private static final double MAX_BURST_SCALE = 4.0;
    // Reach of a burst at scale 1, in anchor widths, and its floor in pixels.
    private static final double REACH_PER_ANCHOR = 1.6;
    private static final double MIN_REACH = 24.0;
    // Keeps the canvas well inside common texture limits even on HiDPI.
    private static final double MAX_REACH = 480.0;
    // Weyl increment: consecutive plays get well-spread seeds.
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final Paint FALLBACK_BURST_COLOR = Color.web("#f56c6c");
    private static final double PALETTE_HUE_SHIFT = 40.0;

    private final SkinDisposer disposer = new SkinDisposer();
    private final Canvas burstLayer = new Canvas();
    private final BurstTransition burst = new BurstTransition();
    private final PopTransition pop = new PopTransition();
    private long playCount;

    /**
     * Creates the skin and wires the burst and pop triggers.
     *
     * @param button the reaction button this skin is attached to
     */
    public RXReactionButtonSkin(RXReactionButton button) {
        super(button);
        burstLayer.getStyleClass().add("burst-layer");
        burstLayer.setManaged(false);
        burstLayer.setMouseTransparent(true);
        burstLayer.setVisible(false);

        // Bubbling and unconsumed: fire() toggles the selection before it
        // dispatches the ActionEvent, so the handler sees the new state.
        // Only the button's own events: an action fired by a control nested
        // in the graphic is not a reaction.
        disposer.registerEventHandler(button, ActionEvent.ACTION, event -> {
            if (event.getTarget() == button) {
                onActivated();
            }
        });
        disposer.registerEventHandler(button, RXAnimationEvent.PLAY_ANIMATION, event -> {
            if (event.getTarget() != button) {
                return;
            }
            event.consume();
            if (button.isAnimated()) {
                playBurst();
            }
        });
        // Deselected any way at all (from code, by a toggle group, or rolled
        // back in onAction): the celebration ends. The shrink pop stays with
        // user activation.
        disposer.registerListener(button.selectedProperty(), () -> {
            if (!button.isSelected()) {
                burst.cancel();
            }
        });
        disposer.registerListener(button.burstScaleProperty(), () -> {
            if (burstLayer.isVisible()) {
                placeBurstLayer();
            }
        });
        disposer.registerListener(button.graphicProperty(), pop::cancel);
        disposer.registerListener(button.animatedProperty(), () -> {
            if (!button.isAnimated()) {
                burst.cancel();
                pop.cancel();
            }
        });

        // The super constructors ran updateChildren before this skin's
        // fields existed, so the canvas is added here.
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first calls come from the super constructors, before this
        // skin's fields are initialized. Above the ripple layer (index 0) and
        // below the icon and text, so particles come out from behind the
        // icon and never cover the count.
        if (burstLayer != null) {
            getChildren().add(Math.min(1, getChildren().size()), burstLayer);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        if (burstLayer.isVisible()) {
            placeBurstLayer();
        }
    }

    /**
     * Stops the burst and the pop, removes the burst canvas and unregisters
     * the triggers before the toggle button skin's own cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeBurst, disposer::dispose, super::dispose);
    }

    private void disposeBurst() {
        burst.cancel();
        pop.cancel();
        getChildren().remove(burstLayer);
    }

    // ==================== Triggers ====================

    private void onActivated() {
        RXReactionButton button = (RXReactionButton) getSkinnable();
        if (!button.isAnimated()) {
            return;
        }
        if (button.isSelected()) {
            pop.play(true);
            playBurst();
        } else {
            pop.play(false);
        }
    }

    private void playBurst() {
        RXReactionButton button = (RXReactionButton) getSkinnable();
        BurstAnimation animation = button.getBurstAnimation();
        Duration duration = burstDurationOrDefault(button.getBurstDuration());
        burst.cancel();
        if (animation == null || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        playCount++;
        burst.start(animation, duration, palette(button), System.nanoTime() ^ (playCount * SEED_STEP));
    }

    // ==================== Geometry ====================

    // The graphic counts only while the skin shows it: a TEXT_ONLY content
    // display, for one, leaves it out of the children.
    private Node visibleGraphic() {
        Node graphic = getSkinnable().getGraphic();
        return graphic != null && getChildren().contains(graphic) ? graphic : null;
    }

    private double anchorSize() {
        Node graphic = visibleGraphic();
        if (graphic != null) {
            Bounds bounds = graphic.getLayoutBounds();
            return Math.max(bounds.getWidth(), bounds.getHeight());
        }
        return Math.min(getSkinnable().getWidth(), getSkinnable().getHeight()) * 0.5;
    }

    private double reach() {
        RXReactionButton button = (RXReactionButton) getSkinnable();
        double scale = button.getBurstScale();
        if (!Double.isFinite(scale) || scale <= 0.0) {
            scale = RXReactionButton.DEFAULT_BURST_SCALE;
        }
        scale = Math.max(MIN_BURST_SCALE, Math.min(MAX_BURST_SCALE, scale));
        return Math.min(MAX_REACH, Math.max(MIN_REACH, anchorSize() * REACH_PER_ANCHOR) * scale);
    }

    // Particles are drawn up to about 0.4 anchor widths across beyond the
    // reach, so the canvas leaves that much room around it.
    private double burstMargin() {
        return anchorSize() * 0.4 + 2.0;
    }

    private void placeBurstLayer() {
        double side = Math.ceil(2.0 * (reach() + burstMargin()));
        if (burstLayer.getWidth() != side || burstLayer.getHeight() != side) {
            burstLayer.setWidth(side);
            burstLayer.setHeight(side);
        }
        double centerX;
        double centerY;
        Node graphic = visibleGraphic();
        if (graphic != null) {
            Bounds bounds = graphic.getLayoutBounds();
            centerX = graphic.getLayoutX() + bounds.getMinX() + bounds.getWidth() / 2.0;
            centerY = graphic.getLayoutY() + bounds.getMinY() + bounds.getHeight() / 2.0;
        } else {
            centerX = getSkinnable().getWidth() / 2.0;
            centerY = getSkinnable().getHeight() / 2.0;
        }
        burstLayer.relocate(centerX - side / 2.0, centerY - side / 2.0);
    }

    // ==================== Colors ====================

    private static List<Paint> palette(RXReactionButton button) {
        List<Paint> palette = button.getBurstPalette();
        if (palette != null && !palette.isEmpty()) {
            return palette;
        }
        Paint base = button.getBurstColor() == null ? FALLBACK_BURST_COLOR : button.getBurstColor();
        if (!(base instanceof Color)) {
            return List.of(base);
        }
        Color color = (Color) base;
        return List.of(color,
                color.deriveColor(PALETTE_HUE_SHIFT, 1.0, 1.0, 1.0),
                color.deriveColor(-PALETTE_HUE_SHIFT, 1.0, 1.0, 1.0),
                color.deriveColor(180.0, 1.0, 1.0, 1.0));
    }

    private static Duration burstDurationOrDefault(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThan(Duration.ZERO)) {
            return RXReactionButton.DEFAULT_BURST_DURATION;
        }
        return duration;
    }

    // ==================== Burst ====================

    private final class BurstTransition extends Transition {

        private BurstAnimation animation;
        private List<Paint> colors;
        private long seed;

        private BurstTransition() {
            setInterpolator(Interpolator.LINEAR);
            setOnFinished(event -> clear());
        }

        void start(BurstAnimation value, Duration duration, List<Paint> palette, long playSeed) {
            animation = value;
            colors = palette;
            seed = playSeed;
            setCycleDuration(duration);
            burstLayer.setVisible(true);
            placeBurstLayer();
            playFromStart();
        }

        @Override
        protected void interpolate(double frac) {
            if (animation == null) {
                return;
            }
            double side = burstLayer.getWidth();
            GraphicsContext gc = burstLayer.getGraphicsContext2D();
            gc.clearRect(0.0, 0.0, side, burstLayer.getHeight());
            gc.save();
            animation.draw(gc, new BurstFrame(frac, side / 2.0, burstLayer.getHeight() / 2.0,
                    reach(), anchorSize(), colors, seed));
            gc.restore();
        }

        // Not an override of stop(): playFromStart() calls stop() itself, which
        // would wipe the play that is just starting.
        void cancel() {
            stop();
            clear();
        }

        private void clear() {
            animation = null;
            colors = null;
            burstLayer.getGraphicsContext2D().clearRect(0.0, 0.0, burstLayer.getWidth(), burstLayer.getHeight());
            burstLayer.setVisible(false);
        }
    }

    // ==================== Pop ====================

    private final class PopTransition extends Transition {

        private final Scale scale = new Scale();
        private Node target;
        private boolean selecting;

        private PopTransition() {
            setInterpolator(Interpolator.LINEAR);
            setOnFinished(event -> detach());
        }

        void play(boolean select) {
            cancel();
            Node graphic = visibleGraphic();
            if (graphic == null) {
                return;
            }
            selecting = select;
            target = graphic;
            Bounds bounds = graphic.getLayoutBounds();
            scale.setPivotX(bounds.getMinX() + bounds.getWidth() / 2.0);
            scale.setPivotY(bounds.getMinY() + bounds.getHeight() / 2.0);
            graphic.getTransforms().add(scale);
            setCycleDuration(select ? SELECT_POP_DURATION : DESELECT_POP_DURATION);
            playFromStart();
        }

        // Selecting dips to 0.7, overshoots to 1.15 and settles; deselecting
        // only dips to 0.85.
        @Override
        protected void interpolate(double frac) {
            double value;
            if (selecting) {
                if (frac < 0.25) {
                    value = 1.0 - 0.3 * easeOut(frac / 0.25);
                } else if (frac < 0.65) {
                    value = 0.7 + 0.45 * easeOut((frac - 0.25) / 0.4);
                } else {
                    value = 1.15 - 0.15 * easeOut((frac - 0.65) / 0.35);
                }
            } else {
                value = frac < 0.5
                        ? 1.0 - 0.15 * easeOut(frac / 0.5)
                        : 0.85 + 0.15 * easeOut((frac - 0.5) / 0.5);
            }
            scale.setX(value);
            scale.setY(value);
        }

        // Not an override of stop(), for the same reason as the burst.
        void cancel() {
            stop();
            detach();
        }

        private void detach() {
            if (target != null) {
                target.getTransforms().remove(scale);
                target = null;
            }
            scale.setX(1.0);
            scale.setY(1.0);
        }

        private double easeOut(double t) {
            double u = 1.0 - t;
            return 1.0 - u * u;
        }
    }
}
