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
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Comb transition. The next page is revealed by interlocking strips
 * that slide in from opposite sides, like the teeth of two combs
 * meshing together.
 *
 * <p>{@link Orientation#HORIZONTAL} produces horizontal strips that
 * slide in from the left and right edges. {@link Orientation#VERTICAL}
 * produces vertical strips that slide in from the top and bottom.</p>
 *
 * <p>FORWARD: odd strips enter from the start side (left / top), even
 * strips from the end side (right / bottom). BACKWARD reverses the
 * assignment.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame to compute strip positions.</p>
 */
public class AnimComb extends CarouselAnimationBase {

    private final Orientation orientation;
    private final int stripCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a horizontal comb animation with 10 strips.
     */
    public AnimComb() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a comb animation with the given orientation and 10 strips.
     *
     * @param orientation the strip and slide direction
     */
    public AnimComb(Orientation orientation) {
        this(orientation, 10);
    }

    /**
     * Creates a comb animation with the given orientation and strip count.
     *
     * @param orientation the strip and slide direction
     * @param stripCount  the number of strips (at least 2, recommended 6 to 16)
     */
    public AnimComb(Orientation orientation, int stripCount) {
        this.orientation = orientation;
        this.stripCount = Math.max(2, stripCount);
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

        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toBack();
        }
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toFront();
            nextPage.setClip(new Rectangle(0, 0, 0, 0));
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
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

        final boolean isForward = forward;
        final boolean horizontal = orientation == Orientation.HORIZONTAL;

        progressListener = (obs, oldVal, newVal) -> {
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateComb(newVal.doubleValue(), w, h, nextPage,
                    horizontal, isForward);
        };
        progress.addListener(progressListener);

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

    private void updateComb(double p, double w, double h,
                             Node nextPage, boolean horizontal,
                             boolean forward) {
        if (nextPage == null) {
            return;
        }

        if (p >= 0.99) {
            nextPage.setClip(null);
            return;
        }

        if (p <= 0.01) {
            nextPage.setClip(new Rectangle(0, 0, 0, 0));
            return;
        }

        Shape clip = null;

        for (int i = 0; i < stripCount; i++) {
            boolean odd = i % 2 != 0;
            boolean fromStart = forward ? odd : !odd;

            if (horizontal) {
                double stripH = h / stripCount;
                double y = i * stripH;
                double travel = w * (1.0 - p);
                double x = fromStart ? -travel : travel;
                Rectangle rect = new Rectangle(x, y, w, stripH);
                clip = (clip == null) ? rect : Shape.union(clip, rect);
            } else {
                double stripW = w / stripCount;
                double x = i * stripW;
                double travel = h * (1.0 - p);
                double y = fromStart ? -travel : travel;
                Rectangle rect = new Rectangle(x, y, stripW, h);
                clip = (clip == null) ? rect : Shape.union(clip, rect);
            }
        }

        if (clip != null) {
            nextPage.setClip(clip);
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
            child.setClip(null);
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