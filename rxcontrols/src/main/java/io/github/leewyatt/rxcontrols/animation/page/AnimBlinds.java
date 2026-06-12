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
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Blinds transition. Rectangular slats expand to reveal the next page,
 * like venetian blinds opening.
 *
 * <p>Works in both horizontal (slats expand vertically) and vertical
 * (slats expand horizontally) orientations.</p>
 */
public class AnimBlinds extends PageAnimationBase {

    private static final int DEFAULT_SLAT_COUNT = 8;

    private final Orientation orientation;
    private final int slatCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty slatSize = new SimpleDoubleProperty(0);
    private final Rectangle[] slats;
    private final ObjectBinding<Node> clipBinding;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal blinds animation with the default slat count.
     */
    public AnimBlinds() {
        this(Orientation.HORIZONTAL, DEFAULT_SLAT_COUNT);
    }

    /**
     * Creates a blinds animation with the given orientation and the default slat count.
     *
     * @param orientation the blinds orientation
     */
    public AnimBlinds(Orientation orientation) {
        this(orientation, DEFAULT_SLAT_COUNT);
    }

    /**
     * Creates a blinds animation with the given orientation and slat count.
     *
     * @param orientation the blinds orientation
     * @param slatCount   the number of slats
     */
    public AnimBlinds(Orientation orientation, int slatCount) {
        this.orientation = orientation;
        this.slatCount = Math.max(2, slatCount);
        this.slats = new Rectangle[this.slatCount];

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        for (int i = 0; i < this.slatCount; i++) {
            slats[i] = new Rectangle();
            if (horizontal) {
                slats[i].heightProperty().bind(slatSize);
            } else {
                slats[i].widthProperty().bind(slatSize);
            }
        }

        clipBinding = Bindings.createObjectBinding(() -> {
            Shape shape = slats[0];
            for (int i = 1; i < this.slatCount; i++) {
                shape = Shape.union(shape, slats[i]);
            }
            return shape;
        }, slatSize);
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

        boolean horizontal = orientation == Orientation.HORIZONTAL;

        // FORWARD: clip on nextPage (blinds reveal next), BACKWARD: clip on currentPage (blinds hide current)
        boolean clipNext = direction == TransitionDirection.FORWARD;
        Node clippedNode = clipNext ? nextPage : currentPage;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // Initialize slats to progress=0 state before binding
        updateBlinds(0, contentPane.getWidth(), contentPane.getHeight(), horizontal, clipNext);

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

        final boolean isForward = clipNext;

        progressListener = (obs, oldVal, newVal) -> {
            updateBlinds(newVal.doubleValue(), contentPane.getWidth(), contentPane.getHeight(), horizontal, isForward);
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
        for (Rectangle slat : slats) {
            slat.widthProperty().unbind();
            slat.heightProperty().unbind();
        }
        if (clipBinding != null) {
            clipBinding.dispose();
        }
        super.dispose();
    }

    private void updateBlinds(double p, double paneWidth, double paneHeight, boolean horizontal, boolean isForward) {
        for (int i = 0; i < slatCount; i++) {
            if (horizontal) {
                slats[i].setX(0);
                slats[i].setY(paneHeight / slatCount * i);
                slats[i].setWidth(paneWidth);
            } else {
                slats[i].setX(paneWidth / slatCount * i);
                slats[i].setY(0);
                slats[i].setHeight(paneHeight);
            }
        }

        double maxSlatSize = horizontal ? paneHeight / slatCount : paneWidth / slatCount;
        if (isForward) {
            slatSize.set(maxSlatSize * p);
        } else {
            slatSize.set(maxSlatSize * (1 - p));
        }
    }
}
