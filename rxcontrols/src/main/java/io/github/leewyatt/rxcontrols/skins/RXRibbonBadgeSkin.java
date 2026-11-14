package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXRibbonBadge.Position;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * Default skin for {@link RXRibbonBadge}.
 *
 * <p>The content sits in a {@code content-slot} pane that fills the control. The
 * {@code ribbon} label lies over the top of the content and reaches past one of
 * its sides; the {@code fold} triangle hangs under that reach. Neither takes
 * part in the size of the control.</p>
 */
public class RXRibbonBadgeSkin extends RXSkinBase<RXRibbonBadge> {

    // Height of the folded corner as a share of its width.
    private static final double FOLD_ASPECT = 0.75;

    private final ContentSlot contentSlot;
    private final Region fold;
    private final Label ribbon;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXRibbonBadgeSkin(RXRibbonBadge control) {
        super(control);
        // A wrapper: mouse events on the content still reach the control's ancestors.
        consumeMouseEvents(false);

        contentSlot = new ContentSlot();

        fold = new Region();
        fold.getStyleClass().add("fold");
        fold.setManaged(false);
        fold.setMouseTransparent(true);

        ribbon = new Label();
        ribbon.getStyleClass().add("ribbon");
        ribbon.setMouseTransparent(true);
        disposer.registerBinding(ribbon.textProperty(), control.textProperty());

        getChildren().addAll(contentSlot, fold, ribbon);

        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.textProperty(), this::updateRibbonVisibility);

        updateContent();
    }

    // ==================== State Changes ====================

    private void updateContent() {
        contentSlot.setContent(getSkinnable().getContent());
        updateRibbonVisibility();
    }

    // A ribbon needs a text to show and a content to lie on.
    private void updateRibbonVisibility() {
        RXRibbonBadge control = getSkinnable();
        String text = control.getText();
        ribbon.setVisible(control.getContent() != null && text != null && !text.isEmpty());
        control.requestLayout();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        contentSlot.layoutContent(x, y, w, h);

        if (!ribbon.isVisible()) {
            fold.setVisible(false);
            return;
        }

        RXRibbonBadge control = getSkinnable();
        // Whole pixels, so the fold ends exactly on the side of the content.
        double overhang = snapSizeX(RXMath.sanitizeFiniteNonNegative(control.getOverhang()));
        double offset = Double.isFinite(control.getRibbonOffset()) ? control.getRibbonOffset() : 0.0;
        boolean start = control.getPosition() == Position.START;
        double contentX = contentSlot.contentX();
        double contentWidth = contentSlot.contentWidth();

        // Never past the far side of the content: a longer text gets an ellipsis.
        double width = Math.min(snapSizeX(ribbon.prefWidth(-1.0)), contentWidth + overhang);
        double height = snapSizeY(ribbon.prefHeight(-1.0));
        double ribbonX = snapPositionX(start ? contentX - overhang : contentX + contentWidth + overhang - width);
        double ribbonY = snapPositionY(contentSlot.contentY() + offset);
        ribbon.resizeRelocate(ribbonX, ribbonY, width, height);

        fold.setVisible(overhang > 0.0);
        double foldX = start ? ribbonX : ribbonX + width - overhang;
        fold.resizeRelocate(foldX, ribbonY + height, overhang, snapSizeY(overhang * FOLD_ASPECT));
    }

    private static double innerSize(double size, double firstInset, double secondInset) {
        return size < 0.0 ? -1.0 : Math.max(0.0, size - firstInset - secondInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + contentSlot.minWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + contentSlot.minHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + contentSlot.prefWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + contentSlot.prefHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + contentSlot.contentMaxWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + contentSlot.contentMaxHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeBaselineOffset(double topInset, double rightInset,
                                           double bottomInset, double leftInset) {
        double offset = contentSlot.getBaselineOffset();
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? offset : topInset + offset;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        contentSlot.setContent(null);
        // A replacement skin is constructed before this one is disposed and has
        // already added its nodes: remove only our own.
        getChildren().removeAll(contentSlot, fold, ribbon);
    }
}
