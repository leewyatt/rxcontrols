package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 * Chrome container for one kanban column: it stacks the column header, the
 * virtualizing card viewport ({@code .content}), an optional footer and an empty-
 * state placeholder, and lays them out itself so each is a direct child styleable
 * as {@code .column > .header / .content / .footer / .placeholder}. The board skin
 * owns the horizontal arrangement of these boxes; this box owns one column's
 * vertical chrome.
 *
 * @param <T> the card type
 */
final class KanbanColumnBox<T> extends Region {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass DROP_TARGET_PSEUDO_CLASS = PseudoClass.getPseudoClass("drop-target");
    private static final PseudoClass OVER_LIMIT_PSEUDO_CLASS = PseudoClass.getPseudoClass("over-limit");
    private static final PseudoClass DRAGGING_PSEUDO_CLASS = PseudoClass.getPseudoClass("dragging");

    // Horizontal breathing room and header/footer gap for the card area so cards do
    // not butt against the column edges (the column itself carries no side padding).
    private static final double CARD_AREA_HGAP = 6.0;
    private static final double CARD_AREA_VGAP = 6.0;

    private final RXKanbanView<T> control;
    private final RXKanbanColumn<T> column;
    private final KanbanColumnViewport<T> viewport;

    private Node headerNode;
    private Node footerNode;
    private Node placeholderNode;

    // Non-null only while the default (factory-less) header is in use, so column
    // property changes can refresh its text in place.
    private Label defaultTitleLabel;
    private Label defaultCountLabel;

    private final InvalidationListener columnListener = obs -> refreshFromModel();
    private final InvalidationListener visibilityListener = obs -> animateVisibility();

    // Hide progress: 0 fully shown, 1 fully hidden. The board skin reads it to
    // interpolate this column's width to zero; the box reads it to fade its card area.
    // Animated by a Timeline when animation is enabled, else stepped.
    private final DoubleProperty hideProgress = new SimpleDoubleProperty(this, "hideProgress");
    private Timeline hideAnimation;

    // Set by the board skin so it can migrate / refresh board-level selection and
    // focus when this column's model changes.
    private Runnable modelListener;
    // Set by the board skin so a hide-progress frame relays out the board (columns
    // reflow to the interpolating width).
    private Runnable boardRelayout;

    KanbanColumnBox(RXKanbanView<T> control, RXKanbanColumn<T> column) {
        this.control = control;
        this.column = column;
        getStyleClass().add("column");
        // The board skin positions every column box explicitly; keep the enclosing
        // .columns Pane from resizing it to its (chrome-only) preferred size.
        setManaged(false);
        viewport = new KanbanColumnViewport<>(control, column);
        viewport.setManaged(false);
        getChildren().add(viewport);

        rebuildHeader();
        rebuildFooter();
        rebuildPlaceholder();

        hideProgress.set(column.isVisible() ? 0.0 : 1.0);
        hideProgress.addListener(obs -> {
            if (boardRelayout != null) {
                boardRelayout.run();
            }
            requestLayout();
        });

        column.cardCountProperty().addListener(columnListener);
        column.titleProperty().addListener(columnListener);
        column.wipLimitProperty().addListener(columnListener);
        column.visibleProperty().addListener(visibilityListener);
        refreshFromModel();
    }

    // ==================== Accessors ====================

    KanbanColumnViewport<T> getViewport() {
        return viewport;
    }

    RXKanbanColumn<T> getColumn() {
        return column;
    }

    void setDropTarget(boolean dropTarget) {
        pseudoClassStateChanged(DROP_TARGET_PSEUDO_CLASS, dropTarget);
    }

    void setColumnDragging(boolean dragging) {
        pseudoClassStateChanged(DRAGGING_PSEUDO_CLASS, dragging);
    }

    /**
     * Whether {@code child} is this column's header node — the only slot a column
     * reorder drag may be armed from.
     *
     * @param child a direct child of this box
     * @return {@code true} if it is the header
     */
    boolean isHeaderNode(Node child) {
        return child != null && child == headerNode;
    }

    void setModelListener(Runnable modelListener) {
        this.modelListener = modelListener;
    }

    void setBoardRelayout(Runnable boardRelayout) {
        this.boardRelayout = boardRelayout;
    }

    /**
     * This column's hide progress: 0 fully shown, 1 fully hidden. The board skin lerps
     * the column width from its full width to zero by it.
     *
     * @return the hide progress in {@code [0, 1]}
     */
    double getHideProgress() {
        return hideProgress.get();
    }

    // Drives hideProgress toward the model's visible state, animated when animation is
    // enabled (each frame relays out the board), else stepped.
    private void animateVisibility() {
        double target = column.isVisible() ? 0.0 : 1.0;
        if (hideAnimation != null) {
            hideAnimation.stop();
            hideAnimation = null;
        }
        if (!animationEnabled()) {
            hideProgress.set(target);
            return;
        }
        hideAnimation = new Timeline(new KeyFrame(control.getAnimationDuration(),
                new KeyValue(hideProgress, target, interpolatorOrDefault())));
        hideAnimation.setOnFinished(e -> hideAnimation = null);
        hideAnimation.play();
    }

    private boolean animationEnabled() {
        Duration duration = control.getAnimationDuration();
        return control.isAnimated() && getScene() != null
                && duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = control.getAnimationInterpolator();
        return value == null ? Interpolator.EASE_BOTH : value;
    }

    // ==================== Slot rebuilds ====================

    void rebuildHeader() {
        if (headerNode != null) {
            getChildren().remove(headerNode);
        }
        defaultTitleLabel = null;
        defaultCountLabel = null;
        Callback<RXKanbanColumn<T>, Node> factory = control.getColumnHeaderFactory();
        headerNode = factory != null ? factory.call(column) : createDefaultHeader();
        if (headerNode != null) {
            if (!headerNode.getStyleClass().contains("header")) {
                headerNode.getStyleClass().add("header");
            }
            headerNode.setManaged(false);
            getChildren().add(headerNode);
        }
        refreshFromModel();
    }

    void rebuildFooter() {
        if (footerNode != null) {
            getChildren().remove(footerNode);
            footerNode = null;
        }
        Callback<RXKanbanColumn<T>, Node> factory = control.getColumnFooterFactory();
        if (factory != null) {
            footerNode = factory.call(column);
            if (footerNode != null) {
                if (!footerNode.getStyleClass().contains("footer")) {
                    footerNode.getStyleClass().add("footer");
                }
                footerNode.setManaged(false);
                getChildren().add(footerNode);
            }
        }
    }

    void rebuildPlaceholder() {
        if (placeholderNode != null) {
            getChildren().remove(placeholderNode);
            placeholderNode = null;
        }
        Callback<RXKanbanColumn<T>, Node> factory = control.getEmptyColumnPlaceholderFactory();
        if (factory != null) {
            placeholderNode = factory.call(column);
            if (placeholderNode != null) {
                if (!placeholderNode.getStyleClass().contains("placeholder")) {
                    placeholderNode.getStyleClass().add("placeholder");
                }
                placeholderNode.setManaged(false);
                // Above the viewport so it covers the empty card area.
                getChildren().add(placeholderNode);
            }
        }
        refreshFromModel();
    }

    void rebuildCells() {
        viewport.recreateCells();
    }

    private Node createDefaultHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        defaultTitleLabel = new Label();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        defaultCountLabel = new Label();
        defaultCountLabel.getStyleClass().add("wip-indicator");
        header.getChildren().addAll(defaultTitleLabel, spacer, defaultCountLabel);
        return header;
    }

    // ==================== Model sync ====================

    private void refreshFromModel() {
        boolean empty = column.getCardCount() == 0;
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, empty);
        int limit = column.getWipLimit();
        pseudoClassStateChanged(OVER_LIMIT_PSEUDO_CLASS, limit > 0 && column.getCardCount() > limit);

        if (defaultTitleLabel != null) {
            String title = column.getTitle();
            defaultTitleLabel.setText(title == null ? "" : title);
        }
        viewport.updateAccessibleLabel(column.getTitle());
        if (defaultCountLabel != null) {
            int count = column.getCardCount();
            defaultCountLabel.setText(limit > 0 ? count + "/" + limit : Integer.toString(count));
        }
        // layoutChildren owns placeholder visibility (it also factors in hiding); a
        // card-count change re-runs it.
        requestLayout();
        if (modelListener != null) {
            modelListener.run();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        Insets in = getInsets();
        double left = in.getLeft();
        double top = in.getTop();
        double innerW = Math.max(0.0, w - in.getLeft() - in.getRight());
        double innerH = Math.max(0.0, h - in.getTop() - in.getBottom());

        // As the column hides its card area fades out and stops taking hits (the whole
        // column is set invisible by the skin once fully hidden).
        double progress = hideProgress.get();
        boolean cardAreaVisible = progress < 0.999;
        double cardAreaOpacity = Math.max(0.0, 1.0 - progress);
        viewport.setVisible(cardAreaVisible);
        viewport.setOpacity(cardAreaOpacity);
        if (footerNode != null) {
            footerNode.setVisible(cardAreaVisible);
            footerNode.setOpacity(cardAreaOpacity);
        }

        double headerH = headerNode != null ? snapSizeY(headerNode.prefHeight(innerW)) : 0.0;
        double footerH = footerNode != null && cardAreaVisible ? snapSizeY(footerNode.prefHeight(innerW)) : 0.0;

        if (headerNode != null) {
            headerNode.resizeRelocate(snapPositionX(left), snapPositionY(top), innerW, headerH);
        }

        double cardsX = left + CARD_AREA_HGAP;
        double cardsW = Math.max(0.0, innerW - 2.0 * CARD_AREA_HGAP);
        double cardsTop = top + headerH + CARD_AREA_VGAP;
        double cardsH = Math.max(0.0, innerH - headerH - footerH - CARD_AREA_VGAP);
        viewport.resizeRelocate(snapPositionX(cardsX), snapPositionY(cardsTop), cardsW, cardsH);
        if (placeholderNode != null) {
            boolean placeholderVisible = cardAreaVisible && column.getCardCount() == 0;
            placeholderNode.setVisible(placeholderVisible);
            placeholderNode.setOpacity(cardAreaOpacity);
            placeholderNode.resizeRelocate(snapPositionX(cardsX), snapPositionY(cardsTop), cardsW, cardsH);
        }
        if (footerNode != null) {
            footerNode.resizeRelocate(snapPositionX(left), snapPositionY(top + innerH - footerH), innerW, footerH);
        }
    }

    // ==================== Dispose ====================

    void dispose() {
        if (hideAnimation != null) {
            hideAnimation.stop();
            hideAnimation = null;
        }
        column.cardCountProperty().removeListener(columnListener);
        column.titleProperty().removeListener(columnListener);
        column.wipLimitProperty().removeListener(columnListener);
        column.visibleProperty().removeListener(visibilityListener);
        viewport.dispose();
    }
}
