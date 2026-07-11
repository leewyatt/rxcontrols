package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;

import java.util.function.DoubleSupplier;

/**
 * Shared base for the ripple-decorated cell skins of the self-built viewports
 * ({@code RXListCellSkin}, {@code RXKanbanCardCellSkin}): the standard
 * {@link CellSkinBase} (Labeled text + graphic rendering) plus an embedded
 * {@link RippleDecoration} placed below everything, the pointer-press ripple
 * trigger with the cell-recycle cancel discipline, and the shared full-row
 * content model:
 *
 * <ul>
 * <li>A <em>resizable</em> graphic under {@link ContentDisplay#GRAPHIC_ONLY} is
 * resized to fill the content area (min/max respected), so full-row rich content
 * needs no manual width binding; pref-locked icons and non-resizable shapes are
 * unaffected.</li>
 * <li>Measurement mirrors layout exactly. {@code LabeledSkinBase} only counts
 * label padding into measurement when text is rendered, so the fill branch adds
 * it back, and the height computations measure the label part at the width the
 * layout will actually hand it (leading slot and, in the fill branch, horizontal
 * label padding subtracted; a negative width is the unconstrained sentinel and
 * passes through). Do not change one side without the other — this invariant is
 * pinned by mutation-verified tests on both concrete skins.</li>
 * </ul>
 *
 * <p>Subclasses contribute an optional leading slot through the
 * {@code leadingSlot*} hooks (all default to a zero-width absent slot) and extra
 * skin children through {@link #appendSkinChildren()}. <strong>Construction-order
 * discipline:</strong> {@code LabeledSkinBase}'s constructor calls
 * {@link #updateChildren()} before any subclass field is initialized, so
 * {@code appendSkinChildren()} must be null-safe against its own fields, and a
 * subclass that appends children must call {@code updateChildren()} again at the
 * end of its own constructor to attach them. All cleanup registers into the
 * single package-private {@link #disposer}; subclasses with manual detach work
 * override {@link #disposeSkinExtras()}, which runs first in the dispose order.
 *
 * @param <C> the concrete cell type
 */
abstract class RippleCellSkinBase<C extends IndexedCell<?>> extends CellSkinBase<C> {

    final SkinDisposer disposer = new SkinDisposer();
    final RippleDecoration ripple;
    private final ObservableValue<Boolean> rippleEnabled;

    /**
     * Creates the skin and wires the ripple. Ends with an {@link #updateChildren()}
     * pass that attaches the ripple layer; subclasses that append their own children
     * must call {@code updateChildren()} again once their fields are initialized.
     *
     * @param cell                the cell this skin is attached to
     * @param rippleEnabled       whether pressing the row creates a ripple
     * @param stateOverlayEnabled whether the hover state overlay may show
     * @param rippleFill          fill of the ripple / state overlay
     * @param rippleOpacity       peak ripple / overlay opacity supplier
     */
    RippleCellSkinBase(C cell,
                       ObservableValue<Boolean> rippleEnabled,
                       ObservableValue<Boolean> stateOverlayEnabled,
                       ObservableValue<Paint> rippleFill,
                       DoubleSupplier rippleOpacity) {
        super(cell);
        this.rippleEnabled = rippleEnabled;
        ripple = new RippleDecoration(cell, rippleEnabled, stateOverlayEnabled,
                rippleFill, rippleOpacity, null, null);

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

        updateChildren();
    }

    // ==================== Children ====================

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // class's fields are initialized; super.updateChildren() also setAll()s the
        // children (text/graphic only), dropping the skin-owned nodes — re-append
        // them after every call.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
        appendSkinChildren();
    }

    /**
     * Re-appends subclass-owned children after {@code super.updateChildren()}
     * dropped them. Called from the {@code LabeledSkinBase} constructor before the
     * subclass's fields are initialized — implementations must be null-safe against
     * their own fields.
     */
    void appendSkinChildren() {
    }

    // ==================== Layout (CheckBoxSkin pattern) ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        C cell = getSkinnable();
        double slotW = layoutLeadingSlot(x, y, h);
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

    /**
     * Lays out the leading slot (if any) inside the content box and returns the
     * width it occupies; the label content area starts right of it. The default has
     * no slot and returns {@code 0}.
     *
     * @param x content-box left edge
     * @param y content-box top edge
     * @param h content-box height
     * @return the slot width, snapped
     */
    double layoutLeadingSlot(double x, double y, double h) {
        return 0.0;
    }

    // The graphic-only fill branch of layoutChildren; the compute* methods below
    // mirror it, because LabeledSkinBase only counts labelPadding into measurement
    // when text is rendered — under GRAPHIC_ONLY the padding the fill branch
    // reserves has to be added back, or measured rows compress/clip the graphic.
    private boolean fillsGraphicOnly() {
        C cell = getSkinnable();
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

    // ==================== Leading slot measurement hooks ====================

    /** Minimum width of the leading slot; {@code 0} when there is none. */
    double leadingSlotMinWidth() {
        return 0.0;
    }

    /** Preferred width of the leading slot; {@code 0} when there is none. */
    double leadingSlotPrefWidth() {
        return 0.0;
    }

    /** Minimum height of the leading slot; {@code 0} when there is none. */
    double leadingSlotMinHeight() {
        return 0.0;
    }

    /** Preferred height of the leading slot; {@code 0} when there is none. */
    double leadingSlotPrefHeight() {
        return 0.0;
    }

    // ==================== Measurement (mirrors the layout) ====================

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(leadingSlotMinWidth()) + fillHorizontalLabelPadding();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Measure the label part at the exact width the layout will hand it (after
        // the slot and, in the fill branch, the horizontal label padding): a
        // width-dependent graphic (wrapping full-row content) measured wider than it
        // is laid out would under-estimate row heights. A negative width is the
        // unconstrained sentinel and passes through. The slot height caps from below
        // so a short row still fits its slot content.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - leadingSlotMinWidth() - fillHorizontalLabelPadding());
        return Math.max(
                super.computeMinHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                        + fillVerticalLabelPadding(),
                topInset + leadingSlotMinHeight() + bottomInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(leadingSlotPrefWidth()) + fillHorizontalLabelPadding();
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        // Same width formula as computeMinHeight: measurement mirrors the layout.
        double labelWidth = width < 0
                ? width
                : Math.max(0.0, width - leadingSlotPrefWidth() - fillHorizontalLabelPadding());
        return Math.max(
                super.computePrefHeight(labelWidth, topInset, rightInset, bottomInset, leftInset)
                        + fillVerticalLabelPadding(),
                topInset + leadingSlotPrefHeight() + bottomInset);
    }

    // ==================== Ripple trigger ====================

    private void onPressed(MouseEvent event) {
        C cell = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !Boolean.TRUE.equals(rippleEnabled.getValue())
                || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        // A press anywhere on the row — including over display-only slot content —
        // is one row click, so it always ripples.
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    // ==================== Dispose ====================

    /**
     * Runs the dispose chain: {@link #disposeSkinExtras()} (subclass detach work),
     * then the ripple, then the shared {@link #disposer}, then the standard
     * {@link CellSkinBase} cleanup.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeSkinExtras, this::disposeRipple, disposer::dispose, super::dispose);
    }

    /**
     * Subclass cleanup hook, run first in the dispose order (while the cell and
     * children are still intact). The default does nothing.
     */
    void disposeSkinExtras() {
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
