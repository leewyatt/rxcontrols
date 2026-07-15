package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSkeleton}. Renders the base block driven by
 * the control's {@link Variant}, overlays a configurable shimmer band, and
 * scrolls that band horizontally on an indefinite {@link Timeline}.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>The shimmer band is a single {@link Rectangle} translated along the
 *       x-axis; the fill paint does not change per frame, so the cost per
 *       frame is a pure affine transform.</li>
 *   <li>The band moves inside a shimmer layer clipped by mask rectangles
 *       computed from the same geometry as the shape layer — this prevents the
 *       gradient from spilling into rounded corners or text-line gaps.</li>
 *   <li>{@link RXTreeShowingProperty} auto-pauses the scroll when the skeleton is
 *       detached, hidden, or hosted by a hidden window, so an invisible
 *       skeleton never burns pulse time.</li>
 *   <li>{@code maxWidth} / {@code maxHeight} report {@link Double#MAX_VALUE},
 *       so the skeleton stretches inside grow-priority containers — the
 *       deliberate opposite of {@link RXCircularProgressIndicatorSkin} /
 *       {@link RXWaveProgressIndicatorSkin}, which lock {@code max == pref}
 *       to keep spinners square.</li>
 * </ul>
 */
public class RXSkeletonSkin extends RXSkinBase<RXSkeleton> {

    // ==================== Layout Constants ====================

    /**
     * Pref size used when neither the user nor the parent container imposes a
     * size. Picked to match a typical "title line" placeholder so a control
     * dropped into a free-form parent at least shows up.
     */
    private static final double DEFAULT_PREF_WIDTH = 120.0;
    private static final double DEFAULT_PREF_HEIGHT = 16.0;
    private static final double DEFAULT_CIRCULAR_SIZE = 48.0;

    private static final double HALF = 0.5;
    private static final double FULL_PERCENT = 100.0;

    // ==================== Nodes ====================

    /**
     * Shape layer drawn under the shimmer. The same computed blocks also drive
     * the shimmer mask, keeping the base and shimmer footprint in sync.
     */
    private final Group shapeLayer = new Group();
    private final Group shimmerLayer = new Group();
    private final Group shimmerMask = new Group();

    private final Rectangle shimmerBand = new Rectangle();

    private final ReadOnlyBooleanProperty treeShowing;

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
    public RXSkeletonSkin(RXSkeleton control) {
        super(control);

        initNodes();
        treeShowing = controlTreeShowingProperty();

        registerListeners(control);
        applyBaseFill();
        applyShimmerFill();
    }

    // ==================== Init ====================

    private void initNodes() {
        shapeLayer.getStyleClass().add("shape-layer");
        shapeLayer.setManaged(false);
        shapeLayer.setMouseTransparent(true);

        shimmerLayer.getStyleClass().add("shimmer-layer");
        shimmerLayer.setManaged(false);
        shimmerLayer.setMouseTransparent(true);
        shimmerLayer.setClip(shimmerMask);
        disposer.registerDisposeTask(() -> shimmerLayer.setClip(null));

        shimmerMask.setManaged(false);
        shimmerMask.setMouseTransparent(true);

        shimmerBand.getStyleClass().add("shimmer-band");
        shimmerBand.setManaged(false);
        shimmerBand.setMouseTransparent(true);

        shimmerLayer.getChildren().setAll(shimmerBand);
        getChildren().setAll(shapeLayer, shimmerLayer);
    }

    private void registerListeners(RXSkeleton control) {
        disposer.registerListener(control.variantProperty(), control::requestLayout);
        disposer.registerListener(control.cornerRadiusProperty(), control::requestLayout);

        disposer.registerListener(control.baseColorProperty(), this::applyBaseFill);
        disposer.registerListener(control.shimmerFillProperty(), this::applyShimmerFill);

        disposer.registerListener(control.cycleDurationProperty(), this::rebuildShimmerTimeline);
        disposer.registerListener(control.shimmerWidthProperty(), control::requestLayout);

        disposer.registerListener(control.lineCountProperty(), control::requestLayout);
        disposer.registerListener(control.lineHeightProperty(), control::requestLayout);
        disposer.registerListener(control.lineSpacingProperty(), control::requestLayout);
        disposer.registerListener(control.lastLineFillPercentProperty(), control::requestLayout);

        disposer.registerListener(treeShowing, () -> onTreeShowingChanged(treeShowing.get()));
    }

    // ==================== Style application ====================

    private void applyBaseFill() {
        Paint p = getSkinnable().getBaseColor();
        for (Node n : shapeLayer.getChildren()) {
            if (n instanceof Rectangle r) {
                r.setFill(p);
            }
        }
    }

    private void applyShimmerFill() {
        shimmerBand.setFill(getSkinnable().getShimmerFill());
    }

    // ==================== Shimmer timeline ====================

    /**
     * Rebuilds the horizontal scroll timeline. Animates the band's
     * {@code translateX} from {@code -bandWidth} to {@code contentWidth} on an
     * {@link Animation#INDEFINITE} loop so the band always enters from the
     * left edge and exits on the right.
     *
     * <p>{@code cycleDuration <= 0} or {@code null} disables the animation and
     * resets the band to a deterministic off-screen pose so a stale frame from
     * a previous animation cannot linger.
     */
    private void rebuildShimmerTimeline() {
        Duration cycle = getSkinnable().getCycleDuration();
        // isUnknown() must be checked here: KeyFrame only rejects the
        // Duration.UNKNOWN singleton (equals-based), so a hand-made NaN
        // duration would silently poison the timeline math instead.
        boolean disabled = cycle == null || cycle.isUnknown() || cycle.isIndefinite()
                || cycle.lessThanOrEqualTo(Duration.ZERO);
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

        List<Block> blocks = computeBlocks(variantOrDefault(), contentWidth, contentHeight);
        if (blocks.isEmpty()) {
            // Nothing visible to shimmer over (e.g. no TEXT line fits) — an
            // empty clip would hide the band while its timeline keeps running.
            collapseAll();
            return;
        }
        syncShapeLayer(blocks, contentX, contentY);
        syncShimmerMask(blocks);

        layoutShimmer(contentX, contentY, contentHeight);
        rebuildShimmerTimeline();
    }

    private void collapseAll() {
        shapeLayer.getChildren().clear();
        shimmerMask.getChildren().clear();
        positionShimmerLayer(0.0, 0.0);
        shimmerBand.setWidth(0.0);
        shimmerBand.setHeight(0.0);
        cachedBandWidth = 0.0;
        rebuildShimmerTimeline();
    }

    private Variant variantOrDefault() {
        Variant v = getSkinnable().getVariant();
        return v == null ? RXSkeleton.DEFAULT_VARIANT : v;
    }

    private List<Block> computeBlocks(Variant variant, double cw, double ch) {
        List<Block> blocks = new ArrayList<>();
        switch (variant) {
            case CIRCULAR -> {
                double diameter = Math.min(cw, ch);
                double offsetX = (cw - diameter) * HALF;
                double offsetY = (ch - diameter) * HALF;
                blocks.add(new Block(offsetX, offsetY, diameter, diameter, diameter, diameter));
            }
            case TEXT -> {
                // Finite sanitizing: an infinite line height / spacing would
                // turn `0 * Infinity` into NaN geometry below.
                double lineHeight = RXMath.sanitizeFiniteNonNegative(getSkinnable().getLineHeight());
                double lineSpacing = RXMath.sanitizeFiniteNonNegative(getSkinnable().getLineSpacing());
                double lastPercentSource = RXMath.sanitizeNonNegative(getSkinnable().getLastLineFillPercent());
                double lastPercent = Math.min(FULL_PERCENT, lastPercentSource);
                int lineCount = Math.max(1, getSkinnable().getLineCount());
                double radius = lineHeight * HALF;
                // Zero-height lines paint nothing; leave the block list empty
                // so the shimmer collapses instead of animating invisibly.
                if (lineHeight <= 0.0) {
                    break;
                }
                for (int i = 0; i < lineCount; i++) {
                    double y = i * (lineHeight + lineSpacing);
                    if (y + lineHeight > ch) {
                        // Control does not clip children; a line whose bottom
                        // edge passes the content height would paint outside
                        // the bounds. Whole lines that do not fit are omitted.
                        break;
                    }
                    double width = cw;
                    if (i == lineCount - 1 && lineCount > 1) {
                        width = cw * lastPercent / FULL_PERCENT;
                    }
                    blocks.add(new Block(0.0, y, width, lineHeight, radius * 2.0, radius * 2.0));
                }
            }
            case ROUNDED_RECTANGLE -> {
                double radius = RXMath.sanitizeNonNegative(getSkinnable().getCornerRadius());
                blocks.add(new Block(0.0, 0.0, cw, ch, radius * 2.0, radius * 2.0));
            }
        }
        return blocks;
    }

    private void syncShapeLayer(List<Block> blocks, double contentX, double contentY) {
        syncRectangles(shapeLayer, blocks, contentX, contentY, getSkinnable().getBaseColor());
    }

    private void syncShimmerMask(List<Block> blocks) {
        syncRectangles(shimmerMask, blocks, 0.0, 0.0, Color.BLACK);
    }

    private void syncRectangles(Group layer, List<Block> blocks, double offsetX, double offsetY, Paint fill) {
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

    private void layoutShimmer(double cx, double cy, double ch) {
        double bandWidth = RXMath.sanitizeFiniteNonNegative(getSkinnable().getShimmerWidth());
        positionShimmerLayer(cx, cy);
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

    private void positionShimmerLayer(double x, double y) {
        // Do not use relocate(): Group layoutBounds include the animated band,
        // so a re-layout after detach/attach would offset the clip by the
        // band's current translateX.
        shimmerLayer.setLayoutX(x);
        shimmerLayer.setLayoutY(y);
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
        Variant variant = variantOrDefault();
        double inner = switch (variant) {
            case CIRCULAR -> DEFAULT_CIRCULAR_SIZE;
            case ROUNDED_RECTANGLE, TEXT -> DEFAULT_PREF_WIDTH;
        };
        return leftInset + inner + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        Variant variant = variantOrDefault();
        double inner = switch (variant) {
            case TEXT -> {
                int n = Math.max(1, getSkinnable().getLineCount());
                double lh = RXMath.sanitizeFiniteNonNegative(getSkinnable().getLineHeight());
                double sp = RXMath.sanitizeFiniteNonNegative(getSkinnable().getLineSpacing());
                // Zero-height lines render nothing (see computeBlocks); do not
                // reserve spacing-only blank space for an invisible skeleton.
                yield lh <= 0.0 ? 0.0 : n * lh + Math.max(0, n - 1) * sp;
            }
            case CIRCULAR -> DEFAULT_CIRCULAR_SIZE;
            case ROUNDED_RECTANGLE -> DEFAULT_PREF_HEIGHT;
        };
        return topInset + inner + bottomInset;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // Timelines are rebuilt many times during the skin's life; stop the
        // current one explicitly here. Listeners, bindings, clip and
        // treeShowing teardown are handled by the base disposer.
        stopAndClearTimeline();
    }

}
