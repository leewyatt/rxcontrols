package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests RXWaveProgressIndicator skin layout of the centre progress labels.
 */
public class RXWaveProgressIndicatorTest {

    private static final double EPSILON = 0.01;

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

    @Test
    public void progressLabelsResizeWhenTheFontChanges() {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(0.6);
        StackPane root = new StackPane(indicator);
        new Scene(root, 200.0, 200.0);
        indicator.setPrefSize(140.0, 140.0);
        root.applyCss();
        root.layout();

        try {
            Label above = (Label) indicator.lookup(".progress-label");
            Label below = (Label) indicator.lookup(".progress-label.below-water");
            double restingWidth = above.getWidth();

            root.setStyle("-fx-font-size: 20px;");
            root.applyCss();
            root.layout();

            assertTrue(above.prefWidth(-1) > restingWidth, "the larger font needs a wider label");
            assertEquals(above.prefWidth(-1), above.getWidth(), EPSILON);
            assertEquals(above.getWidth(), below.getWidth(), EPSILON);
            assertEquals(above.getLayoutX(), below.getLayoutX(), EPSILON);
        } finally {
            indicator.getSkin().dispose();
        }
    }
}
