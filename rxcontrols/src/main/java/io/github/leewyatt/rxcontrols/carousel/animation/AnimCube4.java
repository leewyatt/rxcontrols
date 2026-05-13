package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Four-face cube rotation transition. Four pages are conceptually arranged
 * on the four lateral faces of a cube. Each page turn rotates the cube 90
 * degrees, revealing the next face.
 *
 * <p>The 3D rotation is simulated with {@code scaleX} and {@code translateX}
 * (or {@code scaleY}/{@code translateY} for vertical orientation). Each face
 * narrows as it rotates away from the viewer and expands as it rotates
 * toward the viewer, following cosine/sine curves for physically plausible
 * widths. The two visible faces share a common hinge edge that sweeps
 * across the viewport during the rotation.</p>
 *
 * <p>{@link Orientation#HORIZONTAL} rotates around the vertical axis
 * (pages slide left/right). {@link Orientation#VERTICAL} rotates around
 * the horizontal axis (pages slide up/down).</p>
 *
 * <p>FORWARD: cube rotates left (or up), next page enters from the right
 * (or bottom). BACKWARD reverses the rotation direction.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame.</p>
 */
public class AnimCube4 extends CarouselAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a horizontal four-face cube animation.
     */
    public AnimCube4() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a four-face cube animation with the given orientation.
     *
     * @param orientation the rotation axis orientation
     */
    public AnimCube4(Orientation orientation) {
        this.orientation = orientation;
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
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;
        boolean horizontal = orientation == Orientation.HORIZONTAL;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                resetNode(currentPage);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                resetNode(nextPage);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double size = horizontal
                    ? animContentPane.getWidth()
                    : animContentPane.getHeight();
            updateCube(p, size, currentPage, nextPage, forward, horizontal);
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    /**
     * Updates both pages for the current progress value.
     *
     * <p>The rotation angle theta goes from 0 to PI/2. The current face
     * width follows cos(theta) and the next face width follows sin(theta).
     * A direction sign controls which side the faces anchor to, creating
     * a shared hinge edge that sweeps across the viewport.</p>
     *
     * <p>For FORWARD (sign = +1), the current page anchors its left edge
     * and the hinge sweeps from right to left. For BACKWARD (sign = -1),
     * the current page anchors its right edge and the hinge sweeps from
     * left to right.</p>
     */
    private void updateCube(double p, double size,
                             Node currentPage, Node nextPage,
                             boolean forward, boolean horizontal) {
        double theta = p * Math.PI / 2.0;
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double sign = forward ? 1.0 : -1.0;

        // Current face: width shrinks as cos(theta), anchored on exit side
        if (currentPage != null) {
            double curTranslate = sign * size * (cosT - 1.0) / 2.0;
            if (horizontal) {
                currentPage.setScaleX(cosT);
                currentPage.setTranslateX(curTranslate);
            } else {
                currentPage.setScaleY(cosT);
                currentPage.setTranslateY(curTranslate);
            }
            currentPage.setViewOrder(p < 0.5 ? -1 : 0);
        }

        // Next face: width grows as sin(theta), left edge meets current's right edge
        if (nextPage != null) {
            double nextTranslate = sign * (size * cosT - size / 2.0 + size * sinT / 2.0);
            if (horizontal) {
                nextPage.setScaleX(sinT);
                nextPage.setTranslateX(nextTranslate);
            } else {
                nextPage.setScaleY(sinT);
                nextPage.setTranslateY(nextTranslate);
            }
            nextPage.setViewOrder(p < 0.5 ? 0 : -1);
        }
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setScaleX(1);
            node.setScaleY(1);
            node.setTranslateX(0);
            node.setTranslateY(0);
            node.setViewOrder(0);
        }
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            resetNode(child);
        }
        animContentPane = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        animContentPane = null;
        super.dispose();
    }
}
