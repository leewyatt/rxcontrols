package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXCascader}.
 */
public class RXCascaderTest {

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
     * Verifies the public showing requests update the read-only showing state.
     */
    @Test
    public void showAndHideUpdateShowingState() {
        RXCascader<String> cascader = new RXCascader<>();

        assertFalse(cascader.isShowing());

        cascader.show();
        assertTrue(cascader.isShowing());

        cascader.hide();
        assertFalse(cascader.isShowing());

        cascader.setDisable(true);
        cascader.show();
        assertFalse(cascader.isShowing());
    }
}
