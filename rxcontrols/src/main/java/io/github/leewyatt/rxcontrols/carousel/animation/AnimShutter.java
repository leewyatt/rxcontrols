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
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

/**
 * Camera shutter transition. Multiple arc-shaped blades close from the
 * edges toward the center (covering the current page), then open from
 * the center outward (revealing the next page).
 *
 * <p>The number of blades is configurable. Default is 8, which produces
 * a classic camera iris look.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame to compute the blade radius.</p>
 */
public class AnimShutter extends CarouselAnimationBase {

    private final int bladeCount;
    private Color bladeColor = Color.rgb(20, 20, 20);
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private Arc[] blades;
    private StackPane animContentPane;
    private boolean swapped;

    /**
     * Creates a shutter animation with 8 blades.
     */
    public AnimShutter() {
        this(8);
    }

    /**
     * Creates a shutter animation with the specified number of blades.
     *
     * @param bladeCount the number of blades (at least 2, recommended 4 to 16)
     */
    public AnimShutter(int bladeCount) {
        this.bladeCount = Math.max(2, bladeCount);
    }

    /**
     * Returns the blade fill color.
     *
     * @return the blade color
     */
    public Color getBladeColor() {
        return bladeColor;
    }

    /**
     * Sets the blade fill color. Default is near-black.
     *
     * @param bladeColor the color
     */
    public void setBladeColor(Color bladeColor) {
        this.bladeColor = bladeColor;
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
        animContentPane = context.getContentPane();

        // Both pages visible; current on top initially
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }
        swapped = false;

        // Create blade arcs (overlay on top of everything)
        double sectorAngle = 360.0 / bladeCount;
        blades = new Arc[bladeCount];

        for (int i = 0; i < bladeCount; i++) {
            Arc blade = new Arc();
            blade.setType(ArcType.ROUND);
            blade.setFill(bladeColor);
            blade.setManaged(false);
            blade.setStartAngle(i * sectorAngle);
            blade.setLength(0);
            blades[i] = blade;
            animContentPane.getChildren().add(blade);
        }

        Runnable finish = () -> {
            removeBlades();
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
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
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateBlades(p, w, h, currentPage, nextPage);
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

    private void updateBlades(double p, double w, double h,
                               Node currentPage, Node nextPage) {
        double cx = w / 2;
        double cy = h / 2;
        double radius = Math.sqrt(cx * cx + cy * cy) + 10;
        double sectorAngle = 360.0 / bladeCount;

        // Phase 1 (0→0.5): blades close
        // Phase 2 (0.5→1): blades open
        double bladeAngle;
        if (p <= 0.5) {
            double phase = p * 2.0;
            bladeAngle = sectorAngle * phase;
        } else {
            // Swap pages at midpoint
            if (!swapped) {
                swapped = true;
                if (currentPage != null) {
                    currentPage.setVisible(false);
                }
                if (nextPage != null) {
                    nextPage.setVisible(true);
                    nextPage.toBack();
                }
                // Bring blades to front
                for (Arc blade : blades) {
                    if (blade != null) {
                        blade.toFront();
                    }
                }
            }
            double phase = (p - 0.5) * 2.0;
            bladeAngle = sectorAngle * (1.0 - phase);
        }

        for (int i = 0; i < bladeCount; i++) {
            if (blades[i] == null) {
                continue;
            }
            blades[i].setCenterX(cx);
            blades[i].setCenterY(cy);
            blades[i].setRadiusX(radius);
            blades[i].setRadiusY(radius);
            blades[i].setLength(bladeAngle);
        }
    }

    private void removeBlades() {
        if (blades != null && animContentPane != null) {
            for (Arc blade : blades) {
                if (blade != null) {
                    animContentPane.getChildren().remove(blade);
                }
            }
        }
        blades = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeBlades();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeBlades();
        super.dispose();
    }
}
