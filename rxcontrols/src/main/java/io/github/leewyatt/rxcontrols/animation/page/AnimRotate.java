package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Rotation transition. The current or next page rotates around the Z axis
 * from a bottom-corner pivot, like a page being turned away.
 *
 * <p>FORWARD: current page rotates out (0 → 90°).
 * BACKWARD: next page rotates in (90° → 0).</p>
 */
public class AnimRotate extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_IN;
    private final Rotate rotate = new Rotate();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a rotate animation with pivot at the bottom-left corner.
     */
    public AnimRotate() {
        rotate.setPivotX(0);
        rotate.setAxis(Rotate.Z_AXIS);
    }

    /**
     * Returns the interpolator used for the animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the animation.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // FORWARD: rotate current page away, BACKWARD: rotate next page in
        Node rotatedNode = forward ? currentPage : nextPage;
        if (rotatedNode != null) {
            rotatedNode.toFront();
            rotatedNode.getTransforms().remove(rotate);
            rotatedNode.getTransforms().add(rotate);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.getTransforms().remove(rotate);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.getTransforms().remove(rotate);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Remove old listener
        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        // Create new listener that recomputes pivot and angle from current dimensions each frame
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double h = contentPane.getHeight();

            rotate.setPivotY(h);
            rotate.setAngle(forward ? 90 * p : 90 * (1 - p));
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.getTransforms().remove(rotate);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        super.dispose();
    }
}
