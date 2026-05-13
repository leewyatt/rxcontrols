package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.Random;

/**
 * Multiple circles reveal transition. Random circles expand simultaneously
 * to reveal the next page through their union shape.
 */
public class AnimCircles extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final int circleCount;
    private double maxRadius;
    private final Circle[] circles;
    private final DoubleProperty radiusProperty = new SimpleDoubleProperty();
    private final ObjectBinding<Node> clipBinding;
    private final Random random = new Random();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a circles reveal with 20 circles and max radius 200.
     */
    public AnimCircles() {
        this(20, 200);
    }

    /**
     * Creates a circles reveal with the given parameters.
     *
     * @param circleCount the number of circles (clamped to 2–100;
     *                    higher values degrade performance due to Shape.union operations)
     * @param maxRadius   the maximum radius of each circle
     */
    public AnimCircles(int circleCount, double maxRadius) {
        this.circleCount = Math.max(2, Math.min(100, circleCount));
        this.maxRadius = maxRadius;
        this.circles = new Circle[this.circleCount];

        for (int i = 0; i < this.circleCount; i++) {
            circles[i] = new Circle();
            circles[i].radiusProperty().bind(radiusProperty);
        }

        clipBinding = Bindings.createObjectBinding(() -> {
            Shape shape = circles[0];
            for (int i = 1; i < this.circleCount; i++) {
                shape = Shape.union(shape, circles[i]);
            }
            return shape;
        }, radiusProperty);
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

    /**
     * Returns the number of circles used in the reveal.
     *
     * @return the circle count
     */
    public int getCircleCount() {
        return circleCount;
    }

    /**
     * Returns the maximum radius of each circle.
     *
     * @return the maximum radius
     */
    public double getMaxRadius() {
        return maxRadius;
    }

    /**
     * Sets the maximum radius of each circle.
     *
     * @param maxRadius the maximum radius
     */
    public void setMaxRadius(double maxRadius) {
        this.maxRadius = maxRadius;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        // Generate random ratios (not absolute pixels) so positions scale with resize
        double[] ratioX = new double[circleCount];
        double[] ratioY = new double[circleCount];
        for (int i = 0; i < circleCount; i++) {
            ratioX[i] = random.nextDouble();
            ratioY[i] = random.nextDouble();
        }

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        Node clippedNode = forward ? nextPage : currentPage;

        // Initialize circles to progress=0 state before binding
        updateCircles(0, contentPane.getWidth(), contentPane.getHeight(), forward, ratioX, ratioY);

        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.clipProperty().bind(clipBinding);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.clipProperty().unbind();
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.clipProperty().unbind();
                nextPage.setClip(null);
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

        progressListener = (obs, oldVal, newVal) -> {
            updateCircles(newVal.doubleValue(), contentPane.getWidth(), contentPane.getHeight(), forward, ratioX, ratioY);
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
            child.clipProperty().unbind();
            child.setClip(null);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Circle circle : circles) {
            circle.radiusProperty().unbind();
        }
        if (clipBinding != null) {
            clipBinding.dispose();
        }
        super.dispose();
    }

    private void updateCircles(double p, double w, double h, boolean forward, double[] ratioX, double[] ratioY) {
        for (int i = 0; i < circleCount; i++) {
            circles[i].setCenterX(ratioX[i] * w);
            circles[i].setCenterY(ratioY[i] * h);
        }

        double mr = maxRadius <= 0 ? Math.max(w, h) / 3 : maxRadius;
        if (forward) {
            radiusProperty.set(mr * p);
        } else {
            radiusProperty.set(mr * (1 - p));
        }
    }
}
