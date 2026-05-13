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
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Random;

/**
 * Digital glitch transition. The current page is sliced into horizontal
 * strips that undergo random horizontal displacement and color channel
 * shifting, simulating a digital signal corruption effect.
 *
 * <p>The animation proceeds in two phases:</p>
 * <ol>
 *   <li><b>Glitch phase</b> (first ~70% of duration): strips jitter with
 *       randomized offsets and color shifts that intensify over time.</li>
 *   <li><b>Fade-out phase</b> (last ~30%): strips fade out to reveal the
 *       next page underneath.</li>
 * </ol>
 *
 * <p>The {@link #setGlitchSteps(int) glitchSteps} parameter controls how
 * many discrete random states are generated during the animation, which
 * directly determines the visual rhythm:</p>
 * <ul>
 *   <li><b>Low values (6 ~ 10):</b> each state holds longer, producing a
 *       slow, deliberate "data corruption" stutter with clearly visible
 *       pauses between shifts.</li>
 *   <li><b>Medium values (10 ~ 20):</b> balanced feel — noticeable steps
 *       with moderate pacing. <em>(default: 12)</em></li>
 *   <li><b>High values (30 ~ 60+):</b> states change rapidly, approaching
 *       per-frame noise — resembles aggressive analog TV interference or
 *       VHS tracking errors.</li>
 * </ul>
 *
 * <p>FORWARD and BACKWARD influence the predominant displacement direction
 * of the strips.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimGlitch extends CarouselAnimationBase {

    private int stripCount;
    private double maxOffset;
    private int glitchSteps;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] strips;
    private ColorAdjust[] colorAdjusts;
    private StackPane animContentPane;
    private Random random;

    // Step-based state: holds current glitch offsets between steps
    private int lastStep = -1;
    private double[] stepOffsets;
    private double[] stepHues;
    private boolean[] stepActive;

    /**
     * Creates a glitch animation with 16 strips, 15% max offset, and
     * 12 glitch steps.
     */
    public AnimGlitch() {
        this(16, 0.15, 12);
    }

    /**
     * Creates a glitch animation with the given parameters.
     *
     * @param stripCount the number of horizontal strips (at least 4, recommended 10 to 24)
     * @param maxOffset  the maximum horizontal displacement as a fraction of content width
     *                   (e.g. 0.15 means up to 15%)
     * @param glitchSteps the number of discrete random states during the animation
     *                    (at least 3; see class javadoc for effect of different ranges)
     */
    public AnimGlitch(int stripCount, double maxOffset, int glitchSteps) {
        this.stripCount = Math.max(4, stripCount);
        this.maxOffset = Math.max(0.01, maxOffset);
        this.glitchSteps = Math.max(3, glitchSteps);
    }

    /**
     * Returns the interpolator used for the overall progress.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for the overall progress.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the number of horizontal strips.
     *
     * @return the strip count
     */
    public int getStripCount() {
        return stripCount;
    }

    /**
     * Sets the number of horizontal strips.
     *
     * @param stripCount at least 4, recommended 10 to 24
     */
    public void setStripCount(int stripCount) {
        this.stripCount = Math.max(4, stripCount);
    }

    /**
     * Returns the maximum horizontal offset as a fraction of content width.
     *
     * @return the max offset fraction
     */
    public double getMaxOffset() {
        return maxOffset;
    }

    /**
     * Sets the maximum horizontal displacement as a fraction of content width.
     *
     * @param maxOffset e.g. 0.15 means up to 15% of width
     */
    public void setMaxOffset(double maxOffset) {
        this.maxOffset = Math.max(0.01, maxOffset);
    }

    /**
     * Returns the number of discrete glitch states during the animation.
     *
     * @return the glitch step count
     */
    public int getGlitchSteps() {
        return glitchSteps;
    }

    /**
     * Sets the number of discrete random states generated during the animation.
     * This controls the visual rhythm of the glitch effect:
     * <ul>
     *   <li><b>6 ~ 10:</b> slow, deliberate "data corruption" stutter</li>
     *   <li><b>10 ~ 20:</b> balanced pacing <em>(default: 12)</em></li>
     *   <li><b>30 ~ 60+:</b> rapid noise, resembling analog TV interference</li>
     * </ul>
     *
     * @param glitchSteps at least 3
     */
    public void setGlitchSteps(int glitchSteps) {
        this.glitchSteps = Math.max(3, glitchSteps);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        installResizeGuard(animContentPane);

        boolean forward = direction == Direction.FORWARD;
        random = new Random();

        // Next page as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snap = currentPage != null
                ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        // Create horizontal strips
        strips = new ImageView[stripCount];
        colorAdjusts = new ColorAdjust[stripCount];
        double stripH = h / stripCount;

        if (snap != null) {
            for (int i = 0; i < stripCount; i++) {
                ImageView strip = new ImageView(snap);
                strip.setManaged(false);
                strip.setViewport(new Rectangle2D(0, i * stripH, w, stripH));
                strip.setLayoutX(0);
                strip.setLayoutY(i * stripH);

                ColorAdjust ca = new ColorAdjust();
                strip.setEffect(ca);
                colorAdjusts[i] = ca;

                strips[i] = strip;
                animContentPane.getChildren().add(strip);
            }
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeStrips();
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

        // Initialize step-based state
        lastStep = -1;
        stepOffsets = new double[stripCount];
        stepHues = new double[stripCount];
        stepActive = new boolean[stripCount];

        double dirBias = forward ? 1.0 : -1.0;
        double maxPx = w * maxOffset;

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            updateStrips(p, dirBias, maxPx);
        };
        progress.addListener(progressListener);

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

    /**
     * Phase split:
     * <ul>
     *   <li>0.0 ~ 0.7: glitch phase — increasing jitter intensity</li>
     *   <li>0.7 ~ 1.0: fade-out phase — strips fade while jitter continues</li>
     * </ul>
     *
     * Within the glitch phase, offsets are only recalculated when crossing
     * a step boundary (every {@code 1/glitchSteps} of progress), so each
     * glitch state holds for a period determined by the total step count.
     */
    private void updateStrips(double p, double dirBias, double maxPx) {
        if (strips == null) {
            return;
        }

        double glitchEnd = 0.7;
        double intensity;
        double fadeAlpha;

        if (p <= glitchEnd) {
            intensity = interpolator.interpolate(0.0, 1.0, p / glitchEnd);
            fadeAlpha = 1.0;
        } else {
            intensity = 1.0;
            double fadeP = (p - glitchEnd) / (1.0 - glitchEnd);
            fadeAlpha = 1.0 - fadeP;
        }

        // Determine current step — only regenerate offsets on step change
        int step = Math.min((int) (p * glitchSteps), glitchSteps - 1);
        if (step != lastStep) {
            lastStep = step;
            generateStepState(intensity, dirBias, maxPx);
        }

        // Apply stored offsets and hues
        for (int i = 0; i < strips.length; i++) {
            if (strips[i] == null) {
                continue;
            }

            if (stepActive[i]) {
                strips[i].setTranslateX(stepOffsets[i]);
                if (colorAdjusts[i] != null) {
                    colorAdjusts[i].setHue(stepHues[i]);
                }
            } else {
                strips[i].setTranslateX(0);
                if (colorAdjusts[i] != null) {
                    colorAdjusts[i].setHue(0);
                }
            }

            strips[i].setOpacity(fadeAlpha);
        }
    }

    /**
     * Generates a new random glitch state. Only a fraction of strips are
     * displaced (controlled by intensity); the rest stay in place, creating
     * the characteristic "partial corruption" look. Roughly 1 in 4 steps
     * is a "calm" moment where all strips snap back to their original
     * position.
     */
    private void generateStepState(double intensity, double dirBias, double maxPx) {
        // ~25% chance of a calm step (all strips in place), decreasing at high intensity
        boolean calm = random.nextDouble() < 0.25 * (1.0 - intensity * 0.6);

        double offsetRange = maxPx * intensity;

        for (int i = 0; i < stripCount; i++) {
            if (calm) {
                stepActive[i] = false;
                stepOffsets[i] = 0;
                stepHues[i] = 0;
            } else {
                // Each strip has a probability of being displaced, increasing with intensity
                stepActive[i] = random.nextDouble() < 0.3 + intensity * 0.5;
                if (stepActive[i]) {
                    stepOffsets[i] = (random.nextDouble() * 2.0 - 1.0 + dirBias * 0.3) * offsetRange;
                    // ~40% chance of color shift on active strips
                    stepHues[i] = random.nextDouble() < 0.4
                            ? (random.nextDouble() - 0.5) * 0.6 : 0;
                } else {
                    stepOffsets[i] = 0;
                    stepHues[i] = 0;
                }
            }
        }
    }

    private void removeStrips() {
        if (strips != null && animContentPane != null) {
            for (ImageView strip : strips) {
                if (strip != null) {
                    strip.setEffect(null);
                    strip.setImage(null);
                    strip.setTranslateX(0);
                    strip.setOpacity(1);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        strips = null;
        colorAdjusts = null;
        stepOffsets = null;
        stepHues = null;
        stepActive = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeStrips();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeStrips();
        super.dispose();
    }
}
