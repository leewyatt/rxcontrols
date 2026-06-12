package io.github.leewyatt.rxcontrols.animation.page;

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
 * Cross reveal transition. A cross shape (union of horizontal and vertical
 * rectangles) expands from the center to reveal the next page.
 */
public class AnimCross extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Rectangle rectHor = new Rectangle();
    private final Rectangle rectVer = new Rectangle();
    private final ObjectBinding<Node> shapeBinding;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a cross reveal animation.
     */
    public AnimCross() {
        shapeBinding = Bindings.createObjectBinding(
                () -> Shape.union(rectHor, rectVer),
                rectHor.heightProperty(), rectVer.widthProperty());
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

        Node clippedNode = forward ? nextPage : currentPage;

        // Initialize clip shapes to progress=0 state before binding
        updateCross(0, contentPane.getWidth(), contentPane.getHeight(), forward);

        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.clipProperty().bind(shapeBinding);
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
            updateCross(newVal.doubleValue(), contentPane.getWidth(), contentPane.getHeight(), forward);
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

    private void updateCross(double p, double w, double h, boolean forward) {
        rectVer.setHeight(h);
        rectHor.setWidth(w);

        if (forward) {
            rectVer.setTranslateX(w / 2 * (1 - p));
            rectHor.setTranslateY(h / 2 * (1 - p));
            // Set binding-trigger properties last
            rectHor.setHeight(h * p);
            rectVer.setWidth(w * p);
        } else {
            rectVer.setTranslateX(w / 2 * p);
            rectHor.setTranslateY(h / 2 * p);
            // Set binding-trigger properties last
            rectHor.setHeight(h * (1 - p));
            rectVer.setWidth(w * (1 - p));
        }
    }
}
