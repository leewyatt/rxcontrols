package io.github.leewyatt.rxcontrols.spectrum;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.skins.RXSkinBase;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

import java.util.Arrays;

/**
 * Default skin for {@link RXAudioSpectrum}. Owns the shared infrastructure —
 * a single {@link Canvas}, a single {@link AnimationTimer}, the data pipeline
 * (max-per-bucket resampling, dB normalization, {@link BandLayout}
 * permutation, asymmetric attack/release smoothing, peak hold-then-fall), and
 * the lifecycle (idle self-suspension, tree-showing pause, dispose) — while
 * delegating all drawing to the control's {@link SpectrumVisualization}.
 *
 * <p>Pipeline cadence: bucketing/normalization/permutation run only when data
 * arrives or a related property changes (~10 Hz); smoothing and rendering run
 * per animation frame. The skin's own frame code allocates nothing — the only
 * per-frame allocation is the small {@code State} that {@code GraphicsContext}
 * pushes for the save/restore pair that isolates visualization state, an
 * accepted cost of that isolation.
 */
public class AudioSpectrumSkin extends RXSkinBase<RXAudioSpectrum> {

    // ==================== Constants ====================

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private static final double REF_FRAME_SECONDS = 1.0 / 60.0;

    /**
     * Fixed retention factor for the rising edge; lower than any sensible
     * release smoothing so bars jump up fast and fall slowly.
     */
    private static final double ATTACK_SMOOTHING = 0.4;

    private static final double MIN_FRAME_SECONDS = 1.0 / 240.0;

    private static final double MAX_FRAME_SECONDS = 1.0 / 20.0;

    private static final double MIN_DB_RANGE_EPS = 1.0e-6;

    private static final int PEAK_HOLD_FRAMES = 30;

    private static final double PEAK_FALL_PER_FRAME = 0.015;

    /**
     * Frames without fresh data before incoming silence is assumed and the
     * targets start sinking toward the suspended baseline.
     */
    private static final int IDLE_GRACE_FRAMES = 45;

    private static final double SETTLE_EPS = 0.001;

    private static final double MIN_VISUAL_WIDTH = 40.0;

    private static final double MIN_VISUAL_HEIGHT = 20.0;

    private static final double DEFAULT_PREF_WIDTH = 256.0;

    private static final double DEFAULT_PREF_HEIGHT = 80.0;

    private static final Color GLOW_COLOR = Color.web("#9be8ff", 0.7);

    private static final double GLOW_RADIUS = 12.0;

    private static final double GLOW_SPREAD = 0.25;

    // ==================== State ====================

    private final Canvas canvas;

    private final SpectrumContext context = new SpectrumContext();

    private final ReadOnlyBooleanProperty treeShowing;

    private AnimationTimer timer;

    private boolean timerRunning;

    /** Set whenever the timer (re)starts so the first frame seeds {@code lastNow} and skips easing. */
    private boolean reseedPending;

    private long lastNow;

    /** Grow-only snapshot of the latest raw frame, kept so bandCount /
     * bandLayout / minDecibels changes can rebuild targets while idle. */
    private float[] raw = new float[0];

    private int rawCount;

    private float[] bucketBuf;

    private double[] target;

    private double[] displayed;

    private double[] peak;

    private int[] holdFrames;

    private int idleFrames;

    /** True once the display settled to silence and the timer stopped itself. */
    private boolean idleSuspended = true;

    private SpectrumVisualization fallbackVisualization;

    private DropShadow glowEffect;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public AudioSpectrumSkin(RXAudioSpectrum control) {
        super(control);

        canvas = new Canvas();
        canvas.getStyleClass().add("canvas");
        canvas.setMouseTransparent(true);
        // setAll: take ownership of the children, replacing any predecessor
        // skin's canvas (Control replaces skins new-constructor-first).
        getChildren().setAll(canvas);

        treeShowing = controlTreeShowingProperty();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(now);
            }
        };

        reallocBuffers();
        registerListeners(control);
        updateGlow();

        if (control.getMagnitudeCount() > 0) {
            // Data was fed before the skin attached (e.g. skin churn mid-play).
            onSpectrumUpdate();
        }
    }

    // ==================== Listeners ====================

    private void registerListeners(RXAudioSpectrum control) {
        disposer.registerListener(control.updateSequenceProperty(), this::onSpectrumUpdate);

        disposer.registerListener(control.bandCountProperty(), () -> {
            reallocBuffers();
            rebuildTarget();
            redrawOnce();
        });
        disposer.registerListener(control.minDecibelsProperty(), () -> {
            rebuildTarget();
            redrawOnce();
        });
        disposer.registerListener(control.bandLayoutProperty(), () -> {
            rebuildTarget();
            redrawOnce();
        });

        disposer.registerListener(control.visualizationProperty(),
                (obs, oldVisualization, newVisualization) -> {
                    if (oldVisualization != null) {
                        oldVisualization.dispose();
                    }
                    redrawOnce();
                });

        // Running frames read these through the context every frame; the
        // listener only exists to repaint the suspended state.
        disposer.registerListener(control.barFillProperty(), this::redrawOnce);
        disposer.registerListener(control.peakFillProperty(), this::redrawOnce);
        disposer.registerListener(control.barGapRatioProperty(), this::redrawOnce);
        disposer.registerListener(control.showPeaksProperty(), this::redrawOnce);
        disposer.registerListener(control.smoothingProperty(), this::redrawOnce);

        disposer.registerListener(control.glowProperty(), this::updateGlow);

        disposer.registerListener(treeShowing, () -> {
            if (treeShowing.get()) {
                if (!idleSuspended) {
                    startTimer();
                }
            } else {
                stopTimer();
            }
        });
    }

    // ==================== Data Pipeline ====================

    private void onSpectrumUpdate() {
        RXAudioSpectrum control = getSkinnable();
        int count = control.getMagnitudeCount();
        if (count > raw.length) {
            raw = new float[count];
        }
        rawCount = control.copyMagnitudesTo(raw);
        rebuildTarget();
        idleFrames = 0;
        idleSuspended = false;
        control.setActive(true);
        if (treeShowing.get()) {
            startTimer();
        }
    }

    private void reallocBuffers() {
        int n = bandCountSafe();
        if (target != null && target.length == n) {
            return;
        }
        target = target == null ? new double[n] : Arrays.copyOf(target, n);
        displayed = displayed == null ? new double[n] : Arrays.copyOf(displayed, n);
        peak = peak == null ? new double[n] : Arrays.copyOf(peak, n);
        holdFrames = holdFrames == null ? new int[n] : Arrays.copyOf(holdFrames, n);
        bucketBuf = new float[n];
    }

    private void rebuildTarget() {
        int n = target.length;
        if (rawCount <= 0) {
            Arrays.fill(target, 0.0);
            return;
        }
        bucketMax(raw, rawCount, bucketBuf);
        double minDecibels = getSkinnable().getMinDecibels();
        BandLayout layout = bandLayoutOrDefault();
        for (int i = 0; i < n; i++) {
            target[i] = normalizedLevel(bucketBuf[layout.sourceIndex(i, n)], minDecibels);
        }
    }

    /**
     * Max-per-bucket resampling of {@code rawCount} source samples onto
     * {@code buckets.length} slots. Taking the bucket maximum (rather than the
     * mean) preserves narrow transient peaks. When upsampling, each slot
     * repeats its nearest source sample.
     *
     * @param raw      the source samples
     * @param rawCount the number of valid samples in {@code raw}
     * @param buckets  the output slots
     */
    static void bucketMax(float[] raw, int rawCount, float[] buckets) {
        int n = buckets.length;
        if (rawCount == n) {
            System.arraycopy(raw, 0, buckets, 0, n);
            return;
        }
        for (int j = 0; j < n; j++) {
            int lo = (int) ((long) j * rawCount / n);
            int hi = (int) ((long) (j + 1) * rawCount / n);
            if (hi <= lo) {
                hi = lo + 1;
            }
            float max = raw[lo];
            for (int k = lo + 1; k < hi; k++) {
                if (raw[k] > max) {
                    max = raw[k];
                }
            }
            buckets[j] = max;
        }
    }

    /**
     * Normalizes a dB magnitude into an amplitude level. The divisor guard and
     * the NaN/Infinity-to-zero rule keep one bad frame from killing the
     * animation loop — a deliberate resilience choice local to this call site.
     *
     * @param magnitude   the raw magnitude in dB (non-positive by convention)
     * @param minDecibels the lower bound of the decibel window
     * @return the level in {@code [0, 1]}; {@code 0} for NaN/Infinity
     */
    static double normalizedLevel(double magnitude, double minDecibels) {
        double range = Math.max(0.0 - minDecibels, MIN_DB_RANGE_EPS);
        double level = (magnitude - minDecibels) / range;
        if (Double.isNaN(level) || Double.isInfinite(level)) {
            return 0.0;
        }
        return RXMath.clamp0To1(level);
    }

    // ==================== Frame Loop ====================

    private void onFrame(long now) {
        double dt;
        if (reseedPending) {
            // First frame after (re)start: seed lastNow and skip easing so a
            // huge wall-clock gap cannot snap or freeze the bars.
            reseedPending = false;
            dt = 0.0;
        } else {
            dt = RXMath.clamp((now - lastNow) / NANOS_PER_SECOND,
                    MIN_FRAME_SECONDS, MAX_FRAME_SECONDS);
        }
        lastNow = now;

        idleFrames++;
        if (idleFrames > IDLE_GRACE_FRAMES) {
            // No fresh data for the whole grace window: assume silence so the
            // display sinks to baseline instead of freezing mid-pose.
            Arrays.fill(target, 0.0);
        }
        if (dt > 0.0) {
            stepSmoothing(dt);
            stepPeaks();
        }
        renderFrame(dt);
        maybeSuspend();
    }

    private void stepSmoothing(double dt) {
        // Frame-rate-invariant EMA: `smoothing` is the per-reference-frame
        // retention factor, so coef = 1 - smoothing^(dt/ref) keeps the decay
        // identical at 30 and 60 fps, and smoothing = 0 releases instantly.
        double dtRatio = dt / REF_FRAME_SECONDS;
        double releaseCoef = 1.0 - Math.pow(smoothingOrDefault(), dtRatio);
        double attackCoef = 1.0 - Math.pow(ATTACK_SMOOTHING, dtRatio);
        for (int i = 0; i < displayed.length; i++) {
            double goal = target[i];
            double shown = displayed[i];
            displayed[i] = shown + (goal - shown) * (goal > shown ? attackCoef : releaseCoef);
        }
    }

    private void stepPeaks() {
        for (int i = 0; i < peak.length; i++) {
            if (displayed[i] > peak[i]) {
                peak[i] = displayed[i];
                holdFrames[i] = PEAK_HOLD_FRAMES;
            } else if (holdFrames[i] > 0) {
                holdFrames[i]--;
            } else {
                peak[i] = Math.max(0.0, peak[i] - PEAK_FALL_PER_FRAME);
            }
        }
    }

    private void renderFrame(double dt) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        RXAudioSpectrum control = getSkinnable();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        context.update(gc, width, height, target.length, displayed, peak,
                control.isShowPeaks(), control.getBarFill(), control.getPeakFill(),
                control.getBarGapRatio(), dt);
        gc.save();
        gc.clearRect(0.0, 0.0, width, height);
        visualizationOrDefault().render(context);
        gc.restore();
    }

    /**
     * Repaints the current state outside the running loop. While the timer
     * runs this is a no-op (the next frame picks the change up); while
     * suspended it renders once synchronously — easing skipped — so property
     * changes are reflected immediately without restarting the loop.
     */
    private void redrawOnce() {
        if (timerRunning) {
            return;
        }
        renderFrame(0.0);
    }

    private void maybeSuspend() {
        if (idleFrames <= IDLE_GRACE_FRAMES || !isSettled()) {
            return;
        }
        idleSuspended = true;
        // Flip :silent before stopping so styling reacts ahead of the freeze.
        getSkinnable().setActive(false);
        stopTimer();
    }

    private boolean isSettled() {
        for (int i = 0; i < displayed.length; i++) {
            if (displayed[i] >= SETTLE_EPS || peak[i] >= SETTLE_EPS) {
                return false;
            }
        }
        return true;
    }

    private void startTimer() {
        if (timerRunning || timer == null) {
            return;
        }
        reseedPending = true;
        timer.start();
        timerRunning = true;
    }

    private void stopTimer() {
        if (!timerRunning || timer == null) {
            return;
        }
        timer.stop();
        timerRunning = false;
    }

    // ==================== Use-Site Defaults ====================

    private int bandCountSafe() {
        return Math.max(RXAudioSpectrum.MIN_BAND_COUNT, getSkinnable().getBandCount());
    }

    private BandLayout bandLayoutOrDefault() {
        BandLayout layout = getSkinnable().getBandLayout();
        return layout == null ? RXAudioSpectrum.DEFAULT_BAND_LAYOUT : layout;
    }

    private double smoothingOrDefault() {
        double value = getSkinnable().getSmoothing();
        if (Double.isNaN(value)) {
            return RXAudioSpectrum.DEFAULT_SMOOTHING;
        }
        return RXMath.clamp(value, 0.0, RXAudioSpectrum.MAX_SMOOTHING);
    }

    private SpectrumVisualization visualizationOrDefault() {
        SpectrumVisualization visualization = getSkinnable().getVisualization();
        if (visualization != null) {
            return visualization;
        }
        if (fallbackVisualization == null) {
            // Lazily built per skin; a shared static instance is impossible
            // because visualizations carry per-host geometry caches.
            fallbackVisualization = new VisBars();
        }
        return fallbackVisualization;
    }

    private void updateGlow() {
        if (getSkinnable().isGlow()) {
            if (glowEffect == null) {
                glowEffect = new DropShadow(BlurType.GAUSSIAN, GLOW_COLOR, GLOW_RADIUS, GLOW_SPREAD, 0.0, 0.0);
            }
            canvas.setEffect(glowEffect);
        } else {
            canvas.setEffect(null);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        canvas.setWidth(w);
        canvas.setHeight(h);
        canvas.relocate(x, y);
        // Resizing clears the canvas; repaint immediately (not next frame) so
        // live resize never flashes a blank frame.
        renderFrame(0.0);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + MIN_VISUAL_WIDTH + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + MIN_VISUAL_HEIGHT + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_HEIGHT + bottomInset;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        RXAudioSpectrum control = getSkinnable();
        if (control == null) {
            // Already disposed; JavaFX 17's Control.setSkin(null) disposes the
            // outgoing skin even when application code disposed it first.
            return;
        }
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        timerRunning = false;
        SpectrumVisualization visualization = control.getVisualization();
        if (visualization != null) {
            visualization.dispose();
        }
        if (fallbackVisualization != null) {
            fallbackVisualization.dispose();
            fallbackVisualization = null;
        }
        canvas.setEffect(null);
        getChildren().remove(canvas);
        target = null;
        displayed = null;
        peak = null;
        holdFrames = null;
    }
}
