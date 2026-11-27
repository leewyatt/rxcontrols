package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.RXIcon;
import javafx.geometry.HPos;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;

/**
 * One face of a feedback button: an indicator followed by a caption, placed
 * as one block by the button's alignment. The gap between the two only
 * exists while both have a width, so a zero-sized indicator or an empty
 * caption leaves no hole.
 */
public final class IndicatorFace extends Region {

    private static final String INDICATOR_STYLE_CLASS = "indicator";

    private final Labeled owner;
    private final Label caption = new Label();
    private Node indicator;

    /**
     * Creates a face with a shape indicator: a region carrying the
     * {@code indicator} style class, shaped and sized from CSS.
     *
     * @param owner      the button the face belongs to; its alignment and
     *                   graphic-text gap place the block
     * @param styleClass the style class of the face
     */
    public IndicatorFace(Labeled owner, String styleClass) {
        this.owner = owner;
        getStyleClass().add(styleClass);

        caption.setMnemonicParsing(false);
        // setAll drops the built-in .label class. The caption's text fill
        // and font are bound to the button, so .label rules cannot style
        // it anyway, and .label:disabled would dim it a second time inside
        // the disabled button.
        caption.getStyleClass().setAll("caption");

        indicator = createShapeIndicator();
        getChildren().addAll(indicator, caption);
    }

    /**
     * Creates the default indicator of a face: a region carrying the
     * {@code indicator} style class that keeps its preferred size, ignores
     * the mouse and is never mirrored.
     *
     * @return a new shape indicator
     */
    public static Region createShapeIndicator() {
        RXIcon shape = new RXIcon();
        shape.getStyleClass().add(INDICATOR_STYLE_CLASS);
        shape.setMouseTransparent(true);
        // The built-in shapes have no direction: in a right-to-left button
        // the indicator moves to the other side but is not drawn mirrored.
        shape.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return shape;
    }

    /**
     * Returns the caption label of this face.
     *
     * @return the caption
     */
    public Label getCaption() {
        return caption;
    }

    /**
     * Returns the indicator of this face.
     *
     * @return the indicator
     */
    public Node getIndicator() {
        return indicator;
    }

    /**
     * Replaces the indicator of this face. The node is placed at its
     * preferred size, in front of the caption.
     *
     * @param indicator the new indicator
     */
    public void setIndicator(Node indicator) {
        if (this.indicator == indicator) {
            return;
        }
        // By reference, not by index: a node the application also put
        // somewhere else has already left this face.
        getChildren().remove(this.indicator);
        this.indicator = indicator;
        getChildren().add(0, indicator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        return caption.getContentBias();
    }

    private double indicatorWidth() {
        return snapSizeX(indicator.prefWidth(-1));
    }

    private double indicatorHeight() {
        return snapSizeY(indicator.prefHeight(-1));
    }

    private double gap(double indicatorWidth) {
        return indicatorWidth > 0.0 && caption.prefWidth(-1) > 0.0
                ? snapSpaceX(owner.getGraphicTextGap())
                : 0.0;
    }

    private double captionWidthFor(double width, double indicatorWidth, double gap) {
        if (width < 0) {
            return -1;
        }
        return Math.max(0.0, width - snappedLeftInset() - snappedRightInset() - indicatorWidth - gap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height) {
        double indicatorWidth = indicatorWidth();
        return snappedLeftInset() + indicatorWidth + gap(indicatorWidth)
                + snapSizeX(caption.prefWidth(-1)) + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height) {
        double indicatorWidth = indicatorWidth();
        return snappedLeftInset() + indicatorWidth + gap(indicatorWidth)
                + snapSizeX(caption.minWidth(-1)) + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width) {
        double indicatorWidth = indicatorWidth();
        double captionWidth = captionWidthFor(width, indicatorWidth, gap(indicatorWidth));
        return snappedTopInset()
                + Math.max(indicatorHeight(), snapSizeY(caption.prefHeight(captionWidth)))
                + snappedBottomInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width) {
        double indicatorWidth = indicatorWidth();
        double captionWidth = captionWidthFor(width, indicatorWidth, gap(indicatorWidth));
        return snappedTopInset()
                + Math.max(indicatorHeight(), snapSizeY(caption.minHeight(captionWidth)))
                + snappedBottomInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double width = Math.max(0.0, getWidth() - x - snappedRightInset());
        double height = Math.max(0.0, getHeight() - y - snappedBottomInset());

        double indicatorWidth = indicatorWidth();
        double indicatorHeight = indicatorHeight();
        double gap = gap(indicatorWidth);
        double captionWidth = Math.min(snapSizeX(caption.prefWidth(-1)),
                Math.max(0.0, width - indicatorWidth - gap));
        double captionHeight = Math.min(snapSizeY(caption.prefHeight(captionWidth)), height);

        double blockWidth = indicatorWidth + gap + captionWidth;
        double blockHeight = Math.max(indicatorHeight, captionHeight);
        Pos alignment = owner.getAlignment() == null ? Pos.CENTER : owner.getAlignment();
        double blockX = x;
        if (alignment.getHpos() == HPos.CENTER) {
            blockX += (width - blockWidth) / 2.0;
        } else if (alignment.getHpos() == HPos.RIGHT) {
            blockX += width - blockWidth;
        }
        double blockY = y;
        if (alignment.getVpos() == VPos.BOTTOM) {
            blockY += height - blockHeight;
        } else if (alignment.getVpos() != VPos.TOP) {
            blockY += (height - blockHeight) / 2.0;
        }

        indicator.resizeRelocate(snapPositionX(blockX),
                snapPositionY(blockY + (blockHeight - indicatorHeight) / 2.0),
                indicatorWidth, indicatorHeight);
        caption.resizeRelocate(snapPositionX(blockX + indicatorWidth + gap),
                snapPositionY(blockY + (blockHeight - captionHeight) / 2.0),
                captionWidth, captionHeight);
    }
}
