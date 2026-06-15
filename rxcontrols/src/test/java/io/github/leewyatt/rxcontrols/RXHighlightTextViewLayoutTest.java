package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-level regression for {@link RXHighlightTextView}'s Text-run rendering. The core fix
 * of the rewrite is that a long highlighted run now wraps instead of overflowing — the
 * old Label-in-TextFlow turned each highlighted span into an unbreakable embedded box.
 * These checks need a live toolkit and a real layout pass, so they run on the FX thread.
 */
public class RXHighlightTextViewLayoutTest {

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
            RXHighlightTextView control = new RXHighlightTextView(longToken, longToken);
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
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick", "fox");
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
            RXHighlightTextView control = new RXHighlightTextView("nothing matches here", "zzz");
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
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick");
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
            RXHighlightTextView control = new RXHighlightTextView("abc");
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
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick");
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
            RXHighlightTextView control = new RXHighlightTextView(
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

    @Test
    public void bodyStaysSingleTextRunWithManyMatches() throws Exception {
        // Keyword highlighting must not split the body into per-keyword runs: the TextFlow
        // keeps exactly one body Text no matter how many keywords match.
        AtomicReference<Integer> textRuns = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the cat sat on the flat mat", "at", "the");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            int count = 0;
            for (Node node : flow.getChildrenUnmodifiable()) {
                if (node instanceof Text) {
                    count++;
                }
            }
            textRuns.set(count);
        });
        assertEquals(1, textRuns.get(), "the body must remain a single Text run regardless of matches");
    }

    @Test
    public void manyMatchesUseOneHighlightPathNotPerMatchNodes() throws Exception {
        // A search with many hits must not create one Node per match. The body stays a
        // single Text, there is exactly one .highlight-shape Path, and only its PathElement
        // count grows with the geometry. No legacy .highlight run nodes are created.
        AtomicReference<Integer> bodyRuns = new AtomicReference<>();
        AtomicReference<Integer> highlightShapes = new AtomicReference<>();
        AtomicReference<Integer> highlightRuns = new AtomicReference<>();
        AtomicReference<Integer> pathElements = new AtomicReference<>();
        onFx(() -> {
            String text = "ab ".repeat(150).trim();   // 150 occurrences of "ab"
            RXHighlightTextView control = new RXHighlightTextView(text, "ab");
            StackPane root = new StackPane(control);
            new Scene(root, 600, 400);
            root.applyCss();
            root.layout();
            bodyRuns.set(control.lookupAll(".plain").size());
            highlightShapes.set(control.lookupAll(".highlight-shape").size());
            highlightRuns.set(control.lookupAll(".highlight").size());
            Path shape = (Path) control.lookup(".highlight-shape");
            pathElements.set(shape.getElements().size());
        });
        assertEquals(1, bodyRuns.get(), "the body must stay a single .plain Text run");
        assertEquals(1, highlightShapes.get(), "all keyword backgrounds share one .highlight-shape Path");
        assertEquals(0, highlightRuns.get(), "no per-keyword .highlight run nodes may be created");
        assertTrue(pathElements.get() > 1,
                "geometry should grow inside the single Path (was " + pathElements.get() + " elements)");
    }

    @Test
    public void highlightRangesChangeDoesNotRebuildBodyText() throws Exception {
        // Changing keywords changes highlightRanges (and the painted background) but not
        // the text, so the body Text must not be recreated.
        AtomicReference<Text> before = new AtomicReference<>();
        AtomicReference<Text> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            before.set((Text) control.lookup(".plain"));
            control.getKeywords().setAll("quick");
            root.layout();
            after.set((Text) control.lookup(".plain"));
        });
        assertSame(before.get(), after.get(),
                "a highlight-ranges change must not recreate the body Text");
    }

    @Test
    public void highlightShapeFillTracksControlProperty() throws Exception {
        // §3.2: the highlightShape fill must respond to runtime changes (not a one-shot
        // setFill in the constructor). It is bound to the control's highlightFill.
        AtomicReference<Object> nodeInitial = new AtomicReference<>();
        AtomicReference<Object> controlInitial = new AtomicReference<>();
        AtomicReference<Object> nodeAfter = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            nodeInitial.set(shape.getFill());
            controlInitial.set(control.getHighlightFill());
            control.setHighlightFill(Color.RED);
            nodeAfter.set(shape.getFill());
        });
        assertEquals(controlInitial.get(), nodeInitial.get(),
                "the highlight shape fill must track the control's highlightFill");
        assertEquals(Color.RED, nodeAfter.get(),
                "a runtime setHighlightFill must reach the rendered highlight shape");
    }

    @Test
    public void selectionChangesDoNotRebuildHighlightGeometry() throws Exception {
        // Perf guard: with selection enabled, drag-selecting fires a selection change per
        // event, each requesting a layout pass. The keyword geometry does not move, so the
        // O(matches) rangeShape() rebuild must be skipped — the existing PathElement
        // instances must survive across selection-only layouts.
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox jumps over", "o");
            control.setSelectable(true);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            for (int i = 1; i <= 6; i++) {
                control.selectRange(0, i);   // simulate a drag growing the selection
                root.layout();
            }
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertSame(before.get(), after.get(),
                "selection-only layouts must not rebuild the keyword highlight geometry");
    }

    @Test
    public void textAlignmentChangeRebuildsHighlightGeometry() throws Exception {
        // Counterpart to the perf guard: a change that DOES move the glyphs (alignment
        // shifts x without changing the flow bounds) must still rebuild the highlight.
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            control.setTextAlignment(TextAlignment.RIGHT);
            root.layout();
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertNotSame(before.get(), after.get(),
                "a text-alignment change must rebuild the keyword highlight geometry");
    }

    @Test
    public void lineSpacingChangeRebuildsMultiLineHighlight() throws Exception {
        // The showcase exposes a line-spacing slider; on multi-line text it pushes lines
        // apart, moving the keyword geometry. The TextFlow is stretched to the allocated
        // height (maxHeight=MAX_VALUE), so flow.getHeight() does NOT reflect spacing — the
        // rebuild is keyed on getLineSpacing() directly, which this test guards.
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView(
                    "alpha beta\ngamma delta\nepsilon target", "target");
            control.setPrefWidth(200);
            control.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 400);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            control.setLineSpacing(24);
            root.layout();
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertNotSame(before.get(), after.get(),
                "a line-spacing change on multi-line text must rebuild the keyword highlight");
    }

    @Test
    public void nodeOrientationChangeRebuildsHighlightGeometry() throws Exception {
        // rangeShape() geometry is text-direction dependent: an RTL flip mirrors the glyphs.
        // A runtime node-orientation change leaves width/spacing/alignment/font/ranges
        // untouched, so the highlight rebuild must be keyed on the effective orientation,
        // otherwise the fill would stay stranded at the old LTR position.
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "fox");
            StackPane root = new StackPane(control);
            Scene scene = new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            root.applyCss();
            root.layout();
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertNotSame(before.get(), after.get(),
                "a node-orientation (RTL) flip must rebuild the keyword highlight geometry");
    }

    @Test
    public void tabSizeChangeRebuildsHighlightGeometry() throws Exception {
        // Tab size shifts the x of glyphs after a tab; for a keyword past a tab the highlight
        // geometry depends on it. tabSize is only reachable via the .text-flow stylesheet,
        // but the rebuild must still be keyed on it to stay correct on a runtime change.
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("a\ttarget here", "target");
            control.setPrefWidth(400);
            control.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane root = new StackPane(control);
            new Scene(root, 600, 200);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.setTabSize(16);
            root.layout();
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertNotSame(before.get(), after.get(),
                "a tab-size change must rebuild the keyword highlight after a tab");
    }

    @Test
    public void highlightShapeFollowsTextFlowPadding() throws Exception {
        // The highlight Path origin must include the TextFlow's own insets, since the
        // glyphs are laid out after the flow padding while rangeShape is inset-free.
        AtomicReference<Double> dx = new AtomicReference<>();
        AtomicReference<Double> dy = new AtomicReference<>();
        AtomicReference<Double> left = new AtomicReference<>();
        AtomicReference<Double> top = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView("the quick brown fox", "quick");
            StackPane root = new StackPane(control);
            new Scene(root, 400, 200);
            root.applyCss();
            root.layout();
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.setPadding(new Insets(12, 0, 0, 24));
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            dx.set(shape.getLayoutX() - flow.getLayoutX());
            dy.set(shape.getLayoutY() - flow.getLayoutY());
            left.set(flow.snappedLeftInset());
            top.set(flow.snappedTopInset());
        });
        assertEquals(left.get(), dx.get(), 0.5,
                "highlight origin must include the TextFlow left inset");
        assertEquals(top.get(), dy.get(), 0.5,
                "highlight origin must include the TextFlow top inset");
    }

    @Test
    public void textFlowPaddingChangeRebuildsHighlight() throws Exception {
        // Left/right padding on .text-flow narrows the effective wrap width, re-wrapping the
        // text and moving the keyword geometry — the cache must rebuild (it keys on the
        // effective wrap width = allocated width minus the flow's left/right insets).
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        onFx(() -> {
            RXHighlightTextView control = new RXHighlightTextView(
                    "alpha beta gamma delta target epsilon zeta", "target");
            control.setPrefWidth(200);
            control.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane root = new StackPane(control);
            new Scene(root, 400, 300);
            root.applyCss();
            root.layout();
            Path shape = (Path) control.lookup(".highlight-shape");
            before.set(shape.getElements().get(0));
            TextFlow flow = (TextFlow) control.lookup(".text-flow");
            flow.setPadding(new Insets(0, 60, 0, 60));
            root.layout();
            after.set(shape.getElements().get(0));
        });
        assertNotNull(before.get(), "the keyword highlight should have geometry to begin with");
        assertNotSame(before.get(), after.get(),
                "a .text-flow padding change that re-wraps the text must rebuild the keyword highlight");
    }
}
