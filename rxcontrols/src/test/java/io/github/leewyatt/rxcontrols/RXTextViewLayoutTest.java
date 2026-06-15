package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-level tests for {@link RXTextView}'s selection rendering and selected-text
 * foreground: the selection background Path tracks the (programmatic) selection, clears on
 * deselect, and is still painted when the control is not interactively selectable (the §7
 * orthogonal semantics); the body Text carries the {@code selectedTextFill} override; and
 * there is no visible caret node. Needs a live toolkit and a real layout pass, so they run
 * on the FX thread.
 */
public class RXTextViewLayoutTest {

    /**
     * Starts the JavaFX toolkit so a real layout pass can run.
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

    @Test
    public void selectionShapeTracksApiSelection() throws Exception {
        AtomicReference<Integer> empty = new AtomicReference<>();
        AtomicReference<Integer> selected = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path selection = (Path) control.lookup(".selection-shape");
            empty.set(selection.getElements().size());
            control.selectRange(2, 7);
            root.layout();
            selected.set(selection.getElements().size());
        });
        assertEquals(0, empty.get(), "no selection should leave the selection shape empty");
        assertTrue(selected.get() > 0, "selecting a range should produce selection geometry");
    }

    @Test
    public void selectionShapeClearsOnDeselect() throws Exception {
        AtomicReference<Integer> after = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            control.selectAll();
            root.layout();
            control.deselect();
            root.layout();
            Path selection = (Path) control.lookup(".selection-shape");
            after.set(selection.getElements().size());
        });
        assertEquals(0, after.get(), "deselect should clear the selection shape");
    }

    @Test
    public void selectionRendersEvenWhenNotSelectable() throws Exception {
        // §7: selectable=false disables user interaction, but the programmatic selection
        // API still works and its selection is still painted.
        AtomicReference<Integer> selected = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            control.setSelectable(false);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            control.selectRange(0, 5);
            root.layout();
            Path selection = (Path) control.lookup(".selection-shape");
            selected.set(selection.getElements().size());
        });
        assertTrue(selected.get() > 0, "API selection must still render when not selectable");
    }

    @Test
    public void skinHasNoCaretNode() throws Exception {
        // A selectable text view shows no insertion caret, so the skin must not create a
        // .caret node (it would read as an editable text field).
        AtomicReference<Object> caret = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            caret.set(control.lookup(".caret"));
        });
        assertNull(caret.get(), "a selectable text view must not create a .caret node");
    }

    @Test
    public void selectedTextFillAppliesToBodyRun() throws Exception {
        AtomicReference<Integer> start = new AtomicReference<>();
        AtomicReference<Integer> end = new AtomicReference<>();
        AtomicReference<Object> fill = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            control.setSelectedTextFill(Color.WHITE);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            control.selectRange(0, 5);
            root.layout();
            Text body = (Text) control.lookup(".plain");
            start.set(body.getSelectionStart());
            end.set(body.getSelectionEnd());
            fill.set(body.getSelectionFill());
        });
        assertEquals(0, start.get());
        assertEquals(5, end.get());
        assertEquals(Color.WHITE, fill.get());
    }

    @Test
    public void selectedTextFillNullDisablesForegroundOverride() throws Exception {
        // selectedTextFill == null means "apply no selected-foreground override" (the
        // glyphs keep their ordinary fill) — it does NOT make the selected text transparent.
        AtomicReference<Object> fill = new AtomicReference<>("sentinel");
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            control.setSelectedTextFill(null);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            control.selectRange(0, 5);
            root.layout();
            Text body = (Text) control.lookup(".plain");
            fill.set(body.getSelectionFill());
        });
        assertNull(fill.get(), "null selectedTextFill must leave Text.selectionFill null (no override)");
    }

    @Test
    public void selectionChangeDoesNotRebuildBodyText() throws Exception {
        AtomicReference<Text> before = new AtomicReference<>();
        AtomicReference<Text> after = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            before.set((Text) control.lookup(".plain"));
            control.selectRange(2, 7);
            root.layout();
            after.set((Text) control.lookup(".plain"));
        });
        assertSame(before.get(), after.get(), "a selection change must not recreate the body Text");
    }

    @Test
    public void selectionShapeFillTracksControlProperty() throws Exception {
        // §3.1: the selectionShape fill must respond to runtime changes (not a one-shot
        // setFill in the constructor). It is bound to the control's selectionFill.
        AtomicReference<Object> nodeInitial = new AtomicReference<>();
        AtomicReference<Object> controlInitial = new AtomicReference<>();
        AtomicReference<Object> nodeAfter = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path selection = (Path) control.lookup(".selection-shape");
            nodeInitial.set(selection.getFill());
            controlInitial.set(control.getSelectionFill());
            control.setSelectionFill(Color.RED);
            nodeAfter.set(selection.getFill());
        });
        assertEquals(controlInitial.get(), nodeInitial.get(),
                "the selection shape fill must track the control's selectionFill");
        assertEquals(Color.RED, nodeAfter.get(),
                "a runtime setSelectionFill must reach the rendered selection shape");
    }

    @Test
    public void textFillAppliesToBodyRunAndUpdatesWithoutRebuild() throws Exception {
        AtomicReference<Object> nodeInitial = new AtomicReference<>();
        AtomicReference<Object> controlInitial = new AtomicReference<>();
        AtomicReference<Object> nodeAfter = new AtomicReference<>();
        AtomicReference<Text> before = new AtomicReference<>();
        AtomicReference<Text> after = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Text body = (Text) control.lookup(".plain");
            before.set(body);
            nodeInitial.set(body.getFill());
            controlInitial.set(control.getTextFill());
            control.setTextFill(Color.RED);
            after.set((Text) control.lookup(".plain"));
            nodeAfter.set(((Text) control.lookup(".plain")).getFill());
        });
        assertEquals(controlInitial.get(), nodeInitial.get(),
                "the body Text fill must track the control's textFill");
        assertEquals(Color.RED, nodeAfter.get(), "a runtime setTextFill must update the body Text fill");
        assertSame(before.get(), after.get(), "setTextFill must update the existing run, not rebuild it");
    }

    @Test
    public void selectionShapeFollowsTextFlowPadding() throws Exception {
        // Padding set on the internal .text-flow lays the glyphs out after that inset, while
        // rangeShape is inset-free; the selection background must still sit on the glyphs.
        AtomicReference<Double> textMinX = new AtomicReference<>();
        AtomicReference<Double> selMinX = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.setPadding(new Insets(0, 0, 0, 30));
            root.layout();
            control.selectAll();
            root.layout();
            Text plain = (Text) control.lookup(".plain");
            Path selection = (Path) control.lookup(".selection-shape");
            textMinX.set(plain.localToScene(plain.getBoundsInLocal()).getMinX());
            selMinX.set(selection.localToScene(selection.getBoundsInLocal()).getMinX());
        });
        assertEquals(textMinX.get(), selMinX.get(), 1.5,
                "the selection background must follow the glyphs when .text-flow has padding");
    }

    @Test
    public void multiLineSelectionStaysWithinTextFlow() throws Exception {
        // Regression: the selection Path must stay aligned with the glyphs even after the
        // selection first sat on a lower line and then moved up (the triple-click sequence:
        // single/double click lower down, then select the whole line/paragraph). relocate()
        // compensating the previous frame's layoutBounds drifts the shape outside the control.
        AtomicReference<Bounds> selectionBounds = new AtomicReference<>();
        AtomicReference<Bounds> textFlowBounds = new AtomicReference<>();
        onFx(() -> {
            RXTextView control = new RXTextView(
                    "line one\nline two\nline three\nline four\nline five\nline six");
            control.setLineSpacing(6);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 400);
            root.applyCss();
            root.layout();
            // Selection sits on a lower line first ...
            control.selectRange(40, 48);
            root.layout();
            // ... then jumps to a first-line-anchored range.
            control.selectAll();
            root.layout();
            Region textFlow = (Region) control.lookup(".text-flow");
            Path selection = (Path) control.lookup(".selection-shape");
            textFlowBounds.set(textFlow.getBoundsInParent());
            selectionBounds.set(selection.getBoundsInParent());
        });
        Bounds flow = textFlowBounds.get();
        Bounds selection = selectionBounds.get();
        assertTrue(selection.getMinY() >= flow.getMinY() - 1.0,
                "selection top " + selection.getMinY() + " drifted above textFlow top " + flow.getMinY());
        assertTrue(selection.getMaxY() <= flow.getMaxY() + 1.0,
                "selection bottom " + selection.getMaxY() + " drifted below textFlow bottom " + flow.getMaxY());
    }
}
