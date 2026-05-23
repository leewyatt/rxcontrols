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
 *   <li>The band is clipped to a separate rectangle whose geometry matches
 *       the union of the base shapes — this prevents the gradient from
 *       spilling into the rounded / circular corners.</li>
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
     * Base layer drawn under the shimmer. For {@link Shape#TEXT_LINE} this is
     * a {@link Group} of N {@link Rectangle line nodes}; for the other shapes
     * it is a single {@link Rectangle}.
     */
    private final Group baseLayer = new Group();

    private final Rectangle shimmerBand = new Rectangle();
    private final Rectangle shimmerClip = new Rectangle();

    private final TreeShowingProperty treeShowing;

    private Timeline shimmerTimeline;

    /** Cached geometry — kept so the timeline can be rebuilt without re-querying layout. */
    private double cachedContentX;
    private double cachedContentY;
    private double cachedContentWidth;
    private double cachedContentHeight;
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
        // Layer composition depends on shape (TEXT_LINE rebuilds children),
        // do it once up-front so the first paint is correct.
        rebuildBaseLayer();
    }

    // ==================== Init ====================

    private void initNodes() {
        baseLayer.setManaged(false);
        baseLayer.setMouseTransparent(true);

        shimmerBand.getStyleClass().add("shimmer-band");
        shimmerBand.setManaged(false);
        shimmerBand.setMouseTransparent(true);
        // The band is wider than the visible window during a sweep (band width
        // + content width travel), so clip it to the base footprint to keep
        // the gradient from bleeding into rounded / circular corners.
        shimmerBand.setClip(shimmerClip);
        disposer.registerDisposeTask(() -> shimmerBand.setClip(null));

        getChildren().setAll(baseLayer, shimmerBand);
    }

    private void registerListeners(RXSkeletonLoader control) {
        // Variant changes the children layout, so rebuild the base layer; the
        // skin's own layoutChildren() re-runs naturally on requestLayout().
        disposer.registerListener(control.variantProperty(), () -> {
            rebuildBaseLayer();
            control.requestLayout();
        });
        disposer.registerListener(control.cornerRadiusProperty(), control::requestLayout);

        disposer.registerListener(control.baseColorProperty(), this::applyBaseFill);
        disposer.registerListener(control.shimmerColorProperty(), this::applyShimmerFill);

        disposer.registerListener(control.cycleDurationProperty(), this::rebuildShimmerTimeline);
        disposer.registerListener(control.shimmerWidthRatioProperty(), control::requestLayout);

        disposer.registerListener(control.lineCountProperty(), () -> {
            rebuildBaseLayer();
            control.requestLayout();
        });
        disposer.registerListener(control.lineHeightProperty(), control::requestLayout);
        disposer.registerListener(control.lineSpacingProperty(), control::requestLayout);
        disposer.registerListener(control.lastLineFillPercentProperty(), control::requestLayout);

        disposer.registerListener(treeShowing, () -> onTreeShowingChanged(treeShowing.get()));
    }

    // ==================== Style application ====================

    private void applyBaseFill() {
        Paint p = paintOrDefault(getSkinnable().getBaseColor(), RXSkeletonLoader.DEFAULT_BASE_COLOR);
        for (javafx.scene.Node n : baseLayer.getChildren()) {
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

    // ==================== Base layer composition ====================

    /**
     * Rebuilds the base layer's children to match the current shape. Called on
     * skin construction, on {@link Shape} change, and on
     * {@link RXSkeletonLoader#lineCountProperty() lineCount} change.
     */
    private void rebuildBaseLayer() {
        Shape s = getSkinnable().getVariant();
        Paint fill = paintOrDefault(getSkinnable().getBaseColor(), RXSkeletonLoader.DEFAULT_BASE_COLOR);

        if (s == Shape.TEXT_LINE) {
            int n = Math.max(1, getSkinnable().getLineCount());
            List<Rectangle> lines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Rectangle line = new Rectangle();
                line.setFill(fill);
                line.setManaged(false);
                lines.add(line);
            }
            baseLayer.getChildren().setAll(lines);
        } else {
            Rectangle base = new Rectangle();
            base.setFill(fill);
            base.setManaged(false);
            baseLayer.getChildren().setAll(base);
        }
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
                                cachedContentX - bandWidth, Interpolator.LINEAR)),
                new KeyFrame(cycle,
                        new KeyValue(shimmerBand.translateXProperty(),
                                cachedContentX + cachedContentWidth, Interpolator.LINEAR))
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
        cachedContentX = contentX;
        cachedContentY = contentY;
        cachedContentWidth = contentWidth;
        cachedContentHeight = contentHeight;

        if (contentWidth <= 0.0 || contentHeight <= 0.0) {
            collapseAll();
            return;
        }

        Shape s = getSkinnable().getVariant();
        switch (s) {
            case CIRCLE -> layoutCircle(contentX, contentY, contentWidth, contentHeight);
            case TEXT_LINE -> layoutTextLine(contentX, contentY, contentWidth, contentHeight);
            case ROUNDED_RECT -> layoutRoundedRect(contentX, contentY, contentWidth, contentHeight);
        }

        layoutShimmer(s, contentX, contentY, contentWidth, contentHeight);
        rebuildShimmerTimeline();
    }

    private void collapseAll() {
        for (javafx.scene.Node n : baseLayer.getChildren()) {
            if (n instanceof Rectangle r) {
                r.setWidth(0.0);
                r.setHeight(0.0);
            }
        }
        shimmerBand.setWidth(0.0);
        shimmerBand.setHeight(0.0);
        shimmerClip.setWidth(0.0);
        shimmerClip.setHeight(0.0);
        cachedBandWidth = 0.0;
        rebuildShimmerTimeline();
    }

    private void layoutRoundedRect(double cx, double cy, double cw, double ch) {
        Rectangle base = (Rectangle) baseLayer.getChildren().get(0);
        double radius = RXMath.sanitizeNonNegative(getSkinnable().getCornerRadius());
        base.setX(cx);
        base.setY(cy);
        base.setWidth(cw);
        base.setHeight(ch);
        base.setArcWidth(radius * 2.0);
        base.setArcHeight(radius * 2.0);
    }

    private void layoutCircle(double cx, double cy, double cw, double ch) {
        Rectangle base = (Rectangle) baseLayer.getChildren().get(0);
        // Inscribe the circle in min(cw, ch) and centre it within the content
        // box so the circle stays square even when the parent gives an oblong
        // area (e.g. HBox.Hgrow=ALWAYS with no fixed width).
        double diameter = Math.min(cw, ch);
        double offsetX = cx + (cw - diameter) * HALF;
        double offsetY = cy + (ch - diameter) * HALF;
        base.setX(offsetX);
        base.setY(offsetY);
        base.setWidth(diameter);
        base.setHeight(diameter);
        base.setArcWidth(diameter);
        base.setArcHeight(diameter);
    }

    private void layoutTextLine(double cx, double cy, double cw, double ch) {
        double lineHeight = RXMath.sanitizeNonNegative(getSkinnable().getLineHeight());
        double lineSpacing = RXMath.sanitizeNonNegative(getSkinnable().getLineSpacing());
        double lastPercentSource = RXMath.sanitizeNonNegative(getSkinnable().getLastLineFillPercent());
        double lastPercent = RXMath.clamp(lastPercentSource, 0.0, FULL_PERCENT);
        int lineCount = baseLayer.getChildren().size();
        // Per-line corner radius keeps the line ends rounded without exposing
        // a separate property; using half the line height yields fully rounded
        // pill ends, which mimics modern text skeletons (Material / Twitter).
        double radius = lineHeight * HALF;

        for (int i = 0; i < lineCount; i++) {
            Rectangle line = (Rectangle) baseLayer.getChildren().get(i);
            double y = cy + i * (lineHeight + lineSpacing);
            double width = cw;
            if (i == lineCount - 1 && lineCount > 1) {
                width = cw * lastPercent / FULL_PERCENT;
            }
            line.setX(cx);
            line.setY(y);
            line.setWidth(width);
            line.setHeight(lineHeight);
            line.setArcWidth(radius * 2.0);
            line.setArcHeight(radius * 2.0);
        }
    }

    private void layoutShimmer(Shape s, double cx, double cy, double cw, double ch) {
        double ratio = RXMath.clamp0To1(getSkinnable().getShimmerWidthRatio());
        double bandWidth = cw * ratio;
        if (bandWidth <= 0.0) {
            shimmerBand.setWidth(0.0);
            shimmerClip.setWidth(0.0);
            cachedBandWidth = 0.0;
            return;
        }

        shimmerBand.setX(0.0);
        shimmerBand.setY(cy);
        shimmerBand.setWidth(bandWidth);
        shimmerBand.setHeight(ch);
        // Refresh the gradient — the previous one used a unit rectangle, but a
        // proportional LinearGradient is independent of the rect's width.
        // Still cheap; rebuild every layout pass so colour changes pick up.
        shimmerBand.setFill(buildShimmerGradient());

        // Clip geometry depends on shape: the clip must mirror the base so the
        // shimmer never paints outside the rounded / circular / per-line area.
        switch (s) {
            case CIRCLE -> {
                double diameter = Math.min(cw, ch);
                double offsetX = cx + (cw - diameter) * HALF;
                double offsetY = cy + (ch - diameter) * HALF;
                shimmerClip.setX(offsetX);
                shimmerClip.setY(offsetY);
                shimmerClip.setWidth(diameter);
                shimmerClip.setHeight(diameter);
                shimmerClip.setArcWidth(diameter);
                shimmerClip.setArcHeight(diameter);
            }
            case TEXT_LINE -> {
                // A single rectangular clip across the union of lines is good
                // enough — the gradient is mostly transparent and visually it
                // reads as one band sweeping across the paragraph rather than
                // each line ticking independently.
                double radius = RXMath.sanitizeNonNegative(getSkinnable().getLineHeight()) * HALF;
                shimmerClip.setX(cx);
                shimmerClip.setY(cy);
                shimmerClip.setWidth(cw);
                shimmerClip.setHeight(ch);
                shimmerClip.setArcWidth(radius * 2.0);
                shimmerClip.setArcHeight(radius * 2.0);
            }
            case ROUNDED_RECT -> {
                double radius = RXMath.sanitizeNonNegative(getSkinnable().getCornerRadius());
                shimmerClip.setX(cx);
                shimmerClip.setY(cy);
                shimmerClip.setWidth(cw);
                shimmerClip.setHeight(ch);
                shimmerClip.setArcWidth(radius * 2.0);
                shimmerClip.setArcHeight(radius * 2.0);
            }
        }

        cachedBandWidth = bandWidth;
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
