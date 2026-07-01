package io.github.leewyatt.rxcontrols.internal.smooth;

import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for smooth scroll event normalization, boundary policy and
 * immediate/smooth target handling.
 */
public class RXSmoothScrollEngineTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    @Test
    public void chainConsumesGlideTailWhileCurrentCanStillAbsorb() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.offsetY = 90.0;
        scrollable.maxY = 100.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertTrue(engine.handleScroll(scroll(0.0, -80.0), ScrollAxis.VERTICAL,
                Duration.millis(120.0), null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, false, true, false));
        assertTrue(engine.handleScroll(scroll(0.0, -20.0), ScrollAxis.VERTICAL,
                Duration.millis(120.0), null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, false, true, false),
                "target is at max, but current is still gliding toward it");
    }

    @Test
    public void chainReleasesWhenCurrentAndTargetAreAtBoundary() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.offsetY = 100.0;
        scrollable.maxY = 100.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertFalse(engine.handleScroll(scroll(0.0, -20.0), ScrollAxis.VERTICAL,
                Duration.ZERO, null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, false, false, false));
    }

    @Test
    public void containConsumesAtBoundary() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.offsetY = 100.0;
        scrollable.maxY = 100.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertTrue(engine.handleScroll(scroll(0.0, -20.0), ScrollAxis.VERTICAL,
                Duration.ZERO, null, 1.0, ScrollBoundaryPolicy.CONTAIN,
                true, false, false, false));
    }

    @Test
    public void reducedMotionAppliesTargetImmediately() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.maxY = 500.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertTrue(engine.handleScroll(scroll(0.0, -60.0), ScrollAxis.VERTICAL,
                Duration.millis(120.0), null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, true, true, false));
        assertEquals(60.0, scrollable.offsetY, 0.1);
        assertFalse(scrollable.lastSmoothFrame);
    }

    @Test
    public void lineTextDeltaUsesVerticalUnitIncrement() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.maxY = 500.0;
        scrollable.verticalUnit = 24.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertTrue(engine.handleScroll(textScroll(0.0, 0.0, 0.0, -3.0,
                        ScrollEvent.VerticalTextScrollUnits.LINES), ScrollAxis.VERTICAL,
                Duration.ZERO, null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, false, false, true));
        assertEquals(72.0, scrollable.offsetY, 0.1);
    }

    @Test
    public void shiftWheelMapsVerticalDeltaToHorizontalOffset() {
        FakeScrollable scrollable = new FakeScrollable();
        scrollable.maxX = 500.0;
        scrollable.maxY = 500.0;
        RXSmoothScrollEngine engine = new RXSmoothScrollEngine(scrollable);

        assertTrue(engine.handleScroll(shiftScroll(0.0, -50.0), ScrollAxis.BOTH,
                Duration.ZERO, null, 1.0, ScrollBoundaryPolicy.CHAIN,
                true, false, false, false));
        assertEquals(50.0, scrollable.offsetX, 0.1);
        assertEquals(0.0, scrollable.offsetY, 0.1);
    }

    private static ScrollEvent scroll(double deltaX, double deltaY) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                false, false, false, false,
                false, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0,
                null);
    }

    private static ScrollEvent shiftScroll(double deltaX, double deltaY) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                true, false, false, false,
                false, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0,
                null);
    }

    private static ScrollEvent textScroll(double deltaX, double deltaY, double textDeltaX, double textDeltaY,
                                          ScrollEvent.VerticalTextScrollUnits yUnits) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                false, false, false, false,
                false, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, textDeltaX,
                yUnits, textDeltaY,
                0,
                null);
    }

    private static final class FakeScrollable implements RXSmoothScrollable {
        private double offsetX;
        private double offsetY;
        private double maxX;
        private double maxY;
        private double verticalUnit = 20.0;
        private boolean lastSmoothFrame;

        @Override
        public Node eventNode() {
            return null;
        }

        @Override
        public double getOffsetX() {
            return offsetX;
        }

        @Override
        public double getOffsetY() {
            return offsetY;
        }

        @Override
        public void setOffsetX(double value, boolean smoothFrame) {
            offsetX = value;
            lastSmoothFrame = smoothFrame;
        }

        @Override
        public void setOffsetY(double value, boolean smoothFrame) {
            offsetY = value;
            lastSmoothFrame = smoothFrame;
        }

        @Override
        public double getMaxOffsetX() {
            return maxX;
        }

        @Override
        public double getMaxOffsetY() {
            return maxY;
        }

        @Override
        public double getViewportWidth() {
            return 200.0;
        }

        @Override
        public double getViewportHeight() {
            return 200.0;
        }

        @Override
        public double getHorizontalUnitIncrement() {
            return 10.0;
        }

        @Override
        public double getVerticalUnitIncrement() {
            return verticalUnit;
        }

        @Override
        public boolean isHorizontalWritable() {
            return maxX > 0.0;
        }

        @Override
        public boolean isVerticalWritable() {
            return maxY > 0.0;
        }
    }
}
