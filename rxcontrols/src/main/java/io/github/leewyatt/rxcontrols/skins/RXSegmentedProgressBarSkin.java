package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
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
 * Default skin for {@link RXSegmentedProgressBar}. Renders a row of equal-width
 * pill segments and animates either a determinate left-to-right fill or a
 * single indeterminate highlight wave depending on the control's
 * {@code progress}.
 *
 * <p>Each segment is rendered as a {@link Rectangle} pair:
 * <ul>
 *   <li>a track rectangle painted with the unfilled colour, full segment width;</li>
 *   <li>a fill rectangle painted with the filled colour, also full segment
 *       width but clipped by a third {@code Rectangle} whose width is driven
 *       by the per-segment fill ratio.</li>
 * </ul>
 * The clip is plain (no arc) so partial fills cut a straight right edge even
 * when the segment itself is rounded into a pill — this matches conventional
 * progress-bar rendering and avoids the visual artefact of the fill's right
 * corner sweeping inward as it shrinks.
 *
 * <p>Two animation paths converge on the same {@code applyFills} method:
 * <ul>
 *   <li><b>Determinate</b>: {@code displayedProgress} tweens to
 *       {@code control.progress} and segment {@code i}'s fill is
 *       {@code clamp(N · displayedProgress − i, 0, 1)}.</li>
 *   <li><b>Indeterminate</b>: a global {@code phase} in {@code [0, 1)}
 *       advances linearly on an indefinite timeline; segment {@code i}'s local
 *       time is {@code (phase + i/N) % 1} and its fill follows a brief
 *       sinusoidal pulse, so a highlight wave appears to sweep across the row.</li>
 * </ul>
 *
 * <p>Boundary handling follows AGENTS.md §3.6: when
 * {@link RXSegmentedProgressBar#indeterminateCycleDurationProperty()
 * indeterminateCycleDuration} or
 * {@link RXSegmentedProgressBar#progressTransitionDurationProperty()
 * progressTransitionDuration} is {@code null} or non-positive the timeline is
 * skipped entirely and segments snap to a deterministic rest pose.
 */
public class RXSegmentedProgressBarSkin extends RXSkinBase<RXSegmentedProgressBar> {

    // ==================== Animation Constants ====================

    /**
     * Fraction of each segment's local cycle spent in the "active" pulse during
     * indeterminate mode. The rest of the cycle the segment is dormant
     * (unfilled). {@code 0.5} keeps roughly half the row visibly animating at
     * any moment for the default segment count.
     */
    private static final double INDETERMINATE_ACTIVE_FRACTION = 0.5;

    private static final double HALF = 0.5;

    // ==================== Layout Constants ====================

    /**
     * Default preferred row width when the parent gives no horizontal hint.
     * Matches JavaFX {@link javafx.scene.control.ProgressBar} so dropping a
     * segmented bar into the same slot does not change the layout footprint
     * unexpectedly.
     */
    private static final double DEFAULT_PREF_WIDTH = 150.0;

    // ==================== Nodes ====================

    private final List<Rectangle> trackRects = new ArrayList<>();
    private final List<Rectangle> fillRects = new ArrayList<>();
    /**
     * One clip per fill rectangle, kept as a parallel list so {@code applyFills}
     * can resize them by index without walking the scene graph. The clip nodes
     * are not part of {@code getChildren()} — JavaFX renders them implicitly via
     * {@link javafx.scene.Node#setClip(javafx.scene.Node)}.
     */
    private final List<Rectangle> fillClips = new ArrayList<>();

    // ==================== State ====================

    /** Visible [0,1] progress — diverges from {@code control.progress} during tween. */
    private final DoubleProperty displayedProgress =
            new SimpleDoubleProperty(this, "displayedProgress", 0.0);

    /** Indeterminate phase in [0, 1) — advanced linearly on an indefinite timeline. */
    private final DoubleProperty indeterminatePhase =
            new SimpleDoubleProperty(this, "indeterminatePhase", 0.0);

    private final TreeShowingProperty treeShowing;

    private Timeline progressTween;
    private Timeline indeterminateTimeline;
    private boolean indeterminateMode;

    /** Cached layout numbers used by {@link #applyFills()} to avoid re-querying geometry. */
    private double cachedSegmentWidth;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXSegmentedProgressBarSkin(RXSegmentedProgressBar control) {
        super(control);

        treeShowing = new TreeShowingProperty(control);
        disposer.registerDisposeTask(treeShowing::dispose);

        rebuildSegments();
        registerListeners(control);

        double initial = control.getProgress();
        if (initial >= 0.0) {
            indeterminateMode = false;
            displayedProgress.set(clamp(initial));
        } else {
            displayedProgress.set(0.0);
            startIndeterminate();
        }
    }

    // ==================== Init ====================

    private void registerListeners(RXSegmentedProgressBar control) {
        disposer.registerListener(control.progressProperty(), () -> onProgressChanged(control.getProgress()));

        disposer.registerListener(control.segmentCountProperty(), () -> {
            rebuildSegments();
            // The indeterminate timeline only drives phase, not segment-specific
            // values — applyFills reads N from the current fillClips list on
            // each frame, so the existing timeline keeps working after the
            // rebuild. layoutChildren will reapply fills on the next pulse.
            control.requestLayout();
        });
        disposer.registerListener(control.segmentGapProperty(), control::requestLayout);
        disposer.registerListener(control.segmentHeightProperty(), control::requestLayout);
        disposer.registerListener(control.segmentArcProperty(), this::applySegmentArc);
        disposer.registerListener(control.filledColorProperty(), this::applyColors);
        disposer.registerListener(control.unfilledColorProperty(), this::applyColors);
        disposer.registerListener(control.indeterminateCycleDurationProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });

        disposer.registerListener(displayedProgress, () -> {
            if (!indeterminateMode) {
                applyFills();
            }
        });
        disposer.registerListener(indeterminatePhase, () -> {
            if (indeterminateMode) {
                applyFills();
            }
        });

        disposer.registerListener(treeShowing, () -> {
            if (!indeterminateMode || indeterminateTimeline == null) {
                return;
            }
            if (treeShowing.get()) {
                indeterminateTimeline.play();
            } else {
                indeterminateTimeline.pause();
            }
        });
    }

    // ==================== Segment composition ====================

    private void rebuildSegments() {
        int n = clampSegmentCount(getSkinnable().getSegmentCount());
        Paint filled = paintOrDefault(getSkinnable().getFilledColor(), RXSegmentedProgressBar.DEFAULT_FILLED_COLOR);
        Paint unfilled = paintOrDefault(getSkinnable().getUnfilledColor(), RXSegmentedProgressBar.DEFAULT_UNFILLED_COLOR);
        double arc = sanitize(getSkinnable().getSegmentArc()) * 2.0;

        trackRects.clear();
        fillRects.clear();
        fillClips.clear();
        List<Rectangle> children = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            Rectangle track = new Rectangle();
            track.getStyleClass().add("track");
            track.setManaged(false);
            track.setMouseTransparent(true);
            track.setFill(unfilled);
            track.setArcWidth(arc);
            track.setArcHeight(arc);

            Rectangle fill = new Rectangle();
            fill.getStyleClass().add("segment-fill");
            fill.setManaged(false);
            fill.setMouseTransparent(true);
            fill.setFill(filled);
            fill.setArcWidth(arc);
            fill.setArcHeight(arc);

            Rectangle clip = new Rectangle();
            fill.setClip(clip);

            trackRects.add(track);
            fillRects.add(fill);
            fillClips.add(clip);
            children.add(track);
            children.add(fill);
        }
        getChildren().setAll(children);
    }

    private void applyColors() {
        Paint filled = paintOrDefault(getSkinnable().getFilledColor(), RXSegmentedProgressBar.DEFAULT_FILLED_COLOR);
        Paint unfilled = paintOrDefault(getSkinnable().getUnfilledColor(), RXSegmentedProgressBar.DEFAULT_UNFILLED_COLOR);
        for (Rectangle r : trackRects) {
            r.setFill(unfilled);
        }
        for (Rectangle r : fillRects) {
            r.setFill(filled);
        }
    }

    private void applySegmentArc() {
        double arc = sanitize(getSkinnable().getSegmentArc()) * 2.0;
        for (Rectangle r : trackRects) {
            r.setArcWidth(arc);
            r.setArcHeight(arc);
        }
        for (Rectangle r : fillRects) {
            r.setArcWidth(arc);
            r.setArcHeight(arc);
        }
    }

    // ==================== Determinate progress ====================

    private void onProgressChanged(double newProgress) {
        if (newProgress < 0.0) {
            startIndeterminate();
            return;
        }
        double target = clamp(newProgress);
        if (indeterminateMode) {
            stopIndeterminate();
        }
        stopProgressTween();

        Duration tween = getSkinnable().getProgressTransitionDuration();
        if (tween == null || tween.lessThanOrEqualTo(Duration.ZERO)) {
            displayedProgress.set(target);
            return;
        }
        progressTween = new Timeline(new KeyFrame(
                tween,
                new KeyValue(displayedProgress, target, Interpolator.EASE_OUT)
        ));
        progressTween.play();
    }

    private void stopProgressTween() {
        if (progressTween != null) {
            progressTween.stop();
            progressTween = null;
        }
    }

    // ==================== Indeterminate ====================

    private void startIndeterminate() {
        stopProgressTween();
        indeterminateMode = true;
        rebuildIndeterminateTimeline();
    }

    private void stopIndeterminate() {
        indeterminateMode = false;
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        indeterminatePhase.set(0.0);
        // displayedProgress is the entry point for the determinate tween in
        // onProgressChanged; do not snap it here or the incoming transition
        // would start from a discontinuous frame.
    }

    private void rebuildIndeterminateTimeline() {
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        if (!indeterminateMode) {
            return;
        }

        Duration cycle = getSkinnable().getIndeterminateCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // §1.8: snap to a deterministic rest pose so a stale frame cannot
            // linger when the user disables the animation mid-cycle.
            indeterminatePhase.set(0.0);
            applyFills();
            return;
        }

        indeterminatePhase.set(0.0);
        indeterminateTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(indeterminatePhase, 0.0, Interpolator.LINEAR)),
                new KeyFrame(cycle,
                        new KeyValue(indeterminatePhase, 1.0, Interpolator.LINEAR))
        );
        indeterminateTimeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            indeterminateTimeline.play();
        }
    }

    // ==================== Fill rendering ====================

    /**
     * Recomputes the per-segment fill ratio (in determinate or indeterminate
     * mode) and resizes each clip rectangle accordingly. This is the single
     * write site for the visible fill, so all animations end here.
     */
    private void applyFills() {
        int n = fillClips.size();
        if (n == 0 || cachedSegmentWidth <= 0.0) {
            return;
        }
        if (indeterminateMode) {
            double t = indeterminatePhase.get();
            for (int i = 0; i < n; i++) {
                double local = ((t + (double) i / n) % 1.0 + 1.0) % 1.0;
                double pulse = local < INDETERMINATE_ACTIVE_FRACTION
                        ? Math.sin(Math.PI * local / INDETERMINATE_ACTIVE_FRACTION)
                        : 0.0;
                fillClips.get(i).setWidth(cachedSegmentWidth * pulse);
            }
            return;
        }

        double p = clamp(displayedProgress.get());
        double globalFill = p * n;
        for (int i = 0; i < n; i++) {
            double ratio = globalFill - i;
            if (ratio < 0.0) {
                ratio = 0.0;
            } else if (ratio > 1.0) {
                ratio = 1.0;
            }
            fillClips.get(i).setWidth(cachedSegmentWidth * ratio);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        int n = fillRects.size();
        if (n == 0 || contentWidth <= 0.0 || contentHeight <= 0.0) {
            for (int i = 0; i < n; i++) {
                trackRects.get(i).setWidth(0.0);
                trackRects.get(i).setHeight(0.0);
                fillRects.get(i).setWidth(0.0);
                fillRects.get(i).setHeight(0.0);
                fillClips.get(i).setWidth(0.0);
                fillClips.get(i).setHeight(0.0);
            }
            cachedSegmentWidth = 0.0;
            return;
        }

        RXSegmentedProgressBar control = getSkinnable();
        double gap = sanitize(control.getSegmentGap());
        double segHeight = sanitize(control.getSegmentHeight());
        // Cap the rendered segment height to the available content height so a
        // too-tall segmentHeight on a clamped parent does not overflow.
        double renderedHeight = Math.min(segHeight, contentHeight);

        double totalGap = gap * Math.max(0, n - 1);
        double segWidth = Math.max(0.0, (contentWidth - totalGap) / n);
        cachedSegmentWidth = segWidth;

        double y = contentY + (contentHeight - renderedHeight) * HALF;
        for (int i = 0; i < n; i++) {
            double x = contentX + i * (segWidth + gap);

            Rectangle track = trackRects.get(i);
            track.setX(x);
            track.setY(y);
            track.setWidth(segWidth);
            track.setHeight(renderedHeight);

            Rectangle fill = fillRects.get(i);
            fill.setX(x);
            fill.setY(y);
            fill.setWidth(segWidth);
            fill.setHeight(renderedHeight);

            // Clip lives in the fill rectangle's coordinate space — the same
            // space used by Rectangle.x/y — so it must mirror the fill's
            // origin. With clip.x = fill.x, a clip.width of segWidth * ratio
            // reveals exactly the leftmost ratio fraction of the fill.
            Rectangle clip = fillClips.get(i);
            clip.setX(x);
            clip.setY(y);
            clip.setHeight(renderedHeight);
            // width is set by applyFills().
        }
        // applyFills depends on cachedSegmentWidth, which we just refreshed —
        // push the current fill so a resize is immediately visible without
        // waiting for the next animation tick.
        applyFills();
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // One pixel per segment plus gaps — the absolute floor below which the
        // bar stops being a meaningful progress display.
        int n = clampSegmentCount(getSkinnable().getSegmentCount());
        double gap = sanitize(getSkinnable().getSegmentGap());
        return leftInset + n + gap * Math.max(0, n - 1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + sanitize(getSkinnable().getSegmentHeight()) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + sanitize(getSkinnable().getSegmentHeight()) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        stopProgressTween();
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        // Detach clip nodes — they are not part of getChildren() and would be
        // retained by the fill rectangles otherwise.
        for (Rectangle r : fillRects) {
            r.setClip(null);
        }
        super.dispose();
    }

    // ==================== Helpers ====================

    private static int clampSegmentCount(int v) {
        if (v < RXSegmentedProgressBar.MIN_SEGMENT_COUNT) {
            return RXSegmentedProgressBar.MIN_SEGMENT_COUNT;
        }
        if (v > RXSegmentedProgressBar.MAX_SEGMENT_COUNT) {
            return RXSegmentedProgressBar.MAX_SEGMENT_COUNT;
        }
        return v;
    }

    private static double sanitize(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return v;
    }

    private static double clamp(double v) {
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
