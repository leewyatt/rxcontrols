package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-level regression for {@link RXHighlightText}'s Text-run rendering. The core fix
 * of the rewrite is that a long highlighted run now wraps instead of overflowing — the
 * old Label-in-TextFlow turned each highlighted span into an unbreakable embedded box.
 * These checks need a live toolkit and a real layout pass, so they run on the FX thread.
 */
public class RXHighlightTextLayoutTest {

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
    public void longHighlightedRunWrapsInsteadOfOverflowing() throws Exception {
        AtomicReference<Double> oneLine = new AtomicReference<>();
        AtomicReference<Double> wrapped = new AtomicReference<>();
        onFx(() -> {
            // A long, space-free highlighted token: exactly what the old Label-in-TextFlow
            // could not break, since an embedded object is a single unsplittable glyph.
            String longToken = "highlightedlongtokenwithoutanyspaces".repeat(4);
            RXHighlightText control = new RXHighlightText(longToken, longToken);
            StackPane root = new StackPane(control);
            new Scene(root, 1200, 600);
            root.applyCss();
            root.layout();
            // prefHeight(width) asks the content-biased control directly, free of the
            // StackPane stretching the child to the scene height.
            oneLine.set(control.prefHeight(3000));
            wrapped.set(control.prefHeight(140));
        });
        assertTrue(oneLine.get() > 0, "control should have a measurable one-line height");
        assertTrue(wrapped.get() > oneLine.get() * 2,
                "a long highlighted run should wrap into several lines (wrapped height "
                        + wrapped.get() + " vs one-line height " + oneLine.get() + ")");
    }

    @Test
    public void highlightShapeGeometryIsGenerated() throws Exception {
        AtomicReference<Path> shape = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText("the quick brown fox", "quick", "fox");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            shape.set((Path) control.lookup(".highlight-shape"));
        });
        assertNotNull(shape.get(), "the .highlight-shape Path should exist in the skin");
        assertTrue(shape.get().getElements().size() > 0,
                "highlight background geometry should be generated for matched keywords");
    }

    @Test
    public void noHighlightLeavesEmptyShape() throws Exception {
        AtomicReference<Path> shape = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText("nothing matches here", "zzz");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            shape.set((Path) control.lookup(".highlight-shape"));
        });
        assertNotNull(shape.get(), "the .highlight-shape Path should exist in the skin");
        assertTrue(shape.get().getElements().isEmpty(),
                "no keyword match should leave the highlight shape empty");
    }

    @Test
    public void highlightAlignsWithTextFlowUnderVerticalStretch() throws Exception {
        AtomicReference<Double> shapeY = new AtomicReference<>();
        AtomicReference<Double> flowY = new AtomicReference<>();
        AtomicReference<Double> flowHeight = new AtomicReference<>();
        AtomicReference<Double> prefHeight = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText("the quick brown fox", "quick");
            StackPane root = new StackPane(control);
            // A tall scene: the StackPane stretches its single child to its own height,
            // well past the control's one-line preferred height.
            new Scene(root, 400, 600);
            root.applyCss();
            root.layout();
            prefHeight.set(control.prefHeight(400));
            Region textFlow = (Region) control.lookup(".text-flow");
            Path shape = (Path) control.lookup(".highlight-shape");
            flowY.set(textFlow.getLayoutY());
            flowHeight.set(textFlow.getHeight());
            shapeY.set(shape.getLayoutY());
        });
        // The highlight Path must share the TextFlow's vertical origin no matter how the
        // control is stretched, otherwise the highlight fill drifts off the glyphs.
        assertEquals(flowY.get(), shapeY.get(), 0.5,
                "highlight shape Y (" + shapeY.get() + ") must match TextFlow layoutY ("
                        + flowY.get() + "); textFlow height=" + flowHeight.get()
                        + ", one-line prefHeight=" + prefHeight.get());
    }

    @Test
    public void textChangeWithoutMatchStillUpdatesRuns() throws Exception {
        // Regression: with no keyword match, highlightRanges stays the shared List.of()
        // singleton, so a text change does not change its identity. The skin must still
        // rebuild the runs from the text change itself.
        AtomicReference<String> rendered = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText("abc");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            control.setText("def");
            root.layout();
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            StringBuilder rebuilt = new StringBuilder();
            for (Node node : flow.getChildrenUnmodifiable()) {
                if (node instanceof Text) {
                    rebuilt.append(((Text) node).getText());
                }
            }
            rendered.set(rebuilt.toString());
        });
        assertEquals("def", rendered.get());
    }

    @Test
    public void highlightAlignsUnderControlPadding() throws Exception {
        // The showcase gives the control 16px padding and pane-highlight-text.fxml an
        // 8px top inset; the highlight Path must follow the TextFlow's padded origin.
        AtomicReference<Double> shapeY = new AtomicReference<>();
        AtomicReference<Double> flowY = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText("the quick brown fox", "quick");
            control.setPadding(new Insets(8));
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Region textFlow = (Region) control.lookup(".text-flow");
            Path shape = (Path) control.lookup(".highlight-shape");
            flowY.set(textFlow.getLayoutY());
            shapeY.set(shape.getLayoutY());
        });
        assertEquals(flowY.get(), shapeY.get(), 0.5,
                "highlight shape must share the TextFlow origin under control padding");
        assertTrue(flowY.get() >= 8.0, "the TextFlow should be offset by the top inset");
    }

    @Test
    public void lowerLineHighlightStaysAlignedAcrossRelayout() throws Exception {
        // Regression: a highlight on a lower line must stay aligned after a second layout
        // pass (e.g. a selection change) — relocate() would drift it outside the control.
        AtomicReference<Bounds> shapeBounds = new AtomicReference<>();
        AtomicReference<Bounds> flowBounds = new AtomicReference<>();
        onFx(() -> {
            RXHighlightText control = new RXHighlightText(
                    "first line\nsecond line\nthird target line", "target");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 400);
            root.applyCss();
            root.layout();
            // A second layout pass while the highlight itself is unchanged.
            control.selectRange(0, 5);
            root.layout();
            Region textFlow = (Region) control.lookup(".text-flow");
            Path shape = (Path) control.lookup(".highlight-shape");
            flowBounds.set(textFlow.getBoundsInParent());
            shapeBounds.set(shape.getBoundsInParent());
        });
        Bounds flow = flowBounds.get();
        Bounds shape = shapeBounds.get();
        assertTrue(shape.getMinY() >= flow.getMinY() - 1.0,
                "highlight top " + shape.getMinY() + " drifted above textFlow top " + flow.getMinY());
        assertTrue(shape.getMaxY() <= flow.getMaxY() + 1.0,
                "highlight bottom " + shape.getMaxY() + " drifted below textFlow bottom " + flow.getMaxY());
    }
}
