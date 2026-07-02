package io.github.leewyatt.rxcontrols;

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
 * Headless tests for the {@link RXAutoComplete} control contract: the default
 * filter function, default write-back handler, live suggestions list, and style
 * class. The interactive dropdown (show / keyboard navigation / focus) is left to
 * real-machine checks.
 */
public class RXAutoCompleteTest {

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
        assertTrue(RXAutoComplete.DEFAULT_FILTER_FUNCTION.apply("ST").test("United States"),
                "case-insensitive substring match");
        assertTrue(RXAutoComplete.DEFAULT_FILTER_FUNCTION.apply("united").test("UNITED KINGDOM"),
                "case-insensitive both ways");
        assertFalse(RXAutoComplete.DEFAULT_FILTER_FUNCTION.apply("xyz").test("United States"),
                "no match");
        assertFalse(RXAutoComplete.DEFAULT_FILTER_FUNCTION.apply("a").test(null),
                "null candidate never matches");
        assertTrue(RXAutoComplete.DEFAULT_FILTER_FUNCTION.apply("").test("anything"),
                "empty query matches all");
    }

    @Test
    public void defaultOnAutoCompletedWritesBackAndMovesCaret() throws InterruptedException {
        runOnFx(() -> {
            RXAutoComplete field = new RXAutoComplete();
            field.getOnAutoCompleted().accept("Germany");
            assertEquals("Germany", field.getText(), "default handler writes the item back");
            assertEquals("Germany".length(), field.getCaretPosition(), "caret moves to the end");
        });
    }

    @Test
    public void suggestionsIsALiveList() throws InterruptedException {
        runOnFx(() -> {
            RXAutoComplete field = new RXAutoComplete();
            assertTrue(field.getSuggestions().isEmpty(), "starts empty");
            field.getSuggestions().addAll("a", "b");
            assertEquals(2, field.getSuggestions().size(), "mutations are visible on the same list");
        });
    }

    @Test
    public void defaultsAndStyleClass() throws InterruptedException {
        runOnFx(() -> {
            RXAutoComplete field = new RXAutoComplete();
            assertEquals(RXAutoComplete.DEFAULT_FILTER_FUNCTION, field.getFilterFunction(),
                    "default filter function is the shared constant");
            assertNull(field.getConverter(), "converter defaults to null");
            assertEquals(RXAutoComplete.DEFAULT_VISIBLE_ROW_COUNT, field.getVisibleRowCount(),
                    "default visible rows matches the popup default");
            assertTrue(field.isAnimated(), "animated by default");
            assertTrue(field.getStyleClass().contains("rx-auto-complete"), "carries its own style class");
            assertTrue(field.getStyleClass().contains("rx-text-field"), "inherits the text-field style class");
        });
    }

    @Test
    public void nullFilterFunctionIsTolerated() throws InterruptedException {
        runOnFx(() -> {
            RXAutoComplete field = new RXAutoComplete();
            field.setFilterFunction(null);
            assertNull(field.getFilterFunction(), "null is accepted (skin falls back to the default)");
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
