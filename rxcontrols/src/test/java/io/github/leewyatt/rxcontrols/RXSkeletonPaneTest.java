package io.github.leewyatt.rxcontrols;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link RXSkeletonPane}.
 */
public class RXSkeletonPaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Verifies content bias follows the same content-first sizing priority as
     * preferred size.
     */
    @Test
    public void contentBiasPrefersContentThenSkeleton() {
        FixedRegion skeleton = new FixedRegion(10.0, 10.0, 20.0, 20.0,
                Orientation.VERTICAL);
        FixedRegion content = new FixedRegion(10.0, 10.0, 20.0, 20.0,
                Orientation.HORIZONTAL);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        assertSame(Orientation.HORIZONTAL, pane.getContentBias());

        pane.setContent(null);

        assertSame(Orientation.VERTICAL, pane.getContentBias());

        pane.setSkeleton(null);

        assertNull(pane.getContentBias());
    }

    /**
     * Verifies preferred size stays content-first while minimum size protects
     * both slots.
     */
    @Test
    public void minSizeUsesMaxOfContentAndSkeleton() {
        FixedRegion skeleton = new FixedRegion(90.0, 70.0, 150.0, 120.0, null);
        FixedRegion content = new FixedRegion(40.0, 30.0, 100.0, 50.0, null);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        assertClose(96.0, pane.minWidth(-1.0), "min width");
        assertClose(74.0, pane.minHeight(-1.0), "min height");
        assertClose(106.0, pane.prefWidth(-1.0), "pref width");
        assertClose(54.0, pane.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies layout clamps the child allocation to a non-negative content
     * area when insets exceed the pane size.
     */
    @Test
    public void layoutUsesNonNegativeContentArea() {
        Region skeleton = new Region();
        skeleton.setMinSize(0.0, 0.0);
        skeleton.setPrefSize(0.0, 0.0);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, null, true);
        pane.setPadding(new Insets(10.0));

        layout(pane, 5.0, 5.0);

        assertClose(10.0, skeleton.getLayoutX(), "child x");
        assertClose(10.0, skeleton.getLayoutY(), "child y");
        assertClose(0.0, skeleton.getLayoutBounds().getWidth(), "child width");
        assertClose(0.0, skeleton.getLayoutBounds().getHeight(), "child height");
    }

    /**
     * Verifies only the active slot is attached to the scene graph.
     */
    @Test
    public void loadingSwitchKeepsOnlyActiveSlotAttached() {
        Region skeleton = new Region();
        Region content = new Region();
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertSame(skeleton, pane.getChildrenUnmodifiable().get(0));

        pane.setLoading(false);

        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertSame(content, pane.getChildrenUnmodifiable().get(0));
        assertNull(skeleton.getParent());
    }

    /**
     * Verifies replacing the hidden slot invalidates layout even though the
     * displayed child is untouched — measurement reads both slots, and the
     * detached node cannot bubble a layout request itself.
     */
    @Test
    public void hiddenSlotReplacementInvalidatesLayout() {
        FixedRegion skeleton = new FixedRegion(10.0, 10.0, 50.0, 20.0, null);
        FixedRegion content = new FixedRegion(10.0, 10.0, 100.0, 40.0, null);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        layout(pane, 100.0, 40.0);
        assertEquals(false, pane.isNeedsLayout(), "clean after layout");

        // Loading: content is hidden yet drives pref — replacing it must
        // schedule a re-measure.
        pane.setContent(new FixedRegion(10.0, 10.0, 300.0, 80.0, null));
        assertEquals(true, pane.isNeedsLayout(), "hidden content replaced");
        assertClose(300.0, pane.prefWidth(-1.0), "pref follows new content");

        layout(pane, 300.0, 80.0);
        pane.setLoading(false);
        layout(pane, 300.0, 80.0);

        // Loaded: skeleton is hidden yet participates in min — same contract.
        pane.setSkeleton(new FixedRegion(200.0, 90.0, 220.0, 100.0, null));
        assertEquals(true, pane.isNeedsLayout(), "hidden skeleton replaced");
        assertClose(200.0, pane.minWidth(-1.0), "min follows new skeleton");
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    private static final class FixedRegion extends Region {

        private final double minWidth;
        private final double minHeight;
        private final double prefWidth;
        private final double prefHeight;
        private final Orientation contentBias;

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight,
                            Orientation contentBias) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            this.contentBias = contentBias;
        }

        @Override
        public Orientation getContentBias() {
            return contentBias;
        }

        @Override
        protected double computeMinWidth(double height) {
            return minWidth;
        }

        @Override
        protected double computeMinHeight(double width) {
            return minHeight;
        }

        @Override
        protected double computePrefWidth(double height) {
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return prefHeight;
        }
    }
}
