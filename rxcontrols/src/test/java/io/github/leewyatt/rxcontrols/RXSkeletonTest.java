package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.skins.RXSkeletonSkin;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
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
        assertClose(RXSkeleton.DEFAULT_CORNER_RADIUS, skeleton.getCornerRadius(), "corner radius");
        assertSame(RXSkeleton.DEFAULT_BASE_COLOR, skeleton.getBaseColor());
        assertSame(RXSkeleton.DEFAULT_SHIMMER_FILL, skeleton.getShimmerFill());
        assertSame(RXSkeleton.DEFAULT_CYCLE_DURATION, skeleton.getCycleDuration());
        assertClose(RXSkeleton.DEFAULT_SHIMMER_WIDTH, skeleton.getShimmerWidth(), "shimmer width");
        assertEquals(RXSkeleton.DEFAULT_LINE_COUNT, skeleton.getLineCount());
        assertClose(RXSkeleton.DEFAULT_LINE_HEIGHT, skeleton.getLineHeight(), "line height");
        assertClose(RXSkeleton.DEFAULT_LINE_SPACING, skeleton.getLineSpacing(), "line spacing");
        assertClose(RXSkeleton.DEFAULT_LAST_LINE_FILL_PERCENT,
                skeleton.getLastLineFillPercent(), "last line percent");

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
        assertClose(RXSkeleton.DEFAULT_SHIMMER_WIDTH, shimmerBand.getWidth(), "band width");
        assertClose(-RXSkeleton.DEFAULT_SHIMMER_WIDTH,
                shimmerBand.getTranslateX(), "band translate x");
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
