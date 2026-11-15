package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListSectionCell;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Default skin for {@link RXListSectionCell}: the standard {@link CellSkinBase}
 * (Labeled text + graphic) plus, while the header is
 * {@link RXListSectionCell#isCollapsible() collapsible}, a leading disclosure arrow
 * ({@code .disclosure > .arrow}) that shifts the label content right. The arrow is a
 * shape-backed {@link Region}; its look and its expanded / collapsed orientation are
 * CSS-driven through the cell's {@code :expanded} / {@code :collapsed}
 * pseudo-classes. It is display-only — the whole header row is the toggle, handled
 * by the list view's skin — so it is mouse-transparent. An arrow hidden through CSS
 * ({@code visibility: hidden}) takes no width.
 */
public class RXListSectionCellSkin extends CellSkinBase<RXListSectionCell> {

    private final SkinDisposer disposer = new SkinDisposer();
    private final StackPane disclosure = new StackPane();

    /**
     * Creates the skin and wires the disclosure arrow to the cell's collapsible
     * state.
     *
     * @param cell the cell this skin is attached to
     */
    public RXListSectionCellSkin(RXListSectionCell cell) {
        super(cell);
        Region arrow = new Region();
        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        disclosure.getStyleClass().add("disclosure");
        disclosure.getChildren().add(arrow);
        disclosure.setMouseTransparent(true);
        disposer.registerListener(cell.collapsibleProperty(), this::updateChildren);
        // The LabeledSkinBase constructor ran updateChildren() before the disclosure
        // field existed; run it again to attach the arrow.
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // super.updateChildren() setAll()s the Labeled children, dropping the arrow;
        // the first call comes from the LabeledSkinBase constructor, before the field
        // is initialized.
        if (disclosure != null && getSkinnable().isCollapsible()) {
            getChildren().add(disclosure);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double arrowWidth = disclosureWidth();
        if (arrowWidth > 0.0) {
            layoutInArea(disclosure, x, y, arrowWidth, h, 0, HPos.CENTER, VPos.CENTER);
        }
        layoutLabelInArea(x + arrowWidth, y, Math.max(0.0, w - arrowWidth), h);
    }

    private boolean showsDisclosure() {
        return getSkinnable().isCollapsible() && disclosure.isVisible();
    }

    private double disclosureWidth() {
        return showsDisclosure() ? snapSizeX(disclosure.prefWidth(-1)) : 0.0;
    }

    // ==================== Measurement ====================

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset) + disclosureWidth();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + disclosureWidth();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double label = super.computeMinHeight(width, topInset, rightInset, bottomInset, leftInset);
        return showsDisclosure() ? Math.max(label, topInset + disclosure.minHeight(-1) + bottomInset) : label;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double label = super.computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        return showsDisclosure() ? Math.max(label, topInset + disclosure.prefHeight(-1) + bottomInset) : label;
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(disposer::dispose, () -> getChildren().remove(disclosure), super::dispose);
    }
}
