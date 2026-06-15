package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXTextView;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    // The text-layout inputs that move keyword geometry, captured from the last rebuild.
    // A pure selection change still requests a layout pass in the base skin, but leaves all
    // of these unchanged, so the (O(matches)) rangeShape() rebuild is skipped on selection
    // drags. These are exactly the inputs TextFlow feeds to its TextLayout geometry (see
    // TextFlow: layout.setContent / setWrapWidth / setAlignment / setLineSpacing /
    // setDirection / setTabSize — it never calls setBoundsType), so the set is complete:
    //   - content  -> ranges identity (text) + body-run font
    //   - wrapWidth -> getTextFlow().getWidth() minus the flow's own left/right insets
    //   - alignment, lineSpacing, direction (effective node orientation), tab size
    // Keyed on the inputs themselves, not any derived bounds: the TextFlow is stretched to
    // the allocated height and the body run's intrinsic layoutBounds ignores line spacing,
    // so neither reflects the real geometry.
    private List<IndexRange> lastRanges;
    private double lastWrapWidth = -1.0;
    private double lastLineSpacing = Double.NaN;
    private TextAlignment lastAlignment;
    private Font lastFont;
    private NodeOrientation lastOrientation;
    private int lastTabSize = -1;

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        // Same origin rule as the selection layer (see RXTextViewSkin#layoutChildren):
        // TextFlow origin plus the flow's snapped insets, so the inset-free rangeShape
        // geometry stays aligned when .text-flow carries padding.
        TextFlow flow = getTextFlow();
        highlightShape.setLayoutX(x + flow.snappedLeftInset());
        highlightShape.setLayoutY(y + flow.snappedTopInset());
        rebuildHighlightShapeIfNeeded();
    }

    private void rebuildHighlightShapeIfNeeded() {
        Text body = bodyRun();
        TextFlow flow = getTextFlow();
        // highlightRanges is a fresh list on every non-empty recompute, so identity tracks
        // content; the empty case reuses the List.of() singleton, which is also correct —
        // empty ranges always clear the Path, so an empty -> empty skip leaves it empty.
        List<IndexRange> ranges = control().getHighlightRanges();
        // Effective wrap width: the flow's own left/right padding narrows the content box,
        // so it must be folded out of the allocated width to catch a .text-flow padding change.
        double wrapWidth = flow.getWidth() - flow.snappedLeftInset() - flow.snappedRightInset();
        double lineSpacing = getSkinnable().getLineSpacing();
        TextAlignment alignment = getSkinnable().getTextAlignment();
        Font font = (body == null) ? null : body.getFont();
        NodeOrientation orientation = flow.getEffectiveNodeOrientation();
        int tabSize = flow.getTabSize();
        if (ranges == lastRanges
                && wrapWidth == lastWrapWidth
                && lineSpacing == lastLineSpacing
                && alignment == lastAlignment
                && Objects.equals(font, lastFont)
                && orientation == lastOrientation
                && tabSize == lastTabSize) {
            return;
        }
        lastRanges = ranges;
        lastWrapWidth = wrapWidth;
        lastLineSpacing = lineSpacing;
        lastAlignment = alignment;
        lastFont = font;
        lastOrientation = orientation;
        lastTabSize = tabSize;
        rebuildHighlightShape();
    }

    private Text bodyRun() {
        for (Node child : getTextFlow().getChildrenUnmodifiable()) {
            if (child instanceof Text run) {
                return run;
            }
        }
        return null;
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
