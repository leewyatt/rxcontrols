package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the {@link RXAutoCompleteField} control contract: the default
 * filter function, default completion handler, the auto-completed event wiring,
 * live suggestions list, and style class. The interactive dropdown (show / keyboard
 * navigation / focus) is left to real-machine checks.
 */
public class RXAutoCompleteFieldTest {

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
    public void defaultFilterFunctionIsCaseInsensitiveSubstring() {
        assertTrue(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION.apply("ST").test("United States"),
                "case-insensitive substring match");
        assertTrue(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION.apply("united").test("UNITED KINGDOM"),
                "case-insensitive both ways");
        assertFalse(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION.apply("xyz").test("United States"),
                "no match");
        assertFalse(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION.apply("a").test(null),
                "null candidate never matches");
        assertTrue(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION.apply("").test("anything"),
                "empty query matches all");
    }

    @Test
    public void defaultCompletionHandlerWritesBackAndMovesCaret() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            field.getCompletionHandler().accept("Germany");
            assertEquals("Germany", field.getText(), "default handler writes the item back");
            assertEquals("Germany".length(), field.getCaretPosition(), "caret moves to the end");
        });
    }

    @Test
    public void onAutoCompletedReceivesTheCompletionViaEventWiring() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            AtomicReference<String> completed = new AtomicReference<>();
            field.setOnAutoCompleted(event -> completed.set(event.getCompletion()));
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Japan"));
            assertEquals("Japan", completed.get(),
                    "onAutoCompleted is wired to the COMPLETED event type");
            field.setOnAutoCompleted(null);
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Chile"));
            assertEquals("Japan", completed.get(), "clearing the handler detaches it");
        });
    }

    @Test
    public void suggestionsIsALiveList() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            assertTrue(field.getSuggestions().isEmpty(), "starts empty");
            field.getSuggestions().addAll("a", "b");
            assertEquals(2, field.getSuggestions().size(), "mutations are visible on the same list");
        });
    }

    @Test
    public void defaultsAndStyleClass() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            assertEquals(RXAutoCompleteField.DEFAULT_FILTER_FUNCTION, field.getFilterFunction(),
                    "default filter function is the shared constant");
            assertNull(field.getConverter(), "converter defaults to null");
            assertNull(field.getSuggestionCellFactory(), "cell factory defaults to the built-in cell");
            assertNull(field.getOnAutoCompleted(), "no completion observer by default");
            assertEquals(RXAutoCompleteField.DEFAULT_VISIBLE_ROW_COUNT, field.getVisibleRowCount(),
                    "default visible rows matches the popup default");
            assertTrue(field.isAnimated(), "animated by default");
            assertTrue(field.getStyleClass().contains("rx-auto-complete-field"),
                    "carries its own style class");
            assertTrue(field.getStyleClass().contains("rx-text-field"),
                    "inherits the text-field style class");
        });
    }

    @Test
    public void nullFilterFunctionAndCompletionHandlerAreTolerated() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            field.setFilterFunction(null);
            assertNull(field.getFilterFunction(), "null is accepted (skin falls back to the default)");
            field.setCompletionHandler(null);
            assertNull(field.getCompletionHandler(),
                    "null is accepted (skin falls back to the built-in write-back)");
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
