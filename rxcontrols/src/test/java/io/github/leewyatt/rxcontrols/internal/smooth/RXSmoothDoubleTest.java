package io.github.leewyatt.rxcontrols.internal.smooth;

import javafx.animation.Interpolator;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the internal retargetable double value driver used by smooth
 * scrolling.
 */
public class RXSmoothDoubleTest {

    private static final long MILLIS = 1_000_000L;

    @Test
    public void retargetContinuesFromRenderedCurrentValue() {
        AtomicLong now = new AtomicLong();
        RXSmoothDouble value = smooth(now, 0.0);

        value.animateTo(100.0);
        now.set(50 * MILLIS);
        value.handlePulse(now.get());
        assertEquals(50.0, value.currentValue(), 0.1);

        value.animateTo(200.0);
        now.set(100 * MILLIS);
        value.handlePulse(now.get());
        assertEquals(125.0, value.currentValue(), 0.1,
                "retarget starts from the rendered value, not the old start");
    }

    @Test
    public void shiftByTranslatesRunningBaselineWithoutRestartingProgress() {
        AtomicLong now = new AtomicLong();
        RXSmoothDouble value = smooth(now, 0.0);

        value.animateTo(100.0);
        now.set(50 * MILLIS);
        value.handlePulse(now.get());
        value.shiftBy(20.0);

        now.set(75 * MILLIS);
        value.handlePulse(now.get());
        assertEquals(95.0, value.currentValue(), 0.1,
                "progress is preserved after the baseline shift");
        assertEquals(120.0, value.targetValue(), 0.1);
    }

    @Test
    public void stopDoesNotNotifyUpdateOrFinished() {
        AtomicLong now = new AtomicLong();
        RXSmoothDouble value = smooth(now, 0.0);
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        value.setOnUpdate(v -> updates.incrementAndGet());
        value.setOnFinished(finished::incrementAndGet);

        value.animateTo(100.0);
        now.set(50 * MILLIS);
        value.stop();

        assertEquals(0, updates.get(), "stop does not write an update");
        assertEquals(0, finished.get(), "stop does not finish");
        assertFalse(value.isRunning());
        assertEquals(value.currentValue(), value.targetValue(), 0.1);
    }

    @Test
    public void snapToUpdatesWithoutFinishedAndFinishUpdatesWithFinished() {
        AtomicLong now = new AtomicLong();
        RXSmoothDouble value = smooth(now, 0.0);
        List<Double> updates = new ArrayList<>();
        AtomicInteger finished = new AtomicInteger();
        value.setOnUpdate(updates::add);
        value.setOnFinished(finished::incrementAndGet);

        value.animateTo(100.0);
        value.snapTo(40.0);
        assertEquals(List.of(40.0), updates);
        assertEquals(0, finished.get());

        value.animateTo(80.0);
        value.finish();
        assertEquals(80.0, updates.get(updates.size() - 1), 0.1);
        assertEquals(1, finished.get());
    }

    @Test
    public void disposeIsIdempotentAndIgnoresLaterCommands() {
        AtomicLong now = new AtomicLong();
        RXSmoothDouble value = smooth(now, 0.0);
        value.dispose();
        value.dispose();
        value.animateTo(100.0);
        value.snapTo(50.0);
        assertFalse(value.isRunning());
        assertEquals(0.0, value.currentValue(), 0.1);
    }

    private static RXSmoothDouble smooth(AtomicLong now, double initial) {
        RXSmoothDouble value = new RXSmoothDouble(initial, now::get, false);
        value.setDuration(Duration.millis(100.0));
        value.setInterpolator(Interpolator.LINEAR);
        return value;
    }
}
