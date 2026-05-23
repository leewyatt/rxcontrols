package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.util.Duration;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXWaveProgressIndicator}. Renders a circular water
 * container, two layered wave surfaces, an optional outer ring, and a centre
 * label that shows both {@link RXWaveProgressIndicator#getGraphic()} and the
 * converted progress text (relative layout is controlled via
 * {@code -fx-content-display}).
 *
 * <p>Each wave surface is a <em>sum of sines</em>: several sinusoidal
 * components of different wavelength, amplitude and speed are added into one
 * height function. Because the components travel at different speeds they
 * drift in and out of phase, so individual crests genuinely rise and fall
 * over time instead of scrolling as one frozen shape. The surface is
 * re-evaluated every frame by a single {@link AnimationTimer} and written
 * into reusable {@link LineTo} nodes, so steady-state animation allocates
 * nothing.
 *
 * <p>The wave amplitude is not constant: an internal {@code displayedProgress}
 * lets the control's logical {@code progress} jump while the visible water
 * level tweens, and the surface briefly swells (slosh) while that level is
 * moving, settling back to the calm resting amplitude
 * ({@link RXWaveProgressIndicator#getWaveAmplitude()}) once it stabilises.
 *
 * <p>The indeterminate animation breathes {@code displayedProgress} between
 * {@code 0.35} and {@code 0.65}. The frame timer auto-stops whenever the host
 * window or any ancestor is hidden (via {@link TreeShowingProperty}) or when
 * the surface has nothing left to animate, and resumes on the next change.
 */
public class RXWaveProgressIndicatorSkin extends RXSkinBase<RXWaveProgressIndicator> {

    // ==================== Layout Constants ====================

    private static final double DEFAULT_PREF_SIZE = 80.0;
    private static final double DEFAULT_MIN_SIZE = 32.0;
    private static final double HALF = 0.5;
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Lower water-level bound for the indeterminate breathing.
     */
    private static final double INDETERMINATE_LOW = 0.35;

    /**
     * Upper water-level bound for the indeterminate breathing.
     */
    private static final double INDETERMINATE_HIGH = 0.65;

    /**
     * Mid-range water level used when the indeterminate animation is suppressed.
     */
    private static final double INDETERMINATE_REST = 0.5;

    /**
     * Spacing between wave-surface sample points, in pixels.
     */
    private static final double WAVE_SAMPLE_STEP = 3.0;

    /**
     * Lower bound on surface sample points, so tiny controls still curve smoothly.
     */
    private static final int MIN_WAVE_POINTS = 8;

    /**
     * Upper bound on one frame's time step — guards against a long stall after resume.
     */
    private static final double MAX_FRAME_SECONDS = 1.0 / 30.0;

    /**
     * Radians added to the back layer's component phases so the two layers never coincide.
     */
    private static final double BACK_WAVE_PHASE_OFFSET = Math.PI;

    /**
     * Back baseline lift above the front baseline, as a fraction of resting amplitude
     * (screen-space, smaller Y = higher), so the back wave shows as a permanent band.
     */
    private static final double BACK_BASELINE_LIFT_RATIO = 0.5;

    /**
     * Converts water-level speed (progress per second) into an amplitude-swell multiplier.
     */
    private static final double SLOSH_GAIN = 0.22;

    /**
     * Upper bound on the slosh multiplier — amplitude never exceeds {@code (1 + this)} x rest.
     */
    private static final double SLOSH_MAX_GAIN = 2.0;

    // ==================== Wave Model ====================

    /**
     * One sinusoidal component of a wave surface. Ratios are relative to the
     * base wavelength / base angular speed resolved at render time; weights
     * across all components sum to {@code 1}, so the summed offset stays in
     * {@code [-1, 1]}.
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
     * The components summed into each wave surface. Different speed ratios are
     * what make crests rise and fall: the components drift in and out of phase
     * over time. Weights sum to {@code 1}.
     */
    private static final WaveComponent[] WAVE_COMPONENTS = {
            new WaveComponent(1.00, 0.50, 1.00, 0.0),
            new WaveComponent(0.60, 0.32, 1.60, 1.3),
            new WaveComponent(0.38, 0.18, 0.70, 3.7),
    };

    /**
     * Reusable {@link MoveTo} / {@link LineTo} nodes for one wave-layer path.
     */
    private static final class WaveLayerNodes {
        final MoveTo start = new MoveTo();
        final LineTo[] surface;
        final LineTo bottomRight = new LineTo();
        final LineTo bottomLeft = new LineTo();

        WaveLayerNodes(int pointCount) {
            surface = new LineTo[pointCount - 1];
            for (int i = 0; i < surface.length; i++) {
                surface[i] = new LineTo();
            }
        }
    }

    // ==================== Nodes ====================

    private final Circle container = new Circle();
    private final Circle borderRing = new Circle();
    private final Circle clipCircle = new Circle();
    private final Group waveLayer = new Group();
    private final Path frontWavePath = new Path();
    private final Path backWavePath = new Path();
    private final Label progressLabel = new Label();

    // ==================== State ====================

    /**
     * Visible [0,1] progress — diverges from {@code control.progress} during tween / indeterminate.
     */
    private final DoubleProperty displayedProgress =
            new SimpleDoubleProperty(this, "displayedProgress", 0.0);

    private final TreeShowingProperty treeShowing;

    private Timeline progressTween;
    private Timeline indeterminateTimeline;
    private boolean indeterminateMode;

    /**
     * Per-frame driver for the sum-of-sines surface; started/stopped, never paused.
     */
    private final AnimationTimer waveTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            onWaveFrame(now);
        }
    };

    private boolean waveTimerRunning;
    private long lastFrameNanos = -1L;
    private double elapsedSeconds;
    private double lastDisplayedProgress;

    /**
     * Current slosh swell, as a fraction added on top of the resting amplitude.
     */
    private double sloshMultiplier;

    private WaveLayerNodes frontNodes;
    private WaveLayerNodes backNodes;
    private int wavePointCount;
    private double[] sampleX = new double[0];

    /**
     * Cached geometry used by the per-frame surface rebuild — skin-local coordinates.
     */
    private double cachedCenterX;
    private double cachedCenterY;
    private double cachedWaterRadius;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXWaveProgressIndicatorSkin(RXWaveProgressIndicator control) {
        super(control);

        initNodes(control);
        treeShowing = new TreeShowingProperty(control);
        disposer.registerDisposeTask(treeShowing::dispose);

        registerListeners(control);
        applyBorderWidth();
        applyCenterContent();

        double initial = control.getProgress();
        if (initial >= 0.0) {
            indeterminateMode = false;
            displayedProgress.set(RXMath.clamp0To1(initial));
        } else {
            displayedProgress.set(INDETERMINATE_REST);
            startIndeterminate();
        }
    }

    // ==================== Init ====================

    private void initNodes(RXWaveProgressIndicator control) {
        container.getStyleClass().add("wave-container");
        container.setManaged(false);
        container.setStroke(null);

        borderRing.getStyleClass().add("border-ring");
        borderRing.setManaged(false);
        borderRing.setFill(null);

        backWavePath.getStyleClass().add("back-wave");
        backWavePath.setStroke(null);
        backWavePath.setManaged(false);

        frontWavePath.getStyleClass().add("front-wave");
        frontWavePath.setStroke(null);
        frontWavePath.setManaged(false);

        waveLayer.getChildren().setAll(backWavePath, frontWavePath);
        waveLayer.setManaged(false);
        waveLayer.setMouseTransparent(true);
        // Clip is set on the Group: a Circle in skin-local coordinates that
        // matches the water container, so the paths never spill outside the
        // round container.
        waveLayer.setClip(clipCircle);
        disposer.registerDisposeTask(() -> waveLayer.setClip(null));

        progressLabel.getStyleClass().add("progress-label");
        progressLabel.setAlignment(Pos.CENTER);
        progressLabel.setMouseTransparent(true);
        // Clear the user-supplied graphic on dispose; unbind must run before
        // setGraphic(null), so both steps live in one task.
        disposer.registerDisposeTask(() -> {
            progressLabel.graphicProperty().unbind();
            progressLabel.setGraphic(null);
        });
        progressLabel.graphicProperty().bind(control.graphicProperty());
        disposer.registerBinding(progressLabel.visibleProperty(),
                control.graphicProperty().isNotNull()
                        .or(progressLabel.textProperty().isNotEmpty()));
        disposer.registerBinding(progressLabel.managedProperty(),
                progressLabel.visibleProperty());

        disposer.registerBinding(container.fillProperty(), control.containerFillProperty());
        disposer.registerBinding(frontWavePath.fillProperty(), control.frontWaveFillProperty());
        disposer.registerBinding(backWavePath.fillProperty(), control.backWaveFillProperty());
        disposer.registerBinding(borderRing.strokeProperty(), control.borderStrokeProperty());

        getChildren().setAll(container, waveLayer, borderRing, progressLabel);
    }

    private void registerListeners(RXWaveProgressIndicator control) {
        disposer.registerListener(control.progressProperty(), () -> {
            onProgressChanged(control.getProgress());
            applyCenterContent();
        });
        disposer.registerListener(control.textFactoryProperty(), this::applyCenterContent);

        // The frame timer reads displayedProgress directly; a change only needs
        // to make sure the timer is running so the motion is drawn.
        disposer.registerListener(displayedProgress, this::startWaveTimer);

        disposer.registerListener(control.waveAmplitudeProperty(), this::requestWaveAnimation);
        disposer.registerListener(control.waveLengthProperty(), this::requestWaveAnimation);
        disposer.registerListener(control.waveCycleDurationProperty(), this::requestWaveAnimation);
        disposer.registerListener(control.backWaveSpeedRatioProperty(), this::requestWaveAnimation);
        disposer.registerListener(control.backWaveAmplitudeRatioProperty(), this::requestWaveAnimation);

        disposer.registerListener(control.borderStrokeWidthProperty(), () -> {
            applyBorderWidth();
            control.requestLayout();
        });
        disposer.registerListener(control.borderPaddingProperty(), control::requestLayout);

        disposer.registerListener(control.indeterminateCycleDurationProperty(), () -> {
            if (indeterminateMode) {
                rebuildIndeterminateTimeline();
            }
        });

        disposer.registerListener(treeShowing, () -> onTreeShowingChanged(treeShowing.get()));
    }

    // ==================== Style application ====================

    private void applyBorderWidth() {
        borderRing.setStrokeWidth(RXMath.sanitizeNonNegative(getSkinnable().getBorderStrokeWidth()));
    }

    // ==================== Progress changes ====================

    private void onProgressChanged(double newProgress) {
        if (newProgress < 0.0) {
            startIndeterminate();
            return;
        }
        double target = RXMath.clamp0To1(newProgress);
        if (indeterminateMode) {
            stopIndeterminate();
        }
        stopProgressTween();

        Duration tweenDuration = getSkinnable().getProgressTransitionDuration();
        if (tweenDuration == null || tweenDuration.lessThanOrEqualTo(Duration.ZERO)) {
            displayedProgress.set(target);
        } else {
            progressTween = new Timeline(new KeyFrame(
                    tweenDuration,
                    new KeyValue(displayedProgress, target, Interpolator.EASE_OUT)
            ));
            progressTween.play();
        }
        startWaveTimer();
    }

    private void stopProgressTween() {
        if (progressTween != null) {
            progressTween.stop();
            progressTween = null;
        }
    }

    // ==================== Indeterminate ====================

    private void startIndeterminate() {
        stopProgressTween();
        indeterminateMode = true;
        rebuildIndeterminateTimeline();
        startWaveTimer();
    }

    private void stopIndeterminate() {
        indeterminateMode = false;
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        // displayedProgress is handed over to the tween in onProgressChanged;
        // do not snap it back here, or the determinate transition starts from
        // a discontinuous frame.
    }

    /**
     * Builds the indeterminate breathing timeline. {@code displayedProgress}
     * oscillates between {@link #INDETERMINATE_LOW} and
     * {@link #INDETERMINATE_HIGH} with an {@link Interpolator#EASE_BOTH} curve;
     * the wave surface keeps flowing independently via the frame timer.
     */
    private void rebuildIndeterminateTimeline() {
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        if (!indeterminateMode) {
            return;
        }

        Duration cycle = getSkinnable().getIndeterminateCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Suppress breathing and snap to a deterministic mid-range pose so
            // a stale frame from a previous animation cannot linger.
            displayedProgress.set(INDETERMINATE_REST);
            return;
        }
        Duration halfCycle = cycle.divide(2.0);

        indeterminateTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(displayedProgress, INDETERMINATE_LOW, Interpolator.EASE_BOTH)),
                new KeyFrame(halfCycle,
                        new KeyValue(displayedProgress, INDETERMINATE_HIGH, Interpolator.EASE_BOTH)),
                new KeyFrame(cycle,
                        new KeyValue(displayedProgress, INDETERMINATE_LOW, Interpolator.EASE_BOTH))
        );
        indeterminateTimeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            indeterminateTimeline.play();
        }
    }

    // ==================== Wave animation ====================

    /**
     * Per-frame callback: advances time, derives the slosh swell from the
     * water-level velocity, rebuilds the surface, then stops itself once
     * nothing is left to animate.
     */
    private void onWaveFrame(long now) {
        if (lastFrameNanos < 0L) {
            lastFrameNanos = now;
            return;
        }
        double dt = (now - lastFrameNanos) / 1.0e9;
        lastFrameNanos = now;
        if (dt <= 0.0) {
            return;
        }
        if (dt > MAX_FRAME_SECONDS) {
            dt = MAX_FRAME_SECONDS;
        }
        elapsedSeconds += dt;

        double progressNow = displayedProgress.get();
        double velocity = (progressNow - lastDisplayedProgress) / dt;
        lastDisplayedProgress = progressNow;
        double slosh = SLOSH_GAIN * Math.abs(velocity);
        sloshMultiplier = (slosh > SLOSH_MAX_GAIN) ? SLOSH_MAX_GAIN : slosh;

        updateWaveSurface();

        if (!isWaveActive()) {
            stopWaveTimer();
        }
    }

    private void startWaveTimer() {
        if (waveTimerRunning || !treeShowing.get()) {
            return;
        }
        // Re-prime so the first dt after a (re)start is a normal frame, and the
        // velocity baseline is the current level — no spurious slosh on resume.
        lastFrameNanos = -1L;
        lastDisplayedProgress = displayedProgress.get();
        waveTimerRunning = true;
        waveTimer.start();
    }

    private void stopWaveTimer() {
        if (!waveTimerRunning) {
            return;
        }
        waveTimerRunning = false;
        waveTimer.stop();
        sloshMultiplier = 0.0;
    }

    /**
     * Ensures the surface is current and the frame timer is running if it should be.
     */
    private void requestWaveAnimation() {
        startWaveTimer();
        updateWaveSurface();
    }

    /**
     * Whether the surface still has motion to render. The wave scroll runs
     * forever while enabled; otherwise the timer keeps going only while the
     * indeterminate breathing or a determinate tween is in progress.
     */
    private boolean isWaveActive() {
        return indeterminateMode
                || scrollEnabled()
                || (progressTween != null
                && progressTween.getStatus() == Animation.Status.RUNNING);
    }

    private boolean scrollEnabled() {
        Duration cycle = getSkinnable().getWaveCycleDuration();
        return cycle != null && cycle.greaterThan(Duration.ZERO);
    }

    private void onTreeShowingChanged(boolean showing) {
        if (showing) {
            startWaveTimer();
        } else {
            stopWaveTimer();
        }
        if (indeterminateMode && indeterminateTimeline != null) {
            if (showing) {
                indeterminateTimeline.play();
            } else {
                indeterminateTimeline.pause();
            }
        }
    }

    // ==================== Wave geometry & surface ====================

    /**
     * Resolves the base wavelength. A value of {@code 0}, negative or
     * {@code NaN} falls back to the container diameter; the shorter layered
     * components (see {@link #WAVE_COMPONENTS}) then add the finer ripples.
     */
    private double resolveWaveLength() {
        double declared = getSkinnable().getWaveLength();
        if (Double.isNaN(declared) || declared <= 0.0) {
            return Math.max(0.0, cachedWaterRadius * 2.0);
        }
        return declared;
    }

    private double resolveBaseOmega() {
        Duration cycle = getSkinnable().getWaveCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            return 0.0;
        }
        return TWO_PI / (cycle.toMillis() / 1000.0);
    }

    private double resolveBackSpeedRatio() {
        double ratio = getSkinnable().getBackWaveSpeedRatio();
        return (Double.isNaN(ratio) || ratio <= 0.0) ? 1.0 : ratio;
    }

    /**
     * (Re)builds the reusable path nodes for the current size. The element
     * lists are rebuilt only when the sample-point count changes; on every
     * call the fixed x-coordinates (and the sealed bottom edge) are refreshed,
     * since the centre / radius move on layout.
     */
    private void ensureWaveGeometry() {
        double radius = cachedWaterRadius;
        if (radius <= 0.0) {
            frontWavePath.getElements().clear();
            backWavePath.getElements().clear();
            frontNodes = null;
            backNodes = null;
            wavePointCount = 0;
            return;
        }

        int n = Math.max(MIN_WAVE_POINTS,
                (int) Math.ceil((radius * 2.0) / WAVE_SAMPLE_STEP) + 1);
        if (n != wavePointCount) {
            sampleX = new double[n];
            frontNodes = buildLayerNodes(frontWavePath, n);
            backNodes = buildLayerNodes(backWavePath, n);
            wavePointCount = n;
        }

        double leftX = cachedCenterX - radius;
        double span = radius * 2.0;
        for (int i = 0; i < n; i++) {
            sampleX[i] = leftX + span * ((double) i / (n - 1));
        }
        // Keep the path bottom strictly below the clip so anti-aliasing along
        // the lower edge does not leave a single-pixel transparent seam.
        double sealedBottom = cachedCenterY + radius + 1.0;
        applyLayerX(frontNodes, sealedBottom);
        applyLayerX(backNodes, sealedBottom);
    }

    private void applyLayerX(WaveLayerNodes nodes, double sealedBottom) {
        nodes.start.setX(sampleX[0]);
        for (int i = 1; i < wavePointCount; i++) {
            nodes.surface[i - 1].setX(sampleX[i]);
        }
        nodes.bottomRight.setX(sampleX[wavePointCount - 1]);
        nodes.bottomRight.setY(sealedBottom);
        nodes.bottomLeft.setX(sampleX[0]);
        nodes.bottomLeft.setY(sealedBottom);
    }

    private static WaveLayerNodes buildLayerNodes(Path path, int n) {
        WaveLayerNodes nodes = new WaveLayerNodes(n);
        List<PathElement> elements = new ArrayList<>(n + 3);
        elements.add(nodes.start);
        for (int i = 0; i < n - 1; i++) {
            elements.add(nodes.surface[i]);
        }
        elements.add(nodes.bottomRight);
        elements.add(nodes.bottomLeft);
        elements.add(new ClosePath());
        path.getElements().setAll(elements);
        return nodes;
    }

    /**
     * Recomputes both wave surfaces for the current frame: the water level
     * from {@code displayedProgress}, the amplitude from the resting amplitude
     * scaled by the slosh swell, and each surface point from the sum of sine
     * components.
     */
    private void updateWaveSurface() {
        int n = wavePointCount;
        if (n <= 0) {
            return;
        }
        RXWaveProgressIndicator control = getSkinnable();
        double radius = cachedWaterRadius;
        double bottomY = cachedCenterY + radius;
        double sealedBottom = bottomY + 1.0;

        double level = RXMath.clamp0To1(displayedProgress.get());
        double waterDepth = level * (radius * 2.0);
        double baseline = bottomY - waterDepth;

        double restAmplitude = RXMath.sanitizeNonNegative(control.getWaveAmplitude());
        double frontAmplitude = restAmplitude * (1.0 + sloshMultiplier);
        double backAmplitude = frontAmplitude * RXMath.sanitizeNonNegative(control.getBackWaveAmplitudeRatio());

        // Clamp by water depth so level=0 doesn't leave a back-wave sliver at the bottom.
        double backLift = Math.min(restAmplitude * BACK_BASELINE_LIFT_RATIO, waterDepth);
        double backBaseline = baseline - backLift;

        double lambda = resolveWaveLength();
        double baseOmega = resolveBaseOmega();
        double backOmega = baseOmega / resolveBackSpeedRatio();

        writeLayerSurface(frontNodes, baseline, frontAmplitude, lambda, baseOmega, 0.0, sealedBottom);
        writeLayerSurface(backNodes, backBaseline, backAmplitude, lambda, backOmega,
                BACK_WAVE_PHASE_OFFSET, sealedBottom);
    }

    /**
     * Writes one layer's surface y-coordinates. The surface height at x is
     * {@code baseline - amplitude * Σ wᵢ·sin(kᵢ·x + ωᵢ·t + φᵢ)}; the clip
     * circle trims whatever extends past the round container.
     */
    private void writeLayerSurface(WaveLayerNodes nodes, double baseline, double amplitude,
                                   double lambda, double layerOmega, double layerPhase,
                                   double sealedBottom) {
        double t = elapsedSeconds;
        for (int i = 0; i < wavePointCount; i++) {
            double x = sampleX[i];
            double offset = 0.0;
            for (WaveComponent c : WAVE_COMPONENTS) {
                double k = TWO_PI / (lambda * c.wavelengthRatio);
                offset += c.weight
                        * Math.sin(k * x + layerOmega * c.speedRatio * t + c.phase + layerPhase);
            }
            double y = baseline - amplitude * offset;
            // Never let the surface dip below the sealed bottom, or the closing
            // edge would fold the fill region inside-out.
            if (y > sealedBottom) {
                y = sealedBottom;
            }
            if (i == 0) {
                nodes.start.setY(y);
            } else {
                nodes.surface[i - 1].setY(y);
            }
        }
    }

    // ==================== Centre content ====================

    private void applyCenterContent() {
        progressLabel.setText(formatLabel(getSkinnable().getProgress()));
    }

    private String formatLabel(double progress) {
        Callback<Double, String> textFactory = getSkinnable().getTextFactory();
        if (textFactory == null) {
            textFactory = RXWaveProgressIndicator.DEFAULT_TEXT_FACTORY;
        }
        return textFactory.call(progress);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        double size = Math.min(contentWidth, contentHeight);
        if (size <= 0.0) {
            // JavaFX does not auto-clip children; collapse everything so a
            // previous frame's geometry cannot leak outside the now-zero
            // content area.
            container.setRadius(0.0);
            borderRing.setRadius(0.0);
            clipCircle.setRadius(0.0);
            cachedWaterRadius = 0.0;
            cachedCenterX = contentX;
            cachedCenterY = contentY;
            ensureWaveGeometry();
            progressLabel.resizeRelocate(contentX, contentY, 0.0, 0.0);
            return;
        }

        RXWaveProgressIndicator control = getSkinnable();
        double border = RXMath.sanitizeNonNegative(control.getBorderStrokeWidth());
        double padding = RXMath.sanitizeNonNegative(control.getBorderPadding());

        double waterDiameter = Math.max(0.0, size - 2.0 * (border + padding));
        double offsetX = contentX + (contentWidth - size) * HALF;
        double offsetY = contentY + (contentHeight - size) * HALF;
        double centerX = offsetX + size * HALF;
        double centerY = offsetY + size * HALF;
        double waterRadius = waterDiameter * HALF;

        container.setCenterX(centerX);
        container.setCenterY(centerY);
        container.setRadius(waterRadius);

        // Stroke is rendered centred on the radius, so the visible outer edge
        // sits at waterRadius + padding + border, and the inner edge at
        // waterRadius + padding.
        borderRing.setCenterX(centerX);
        borderRing.setCenterY(centerY);
        borderRing.setRadius(waterRadius + padding + border * HALF);

        clipCircle.setCenterX(centerX);
        clipCircle.setCenterY(centerY);
        clipCircle.setRadius(waterRadius);

        cachedCenterX = centerX;
        cachedCenterY = centerY;
        cachedWaterRadius = waterRadius;

        ensureWaveGeometry();
        requestWaveAnimation();
        layoutLabel(centerX, centerY, waterRadius);
    }

    private void layoutLabel(double centerX, double centerY, double waterRadius) {
        double innerDiameter = Math.max(0.0, waterRadius * 2.0);
        double labelWidth = Math.min(progressLabel.prefWidth(innerDiameter), innerDiameter);
        double labelHeight = Math.min(progressLabel.prefHeight(labelWidth), innerDiameter);
        progressLabel.resizeRelocate(
                centerX - labelWidth * HALF,
                centerY - labelHeight * HALF,
                labelWidth,
                labelHeight);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + DEFAULT_MIN_SIZE + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + DEFAULT_MIN_SIZE + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_SIZE + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_SIZE + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        // The frame timer and the two timelines are not managed by the embedded
        // SkinDisposer; stop them explicitly. Listeners, bindings, clip and
        // treeShowing teardown are handled by RXSkinBase.dispose().
        stopProgressTween();
        stopWaveTimer();
        if (indeterminateTimeline != null) {
            indeterminateTimeline.stop();
            indeterminateTimeline = null;
        }
        super.dispose();
    }

}
