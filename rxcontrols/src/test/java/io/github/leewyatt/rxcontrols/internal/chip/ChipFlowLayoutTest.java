package io.github.leewyatt.rxcontrols.internal.chip;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Headless tests for {@link ChipFlowLayout}'s gap handling: the rendered gaps come
 * from the supplied values but are clamped (hgap to at least one so the editor always
 * has room, vgap to at least zero), independent of what CSS sets on the control.
 */
public class ChipFlowLayoutTest {

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
    public void rowGapReflectsSupplierWhenNonNegative() throws InterruptedException {
        runOnFx(() -> {
            ChipEditor editor = new ChipEditor(() -> 60.0);
            ChipFlowLayout layout = new ChipFlowLayout(editor, () -> 60.0, () -> 6.0, () -> 4.0);
            assertEquals(4.0, layout.rowGap(), "a non-negative vgap passes through unclamped");
        });
    }

    @Test
    public void rowGapClampsNegativeVgapToZero() throws InterruptedException {
        runOnFx(() -> {
            ChipEditor editor = new ChipEditor(() -> 60.0);
            ChipFlowLayout layout = new ChipFlowLayout(editor, () -> 60.0, () -> 6.0, () -> -5.0);
            assertEquals(0.0, layout.rowGap(), "a negative vgap clamps to zero at render time");
        });
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
