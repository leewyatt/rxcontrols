package io.github.leewyatt.rxcontrols.animation.page;

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
 * Reveal / cover transition. Unlike {@link AnimSlide} where both pages
 * move simultaneously, only one page moves while the other stays still.
 *
 * <ul>
 *   <li>FORWARD: the current page slides away, revealing the stationary
 *       next page underneath.</li>
 *   <li>BACKWARD: the next page slides in from the side, covering the
 *       stationary current page.</li>
 * </ul>
 *
 * <p>Supports both horizontal and vertical orientations.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimReveal extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private ChangeListener<Number> sizeListener;
    private StackPane animatingContentPane;

    /**
     * Creates a horizontal reveal animation.
     */
    public AnimReveal() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a reveal animation with the given orientation.
     *
     * @param orientation the slide orientation
     */
    public AnimReveal(Orientation orientation) {
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
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == TransitionDirection.FORWARD;

        // FORWARD (reveal): currentPage on top slides away, nextPage stays behind
        // BACKWARD (cover): nextPage on top slides in, currentPage stays behind
        if (forward) {
            // Next page stationary behind
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.toBack();
            }
            // Current page on top, will slide away
            if (currentPage != null) {
                currentPage.setVisible(true);
                currentPage.toFront();
            }
        } else {
            // Current page stationary behind
            if (currentPage != null) {
                currentPage.setVisible(true);
                currentPage.toBack();
            }
            // Next page on top, slides in from off-screen
            if (nextPage != null) {
                double size = horizontal ? contentPane.getWidth() : contentPane.getHeight();
                if (horizontal) {
                    nextPage.setTranslateX(-size);
                } else {
                    nextPage.setTranslateY(-size);
                }
                nextPage.setVisible(true);
                nextPage.toFront();
            }
        }

        DoubleProperty progress = new SimpleDoubleProperty(0);

        Runnable updatePositions = () -> {
            double p = progress.get();
            double size = horizontal ? contentPane.getWidth() : contentPane.getHeight();

            if (forward) {
                // Current page slides out to the left/up
                if (currentPage != null) {
                    if (horizontal) {
                        currentPage.setTranslateX(-size * p);
                    } else {
                        currentPage.setTranslateY(-size * p);
                    }
                }
            } else {
                // Next page slides in from the left/top
                if (nextPage != null) {
                    if (horizontal) {
                        nextPage.setTranslateX(-size * (1.0 - p));
                    } else {
                        nextPage.setTranslateY(-size * (1.0 - p));
                    }
                }
            }
        };

        Runnable finish = () -> {
            removeSizeListener();
            if (currentPage != null) {
                currentPage.setTranslateX(0);
                currentPage.setTranslateY(0);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setTranslateX(0);
                nextPage.setTranslateY(0);
                nextPage.setVisible(true);
            }
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
