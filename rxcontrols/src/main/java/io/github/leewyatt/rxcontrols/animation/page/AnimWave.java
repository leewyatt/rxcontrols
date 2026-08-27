package io.github.leewyatt.rxcontrols.animation.page;

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
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;

/**
 * Water-fill wave transition. The revealed page is clipped to a rising body of
 * water whose top edge is a wavy surface; as the surface sweeps up the page it
 * uncovers the next page, and the crests genuinely rise and fall while it moves.
 *
 * <p>The surface is a <em>sum of sines</em>: several sinusoidal components of
 * different wavelength, weight and speed are added into one height function.
 * Because the components travel at different speeds they drift in and out of
 * phase, so the water line ripples like a real wave rather than sliding as one
 * frozen shape — the same surface model used by the wave progress indicator.</p>
 *
 * <p>Only a clip {@link Path} is mutated each frame; no page node is ever
 * translated, resized or relocated. A clip is not a laid-out child, so the
 * animation drives no {@code requestParentLayout} on the transition host and
 * therefore cannot trigger a per-pulse relayout of the surrounding scene.</p>
 *
 * <p>The transition is direction-aware: a {@link TransitionDirection#FORWARD}
 * change fills water up to reveal the next page, while a
 * {@link TransitionDirection#BACKWARD} change drains it away to uncover the
 * page underneath.</p>
 */
public class AnimWave extends PageAnimationBase {

    // ==================== Constants ====================

    private static final int DEFAULT_WAVE_COUNT = 2;
    private static final double DEFAULT_AMPLITUDE = 24.0;

    /**
     * Number of surface sample points. Fixed and independent of the page width,
     * so a resize mid-transition only re-spaces the existing points (fully
     * resize-safe) and never reallocates the path.
     */
    private static final int SAMPLE_COUNT = 120;

    /**
     * How many phase cycles the surface drifts over the whole transition. The
     * clock is the transition progress itself, so the ripple flows as the water
     * rises without needing a separate frame timer.
     */
    private static final double FLOW_CYCLES = 1.25;

    /**
     * Amplitude ceiling as a fraction of page height, so a large configured
     * amplitude cannot make the overshoot swallow the whole reveal.
     */
    private static final double AMPLITUDE_MAX_RATIO = 1.0 / 3.0;

    /**
     * One sinusoidal component of the surface. Ratios are relative to the base
     * wavelength resolved from {@link #waveCount}; weights sum to {@code 1} so
     * the combined offset stays in {@code [-1, 1]}.
     */
    private static final class WaveComponent {
        final double wavelengthRatio;
        final double weight;
        final double speedRatio;
        final double phase;

        WaveComponent(double wavelengthRatio, double weight, double speedRatio, double phase) {
            this.wavelengthRatio = wavelengthRatio;
            this.weight = weight;
            this.speedRatio = speedRatio;
            this.phase = phase;
        }
    }

    /**
     * The components summed into the surface. Different speed ratios are what
     * make crests rise and fall as they drift in and out of phase. Weights sum
     * to {@code 1}.
     */
    private static final WaveComponent[] WAVE_COMPONENTS = {
            new WaveComponent(1.00, 0.50, 1.00, 0.0),
            new WaveComponent(0.57, 0.32, 1.60, 1.3),
            new WaveComponent(0.31, 0.18, 0.70, 3.7),
    };

    private static final double TWO_PI = 2.0 * Math.PI;

    // ==================== State ====================

    private int waveCount = DEFAULT_WAVE_COUNT;
    private double amplitude = DEFAULT_AMPLITUDE;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private Path waterClip;
    private MoveTo start;
    private LineTo[] surface;
    private LineTo bottomRight;
    private LineTo bottomLeft;

    // ==================== Constructors ====================

    /**
     * Creates a wave animation with default parameters.
     */
    public AnimWave() {
    }

    /**
     * Creates a wave animation with the given number of primary crests.
     *
     * @param waveCount number of primary wave crests across the page width (minimum 1)
     */
    public AnimWave(int waveCount) {
        this.waveCount = Math.max(1, waveCount);
    }

    /**
     * Creates a wave animation with the given crest count and amplitude.
     *
     * @param waveCount number of primary wave crests across the page width (minimum 1)
     * @param amplitude wave crest height in pixels (minimum 0)
     */
    public AnimWave(int waveCount, double amplitude) {
        this.waveCount = Math.max(1, waveCount);
        this.amplitude = Math.max(0.0, amplitude);
    }

    // ==================== Configuration ====================

    /**
     * Returns the number of primary wave crests across the page width.
     *
     * @return the wave count
     */
    public int getWaveCount() {
        return waveCount;
    }

    /**
     * Sets the number of primary wave crests across the page width.
     *
     * @param waveCount the wave count (minimum 1)
     */
    public void setWaveCount(int waveCount) {
        this.waveCount = Math.max(1, waveCount);
    }

    /**
     * Returns the wave crest height in pixels.
     *
     * @return the amplitude
     */
    public double getAmplitude() {
        return amplitude;
    }

    /**
     * Sets the wave crest height in pixels.
     *
     * @param amplitude the amplitude (minimum 0)
     */
    public void setAmplitude(double amplitude) {
        this.amplitude = Math.max(0.0, amplitude);
    }

    /**
     * Returns the interpolator that drives the water level.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator that drives the water level.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    // ==================== Animation ====================

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        StackPane contentPane = context.getContentPane();
        boolean forward = context.getDirection() == TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // FORWARD fills water up over the next page to reveal it; BACKWARD drains
        // water off the current page to uncover the next one sitting underneath.
        Node clippedNode = forward ? nextPage : currentPage;
        ensureClipNodes();
        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.setClip(waterClip);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
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
                updateSurface(newVal.doubleValue(), forward,
                        contentPane.getWidth(), contentPane.getHeight());
        progress.addListener(progressListener);
        // Apply the p=0 pose before the first render so the revealed page does
        // not flash at full size for one frame.
        updateSurface(0.0, forward, contentPane.getWidth(), contentPane.getHeight());

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
     * Lazily builds the reusable clip path. The element list has a fixed length
     * ({@link #SAMPLE_COUNT} surface points plus the two sealed-bottom corners),
     * so it is created once and only its coordinates change afterwards.
     */
    private void ensureClipNodes() {
        if (waterClip != null) {
            return;
        }
        start = new MoveTo();
        surface = new LineTo[SAMPLE_COUNT - 1];
        for (int i = 0; i < surface.length; i++) {
            surface[i] = new LineTo();
        }
        bottomRight = new LineTo();
        bottomLeft = new LineTo();

        waterClip = new Path();
        // Path defaults to fill=null + stroke=BLACK; as a clip mask that would
        // leave only the 1px outline. Fill the closed water body instead.
        waterClip.setFill(Color.BLACK);
        waterClip.setStroke(null);
        waterClip.setManaged(false);
        waterClip.getElements().add(start);
        for (LineTo line : surface) {
            waterClip.getElements().add(line);
        }
        waterClip.getElements().add(bottomRight);
        waterClip.getElements().add(bottomLeft);
        waterClip.getElements().add(new ClosePath());
    }

    /**
     * Recomputes the water body for the given transition progress. The water
     * level is derived from the progress (inverted on a backward transition so
     * the surface drains), while the ripple phase always advances with the raw
     * progress so the flow direction stays constant.
     */
    private void updateSurface(double p, boolean forward, double w, double h) {
        if (waterClip == null || w <= 0.0 || h <= 0.0) {
            return;
        }

        double effAmplitude = Math.min(Math.max(0.0, amplitude), h * AMPLITUDE_MAX_RATIO);
        double sealedBottom = h + effAmplitude + 1.0;

        // level 0 -> surface parked at the bottom (page hidden);
        // level 1 -> surface swept above the top (page fully shown).
        double level = forward ? p : 1.0 - p;
        double travel = h + 2.0 * effAmplitude;
        double baseline = (h + effAmplitude) - level * travel;

        double lambda = w / Math.max(1, waveCount);
        double spacing = w / (SAMPLE_COUNT - 1);
        double flow = TWO_PI * FLOW_CYCLES * p;

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            double x = (i == SAMPLE_COUNT - 1) ? w : i * spacing;
            double offset = 0.0;
            for (WaveComponent c : WAVE_COMPONENTS) {
                double k = TWO_PI / (lambda * c.wavelengthRatio);
                offset += c.weight * Math.sin(k * x + flow * c.speedRatio + c.phase);
            }
            double y = baseline - effAmplitude * offset;
            // Never let the surface dip past the sealed bottom, or the closing
            // edge would fold the fill region inside-out.
            if (y > sealedBottom) {
                y = sealedBottom;
            }
            setPoint(i, x, y);
        }

        bottomRight.setX(w);
        bottomRight.setY(sealedBottom);
        bottomLeft.setX(0.0);
        bottomLeft.setY(sealedBottom);
    }

    private void setPoint(int i, double x, double y) {
        if (i == 0) {
            start.setX(x);
            start.setY(y);
        } else {
            LineTo line = surface[i - 1];
            line.setX(x);
            line.setY(y);
        }
    }

    // ==================== Cleanup ====================

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
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        super.dispose();
    }
}
