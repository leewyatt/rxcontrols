package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Pinwheel wipe transition. Multiple blades rotate around the center of
 * the page like a windmill, progressively sweeping away the current page
 * to reveal the next one underneath.
 *
 * <p>The {@link #setBladeCount(int) bladeCount} parameter controls the
 * number of blades:</p>
 * <ul>
 *   <li><b>2:</b> two-blade sweep, each blade covers 180° — bold,
 *       dramatic look.</li>
 *   <li><b>3 ~ 4:</b> classic pinwheel / windmill feel.
 *       <em>(default: 4)</em></li>
 *   <li><b>5 ~ 8:</b> finer blades, faster visual rhythm.</li>
 *   <li><b>8+:</b> many thin blades, approaches a radial-shutter
 *       appearance.</li>
 * </ul>
 *
 * <p>FORWARD sweeps clockwise; BACKWARD sweeps counter-clockwise.</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimPinwheel extends PageAnimationBase {

    private int bladeCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    private Arc[] clipArcs;
    private Group clipGroup;

    /**
     * Creates a pinwheel animation with 4 blades.
     */
    public AnimPinwheel() {
        this(4);
    }

    /**
     * Creates a pinwheel animation with the given number of blades.
     *
     * @param bladeCount the number of blades (at least 2;
     *                   see class javadoc for effect of different ranges)
     */
    public AnimPinwheel(int bladeCount) {
        this.bladeCount = Math.max(2, bladeCount);
    }

    /**
     * Returns the interpolator used for the rotation progress.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for the rotation progress.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the number of blades.
     *
     * @return the blade count
     */
    public int getBladeCount() {
        return bladeCount;
    }

    /**
     * Sets the number of blades.
     *
     * @param bladeCount at least 2; see class javadoc for effect of
     *                   different ranges
     */
    public void setBladeCount(int bladeCount) {
        this.bladeCount = Math.max(2, bladeCount);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

        // Layer order: nextPage behind, currentPage on top (clipped)
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        // Pre-create Arc objects for the clip
        clipArcs = new Arc[bladeCount];
        clipGroup = new Group();
        for (int i = 0; i < bladeCount; i++) {
            Arc arc = new Arc();
            arc.setType(ArcType.ROUND);
            clipArcs[i] = arc;
            clipGroup.getChildren().add(arc);
        }
        if (currentPage != null) {
            currentPage.setClip(clipGroup);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
            clipArcs = null;
            clipGroup = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) ->
                updateClip(newVal.doubleValue(), currentPage, forward);
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

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

    /**
     * Updates the clip arcs to reflect the current progress.
     *
     * <p>Each blade "owns" a sector of {@code 360/bladeCount} degrees.
     * As progress advances, the blade sweeps through its sector, and
     * the un-swept portion (still showing the current page) shrinks.</p>
     *
     * <p>Angles are converted to the JavaFX convention (counter-clockwise
     * from the positive X axis) with a 90° offset so that blade 0 starts
     * at the 12 o'clock position.</p>
     */
    private void updateClip(double p, Node currentPage, boolean forward) {
        if (currentPage == null || clipArcs == null || animContentPane == null) {
            return;
        }

        if (p >= 1.0) {
            currentPage.setClip(new Rectangle(0, 0, 0, 0));
            return;
        }

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double radius = Math.sqrt(cx * cx + cy * cy) + 1;

        double sectorWidth = 360.0 / bladeCount;
        double unsweptWidth = (1.0 - p) * sectorWidth;

        for (int i = 0; i < bladeCount; i++) {
            Arc arc = clipArcs[i];
            arc.setCenterX(cx);
            arc.setCenterY(cy);
            arc.setRadiusX(radius);
            arc.setRadiusY(radius);

            // Screen angles: 0°=top, increases clockwise.
            // JavaFX angles: 0°=right, increases counter-clockwise.
            // Conversion: jfxAngle = 90° - screenAngle
            //
            // FORWARD (CW sweep): un-swept region is at the trailing
            // end of each sector (closer to the next blade).
            // BACKWARD (CCW sweep): un-swept region is at the leading
            // end (closer to the blade's own start).
            double jfxStartAngle;
            if (forward) {
                jfxStartAngle = 90.0 - (i + 1) * sectorWidth;
            } else {
                jfxStartAngle = 90.0 - i * sectorWidth - unsweptWidth;
            }

            arc.setStartAngle(jfxStartAngle);
            arc.setLength(unsweptWidth);
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
        clipArcs = null;
        clipGroup = null;
        animContentPane = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        clipArcs = null;
        clipGroup = null;
        animContentPane = null;
        super.dispose();
    }
}
