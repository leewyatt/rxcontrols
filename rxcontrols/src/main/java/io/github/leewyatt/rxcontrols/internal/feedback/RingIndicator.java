package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * The built-in pending indicator of a feedback button: a ring. A progress
 * between 0 and 1 is drawn as an arc that grows clockwise from twelve
 * o'clock; an indeterminate progress as an arc that stretches and shrinks
 * while it travels round the ring. The arc and the track below it share one
 * paint, the track dimmed.
 *
 * <p>The ring is sized from CSS through its {@code indicator} style class and
 * keeps its preferred size. It animates only while it can be seen, that is
 * while it and all its ancestors are visible in a showing window; hidden, it
 * rests on the pose a newly shown ring starts from.</p>
 */
public final class RingIndicator extends Region {

    private static final String STYLE_CLASS = "indicator";
    private static final double FULL_CIRCLE = 360.0;
    private static final double TWELVE_O_CLOCK = 90.0;
    private static final double TRACK_OPACITY = 0.28;
    // The stroke scales with the ring, but stays visible on a tiny one.
    private static final double STROKE_RATIO = 0.14;
    private static final double MIN_STROKE_WIDTH = 1.5;

    /** Length of the travelling arc at the start and the end of a cycle. */
    private static final double SWEEP_MIN_LENGTH = 12.0;
    /** Length of the travelling arc at the middle of a cycle. */
    private static final double SWEEP_MAX_LENGTH = 270.0;
    private static final Duration SWEEP_CYCLE = Duration.millis(1500.0);
    private static final Interpolator SWEEP_EASING = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);
    private static final Duration TWEEN_DURATION = Duration.millis(200.0);

    private final Arc track = new Arc();
    private final Arc arc = new Arc();
    private final Rotate spin = new Rotate();
    // Added to the start angle of the arc while it travels.
    private final DoubleProperty sweepOffset = new SimpleDoubleProperty();
    private final RXTreeShowingProperty showing = new RXTreeShowingProperty(this);
    private final InvalidationListener showingListener = observable -> refresh(false);

    private Timeline sweep;
    private Timeline tween;
    // The tween is shrinking the arc to the length the sweep starts from.
    private boolean shrinking;
    private double progress = ProgressIndicator.INDETERMINATE_PROGRESS;

    /**
     * Creates an indeterminate ring.
     */
    public RingIndicator() {
        getStyleClass().add(STYLE_CLASS);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMouseTransparent(true);
        // The ring turns clockwise in a right-to-left button too.
        setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        for (Arc each : new Arc[] {track, arc}) {
            each.setManaged(false);
            each.setFill(null);
            each.setType(ArcType.OPEN);
            each.setStrokeLineCap(StrokeLineCap.ROUND);
            each.strokeProperty().bind(paint);
        }
        track.setOpacity(TRACK_OPACITY);
        track.setLength(FULL_CIRCLE);
        arc.startAngleProperty().bind(sweepOffset.add(TWELVE_O_CLOCK));
        arc.getTransforms().add(spin);
        getChildren().addAll(track, arc);

        showing.addListener(showingListener);
        refresh(false);
    }

    // ==================== Paint ====================

    private final ObjectProperty<Paint> paint = new SimpleObjectProperty<>(this, "paint");

    /**
     * The paint of the arc and, dimmed, of the track. {@code null} draws
     * nothing.
     *
     * @return the paint property
     */
    public ObjectProperty<Paint> paintProperty() {
        return paint;
    }

    /**
     * Returns the paint of the ring.
     *
     * @return the paint, may be {@code null}
     */
    public Paint getPaint() {
        return paint.get();
    }

    /**
     * Sets the paint of the ring.
     *
     * @param value the paint, or {@code null} to draw nothing
     */
    public void setPaint(Paint value) {
        paint.set(value);
    }

    // ==================== Progress ====================

    /**
     * Sets the progress to draw. A negative value or NaN is indeterminate; a
     * value above 1 is drawn as 1. While the ring can be seen, the arc moves
     * to a new length in a short tween that restarts from where the arc is
     * on every call; a ring that cannot be seen takes the new length at once.
     *
     * @param value the progress
     */
    public void setProgress(double value) {
        boolean wasIndeterminate = isIndeterminate();
        progress = value;
        refresh(wasIndeterminate != isIndeterminate());
    }

    private boolean isIndeterminate() {
        return Double.isNaN(progress) || progress < 0.0;
    }

    private void refresh(boolean modeChanged) {
        if (isIndeterminate()) {
            if (!showing.get()) {
                stopTween();
                stopSweep();
                arc.setLength(-SWEEP_MIN_LENGTH);
                sweepOffset.set(0.0);
                spin.setAngle(0.0);
                return;
            }
            if (sweep != null || shrinking) {
                return;
            }
            stopTween();
            if (modeChanged) {
                // The arc first shrinks to the length the sweep starts from.
                shrinking = true;
                tweenLengthTo(-SWEEP_MIN_LENGTH, () -> {
                    shrinking = false;
                    startSweep();
                });
            } else {
                startSweep();
            }
            return;
        }
        stopTween();
        stopSweep();
        sweepOffset.set(0.0);
        spin.setAngle(0.0);
        double length = -FULL_CIRCLE * RXMath.clamp0To1(progress);
        if (showing.get()) {
            tweenLengthTo(length, null);
        } else {
            arc.setLength(length);
        }
    }

    // ==================== Animations ====================

    private void tweenLengthTo(double length, Runnable whenDone) {
        tween = new Timeline(new KeyFrame(TWEEN_DURATION,
                new KeyValue(arc.lengthProperty(), length, Interpolator.EASE_OUT)));
        tween.setOnFinished(event -> {
            tween = null;
            if (whenDone != null) {
                whenDone.run();
            }
        });
        tween.play();
    }

    private void stopTween() {
        shrinking = false;
        if (tween != null) {
            tween.stop();
            tween = null;
        }
    }

    // One cycle has two halves. In the first the tail of the arc stays where
    // it is and the arc stretches from the minimum to the maximum length; in
    // the second the head stays and the tail catches up. The rotation adds
    // the rest of two full turns, so each end of the arc advances by two
    // turns per cycle, never moves backwards, and the cycle ends on the pose
    // it started from.
    private void startSweep() {
        double stretch = SWEEP_MAX_LENGTH - SWEEP_MIN_LENGTH;
        double rotation = 2.0 * FULL_CIRCLE - stretch;
        sweepOffset.set(0.0);
        spin.setAngle(0.0);
        sweep = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(arc.lengthProperty(), -SWEEP_MIN_LENGTH, SWEEP_EASING),
                        new KeyValue(sweepOffset, 0.0, SWEEP_EASING),
                        new KeyValue(spin.angleProperty(), 0.0, Interpolator.LINEAR)),
                new KeyFrame(SWEEP_CYCLE.divide(2.0),
                        new KeyValue(arc.lengthProperty(), -SWEEP_MAX_LENGTH, SWEEP_EASING),
                        new KeyValue(sweepOffset, 0.0, SWEEP_EASING),
                        new KeyValue(spin.angleProperty(), rotation / 2.0, Interpolator.LINEAR)),
                new KeyFrame(SWEEP_CYCLE,
                        new KeyValue(arc.lengthProperty(), -SWEEP_MIN_LENGTH, SWEEP_EASING),
                        new KeyValue(sweepOffset, -stretch, SWEEP_EASING),
                        new KeyValue(spin.angleProperty(), rotation, Interpolator.LINEAR)));
        sweep.setCycleCount(Animation.INDEFINITE);
        sweep.play();
    }

    private void stopSweep() {
        if (sweep != null) {
            sweep.stop();
            sweep = null;
        }
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren() {
        double size = Math.min(getWidth(), getHeight());
        double strokeWidth = size > 0.0 ? Math.max(MIN_STROKE_WIDTH, size * STROKE_RATIO) : 0.0;
        double radius = Math.max(0.0, (size - strokeWidth) / 2.0);
        double centerX = getWidth() / 2.0;
        double centerY = getHeight() / 2.0;
        for (Arc each : new Arc[] {track, arc}) {
            each.setCenterX(centerX);
            each.setCenterY(centerY);
            each.setRadiusX(radius);
            each.setRadiusY(radius);
            each.setStrokeWidth(strokeWidth);
        }
        spin.setPivotX(centerX);
        spin.setPivotY(centerY);
    }

    // ==================== Dispose ====================

    /**
     * Stops the animations and stops watching whether the ring can be seen.
     * The ring does not animate again afterwards.
     */
    public void dispose() {
        showing.removeListener(showingListener);
        showing.dispose();
        stopTween();
        stopSweep();
    }
}
