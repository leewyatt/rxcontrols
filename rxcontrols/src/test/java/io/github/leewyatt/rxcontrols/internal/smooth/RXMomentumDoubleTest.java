package io.github.leewyatt.rxcontrols.internal.smooth;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the internal kinetic double value driver used by momentum
 * scrolling.
 */
public class RXMomentumDoubleTest {

    private static final long MILLIS = 1_000_000L;

    @Test
    public void pushConvertsDeltaToDecayingMotion() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);
        List<Double> updates = new ArrayList<>();
        value.setOnUpdate(updates::add);

        value.push(100.0);
        now.set(100 * MILLIS);
        value.handlePulse(now.get());

        assertTrue(value.currentValue() > 0.0, "momentum advances after a pulse");
        assertTrue(value.velocity() > 0.0, "velocity decays but keeps direction");
        assertFalse(updates.isEmpty(), "pulse writes an update");
    }

    @Test
    public void shiftByMovesCurrentWithoutChangingVelocity() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 10.0);

        value.push(100.0);
        double velocity = value.velocity();
        value.shiftBy(25.0);

        assertEquals(35.0, value.currentValue(), 0.1);
        assertEquals(velocity, value.velocity(), 0.1);
    }

    @Test
    public void repeatedPushRestartsMaxDurationWindow() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        value.push(100.0);
        now.set(1_240 * MILLIS);
        value.handlePulse(now.get());
        assertTrue(value.isRunning(), "first momentum is still inside the max-duration window");

        now.set(1_245 * MILLIS);
        value.push(100.0);
        now.set(1_260 * MILLIS);
        value.handlePulse(now.get());

        assertTrue(value.isRunning(), "a fresh wheel push restarts the max-duration window");
        assertTrue(value.velocity() > 0.0, "the second push remains active after the next pulse");
    }

    @Test
    public void repeatedPushAdvancesBeforeAddingImpulse() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        value.push(100.0);
        now.set(100 * MILLIS);
        value.push(100.0);

        assertTrue(value.currentValue() > 0.0, "running push advances old velocity to the push time first");
    }

    @Test
    public void pushTravelsItsDeltaAtAnyFrameRate() {
        // A wheel step scrolls as far as without smooth scrolling, whatever the frame rate.
        for (long frameMillis : new long[]{8, 16, 33}) {
            AtomicLong now = new AtomicLong();
            RXMomentumDouble value = momentum(now, 0.0);

            value.push(100.0);
            settle(value, now, frameMillis);

            assertEquals(100.0, value.currentValue(), 1.0, "frames of " + frameMillis + " ms");
        }
    }

    @Test
    public void repeatedPushesAddUp() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        value.push(60.0);
        for (int frame = 0; frame < 6; frame++) {
            now.addAndGet(16 * MILLIS);
            value.handlePulse(now.get());
        }
        value.push(40.0);
        settle(value, now, 16);

        assertEquals(100.0, value.currentValue(), 1.0);
    }

    @Test
    public void slowSmallPushesAddUp() {
        // A slow trackpad drag: one small push per frame. Each push must carry on from the
        // motion before it instead of ending after a single frame.
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        for (int frame = 0; frame < 60; frame++) {
            value.push(0.25);
            now.addAndGet(16 * MILLIS);
            value.handlePulse(now.get());
        }
        settle(value, now, 16);

        assertEquals(15.0, value.currentValue(), 1.0, "the pushes travel their sum");
    }

    @Test
    public void pushesTooSmallToOutlastAFrameTravelTheirDelta() {
        // A slower drag still: each push is below the stop threshold after one frame.
        // Its remaining coast is travelled at once instead of being dropped.
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        for (int frame = 0; frame < 60; frame++) {
            value.push(0.1);
            now.addAndGet(16 * MILLIS);
            value.handlePulse(now.get());
        }
        settle(value, now, 16);

        assertEquals(6.0, value.currentValue(), 0.01, "the pushes travel their sum");
    }

    @Test
    public void momentumSettlesWithinMaxDuration() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);

        value.push(100.0);
        for (int frame = 0; frame < 80 && value.isRunning(); frame++) {
            now.addAndGet(16 * MILLIS);
            value.handlePulse(now.get());
        }

        assertFalse(value.isRunning(), "momentum stops within its maximum duration");
        assertEquals(0.0, value.velocity(), 0.0);
    }

    @Test
    public void stopAtDoesNotNotifyUpdate() {
        AtomicLong now = new AtomicLong();
        RXMomentumDouble value = momentum(now, 0.0);
        List<Double> updates = new ArrayList<>();
        value.setOnUpdate(updates::add);

        value.push(100.0);
        value.stopAt(40.0);

        assertFalse(value.isRunning());
        assertEquals(40.0, value.currentValue(), 0.1);
        assertEquals(0.0, value.velocity(), 0.1);
        assertTrue(updates.isEmpty(), "internal stopAt only syncs state");
    }

    private static RXMomentumDouble momentum(AtomicLong now, double initial) {
        return new RXMomentumDouble(initial, now::get, false);
    }

    private static void settle(RXMomentumDouble value, AtomicLong now, long frameMillis) {
        for (int frame = 0; frame < 500 && value.isRunning(); frame++) {
            now.addAndGet(frameMillis * MILLIS);
            value.handlePulse(now.get());
        }
        assertFalse(value.isRunning(), "momentum settles");
    }
}
