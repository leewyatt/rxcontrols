package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.skin.CellSkinBase;
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
public class RXListCellSkin<T> extends RippleCellSkinBase<RXListCell<T>> {

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
        super(cell, cell.rippleEnabledProperty(), cell.stateOverlayEnabledProperty(),
                cell.rippleFillProperty(), cell::getRippleOpacity);

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

        disposer.registerListener(cell.listViewProperty(), this::rebindView);
        disposer.registerListener(cell.emptyProperty(), this::refreshSelectionSlot);
        rebindView();

        // The base constructor's updateChildren() pass ran before this class's
        // fields were initialized; re-run it now to attach the selection slot.
        updateChildren();
    }

    @Override
    void appendSkinChildren() {
        // Null guard: the first call comes from the LabeledSkinBase constructor,
        // before this class's fields are initialized.
        if (selectionSlot != null) {
            getChildren().add(selectionSlot);
        }
    }

    // ==================== Leading slot (selection indicator) ====================

    @Override
    double layoutLeadingSlot(double x, double y, double h) {
        double slotW = snapSizeX(selectionSlot.prefWidth(-1));
        layoutInArea(selectionSlot, x, y, slotW, h, 0, HPos.CENTER, VPos.CENTER);
        return slotW;
    }

    @Override
    double leadingSlotMinWidth() {
        return selectionSlot.minWidth(-1);
    }

    @Override
    double leadingSlotPrefWidth() {
        return selectionSlot.prefWidth(-1);
    }

    @Override
    double leadingSlotMinHeight() {
        return selectionSlot.minHeight(-1);
    }

    @Override
    double leadingSlotPrefHeight() {
        return selectionSlot.prefHeight(-1);
    }

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

    @Override
    void disposeSkinExtras() {
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
}
