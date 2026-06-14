package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.control.IndexRange;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interaction tests for {@link RXSelectableText}: synthesized keyboard and mouse events
 * are dispatched to the skin's handlers to verify the event-to-API mapping (select-all,
 * deselect, caret movement, shift-extend, double-click word, triple-click paragraph).
 * Runs on the FX thread with a live toolkit.
 */
public class RXSelectableTextInteractionTest {

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

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

    private interface FxTask {
        void run() throws Exception;
    }

    private static void onFx(FxTask task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    private static RXSelectableText laidOut(String text) {
        RXSelectableText control = new RXSelectableText(text);
        StackPane root = new StackPane(control);
        new Scene(root, 400, 200);
        root.applyCss();
        root.layout();
        return control;
    }

    private static KeyEvent key(KeyCode code, boolean shift, boolean shortcut) {
        // isShortcutDown() resolves to meta on macOS and control elsewhere.
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift,
                shortcut && !MAC, false, shortcut && MAC);
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, double x, double y, int clickCount, boolean shift) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, clickCount,
                shift, false, false, false,
                true, false, false,
                false, false, false, null);
    }

    private static MouseEvent press(double x, double y, int clickCount) {
        return mouse(MouseEvent.MOUSE_PRESSED, x, y, clickCount, false);
    }

    // ==================== Keyboard ====================

    @Test
    public void shortcutASelectsAll() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.fireEvent(key(KeyCode.A, false, true));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getStart());
        assertEquals(11, selection.get().getEnd());
    }

    @Test
    public void escapeDeselects() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.selectAll();
            control.fireEvent(key(KeyCode.ESCAPE, false, false));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getLength());
    }

    @Test
    public void rightArrowMovesCaret() throws Exception {
        AtomicReference<Integer> caret = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello");
            control.positionCaret(0);
            control.fireEvent(key(KeyCode.RIGHT, false, false));
            caret.set(control.getCaretPosition());
        });
        assertEquals(1, caret.get());
    }

    @Test
    public void shiftRightExtendsSelection() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello");
            control.positionCaret(0);
            control.fireEvent(key(KeyCode.RIGHT, true, false));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getStart());
        assertEquals(1, selection.get().getEnd());
    }

    @Test
    public void notSelectableIgnoresKeyboard() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.setSelectable(false);
            control.fireEvent(key(KeyCode.A, false, true));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getLength(), "selectable=false should ignore keyboard selection");
    }

    // ==================== Mouse ====================

    @Test
    public void doubleClickSelectsWord() throws Exception {
        AtomicReference<String> selectedText = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(press(5, 5, 2));
            selectedText.set(control.getSelectedText());
        });
        // hitting near the start lands inside "hello"; the whole word is selected
        assertEquals("hello", selectedText.get());
    }

    @Test
    public void singleClickPlacesCaretAndClearsSelection() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.selectAll();
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(press(5, 5, 1));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getLength(), "a single click should collapse the selection");
    }

    @Test
    public void tripleClickSelectsParagraph() throws Exception {
        AtomicReference<String> selectedText = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("line one\nline two");
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 3, false));
            selectedText.set(control.getSelectedText());
        });
        assertEquals("line one", selectedText.get());
    }

    @Test
    public void shiftClickExtendsSelectionFromAnchor() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.positionCaret(0);
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 60, 5, 1, true));
            selection.set(control.getSelection());
        });
        assertEquals(0, selection.get().getStart());
        assertTrue(selection.get().getEnd() > 0, "shift-click should extend the selection from the anchor");
    }

    @Test
    public void dragExtendsSelection() throws Exception {
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 1, false));
            flow.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 60, 5, 1, false));
            selection.set(control.getSelection());
        });
        assertTrue(selection.get().getLength() > 0, "dragging should extend the selection");
    }

    @Test
    public void shortcutCCopiesSelectionToClipboard() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.selectRange(0, 5);
            control.fireEvent(key(KeyCode.C, false, true));
            clipboard.set(Clipboard.getSystemClipboard().getString());
        });
        assertEquals("hello", clipboard.get());
    }

    @Test
    public void notSelectableIgnoresMouse() throws Exception {
        AtomicReference<String> selectedText = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world");
            control.setSelectable(false);
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 2, false));
            selectedText.set(control.getSelectedText());
        });
        assertEquals("", selectedText.get(), "selectable=false should ignore mouse selection");
    }

    @Test
    public void dragKeepsAnchorAtPressPosition() throws Exception {
        // Reverse / jittery drag: the anchor must stay at the press position. extendSelection
        // recomputes from the live caret and drifts the anchor when the drag reverses.
        AtomicReference<Integer> pressAnchor = new AtomicReference<>();
        AtomicReference<Integer> finalAnchor = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = laidOut("hello world example text here");
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.fireEvent(press(140, 5, 1));
            pressAnchor.set(control.getAnchor());
            flow.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 20, 5, 1, false));  // drag far left
            flow.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 60, 5, 1, false));  // jitter back right
            finalAnchor.set(control.getAnchor());
        });
        assertEquals(pressAnchor.get(), finalAnchor.get(),
                "drag must keep the anchor fixed at the press position");
    }
}
