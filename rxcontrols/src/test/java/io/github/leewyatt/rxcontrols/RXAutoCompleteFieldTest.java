package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import io.github.leewyatt.rxcontrols.skins.RXAutoCompleteFieldSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
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
            AtomicReference<Object> item = new AtomicReference<>();
            field.setOnAutoCompleted(event -> {
                completed.set(event.getCompletion());
                item.set(event.getItem());
            });
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Japan", "Japan"));
            assertEquals("Japan", completed.get(),
                    "onAutoCompleted is wired to the COMPLETED event type");
            assertEquals("Japan", item.get(), "the event carries the original item");
            field.setOnAutoCompleted(null);
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, "Chile", "Chile"));
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

    @Test
    public void popupShowingDefaultsFalseAndDrivesThePseudoClass() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            PseudoClass popupShowing = PseudoClass.getPseudoClass("popup-showing");
            assertFalse(field.isPopupShowing(), "popupShowing defaults to false");
            assertFalse(field.getPseudoClassStates().contains(popupShowing));
            field.setPopupShowing(true);
            assertTrue(field.getPseudoClassStates().contains(popupShowing));
            field.setPopupShowing(false);
            assertFalse(field.getPseudoClassStates().contains(popupShowing));
        });
    }

    @Test
    public void showHideSuggestionsAreNoOpsWithoutSkin() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = new RXAutoCompleteField();
            field.getSuggestions().add("a");
            field.showSuggestions();
            field.hideSuggestions();
            assertFalse(field.isPopupShowing(), "no skin: show / hide are no-ops");
        });
    }

    @Test
    public void showSuggestionsRequiresFocusAndHideIsSafeWithSkin() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = attach(new RXAutoCompleteField());
            field.getSuggestions().addAll("alpha", "beta");
            field.showSuggestions();
            assertFalse(field.isPopupShowing(),
                    "an unfocused field never opens the dropdown, even programmatically");
            field.hideSuggestions();
            assertFalse(field.isPopupShowing(), "hide is safe when nothing is showing");
        });
    }

    @Test
    public void skinDisposeResetsPopupShowing() throws InterruptedException {
        runOnFx(() -> {
            RXAutoCompleteField field = attach(new RXAutoCompleteField());
            field.getSuggestions().addAll("a", "b");
            // Simulate the dropdown being open, then dispose the skin directly: a
            // same-class setSkin is a no-op in JavaFX 17, so it would not dispose.
            field.setPopupShowing(true);
            field.getSkin().dispose();
            assertFalse(field.isPopupShowing(), "disposing the skin clears the popup-showing mirror");
        });
    }

    private static RXAutoCompleteField attach(RXAutoCompleteField field) {
        field.setSkin(new RXAutoCompleteFieldSkin(field));
        StackPane root = new StackPane(field);
        new Scene(root, 400.0, 200.0);
        root.applyCss();
        root.layout();
        return field;
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
