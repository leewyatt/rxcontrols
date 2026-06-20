package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.internal.TextNavigation;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Path;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Skin for {@link RXTextView}. Renders the text into a single {@link TextFlow} built from
 * one {@code .plain} {@link Text} run (so long runs wrap), and layers a selection
 * background Path beneath it. Selection geometry comes from
 * {@link TextFlow#rangeShape(int, int)}, which is inset-free on JFX 17, so the unmanaged
 * Path is placed at the TextFlow's content origin — its layout origin plus the flow's own
 * snapped insets — to stay aligned with the glyphs when {@code .text-flow} carries padding.
 *
 * <p>This is a selectable text view, not a text input: there is no visible blinking caret
 * and no arrow-key insertion-point navigation. The selected-glyph foreground is rendered
 * with JavaFX {@link Text}'s own selection primitives
 * ({@link Text#setSelectionStart(int)} / {@link Text#setSelectionEnd(int)} /
 * {@link Text#setSelectionFill(Paint)}) on the single body run — no overlay TextFlow.
 *
 * <p>The control's colours are pushed into the internal nodes from the control-level
 * styleable properties: {@code selectionShape} fill is bound to
 * {@link RXTextView#selectionFillProperty() selectionFill}, while
 * {@link RXTextView#textFillProperty() textFill} and
 * {@link RXTextView#selectedTextFillProperty() selectedTextFill} are written onto the body
 * {@link Text} (which is recreated on text change, so a long-lived binding would dangle).
 *
 * <p>User interaction (mouse, keyboard, context menu, I-beam cursor) is gated by
 * {@link RXTextView#selectableProperty() selectable}; the programmatic selection API still
 * works and its selection is still painted when {@code selectable} is false.
 */
public class RXTextViewSkin extends RXSkinBase<RXTextView> {

    // ==================== Nodes ====================

    private final TextFlow textFlow = new TextFlow();
    private final Path selectionShape = new Path();

    private ContextMenu contextMenu;
    private MenuItem copyMenuItem;

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXTextViewSkin(RXTextView control) {
        super(control);
        textFlow.getStyleClass().add("text-flow");

        selectionShape.getStyleClass().add("selection-shape");
        selectionShape.setManaged(false);
        selectionShape.setMouseTransparent(true);
        selectionShape.setStroke(null);

        // back-to-front: selection background, then text. Subclasses insert their own
        // layers (e.g. keyword highlight) below the selection via add(0, ...).
        getChildren().setAll(selectionShape, textFlow);

        disposer.registerBinding(textFlow.lineSpacingProperty(), control.lineSpacingProperty());
        disposer.registerBinding(textFlow.textAlignmentProperty(), control.textAlignmentProperty());
        // selectionShape is a stable node, so a long-lived binding is correct here. The
        // body Text fill is NOT bound, because the Text is recreated on every text change.
        disposer.registerBinding(selectionShape.fillProperty(), control.selectionFillProperty());

        registerContentListeners(control);

        disposer.registerListener(control.selectionProperty(), this::onSelectionChanged);
        disposer.registerListener(control.textFillProperty(), this::applyTextFill);
        disposer.registerListener(control.selectedTextFillProperty(), this::applySelectedTextFill);
        disposer.registerListener(control.selectableProperty(), this::updateCursor);
        disposer.registerListener(control.disabledProperty(), this::updateCursor);

        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);

        rebuildRuns();
        updateCursor();
    }

    // ==================== Text runs ====================

    /**
     * Registers the listener(s) that rebuild the text runs when the content changes. The
     * base implementation listens to {@code text}. A subclass whose run rebuild is driven
     * by a derived single-source-of-truth property may override this to add that property,
     * so the runs rebuild exactly once per change. Invoked from the constructor — an
     * override may use only {@code control}, {@link #disposer} and {@link #rebuildRuns()}.
     *
     * @param control the control
     */
    protected void registerContentListeners(RXTextView control) {
        disposer.registerListener(control.textProperty(), this::rebuildRuns);
    }

    /**
     * Rebuilds the text runs from the current text. Subclasses call this to refresh the
     * runs after their content source changes.
     */
    protected final void rebuildRuns() {
        rebuildTextRuns(textFlow, getSkinnable().textProperty().getValueSafe());
    }

    /**
     * Rebuilds the TextFlow's child runs for the given text. The base implementation
     * renders the whole text as a single {@code .plain} {@link Text} run, initialized with
     * the control's {@link RXTextView#textFillProperty() textFill} and current selection
     * foreground. The run must be a {@link Text} node so the layout engine can wrap long
     * runs.
     *
     * <p>This is invoked from the constructor, so an override must rely only on its
     * {@code flow} and {@code text} parameters and {@link #getSkinnable()} — subclass
     * instance fields are not yet initialized at that point.
     *
     * @param flow the TextFlow to populate
     * @param text the text to render (never {@code null})
     */
    protected void rebuildTextRuns(TextFlow flow, String text) {
        Text run = new Text(text);
        run.getStyleClass().add("plain");   // run carries .plain
        run.setFill(getSkinnable().getTextFill());
        applySelectedTextFillTo(run);
        flow.getChildren().setAll(run);
    }

    /**
     * Returns the TextFlow that holds the text runs, for subclasses that decorate it.
     *
     * @return the text flow
     */
    protected final TextFlow getTextFlow() {
        return textFlow;
    }

    private Text getTextRun() {
        for (Node child : textFlow.getChildren()) {
            if (child instanceof Text run) {
                return run;
            }
        }
        return null;
    }

    // ==================== Colours ====================

    private void applyTextFill() {
        Text run = getTextRun();
        if (run != null) {
            run.setFill(getSkinnable().getTextFill());
        }
    }

    private void applySelectedTextFill() {
        Text run = getTextRun();
        if (run != null) {
            applySelectedTextFillTo(run);
        }
    }

    private void applySelectedTextFillTo(Text run) {
        RXTextView control = getSkinnable();
        IndexRange selection = control.getSelection();
        if (selection.getLength() == 0) {
            // -1 is the JavaFX "no selection" sentinel for Text.selectionStart/End.
            run.setSelectionStart(-1);
            run.setSelectionEnd(-1);
        } else {
            run.setSelectionStart(selection.getStart());
            run.setSelectionEnd(selection.getEnd());
        }
        // null is forwarded verbatim: per JavaFX Text.selectionFill, null disables the
        // selected-foreground override (the glyphs keep their ordinary fill), it does not
        // paint the selected text transparent.
        run.setSelectionFill(control.getSelectedTextFill());
    }

    // ==================== Cursor ====================

    private void updateCursor() {
        RXTextView control = getSkinnable();
        boolean interactive = control.isSelectable() && !control.isDisabled();
        // I-beam over the text signals "selectable text", not "editable". null lets the
        // cursor fall back to the inherited one when interaction is off.
        textFlow.setCursor(interactive ? Cursor.TEXT : null);
    }

    // ==================== Selection geometry ====================

    private void onSelectionChanged() {
        getSkinnable().requestLayout();
        applySelectedTextFill();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(textFlow, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
        // The unmanaged selection layer is not positioned by layoutInArea. Set the layout
        // origin directly rather than relocate(x, y): relocate subtracts the Path's own
        // layoutBounds min, which — because the inset-free range shape is TextFlow-local (a
        // multi-line or lower-line shape has minY > 0) — would drift the shape outside the
        // control. rangeShape() is inset-free (relative to the TextFlow's content box,
        // before its own padding), but the glyphs are laid out after that padding, so the
        // Path origin is the TextFlow origin plus the flow's snapped insets — keeping the
        // layer aligned when .text-flow carries padding (zero insets ⇒ just the origin).
        selectionShape.setLayoutX(x + textFlow.snappedLeftInset());
        selectionShape.setLayoutY(y + textFlow.snappedTopInset());
        rebuildSelectionShape();
    }

    private void rebuildSelectionShape() {
        IndexRange selection = getSkinnable().getSelection();
        if (selection.getLength() == 0) {
            selectionShape.getElements().clear();
            return;
        }
        selectionShape.getElements().setAll(textFlow.rangeShape(selection.getStart(), selection.getEnd()));
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        RXTextView control = getSkinnable();
        // Any press dismisses an open context menu — the standard text controls hide it
        // explicitly rather than relying on autoHide (which is unreliable for presses
        // inside the owner control, especially once the press is consumed below).
        if (contextMenu != null && contextMenu.isShowing()) {
            contextMenu.hide();
        }
        if (!control.isSelectable() || control.isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!control.isFocused()) {
            control.requestFocus();
        }
        int index = hitIndex(event);
        String valueSafe = control.textProperty().getValueSafe();
        if (event.getClickCount() >= 3) {
            IndexRange paragraph = TextNavigation.paragraphRangeAt(valueSafe, index);
            control.selectRange(paragraph.getStart(), paragraph.getEnd());
        } else if (event.getClickCount() == 2) {
            IndexRange word = TextNavigation.wordRangeAt(valueSafe, index);
            control.selectRange(word.getStart(), word.getEnd());
        } else if (event.isShiftDown()) {
            control.extendSelection(index);
        } else {
            control.positionCaret(index);
        }
        event.consume();
    }

    private void onMouseDragged(MouseEvent event) {
        RXTextView control = getSkinnable();
        if (!control.isSelectable() || control.isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        // Drag keeps the anchor fixed at the press position and moves only the caret;
        // extendSelection would recompute from the live caret and drift the anchor when
        // the drag reverses direction.
        control.selectRange(control.getAnchor(), hitIndex(event));
        event.consume();
    }

    private int hitIndex(MouseEvent event) {
        // The handler is on the TextFlow, so event coordinates are TextFlow-local. hitTest
        // is inset-free, so subtract the flow's snapped insets to map a click into content
        // coordinates (zero insets ⇒ the coordinates are used unchanged).
        HitInfo hit = textFlow.hitTest(new Point2D(
                event.getX() - textFlow.snappedLeftInset(),
                event.getY() - textFlow.snappedTopInset()));
        return hit.getInsertionIndex();
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        RXTextView control = getSkinnable();
        if (!control.isSelectable()) {
            return;
        }
        KeyCode code = event.getCode();
        // Selectable text view: copy / select-all / clear only. Arrow keys do not move an
        // insertion point, so they are left to default focus traversal.
        if (event.isShortcutDown() && code == KeyCode.C) {
            control.copy();
            event.consume();
        } else if (event.isShortcutDown() && code == KeyCode.A) {
            control.selectAll();
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            control.deselect();
            event.consume();
        }
    }

    // ==================== Context menu ====================

    private void onContextMenuRequested(ContextMenuEvent event) {
        RXTextView control = getSkinnable();
        if (!control.isSelectable() || control.isDisabled()) {
            return;
        }
        // Step aside for a developer-supplied menu, mirroring JavaFX TextInputControl: when
        // Control.contextMenu is set, Control's own CONTEXT_MENU_REQUESTED handler pops it up;
        // when onContextMenuRequested is set, the developer's own handler runs. Either way we
        // must not also show the built-in menu, so show it only when neither is set.
        if (control.getContextMenu() != null || control.getOnContextMenuRequested() != null) {
            return;
        }
        if (contextMenu == null) {
            createContextMenu();
        }
        copyMenuItem.setDisable(control.getSelectedText().isEmpty());
        contextMenu.show(control, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void createContextMenu() {
        copyMenuItem = new MenuItem("Copy");
        copyMenuItem.setOnAction(action -> getSkinnable().copy());
        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(action -> getSkinnable().selectAll());
        contextMenu = new ContextMenu(copyMenuItem, selectAllItem);
    }

    // ==================== Sizing ====================

    // SkinBase's default height computation asks the child for prefHeight(-1)
    // (unbounded width), ignoring that TextFlow is HORIZONTAL content-biased — so
    // wrapped text would overflow. Delegate to the TextFlow at the actual wrap width.

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double contentWidth = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        return topInset + textFlow.prefHeight(contentWidth) + bottomInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        if (contextMenu != null) {
            contextMenu.hide();
        }
        textFlow.getChildren().clear();
    }
}
