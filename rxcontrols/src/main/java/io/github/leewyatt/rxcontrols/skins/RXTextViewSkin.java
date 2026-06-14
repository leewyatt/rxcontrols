package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.internal.TextNavigation;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Skin for {@link RXTextView}. Renders the text into a single {@link TextFlow}
 * built entirely from {@link Text} runs (so long runs wrap), and layers a selection
 * background Path beneath the text and a blinking caret Path above it. The selection and
 * caret geometry come from {@link TextFlow#rangeShape(int, int)} /
 * {@link TextFlow#caretShape(int, boolean)}, which are inset-free on JFX 17, so the
 * unmanaged Path layers only need to share the TextFlow's origin.
 *
 * <p>The base implementation renders the whole text as one {@code .plain} run; subclasses
 * split it by overriding {@link #rebuildTextRuns(TextFlow, String)}. User interaction
 * (mouse, keyboard, context menu, caret) is gated by
 * {@link RXTextView#selectableProperty() selectable}; the programmatic selection
 * API still works and its selection is still painted when {@code selectable} is false.
 */
public class RXTextViewSkin extends RXSkinBase<RXTextView> {

    // ==================== Constants ====================

    private static final String TEXT_FLOW_STYLE_CLASS = "text-flow";
    private static final String SELECTION_SHAPE_STYLE_CLASS = "selection-shape";
    private static final String CARET_STYLE_CLASS = "caret";

    /** Style class for plain (non-highlighted) text runs. */
    protected static final String PLAIN_STYLE_CLASS = "plain";

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    // ==================== Nodes ====================

    private final TextFlow textFlow = new TextFlow();
    private final Path selectionLayer = new Path();
    private final Path caretLayer = new Path();

    // ==================== Caret state ====================

    private final Timeline caretBlink = createCaretBlink();
    private boolean caretArmed;
    // The visual column remembered across consecutive Up/Down moves (-1 = none).
    private double targetCaretX = -1.0;

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
        textFlow.getStyleClass().add(TEXT_FLOW_STYLE_CLASS);

        selectionLayer.getStyleClass().add(SELECTION_SHAPE_STYLE_CLASS);
        selectionLayer.setManaged(false);
        selectionLayer.setMouseTransparent(true);
        selectionLayer.setStroke(null);

        caretLayer.getStyleClass().add(CARET_STYLE_CLASS);
        caretLayer.setManaged(false);
        caretLayer.setMouseTransparent(true);
        caretLayer.setOpacity(0.0);

        // back-to-front: selection background, text, caret. Subclasses insert their own
        // layers (e.g. keyword highlight) below the selection via add(0, ...).
        getChildren().setAll(selectionLayer, textFlow, caretLayer);

        disposer.registerBinding(textFlow.lineSpacingProperty(), control.lineSpacingProperty());
        disposer.registerBinding(textFlow.textAlignmentProperty(), control.textAlignmentProperty());

        registerContentListeners(control);

        disposer.registerListener(control.selectionProperty(), () -> getSkinnable().requestLayout());
        disposer.registerListener(control.caretPositionProperty(), this::onCaretMoved);
        disposer.registerListener(control.anchorProperty(), this::updateCaret);
        disposer.registerListener(control.selectableProperty(), this::updateCaret);
        disposer.registerListener(control.focusedProperty(), this::onFocusChanged);
        disposer.registerListener(control.disabledProperty(), this::updateCaret);
        disposer.registerListener(controlTreeShowingProperty(), this::updateCaret);

        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);

        rebuildRuns();
        updateCaret();
    }

    // ==================== Text runs ====================

    /**
     * Registers the listener(s) that rebuild the text runs when the content changes. The
     * base implementation listens to {@code text}. A subclass whose run split is driven
     * by a derived single-source-of-truth property (such as highlight ranges) overrides
     * this to listen to that property instead, so the runs rebuild exactly once per
     * change. Invoked from the constructor — an override may use only {@code control},
     * {@link #disposer} and {@link #rebuildRuns()}.
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
     * renders the whole text as a single {@code .plain} {@link Text} run. Subclasses
     * override this to split the text into differently-styled runs (for example
     * highlighted keyword runs); they must populate {@code flow} with {@link Text}
     * nodes only, so the layout engine can wrap long runs.
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
        run.getStyleClass().add(PLAIN_STYLE_CLASS);
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

    // ==================== Selection / caret geometry ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(textFlow, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
        // Unmanaged layers are not positioned by layoutInArea. Set the layout origin
        // directly rather than relocate(x, y): relocate subtracts the Path's own
        // layoutBounds min, which — because the inset-free range / caret shapes are
        // TextFlow-local (a multi-line or lower-line shape has minY > 0) — would drift the
        // shape outside the control. The Path origin must simply equal the TextFlow origin.
        selectionLayer.setLayoutX(x);
        selectionLayer.setLayoutY(y);
        caretLayer.setLayoutX(x);
        caretLayer.setLayoutY(y);
        rebuildSelectionShape();
        rebuildCaretShape();
    }

    private void rebuildSelectionShape() {
        IndexRange selection = getSkinnable().getSelection();
        if (selection.getLength() == 0) {
            selectionLayer.getElements().clear();
            return;
        }
        selectionLayer.getElements().setAll(textFlow.rangeShape(selection.getStart(), selection.getEnd()));
    }

    private void rebuildCaretShape() {
        int caret = getSkinnable().getCaretPosition();
        caretLayer.getElements().setAll(textFlow.caretShape(caret, true));
    }

    // ==================== Caret visibility / animation ====================

    private Timeline createCaretBlink() {
        // Hard on / off blink: a single KeyFrame toggles the caret's opacity every 0.5s and
        // loops forever (INDEFINITE) — registered with the animation engine once, with no
        // per-cycle restart and no fade.
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.5),
                event -> caretLayer.setOpacity(caretLayer.getOpacity() > 0.0 ? 0.0 : 1.0)));
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }

    private boolean caretShouldShow() {
        RXTextView control = getSkinnable();
        return caretArmed
                && control.isSelectable()
                && control.isFocused()
                && !control.isDisabled()
                && controlTreeShowingProperty().get()
                // On non-Windows the caret hides while a selection is active.
                && (IS_WINDOWS || control.getCaretPosition() == control.getAnchor());
    }

    private void updateCaret() {
        if (caretShouldShow()) {
            if (caretBlink.getStatus() != Animation.Status.RUNNING) {
                caretLayer.setOpacity(1.0);
                caretBlink.playFromStart();
            }
        } else {
            caretBlink.stop();
            caretLayer.setOpacity(0.0);
        }
    }

    private void onCaretMoved() {
        // Restart the blink so the caret is solid at its new position, then resumes blinking.
        if (caretShouldShow()) {
            caretLayer.setOpacity(1.0);
            caretBlink.playFromStart();
        } else {
            caretBlink.stop();
            caretLayer.setOpacity(0.0);
        }
    }

    private void onFocusChanged() {
        if (!getSkinnable().isFocused()) {
            // Disarm the caret when focus is lost; a later mouse press re-arms it.
            caretArmed = false;
        }
        updateCaret();
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
        // The caret appears only after a real mouse interaction, not on automatic focus.
        caretArmed = true;
        targetCaretX = -1.0;
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
        updateCaret();
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

    private void onMouseReleased(MouseEvent event) {
        if (getSkinnable().isSelectable()) {
            updateCaret();
        }
    }

    private int hitIndex(MouseEvent event) {
        // The handler is on the TextFlow, so event coordinates are TextFlow-local; the
        // JFX 17 hitTest is inset-free, so they can be fed directly.
        HitInfo hit = textFlow.hitTest(new Point2D(event.getX(), event.getY()));
        return hit.getInsertionIndex();
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        RXTextView control = getSkinnable();
        if (!control.isSelectable()) {
            return;
        }
        KeyCode code = event.getCode();
        boolean extend = event.isShiftDown();
        if (code != KeyCode.UP && code != KeyCode.DOWN) {
            // Any non-vertical key forgets the column remembered for Up / Down.
            targetCaretX = -1.0;
        }
        if (event.isShortcutDown() && code == KeyCode.C) {
            control.copy();
            event.consume();
        } else if (event.isShortcutDown() && code == KeyCode.A) {
            control.selectAll();
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            control.deselect();
            event.consume();
        } else if (code == KeyCode.LEFT && !event.isShortcutDown()) {
            moveCaret(control.getCaretPosition() - 1, extend);
            event.consume();
        } else if (code == KeyCode.RIGHT && !event.isShortcutDown()) {
            moveCaret(control.getCaretPosition() + 1, extend);
            event.consume();
        } else if (code == KeyCode.UP && !event.isShortcutDown()) {
            // Consume even at the first line so the key does not trigger focus traversal.
            moveCaretVertical(true, extend);
            event.consume();
        } else if (code == KeyCode.DOWN && !event.isShortcutDown()) {
            moveCaretVertical(false, extend);
            event.consume();
        }
    }

    private void moveCaret(int target, boolean extend) {
        if (extend) {
            getSkinnable().extendSelection(target);
        } else {
            getSkinnable().positionCaret(target);
        }
    }

    private void moveCaretVertical(boolean up, boolean extend) {
        int caret = getSkinnable().getCaretPosition();
        double caretX = Double.NaN;
        double top = Double.NaN;
        double bottom = Double.NaN;
        for (PathElement element : textFlow.caretShape(caret, true)) {
            if (element instanceof MoveTo moveTo) {
                caretX = moveTo.getX();
                top = moveTo.getY();
            } else if (element instanceof LineTo lineTo) {
                bottom = lineTo.getY();
            }
        }
        if (Double.isNaN(caretX) || Double.isNaN(top) || Double.isNaN(bottom)) {
            return;
        }
        // Remember the column on the first vertical move so consecutive Up / Down keep it.
        if (targetCaretX < 0) {
            targetCaretX = caretX;
        }
        // Clear the inter-line gap: caretShape spans only the glyph height, so a probe of
        // bottom+1 / top-1 would land in the lineSpacing gap and hitTest would resolve it
        // back to the current line, leaving the caret stuck (Down in particular).
        double lineSpacing = getSkinnable().getLineSpacing();
        double probeY = up ? top - lineSpacing - 1.0 : bottom + lineSpacing + 1.0;
        int target = textFlow.hitTest(new Point2D(targetCaretX, probeY)).getInsertionIndex();
        moveCaret(target, extend);
    }

    // ==================== Context menu ====================

    private void onContextMenuRequested(ContextMenuEvent event) {
        RXTextView control = getSkinnable();
        if (!control.isSelectable() || control.isDisabled()) {
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
        // The caret blink is created once; stop it by reading the live field.
        caretBlink.stop();
        if (contextMenu != null) {
            contextMenu.hide();
        }
        textFlow.getChildren().clear();
    }
}
