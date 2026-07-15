package io.github.leewyatt.rxcontrols;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        Region slot = (Region) skeleton.getParent();
        assertClose(10.0, slot.getLayoutX(), "slot x");
        assertClose(10.0, slot.getLayoutY(), "slot y");
        assertClose(0.0, slot.getLayoutBounds().getWidth(), "slot width");
        assertClose(0.0, slot.getLayoutBounds().getHeight(), "slot height");
        assertClose(0.0, skeleton.getLayoutX(), "child x");
        assertClose(0.0, skeleton.getLayoutY(), "child y");
        assertClose(0.0, skeleton.getLayoutBounds().getWidth(), "child width");
        assertClose(0.0, skeleton.getLayoutBounds().getHeight(), "child height");
    }

    /**
     * Verifies both slots stay attached and only the active slot is visible.
     */
    @Test
    public void loadingSwitchKeepsSlotsAttachedAndTogglesVisibility() {
        Region skeleton = new Region();
        Region content = new Region();
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        assertEquals(2, pane.getChildrenUnmodifiable().size());
        assertNotNull(skeleton.getParent());
        assertNotNull(content.getParent());
        assertTrue(skeleton.getParent().isVisible(), "skeleton slot visible");
        assertFalse(content.getParent().isVisible(), "content slot hidden");

        pane.setLoading(false);

        assertEquals(2, pane.getChildrenUnmodifiable().size());
        assertNotNull(skeleton.getParent());
        assertNotNull(content.getParent());
        assertFalse(skeleton.getParent().isVisible(), "skeleton slot hidden");
        assertTrue(content.getParent().isVisible(), "content slot visible");
    }

    /**
     * Verifies assigning the same node to both slots does not duplicate it in
     * the child tree.
     */
    @Test
    public void sameNodeCanServeBothSlots() {
        Region shared = new Region();
        RXSkeletonPane pane = new RXSkeletonPane(shared, shared, true);

        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertNotNull(shared.getParent());
        assertTrue(shared.getParent().isVisible(), "shared slot visible");

        pane.setLoading(false);

        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertNotNull(shared.getParent());
        assertTrue(shared.getParent().isVisible(), "shared slot remains visible");
    }

    /**
     * Verifies replacing the hidden slot invalidates layout even though the
     * displayed child is untouched.
     */
    @Test
    public void hiddenSlotReplacementInvalidatesLayout() {
        FixedRegion skeleton = new FixedRegion(10.0, 10.0, 50.0, 20.0, null);
        FixedRegion content = new FixedRegion(10.0, 10.0, 100.0, 40.0, null);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        layout(pane, 100.0, 40.0);
        assertFalse(pane.isNeedsLayout(), "clean after layout");

        pane.setContent(new FixedRegion(10.0, 10.0, 300.0, 80.0, null));
        assertTrue(pane.isNeedsLayout(), "hidden content replaced");
        assertClose(300.0, pane.prefWidth(-1.0), "pref follows new content");

        layout(pane, 300.0, 80.0);
        pane.setLoading(false);
        layout(pane, 300.0, 80.0);

        pane.setSkeleton(new FixedRegion(200.0, 90.0, 220.0, 100.0, null));
        assertTrue(pane.isNeedsLayout(), "hidden skeleton replaced");
        assertClose(200.0, pane.minWidth(-1.0), "min follows new skeleton");
    }

    /**
     * Verifies hidden slot internal size changes bubble to the pane while
     * loading remains unchanged.
     */
    @Test
    public void hiddenSlotInternalSizeChangeInvalidatesLayout() {
        MutablePrefRegion skeleton = new MutablePrefRegion(10.0, 10.0, 50.0, 20.0, null);
        MutablePrefRegion content = new MutablePrefRegion(10.0, 10.0, 100.0, 40.0, null);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        assertClose(100.0, pane.prefWidth(-1.0), "initial pref");
        layout(pane, 100.0, 40.0);
        assertFalse(pane.isNeedsLayout(), "clean after layout");

        content.setPrefSizeValue(300.0, 80.0);

        assertTrue(pane.isNeedsLayout(), "hidden content size changed");
        assertClose(300.0, pane.prefWidth(-1.0), "pref follows hidden content");
    }

    /**
     * Verifies unmanaged user slots are ignored by measurement, bias and
     * automatic layout.
     */
    @Test
    public void unmanagedSlotsAreIgnoredByMeasurementBiasAndLayout() {
        FixedRegion skeleton = new FixedRegion(30.0, 20.0, 80.0, 60.0,
                Orientation.VERTICAL);
        FixedRegion content = new FixedRegion(100.0, 90.0, 200.0, 120.0,
                Orientation.HORIZONTAL);
        content.setManaged(false);
        content.resizeRelocate(7.0, 8.0, 9.0, 10.0);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, false);

        assertSame(Orientation.VERTICAL, pane.getContentBias());
        assertClose(30.0, pane.minWidth(-1.0), "min width");
        assertClose(20.0, pane.minHeight(-1.0), "min height");
        assertClose(80.0, pane.prefWidth(-1.0), "pref width");
        assertClose(60.0, pane.prefHeight(-1.0), "pref height");

        layout(pane, 200.0, 100.0);

        assertClose(7.0, content.getLayoutX(), "unmanaged x");
        assertClose(8.0, content.getLayoutY(), "unmanaged y");
        assertClose(9.0, content.getLayoutBounds().getWidth(), "unmanaged width");
        assertClose(10.0, content.getLayoutBounds().getHeight(), "unmanaged height");
    }

    /**
     * Verifies preferred size is bounded by the content node's min/max range.
     */
    @Test
    public void preferredSizeIsBoundedByManagedContentRange() {
        FixedRegion skeleton = new FixedRegion(10.0, 10.0, 50.0, 20.0, null);
        FixedRegion content = new FixedRegion(20.0, 15.0, 300.0, 120.0,
                80.0, 60.0, null);
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);

        assertClose(80.0, pane.prefWidth(-1.0), "pref width");
        assertClose(60.0, pane.prefHeight(-1.0), "pref height");
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
        private final double maxWidth;
        private final double maxHeight;
        private final Orientation contentBias;

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight,
                            Orientation contentBias) {
            this(minWidth, minHeight, prefWidth, prefHeight,
                    Double.MAX_VALUE, Double.MAX_VALUE, contentBias);
        }

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight,
                            double maxWidth, double maxHeight,
                            Orientation contentBias) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
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

        @Override
        protected double computeMaxWidth(double height) {
            return maxWidth;
        }

        @Override
        protected double computeMaxHeight(double width) {
            return maxHeight;
        }
    }

    private static final class MutablePrefRegion extends Region {

        private final double minWidth;
        private final double minHeight;
        private final Orientation contentBias;
        private double prefWidth;
        private double prefHeight;

        private MutablePrefRegion(double minWidth, double minHeight,
                                  double prefWidth, double prefHeight,
                                  Orientation contentBias) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            this.contentBias = contentBias;
        }

        private void setPrefSizeValue(double prefWidth, double prefHeight) {
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            requestLayout();
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
