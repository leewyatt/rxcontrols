package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.burst.BurstPlayback;
import io.github.leewyatt.rxcontrols.internal.burst.BurstSettings;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
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
 * between bursts so that it never adds to the button's bounds. A shared
 * burst playback clears it every frame and lets the {@link BurstAnimation}
 * draw. The pop is a {@link Scale} appended to the graphic's transforms for
 * its duration, so the graphic's own scale properties are never touched.</p>
 */
public class RXReactionButtonSkin extends RXToggleButtonSkin {

    private static final Duration SELECT_POP_DURATION = Duration.millis(400.0);
    private static final Duration DESELECT_POP_DURATION = Duration.millis(200.0);
    private static final Paint FALLBACK_BURST_COLOR = Color.web("#f56c6c");

    private final SkinDisposer disposer = new SkinDisposer();
    private final Canvas burstLayer = new Canvas();
    private final BurstPlayback burst =
            new BurstPlayback(burstLayer, this::reach, this::anchorSize, this::placeBurstLayer, () -> { });
    private final PopTransition pop = new PopTransition();

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
        // back in onAction): the celebration ends, the select pop with it.
        // The shrink pop stays with user activation, which starts it after
        // this listener has run.
        disposer.registerListener(button.selectedProperty(), () -> {
            if (!button.isSelected()) {
                burst.cancel();
                pop.cancel();
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
        burst.refresh();
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
        Duration duration = BurstSettings.durationOrDefault(button.getBurstDuration(),
                RXBurstOptions.DEFAULT_DURATION);
        burst.cancel();
        if (animation == null || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        List<Paint> colors = BurstSettings.palette(button.getBurstPalette(), button.getBurstColor(),
                FALLBACK_BURST_COLOR);
        burst.play(animation, duration, colors, BurstSettings.nextSeed());
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
        double scale = BurstSettings.scaleOrDefault(button.getBurstScale(), RXBurstOptions.DEFAULT_SCALE);
        return BurstSettings.reach(anchorSize(), scale);
    }

    private void placeBurstLayer() {
        double side = burstLayer.getWidth();
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
        // Snapped to device pixels, so particles stay sharp when the icon's
        // center falls on a half pixel.
        burstLayer.relocate(snapPositionX(centerX - side / 2.0), snapPositionY(centerY - side / 2.0));
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

        // Not an override of stop(): playFromStart() calls stop() itself, which
        // would wipe the play that is just starting.
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
