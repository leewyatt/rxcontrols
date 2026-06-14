package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-level tests for {@link RXSelectableText}'s selection and caret rendering: the
 * selection background Path tracks the (programmatic) selection, clears on deselect, and
 * is still painted when the control is not interactively selectable (the §7 orthogonal
 * semantics); the caret Path carries geometry. Needs a live toolkit and a real layout
 * pass, so they run on the FX thread.
 */
public class RXSelectableTextLayoutTest {

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
            RXSelectableText control = new RXSelectableText("hello world");
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
            RXSelectableText control = new RXSelectableText("hello world");
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
            RXSelectableText control = new RXSelectableText("hello world");
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
    public void caretGeometryIsGenerated() throws Exception {
        AtomicReference<Path> caret = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = new RXSelectableText("hello");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            caret.set((Path) control.lookup(".caret"));
        });
        assertNotNull(caret.get(), "the .caret Path should exist in the skin");
        assertTrue(caret.get().getElements().size() > 0, "the caret should carry geometry");
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
            RXSelectableText control = new RXSelectableText(
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

    @Test
    public void caretHiddenByDefault() throws Exception {
        AtomicReference<Double> caretOpacity = new AtomicReference<>();
        onFx(() -> {
            RXSelectableText control = new RXSelectableText("hello world");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path caret = (Path) control.lookup(".caret");
            caretOpacity.set(caret.getOpacity());
        });
        assertEquals(0.0, caretOpacity.get(),
                "the caret must be hidden until the user interacts (no caret on automatic focus)");
    }
}
