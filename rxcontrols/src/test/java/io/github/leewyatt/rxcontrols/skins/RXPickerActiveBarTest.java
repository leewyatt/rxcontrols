package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the bar a range picker draws under its active field.
 */
public class RXPickerActiveBarTest {

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    /**
     * Verifies an active field is underlined without resizing the picker or moving its text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void activeFieldIsUnderlinedWithoutResizing() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = new RXDateRangePicker();
            VBox root = new VBox(picker);
            new Scene(root, 520, 240);
            root.applyCss();
            root.layout();
            RXDateRangePickerSkin skin = (RXDateRangePickerSkin) picker.getSkin();
            List<TextField> editors = new ArrayList<>();
            for (Node child : picker.getChildrenUnmodifiable()) {
                if (child instanceof TextField) {
                    editors.add((TextField) child);
                }
            }
            TextField start = editors.get(0);
            TextField end = editors.get(1);
            double prefWidth = picker.prefWidth(-1);
            double prefHeight = picker.prefHeight(-1);
            Insets endInsets = end.getInsets();
            assertNull(picker.lookup(".active-bar"));

            skin.setFieldActive(end, true);
            root.applyCss();
            root.layout();
            assertEquals(prefWidth, picker.prefWidth(-1));
            assertEquals(prefHeight, picker.prefHeight(-1));
            assertEquals(endInsets, end.getInsets());
            Node bar = picker.lookup(".active-bar");
            assertNotNull(bar);
            assertTrue(bar.isVisible());
            Bounds underEnd = bar.getBoundsInParent();
            assertEquals(end.getLayoutX() + end.snappedLeftInset(), underEnd.getMinX(), 0.01);
            assertEquals(end.getLayoutX() + end.getWidth(), underEnd.getMaxX(), 0.01);
            assertEquals(picker.getHeight(), underEnd.getMaxY(), 0.01);
            assertTrue(underEnd.getHeight() > 0.0);

            skin.setFieldActive(end, false);
            skin.setFieldActive(start, true);
            root.layout();
            assertEquals(start.getLayoutX() + start.snappedLeftInset(), bar.getBoundsInParent().getMinX(), 0.01);

            skin.setFieldActive(start, false);
            assertFalse(bar.isVisible());

            picker.setSkin(null);
            assertNull(picker.lookup(".active-bar"));
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
