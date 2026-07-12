package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the {@link RXAutoCompletion} binding facade: the bind /
 * unbind / dispose registry lifecycle (via public behavior only), the three bind
 * overloads' list semantics, the guard contracts (NPE / IAE), configuration
 * defaults, the event-handler property wiring, and {@link
 * RXAutoCompletion#acceptAll()}. The interactive dropdown (show / filter /
 * keyboard / commit write-back) needs a focused field in a showing window and is
 * left to real-machine checks.
 */
public class RXAutoCompletionTest {

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

    // ==================== Registry lifecycle ====================

    @Test
    public void bindRegistersTheHandleOnTheField() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field);
            assertTrue(field.getProperties().containsValue(completion),
                    "the binding is attached to the field");
        });
    }

    @Test
    public void rebindSilentlyReplacesTheOldBinding() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> first = RXAutoCompletion.bind(field);
            RXAutoCompletion<String> second = RXAutoCompletion.bind(field);
            assertFalse(field.getProperties().containsValue(first),
                    "the replaced binding is detached");
            assertTrue(field.getProperties().containsValue(second),
                    "the new binding is attached");
        });
    }

    @Test
    public void disposingAStaleHandleDoesNotDetachTheNewBinding() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> first = RXAutoCompletion.bind(field);
            RXAutoCompletion<String> second = RXAutoCompletion.bind(field);
            first.dispose();
            assertTrue(field.getProperties().containsValue(second),
                    "a stale handle's dispose only cleans its own leftovers");
        });
    }

    @Test
    public void unbindDetachesAndIsIdempotent() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field);
            RXAutoCompletion.unbind(field);
            assertFalse(field.getProperties().containsValue(completion),
                    "unbind detaches the binding");
            RXAutoCompletion.unbind(field);
            assertFalse(field.getProperties().containsValue(completion), "unbind is idempotent");
        });
    }

    @Test
    public void disposeIsIdempotentAndClearsPopupShowing() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field);
            completion.dispose();
            completion.dispose();
            assertFalse(completion.isPopupShowing(), "popupShowing reads false after dispose");
            assertFalse(field.getProperties().containsValue(completion), "the handle is detached");
        });
    }

    @Test
    public void inputAfterUnbindNeverDrivesTheDropdown() throws InterruptedException {
        TextField field = new TextField();
        AtomicInteger filterCalls = new AtomicInteger();
        AtomicReference<RXAutoCompletion<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field, List.of("alpha", "beta"));
            ref.set(completion);
            // The filter runs before the focus check, so its invocation count observes
            // the debounced text pipeline even though the dropdown cannot open headless.
            completion.setFilterFunction(query -> {
                filterCalls.incrementAndGet();
                return item -> true;
            });
            field.setText("al");
        });
        // Outlive the 150ms filter debounce.
        Thread.sleep(400);
        runOnFx(() -> assertTrue(filterCalls.get() > 0,
                "while bound, typing reaches the filter pipeline"));
        runOnFx(() -> {
            RXAutoCompletion.unbind(field);
            filterCalls.set(0);
            field.setText("be");
        });
        Thread.sleep(400);
        runOnFx(() -> {
            assertEquals(0, filterCalls.get(),
                    "after unbind, typing no longer drives the filter pipeline");
            assertFalse(ref.get().isPopupShowing(), "and the dropdown state stays false");
        });
    }

    // ==================== Guards ====================

    @Test
    public void nullArgumentsThrowNpe() {
        assertThrows(NullPointerException.class, () -> RXAutoCompletion.bind(null));
        assertThrows(NullPointerException.class,
                () -> RXAutoCompletion.bind(null, FXCollections.observableArrayList()));
        assertThrows(NullPointerException.class,
                () -> RXAutoCompletion.bind(new TextField(), (ObservableList<String>) null));
        assertThrows(NullPointerException.class,
                () -> RXAutoCompletion.bind(new TextField(), (Collection<String>) null));
        assertThrows(NullPointerException.class, () -> RXAutoCompletion.unbind(null));
    }

    @Test
    public void bindingAnRXAutoCompleteFieldIsRejected() throws InterruptedException {
        runOnFx(() -> assertThrows(IllegalArgumentException.class,
                () -> RXAutoCompletion.bind(new RXAutoCompleteField()),
                "the control owns its own dropdown; binding would open two popups"));
    }

    // ==================== Bind overloads ====================

    @Test
    public void plainBindStartsWithAnEmptyLiveList() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(new TextField());
            assertTrue(completion.getSuggestions().isEmpty(), "starts empty");
            completion.getSuggestions().addAll("a", "b");
            assertEquals(2, completion.getSuggestions().size(),
                    "mutations are visible on the same list");
        });
    }

    @Test
    public void observableListOverloadAdoptsTheListAsLiveBacking() throws InterruptedException {
        runOnFx(() -> {
            ObservableList<String> external = FXCollections.observableArrayList("a");
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(new TextField(), external);
            assertSame(external, completion.getSuggestions(),
                    "the given list is adopted, not copied");
            external.setAll("x", "y");
            assertEquals(List.of("x", "y"), completion.getSuggestions(),
                    "external mutations are the binding's suggestions");
        });
    }

    @Test
    public void collectionOverloadCopiesWithoutWritingBack() throws InterruptedException {
        runOnFx(() -> {
            List<String> source = new ArrayList<>(List.of("a", "b"));
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(new TextField(), source);
            assertEquals(List.of("a", "b"), completion.getSuggestions(), "initial contents copied");
            completion.getSuggestions().add("c");
            assertEquals(List.of("a", "b"), source, "the source collection is never written back");
            source.add("d");
            assertEquals(List.of("a", "b", "c"), completion.getSuggestions(),
                    "later source changes are not reflected");
        });
    }

    // ==================== Configuration ====================

    @Test
    public void configurationDefaults() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(new TextField());
            assertNull(completion.getFilterFunction(),
                    "filter function defaults to null (display-text contains fallback)");
            assertNull(completion.getCompletionHandler(),
                    "completion handler defaults to null (display-text write-back fallback)");
            assertNull(completion.getConverter(), "converter defaults to null");
            assertNull(completion.getSuggestionCellFactory(),
                    "cell factory defaults to the built-in cell");
            assertNull(completion.getOnAutoCompleted(), "no completion observer by default");
            assertEquals(RXAutoCompletion.DEFAULT_VISIBLE_ROW_COUNT, completion.getVisibleRowCount(),
                    "default visible rows matches the popup default");
            assertTrue(completion.isAnimated(), "animated by default");
            assertFalse(completion.isPopupShowing(), "popupShowing defaults to false");
        });
    }

    @Test
    public void acceptAllAcceptsEverything() {
        assertTrue(RXAutoCompletion.<String>acceptAll().apply("query").test("unrelated"));
        assertTrue(RXAutoCompletion.<String>acceptAll().apply("").test("anything"));
    }

    @Test
    public void showAndHideAreSafeWithoutFocusAndAfterDispose() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field, List.of("a"));
            completion.showSuggestions();
            assertFalse(completion.isPopupShowing(),
                    "an unfocused field never opens the dropdown, even programmatically");
            completion.hideSuggestions();
            completion.dispose();
            completion.showSuggestions();
            completion.hideSuggestions();
            assertFalse(completion.isPopupShowing(), "show / hide are no-ops after dispose");
        });
    }

    // ==================== Events ====================

    @Test
    public void onAutoCompletedAttachesToTheBoundFieldAndDetachesOnDispose() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> completion = RXAutoCompletion.bind(field);
            AtomicReference<Object> received = new AtomicReference<>();
            completion.setOnAutoCompleted(event -> received.set(event.getItem()));
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Java", "Java"));
            assertEquals("Java", received.get(), "the handler observes events fired on the field");

            completion.setOnAutoCompleted(null);
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Kotlin", "Kotlin"));
            assertEquals("Java", received.get(), "clearing the handler detaches it");

            completion.setOnAutoCompleted(event -> received.set(event.getItem()));
            completion.dispose();
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Rust", "Rust"));
            assertEquals("Java", received.get(), "dispose detaches the handler");
        });
    }

    @Test
    public void staleHandleSetOnAutoCompletedDoesNotReattach() throws InterruptedException {
        runOnFx(() -> {
            TextField field = new TextField();
            RXAutoCompletion<String> stale = RXAutoCompletion.bind(field);
            stale.dispose();
            AtomicReference<Object> received = new AtomicReference<>();
            stale.setOnAutoCompleted(event -> received.set(event.getItem()));
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Go", "Go"));
            assertNull(received.get(),
                    "a disposed handle never attaches handlers to the field again");
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
