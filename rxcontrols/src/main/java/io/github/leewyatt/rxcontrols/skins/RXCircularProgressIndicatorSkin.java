package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXCircularProgressIndicator}. Renders a track ring,
 * a progress arc, and a centre slot that hosts either {@link
 * RXCircularProgressIndicator#getGraphic()} or a default percentage label.
 *
 * <p>The indeterminate animation is a Material-style sweep
 * ({@code progressArc.length} animated between a short and long sweep) combined
 * with a continuous rotation. It auto-pauses whenever the host window or any
 * ancestor of the control is hidden, via {@link TreeShowingProperty}.
 *
 * <p>An internal {@code displayedProgress} lets the control's logical
 * {@code progress} jump while the visible arc tweens.
 */
public class RXCircularProgressIndicatorSkin extends SkinBase<RXCircularProgressIndicator> {

    // ==================== Layout Constants ====================

    private static final double DEFAULT_PREF_SIZE = 48.0;
    private static final double DEFAULT_MIN_SIZE = 24.0;
    private static final double FULL_CIRCLE = 360.0;
    private static final double HALF = 0.5;

    /** Sweep length of the indeterminate arc at the start/end of a cycle. */
    private static final double INDETERMINATE_MIN_LENGTH = 12.0;

    /** Sweep length of the indeterminate arc at the midpoint of a cycle. */
    private static final double INDETERMINATE_MAX_LENGTH = 270.0;

    /** Below this inner diameter the centre slot is hidden to avoid overflow. */
    private static final double CENTER_SLOT_MIN_INNER = 14.0;

    /**
     * Extra base-rotation revolutions per cycle, on top of the obligatory
     * {@code 360° − sweepDelta}. Lifting this beyond 1 narrows the head/tail
     * speed gap so the spinner reads as a steady rotation rather than two
     * alternating bursts.
     */
    private static final double EXTRA_BASE_REVOLUTIONS = 1.0;

    /**
     * Material "fast-out, slow-in" easing applied to {@code length} and
     * {@code animatedStartOffset}. Compared to {@code EASE_BOTH}, the peak is
     * passed through quickly instead of sustained for ~30% of the cycle.
     */
    private static final Interpolator MATERIAL_EASING =
            Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    // ==================== Nodes ====================

    private final Arc trackArc = new Arc();
    private final Arc progressArc = new Arc();
    private final StackPane centerSlot = new StackPane();
    private final Label progressLabel = new Label();
    private final Rotate spinRotate = new Rotate();

    // ==================== State ====================

    /** Visible [0,1] progress — diverges from {@code control.progress} during tween. */
    private final DoubleProperty displayedProgress =
            new SimpleDoubleProperty(this, "displayedProgress", 0.0);

    /**
     * Additive offset applied on top of {@code control.startAngle}. Used by the
     * Material indeterminate animation to advance the head endpoint during the
     * "contract" half-cycle while {@code control.startAngle} stays user-owned.
     */
    private final DoubleProperty animatedStartOffset =
            new SimpleDoubleProperty(this, "animatedStartOffset", 0.0);

    private final TreeShowingProperty treeShowing;
    private final List<Runnable> disposers = new ArrayList<>();

    private Timeline progressTween;
    private Timeline indeterminateTimeline;
    private boolean indeterminateMode;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXCircularProgressIndicatorSkin(RXCircularProgressIndicator control) {
        super(control);

        initNodes(control);
        treeShowing = new TreeShowingProperty(control);

        registerListeners(control);
        applyStrokeStyles();
        applyCenterContent();

        double initial = control.getProgress();
        if (initial >= 0.0) {
            indeterminateMode = false;
            displayedProgress.set(clamp(initial));
            applyDisplayedLength();
        } else {
            displayedProgress.set(0.0);
            startIndeterminate();
        }
    }

    // ==================== Init ====================

    private void initNodes(RXCircularProgressIndicator control) {
        trackArc.getStyleClass().add("track-arc");
        trackArc.setManaged(false);
        trackArc.setType(ArcType.OPEN);
        trackArc.setStartAngle(0.0);
        trackArc.setLength(FULL_CIRCLE);
        trackArc.setFill(null);

        progressArc.getStyleClass().add("progress-arc");
        progressArc.setManaged(false);
        progressArc.setType(ArcType.OPEN);
        progressArc.setLength(0.0);
        progressArc.setFill(null);
        progressArc.startAngleProperty().bind(
                control.startAngleProperty().add(animatedStartOffset));
        progressArc.getTransforms().add(spinRotate);

        progressLabel.getStyleClass().add("progress-label");
        progressLabel.setAlignment(Pos.CENTER);
        progressLabel.setMouseTransparent(true);
        progressLabel.graphicProperty().bind(control.graphicProperty());
        progressLabel.visibleProperty().bind(
                control.graphicProperty().isNotNull().or(progressLabel.textProperty().isNotEmpty()));
        progressLabel.managedProperty().bind(progressLabel.visibleProperty());

        centerSlot.getStyleClass().add("center-slot");
        centerSlot.setMouseTransparent(true);
        centerSlot.getChildren().setAll(progressLabel);

        getChildren().setAll(trackArc, progressArc, centerSlot);
    }

    private void registerListeners(RXCircularProgressIndicator control) {
        track(control.progressProperty(), (obs, oldV, newV) -> {
            onProgressChanged(newV.doubleValue());
            applyCenterContent();
        });
        track(control.clockwiseProperty(), (obs, oldV, newV) -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            } else {
                applyDisplayedLength();
            }
        });
        track(control.converterProperty(), (obs, oldV, newV) -> applyCenterContent());
        track(displayedProgress, (obs, oldV, newV) -> {
            if (!indeterminateMode) {
                applyDisplayedLength();
            }
        });
        track(control.indeterminateCycleDurationProperty(), (obs, oldV, newV) -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });
        track(control.trackStrokeProperty(), (obs, oldV, newV) -> applyStrokeStyles());
        track(control.progressStrokeProperty(), (obs, oldV, newV) -> applyStrokeStyles());
        track(control.trackStrokeWidthProperty(), (obs, oldV, newV) -> {
            applyStrokeStyles();
            control.requestLayout();
        });
        track(control.progressStrokeWidthProperty(), (obs, oldV, newV) -> {
            applyStrokeStyles();
            control.requestLayout();
        });
        track(control.strokeLineCapProperty(), (obs, oldV, newV) -> applyStrokeStyles());

        track(treeShowing, (obs, oldV, newV) -> {
            if (!indeterminateMode || indeterminateTimeline == null) {
                return;
            }
            if (newV) {
                indeterminateTimeline.play();
            } else {
                indeterminateTimeline.pause();
            }
        });
    }

    private <T> void track(ObservableValue<T> obs, ChangeListener<T> listener) {
        obs.addListener(listener);
        disposers.add(() -> obs.removeListener(listener));
    }

    // ==================== Style application ====================

    private void applyStrokeStyles() {
        RXCircularProgressIndicator control = getSkinnable();

        Paint track = control.getTrackStroke();
        trackArc.setStroke(track != null ? track : RXCircularProgressIndicator.DEFAULT_TRACK_STROKE);

        Paint progress = control.getProgressStroke();
        progressArc.setStroke(progress != null ? progress : RXCircularProgressIndicator.DEFAULT_PROGRESS_STROKE);

        trackArc.setStrokeWidth(sanitizeStrokeWidth(control.getTrackStrokeWidth()));
        progressArc.setStrokeWidth(sanitizeStrokeWidth(control.getProgressStrokeWidth()));

        StrokeLineCap cap = control.getStrokeLineCap();
        if (cap == null) {
            cap = RXCircularProgressIndicator.DEFAULT_STROKE_LINE_CAP;
        }
        trackArc.setStrokeLineCap(cap);
        progressArc.setStrokeLineCap(cap);
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

    private void applyDisplayedLength() {
        // JavaFX Arc: positive length sweeps counter-clockwise. Negate when clockwise is requested.
        double signedFullCircle = getSkinnable().isClockwise() ? -FULL_CIRCLE : FULL_CIRCLE;
        progressArc.setLength(signedFullCircle * displayedProgress.get());
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
        spinRotate.setAngle(0.0);
        animatedStartOffset.set(0.0);
        applyDisplayedLength();
    }

    /**
     * Builds a Material-style two-phase indeterminate cycle:
     * <ul>
     *   <li>Phase A (0 → T/2, "expand"): tail fixed in local frame, arc length
     *       grows {@code MIN → MAX} — the leading edge sweeps forward.</li>
     *   <li>Phase B (T/2 → T, "contract"): head fixed in local frame, arc length
     *       shrinks {@code MAX → MIN} while {@code animatedStartOffset} advances
     *       by {@code sweepDelta} — the trailing edge catches up.</li>
     * </ul>
     * The {@code spinRotate} transform provides the remaining base rotation —
     * {@code (1 + EXTRA_BASE_REVOLUTIONS) * 360° − sweepDelta} — so each
     * endpoint advances by {@code (1 + EXTRA_BASE_REVOLUTIONS) * 360°} per
     * cycle and never moves backwards. Length and offset use a Material
     * "fast-out, slow-in" curve so the peak length is passed through cleanly.
     */
    private void rebuildIndeterminateTimeline() {
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        if (!indeterminateMode) {
            return;
        }

        RXCircularProgressIndicator control = getSkinnable();
        Duration cycle = control.getIndeterminateCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Suppress indeterminate animation; ring stays static.
            return;
        }
        Duration halfCycle = cycle.divide(2.0);

        double sweepSign = control.isClockwise() ? -1.0 : 1.0;
        double sweepDelta = INDETERMINATE_MAX_LENGTH - INDETERMINATE_MIN_LENGTH;
        double signedMinLen = sweepSign * INDETERMINATE_MIN_LENGTH;
        double signedMaxLen = sweepSign * INDETERMINATE_MAX_LENGTH;
        double startOffsetEnd = sweepSign * sweepDelta;
        // Each endpoint advances by (1 + EXTRA_BASE_REVOLUTIONS) * 360° per cycle.
        // Of that, sweepDelta is contributed by length / animatedStartOffset growth;
        // the rest is spinRotate. Keeping spinRotate ≡ sweepSign * sweepDelta (mod 360)
        // ensures the screen position is continuous across the cycle boundary.
        double baseRotation = (1.0 + EXTRA_BASE_REVOLUTIONS) * FULL_CIRCLE - sweepDelta;
        double rotateEnd = control.isClockwise() ? baseRotation : -baseRotation;

        animatedStartOffset.set(0.0);
        spinRotate.setAngle(0.0);

        indeterminateTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progressArc.lengthProperty(),
                                signedMinLen, MATERIAL_EASING),
                        new KeyValue(animatedStartOffset,
                                0.0, MATERIAL_EASING),
                        new KeyValue(spinRotate.angleProperty(),
                                0.0, Interpolator.LINEAR)),
                new KeyFrame(halfCycle,
                        new KeyValue(progressArc.lengthProperty(),
                                signedMaxLen, MATERIAL_EASING),
                        new KeyValue(animatedStartOffset,
                                0.0, MATERIAL_EASING),
                        new KeyValue(spinRotate.angleProperty(),
                                rotateEnd * HALF, Interpolator.LINEAR)),
                new KeyFrame(cycle,
                        new KeyValue(progressArc.lengthProperty(),
                                signedMinLen, MATERIAL_EASING),
                        new KeyValue(animatedStartOffset,
                                startOffsetEnd, MATERIAL_EASING),
                        new KeyValue(spinRotate.angleProperty(),
                                rotateEnd, Interpolator.LINEAR))
        );
        indeterminateTimeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            indeterminateTimeline.play();
        }
    }

    // ==================== Centre content ====================

    private void applyCenterContent() {
        progressLabel.setText(formatLabel(getSkinnable().getProgress()));
    }

    private String formatLabel(double progress) {
        StringConverter<Double> converter = getSkinnable().getConverter();
        if (converter == null) {
            converter = RXCircularProgressIndicator.DEFAULT_CONVERTER;
        }
        return converter.toString(progress);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        double size = Math.min(contentWidth, contentHeight);
        if (size <= 0.0) {
            return;
        }

        double offsetX = contentX + (contentWidth - size) * HALF;
        double offsetY = contentY + (contentHeight - size) * HALF;
        double strokeMax = Math.max(trackArc.getStrokeWidth(), progressArc.getStrokeWidth());
        double radius = Math.max(0.0, (size - strokeMax) * HALF);
        double centerX = offsetX + size * HALF;
        double centerY = offsetY + size * HALF;

        layoutArc(trackArc, centerX, centerY, radius);
        layoutArc(progressArc, centerX, centerY, radius);
        spinRotate.setPivotX(centerX);
        spinRotate.setPivotY(centerY);

        double innerDiameter = Math.max(0.0, (radius - strokeMax) * 2.0);
        if (innerDiameter < CENTER_SLOT_MIN_INNER) {
            centerSlot.setVisible(false);
            centerSlot.setManaged(false);
        } else {
            centerSlot.setVisible(true);
            centerSlot.setManaged(true);
            centerSlot.resizeRelocate(
                    centerX - innerDiameter * HALF,
                    centerY - innerDiameter * HALF,
                    innerDiameter,
                    innerDiameter);
        }
    }

    private void layoutArc(Arc arc, double centerX, double centerY, double radius) {
        arc.setCenterX(centerX);
        arc.setCenterY(centerY);
        arc.setRadiusX(radius);
        arc.setRadiusY(radius);
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
        stopProgressTween();
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        for (int i = disposers.size() - 1; i >= 0; i--) {
            disposers.get(i).run();
        }
        disposers.clear();
        progressArc.startAngleProperty().unbind();
        progressArc.getTransforms().remove(spinRotate);
        progressLabel.graphicProperty().unbind();
        progressLabel.setGraphic(null);
        treeShowing.dispose();
        super.dispose();
    }

    // ==================== Helpers ====================

    private static double clamp(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    private static double sanitizeStrokeWidth(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return v;
    }
}
