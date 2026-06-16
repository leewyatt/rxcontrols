package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXBarSpinner.AnimationMode;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
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
 * Default skin for {@link RXBarSpinner}. Renders a row of vertical
 * {@link Region} bars using the {@code .bar} style class. Bar appearance is
 * delegated to CSS; the skin owns only bar count, geometry, and animation.
 *
 * <p>A shared {@code phase} property is driven by one indefinite
 * {@link Timeline}; an invalidation listener fans the phase out to each bar
 * using a per-bar offset of {@code i / barCount}, then maps the per-bar local
 * time to a bar height via the curve selected by {@link AnimationMode}.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>The driver is a single {@link Timeline}, not one timeline per bar,
 *       so phase relationships stay exact across the cycle boundary and there
 *       is only one play / pause site to wire up to
 *       {@link RXTreeShowingProperty}.</li>
 *   <li>Each bar's "local time" is {@code (phase + i/N) % 1.0}; this maps
 *       through {@link AnimationMode#WAVE} ({@code sin}) or {@link AnimationMode#BOUNCE}
 *       (tent) to a value in {@code [0, 1]}, then lerped from
 *       {@code minHeight} to {@code peakHeight}.</li>
 *   <li>{@code cycleDuration ≤ 0} or {@code null} disables the timeline per
 *       AGENTS.md §3.6 and snaps every bar to its minimum height so a stale
 *       frame cannot linger (§1.8).</li>
 *   <li>Bars are bottom-anchored so the row reads as growing upward, which is
 *       the conventional equalizer / spectrum-analyser metaphor.</li>
 * </ul>
 */
public class RXBarSpinnerSkin extends RXSkinBase<RXBarSpinner> {

    // ==================== Animation Constants ====================

    private static final double TWO_PI = Math.PI * 2.0;

    private static final double HALF = 0.5;

    /**
     * Fraction of each bar's local cycle spent in the "active" pulse for
     * {@link AnimationMode#BOUNCE}; the remainder is rest at the minimum height.
     * {@code 0.5} keeps roughly half the bars visibly bouncing at any moment
     * for the default 5-bar configuration, which reads as a sequence of pings
     * rather than a continuous wave.
     */
    private static final double BOUNCE_ACTIVE_FRACTION = 0.5;

    /**
     * Per-bar frequency multipliers for {@link AnimationMode#RANDOM}. Picked as
     * irrational-ish ratios so adjacent bars do not visibly fall back into
     * phase; when the user renders more bars than this table contains the
     * pattern repeats.
     */
    private static final double[] RANDOM_FREQUENCIES = {
            1.0, 1.7, 1.3, 2.1, 1.5, 1.9,
            1.1, 2.3, 1.8, 1.2, 2.0, 1.4
    };

    /**
     * Per-bar starting phase offsets for {@link AnimationMode#RANDOM}, in cycle
     * units {@code [0, 1)}. Hand-spread across the range so no two adjacent
     * bars share a starting position.
     */
    private static final double[] RANDOM_PHASE_OFFSETS = {
            0.00, 0.31, 0.67, 0.14, 0.83, 0.41,
            0.59, 0.22, 0.71, 0.05, 0.93, 0.48
    };

    // ==================== Pseudo-Classes ====================

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");

    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    // ==================== Nodes ====================

    private final List<Region> bars = new ArrayList<>();

    /**
     * Global cycle position in {@code [0, 1)}. The timeline animates this
     * linearly; an invalidation listener fans it out to each bar's height.
     */
    private final DoubleProperty phase = new SimpleDoubleProperty(this, "phase", 0.0);

    private final ReadOnlyBooleanProperty treeShowing;

    private Timeline timeline;

    /** Cached geometry — kept so the phase listener can update bar heights without re-querying layout. */
    private double cachedBottomY;
    private double cachedPeakHeight;
    private double cachedMinHeight;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXBarSpinnerSkin(RXBarSpinner control) {
        super(control);

        treeShowing = controlTreeShowingProperty();

        rebuildBars();
        registerListeners(control);

        rebuildTimeline();
        if (timeline == null) {
            // Animation disabled at construction (e.g. cycleDuration <= 0) —
            // §1.8: still snap the bars to a deterministic rest pose so the
            // first frame is not whatever defaults Region was initialised
            // to. Layout will overwrite once dimensions are known; this only
            // matters for the brief window before the first layout pass.
            applyStaticRest();
        }
    }

    // ==================== Init ====================

    private void registerListeners(RXBarSpinner control) {
        disposer.registerListener(control.barCountProperty(), () -> {
            rebuildBars();
            control.requestLayout();
            // Rebuild the timeline too: the per-bar phase offset uses N, and
            // the running animation referenced the previous bar list.
            rebuildTimeline();
            if (timeline == null) {
                applyStaticRest();
            }
        });
        disposer.registerListener(control.barWidthProperty(), control::requestLayout);
        disposer.registerListener(control.barHeightProperty(), control::requestLayout);
        disposer.registerListener(control.barGapProperty(), control::requestLayout);
        disposer.registerListener(control.animationModeProperty(), this::refreshBars);
        disposer.registerListener(control.minBarHeightRatioProperty(), control::requestLayout);
        disposer.registerListener(control.cycleDurationProperty(), () -> {
            rebuildTimeline();
            if (timeline == null) {
                applyStaticRest();
            }
        });

        disposer.registerListener(phase, this::updateBarHeights);

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

    // ==================== Bar composition ====================

    private void rebuildBars() {
        int n = renderedBarCount();

        bars.clear();
        for (int i = 0; i < n; i++) {
            Region r = new Region();
            r.getStyleClass().add("bar");
            r.setManaged(false);
            r.setMouseTransparent(true);
            r.pseudoClassStateChanged(FIRST, i == 0);
            r.pseudoClassStateChanged(LAST, i == n - 1);
            bars.add(r);
        }
        getChildren().setAll(bars);
    }

    // ==================== Animation ====================

    private void rebuildTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        Duration cycle = getSkinnable().getCycleDuration();
        if (renderedBarCount() == 0 || cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Caller is responsible for following up with applyStaticRest()
            // — keeping that off this method lets barCount / duration change
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

    private void updateBarHeights() {
        int n = bars.size();
        if (n == 0 || cachedPeakHeight <= 0.0) {
            return;
        }
        double t = phase.get();
        AnimationMode mode = getSkinnable().getAnimationMode();
        if (mode == null) {
            mode = RXBarSpinner.DEFAULT_ANIMATION_MODE;
        }
        double minH = cachedMinHeight;
        double range = cachedPeakHeight - minH;

        for (int i = 0; i < n; i++) {
            double local = computeLocal(t, i, n, mode);
            double k = curveValue(local, mode);
            double h = minH + range * k;
            Region r = bars.get(i);
            r.resizeRelocate(r.getLayoutX(), cachedBottomY - h, r.getWidth(), h);
        }
    }

    /**
     * Maps the shared global {@code phase} into a per-bar local cycle
     * position. The mapping is what visually distinguishes the styles:
     * <ul>
     *   <li>{@code WAVE} / {@code BOUNCE}: i/N phase offset → travelling wave</li>
     *   <li>{@code PULSE}: no offset → all bars in lock-step</li>
     *   <li>{@code RANDOM}: per-bar frequency × global phase + per-bar offset
     *       → uncorrelated jitter</li>
     * </ul>
     *
     * @return cycle position in {@code [0, 1)}
     */
    private static double computeLocal(double phase, int i, int n, AnimationMode mode) {
        double raw = switch (mode) {
            case PULSE -> phase;
            case RANDOM -> {
                double freq = RANDOM_FREQUENCIES[i % RANDOM_FREQUENCIES.length];
                double offset = RANDOM_PHASE_OFFSETS[i % RANDOM_PHASE_OFFSETS.length];
                yield phase * freq + offset;
            }
            default -> phase + (double) i / n;
        };
        return mod1(raw);
    }

    private static double mod1(double v) {
        double m = v % 1.0;
        return m < 0.0 ? m + 1.0 : m;
    }

    private static double curveValue(double local, AnimationMode mode) {
        return switch (mode) {
            // WAVE / PULSE / RANDOM: full oscillation in [0, 1) — every bar is
            // always somewhere on the curve. Shift by π/2 so the curve starts
            // at the peak — at t=0 PULSE / WAVE put the leading bar at peak
            // height (decisive start instead of a zero crossing).
            case WAVE, PULSE, RANDOM ->
                    (1.0 + Math.sin(TWO_PI * local + Math.PI * HALF)) * HALF;
            // BOUNCE: a half-cycle sine pulse during the first BOUNCE_ACTIVE_FRACTION
            // of the local cycle, then rest at 0 for the remainder. Combined
            // with the i/N phase offset, only ACTIVE_FRACTION × N bars are
            // bouncing at any instant — the others sit at minimum height. Reads
            // as discrete pings rather than a continuous wave, which is the
            // visual distinction from WAVE.
            case BOUNCE -> (local < BOUNCE_ACTIVE_FRACTION)
                    ? Math.sin(Math.PI * local / BOUNCE_ACTIVE_FRACTION)
                    : 0.0;
        };
    }

    private void applyStaticRest() {
        if (cachedPeakHeight <= 0.0) {
            // First-frame guard: layout hasn't happened yet, so we can't
            // bottom-anchor properly. Drop bars to zero height — layout will
            // overwrite as soon as it runs.
            for (Region r : bars) {
                r.resize(r.getWidth(), 0.0);
            }
            return;
        }
        double h = cachedMinHeight;
        for (Region r : bars) {
            r.resizeRelocate(r.getLayoutX(), cachedBottomY - h, r.getWidth(), h);
        }
    }

    /**
     * Pushes a fresh height to every bar — either by recomputing against the
     * current phase (timeline running) or by snapping to rest (timeline
     * disabled). Use when a non-timing property changes mid-cycle and
     * we want the new curve visible this frame instead of next.
     */
    private void refreshBars() {
        if (timeline == null) {
            applyStaticRest();
        } else {
            updateBarHeights();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        int n = bars.size();
        if (n == 0 || contentWidth <= 0.0 || contentHeight <= 0.0) {
            for (Region r : bars) {
                r.resizeRelocate(contentX, contentY, 0.0, 0.0);
            }
            cachedBottomY = contentY + contentHeight;
            cachedPeakHeight = 0.0;
            cachedMinHeight = 0.0;
            return;
        }

        RXBarSpinner control = getSkinnable();
        double width = RXMath.sanitizeNonNegative(control.getBarWidth());
        double gap = RXMath.sanitizeNonNegative(control.getBarGap());
        double declaredHeight = RXMath.sanitizeNonNegative(control.getBarHeight());
        // Cap peak to the available content height so a too-tall barHeight on
        // a clamped parent does not clip the bars.
        double peak = Math.min(declaredHeight, contentHeight);
        double ratio = RXMath.clamp0To1(control.getMinBarHeightRatio());
        double minH = peak * ratio;

        double rowWidth = n * width + Math.max(0, n - 1) * gap;
        double startX = contentX + (contentWidth - rowWidth) * HALF;
        double bottomY = contentY + contentHeight;

        cachedBottomY = bottomY;
        cachedPeakHeight = peak;
        cachedMinHeight = minH;

        for (int i = 0; i < n; i++) {
            Region r = bars.get(i);
            r.resizeRelocate(startX + i * (width + gap), bottomY, width, 0.0);
        }
        // Heights/y depend on phase; refresh against the current phase (or
        // rest pose if the timeline is disabled).
        refreshBars();
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + computeRowWidth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        if (renderedBarCount() == 0) {
            // No rendered bars means no content footprint, regardless of configured barHeight.
            return topInset + bottomInset;
        }
        return topInset + RXMath.sanitizeNonNegative(getSkinnable().getBarHeight()) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + computeRowWidth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        if (renderedBarCount() == 0) {
            // No rendered bars means no content footprint, regardless of configured barHeight.
            return topInset + bottomInset;
        }
        return topInset + RXMath.sanitizeNonNegative(getSkinnable().getBarHeight()) + bottomInset;
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
        int n = renderedBarCount();
        double width = RXMath.sanitizeNonNegative(getSkinnable().getBarWidth());
        double gap = RXMath.sanitizeNonNegative(getSkinnable().getBarGap());
        return n * width + Math.max(0, n - 1) * gap;
    }

    private int renderedBarCount() {
        return Math.max(0, getSkinnable().getBarCount());
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners and treeShowing teardown are
        // handled by the base disposer.
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
