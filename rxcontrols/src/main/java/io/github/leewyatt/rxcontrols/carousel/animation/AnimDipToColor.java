package io.github.leewyatt.rxcontrols.carousel.animation;

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
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Dip-to-color transition. The current page fades into a solid color,
 * then the solid color fades out to reveal the next page — a staple
 * of film and video editing (commonly known as "Dip to Black" or
 * "Dip to White").
 *
 * <p>The animation proceeds in two phases:</p>
 * <ol>
 *   <li><b>Fade-to-color</b> (first ~50%): the current page fades out,
 *       revealing the solid color layer underneath.</li>
 *   <li><b>Fade-from-color</b> (last ~50%): the solid color layer fades
 *       out, revealing the next page.</li>
 * </ol>
 *
 * <p>The dip color is configurable via {@link #setColor(Color)}. Common
 * choices:</p>
 * <ul>
 *   <li>{@link Color#BLACK} — classic cinematic "dip to black"
 *       <em>(default)</em></li>
 *   <li>{@link Color#WHITE} — bright "dip to white", often used for
 *       dream sequences or flashbacks</li>
 *   <li>Any color — creative uses, e.g. brand color transitions</li>
 * </ul>
 *
 * <p>This animation is not direction-sensitive — FORWARD and BACKWARD
 * produce the same visual effect.</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimDipToColor extends CarouselAnimationBase {

    private Color color;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private Rectangle colorLayer;
    private StackPane animContentPane;

    /**
     * Creates a dip-to-black transition.
     */
    public AnimDipToColor() {
        this(Color.BLACK);
    }

    /**
     * Creates a dip-to-color transition with the given color.
     *
     * @param color the solid color to dip through
     */
    public AnimDipToColor(Color color) {
        this.color = color;
    }

    /**
     * Returns the interpolator used for the fade curves.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for the fade curves.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the solid color used for the dip.
     *
     * @return the dip color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Sets the solid color to dip through.
     *
     * @param color the dip color (e.g. {@code Color.BLACK}, {@code Color.WHITE})
     */
    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        animContentPane = context.getContentPane();

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        // Layer order (bottom to top): nextPage → colorLayer → currentPage
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.setOpacity(1);
            nextPage.toBack();
        }

        colorLayer = new Rectangle(w, h, color);
        colorLayer.setManaged(false);
        colorLayer.setLayoutX(0);
        colorLayer.setLayoutY(0);
        animContentPane.getChildren().add(colorLayer);

        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            removeColorLayer();
            if (currentPage != null) {
                currentPage.setOpacity(1);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setOpacity(1);
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

        progressListener = (obs, oldVal, newVal) ->
                updateFade(newVal.doubleValue(), currentPage);
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, Interpolator.LINEAR))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updateFade(double p, Node currentPage) {
        double midpoint = 0.5;

        if (p <= midpoint) {
            // Phase 1: current page fades out → solid color appears
            double t = interpolator.interpolate(0.0, 1.0, p / midpoint);
            if (currentPage != null) {
                currentPage.setOpacity(1.0 - t);
            }
            if (colorLayer != null) {
                colorLayer.setOpacity(1);
            }
        } else {
            // Phase 2: solid color fades out → next page appears
            double t = interpolator.interpolate(0.0, 1.0, (p - midpoint) / (1.0 - midpoint));
            if (currentPage != null) {
                currentPage.setOpacity(0);
            }
            if (colorLayer != null) {
                colorLayer.setOpacity(1.0 - t);
            }
        }
    }

    private void removeColorLayer() {
        if (colorLayer != null && animContentPane != null) {
            animContentPane.getChildren().remove(colorLayer);
        }
        colorLayer = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeColorLayer();
        for (Node child : context.getContentPane().getChildren()) {
            child.setOpacity(1);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeColorLayer();
        super.dispose();
    }
}
