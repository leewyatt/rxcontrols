package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXSegmentedStepIndicatorSkin;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the RXSegmentedStepIndicator skin lifecycle.
 */
public class RXSegmentedStepIndicatorTest {

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

    // ==================== Skin lifecycle ====================

    @Test
    public void replacementSkinKeepsItsNodes() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        StackPane root = new StackPane(indicator);
        new Scene(root, 320.0, 80.0);
        root.applyCss();
        root.layout();
        // One track and one fill per step.
        assertEquals(6, indicator.getChildrenUnmodifiable().size());

        // A skin of the installed class is ignored by setSkin, hence the subclass.
        indicator.setSkin(new RXSegmentedStepIndicatorSkin(indicator) {
        });

        assertEquals(6, indicator.getChildrenUnmodifiable().size());
        indicator.getSkin().dispose();
    }
}
