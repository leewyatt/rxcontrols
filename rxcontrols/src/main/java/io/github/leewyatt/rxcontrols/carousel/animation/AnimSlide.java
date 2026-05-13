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
 * Sliding transition. The current page slides out to one side while the
 * next page slides in from the opposite side.
 *
 * <p>Supports both horizontal and vertical orientations.</p>
 *
 * <p>This animation is resize-safe: it animates a normalized progress value
 * (0 to 1) and computes translate from the content pane's current size
 * on every frame, so pages stay seamlessly joined during window resize.</p>
 */
public class AnimSlide extends CarouselAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private ChangeListener<Number> sizeListener;
    private StackPane animatingContentPane;

    /**
     * Creates a horizontal slide animation.
     */
    public AnimSlide() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a slide animation with the given orientation.
     *
     * @param orientation the slide orientation
     */
    public AnimSlide(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the interpolator used for the slide animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the slide animation.
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
        StackPane contentPane = context.getContentPane();

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        double sign = direction == Direction.FORWARD ? 1.0 : -1.0;

        double initialOffset = sign * (horizontal ? contentPane.getWidth() : contentPane.getHeight());
        if (horizontal) {
            nextPage.setTranslateX(initialOffset);
        } else {
            nextPage.setTranslateY(initialOffset);
        }
        nextPage.setVisible(true);

        DoubleProperty progress = new SimpleDoubleProperty(0);

        Runnable updatePositions = () -> {
            double p = progress.get();
            double size = horizontal ? contentPane.getWidth() : contentPane.getHeight();
            if (currentPage != null) {
                if (horizontal) {
                    currentPage.setTranslateX(-sign * size * p);
                } else {
                    currentPage.setTranslateY(-sign * size * p);
                }
            }
            if (horizontal) {
                nextPage.setTranslateX(sign * size * (1.0 - p));
            } else {
                nextPage.setTranslateY(sign * size * (1.0 - p));
            }
        };

        Runnable finish = () -> {
            removeSizeListener();
            if (currentPage != null) {
                currentPage.setTranslateX(0);
                currentPage.setTranslateY(0);
                currentPage.setVisible(false);
            }
            nextPage.setTranslateX(0);
            nextPage.setTranslateY(0);
            nextPage.setVisible(true);
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        progress.addListener((obs, oldVal, newVal) -> updatePositions.run());
        // Apply p=0 state before first render frame to prevent nextPage flicker
        updatePositions.run();

        removeSizeListener();
        animatingContentPane = contentPane;
        sizeListener = (obs, oldVal, newVal) -> updatePositions.run();
        if (horizontal) {
            contentPane.widthProperty().addListener(sizeListener);
        } else {
            contentPane.heightProperty().addListener(sizeListener);
        }

        Timeline timeline = new Timeline(
                new KeyFrame(duration, new KeyValue(progress, 1.0, interpolator))
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
        removeSizeListener();
        for (Node child : context.getContentPane().getChildren()) {
            child.setTranslateX(0);
            child.setTranslateY(0);
        }
    }

    @Override
    public void dispose() {
        removeSizeListener();
        super.dispose();
    }

    private void removeSizeListener() {
        if (sizeListener != null && animatingContentPane != null) {
            animatingContentPane.widthProperty().removeListener(sizeListener);
            animatingContentPane.heightProperty().removeListener(sizeListener);
            sizeListener = null;
            animatingContentPane = null;
        }
    }
}
