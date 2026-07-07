package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
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
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import javafx.util.Callback;

/**
 * Default skin for {@link RXCircularProgressIndicator}. Renders a track ring,
 * a progress arc, and a centre label that shows both {@link
 * RXCircularProgressIndicator#getGraphic()} and the converted progress text;
 * their relative layout is controlled via {@code -fx-content-display}.
 *
 * <p>The indeterminate animation is a Material-style sweep
 * ({@code progressArc.length} animated between a short and long sweep) combined
 * with a continuous rotation. It auto-pauses whenever the host window or any
 * ancestor of the control is hidden, via {@link RXTreeShowingProperty}.
 *
 * <p>An internal {@code displayedProgress} lets the control's logical
 * {@code progress} jump while the visible arc tweens.
 */
public class RXCircularProgressIndicatorSkin extends RXSkinBase<RXCircularProgressIndicator> {

    // ==================== Layout Constants ====================

    private static final double DEFAULT_PREF_SIZE = 48.0;
    private static final double DEFAULT_MIN_SIZE = 24.0;
    private static final double FULL_CIRCLE = 360.0;
    private static final double HALF = 0.5;

    /** Sweep length of the indeterminate arc at the start/end of a cycle. */
    private static final double INDETERMINATE_MIN_LENGTH = 12.0;

    /** Sweep length of the indeterminate arc at the midpoint of a cycle. */
    private static final double INDETERMINATE_MAX_LENGTH = 270.0;

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

    private final ReadOnlyBooleanProperty treeShowing;

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
        treeShowing = controlTreeShowingProperty();

        registerListeners(control);
        applyStrokeStyles();
        applyCenterContent();

        double initial = control.getProgress();
        if (initial >= 0.0) {
            indeterminateMode = false;
            displayedProgress.set(RXMath.clamp0To1(initial));
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
        disposer.registerBinding(progressArc.startAngleProperty(),
                control.startAngleProperty().add(animatedStartOffset));
        progressArc.getTransforms().add(spinRotate);
        disposer.registerDisposeTask(() -> progressArc.getTransforms().remove(spinRotate));

        progressLabel.getStyleClass().add("progress-label");
        progressLabel.setAlignment(Pos.CENTER);
        progressLabel.setMouseTransparent(true);
        // Clear the user-supplied graphic on dispose; the unbind must happen
        // before setGraphic(null) or it would throw because the property is bound.
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

        getChildren().setAll(trackArc, progressArc, progressLabel);
    }

    private void registerListeners(RXCircularProgressIndicator control) {
        disposer.registerListener(control.progressProperty(), () -> {
            onProgressChanged(control.getProgress());
            applyCenterContent();
        });
        disposer.registerListener(control.clockwiseProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            } else {
                applyDisplayedLength();
            }
        });
        disposer.registerListener(control.textFactoryProperty(), this::applyCenterContent);
        disposer.registerListener(displayedProgress, () -> {
            if (!indeterminateMode) {
                applyDisplayedLength();
            }
        });
        disposer.registerListener(control.indeterminateCycleDurationProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });
        disposer.registerListener(control.trackStrokeProperty(), this::applyStrokeStyles);
        disposer.registerListener(control.progressStrokeProperty(), this::applyStrokeStyles);
        disposer.registerListener(control.trackStrokeWidthProperty(), () -> {
            applyStrokeStyles();
            control.requestLayout();
        });
        disposer.registerListener(control.progressStrokeWidthProperty(), () -> {
            applyStrokeStyles();
            control.requestLayout();
        });
        disposer.registerListener(control.strokeLineCapProperty(), this::applyStrokeStyles);

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

    // ==================== Style application ====================

    private void applyStrokeStyles() {
        RXCircularProgressIndicator control = getSkinnable();

        Paint track = control.getTrackStroke();
        trackArc.setStroke(track != null ? track : RXCircularProgressIndicator.DEFAULT_TRACK_STROKE);

        Paint progress = control.getProgressStroke();
        progressArc.setStroke(progress != null ? progress : RXCircularProgressIndicator.DEFAULT_PROGRESS_STROKE);

        trackArc.setStrokeWidth(RXMath.sanitizeNonNegative(control.getTrackStrokeWidth()));
        progressArc.setStrokeWidth(RXMath.sanitizeNonNegative(control.getProgressStrokeWidth()));

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
        double target = RXMath.clamp0To1(newProgress);
        if (indeterminateMode) {
            stopIndeterminate();
        }
        stopProgressTween();

        Duration tweenDuration = getSkinnable().getProgressTransitionDuration();
        if (tweenDuration == null || tweenDuration.isUnknown() || tweenDuration.isIndefinite()
                || tweenDuration.lessThanOrEqualTo(Duration.ZERO)) {
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
        double sweepSign = control.isClockwise() ? -1.0 : 1.0;
        double signedMinLen = sweepSign * INDETERMINATE_MIN_LENGTH;

        Duration cycle = control.getIndeterminateCycleDuration();
        if (cycle == null || cycle.isUnknown() || cycle.isIndefinite()
                || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Suppress indeterminate animation, snap to the cycle's t=0 pose so
            // the ring shows a deterministic static frame instead of whatever
            // arc length / rotation was lingering from a previous animation.
            progressArc.setLength(signedMinLen);
            animatedStartOffset.set(0.0);
            spinRotate.setAngle(0.0);
            return;
        }
        Duration halfCycle = cycle.divide(2.0);

        double sweepDelta = INDETERMINATE_MAX_LENGTH - INDETERMINATE_MIN_LENGTH;
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
        Callback<Double, String> textFactory = getSkinnable().getTextFactory();
        if (textFactory == null) {
            textFactory = RXCircularProgressIndicator.DEFAULT_TEXT_FACTORY;
        }
        return textFactory.call(progress);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        double size = Math.min(contentWidth, contentHeight);
        if (size <= 0.0) {
            // JavaFX does not auto-clip children to parent bounds; if we just return,
            // arcs / label keep their previous geometry and can render outside this
            // now-zero-sized control. Collapse them to a deterministic zero pose.
            layoutArc(trackArc, contentX, contentY, 0.0);
            layoutArc(progressArc, contentX, contentY, 0.0);
            spinRotate.setPivotX(contentX);
            spinRotate.setPivotY(contentY);
            progressLabel.resizeRelocate(contentX, contentY, 0.0, 0.0);
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
        double labelWidth = Math.min(progressLabel.prefWidth(innerDiameter), innerDiameter);
        double labelHeight = Math.min(progressLabel.prefHeight(labelWidth), innerDiameter);
        progressLabel.resizeRelocate(
                centerX - labelWidth * HALF,
                centerY - labelHeight * HALF,
                labelWidth,
                labelHeight);
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
    protected void disposeSkin() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners, bindings, transform and
        // treeShowing teardown are handled by the base disposer.
        stopProgressTween();
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
    }

}
