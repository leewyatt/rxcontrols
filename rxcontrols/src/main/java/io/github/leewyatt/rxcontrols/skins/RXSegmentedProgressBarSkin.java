package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSegmentedProgressBar}. Renders a row of
 * equal-width CSS-styled segment regions and animates either a determinate
 * left-to-right fill or a single indeterminate highlight band depending on the
 * control's {@code progress}.
 *
 * <p>Each segment is rendered as a {@link Region} pair:
 * <ul>
 *   <li>a track region styled by {@code .track}, full segment width;</li>
 *   <li>a fill region styled by {@code .segment-fill}, also full segment
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
 *   <li><b>Indeterminate</b>: a fixed-width band moves from left to right;
 *       each segment clips that band to its own bounds, so the visible fill is
 *       the geometric overlap between the band and the segment.</li>
 * </ul>
 *
 * <p>When
 * {@link RXSegmentedProgressBar#indeterminateCycleDurationProperty()
 * indeterminateCycleDuration} or
 * {@link RXSegmentedProgressBar#progressTransitionDurationProperty()
 * progressTransitionDuration} is {@code null} or non-positive, the relevant
 * timeline is skipped: determinate fills jump directly to the target and
 * indeterminate fills clear to an empty row.
 */
public class RXSegmentedProgressBarSkin extends RXSkinBase<RXSegmentedProgressBar> {

    // ==================== Layout Constants ====================

    private static final double HALF = 0.5;

    /**
     * Default preferred row width when the parent gives no horizontal hint.
     * Matches JavaFX {@link javafx.scene.control.ProgressBar} so dropping a
     * segmented bar into the same slot does not change the layout footprint
     * unexpectedly.
     */
    private static final double DEFAULT_PREF_WIDTH = 150.0;

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");

    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    // ==================== Nodes ====================

    private final List<Region> trackRegions = new ArrayList<>();
    private final List<Region> fillRegions = new ArrayList<>();
    /**
     * One clip per fill region, kept as a parallel list so {@code applyFills}
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
    private double cachedContentX;
    private double cachedContentWidth;
    private double cachedSegmentWidth;
    private double[] cachedSegmentStartX = new double[0];

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
            displayedProgress.set(RXMath.clamp0To1(initial));
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
        disposer.registerListener(control.indeterminateCycleDurationProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });
        disposer.registerListener(control.indeterminateBandRatioProperty(), () -> {
            if (indeterminateMode) {
                applyFills();
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
        int n = RXMath.clamp(getSkinnable().getSegmentCount(),
                RXSegmentedProgressBar.MIN_SEGMENT_COUNT, RXSegmentedProgressBar.MAX_SEGMENT_COUNT);

        trackRegions.clear();
        fillRegions.clear();
        fillClips.clear();
        List<Region> children = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            Region track = new Region();
            track.getStyleClass().setAll("track");
            track.setManaged(false);
            track.setMouseTransparent(true);

            Region fill = new Region();
            fill.getStyleClass().setAll("segment-fill");
            fill.setManaged(false);
            fill.setMouseTransparent(true);
            fill.setVisible(false);

            Rectangle clip = new Rectangle();
            fill.setClip(clip);

            boolean first = i == 0;
            boolean last = i == n - 1;
            track.pseudoClassStateChanged(FIRST, first);
            track.pseudoClassStateChanged(LAST, last);
            fill.pseudoClassStateChanged(FIRST, first);
            fill.pseudoClassStateChanged(LAST, last);

            trackRegions.add(track);
            fillRegions.add(fill);
            fillClips.add(clip);
            children.add(track);
            children.add(fill);
        }
        getChildren().setAll(children);
        cachedContentWidth = 0.0;
        cachedSegmentWidth = 0.0;
        cachedSegmentStartX = new double[n];
    }

    // ==================== Determinate progress ====================

    private void onProgressChanged(double newProgress) {
        if (newProgress < 0.0) {
            startIndeterminate();
            return;
        }
        double target = RXMath.clamp0To1(newProgress);
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
        // Reset clip geometry to the determinate form now — if the incoming
        // tween target equals the current displayedProgress, no listener fires
        // and last frame's band overlap would otherwise linger (§1.8).
        applyFills();
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
            indeterminatePhase.set(0.0);
            clearFills();
            return;
        }

        indeterminatePhase.set(0.0);
        applyFills();
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
     * mode) and resizes each clip accordingly. This is the single
     * write site for the visible fill, so all animations end here.
     */
    private void applyFills() {
        int n = fillClips.size();
        if (n == 0) {
            return;
        }
        if (cachedSegmentWidth <= 0.0 || cachedContentWidth <= 0.0) {
            clearFills();
            return;
        }
        if (indeterminateMode) {
            double bandRatio = RXMath.clamp0To1(getSkinnable().getIndeterminateBandRatio());
            if (bandRatio <= 0.0) {
                clearFills();
                return;
            }
            double bandWidth = cachedContentWidth * bandRatio;
            double bandRight = cachedContentX
                    + indeterminatePhase.get() * (cachedContentWidth + bandWidth);
            double bandLeft = bandRight - bandWidth;
            for (int i = 0; i < n; i++) {
                double segmentStart = cachedSegmentStartX[i];
                double segmentEnd = segmentStart + cachedSegmentWidth;
                double overlapStart = Math.max(segmentStart, bandLeft);
                double overlapEnd = Math.min(segmentEnd, bandRight);
                double overlap = Math.max(0.0, overlapEnd - overlapStart);
                setFillClip(i, overlap > 0.0 ? overlapStart - segmentStart : 0.0, overlap);
            }
            return;
        }

        double p = RXMath.clamp0To1(displayedProgress.get());
        double globalFill = p * n;
        for (int i = 0; i < n; i++) {
            double ratio = globalFill - i;
            if (ratio < 0.0) {
                ratio = 0.0;
            } else if (ratio > 1.0) {
                ratio = 1.0;
            }
            setFillClip(i, 0.0, cachedSegmentWidth * ratio);
        }
    }

    private void clearFills() {
        for (int i = 0; i < fillClips.size(); i++) {
            setFillClip(i, 0.0, 0.0);
        }
    }

    private void setFillClip(int index, double x, double width) {
        Rectangle clip = fillClips.get(index);
        clip.setX(x);
        clip.setWidth(width);
        // A zero-width clip can still leave an anti-aliased edge on some pipelines.
        fillRegions.get(index).setVisible(width > 0.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        int n = fillRegions.size();
        if (n == 0 || contentWidth <= 0.0 || contentHeight <= 0.0) {
            for (int i = 0; i < n; i++) {
                trackRegions.get(i).resizeRelocate(contentX, contentY, 0.0, 0.0);
                fillRegions.get(i).resizeRelocate(contentX, contentY, 0.0, 0.0);
                fillRegions.get(i).setVisible(false);
                fillClips.get(i).setX(0.0);
                fillClips.get(i).setY(0.0);
                fillClips.get(i).setWidth(0.0);
                fillClips.get(i).setHeight(0.0);
            }
            cachedContentX = contentX;
            cachedContentWidth = 0.0;
            cachedSegmentWidth = 0.0;
            cachedSegmentStartX = new double[n];
            return;
        }

        RXSegmentedProgressBar control = getSkinnable();
        double gap = RXMath.sanitizeNonNegative(control.getSegmentGap());
        double segHeight = RXMath.sanitizeNonNegative(control.getSegmentHeight());
        // Cap the rendered segment height to the available content height so a
        // too-tall segmentHeight on a clamped parent does not overflow.
        double renderedHeight = Math.min(segHeight, contentHeight);

        double totalGap = gap * Math.max(0, n - 1);
        double segWidth = Math.max(0.0, (contentWidth - totalGap) / n);
        cachedContentX = contentX;
        cachedContentWidth = contentWidth;
        cachedSegmentWidth = segWidth;
        if (cachedSegmentStartX.length != n) {
            cachedSegmentStartX = new double[n];
        }

        double y = contentY + (contentHeight - renderedHeight) * HALF;
        for (int i = 0; i < n; i++) {
            double x = contentX + i * (segWidth + gap);
            cachedSegmentStartX[i] = x;

            trackRegions.get(i).resizeRelocate(x, y, segWidth, renderedHeight);
            fillRegions.get(i).resizeRelocate(x, y, segWidth, renderedHeight);

            Rectangle clip = fillClips.get(i);
            clip.setX(0.0);
            clip.setY(0.0);
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
        int n = RXMath.clamp(getSkinnable().getSegmentCount(),
                RXSegmentedProgressBar.MIN_SEGMENT_COUNT, RXSegmentedProgressBar.MAX_SEGMENT_COUNT);
        double gap = RXMath.sanitizeNonNegative(getSkinnable().getSegmentGap());
        return leftInset + n + gap * Math.max(0, n - 1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + RXMath.sanitizeNonNegative(getSkinnable().getSegmentHeight()) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + RXMath.sanitizeNonNegative(getSkinnable().getSegmentHeight()) + bottomInset;
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
        // retained by the fill regions otherwise.
        for (Region r : fillRegions) {
            r.setClip(null);
        }
        super.dispose();
    }

}
