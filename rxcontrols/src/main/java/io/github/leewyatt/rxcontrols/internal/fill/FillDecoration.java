package io.github.leewyatt.rxcontrols.internal.fill;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * The fill sweep decoration shared by fill hosts ({@code RXFillButton},
 * {@code RXFillLabel}): an unmanaged layer revealing a fill color through a
 * progress clip, bounded to the host's painted geometry.
 *
 * <p>A single timeline drives the fill progress; the trigger state (hover or
 * pressed) plays it forward or, from the current progress, in reverse, so
 * interrupted sweeps reverse smoothly with proportional duration. While any
 * fill is visible the {@code :filling} pseudo-class is active on the host.
 * Disabling the host releases an active fill (a disabled node no longer
 * receives the exit/release event that would end it).</p>
 *
 * <p>The owning skin inserts {@link #getLayer()} into its children (below
 * label content), calls {@link #layout(double, double)} from
 * {@code layoutChildren} and {@link #dispose()} from its dispose chain
 * (children removal stays with the skin).</p>
 */
public final class FillDecoration {

    private static final PseudoClass FILLING_PSEUDO_CLASS = PseudoClass.getPseudoClass("filling");

    private static final FillAnimation DEFAULT_ANIMATION = FillAnimation.LEFT_TO_RIGHT;
    private static final RXAnimationTrigger DEFAULT_TRIGGER = RXAnimationTrigger.HOVER;
    private static final Duration DEFAULT_DURATION = Duration.millis(200.0);

    private final Control host;
    private final ObjectProperty<FillAnimation> animation;
    private final ObjectProperty<RXAnimationTrigger> trigger;
    private final ObjectProperty<Duration> duration;
    private final ObjectProperty<Insets> insets;
    private final ObjectProperty<CornerRadii> radius;

    private final SkinDisposer disposer = new SkinDisposer();
    private final Pane fillLayer = new Pane();
    private final Pane fillContent = new Pane();
    private final Region fillRegion = new Region();
    private final BoundedClipSupport boundedClip = new BoundedClipSupport(fillLayer);
    private final DoubleProperty fillProgress =
            new SimpleDoubleProperty(this, "fillProgress", 0.0);

    private Timeline fillTimeline;
    private FillAnimation appliedAnimation;
    private Node fillClip;

    /**
     * Creates the decoration and wires its triggers on the host.
     *
     * @param host      the control carrying the fill
     * @param animation the fill animation property
     * @param trigger   the animation trigger property
     * @param duration  the animation duration property
     * @param insets    the fill insets property
     * @param radius    the fill radius property
     */
    public FillDecoration(Control host,
                          ObjectProperty<FillAnimation> animation,
                          ObjectProperty<RXAnimationTrigger> trigger,
                          ObjectProperty<Duration> duration,
                          ObjectProperty<Insets> insets,
                          ObjectProperty<CornerRadii> radius) {
        this.host = host;
        this.animation = animation;
        this.trigger = trigger;
        this.duration = duration;
        this.insets = insets;
        this.radius = radius;

        fillLayer.getStyleClass().add("fill-layer");
        fillLayer.setManaged(false);
        fillLayer.setMouseTransparent(true);
        fillContent.getStyleClass().add("fill-content");
        fillContent.setManaged(false);
        fillRegion.getStyleClass().add("fill-region");
        fillRegion.setManaged(false);
        fillContent.getChildren().add(fillRegion);
        fillLayer.getChildren().add(fillContent);

        // ==================== Progress model ====================
        disposer.registerListener(fillProgress, this::updateFillGeometry);
        disposer.registerListener(animation, this::updateFillGeometry);
        disposer.registerListener(duration, this::rebuildTimeline);

        // ==================== Triggers ====================
        disposer.registerEventHandler(host, MouseEvent.MOUSE_ENTERED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_EXITED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(false);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_PRESSED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_RELEASED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(false);
            }
        });
        disposer.registerListener(trigger, () -> animateTo(isTriggerActive()));
        disposer.registerListener(host.sceneProperty(), () -> {
            if (host.getScene() == null) {
                snapTo(isTriggerActive());
            }
        });
        // A node disabled mid-gesture stops receiving the ending event.
        disposer.registerListener(host.disabledProperty(), () -> {
            if (host.isDisabled()) {
                animateTo(false);
            }
        });
        // Border width changes relayout via the insets chain on their own.
        disposer.registerListener(insets, host::requestLayout);
        disposer.registerListener(radius, host::requestLayout);

        rebuildTimeline();
        if (isTriggerActive()) {
            snapTo(true);
        }
    }

    /**
     * Returns the fill layer for the owning skin to insert into its children.
     *
     * @return the fill layer
     */
    public Pane getLayer() {
        return fillLayer;
    }

    /**
     * Lays out the fill layer over the host bounds and refreshes both clips.
     *
     * @param width  the host width
     * @param height the host height
     */
    public void layout(double width, double height) {
        Insets fillInsets = insets.get();
        Insets effective = fillInsets != null
                ? fillInsets
                : BoundedClipSupport.borderInsetsOf(host);
        double areaW = Math.max(0.0, width - effective.getLeft() - effective.getRight());
        double areaH = Math.max(0.0, height - effective.getTop() - effective.getBottom());
        fillLayer.resizeRelocate(0.0, 0.0, width, height);
        boundedClip.updateClipFor(host, width, height, fillInsets, radius.get());
        fillContent.resizeRelocate(effective.getLeft(), effective.getTop(), areaW, areaH);
        fillRegion.resizeRelocate(0.0, 0.0, areaW, areaH);
        updateFillGeometry();
    }

    /**
     * Stops the fill animation and unregisters all listeners; the owning skin
     * removes {@link #getLayer()} from its children itself.
     */
    public void dispose() {
        if (fillTimeline != null) {
            fillTimeline.stop();
        }
        boundedClip.clearClip();
        fillContent.setClip(null);
        appliedAnimation = null;
        fillClip = null;
        host.pseudoClassStateChanged(FILLING_PSEUDO_CLASS, false);
        disposer.dispose();
    }

    // ==================== Progress Model ====================

    private void animateTo(boolean active) {
        Duration value = duration.get();
        if (value != null && value.equals(Duration.ZERO)) {
            snapTo(active);
            return;
        }
        // Already resting at the target end: skip. Starting a finished
        // timeline jumps to the opposite end first (Animation.play with
        // lastPlayedFinished), which would replay a full phantom sweep.
        double progress = RXMath.clamp0To1(fillProgress.get());
        if (fillTimeline.getStatus() != Animation.Status.RUNNING
                && ((active && progress >= 1.0) || (!active && progress <= 0.0))) {
            return;
        }
        fillTimeline.setRate(active ? 1.0 : -1.0);
        fillTimeline.play();
    }

    private void snapTo(boolean active) {
        fillTimeline.stop();
        fillTimeline.jumpTo(active ? fillTimeline.getTotalDuration() : Duration.ZERO);
        fillProgress.set(active ? 1.0 : 0.0);
    }

    private void rebuildTimeline() {
        double progress = RXMath.clamp0To1(fillProgress.get());
        boolean running = false;
        double rate = 1.0;
        if (fillTimeline != null) {
            running = fillTimeline.getStatus() == Animation.Status.RUNNING;
            rate = fillTimeline.getRate();
            fillTimeline.stop();
        }
        Duration cycle = positiveDurationOrDefault();
        fillTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(fillProgress, 0.0, Interpolator.EASE_BOTH)),
                new KeyFrame(cycle,
                        new KeyValue(fillProgress, 1.0, Interpolator.EASE_BOTH)));
        fillTimeline.jumpTo(cycle.multiply(progress));
        if (running) {
            fillTimeline.setRate(rate);
            fillTimeline.play();
        }
    }

    private void updateFillGeometry() {
        // The fill area is the (possibly inset) fillContent box, sized by
        // layout(...) from the insets property or the host border.
        double areaW = fillContent.getWidth();
        double areaH = fillContent.getHeight();
        if (areaW <= 0.0 || areaH <= 0.0
                || !Double.isFinite(areaW) || !Double.isFinite(areaH)) {
            appliedAnimation = null;
            fillClip = null;
            fillContent.setClip(null);
            setFilling(false);
            return;
        }
        FillAnimation value = animationOrDefault();
        if (value != appliedAnimation || fillClip == null) {
            appliedAnimation = value;
            fillClip = value.createClip();
            fillContent.setClip(fillClip);
        }
        double progress = RXMath.clamp0To1(fillProgress.get());
        // Hide the layer at rest: a zero-progress clip should paint nothing,
        // but sub-pixel clip rasterization can leak a hairline of the fill.
        setFilling(progress > 0.0);
        value.update(fillClip, progress, areaW, areaH);
    }

    private void setFilling(boolean filling) {
        fillLayer.setVisible(filling);
        host.pseudoClassStateChanged(FILLING_PSEUDO_CLASS, filling);
    }

    // ==================== Trigger State ====================

    private RXAnimationTrigger triggerOrDefault() {
        RXAnimationTrigger value = trigger.get();
        return value == null ? DEFAULT_TRIGGER : value;
    }

    private FillAnimation animationOrDefault() {
        FillAnimation value = animation.get();
        return value == null ? DEFAULT_ANIMATION : value;
    }

    private Duration positiveDurationOrDefault() {
        Duration value = duration.get();
        if (value == null || value.isUnknown() || value.isIndefinite()
                || value.lessThanOrEqualTo(Duration.ZERO)) {
            return DEFAULT_DURATION;
        }
        return value;
    }

    private boolean isTriggerActive() {
        return triggerOrDefault() == RXAnimationTrigger.HOVER
                ? host.isHover()
                : host.isPressed();
    }
}
