package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.util.Duration;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXWaveProgressIndicator}. Renders a circular water
 * container, two layered sine waves that scroll horizontally, an optional
 * outer ring, and a centre label that shows both
 * {@link RXWaveProgressIndicator#getGraphic()} and the converted progress
 * text (relative layout is controlled via {@code -fx-content-display}).
 *
 * <p>The horizontal scroll is implemented by animating {@code translateX} on
 * each wave {@link Path} from {@code 0} to {@code -waveLength} on
 * {@link Animation#INDEFINITE INDEFINITE} loop — the path content stays
 * static, so the cost per frame is a pure affine transform.
 *
 * <p>An internal {@code displayedProgress} lets the control's logical
 * {@code progress} jump while the visible water level tweens. The
 * indeterminate animation breathes {@code displayedProgress} between
 * {@code 0.35} and {@code 0.65} while the wave scroll keeps running.
 *
 * <p>All long-running timelines auto-pause whenever the host window or any
 * ancestor of the control is hidden, via {@link TreeShowingProperty}.
 */
public class RXWaveProgressIndicatorSkin extends RXSkinBase<RXWaveProgressIndicator> {

    // ==================== Layout Constants ====================

    private static final double DEFAULT_PREF_SIZE = 80.0;
    private static final double DEFAULT_MIN_SIZE = 32.0;
    private static final double HALF = 0.5;

    /** Lower water-level bound for the indeterminate breathing. */
    private static final double INDETERMINATE_LOW = 0.35;

    /** Upper water-level bound for the indeterminate breathing. */
    private static final double INDETERMINATE_HIGH = 0.65;

    /** Mid-range water level used when the indeterminate animation is suppressed. */
    private static final double INDETERMINATE_REST = 0.5;

    /** Phase offset applied to the back wave, expressed as a fraction of one wavelength. */
    private static final double BACK_WAVE_PHASE_OFFSET = 1.0 / 3.0;

    /** Cubic Bezier control-point x ratio (relative to half-wavelength) — approximates a sine half-cycle. */
    private static final double BEZIER_C1_RATIO = 0.36;

    /** Cubic Bezier control-point x ratio for the second control point. */
    private static final double BEZIER_C2_RATIO = 0.64;

    // ==================== Nodes ====================

    private final Circle container = new Circle();
    private final Circle borderRing = new Circle();
    private final Circle clipCircle = new Circle();
    private final Group waveLayer = new Group();
    private final Path frontWavePath = new Path();
    private final Path backWavePath = new Path();
    private final Label progressLabel = new Label();

    // ==================== State ====================

    /** Visible [0,1] progress — diverges from {@code control.progress} during tween / indeterminate. */
    private final DoubleProperty displayedProgress =
            new SimpleDoubleProperty(this, "displayedProgress", 0.0);

    private final TreeShowingProperty treeShowing;

    private Timeline progressTween;
    private Timeline frontWaveTimeline;
    private Timeline backWaveTimeline;
    private Timeline indeterminateTimeline;
    private boolean indeterminateMode;

    /** Cached geometry used by path rebuilds — kept in skin-local coordinates. */
    private double cachedCenterX;
    private double cachedCenterY;
    private double cachedWaterRadius;

    /**
     * Wave-scroll timeline parameters that were in effect when the current
     * timelines were built. Used to short-circuit layout-driven rebuilds when
     * nothing material has changed, so the scroll position is not reset to
     * {@code translateX = 0} on every size change.
     */
    private double timelineLambda = Double.NaN;
    private Duration timelineFrontCycle;
    private double timelineBackRatio = Double.NaN;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXWaveProgressIndicatorSkin(RXWaveProgressIndicator control) {
        super(control);

        initNodes(control);
        treeShowing = new TreeShowingProperty(control);
        disposer.registerDisposeTask(treeShowing::dispose);

        registerListeners(control);
        applyFills();
        applyBorder();
        applyCenterContent();

        double initial = control.getProgress();
        if (initial >= 0.0) {
            indeterminateMode = false;
            displayedProgress.set(clamp(initial));
        } else {
            displayedProgress.set(INDETERMINATE_REST);
            startIndeterminate();
        }
    }

    // ==================== Init ====================

    private void initNodes(RXWaveProgressIndicator control) {
        container.getStyleClass().add("wave-container");
        container.setManaged(false);
        container.setStroke(null);

        borderRing.getStyleClass().add("border-ring");
        borderRing.setManaged(false);
        borderRing.setFill(null);

        backWavePath.getStyleClass().add("back-wave");
        backWavePath.setStroke(null);
        backWavePath.setManaged(false);

        frontWavePath.getStyleClass().add("front-wave");
        frontWavePath.setStroke(null);
        frontWavePath.setManaged(false);

        waveLayer.getChildren().setAll(backWavePath, frontWavePath);
        waveLayer.setManaged(false);
        waveLayer.setMouseTransparent(true);
        // Clip is set on the Group: a Circle in skin-local coordinates that
        // matches the water container, so the paths never spill outside the
        // round container.
        waveLayer.setClip(clipCircle);
        disposer.registerDisposeTask(() -> waveLayer.setClip(null));

        progressLabel.getStyleClass().add("progress-label");
        progressLabel.setAlignment(Pos.CENTER);
        progressLabel.setMouseTransparent(true);
        // Clear the user-supplied graphic on dispose; unbind must run before
        // setGraphic(null), so both steps live in one task.
        disposer.registerDisposeTask(() -> {
            progressLabel.graphicProperty().unbind();
            progressLabel.setGraphic(null);
        });
        progressLabel.graphicProperty().bind(control.graphicProperty());
        disposer.registerBinding(progressLabel.visibleProperty(),
                control.graphicProperty().isNotNull()
                        .or(progressLabel.textProperty().isNotEmpty()));
        disposer.registerBinding(progressLabel.managedProperty(),
                progressLabel.visibleProperty());

        getChildren().setAll(container, waveLayer, borderRing, progressLabel);
    }

    private void registerListeners(RXWaveProgressIndicator control) {
        disposer.registerListener(control.progressProperty(), () -> {
            onProgressChanged(control.getProgress());
            applyCenterContent();
        });
        disposer.registerListener(control.textFactoryProperty(), this::applyCenterContent);

        disposer.registerListener(displayedProgress, this::rebuildWavePaths);

        disposer.registerListener(control.waveAmplitudeProperty(), this::rebuildWavePaths);
        disposer.registerListener(control.waveLengthProperty(), () -> {
            rebuildWavePaths();
            rebuildWaveTimelines();
        });
        disposer.registerListener(control.waveCycleDurationProperty(), this::rebuildWaveTimelines);
        disposer.registerListener(control.backWaveSpeedRatioProperty(), this::rebuildWaveTimelines);
        disposer.registerListener(control.backWaveAmplitudeRatioProperty(), this::rebuildWavePaths);

        disposer.registerListener(control.containerFillProperty(), this::applyFills);
        disposer.registerListener(control.frontWaveFillProperty(), this::applyFills);
        disposer.registerListener(control.backWaveFillProperty(), this::applyFills);

        disposer.registerListener(control.borderStrokeProperty(), this::applyBorder);
        disposer.registerListener(control.borderStrokeWidthProperty(), () -> {
            applyBorder();
            control.requestLayout();
        });
        disposer.registerListener(control.borderPaddingProperty(), control::requestLayout);

        disposer.registerListener(control.indeterminateCycleDurationProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });

        disposer.registerListener(treeShowing, () -> onTreeShowingChanged(treeShowing.get()));
    }

    // ==================== Style application ====================

    private void applyFills() {
        RXWaveProgressIndicator control = getSkinnable();

        Paint cf = control.getContainerFill();
        container.setFill(cf != null ? cf : RXWaveProgressIndicator.DEFAULT_CONTAINER_FILL);

        Paint ff = control.getFrontWaveFill();
        frontWavePath.setFill(ff != null ? ff : RXWaveProgressIndicator.DEFAULT_FRONT_WAVE_FILL);

        Paint bf = control.getBackWaveFill();
        backWavePath.setFill(bf != null ? bf : RXWaveProgressIndicator.DEFAULT_BACK_WAVE_FILL);
    }

    private void applyBorder() {
        RXWaveProgressIndicator control = getSkinnable();

        Paint bs = control.getBorderStroke();
        borderRing.setStroke(bs != null ? bs : RXWaveProgressIndicator.DEFAULT_BORDER_STROKE);
        borderRing.setStrokeWidth(sanitize(control.getBorderStrokeWidth()));
    }

    // ==================== Progress changes ====================

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

        Duration tweenDuration = getSkinnable().getProgressTransitionDuration();
        if (tweenDuration == null || tweenDuration.lessThanOrEqualTo(Duration.ZERO)) {
            displayedProgress.set(target);
            return;
        }
        progressTween = new Timeline(new KeyFrame(
                tweenDuration,
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
        // displayedProgress is handed over to the tween in onProgressChanged;
        // do not snap it back here, or the determinate transition starts from
        // a discontinuous frame.
    }

    /**
     * Builds the indeterminate breathing timeline. {@code displayedProgress}
     * oscillates between {@link #INDETERMINATE_LOW} and
     * {@link #INDETERMINATE_HIGH} with an {@link Interpolator#EASE_BOTH} curve.
     * Wave-scroll timelines keep running independently so the surface still
     * appears to flow.
     */
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
            // Suppress breathing and snap to a deterministic mid-range pose so
            // a stale frame from a previous animation cannot linger.
            displayedProgress.set(INDETERMINATE_REST);
            return;
        }
        Duration halfCycle = cycle.divide(2.0);

        indeterminateTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(displayedProgress, INDETERMINATE_LOW, Interpolator.EASE_BOTH)),
                new KeyFrame(halfCycle,
                        new KeyValue(displayedProgress, INDETERMINATE_HIGH, Interpolator.EASE_BOTH)),
                new KeyFrame(cycle,
                        new KeyValue(displayedProgress, INDETERMINATE_LOW, Interpolator.EASE_BOTH))
        );
        indeterminateTimeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            indeterminateTimeline.play();
        }
    }

    // ==================== Wave scroll timelines ====================

    /**
     * Rebuilds the front and back wave scroll timelines. Each timeline animates
     * the path's {@code translateX} from {@code 0} to {@code -waveLength} with
     * a {@link Interpolator#LINEAR LINEAR} curve so the loop boundary is
     * seamless — the path content covers an integer number of periods, so the
     * post-cycle frame is geometrically identical to {@code t = 0}.
     */
    private void rebuildWaveTimelines() {
        double lambda = resolveWaveLength();
        RXWaveProgressIndicator control = getSkinnable();
        Duration cycle = control.getWaveCycleDuration();
        double speedRatio = control.getBackWaveSpeedRatio();
        if (Double.isNaN(speedRatio) || speedRatio <= 0.0) {
            speedRatio = 1.0;
        }

        boolean disabled = lambda <= 0.0 || cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO);
        if (disabled) {
            stopAndClearWaveTimeline(true);
            stopAndClearWaveTimeline(false);
            frontWavePath.setTranslateX(0.0);
            backWavePath.setTranslateX(0.0);
            timelineLambda = Double.NaN;
            timelineFrontCycle = null;
            timelineBackRatio = Double.NaN;
            return;
        }

        // Avoid rebuilding when nothing material changed — a layout-driven call
        // with identical lambda / cycle / ratio would otherwise stop running
        // timelines and snap translateX back to 0, freezing the scroll visually.
        boolean lambdaChanged = lambda != timelineLambda;
        boolean cycleChanged = !durationEquals(cycle, timelineFrontCycle);
        boolean ratioChanged = speedRatio != timelineBackRatio;
        if (!lambdaChanged && !cycleChanged && !ratioChanged
                && frontWaveTimeline != null && backWaveTimeline != null) {
            return;
        }

        if (lambdaChanged || cycleChanged) {
            stopAndClearWaveTimeline(true);
            frontWaveTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(frontWavePath.translateXProperty(), 0.0, Interpolator.LINEAR)),
                    new KeyFrame(cycle,
                            new KeyValue(frontWavePath.translateXProperty(), -lambda, Interpolator.LINEAR))
            );
            frontWaveTimeline.setCycleCount(Animation.INDEFINITE);
            if (treeShowing.get()) {
                frontWaveTimeline.play();
            }
        }

        if (lambdaChanged || cycleChanged || ratioChanged) {
            stopAndClearWaveTimeline(false);
            Duration backCycle = cycle.multiply(speedRatio);
            backWaveTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(backWavePath.translateXProperty(), 0.0, Interpolator.LINEAR)),
                    new KeyFrame(backCycle,
                            new KeyValue(backWavePath.translateXProperty(), -lambda, Interpolator.LINEAR))
            );
            backWaveTimeline.setCycleCount(Animation.INDEFINITE);
            if (treeShowing.get()) {
                backWaveTimeline.play();
            }
        }

        timelineLambda = lambda;
        timelineFrontCycle = cycle;
        timelineBackRatio = speedRatio;
    }

    private static boolean durationEquals(Duration a, Duration b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.toMillis() == b.toMillis();
    }

    private void stopAndClearWaveTimeline(boolean front) {
        Timeline target = front ? frontWaveTimeline : backWaveTimeline;
        if (target != null) {
            target.stop();
        }
        if (front) {
            frontWaveTimeline = null;
        } else {
            backWaveTimeline = null;
        }
    }

    // ==================== tree-showing pause ====================

    private void onTreeShowingChanged(boolean showing) {
        pauseOrResume(frontWaveTimeline, showing);
        pauseOrResume(backWaveTimeline, showing);
        if (indeterminateMode) {
            pauseOrResume(indeterminateTimeline, showing);
        }
    }

    private static void pauseOrResume(Timeline timeline, boolean showing) {
        if (timeline == null) {
            return;
        }
        if (showing) {
            timeline.play();
        } else {
            timeline.pause();
        }
    }

    // ==================== Path geometry ====================

    private double resolveWaveLength() {
        double declared = getSkinnable().getWaveLength();
        if (Double.isNaN(declared) || declared <= 0.0) {
            // Fall back to the container diameter so a default control still
            // shows a complete sine period across its width.
            return Math.max(0.0, cachedWaterRadius * 2.0);
        }
        return declared;
    }

    private void rebuildWavePaths() {
        double radius = cachedWaterRadius;
        if (radius <= 0.0) {
            frontWavePath.getElements().clear();
            backWavePath.getElements().clear();
            return;
        }

        RXWaveProgressIndicator control = getSkinnable();
        double lambda = resolveWaveLength();
        if (lambda <= 0.0) {
            frontWavePath.getElements().clear();
            backWavePath.getElements().clear();
            return;
        }

        double amplitude = sanitize(control.getWaveAmplitude());
        double backRatio = sanitize(control.getBackWaveAmplitudeRatio());
        double backAmplitude = amplitude * backRatio;

        double cx = cachedCenterX;
        double cy = cachedCenterY;
        double topY = cy - radius;
        double bottomY = cy + radius;
        double level = clamp(displayedProgress.get());
        double baseline = bottomY - level * (radius * 2.0);

        double leftX = cx - radius - lambda;
        int periods = Math.max(3, (int) Math.ceil((radius * 2.0) / lambda) + 3);

        frontWavePath.getElements().setAll(
                buildWaveElements(leftX, baseline, amplitude, lambda, bottomY, topY, periods));
        backWavePath.getElements().setAll(
                buildWaveElements(leftX + lambda * BACK_WAVE_PHASE_OFFSET,
                        baseline, backAmplitude, lambda, bottomY, topY, periods));
    }

    /**
     * Builds a closed wave shape spanning {@code periods} full sine periods.
     * The top edge alternates {@code crest → trough} cubic Bezier segments
     * (one segment per half-period); the path then closes back to
     * {@code bottomY} so the region below the surface fills solidly.
     *
     * @param leftX     left edge of the path
     * @param baseline  y-coordinate at which the surface crosses (water level)
     * @param amplitude crest height (already sanitized to >= 0)
     * @param lambda    wavelength in pixels (already > 0)
     * @param bottomY   y-coordinate of the bottom edge (below the clip)
     * @param topY      y-coordinate of the top edge (used to clamp out-of-range baselines)
     * @param periods   number of full sine periods to draw
     * @return the path-element sequence
     */
    private static List<PathElement> buildWaveElements(double leftX, double baseline,
                                                      double amplitude, double lambda,
                                                      double bottomY, double topY, int periods) {
        // Keep the path bottom strictly below the clip so anti-aliasing along
        // the lower edge does not leave a single-pixel transparent seam.
        double sealedBottom = bottomY + 1.0;
        // Clamp baseline within [topY - amplitude - 1, bottomY + amplitude + 1]
        // so a sanitized-zero amplitude with an off-range baseline still gives
        // a sensible closed region.
        double clampedBaseline = baseline;
        if (clampedBaseline > sealedBottom) {
            clampedBaseline = sealedBottom;
        }
        if (clampedBaseline < topY - amplitude - 1.0) {
            clampedBaseline = topY - amplitude - 1.0;
        }

        List<PathElement> elements = new ArrayList<>(periods * 2 + 4);
        elements.add(new MoveTo(leftX, clampedBaseline));

        double half = lambda * HALF;
        double x = leftX;
        for (int i = 0, halfCycles = periods * 2; i < halfCycles; i++) {
            boolean crest = (i % 2 == 0);
            double crestY = crest ? clampedBaseline - amplitude : clampedBaseline + amplitude;
            double nextX = x + half;
            elements.add(new CubicCurveTo(
                    x + half * BEZIER_C1_RATIO, crestY,
                    x + half * BEZIER_C2_RATIO, crestY,
                    nextX, clampedBaseline));
            x = nextX;
        }
        elements.add(new LineTo(x, sealedBottom));
        elements.add(new LineTo(leftX, sealedBottom));
        elements.add(new ClosePath());
        return elements;
    }

    // ==================== Centre content ====================

    private void applyCenterContent() {
        progressLabel.setText(formatLabel(getSkinnable().getProgress()));
    }

    private String formatLabel(double progress) {
        Callback<Double, String> textFactory = getSkinnable().getTextFactory();
        if (textFactory == null) {
            textFactory = RXWaveProgressIndicator.DEFAULT_TEXT_FACTORY;
        }
        return textFactory.call(progress);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        double size = Math.min(contentWidth, contentHeight);
        if (size <= 0.0) {
            // JavaFX does not auto-clip children; collapse everything so a
            // previous frame's geometry cannot leak outside the now-zero
            // content area.
            container.setRadius(0.0);
            borderRing.setRadius(0.0);
            clipCircle.setRadius(0.0);
            cachedWaterRadius = 0.0;
            cachedCenterX = contentX;
            cachedCenterY = contentY;
            rebuildWavePaths();
            progressLabel.resizeRelocate(contentX, contentY, 0.0, 0.0);
            return;
        }

        RXWaveProgressIndicator control = getSkinnable();
        double border = sanitize(control.getBorderStrokeWidth());
        double padding = sanitize(control.getBorderPadding());

        double waterDiameter = Math.max(0.0, size - 2.0 * (border + padding));
        double offsetX = contentX + (contentWidth - size) * HALF;
        double offsetY = contentY + (contentHeight - size) * HALF;
        double centerX = offsetX + size * HALF;
        double centerY = offsetY + size * HALF;
        double waterRadius = waterDiameter * HALF;

        container.setCenterX(centerX);
        container.setCenterY(centerY);
        container.setRadius(waterRadius);

        // Stroke is rendered centred on the radius, so the visible outer edge
        // sits at waterRadius + padding + border, and the inner edge at
        // waterRadius + padding.
        borderRing.setCenterX(centerX);
        borderRing.setCenterY(centerY);
        borderRing.setRadius(waterRadius + padding + border * HALF);

        clipCircle.setCenterX(centerX);
        clipCircle.setCenterY(centerY);
        clipCircle.setRadius(waterRadius);

        cachedCenterX = centerX;
        cachedCenterY = centerY;
        cachedWaterRadius = waterRadius;

        rebuildWavePaths();
        rebuildWaveTimelines();
        layoutLabel(centerX, centerY, waterRadius);
    }

    private void layoutLabel(double centerX, double centerY, double waterRadius) {
        double innerDiameter = Math.max(0.0, waterRadius * 2.0);
        double labelWidth = Math.min(progressLabel.prefWidth(innerDiameter), innerDiameter);
        double labelHeight = Math.min(progressLabel.prefHeight(labelWidth), innerDiameter);
        progressLabel.resizeRelocate(
                centerX - labelWidth * HALF,
                centerY - labelHeight * HALF,
                labelWidth,
                labelHeight);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + DEFAULT_MIN_SIZE + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + DEFAULT_MIN_SIZE + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_SIZE + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_SIZE + bottomInset;
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

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current ones explicitly here. Listeners, bindings, clip and
        // treeShowing teardown are handled by the embedded SkinDisposer in
        // RXSkinBase.dispose().
        stopProgressTween();
        stopAndClearWaveTimeline(true);
        stopAndClearWaveTimeline(false);
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        super.dispose();
    }

    // ==================== Helpers ====================

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    private static double sanitize(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return v;
    }
}
