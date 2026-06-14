package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSelectableText;
import io.github.leewyatt.rxcontrols.internal.TextNavigation;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.shape.Path;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Skin for {@link RXSelectableText}. Renders the text into a single {@link TextFlow}
 * built entirely from {@link Text} runs (so long runs wrap), and layers a selection
 * background Path beneath the text and a blinking caret Path above it. The selection and
 * caret geometry come from {@link TextFlow#rangeShape(int, int)} /
 * {@link TextFlow#caretShape(int, boolean)}, which are inset-free on JFX 17, so the
 * unmanaged Path layers only need to share the TextFlow's origin.
 *
 * <p>The base implementation renders the whole text as one {@code .plain} run; subclasses
 * split it by overriding {@link #rebuildTextRuns(TextFlow, String)}. User interaction
 * (mouse, keyboard, context menu, caret) is gated by
 * {@link RXSelectableText#selectableProperty() selectable}; the programmatic selection
 * API still works and its selection is still painted when {@code selectable} is false.
 */
public class RXSelectableTextSkin extends RXSkinBase<RXSelectableText> {

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

    private final BooleanProperty blink = new SimpleBooleanProperty(this, "blink", true);
    private final CaretBlinking caretBlinking = new CaretBlinking();

    private ContextMenu contextMenu;
    private MenuItem copyMenuItem;

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXSelectableTextSkin(RXSelectableText control) {
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
        disposer.registerListener(control.anchorProperty(), this::updateCaretVisibility);
        disposer.registerListener(blink, this::updateCaretVisibility);
        disposer.registerListener(control.selectableProperty(), this::updateCaretAnimation);
        disposer.registerListener(control.focusedProperty(), this::updateCaretAnimation);
        disposer.registerListener(control.disabledProperty(), this::updateCaretAnimation);
        disposer.registerListener(controlTreeShowingProperty(), this::updateCaretAnimation);

        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(textFlow, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);

        rebuildRuns();
        updateCaretAnimation();
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
    protected void registerContentListeners(RXSelectableText control) {
        disposer.registerListener(control.textProperty(), this::rebuildRuns);
    }

    /**
     * Rebuilds the text runs from the current text. Subclasses call this to refresh the
     * runs after their content source changes.
     */
    protected final void rebuildRuns() {
        rebuildTextRuns(textFlow, textOrEmpty());
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

    /**
     * Returns this control's text, treating {@code null} as empty.
     *
     * @return the text, never {@code null}
     */
    protected final String textOrEmpty() {
        String value = getSkinnable().getText();
        return value == null ? "" : value;
    }

    // ==================== Selection / caret geometry ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(textFlow, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
        // Unmanaged layers are not positioned by layoutInArea: align to the TextFlow
        // origin so the inset-free range / caret shapes line up with the glyphs.
        selectionLayer.relocate(x, y);
        caretLayer.relocate(x, y);
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

    private void updateCaretVisibility() {
        RXSelectableText control = getSkinnable();
        boolean visible = !blink.get()
                && control.isSelectable()
                && control.isFocused()
                && !control.isDisabled()
                // On non-Windows the caret hides while a selection is active (JFX behaviour).
                && (IS_WINDOWS || control.getCaretPosition() == control.getAnchor());
        caretLayer.setOpacity(visible ? 1.0 : 0.0);
    }

    private boolean isCaretActive() {
        RXSelectableText control = getSkinnable();
        return control.isSelectable()
                && control.isFocused()
                && !control.isDisabled()
                && controlTreeShowingProperty().get();
    }

    private void updateCaretAnimation() {
        if (isCaretActive()) {
            caretBlinking.start();
        } else {
            caretBlinking.stop();
            blink.set(true);
        }
        updateCaretVisibility();
    }

    private void onCaretMoved() {
        // Restart the blink phase so the caret shows solid at its new position, then
        // resumes blinking (mirrors TextInputControlSkin resetting the caret on move).
        if (isCaretActive()) {
            caretBlinking.start();
        }
        updateCaretVisibility();
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        RXSelectableText control = getSkinnable();
        if (!control.isSelectable() || control.isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!control.isFocused()) {
            control.requestFocus();
        }
        int index = hitIndex(event);
        if (event.getClickCount() >= 3) {
            IndexRange paragraph = TextNavigation.paragraphRangeAt(textOrEmpty(), index);
            control.selectRange(paragraph.getStart(), paragraph.getEnd());
        } else if (event.getClickCount() == 2) {
            IndexRange word = TextNavigation.wordRangeAt(textOrEmpty(), index);
            control.selectRange(word.getStart(), word.getEnd());
        } else if (event.isShiftDown()) {
            control.extendSelection(index);
        } else {
            control.positionCaret(index);
        }
        event.consume();
    }

    private void onMouseDragged(MouseEvent event) {
        RXSelectableText control = getSkinnable();
        if (!control.isSelectable() || control.isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        control.extendSelection(hitIndex(event));
        event.consume();
    }

    private void onMouseReleased(MouseEvent event) {
        if (getSkinnable().isSelectable()) {
            updateCaretAnimation();
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
        RXSelectableText control = getSkinnable();
        if (!control.isSelectable()) {
            return;
        }
        KeyCode code = event.getCode();
        boolean extend = event.isShiftDown();
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
        }
    }

    private void moveCaret(int target, boolean extend) {
        if (extend) {
            getSkinnable().extendSelection(target);
        } else {
            getSkinnable().positionCaret(target);
        }
    }

    // ==================== Context menu ====================

    private void onContextMenuRequested(ContextMenuEvent event) {
        RXSelectableText control = getSkinnable();
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
        // The caret Timeline is created once; stop it by reading the live field.
        caretBlinking.stop();
        if (contextMenu != null) {
            contextMenu.hide();
        }
        textFlow.getChildren().clear();
    }

    // ==================== Caret blinking ====================

    /**
     * Drives the {@link #blink} property on a 1s cycle (visible for the first half,
     * hidden for the second), mirroring {@code TextInputControlSkin.CaretBlinking}.
     */
    private final class CaretBlinking {

        private final Timeline timeline;

        CaretBlinking() {
            timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, event -> blink.set(false)),
                    new KeyFrame(Duration.seconds(0.5), event -> blink.set(true)),
                    new KeyFrame(Duration.seconds(1.0)));
            timeline.setCycleCount(Animation.INDEFINITE);
        }

        void start() {
            blink.set(false);
            timeline.playFromStart();
        }

        void stop() {
            timeline.stop();
        }
    }
}
