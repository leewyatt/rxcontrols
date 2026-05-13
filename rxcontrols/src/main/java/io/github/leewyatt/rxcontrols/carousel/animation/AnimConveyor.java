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
 * Conveyor belt transition. The current page shrinks and slides to one
 * side while the next page enters from the opposite side, grows to full
 * size, and takes center stage — like items on a conveyor belt passing
 * through a viewing window.
 *
 * <p>{@link Orientation#HORIZONTAL} slides pages left/right.
 * {@link Orientation#VERTICAL} slides pages up/down.</p>
 *
 * <p>FORWARD: current page exits left (or up), next enters from right
 * (or below). BACKWARD reverses both directions.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame.</p>
 */
public class AnimConveyor extends CarouselAnimationBase {

    private static final double MIN_SCALE = 0.7;

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a horizontal conveyor animation.
     */
    public AnimConveyor() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a conveyor animation with the given orientation.
     *
     * @param orientation the slide direction
     */
    public AnimConveyor(Orientation orientation) {
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
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateConveyor(newVal.doubleValue(), w, h,
                    currentPage, nextPage, forward, horizontal);
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

    private void updateConveyor(double p, double w, double h,
                                 Node currentPage, Node nextPage,
                                 boolean forward, boolean horizontal) {

        double exitSign = forward ? -1 : 1;
        double size = horizontal ? w : h;
        double travel = size * 0.8;

        // Current page: scale 1→MIN_SCALE, slide out
        if (currentPage != null) {
            double currScale = 1.0 - (1.0 - MIN_SCALE) * p;
            currentPage.setScaleX(currScale);
            currentPage.setScaleY(currScale);

            double currOffset = exitSign * travel * p;
            if (horizontal) {
                currentPage.setTranslateX(currOffset);
            } else {
                currentPage.setTranslateY(currOffset);
            }

            currentPage.setViewOrder(p < 0.5 ? -1 : 0);
        }

        // Next page: scale MIN_SCALE→1, slide in from opposite side
        if (nextPage != null) {
            double nextScale = MIN_SCALE + (1.0 - MIN_SCALE) * p;
            nextPage.setScaleX(nextScale);
            nextPage.setScaleY(nextScale);

            double nextOffset = -exitSign * travel * (1.0 - p);
            if (horizontal) {
                nextPage.setTranslateX(nextOffset);
            } else {
                nextPage.setTranslateY(nextOffset);
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