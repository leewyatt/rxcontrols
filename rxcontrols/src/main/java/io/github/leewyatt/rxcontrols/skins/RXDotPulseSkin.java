package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXDotPulse.AnimationMode;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXDotPulse}. Renders a row of unmanaged
 * {@link Region} dots using the {@code .dot} style class. Dot appearance is
 * delegated to CSS; the user-agent stylesheet provides the default circular
 * fill via {@code -rx-dot-fill}.
 *
 * <p>A single {@link Timeline} drives a shared phase value. Each dot derives
 * its local phase from its index, then maps that phase to translate, scale, or
 * opacity according to {@link AnimationMode}. The first and last dots expose
 * {@code :first} and {@code :last} pseudo-classes for CSS theming.
 */
public class RXDotPulseSkin extends RXSkinBase<RXDotPulse> {

    // ==================== Animation Constants ====================

    /**
     * Fraction of each dot's local cycle spent in the "active" pulse; the
     * remainder is rest. {@code 0.5} keeps about half the dots visibly
     * animating at any given moment for the default 3-dot configuration,
     * which reads as the classic typing indicator.
     */
    private static final double ACTIVE_FRACTION = 0.5;

    /**
     * Peak upward translation for {@link AnimationMode#BOUNCE}, expressed as a
     * fraction of {@link RXDotPulse#dotSizeProperty() dotSize}, before the
     * amplitude multiplier is applied.
     */
    private static final double BOUNCE_PEAK_FACTOR = 0.75;

    /**
     * Peak scale increment for {@link AnimationMode#PULSE}, before the
     * amplitude multiplier is applied. Combined with amplitude {@code = 1}
     * the dot grows to {@code 1.5x}.
     */
    private static final double PULSE_SCALE_INCREMENT = 0.5;

    /**
     * Resting-opacity reduction for {@link AnimationMode#FADE} at amplitude
     * {@code = 1}: dots at rest sit at {@code 1.0 − 0.7 = 0.3}, rising to
     * {@code 1.0} at the peak.
     */
    private static final double FADE_RESTING_REDUCTION = 0.7;

    // ==================== Layout Constants ====================

    /**
     * Multiplier applied to {@link RXDotPulse#dotSizeProperty() dotSize} to
     * derive {@code prefHeight}. Two times the dot size gives the row enough
     * headroom for the default-amplitude bounce without exposing a separate
     * "vertical padding" property.
     */
    private static final double PREF_HEIGHT_FACTOR = 2.0;

    private static final double HALF = 0.5;

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    // ==================== Nodes ====================

    private final List<Region> dots = new ArrayList<>();

    /**
     * Global cycle position in {@code [0, 1)}. The timeline animates this
     * linearly; an invalidation listener fans it out to each dot's transform.
     */
    private final DoubleProperty phase = new SimpleDoubleProperty(this, "phase", 0.0);

    private final ReadOnlyBooleanProperty treeShowing;

    private Timeline timeline;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXDotPulseSkin(RXDotPulse control) {
        super(control);

        treeShowing = controlTreeShowingProperty();

        rebuildDots();
        registerListeners(control);

        rebuildTimeline();
        if (timeline == null) {
            // Keep the first frame initialized when animation is disabled.
            applyStaticRest();
        }
    }

    // ==================== Init ====================

    private void registerListeners(RXDotPulse control) {
        disposer.registerListener(control.dotCountProperty(), () -> {
            rebuildDots();
            control.requestLayout();
            // Rebuild the timeline too: the per-dot phase offset uses N, and
            // the running animation referenced the previous dot list.
            rebuildTimeline();
            if (timeline == null) {
                applyStaticRest();
            }
        });
        disposer.registerListener(control.dotSizeProperty(), control::requestLayout);
        disposer.registerListener(control.dotGapProperty(), control::requestLayout);
        disposer.registerListener(control.animationModeProperty(), this::refreshDots);
        disposer.registerListener(control.cycleDurationProperty(), () -> {
            rebuildTimeline();
            if (timeline == null) {
                applyStaticRest();
            }
        });
        disposer.registerListener(control.amplitudeProperty(), this::refreshDots);

        disposer.registerListener(phase, this::updateDotStates);

        disposer.registerListener(treeShowing, () -> {
            if (timeline == null) {
                return;
            }
            if (treeShowing.get()) {
                timeline.play();
            } else {
                timeline.pause();
            }
        });
    }

    // ==================== Dot composition ====================

    private void rebuildDots() {
        int n = RXMath.clamp(getSkinnable().getDotCount(),
                RXDotPulse.MIN_DOT_COUNT, RXDotPulse.MAX_DOT_COUNT);

        dots.clear();
        for (int i = 0; i < n; i++) {
            Region r = new Region();
            r.getStyleClass().add("dot");
            r.setManaged(false);
            dots.add(r);
        }
        dots.get(0).pseudoClassStateChanged(FIRST, true);
        dots.get(n - 1).pseudoClassStateChanged(LAST, true);
        getChildren().setAll(dots);
    }

    // ==================== Animation ====================

    private void rebuildTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        Duration cycle = getSkinnable().getCycleDuration();
        if (cycle == null || cycle.isUnknown() || cycle.isIndefinite()
                || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Caller is responsible for following up with applyStaticRest()
            // — keeping that off this method lets dotCount/mode change
            // paths share a single "stop + reset" sequence (see registerListeners).
            phase.set(0.0);
            return;
        }

        phase.set(0.0);
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(phase, 0.0, Interpolator.LINEAR)),
                new KeyFrame(cycle,
                        new KeyValue(phase, 1.0, Interpolator.LINEAR))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            timeline.play();
        }
    }

    private void updateDotStates() {
        int n = dots.size();
        if (n == 0) {
            return;
        }
        double t = phase.get();
        AnimationMode mode = getSkinnable().getAnimationMode();
        if (mode == null) {
            mode = RXDotPulse.DEFAULT_ANIMATION_MODE;
        }
        double amp = RXMath.sanitizeNonNegative(getSkinnable().getAmplitude());
        double size = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());

        for (int i = 0; i < n; i++) {
            double local = ((t + (double) i / n) % 1.0 + 1.0) % 1.0;
            applyDotState(dots.get(i), local, mode, amp, size);
        }
    }

    private void applyStaticRest() {
        for (Region r : dots) {
            r.setTranslateY(0.0);
            r.setScaleX(1.0);
            r.setScaleY(1.0);
            r.setOpacity(1.0);
        }
    }

    /**
     * Pushes a fresh transform to every dot — either by recomputing against
     * the current phase (timeline running) or by snapping to rest (timeline
     * disabled). Use when a non-timing property (mode, amplitude) changes
     * mid-cycle and we want the new effect visible this frame instead of next.
     */
    private void refreshDots() {
        if (timeline == null) {
            applyStaticRest();
        } else {
            updateDotStates();
        }
    }

    private static void applyDotState(Region r, double local, AnimationMode mode,
                                      double amp, double size) {
        double pulse = (local < ACTIVE_FRACTION)
                ? Math.sin(Math.PI * local / ACTIVE_FRACTION)
                : 0.0;

        switch (mode) {
            case BOUNCE -> {
                r.setTranslateY(-pulse * amp * size * BOUNCE_PEAK_FACTOR);
                r.setScaleX(1.0);
                r.setScaleY(1.0);
                r.setOpacity(1.0);
            }
            case PULSE -> {
                double scale = 1.0 + pulse * amp * PULSE_SCALE_INCREMENT;
                r.setTranslateY(0.0);
                r.setScaleX(scale);
                r.setScaleY(scale);
                r.setOpacity(1.0);
            }
            case FADE -> {
                double restingReduction = Math.min(1.0, amp * FADE_RESTING_REDUCTION);
                double opacity = 1.0 - restingReduction * (1.0 - pulse);
                r.setTranslateY(0.0);
                r.setScaleX(1.0);
                r.setScaleY(1.0);
                r.setOpacity(RXMath.clamp0To1(opacity));
            }
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        int n = dots.size();
        if (n == 0 || contentWidth <= 0.0 || contentHeight <= 0.0) {
            for (Region r : dots) {
                r.resizeRelocate(contentX, contentY, 0.0, 0.0);
            }
            return;
        }

        double size = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());
        double gap = RXMath.sanitizeNonNegative(getSkinnable().getDotGap());
        double radius = size * HALF;
        double rowWidth = n * size + Math.max(0, n - 1) * gap;
        double startCenterX = contentX + (contentWidth - rowWidth) * HALF + radius;
        // Bottom-anchor the row so the BOUNCE translateY (upward) stays
        // visible within the content box even when the user shrinks
        // prefHeight below the default-derived margin.
        double centerY = contentY + contentHeight - radius;

        for (int i = 0; i < n; i++) {
            Region r = dots.get(i);
            double cx = startCenterX + i * (size + gap);
            r.resizeRelocate(cx - radius, centerY - radius, size, size);
        }
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + computeRowWidth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + RXMath.sanitizeNonNegative(getSkinnable().getDotSize()) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + computeRowWidth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double size = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());
        double amp = RXMath.sanitizeNonNegative(getSkinnable().getAmplitude());
        // Always leave room for the bounce headroom, even on PULSE/FADE — the
        // user may switch styles at runtime and we should not have to relayout
        // on every change.
        double minimal = size * PREF_HEIGHT_FACTOR;
        double withBounce = size + amp * size * BOUNCE_PEAK_FACTOR;
        return topInset + Math.max(minimal, withBounce) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    private double computeRowWidth() {
        int n = RXMath.clamp(getSkinnable().getDotCount(),
                RXDotPulse.MIN_DOT_COUNT, RXDotPulse.MAX_DOT_COUNT);
        double size = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());
        double gap = RXMath.sanitizeNonNegative(getSkinnable().getDotGap());
        return n * size + Math.max(0, n - 1) * gap;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners, transforms, and treeShowing
        // teardown are handled by the base disposer.
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
