package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.enums.TimelineItemType;
import io.github.leewyatt.rxcontrols.event.RXTimelineItemEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skin for {@link RXTimelineView}.
 *
 * <p>Items are rendered into a {@link VBox} (spacing 0) of {@code ItemNode}
 * {@link HBox}es. Each item's axis column is a {@link StackPane} holding a
 * full-height connector {@link Region} with the dot {@link StackPane} pinned to
 * its top, so the connector runs continuously through the inter-item gap
 * (carried by the content column's bottom padding) and behind the dot. The skin
 * owns control-level long-lived resources; each {@code ItemNode} owns a private
 * {@link SkinDisposer} for its per-item bindings, disposed and rebuilt on every
 * list / reverse change.
 */
public class RXTimelineViewSkin extends RXSkinBase<RXTimelineView> {

    // ==================== Nodes ====================

    private final VBox itemsBox = new VBox();
    private final StackPane placeholderRegion = new StackPane();
    private final List<ItemNode> itemNodes = new ArrayList<>();

    // ==================== Listeners ====================

    private final Runnable rebuildAction = this::rebuildItemNodes;
    private final Runnable metricsAction = this::applyMetricsAndRequestLayout;
    private final ChangeListener<Node> placeholderListener =
            (observable, oldValue, newValue) -> onPlaceholderChanged(oldValue, newValue);
    private final EventHandler<MouseEvent> clickHandler = this::onItemsClicked;

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXTimelineViewSkin(RXTimelineView control) {
        super(control);

        itemsBox.getStyleClass().add("items");
        itemsBox.setFillWidth(true);
        itemsBox.setSpacing(0.0);
        placeholderRegion.getStyleClass().add("placeholder");
        getChildren().setAll(itemsBox, placeholderRegion);
        setPlaceholderNode(control.getPlaceholder());

        disposer.registerListener(control.getItems(), rebuildAction);
        disposer.registerListener(control.reverseProperty(), rebuildAction);
        disposer.registerListener(control.dotSizeProperty(), metricsAction);
        disposer.registerListener(control.lineWidthProperty(), metricsAction);
        disposer.registerListener(control.itemSpacingProperty(), metricsAction);
        disposer.registerListener(control.placeholderProperty(), placeholderListener);
        disposer.registerEventHandler(itemsBox, MouseEvent.MOUSE_CLICKED, clickHandler);

        rebuildItemNodes();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        if (isEmpty()) {
            layoutInArea(placeholderRegion, contentX, contentY, contentWidth, contentHeight,
                    -1, HPos.CENTER, VPos.CENTER);
        } else {
            layoutInArea(itemsBox, contentX, contentY, contentWidth, contentHeight,
                    -1, HPos.CENTER, VPos.TOP);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        Region active = activeRegion();
        double inner = (active == null) ? 0.0 : active.prefWidth(-1);
        return leftInset + inner + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        Region active = activeRegion();
        if (active == null) {
            return topInset + bottomInset;
        }
        double innerWidth = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + active.prefHeight(innerWidth) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Rebuild ====================

    private void rebuildItemNodes() {
        for (ItemNode node : itemNodes) {
            node.dispose();
        }
        itemNodes.clear();

        List<RXTimelineItem> items = getSkinnable().getItems();
        boolean reverse = getSkinnable().isReverse();
        int count = items.size();
        for (int i = 0; i < count; i++) {
            int modelIndex = reverse ? count - 1 - i : i;
            itemNodes.add(new ItemNode(items.get(modelIndex), modelIndex));
        }
        itemsBox.getChildren().setAll(itemNodes);
        int lastDisplayIndex = itemNodes.size() - 1;
        for (int i = 0; i < itemNodes.size(); i++) {
            itemNodes.get(i).setLast(i == lastDisplayIndex);
        }
        applyMetrics();
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    // ==================== Metrics ====================

    private void applyMetrics() {
        double dotSize = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());
        double lineWidth = RXMath.sanitizeNonNegative(getSkinnable().getLineWidth());
        double itemSpacing = RXMath.sanitizeNonNegative(getSkinnable().getItemSpacing());
        for (ItemNode node : itemNodes) {
            node.applyMetrics(dotSize, lineWidth, itemSpacing);
        }
    }

    private void applyMetricsAndRequestLayout() {
        applyMetrics();
        getSkinnable().requestLayout();
    }

    // ==================== Placeholder ====================

    private void onPlaceholderChanged(Node oldValue, Node newValue) {
        setPlaceholderNode(newValue);
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    private void setPlaceholderNode(Node placeholder) {
        if (placeholder == null) {
            placeholderRegion.getChildren().clear();
        } else {
            placeholderRegion.getChildren().setAll(placeholder);
        }
    }

    private void updatePlaceholderState() {
        boolean empty = isEmpty();
        itemsBox.setVisible(!empty);
        itemsBox.setManaged(!empty);
        placeholderRegion.setVisible(empty);
        placeholderRegion.setManaged(empty);
    }

    private boolean isEmpty() {
        return getSkinnable().getItems().isEmpty();
    }

    private Region activeRegion() {
        return isEmpty() ? placeholderRegion : itemsBox;
    }

    // ==================== Events ====================

    private void onItemsClicked(MouseEvent event) {
        ItemNode node = findItemNode(event.getTarget());
        if (node == null) {
            return;
        }
        getSkinnable().fireEvent(new RXTimelineItemEvent(
                getSkinnable(), RXTimelineItemEvent.ITEM_CLICKED, node.getItem(), node.getModelIndex()));
    }

    private ItemNode findItemNode(Object target) {
        if (!(target instanceof Node node)) {
            return null;
        }
        while (node != null && node != itemsBox) {
            if (node instanceof ItemNode itemNode) {
                return itemNode;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        for (ItemNode node : itemNodes) {
            node.dispose();
        }
        itemNodes.clear();
        itemsBox.getChildren().clear();
        placeholderRegion.getChildren().clear();
    }

    private static String toCssColor(Color color) {
        int r = (int) Math.round(color.getRed() * 255.0);
        int g = (int) Math.round(color.getGreen() * 255.0);
        int b = (int) Math.round(color.getBlue() * 255.0);
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.4f)", r, g, b, color.getOpacity());
    }

    // ==================== Item Node ====================

    private static final class ItemNode extends HBox {

        private static final PseudoClass LAST_PSEUDO_CLASS = PseudoClass.getPseudoClass("last");
        private static final PseudoClass PRIMARY_PSEUDO_CLASS = PseudoClass.getPseudoClass("primary");
        private static final PseudoClass SUCCESS_PSEUDO_CLASS = PseudoClass.getPseudoClass("success");
        private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");
        private static final PseudoClass DANGER_PSEUDO_CLASS = PseudoClass.getPseudoClass("danger");
        private static final PseudoClass INFO_PSEUDO_CLASS = PseudoClass.getPseudoClass("info");

        private final SkinDisposer itemDisposer = new SkinDisposer();
        private final RXTimelineItem item;
        private final int modelIndex;
        private boolean last;

        private final StackPane axis = new StackPane();
        private final Region connector = new Region();
        private final StackPane dot = new StackPane();
        private final VBox content = new VBox();
        private final Label title = new Label();
        private final Label description = new Label();
        private final Label timestamp = new Label();

        private final ChangeListener<Node> contentListener =
                (observable, oldValue, newValue) -> updateContent(newValue);
        private final ChangeListener<Node> dotGraphicListener =
                (observable, oldValue, newValue) -> updateDotGraphic(newValue);

        private ItemNode(RXTimelineItem item, int modelIndex) {
            this.item = item;
            this.modelIndex = modelIndex;

            getStyleClass().add("item");
            axis.getStyleClass().add("axis");
            connector.getStyleClass().add("connector");
            dot.getStyleClass().add("dot");
            content.getStyleClass().add("content");
            title.getStyleClass().add("title");
            description.getStyleClass().add("description");
            timestamp.getStyleClass().add("timestamp");

            dot.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
            StackPane.setAlignment(dot, Pos.TOP_CENTER);
            // Dot is added after the connector so it paints over the connector's top.
            axis.getChildren().setAll(connector, dot);
            axis.setMinWidth(USE_PREF_SIZE);

            description.setWrapText(true);
            content.getChildren().setAll(title, description, timestamp);

            getChildren().setAll(axis, content);
            setHgrow(content, Priority.ALWAYS);

            itemDisposer.registerBinding(title.textProperty(), item.titleProperty());
            itemDisposer.registerBinding(description.textProperty(), item.descriptionProperty());
            itemDisposer.registerBinding(timestamp.textProperty(), item.timestampTextProperty());
            bindCollapseWhenEmpty(title);
            bindCollapseWhenEmpty(description);
            bindCollapseWhenEmpty(timestamp);

            itemDisposer.registerListener(item.contentProperty(), contentListener);
            itemDisposer.registerListener(item.dotGraphicProperty(), dotGraphicListener);
            itemDisposer.registerListener(item.typeProperty(), () -> applyTypePseudo(item.getType()));
            itemDisposer.registerListener(item.dotFillProperty(), () -> applyDotFill(item.getDotFill()));

            updateContent(item.getContent());
            updateDotGraphic(item.getDotGraphic());
            applyTypePseudo(item.getType());
            applyDotFill(item.getDotFill());
        }

        private void bindCollapseWhenEmpty(Label label) {
            BooleanBinding nonEmpty = label.textProperty().isEmpty().not();
            itemDisposer.registerBinding(label.managedProperty(), nonEmpty);
            itemDisposer.registerBinding(label.visibleProperty(), nonEmpty);
        }

        private void applyMetrics(double dotSize, double lineWidth, double itemSpacing) {
            dot.setPrefSize(dotSize, dotSize);
            connector.setPrefWidth(lineWidth);
            connector.setMaxSize(lineWidth, Double.MAX_VALUE);
            // itemSpacing is the gap between items, so the last row carries no trailing padding.
            double bottom = last ? 0.0 : itemSpacing;
            content.setPadding(new Insets(0.0, 0.0, bottom, 0.0));
        }

        private void updateContent(Node custom) {
            if (custom == null) {
                content.getChildren().setAll(title, description, timestamp);
            } else {
                content.getChildren().setAll(custom);
            }
        }

        private void updateDotGraphic(Node graphic) {
            if (graphic == null) {
                dot.getChildren().clear();
            } else {
                dot.getChildren().setAll(graphic);
            }
        }

        private void applyTypePseudo(TimelineItemType type) {
            pseudoClassStateChanged(PRIMARY_PSEUDO_CLASS, type == TimelineItemType.PRIMARY);
            pseudoClassStateChanged(SUCCESS_PSEUDO_CLASS, type == TimelineItemType.SUCCESS);
            pseudoClassStateChanged(WARNING_PSEUDO_CLASS, type == TimelineItemType.WARNING);
            pseudoClassStateChanged(DANGER_PSEUDO_CLASS, type == TimelineItemType.DANGER);
            pseudoClassStateChanged(INFO_PSEUDO_CLASS, type == TimelineItemType.INFO);
        }

        private void applyDotFill(Color color) {
            if (color == null) {
                dot.setStyle("");
            } else {
                dot.setStyle("-fx-background-color: " + toCssColor(color) + ";");
            }
        }

        private void setLast(boolean last) {
            this.last = last;
            pseudoClassStateChanged(LAST_PSEUDO_CLASS, last);
        }

        private RXTimelineItem getItem() {
            return item;
        }

        private int getModelIndex() {
            return modelIndex;
        }

        private void dispose() {
            itemDisposer.dispose();
            // Release the item-owned nodes hosted here so they can be re-parented
            // by the next ItemNode and are not retained by this discarded node.
            dot.getChildren().clear();
            content.getChildren().clear();
        }
    }
}
