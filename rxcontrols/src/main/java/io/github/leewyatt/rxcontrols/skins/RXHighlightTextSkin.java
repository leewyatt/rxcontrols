package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHighlightText;
import io.github.leewyatt.rxcontrols.RXSelectableText;
import javafx.scene.control.IndexRange;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Skin for {@link RXHighlightText}. Extends {@link RXSelectableTextSkin} with keyword
 * highlighting: the run-building hook splits the text into plain and highlighted
 * {@link Text} runs (built entirely from {@code Text}, so long highlighted spans still
 * wrap), and a background {@code .highlight-shape} {@link Path} paints the highlight fill
 * beneath the text using {@link TextFlow#rangeShape(int, int)} geometry.
 *
 * <p>Both the run split and the background geometry are driven by the control's
 * {@link RXHighlightText#highlightRangesProperty() highlightRanges} — the single source
 * of truth — so they cannot disagree.
 */
public class RXHighlightTextSkin extends RXSelectableTextSkin {

    // ==================== Constants ====================

    private static final String HIGHLIGHT_STYLE_CLASS = "highlight";
    private static final String HIGHLIGHT_SHAPE_STYLE_CLASS = "highlight-shape";

    // ==================== Nodes ====================

    private final Path highlightShape = new Path();

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXHighlightTextSkin(RXHighlightText control) {
        super(control);
        highlightShape.getStyleClass().add(HIGHLIGHT_SHAPE_STYLE_CLASS);
        highlightShape.setManaged(false);
        highlightShape.setMouseTransparent(true);
        highlightShape.setStroke(null);
        // Below the selection layer and the text runs so the fill shows through the
        // (transparent) glyphs.
        getChildren().add(0, highlightShape);
    }

    @Override
    protected void registerContentListeners(RXSelectableText control) {
        // Keep the base class's text listener: highlightRanges alone is not enough. An
        // empty match returns the shared List.of() singleton, so a text change between two
        // non-matching values would not change the property's identity and it would not
        // fire. The text listener guarantees a rebuild on every text change; the
        // highlightRanges listener additionally covers keyword / rule changes (a text
        // change that flips matching also fires both — a cheap, idempotent extra rebuild).
        super.registerContentListeners(control);
        disposer.registerListener(((RXHighlightText) control).highlightRangesProperty(), this::rebuildRuns);
    }

    private RXHighlightText control() {
        return (RXHighlightText) getSkinnable();
    }

    // ==================== Text runs ====================

    @Override
    protected void rebuildTextRuns(TextFlow flow, String text) {
        List<IndexRange> ranges = control().getHighlightRanges();
        if (ranges.isEmpty()) {
            super.rebuildTextRuns(flow, text);
            return;
        }
        List<Text> runs = new ArrayList<>();
        int cursor = 0;
        for (IndexRange range : ranges) {
            int start = range.getStart();
            int end = range.getEnd();
            if (start > cursor) {
                runs.add(run(text.substring(cursor, start), PLAIN_STYLE_CLASS));
            }
            runs.add(run(text.substring(start, end), HIGHLIGHT_STYLE_CLASS));
            cursor = end;
        }
        if (cursor < text.length()) {
            runs.add(run(text.substring(cursor), PLAIN_STYLE_CLASS));
        }
        flow.getChildren().setAll(runs);
    }

    private Text run(String content, String styleClass) {
        Text text = new Text(content);
        text.getStyleClass().add(styleClass);
        return text;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        // Set the origin directly, not relocate() — see RXSelectableTextSkin#layoutChildren:
        // relocate would subtract the Path's layoutBounds min and drift a lower-line
        // highlight shape outside the control.
        highlightShape.setLayoutX(x);
        highlightShape.setLayoutY(y);
        rebuildHighlightShape();
    }

    private void rebuildHighlightShape() {
        List<IndexRange> ranges = control().getHighlightRanges();
        if (ranges.isEmpty()) {
            highlightShape.getElements().clear();
            return;
        }
        TextFlow flow = getTextFlow();
        List<PathElement> elements = new ArrayList<>();
        for (IndexRange range : ranges) {
            if (range.getLength() > 0) {
                Collections.addAll(elements, flow.rangeShape(range.getStart(), range.getEnd()));
            }
        }
        highlightShape.getElements().setAll(elements);
    }
}
