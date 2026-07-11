package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXKanbanCardCell}: the standard {@link CellSkinBase} (Labeled
 * text + graphic rendering) plus an embedded {@link RippleDecoration} placed
 * below the card content; it supplies the pointer-press ripple trigger and
 * clears the ripple when the cell is recycled.
 *
 * <p>One deliberate deviation from plain {@code LabeledSkinBase} layout: a
 * <em>resizable</em> graphic under {@link ContentDisplay#GRAPHIC_ONLY} is
 * resized to fill the card's content area (min/max respected, label padding
 * honored in both layout and measurement), so rich full-card content needs no
 * manual width binding; pref-locked icons and non-resizable shapes are
 * unaffected.
 *
 * @param <T> the card type
 */
public class RXKanbanCardCellSkin<T> extends CellSkinBase<RXKanbanCardCell<T>> {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    /**
     * Creates the skin and wires the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXKanbanCardCellSkin(RXKanbanCardCell<T> cell) {
        super(cell);
        ripple = new RippleDecoration(cell, cell.rippleEnabledProperty(),
                cell.stateOverlayEnabledProperty(), cell.rippleFillProperty(),
                cell::getRippleOpacity, null, null);

        disposer.registerEventHandler(cell, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventHandler(cell, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        // Drop any in-flight ripple AND cancel a held press when the cell is recycled
        // so neither bleeds onto the next card: the index changes on every recycle and
        // on parking (index -> -1); the item listener additionally covers an in-place
        // replace at the same index. cancelInteraction (not clear) also resets the
        // pressed flag, so a cell recycled mid-press does not re-derive a pressed
        // overlay on the new card.
        disposer.registerListener(cell.indexProperty(), ripple::cancelInteraction);
        disposer.registerListener(cell.itemProperty(), ripple::cancelInteraction);

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // skin's fields are initialized.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXKanbanCardCell<T> cell = getSkinnable();
        if (fillsGraphicOnly()) {
            // Full-card rich content: fill the content area (label padding honored,
            // min/max respected by layoutInArea) — pref-locked icons stay pref-sized.
            Insets lp = cell.getLabelPadding();
            layoutInArea(cell.getGraphic(),
                    x + snapSizeX(lp.getLeft()), y + snapSizeY(lp.getTop()),
                    Math.max(0.0, w - snapSizeX(lp.getLeft()) - snapSizeX(lp.getRight())),
                    Math.max(0.0, h - snapSizeY(lp.getTop()) - snapSizeY(lp.getBottom())),
                    0, HPos.LEFT, VPos.CENTER);
        } else {
            super.layoutChildren(x, y, w, h);
        }
        ripple.layout(cell.getWidth(), cell.getHeight());
    }

    // The graphic-only fill branch of layoutChildren; the compute* methods below must
    // mirror it, because LabeledSkinBase only counts labelPadding into measurement
    // when text is rendered — under GRAPHIC_ONLY the padding the fill branch reserves
    // has to be added back, or measured cards compress/clip the graphic.
    private boolean fillsGraphicOnly() {
        RXKanbanCardCell<T> cell = getSkinnable();
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
                + fillHorizontalLabelPadding();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Measure the label part at the exact width the layout will hand it (in the
        // fill branch: minus the horizontal label padding): a width-dependent graphic
        // (wrapping full-card content) measured wider than it is laid out would
        // under-estimate card heights. A negative width is the unconstrained sentinel
        // and passes through.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - fillHorizontalLabelPadding());
        return super.computeMinHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                + fillVerticalLabelPadding();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + fillHorizontalLabelPadding();
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        // Same width formula as computeMinHeight: measurement mirrors the layout.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - fillHorizontalLabelPadding());
        return super.computePrefHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                + fillVerticalLabelPadding();
    }

    /**
     * Stops ripple animations, removes the ripple layer and unregisters all ripple
     * listeners before the standard {@link CellSkinBase} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    // ==================== Ripple trigger ====================

    private void onPressed(MouseEvent event) {
        RXKanbanCardCell<T> cell = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !cell.isRippleEnabled() || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
