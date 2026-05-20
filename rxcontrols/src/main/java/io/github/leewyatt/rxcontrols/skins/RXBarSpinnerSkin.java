package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXBarSpinner.BarStyle;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXBarSpinner}. Renders a row of vertical
 * {@link Rectangle} bars and drives a shared {@code phase} property on an
 * indefinite {@link Timeline}; an invalidation listener fans the phase out to
 * each bar using a per-bar offset of {@code i / barCount}, then maps the
 * per-bar local time to a bar height via the curve selected by
 * {@link BarStyle}.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>The driver is a single {@link Timeline}, not one timeline per bar,
 *       so phase relationships stay exact across the cycle boundary and there
 *       is only one play / pause site to wire up to
 *       {@link TreeShowingProperty}.</li>
 *   <li>Each bar's "local time" is {@code (phase + i/N) % 1.0}; this maps
 *       through {@link BarStyle#WAVE} ({@code sin}) or {@link BarStyle#BOUNCE}
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

    // ==================== Nodes ====================

    private final List<Rectangle> bars = new ArrayList<>();

    /**
     * Global cycle position in {@code [0, 1)}. The timeline animates this
     * linearly; an invalidation listener fans it out to each bar's height.
     */
    private final DoubleProperty phase = new SimpleDoubleProperty(this, "phase", 0.0);

    private final TreeShowingProperty treeShowing;

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

        treeShowing = new TreeShowingProperty(control);
        disposer.registerDisposeTask(treeShowing::dispose);

        rebuildBars();
        registerListeners(control);

        rebuildTimeline();
        if (timeline == null) {
            // Animation disabled at construction (e.g. cycleDuration <= 0) —
            // §1.8: still snap the bars to a deterministic rest pose so the
            // first frame is not whatever defaults Rectangle was initialised
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
        disposer.registerListener(control.barArcProperty(), this::applyBarArc);
        disposer.registerListener(control.barColorProperty(), this::applyBarFill);
        disposer.registerListener(control.barStyleProperty(), this::refreshBars);
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
        int n = clampBarCount(getSkinnable().getBarCount());
        Paint fill = paintOrDefault(getSkinnable().getBarColor(), RXBarSpinner.DEFAULT_BAR_COLOR);
        double arc = sanitize(getSkinnable().getBarArc());

        bars.clear();
        for (int i = 0; i < n; i++) {
            Rectangle r = new Rectangle();
            r.getStyleClass().add("bar");
            r.setManaged(false);
            r.setMouseTransparent(true);
            r.setFill(fill);
            r.setArcWidth(arc * 2.0);
            r.setArcHeight(arc * 2.0);
            bars.add(r);
        }
        getChildren().setAll(bars);
    }

    private void applyBarFill() {
        Paint fill = paintOrDefault(getSkinnable().getBarColor(), RXBarSpinner.DEFAULT_BAR_COLOR);
        for (Rectangle r : bars) {
            r.setFill(fill);
        }
    }

    private void applyBarArc() {
        double arc = sanitize(getSkinnable().getBarArc()) * 2.0;
        for (Rectangle r : bars) {
            r.setArcWidth(arc);
            r.setArcHeight(arc);
        }
    }

    // ==================== Animation ====================

    private void rebuildTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        Duration cycle = getSkinnable().getCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Caller is responsible for following up with applyStaticRest()
            // — keeping that off this method lets barCount / style change
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
        BarStyle style = getSkinnable().getBarStyle();
        if (style == null) {
            style = RXBarSpinner.DEFAULT_BAR_STYLE;
        }
        double minH = cachedMinHeight;
        double range = cachedPeakHeight - minH;

        for (int i = 0; i < n; i++) {
            double local = ((t + (double) i / n) % 1.0 + 1.0) % 1.0;
            double k = curveValue(local, style);
            double h = minH + range * k;
            Rectangle r = bars.get(i);
            r.setHeight(h);
            r.setY(cachedBottomY - h);
        }
    }

    private static double curveValue(double local, BarStyle style) {
        return switch (style) {
            // sin maps [0, 1) to a full oscillation; shift by π/2 so the curve
            // starts at the peak — this puts the first bar at peak height at
            // t=0 rather than at the trough, which reads as a more decisive
            // wave start than starting on a zero crossing.
            case WAVE -> (1.0 + Math.sin(TWO_PI * local + Math.PI * HALF)) * HALF;
            // Tent: rises 0 → 1 in the first half, falls 1 → 0 in the second.
            // Sharper than sin so the bounce reads as discrete.
            case BOUNCE -> 1.0 - Math.abs(2.0 * local - 1.0);
        };
    }

    private void applyStaticRest() {
        if (cachedPeakHeight <= 0.0) {
            // First-frame guard: layout hasn't happened yet, so we can't
            // bottom-anchor properly. Drop bars to zero height — layout will
            // overwrite as soon as it runs.
            for (Rectangle r : bars) {
                r.setHeight(0.0);
            }
            return;
        }
        double h = cachedMinHeight;
        for (Rectangle r : bars) {
            r.setHeight(h);
            r.setY(cachedBottomY - h);
        }
    }

    /**
     * Pushes a fresh height to every bar — either by recomputing against the
     * current phase (timeline running) or by snapping to rest (timeline
     * disabled). Use when a non-timing property (style) changes mid-cycle and
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
            for (Rectangle r : bars) {
                r.setWidth(0.0);
                r.setHeight(0.0);
                r.setX(contentX);
                r.setY(contentY);
            }
            cachedBottomY = contentY + contentHeight;
            cachedPeakHeight = 0.0;
            cachedMinHeight = 0.0;
            return;
        }

        RXBarSpinner control = getSkinnable();
        double width = sanitize(control.getBarWidth());
        double gap = sanitize(control.getBarGap());
        double declaredHeight = sanitize(control.getBarHeight());
        // Cap peak to the available content height so a too-tall barHeight on
        // a clamped parent does not clip the bars.
        double peak = Math.min(declaredHeight, contentHeight);
        double ratio = clampRatio(control.getMinBarHeightRatio());
        double minH = peak * ratio;

        double rowWidth = n * width + Math.max(0, n - 1) * gap;
        double startX = contentX + (contentWidth - rowWidth) * HALF;
        double bottomY = contentY + contentHeight;

        cachedBottomY = bottomY;
        cachedPeakHeight = peak;
        cachedMinHeight = minH;

        for (int i = 0; i < n; i++) {
            Rectangle r = bars.get(i);
            r.setWidth(width);
            r.setX(startX + i * (width + gap));
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
        return topInset + sanitize(getSkinnable().getBarHeight()) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + computeRowWidth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + sanitize(getSkinnable().getBarHeight()) + bottomInset;
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
        int n = clampBarCount(getSkinnable().getBarCount());
        double width = sanitize(getSkinnable().getBarWidth());
        double gap = sanitize(getSkinnable().getBarGap());
        return n * width + Math.max(0, n - 1) * gap;
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners and treeShowing teardown are
        // handled by the embedded SkinDisposer in RXSkinBase.dispose().
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        super.dispose();
    }

    // ==================== Helpers ====================

    private static int clampBarCount(int v) {
        if (v < RXBarSpinner.MIN_BAR_COUNT) {
            return RXBarSpinner.MIN_BAR_COUNT;
        }
        if (v > RXBarSpinner.MAX_BAR_COUNT) {
            return RXBarSpinner.MAX_BAR_COUNT;
        }
        return v;
    }

    private static double sanitize(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return v;
    }

    private static double clampRatio(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    private static Paint paintOrDefault(Paint v, Paint fallback) {
        return v != null ? v : fallback;
    }
}
