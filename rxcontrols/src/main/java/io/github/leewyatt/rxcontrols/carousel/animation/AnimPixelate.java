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
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Pixelate transition. The current page progressively dissolves into
 * larger and larger mosaic blocks, then fades out to reveal the next
 * page underneath — a classic "digital dissolve" effect seen in video
 * editors and game scene transitions.
 *
 * <p>The animation proceeds in two phases:</p>
 * <ol>
 *   <li><b>Pixelate phase</b> (first ~50% of duration): block size
 *       increases from 1 pixel (original image) up to
 *       {@link #setMaxBlockSize(int) maxBlockSize}, creating an
 *       increasingly coarse mosaic.</li>
 *   <li><b>Fade-out phase</b> (last ~50%): the mosaic holds at
 *       maximum pixelation while fading out, revealing the next page.</li>
 * </ol>
 *
 * <p>The {@link #setMaxBlockSize(int) maxBlockSize} parameter controls
 * how coarse the mosaic becomes at peak pixelation:</p>
 * <ul>
 *   <li><b>10 ~ 20:</b> subtle pixelation — individual blocks are small,
 *       the image remains somewhat recognizable.</li>
 *   <li><b>20 ~ 50:</b> moderate pixelation — clearly blocky, classic
 *       retro game feel. <em>(default: 40)</em></li>
 *   <li><b>50 ~ 100+:</b> extreme pixelation — very few large color
 *       blocks, image becomes abstract.</li>
 * </ul>
 *
 * <p>This animation is not direction-sensitive — FORWARD and BACKWARD
 * produce the same visual effect.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimPixelate extends CarouselAnimationBase {

    private int maxBlockSize;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView pixelView;
    private WritableImage sourceSnap;
    private WritableImage pixelatedSnap;
    private StackPane animContentPane;
    private int lastBlockSize = -1;

    /**
     * Creates a pixelate animation with a maximum block size of 40 pixels.
     */
    public AnimPixelate() {
        this(40);
    }

    /**
     * Creates a pixelate animation with the given maximum block size.
     *
     * @param maxBlockSize the block size in pixels at peak pixelation
     *                     (at least 2; see class javadoc for effect of different ranges)
     */
    public AnimPixelate(int maxBlockSize) {
        this.maxBlockSize = Math.max(2, maxBlockSize);
    }

    /**
     * Returns the interpolator used for the pixelation ramp.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for the pixelation ramp.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the maximum block size in pixels at peak pixelation.
     *
     * @return the max block size
     */
    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    /**
     * Sets the maximum block size in pixels at peak pixelation.
     * <ul>
     *   <li><b>10 ~ 20:</b> subtle pixelation, image still recognizable</li>
     *   <li><b>20 ~ 50:</b> moderate, classic retro feel <em>(default: 40)</em></li>
     *   <li><b>50 ~ 100+:</b> extreme, image becomes abstract</li>
     * </ul>
     *
     * @param maxBlockSize at least 2
     */
    public void setMaxBlockSize(int maxBlockSize) {
        this.maxBlockSize = Math.max(2, maxBlockSize);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        animContentPane = context.getContentPane();

        installResizeGuard(animContentPane);

        // Next page as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        sourceSnap = currentPage != null
                ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        if (sourceSnap != null) {
            int sw = (int) sourceSnap.getWidth();
            int sh = (int) sourceSnap.getHeight();
            pixelatedSnap = new WritableImage(sw, sh);

            pixelView = new ImageView(sourceSnap);
            pixelView.setManaged(false);
            pixelView.setLayoutX(0);
            pixelView.setLayoutY(0);
            animContentPane.getChildren().add(pixelView);
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removePixelView();
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
        lastBlockSize = -1;

        progressListener = (obs, oldVal, newVal) ->
                updatePixelation(newVal.doubleValue());
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

    private void updatePixelation(double p) {
        if (pixelView == null) {
            return;
        }

        // Block size: ramps up over the first half, then holds at max
        double pixelateEnd = 0.5;
        int blockSize;
        if (p <= pixelateEnd) {
            double t = interpolator.interpolate(0.0, 1.0, p / pixelateEnd);
            blockSize = 1 + (int) (t * (maxBlockSize - 1));
        } else {
            blockSize = maxBlockSize;
        }

        // Opacity: fades from 1 to 0 across the entire duration
        double alpha = 1.0 - p;

        if (blockSize != lastBlockSize) {
            lastBlockSize = blockSize;
            if (blockSize <= 1) {
                // At block size 1, show original snapshot directly
                pixelView.setImage(sourceSnap);
            } else {
                renderPixelated(blockSize);
                pixelView.setImage(pixelatedSnap);
            }
        }

        pixelView.setOpacity(alpha);
    }

    /**
     * Renders a pixelated version of the source snapshot into
     * {@code pixelatedSnap}. Each block of {@code blockSize × blockSize}
     * pixels is filled with the color sampled from the block's center.
     */
    private void renderPixelated(int blockSize) {
        if (sourceSnap == null || pixelatedSnap == null) {
            return;
        }

        PixelReader reader = sourceSnap.getPixelReader();
        PixelWriter writer = pixelatedSnap.getPixelWriter();
        int w = (int) sourceSnap.getWidth();
        int h = (int) sourceSnap.getHeight();

        for (int by = 0; by < h; by += blockSize) {
            int endY = Math.min(by + blockSize, h);
            int cy = Math.min(by + blockSize / 2, h - 1);

            for (int bx = 0; bx < w; bx += blockSize) {
                int endX = Math.min(bx + blockSize, w);
                int cx = Math.min(bx + blockSize / 2, w - 1);
                int argb = reader.getArgb(cx, cy);

                for (int y = by; y < endY; y++) {
                    for (int x = bx; x < endX; x++) {
                        writer.setArgb(x, y, argb);
                    }
                }
            }
        }
    }

    private void removePixelView() {
        if (pixelView != null && animContentPane != null) {
            pixelView.setImage(null);
            pixelView.setOpacity(1);
            animContentPane.getChildren().remove(pixelView);
        }
        pixelView = null;
        sourceSnap = null;
        pixelatedSnap = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removePixelView();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removePixelView();
        super.dispose();
    }
}
