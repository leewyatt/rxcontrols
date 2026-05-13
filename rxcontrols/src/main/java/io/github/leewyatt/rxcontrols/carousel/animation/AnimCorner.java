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
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Corner reveal transition. Four rectangles slide in from the corners toward
 * the center, forming a union clip that reveals the next page. The next page
 * also scales down from a slightly enlarged state.
 */
public class AnimCorner extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Rectangle rectTL = new Rectangle();
    private final Rectangle rectTR = new Rectangle();
    private final Rectangle rectBL = new Rectangle();
    private final Rectangle rectBR = new Rectangle();
    private final ObjectBinding<Node> shapeBinding;
    private double scaleX;
    private double scaleY;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a corner reveal with default scale (1.5, 1.5).
     */
    public AnimCorner() {
        this(1.5, 1.5);
    }

    /**
     * Creates a corner reveal with the given scale factors.
     *
     * @param scaleX the initial X scale of the next page
     * @param scaleY the initial Y scale of the next page
     */
    public AnimCorner(double scaleX, double scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        shapeBinding = Bindings.createObjectBinding(
                () -> Shape.union(Shape.union(rectTL, rectBL), Shape.union(rectTR, rectBR)),
                rectTL.translateYProperty(), rectBL.translateXProperty(),
                rectTR.translateYProperty(), rectBR.translateXProperty());
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
     * Returns the initial X scale of the next page.
     *
     * @return the initial X scale
     */
    public double getScaleX() {
        return scaleX;
    }

    /**
     * Sets the initial X scale of the next page.
     *
     * @param scaleX the initial X scale
     */
    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }

    /**
     * Returns the initial Y scale of the next page.
     *
     * @return the initial Y scale
     */
    public double getScaleY() {
        return scaleY;
    }

    /**
     * Sets the initial Y scale of the next page.
     *
     * @param scaleY the initial Y scale
     */
    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // FORWARD: clip nextPage expanding from corners, nextPage on top
        // BACKWARD: clip currentPage shrinking toward corners, currentPage on top
        Node clippedNode = forward ? nextPage : currentPage;

        final double sx = scaleX;
        final double sy = scaleY;

        // Initialize clip rectangles to progress=0 state before binding
        updateCorners(0, contentPane.getWidth(), contentPane.getHeight(), forward, clippedNode, sx, sy);

        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.clipProperty().bind(shapeBinding);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.clipProperty().unbind();
                currentPage.setClip(null);
                currentPage.setScaleX(1.0);
                currentPage.setScaleY(1.0);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.clipProperty().unbind();
                nextPage.setClip(null);
                nextPage.setScaleX(1.0);
                nextPage.setScaleY(1.0);
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
            double p = newVal.doubleValue();
            updateCorners(p, contentPane.getWidth(), contentPane.getHeight(), forward, clippedNode, sx, sy);
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            if (progressListener != null) {
                progress.removeListener(progressListener);
            }
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
            child.setScaleX(1.0);
            child.setScaleY(1.0);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        if (shapeBinding != null) {
            shapeBinding.dispose();
        }
        super.dispose();
    }

    private void updateCorners(double p, double w, double h, boolean forward, Node clippedNode, double sx, double sy) {
        double halfW = w / 2;
        double halfH = h / 2;
        double ep = forward ? p : 1.0 - p;

        for (Rectangle r : new Rectangle[]{rectTL, rectTR, rectBL, rectBR}) {
            r.setWidth(halfW);
            r.setHeight(halfH);
        }

        rectBL.setTranslateY(halfH);
        rectBR.setTranslateX(halfW);

        rectTL.setTranslateY(-halfH * (1 - ep));
        rectBL.setTranslateX(-halfW * (1 - ep));
        rectBR.setTranslateY(h * (1 - ep) + halfH * ep);
        rectTR.setTranslateX(w * (1 - ep) + halfW * ep);

        if (clippedNode != null) {
            clippedNode.setScaleX(sx + (1 - sx) * ep);
            clippedNode.setScaleY(sy + (1 - sy) * ep);
        }
    }
}
