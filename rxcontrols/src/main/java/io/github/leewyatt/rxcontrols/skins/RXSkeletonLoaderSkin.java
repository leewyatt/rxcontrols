package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSkeletonLoader;
import io.github.leewyatt.rxcontrols.RXSkeletonLoader.Shape;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSkeletonLoader}. Renders the base block driven by
 * the control's {@link Shape}, overlays a translucent gradient band, and
 * scrolls that band horizontally on an indefinite {@link Timeline}.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>The shimmer band is a single {@link Rectangle} translated along the
 *       x-axis; the gradient stops do not change per frame, so the cost per
 *       frame is a pure affine transform.</li>
 *   <li>The band moves inside a fixed viewport clipped by rectangles computed
 *       from the same geometry as the base layer — this prevents the gradient
 *       from spilling into rounded corners or text-line gaps.</li>
 *   <li>{@link TreeShowingProperty} auto-pauses the scroll when the loader is
 *       detached, hidden, or hosted by a hidden window — see
 *       {@code AGENTS.md} §3.1.</li>
 *   <li>{@code maxWidth} / {@code maxHeight} report {@link Double#MAX_VALUE},
 *       so the loader stretches inside grow-priority containers — the
 *       deliberate opposite of {@link RXCircularProgressIndicatorSkin} /
 *       {@link RXWaveProgressIndicatorSkin}, which lock {@code max == pref}
 *       to keep spinners square.</li>
 * </ul>
 */
public class RXSkeletonLoaderSkin extends RXSkinBase<RXSkeletonLoader> {

    // ==================== Layout Constants ====================

    /**
     * Pref size used when neither the user nor the parent container imposes a
     * size. Picked to match a typical "title line" placeholder so a control
     * dropped into a free-form parent at least shows up.
     */
    private static final double DEFAULT_PREF_WIDTH = 120.0;
    private static final double DEFAULT_PREF_HEIGHT = 16.0;

    private static final double HALF = 0.5;
    private static final double FULL_PERCENT = 100.0;

    // ==================== Nodes ====================

    /**
     * Base layer drawn under the shimmer. The same computed blocks also drive
     * the clip layer, keeping the base and shimmer footprint in sync.
     */
    private final Group baseLayer = new Group();
    private final Group shimmerViewport = new Group();
    private final Group clipLayer = new Group();

    private final Rectangle shimmerBand = new Rectangle();

    private final TreeShowingProperty treeShowing;

    private Timeline shimmerTimeline;

    /** Cached geometry kept so the timeline can be rebuilt without re-querying layout. */
    private double cachedContentWidth;
    private double cachedBandWidth;

    /**
     * Snapshot of the timeline-inducing parameters from the last rebuild.
     * Used to short-circuit layout-driven rebuilds when nothing material has
     * changed, so the band's {@code translateX} is not reset on every resize.
     */
    private double timelineBandWidth = Double.NaN;
    private double timelineSpan = Double.NaN;
    private Duration timelineCycle;

    /**
     * Constructs a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXSkeletonLoaderSkin(RXSkeletonLoader control) {
        super(control);

        initNodes();
        treeShowing = new TreeShowingProperty(control);
        disposer.registerDisposeTask(treeShowing::dispose);

        registerListeners(control);
        applyBaseFill();
        applyShimmerFill();
    }

    // ==================== Init ====================

    private void initNodes() {
        baseLayer.setManaged(false);
        baseLayer.setMouseTransparent(true);

        shimmerViewport.setManaged(false);
        shimmerViewport.setMouseTransparent(true);
        shimmerViewport.setClip(clipLayer);
        disposer.registerDisposeTask(() -> shimmerViewport.setClip(null));

        clipLayer.setManaged(false);
        clipLayer.setMouseTransparent(true);

        shimmerBand.getStyleClass().add("shimmer-band");
        shimmerBand.setManaged(false);
        shimmerBand.setMouseTransparent(true);

        shimmerViewport.getChildren().setAll(shimmerBand);
        getChildren().setAll(baseLayer, shimmerViewport);
    }

    private void registerListeners(RXSkeletonLoader control) {
        disposer.registerListener(control.variantProperty(), control::requestLayout);
        disposer.registerListener(control.cornerRadiusProperty(), control::requestLayout);

        disposer.registerListener(control.baseColorProperty(), this::applyBaseFill);
        disposer.registerListener(control.shimmerColorProperty(), this::applyShimmerFill);

        disposer.registerListener(control.cycleDurationProperty(), this::rebuildShimmerTimeline);
        disposer.registerListener(control.shimmerWidthRatioProperty(), control::requestLayout);

        disposer.registerListener(control.lineCountProperty(), control::requestLayout);
        disposer.registerListener(control.lineHeightProperty(), control::requestLayout);
        disposer.registerListener(control.lineSpacingProperty(), control::requestLayout);
        disposer.registerListener(control.lastLineFillPercentProperty(), control::requestLayout);

        disposer.registerListener(treeShowing, () -> onTreeShowingChanged(treeShowing.get()));
    }

    // ==================== Style application ====================

    private void applyBaseFill() {
        Paint p = paintOrDefault(getSkinnable().getBaseColor(), RXSkeletonLoader.DEFAULT_BASE_COLOR);
        for (Node n : baseLayer.getChildren()) {
            if (n instanceof Rectangle r) {
                r.setFill(p);
            }
        }
    }

    private void applyShimmerFill() {
        // Recompute the gradient stops; the geometry is set during
        // layoutChildren so we only refresh the paint here.
        shimmerBand.setFill(buildShimmerGradient());
    }

    private LinearGradient buildShimmerGradient() {
        Paint raw = getSkinnable().getShimmerColor();
        Color stopColor = (raw instanceof Color c)
                ? c
                : (RXSkeletonLoader.DEFAULT_SHIMMER_COLOR instanceof Color dc ? dc : Color.WHITE);
        Color edge = new Color(stopColor.getRed(), stopColor.getGreen(), stopColor.getBlue(), 0.0);
        return new LinearGradient(0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, edge),
                new Stop(HALF, stopColor),
                new Stop(1.0, edge));
    }

    // ==================== Shimmer timeline ====================

    /**
     * Rebuilds the horizontal scroll timeline. Animates the band's
     * {@code translateX} from {@code -bandWidth} to {@code contentWidth} on an
     * {@link Animation#INDEFINITE} loop so the band always enters from the
     * left edge and exits on the right.
     *
     * <p>{@code cycleDuration <= 0} or {@code null} disables the animation per
     * AGENTS.md §3.6 — and resets the band to a deterministic off-screen pose
     * so a stale frame from a previous animation cannot linger (§1.8).
     */
    private void rebuildShimmerTimeline() {
        Duration cycle = getSkinnable().getCycleDuration();
        boolean disabled = cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO);
        double bandWidth = cachedBandWidth;
        double span = cachedContentWidth + bandWidth;

        if (disabled || bandWidth <= 0.0 || cachedContentWidth <= 0.0) {
            stopAndClearTimeline();
            // Park the band off-screen so the disabled state shows nothing,
            // overriding any leftover translateX from a previous animation.
            shimmerBand.setTranslateX(-bandWidth);
            timelineBandWidth = Double.NaN;
            timelineSpan = Double.NaN;
            timelineCycle = null;
            return;
        }

        // Skip rebuild when nothing material changed — a layout-driven call
        // with identical band / span / cycle would otherwise stop the running
        // timeline and snap translateX, freezing the scroll visually.
        boolean unchanged = bandWidth == timelineBandWidth
                && span == timelineSpan
                && durationEquals(cycle, timelineCycle)
                && shimmerTimeline != null;
        if (unchanged) {
            return;
        }

        stopAndClearTimeline();
        shimmerTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(shimmerBand.translateXProperty(),
                                -bandWidth, Interpolator.LINEAR)),
                new KeyFrame(cycle,
                        new KeyValue(shimmerBand.translateXProperty(),
                                cachedContentWidth, Interpolator.LINEAR))
        );
        shimmerTimeline.setCycleCount(Animation.INDEFINITE);
        if (treeShowing.get()) {
            shimmerTimeline.play();
        }

        timelineBandWidth = bandWidth;
        timelineSpan = span;
        timelineCycle = cycle;
    }

    private void stopAndClearTimeline() {
        if (shimmerTimeline != null) {
            shimmerTimeline.stop();
            shimmerTimeline = null;
        }
    }

    private static boolean durationEquals(Duration a, Duration b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.toMillis() == b.toMillis();
    }

    // ==================== tree-showing pause ====================

    private void onTreeShowingChanged(boolean showing) {
        if (shimmerTimeline == null) {
            return;
        }
        if (showing) {
            shimmerTimeline.play();
        } else {
            shimmerTimeline.pause();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        cachedContentWidth = contentWidth;

        if (contentWidth <= 0.0 || contentHeight <= 0.0) {
            collapseAll();
            return;
        }

        List<Block> blocks = computeBlocks(getSkinnable().getVariant(), contentWidth, contentHeight);
        syncLayer(baseLayer, blocks, contentX, contentY,
                paintOrDefault(getSkinnable().getBaseColor(), RXSkeletonLoader.DEFAULT_BASE_COLOR));
        syncLayer(clipLayer, blocks, 0.0, 0.0, Color.BLACK);

        layoutShimmer(contentX, contentY, contentWidth, contentHeight);
        rebuildShimmerTimeline();
    }

    private void collapseAll() {
        baseLayer.getChildren().clear();
        clipLayer.getChildren().clear();
        positionShimmerViewport(0.0, 0.0);
        shimmerBand.setWidth(0.0);
        shimmerBand.setHeight(0.0);
        cachedBandWidth = 0.0;
        rebuildShimmerTimeline();
    }

    private List<Block> computeBlocks(Shape shape, double cw, double ch) {
        List<Block> blocks = new ArrayList<>();
        switch (shape) {
            case CIRCLE -> {
                double diameter = Math.min(cw, ch);
                double offsetX = (cw - diameter) * HALF;
                double offsetY = (ch - diameter) * HALF;
                blocks.add(new Block(offsetX, offsetY, diameter, diameter, diameter, diameter));
            }
            case TEXT_LINE -> {
                double lineHeight = RXMath.sanitizeNonNegative(getSkinnable().getLineHeight());
                double lineSpacing = RXMath.sanitizeNonNegative(getSkinnable().getLineSpacing());
                double lastPercentSource = RXMath.sanitizeNonNegative(getSkinnable().getLastLineFillPercent());
                double lastPercent = RXMath.clamp(lastPercentSource, 0.0, FULL_PERCENT);
                int lineCount = Math.max(1, getSkinnable().getLineCount());
                double radius = lineHeight * HALF;
                for (int i = 0; i < lineCount; i++) {
                    double y = i * (lineHeight + lineSpacing);
                    double width = cw;
                    if (i == lineCount - 1 && lineCount > 1) {
                        width = cw * lastPercent / FULL_PERCENT;
                    }
                    blocks.add(new Block(0.0, y, width, lineHeight, radius * 2.0, radius * 2.0));
                }
            }
            case ROUNDED_RECT -> {
                double radius = RXMath.sanitizeNonNegative(getSkinnable().getCornerRadius());
                blocks.add(new Block(0.0, 0.0, cw, ch, radius * 2.0, radius * 2.0));
            }
        }
        return blocks;
    }

    private void syncLayer(Group layer, List<Block> blocks, double offsetX,
                           double offsetY, Paint fill) {
        while (layer.getChildren().size() < blocks.size()) {
            Rectangle rectangle = new Rectangle();
            rectangle.setManaged(false);
            rectangle.setMouseTransparent(true);
            layer.getChildren().add(rectangle);
        }
        while (layer.getChildren().size() > blocks.size()) {
            layer.getChildren().remove(layer.getChildren().size() - 1);
        }

        for (int i = 0; i < blocks.size(); i++) {
            Rectangle rectangle = (Rectangle) layer.getChildren().get(i);
            Block block = blocks.get(i);
            rectangle.setX(offsetX + block.x());
            rectangle.setY(offsetY + block.y());
            rectangle.setWidth(block.width());
            rectangle.setHeight(block.height());
            rectangle.setArcWidth(block.arcWidth());
            rectangle.setArcHeight(block.arcHeight());
            rectangle.setFill(fill);
        }
    }

    private void layoutShimmer(double cx, double cy, double cw, double ch) {
        double ratio = RXMath.clamp0To1(getSkinnable().getShimmerWidthRatio());
        double bandWidth = cw * ratio;
        positionShimmerViewport(cx, cy);
        if (bandWidth <= 0.0) {
            shimmerBand.setWidth(0.0);
            shimmerBand.setHeight(ch);
            cachedBandWidth = 0.0;
            return;
        }

        shimmerBand.setX(0.0);
        shimmerBand.setY(0.0);
        shimmerBand.setWidth(bandWidth);
        shimmerBand.setHeight(ch);

        cachedBandWidth = bandWidth;
    }

    private void positionShimmerViewport(double x, double y) {
        // Do not use relocate(): Group layoutBounds include the animated band,
        // so a re-layout after detach/attach would offset the clip by the
        // band's current translateX.
        shimmerViewport.setLayoutX(x);
        shimmerViewport.setLayoutY(y);
    }

    private record Block(double x, double y, double width, double height,
                         double arcWidth, double arcHeight) {
    }

    // ==================== Sizing — deliberately stretchable ====================

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        Shape s = getSkinnable().getVariant();
        double inner = switch (s) {
            case TEXT_LINE -> {
                int n = Math.max(1, getSkinnable().getLineCount());
                double lh = RXMath.sanitizeNonNegative(getSkinnable().getLineHeight());
                double sp = RXMath.sanitizeNonNegative(getSkinnable().getLineSpacing());
                yield n * lh + Math.max(0, n - 1) * sp;
            }
            case CIRCLE -> DEFAULT_PREF_HEIGHT * 2.0;
            default -> DEFAULT_PREF_HEIGHT;
        };
        return topInset + inner + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Deliberate divergence from RXCircular / RXWave: the skeleton is a
        // placeholder, so it must grow inside HBox.Hgrow=ALWAYS / VBox.Vgrow=
        // ALWAYS. Locking max to pref would freeze it at the default size.
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners, bindings, clip and
        // treeShowing teardown are handled by the embedded SkinDisposer in
        // RXSkinBase.dispose().
        stopAndClearTimeline();
        super.dispose();
    }

    // ==================== Helpers ====================

    private static Paint paintOrDefault(Paint v, Paint fallback) {
        return v != null ? v : fallback;
    }

}
