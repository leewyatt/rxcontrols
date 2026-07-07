package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.skins.RXSkeletonSkin;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.StyleOrigin;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSkeleton}.
 */
public class RXSkeletonTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Verifies the public defaults and CSS metadata surface.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXSkeleton skeleton = new RXSkeleton();

        assertTrue(skeleton.getStyleClass().contains("rx-skeleton"));
        assertSame(RXSkeleton.DEFAULT_VARIANT, skeleton.getVariant());

        Set<String> properties = cssPropertyNames();
        assertTrue(properties.contains("-fx-skin"));
        assertTrue(properties.contains("-rx-variant"));
        assertTrue(properties.contains("-rx-corner-radius"));
        assertTrue(properties.contains("-rx-base-color"));
        assertTrue(properties.contains("-rx-shimmer-fill"));
        assertTrue(properties.contains("-rx-cycle-duration"));
        assertTrue(properties.contains("-rx-shimmer-width"));
        assertTrue(properties.contains("-rx-line-count"));
        assertTrue(properties.contains("-rx-line-height"));
        assertTrue(properties.contains("-rx-line-spacing"));
        assertTrue(properties.contains("-rx-last-line-fill-percent"));
    }

    /**
     * Verifies the lenient FXML-style constructor fallback and that later
     * {@code null} writes are stored rather than rejected.
     */
    @Test
    public void variantNullDegradesToDefault() {
        RXSkeleton skeleton = new RXSkeleton(null);
        assertSame(RXSkeleton.DEFAULT_VARIANT, skeleton.getVariant());

        skeleton.setVariant(Variant.TEXT);
        assertSame(Variant.TEXT, skeleton.getVariant());

        skeleton.setVariant(null);
        assertNull(skeleton.getVariant(), "null is stored, not rejected");

        // The skin must tolerate a null variant at layout time: it resolves to the
        // default and lays out without an NPE.
        installSkin(skeleton);
        layout(skeleton, 200.0, 100.0);
        assertTrue(baseLayer(skeleton).getChildren().size() > 0,
                "null variant lays out as the default (no NPE)");
    }

    /**
     * Verifies the gradient factory creates transparent edges and rejects null.
     */
    @Test
    public void createShimmerGradientUsesTransparentEdges() {
        Color highlight = Color.web("#ffffff", 0.6);

        LinearGradient gradient = RXSkeleton.createShimmerGradient(highlight);
        List<Stop> stops = gradient.getStops();

        assertEquals(3, stops.size());
        assertClose(0.0, stops.get(0).getOffset(), "start offset");
        assertClose(0.0, stops.get(0).getColor().getOpacity(), "start opacity");
        assertClose(0.5, stops.get(1).getOffset(), "center offset");
        assertEquals(highlight, stops.get(1).getColor());
        assertClose(1.0, stops.get(2).getOffset(), "end offset");
        assertClose(0.0, stops.get(2).getColor().getOpacity(), "end opacity");

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> RXSkeleton.createShimmerGradient(null));
        assertEquals("highlightColor cannot be null", exception.getMessage());
    }

    /**
     * Verifies CIRCULAR reports a square default preferred size.
     */
    @Test
    public void circularDefaultPrefSizeIsSquare() {
        RXSkeleton skeleton = new RXSkeleton(Variant.CIRCULAR);
        installSkin(skeleton);

        assertClose(48.0, skeleton.prefWidth(-1.0), "pref width");
        assertClose(48.0, skeleton.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies base and shimmer fills follow the B1 null-through contract.
     */
    @Test
    public void nullPaintsPassThroughToInternalShapes() {
        RXSkeleton skeleton = new RXSkeleton();
        skeleton.setBaseColor(null);
        skeleton.setShimmerFill(null);
        installSkin(skeleton);

        layout(skeleton, 120.0, 16.0);

        Rectangle baseBlock = rectanglesIn(baseLayer(skeleton)).get(0);
        Rectangle shimmerBand = shimmerBand(skeleton);
        assertNull(skeleton.getBaseColor());
        assertNull(skeleton.getShimmerFill());
        assertNull(baseBlock.getFill());
        assertNull(shimmerBand.getFill());
    }

    /**
     * Verifies TEXT uses one base and clip rectangle per visible text line.
     */
    @Test
    public void textVariantCreatesPerLineBaseAndClipBlocks() {
        RXSkeleton skeleton = new RXSkeleton(Variant.TEXT);
        skeleton.setLineCount(3);
        skeleton.setLineHeight(10.0);
        skeleton.setLineSpacing(5.0);
        skeleton.setLastLineFillPercent(50.0);
        installSkin(skeleton);

        layout(skeleton, 200.0, 60.0);

        List<Rectangle> baseBlocks = rectanglesIn(baseLayer(skeleton));
        List<Rectangle> clipBlocks = rectanglesIn(clipLayer(skeleton));
        assertEquals(3, baseBlocks.size());
        assertEquals(3, clipBlocks.size());

        assertBlock(baseBlocks.get(0), 0.0, 0.0, 200.0, 10.0);
        assertBlock(baseBlocks.get(1), 0.0, 15.0, 200.0, 10.0);
        assertBlock(baseBlocks.get(2), 0.0, 30.0, 100.0, 10.0);
        assertBlock(clipBlocks.get(0), 0.0, 0.0, 200.0, 10.0);
        assertBlock(clipBlocks.get(1), 0.0, 15.0, 200.0, 10.0);
        assertBlock(clipBlocks.get(2), 0.0, 30.0, 100.0, 10.0);
    }

    /**
     * Verifies zero or invalid shimmer widths collapse the moving band.
     */
    @Test
    public void invalidShimmerWidthCollapsesBand() {
        RXSkeleton skeleton = new RXSkeleton();
        installSkin(skeleton);

        skeleton.setShimmerWidth(Double.NaN);
        layout(skeleton, 120.0, 16.0);
        assertClose(0.0, shimmerBand(skeleton).getWidth(), "NaN band width");

        skeleton.setShimmerWidth(Double.POSITIVE_INFINITY);
        layout(skeleton, 120.0, 16.0);
        assertClose(0.0, shimmerBand(skeleton).getWidth(), "infinite band width");

        skeleton.setShimmerWidth(-1.0);
        layout(skeleton, 120.0, 16.0);
        assertClose(0.0, shimmerBand(skeleton).getWidth(), "negative band width");
    }

    /**
     * Verifies non-positive cycle duration parks the shimmer off-screen.
     */
    @Test
    public void nonPositiveCycleDurationDisablesAnimationDeterministically() {
        RXSkeleton skeleton = new RXSkeleton();
        skeleton.setCycleDuration(Duration.ZERO);
        installSkin(skeleton);

        layout(skeleton, 120.0, 16.0);

        Rectangle shimmerBand = shimmerBand(skeleton);
        assertClose(56.0, shimmerBand.getWidth(), "band width");
        assertClose(-56.0,
                shimmerBand.getTranslateX(), "band translate x");
    }

    /**
     * Verifies unknown and indefinite cycle durations disable the animation
     * instead of crashing the layout pass. Covers the Duration.UNKNOWN
     * singleton, a hand-made NaN duration (which KeyFrame's own equals-based
     * check would not reject), and Duration.INDEFINITE.
     */
    @Test
    public void unknownAndIndefiniteCycleDurationsDisableAnimation() {
        for (Duration cycle : new Duration[]{
                Duration.UNKNOWN, new Duration(Double.NaN), Duration.INDEFINITE}) {
            RXSkeleton skeleton = new RXSkeleton();
            skeleton.setCycleDuration(cycle);
            installSkin(skeleton);

            layout(skeleton, 120.0, 16.0);

            Rectangle shimmerBand = shimmerBand(skeleton);
            assertClose(56.0, shimmerBand.getWidth(), "band width for " + cycle);
            assertClose(-56.0, shimmerBand.getTranslateX(),
                    "band parked off-screen for " + cycle);
        }
    }

    /**
     * Verifies construction does not stamp the USER style origin on the
     * variant property unless a variant was chosen explicitly, so the
     * user-agent stylesheet can still set {@code -rx-variant}.
     */
    @Test
    public void constructorsDoNotStampUserStyleOriginOnVariant() {
        assertNull(variantOrigin(new RXSkeleton()), "no-arg constructor");
        assertNull(variantOrigin(new RXSkeleton(null)), "null variant");
        assertSame(StyleOrigin.USER, variantOrigin(new RXSkeleton(Variant.TEXT)),
                "explicit variant is a deliberate user choice");
    }

    /**
     * Verifies TEXT omits whole lines that do not fit the content height —
     * including lines whose bottom edge would cross it — instead of painting
     * outside the control's bounds.
     */
    @Test
    public void textVariantOmitsLinesThatDoNotFitContentHeight() {
        RXSkeleton skeleton = new RXSkeleton(Variant.TEXT);
        skeleton.setLineCount(5);
        skeleton.setLineHeight(10.0);
        skeleton.setLineSpacing(5.0);
        installSkin(skeleton);

        // Line 2 spans y=15..25 — its bottom edge crosses the 22px height.
        layout(skeleton, 200.0, 22.0);
        assertEquals(1, rectanglesIn(baseLayer(skeleton)).size(),
                "only the line at y=0..10 fits into 22px");
        assertEquals(1, rectanglesIn(clipLayer(skeleton)).size());

        // Exact fit is inclusive: y=15..25 with ch=25 is kept.
        layout(skeleton, 200.0, 25.0);
        assertEquals(2, rectanglesIn(baseLayer(skeleton)).size(),
                "a line ending exactly at the content height fits");

        // Degenerate: not even the first line fits — nothing is painted and
        // the shimmer collapses instead of animating invisibly.
        skeleton.setLineHeight(30.0);
        layout(skeleton, 200.0, 22.0);
        assertEquals(0, rectanglesIn(baseLayer(skeleton)).size(),
                "a 30px line cannot fit into 22px");
        assertClose(0.0, shimmerBand(skeleton).getWidth(),
                "shimmer collapses with no visible block");

        // Infinite line height degrades to 0 — no lines, no shimmer.
        skeleton.setLineHeight(Double.POSITIVE_INFINITY);
        layout(skeleton, 200.0, 60.0);
        assertEquals(0, rectanglesIn(baseLayer(skeleton)).size(),
                "infinite line height renders no lines");
        assertClose(0.0, shimmerBand(skeleton).getWidth(),
                "shimmer stays collapsed for infinite line height");
    }

    /**
     * Verifies the Skin exposes stable style classes for tests and diagnostics.
     */
    @Test
    public void skinNodesHaveStableStyleClasses() {
        RXSkeleton skeleton = new RXSkeleton();
        installSkin(skeleton);
        layout(skeleton, 120.0, 16.0);

        assertTrue(baseLayer(skeleton).getStyleClass().contains("base-layer"));
        assertTrue(shimmerViewport(skeleton).getStyleClass().contains("shimmer-viewport"));
        assertTrue(clipLayer(skeleton).getStyleClass().contains("clip-layer"));
        assertTrue(rectanglesIn(baseLayer(skeleton)).get(0).getStyleClass().contains("base-block"));
        assertTrue(rectanglesIn(clipLayer(skeleton)).get(0).getStyleClass().contains("clip-block"));
        assertTrue(shimmerBand(skeleton).getStyleClass().contains("shimmer-band"));
    }

    private static StyleOrigin variantOrigin(RXSkeleton skeleton) {
        return ((StyleableProperty<?>) skeleton.variantProperty()).getStyleOrigin();
    }

    private static Set<String> cssPropertyNames() {
        return RXSkeleton.getClassCssMetaData().stream()
                .map(CssMetaData<? extends Styleable, ?>::getProperty)
                .collect(Collectors.toSet());
    }

    private static void installSkin(RXSkeleton skeleton) {
        skeleton.setSkin(new RXSkeletonSkin(skeleton));
    }

    private static void layout(RXSkeleton skeleton, double width, double height) {
        skeleton.resize(width, height);
        skeleton.requestLayout();
        skeleton.layout();
    }

    private static Group baseLayer(RXSkeleton skeleton) {
        return styledChild(skeleton, "base-layer");
    }

    private static Group shimmerViewport(RXSkeleton skeleton) {
        return styledChild(skeleton, "shimmer-viewport");
    }

    private static Group clipLayer(RXSkeleton skeleton) {
        Node clip = shimmerViewport(skeleton).getClip();
        assertInstanceOf(Group.class, clip);
        return (Group) clip;
    }

    private static Rectangle shimmerBand(RXSkeleton skeleton) {
        Group viewport = shimmerViewport(skeleton);
        assertEquals(1, viewport.getChildren().size());
        assertInstanceOf(Rectangle.class, viewport.getChildren().get(0));
        return (Rectangle) viewport.getChildren().get(0);
    }

    private static Group styledChild(RXSkeleton skeleton, String styleClass) {
        for (Node child : skeleton.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(styleClass)) {
                assertInstanceOf(Group.class, child);
                return (Group) child;
            }
        }
        throw new AssertionError("Missing child style class: " + styleClass);
    }

    private static List<Rectangle> rectanglesIn(Group group) {
        return group.getChildren().stream()
                .map(node -> assertInstanceOf(Rectangle.class, node))
                .map(Rectangle.class::cast)
                .toList();
    }

    private static void assertBlock(Rectangle rectangle, double x, double y,
                                    double width, double height) {
        assertClose(x, rectangle.getX(), "block x");
        assertClose(y, rectangle.getY(), "block y");
        assertClose(width, rectangle.getWidth(), "block width");
        assertClose(height, rectangle.getHeight(), "block height");
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }
}
