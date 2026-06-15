package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXTextView;
import javafx.scene.control.IndexRange;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Skin for {@link RXHighlightTextView}. Extends {@link RXTextViewSkin} with a single
 * background {@code .highlight-shape} {@link Path} that paints the keyword highlight fill
 * beneath the body text, using {@link TextFlow#rangeShape(int, int)} geometry.
 *
 * <p>The body stays a single {@link Text} run inherited from the base skin — the text is
 * never split into per-keyword runs. Keyword highlighting therefore only colours the
 * background; a search that matches hundreds of times grows the {@link PathElement} count
 * of one {@code Path}, not the {@code Node} count. The keyword geometry is driven by the
 * control's {@link RXHighlightTextView#highlightRangesProperty() highlightRanges} — the
 * single source of truth — so the matched flag and the painted highlight cannot disagree.
 */
public class RXHighlightTextViewSkin extends RXTextViewSkin {

    // ==================== Nodes ====================

    private final Path highlightShape = new Path();

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXHighlightTextViewSkin(RXHighlightTextView control) {
        super(control);
        highlightShape.getStyleClass().add("highlight-shape");
        highlightShape.setManaged(false);
        highlightShape.setMouseTransparent(true);
        highlightShape.setStroke(null);
        // highlightShape is a stable node, so a long-lived binding to the control's fill is
        // correct (mirrors the selectionShape binding in the base skin).
        disposer.registerBinding(highlightShape.fillProperty(), control.highlightFillProperty());
        // Below the selection layer and the text run so the fill shows behind the glyphs.
        getChildren().add(0, highlightShape);
    }

    @Override
    protected void registerContentListeners(RXTextView control) {
        // Keep the base class's text listener so the body run rebuilds on every text
        // change. Highlight ranges do NOT rebuild the body run (the body stays a single
        // Text); a ranges change only requests a layout pass, which repaints the
        // highlight Path from the new geometry.
        super.registerContentListeners(control);
        disposer.registerListener(((RXHighlightTextView) control).highlightRangesProperty(),
                () -> getSkinnable().requestLayout());
    }

    private RXHighlightTextView control() {
        return (RXHighlightTextView) getSkinnable();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        // Set the origin directly, not relocate() — see RXTextViewSkin#layoutChildren:
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
