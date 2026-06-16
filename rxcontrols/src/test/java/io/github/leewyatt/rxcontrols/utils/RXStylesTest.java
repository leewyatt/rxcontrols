package io.github.leewyatt.rxcontrols.utils;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXStyles}: style-class operations on any {@code Styleable}
 * (exercised through a non-node {@link MenuItem}) and stylesheet operations on a
 * {@link javafx.scene.Parent} and {@link Scene}.
 */
public class RXStylesTest {

    /**
     * Starts the JavaFX toolkit so a {@link Scene} can be constructed and mutated
     * on the application thread.
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

    // ==================== Style Class ====================

    @Test
    public void addClassIsAddIfAbsent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().clear();
        RXStyles.addClass(item, "a", "b");
        RXStyles.addClass(item, "a", "c");
        assertEquals(List.of("a", "b", "c"), item.getStyleClass());
    }

    @Test
    public void removeClassRemovesAllOccurrences() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "a", "c");
        RXStyles.removeClass(item, "a");
        assertEquals(List.of("b", "c"), item.getStyleClass());
    }

    @Test
    public void removeClassRemovesMultipleDifferent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "c", "d");
        RXStyles.removeClass(item, "a", "c");
        assertEquals(List.of("b", "d"), item.getStyleClass());
    }

    @Test
    public void toggleClassFlipsEach() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a");
        RXStyles.toggleClass(item, "a", "b");
        assertEquals(List.of("b"), item.getStyleClass());
    }

    @Test
    public void conditionalToggleAddsWhenPresentTrue() {
        MenuItem item = new MenuItem();
        item.getStyleClass().clear();
        RXStyles.toggleClass(item, true, "on");
        assertTrue(RXStyles.hasClass(item, "on"));
        RXStyles.toggleClass(item, true, "on");
        assertEquals(List.of("on"), item.getStyleClass());
    }

    @Test
    public void conditionalToggleRemovesWhenPresentFalse() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("on");
        RXStyles.toggleClass(item, false, "on");
        assertFalse(RXStyles.hasClass(item, "on"));
    }

    @Test
    public void replaceClassSwapsInPlace() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("x", "old", "y");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("x", "new", "y"), item.getStyleClass());
    }

    @Test
    public void replaceClassAppendsWhenOldAbsent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("x");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("x", "new"), item.getStyleClass());
    }

    @Test
    public void replaceClassDropsOldWhenNewAlreadyPresent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("old", "new");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("new"), item.getStyleClass());
    }

    @Test
    public void replaceClassOldEqualsNewIsNoOp() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b");
        RXStyles.replaceClass(item, "a", "a");
        assertEquals(List.of("a", "b"), item.getStyleClass());
    }

    @Test
    public void replaceClassRemovesDuplicateOld() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("old", "x", "old");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("new", "x"), item.getStyleClass());
    }

    @Test
    public void distinctClassRemovesDuplicates() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "a", "b", "c");
        RXStyles.distinctClass(item);
        assertEquals(List.of("a", "b", "c"), item.getStyleClass());
    }

    @Test
    public void distinctClassDoesNotMutateWhenNoDuplicates() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "c");
        AtomicInteger changes = new AtomicInteger();
        item.getStyleClass().addListener((ListChangeListener<String>) c -> changes.incrementAndGet());
        RXStyles.distinctClass(item);
        assertEquals(0, changes.get());
        assertEquals(List.of("a", "b", "c"), item.getStyleClass());
    }

    @Test
    public void distinctOnlyRemovesDuplicateOccurrences() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "a");
        List<String> removed = new ArrayList<>();
        item.getStyleClass().addListener((ListChangeListener<String>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    removed.addAll(c.getRemoved());
                }
            }
        });
        RXStyles.distinctClass(item);
        // Only the duplicate "a" is reported removed; the kept "b" is never touched.
        assertEquals(List.of("a"), removed);
        assertEquals(List.of("a", "b"), item.getStyleClass());
    }

    @Test
    public void clearClassEmptiesList() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b");
        RXStyles.clearClass(item);
        assertTrue(item.getStyleClass().isEmpty());
    }

    @Test
    public void nullTargetThrows() {
        assertThrows(NullPointerException.class, () -> RXStyles.addClass(null, "a"));
    }

    // ==================== Stylesheet (Parent) ====================

    @Test
    public void addSheetsIsAddIfAbsent() {
        Group root = new Group();
        RXStyles.addSheets(root, "one.css", "two.css");
        RXStyles.addSheets(root, "one.css", "three.css");
        assertEquals(List.of("one.css", "two.css", "three.css"), root.getStylesheets());
    }

    @Test
    public void removeSheetsRemovesAllOccurrences() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css", "b.css", "a.css");
        RXStyles.removeSheets(root, "a.css");
        assertEquals(List.of("b.css"), root.getStylesheets());
    }

    @Test
    public void toggleSheetsFlipsEach() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css");
        RXStyles.toggleSheets(root, "a.css", "b.css");
        assertEquals(List.of("b.css"), root.getStylesheets());
    }

    @Test
    public void replaceSheetsSwapsTheme() {
        Group root = new Group();
        String[] all = {"sunset.css", "ocean.css"};
        root.getStylesheets().setAll(all);
        RXStyles.replaceSheets(root, all, "ocean.css");
        assertEquals(List.of("ocean.css"), root.getStylesheets());
        assertTrue(RXStyles.hasSheet(root, "ocean.css"));
        assertFalse(RXStyles.hasSheet(root, "sunset.css"));
    }

    @Test
    public void replaceSheetsEmptyRemovesIsPureAdd() {
        Group root = new Group();
        root.getStylesheets().setAll("keep.css");
        RXStyles.replaceSheets(root, new String[]{}, "new.css");
        assertEquals(List.of("keep.css", "new.css"), root.getStylesheets());
    }

    @Test
    public void replaceSheetsEmptyAddsIsPureRemove() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css", "b.css");
        RXStyles.replaceSheets(root, new String[]{"a.css"});
        assertEquals(List.of("b.css"), root.getStylesheets());
    }

    @Test
    public void distinctSheetsRemovesDuplicates() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css", "a.css", "b.css");
        RXStyles.distinctSheets(root);
        assertEquals(List.of("a.css", "b.css"), root.getStylesheets());
    }

    @Test
    public void distinctSheetsOnlyRemovesDuplicateOccurrences() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css", "b.css", "a.css", "b.css", "c.css");
        List<String> removed = new ArrayList<>();
        root.getStylesheets().addListener((ListChangeListener<String>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    removed.addAll(c.getRemoved());
                }
            }
        });
        RXStyles.distinctSheets(root);
        // Only the duplicate occurrences are reported removed; first a/b/c stay put.
        assertEquals(2, removed.size());
        assertTrue(removed.contains("a.css"));
        assertTrue(removed.contains("b.css"));
        assertFalse(removed.contains("c.css"));
        assertEquals(List.of("a.css", "b.css", "c.css"), root.getStylesheets());
    }

    @Test
    public void clearSheetsEmptiesList() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css");
        RXStyles.clearSheets(root);
        assertTrue(root.getStylesheets().isEmpty());
    }

    // ==================== Stylesheet (Scene) ====================

    @Test
    public void sceneSheetOverloadsForwardToSameLogic() {
        runFx(() -> {
            Scene scene = new Scene(new Group());

            RXStyles.addSheets(scene, "a.css", "b.css", "a.css");
            assertEquals(List.of("a.css", "b.css"), scene.getStylesheets());
            assertTrue(RXStyles.hasSheet(scene, "a.css"));

            RXStyles.toggleSheets(scene, "a.css");
            assertFalse(RXStyles.hasSheet(scene, "a.css"));

            RXStyles.replaceSheets(scene, new String[]{"b.css"}, "c.css");
            assertEquals(List.of("c.css"), scene.getStylesheets());

            scene.getStylesheets().setAll("x.css", "x.css", "y.css");
            RXStyles.distinctSheets(scene);
            assertEquals(List.of("x.css", "y.css"), scene.getStylesheets());

            RXStyles.removeSheets(scene, "x.css");
            assertEquals(List.of("y.css"), scene.getStylesheets());

            RXStyles.clearSheets(scene);
            assertTrue(scene.getStylesheets().isEmpty());
        });
    }

    /**
     * Runs the body on the JavaFX application thread and rethrows any assertion
     * failure on the calling thread.
     */
    private static void runFx(Runnable body) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }
}
