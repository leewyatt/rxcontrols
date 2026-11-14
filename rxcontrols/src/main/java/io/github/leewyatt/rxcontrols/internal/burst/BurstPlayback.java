package io.github.leewyatt.rxcontrols.internal.burst;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.animation.burst.BurstFrame;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Plays bursts on one canvas: sizes the canvas for the current reach, clears
 * it every frame and lets the {@link BurstAnimation} draw. The host decides
 * where the canvas goes through the {@code place} callback, which runs at the
 * start and before every frame; the canvas is visible only while a burst
 * plays.
 */
public final class BurstPlayback {

    private final Canvas canvas;
    private final DoubleSupplier reach;
    private final DoubleSupplier anchorSize;
    private final Runnable place;
    private final Runnable onFinished;
    private final Driver driver = new Driver();

    /**
     * Creates a playback for the given canvas.
     *
     * @param canvas     the canvas to draw on; hidden until a burst plays
     * @param reach      supplies the current reach in pixels
     * @param anchorSize supplies the current anchor size in pixels
     * @param place      positions the canvas; reads its size from the canvas
     * @param onFinished runs when a burst ends on its own, not when cancelled
     */
    public BurstPlayback(Canvas canvas, DoubleSupplier reach, DoubleSupplier anchorSize,
                         Runnable place, Runnable onFinished) {
        this.canvas = canvas;
        this.reach = reach;
        this.anchorSize = anchorSize;
        this.place = place;
        this.onFinished = onFinished;
        canvas.setVisible(false);
    }

    /**
     * Returns the canvas this playback draws on.
     *
     * @return the canvas
     */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Plays a burst from the start, cancelling any burst still playing.
     *
     * @param animation the burst to draw
     * @param duration  a positive duration; a zero one ends at once
     * @param colors    the particle colors
     * @param seed      the random seed of this play
     */
    public void play(BurstAnimation animation, Duration duration, List<Paint> colors, long seed) {
        cancel();
        driver.start(animation, duration, colors, seed);
    }

    /**
     * Stops the burst, if any, and hides the cleared canvas. Does not run
     * the {@code onFinished} callback; a host that pools canvases reclaims
     * a cancelled one itself.
     */
    public void cancel() {
        driver.stop();
        driver.clear();
    }

    /**
     * Returns whether a burst is playing.
     *
     * @return {@code true} while a burst plays
     */
    public boolean isPlaying() {
        return driver.animation != null;
    }

    /**
     * Resizes the canvas for the current reach and repositions it, for a
     * host whose geometry changed while a burst plays.
     */
    public void refresh() {
        if (isPlaying()) {
            fit(reach.getAsDouble(), anchorSize.getAsDouble());
        }
    }

    private void fit(double currentReach, double currentAnchorSize) {
        double side = BurstSettings.canvasSide(currentReach, currentAnchorSize);
        if (canvas.getWidth() != side || canvas.getHeight() != side) {
            canvas.setWidth(side);
            canvas.setHeight(side);
        }
        place.run();
    }

    private final class Driver extends Transition {

        private BurstAnimation animation;
        private List<Paint> colors;
        private long seed;

        private Driver() {
            setInterpolator(Interpolator.LINEAR);
            setOnFinished(event -> {
                clear();
                onFinished.run();
            });
        }

        void start(BurstAnimation value, Duration duration, List<Paint> palette, long playSeed) {
            animation = value;
            colors = palette;
            seed = playSeed;
            setCycleDuration(duration);
            canvas.setVisible(true);
            fit(reach.getAsDouble(), anchorSize.getAsDouble());
            playFromStart();
        }

        @Override
        protected void interpolate(double frac) {
            if (animation == null) {
                return;
            }
            // Read once, so the canvas size and the frame agree.
            double currentReach = reach.getAsDouble();
            double currentAnchorSize = anchorSize.getAsDouble();
            fit(currentReach, currentAnchorSize);
            double side = canvas.getWidth();
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0.0, 0.0, side, canvas.getHeight());
            gc.save();
            animation.draw(gc, new BurstFrame(frac, side / 2.0, canvas.getHeight() / 2.0,
                    currentReach, currentAnchorSize, colors, seed));
            gc.restore();
        }

        // stop() itself is not overridden: playFromStart() calls it, which
        // would wipe the play that is just starting.
        private void clear() {
            animation = null;
            colors = null;
            canvas.getGraphicsContext2D().clearRect(0.0, 0.0, canvas.getWidth(), canvas.getHeight());
            canvas.setVisible(false);
        }
    }
}
