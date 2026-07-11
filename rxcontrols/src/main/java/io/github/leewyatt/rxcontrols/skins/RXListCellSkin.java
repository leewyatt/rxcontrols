package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Skin for {@link RXListCell}: the standard {@link CellSkinBase} (Labeled
 * text + graphic rendering) plus two skin-owned layers, following the
 * {@code CheckBoxSkin} extension pattern — a leading <em>selection slot</em>
 * (checkbox / checkmark, chosen from the owning list view's effective selection
 * visual mode) that shifts the label content area right, and an embedded
 * {@link RippleDecoration} placed below everything. Width and height
 * computations compensate for the slot; the slot's reserved width itself is
 * CSS-driven (collapsed to zero in ROW mode via the view's
 * {@code :selection-row} pseudo-class).
 *
 * <p>One deliberate deviation from plain {@code LabeledSkinBase} layout: a
 * <em>resizable</em> graphic under {@link ContentDisplay#GRAPHIC_ONLY} is
 * resized to fill the content area (min/max respected), so full-row custom
 * content needs no manual width binding; pref-locked icons and non-resizable
 * shapes are unaffected.
 *
 * <p>Selection toggling is handled centrally by {@code RXListViewSkin}; the
 * checkbox / checkmark are mouse-transparent display indicators, so a press
 * anywhere on the row — including over them — is one row click that ripples.
 *
 * @param <T> the item type
 */
public class RXListCellSkin<T> extends CellSkinBase<RXListCell<T>> {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;
    private final StackPane selectionSlot = new StackPane();
    private final CheckBox checkBox = new CheckBox();
    private final Region checkmark = new Region();

    // The effective visual mode depends on view.selectionVisualMode and the selection
    // model's selectionMode (AUTO resolution); both owners outlive this cell (the
    // viewport discards cell pools without disposing skins on a factory change), so
    // the view/model registrations are weak — the skin island stays collectable —
    // with the real listeners held strongly here. Tracked owners because both are
    // swappable and SkinDisposer has no per-item unregister.
    private final InvalidationListener slotRefreshListener = obs -> refreshSelectionSlot();
    private final InvalidationListener selectionModelChangedListener = obs -> rebindSelectionModel();
    private final WeakInvalidationListener weakSlotRefreshListener =
            new WeakInvalidationListener(slotRefreshListener);
    private final WeakInvalidationListener weakSelectionModelChangedListener =
            new WeakInvalidationListener(selectionModelChangedListener);
    private RXListView<T> observedView;
    private MultipleSelectionModel<T> observedModel;

    /**
     * Creates the skin and wires the selection slot and the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXListCellSkin(RXListCell<T> cell) {
        super(cell);
        ripple = new RippleDecoration(cell, cell.rippleEnabledProperty(),
                cell.stateOverlayEnabledProperty(), cell.rippleFillProperty(),
                cell::getRippleOpacity, null, null);

        selectionSlot.getStyleClass().add("selection-slot");
        // The checkbox / checkmark are pure visual + a11y indicators of the single
        // selection state; all pointer toggling is handled centrally by the list
        // view's skin, so they never independently react to the mouse or take
        // keyboard focus. The checkbox simply mirrors the cell's selected state
        // (one-way bind; never user-driven), and the checkmark's visibility follows
        // the :selected pseudo-class via CSS.
        checkBox.setFocusTraversable(false);
        checkBox.setMouseTransparent(true);
        checkBox.setAllowIndeterminate(false);
        checkmark.getStyleClass().add("checkmark");
        checkmark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        checkmark.setMouseTransparent(true);
        disposer.registerBinding(checkBox.selectedProperty(), cell.selectedProperty());

        disposer.registerEventHandler(cell, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventHandler(cell, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        // Drop any in-flight ripple AND cancel a held press when the cell is recycled
        // so neither bleeds onto the next item: the index changes on every recycle
        // (covering a new item even at a duplicate object reference) and on parking
        // (index -> -1); the item listener additionally covers an in-place replace at
        // the same index. cancelInteraction (not clear) also resets the pressed flag,
        // so a cell recycled mid-press does not re-derive a pressed overlay on the new
        // item at the next layout pass; pointer-inside is kept so hover still follows.
        disposer.registerListener(cell.indexProperty(), ripple::cancelInteraction);
        disposer.registerListener(cell.itemProperty(), ripple::cancelInteraction);

        disposer.registerListener(cell.listViewProperty(), this::rebindView);
        disposer.registerListener(cell.emptyProperty(), this::refreshSelectionSlot);
        rebindView();

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // skin's fields are initialized; super.updateChildren() also setAll()s the
        // children (text/graphic only), dropping the skin-owned nodes — re-append
        // them after every call.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
        if (selectionSlot != null) {
            getChildren().add(selectionSlot);
        }
    }

    // ==================== Layout (CheckBoxSkin pattern) ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXListCell<T> cell = getSkinnable();
        double slotW = snapSizeX(selectionSlot.prefWidth(-1));
        layoutInArea(selectionSlot, x, y, slotW, h, 0, HPos.CENTER, VPos.CENTER);
        double contentX = x + slotW;
        double contentW = Math.max(0.0, w - slotW);
        if (fillsGraphicOnly()) {
            // Full-row rich content: fill the content area (label padding honored,
            // min/max respected by layoutInArea) — pref-locked icons stay pref-sized.
            Insets lp = cell.getLabelPadding();
            layoutInArea(cell.getGraphic(),
                    contentX + snapSizeX(lp.getLeft()), y + snapSizeY(lp.getTop()),
                    Math.max(0.0, contentW - snapSizeX(lp.getLeft()) - snapSizeX(lp.getRight())),
                    Math.max(0.0, h - snapSizeY(lp.getTop()) - snapSizeY(lp.getBottom())),
                    0, HPos.LEFT, VPos.CENTER);
        } else {
            layoutLabelInArea(contentX, y, contentW, h);
        }
        ripple.layout(cell.getWidth(), cell.getHeight());
    }

    // The graphic-only fill branch of layoutChildren; the compute* methods below must
    // mirror it, because LabeledSkinBase only counts labelPadding into measurement
    // when text is rendered — under GRAPHIC_ONLY the padding the fill branch reserves
    // has to be added back, or measured rows compress/clip the graphic.
    private boolean fillsGraphicOnly() {
        RXListCell<T> cell = getSkinnable();
        Node graphic = cell.getGraphic();
        return cell.getContentDisplay() == ContentDisplay.GRAPHIC_ONLY
                && graphic != null && graphic.isResizable();
    }

    private double fillHorizontalLabelPadding() {
        Insets lp = getSkinnable().getLabelPadding();
        return fillsGraphicOnly() ? snapSizeX(lp.getLeft()) + snapSizeX(lp.getRight()) : 0.0;
    }

    private double fillVerticalLabelPadding() {
        Insets lp = getSkinnable().getLabelPadding();
        return fillsGraphicOnly() ? snapSizeY(lp.getTop()) + snapSizeY(lp.getBottom()) : 0.0;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(selectionSlot.minWidth(-1)) + fillHorizontalLabelPadding();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Measure the label part at the exact width the layout will hand it (after
        // the slot and, in the fill branch, the horizontal label padding): a
        // width-dependent graphic (wrapping full-row content) measured wider than it
        // is laid out would under-estimate row heights. A negative width is the
        // unconstrained sentinel and passes through. The slot height caps from below
        // so an empty/short row in CHECKBOX / CHECKMARK mode still fits its indicator.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - selectionSlot.minWidth(-1) - fillHorizontalLabelPadding());
        return Math.max(
                super.computeMinHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                        + fillVerticalLabelPadding(),
                topInset + selectionSlot.minHeight(-1) + bottomInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(selectionSlot.prefWidth(-1)) + fillHorizontalLabelPadding();
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        // Same width formula as computeMinHeight: measurement mirrors the layout.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - selectionSlot.prefWidth(-1) - fillHorizontalLabelPadding());
        return Math.max(
                super.computePrefHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                        + fillVerticalLabelPadding(),
                topInset + selectionSlot.prefHeight(-1) + bottomInset);
    }

    // ==================== Selection slot ====================

    private void rebindView() {
        if (observedView != null) {
            observedView.selectionVisualModeProperty().removeListener(weakSlotRefreshListener);
            observedView.selectionModelProperty().removeListener(weakSelectionModelChangedListener);
        }
        observedView = getSkinnable().getListView();
        if (observedView != null) {
            observedView.selectionVisualModeProperty().addListener(weakSlotRefreshListener);
            observedView.selectionModelProperty().addListener(weakSelectionModelChangedListener);
        }
        rebindSelectionModel();
    }

    private void rebindSelectionModel() {
        if (observedModel != null) {
            observedModel.selectionModeProperty().removeListener(weakSlotRefreshListener);
        }
        observedModel = observedView == null ? null : observedView.getSelectionModel();
        if (observedModel != null) {
            observedModel.selectionModeProperty().addListener(weakSlotRefreshListener);
        }
        refreshSelectionSlot();
    }

    private void refreshSelectionSlot() {
        RXListCell<T> cell = getSkinnable();
        RXListSelectionVisualMode mode = effectiveVisualMode();
        if (cell.isEmpty() || mode == RXListSelectionVisualMode.ROW) {
            // ROW / empty rows show no indicator; the slot stays managed so its
            // (CSS-driven, ROW-collapsed) reserved width keeps applying.
            setSlotChild(null);
            selectionSlot.setVisible(false);
        } else {
            setSlotChild(mode == RXListSelectionVisualMode.CHECKBOX ? checkBox : checkmark);
            selectionSlot.setVisible(true);
        }
    }

    private void setSlotChild(Node child) {
        if (child == null) {
            selectionSlot.getChildren().clear();
        } else if (selectionSlot.getChildren().size() != 1 || selectionSlot.getChildren().get(0) != child) {
            selectionSlot.getChildren().setAll(child);
        }
    }

    private RXListSelectionVisualMode effectiveVisualMode() {
        RXListView<T> view = getSkinnable().getListView();
        if (view == null) {
            return RXListSelectionVisualMode.ROW;
        }
        return RXListSelectionVisualMode.resolve(view.getSelectionVisualMode(), view.getSelectionMode());
    }

    // ==================== Dispose ====================

    /**
     * Detaches the selection-slot tracking, stops ripple animations, removes the
     * skin-owned layers and unregisters all listeners before the standard
     * {@link CellSkinBase} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeSlot, this::disposeRipple, disposer::dispose, super::dispose);
    }

    // ==================== Ripple trigger ====================

    private void onPressed(MouseEvent event) {
        RXListCell<T> cell = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !cell.isRippleEnabled() || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        // A press anywhere on the row — including over the display-only checkbox /
        // checkmark — is one row click, so it always ripples.
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    private void disposeSlot() {
        if (observedModel != null) {
            observedModel.selectionModeProperty().removeListener(weakSlotRefreshListener);
            observedModel = null;
        }
        if (observedView != null) {
            observedView.selectionVisualModeProperty().removeListener(weakSlotRefreshListener);
            observedView.selectionModelProperty().removeListener(weakSelectionModelChangedListener);
            observedView = null;
        }
        getChildren().remove(selectionSlot);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
