package io.github.leewyatt.rxcontrols.internal.popup;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the data model, filtered-view stability, keyboard bridge,
 * and height computation of {@link RXSuggestionPopup}. None require a shown
 * window; the visual show / animation paths are left to real-machine checks.
 */
public class RXSuggestionPopupTest {

    /**
     * Starts the JavaFX toolkit before constructing controls.
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

    @Test
    public void filteredReflectsSuggestionsAndPredicate() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("apple", "banana", "berry"));
            assertEquals(3, popup.getFilteredSuggestions().size(), "all pass with no predicate");
            popup.setFilterPredicate(s -> s.startsWith("b"));
            assertEquals(2, popup.getFilteredSuggestions().size(), "predicate narrows the view");
            assertTrue(popup.getFilteredSuggestions().containsAll(FXCollections.observableArrayList("banana", "berry")));
        });
    }

    @Test
    public void mutatingInitialSuggestionsListBeforeSetIsMirrored() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            // No setSuggestions() call: mutate the default live list directly. The
            // constructor observes the initial list, so this must still mirror.
            popup.getSuggestions().addAll("alpha", "beta");
            assertEquals(2, popup.getFilteredSuggestions().size(),
                    "mutations to the initial list flow into the filtered view");
        });
    }

    @Test
    public void nullSuggestionsTreatedAsEmpty() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("a", "b"));
            popup.setSuggestions(null);
            assertEquals(0, popup.getFilteredSuggestions().size(), "null source is treated as empty");
        });
    }

    @Test
    public void filteredViewReferenceStableAcrossSourceSwap() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            ObservableList<String> before = popup.getFilteredSuggestions();
            popup.setSuggestions(FXCollections.observableArrayList("x", "y"));
            ObservableList<String> after = popup.getFilteredSuggestions();
            assertSame(before, after, "filtered view reference is stable across source swaps");
            assertEquals(2, after.size(), "and it reflects the new source");
        });
    }

    @Test
    public void filteredViewIsUnmodifiable() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            assertThrows(UnsupportedOperationException.class,
                    () -> popup.getFilteredSuggestions().add("nope"));
        });
    }

    @Test
    public void sourceListMutationIsMirrored() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            ObservableList<String> source = FXCollections.observableArrayList("a");
            popup.setSuggestions(source);
            source.add("b");
            assertEquals(2, popup.getFilteredSuggestions().size(), "mutating the source updates the backing");
        });
    }

    @Test
    public void moveHighlightSelectsClampsAndReturnsItem() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("a", "b", "c"));
            assertNull(popup.highlightedItem(), "nothing highlighted initially");
            popup.moveHighlight(1);
            assertEquals("a", popup.highlightedItem(), "first down highlights the first item");
            popup.moveHighlight(1);
            popup.moveHighlight(1);
            assertEquals("c", popup.highlightedItem());
            popup.moveHighlight(1);
            assertEquals("c", popup.highlightedItem(), "clamped at the last item");
            popup.moveHighlight(-1);
            assertEquals("b", popup.highlightedItem(), "up moves the highlight back");
        });
    }

    @Test
    public void selectHighlightedFiresCallback() throws InterruptedException {
        AtomicReference<String> committed = new AtomicReference<>();
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setOnSuggestionSelected(committed::set);
            popup.setSuggestions(FXCollections.observableArrayList("a", "b"));
            popup.moveHighlight(1);
            String result = popup.selectHighlighted();
            assertEquals("a", result, "returns the committed item");
        });
        assertEquals("a", committed.get(), "callback receives the committed item");
    }

    @Test
    public void filterChangeResetsHighlight() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("apple", "banana", "berry"));
            popup.moveHighlight(1);
            assertEquals("apple", popup.highlightedItem());
            popup.setFilterPredicate(s -> s.startsWith("b"));
            assertNull(popup.highlightedItem(), "changing the filter clears the highlight");
        });
    }

    @Test
    public void moveHighlightSkipsDisabledItems() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("a", "b", "c", "d"));
            popup.setDisabledPredicate(s -> s.equals("b") || s.equals("c"));

            popup.moveHighlight(1);
            assertEquals("a", popup.highlightedItem(), "first down lands on the first enabled item");
            popup.moveHighlight(1);
            assertEquals("d", popup.highlightedItem(), "down skips the disabled b and c");
            popup.moveHighlight(1);
            assertEquals("d", popup.highlightedItem(), "no enabled item past d: highlight stays");
            popup.moveHighlight(-1);
            assertEquals("a", popup.highlightedItem(), "up skips c and b back to a");
        });
    }

    @Test
    public void allDisabledYieldsNoHighlightNorCommit() throws InterruptedException {
        AtomicReference<String> committed = new AtomicReference<>();
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setOnSuggestionSelected(committed::set);
            popup.setSuggestions(FXCollections.observableArrayList("a", "b"));
            popup.setDisabledPredicate(s -> true);

            popup.moveHighlight(1);
            assertNull(popup.highlightedItem(), "no selectable item can be highlighted");
            assertNull(popup.selectHighlighted(), "nothing to commit");
        });
        assertNull(committed.get(), "no callback for an all-disabled list");
    }

    @Test
    public void nullDisabledPredicateSelectsEveryItem() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("a", "b", "c"));

            popup.moveHighlight(1);
            assertEquals("a", popup.highlightedItem());
            popup.moveHighlight(1);
            assertEquals("b", popup.highlightedItem(), "with no disabled predicate nothing is skipped");
        });
    }

    @Test
    public void disposeIsSafe() throws InterruptedException {
        runOnFx(() -> {
            RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
            popup.setSuggestions(FXCollections.observableArrayList("a"));
            popup.dispose();
            popup.dispose();
            assertFalse(popup.isShowing());
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
