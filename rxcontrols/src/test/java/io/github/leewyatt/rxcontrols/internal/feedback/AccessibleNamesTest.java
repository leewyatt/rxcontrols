package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the fallback chain of the accessible name of a feedback button.
 */
public class AccessibleNamesTest {

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
        Platform.setImplicitExit(false);
    }

    @Test
    public void firstNonEmptyNameWins() {
        Rectangle graphic = new Rectangle(8.0, 8.0);
        graphic.setAccessibleText("Disk");
        Button button = new Button("Save", graphic);
        button.setAccessibleText("Save the document");

        assertEquals("Save the document", AccessibleNames.resolve(button, "Saving"));

        button.setAccessibleText("");
        assertEquals("Saving", AccessibleNames.resolve(button, "Saving"));
        assertEquals("Save", AccessibleNames.resolve(button, ""));
        assertEquals("Save", AccessibleNames.resolve(button, null));

        button.setText("");
        assertEquals("Disk", AccessibleNames.resolve(button, null));
    }

    @Test
    public void buttonWithoutAnyNameGivesItsOwnText() {
        Button empty = new Button("");
        assertEquals("", AccessibleNames.resolve(empty, null));

        Button none = new Button(null);
        assertNull(AccessibleNames.resolve(none, null));
    }
}
