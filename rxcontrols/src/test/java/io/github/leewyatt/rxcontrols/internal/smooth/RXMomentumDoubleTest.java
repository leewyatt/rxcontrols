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
}
