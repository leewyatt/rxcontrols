package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.event.RXTimelineItemEvent;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skin for {@link RXTimelineView}.
 *
 * <p>Items are rendered into an {@link RXBox} (spacing 0) of {@code ItemNode}
 * {@link RXBox}es. The container's orientation follows the control's
 * orientation; each item's box runs on the perpendicular axis so the axis
 * column / row sits beside (vertical) or above/below (horizontal) the content.
 * The axis is a {@link StackPane} holding a connector {@link Region} that spans
 * from the first dot's center to the last dot's center, with the dot pinned to
 * the leading edge. The skin owns control-level long-lived resources; each
 * {@code ItemNode} owns a private {@link SkinDisposer} for its per-item
 * bindings, disposed and rebuilt on every list / reverse change.
 */
public class RXTimelineViewSkin extends RXSkinBase<RXTimelineView> {

    // ==================== Nodes ====================

    private final RXBox itemsBox = new RXBox();
    private final StackPane placeholderRegion = new StackPane();
    private final List<ItemNode> itemNodes = new ArrayList<>();

    // ==================== Listeners ====================

    private final Runnable rebuildAction = this::rebuildItemNodes;
    private final Runnable metricsAction = this::applyMetricsAndRequestLayout;
    private final Runnable positionAction = this::applyPositionAndRequestLayout;
    private final Runnable orientationAction = this::applyOrientationAndRequestLayout;
    private final Runnable oppositeAction = this::applyShowOppositeAndRequestLayout;
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
        itemsBox.setFillCrossAxis(true);
        itemsBox.setSpacing(0.0);
        placeholderRegion.getStyleClass().add("placeholder");
        getChildren().setAll(itemsBox, placeholderRegion);
        setPlaceholderNode(control.getPlaceholder());

        disposer.registerListener(control.getItems(), rebuildAction);
        disposer.registerListener(control.reverseProperty(), rebuildAction);
        disposer.registerListener(control.dotSizeProperty(), metricsAction);
        disposer.registerListener(control.lineWidthProperty(), metricsAction);
        disposer.registerListener(control.itemSpacingProperty(), metricsAction);
        disposer.registerListener(control.axisSpacingProperty(), metricsAction);
        disposer.registerListener(control.positionProperty(), positionAction);
        disposer.registerListener(control.orientationProperty(), orientationAction);
        disposer.registerListener(control.showOppositeContentProperty(), oppositeAction);
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
        } else if (orientationOrDefault() == Orientation.HORIZONTAL) {
            // Non-centered LEFT/RIGHT: size the row to its preferred height (not the
            // full area) so the axis and content stay together. A centered form
            // (ALTERNATE or showOppositeContent) splits the height for the centered
            // axis, so it must fill the height; fillCrossAxis then makes every item
            // that tall, keeping the centered axis line aligned across rows.
            boolean centered = isCentered();
            layoutInArea(itemsBox, contentX, contentY, contentWidth, contentHeight,
                    -1, Insets.EMPTY, false, centered, HPos.LEFT, VPos.TOP);
        } else {
            layoutInArea(itemsBox, contentX, contentY, contentWidth, contentHeight,
                    -1, Insets.EMPTY, true, true, HPos.LEFT, VPos.TOP);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        Region active = activeRegion();
        if (active == null) {
            return leftInset + rightInset;
        }
        double innerHeight = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        double width = (active.getContentBias() == Orientation.VERTICAL)
                ? active.prefWidth(innerHeight) : active.prefWidth(-1);
        return leftInset + width + rightInset;
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
        double height = (active.getContentBias() == Orientation.HORIZONTAL)
                ? active.prefHeight(innerWidth) : active.prefHeight(-1);
        return topInset + height + bottomInset;
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
            itemNodes.add(new ItemNode(getSkinnable(), items.get(modelIndex), modelIndex));
        }
        itemsBox.getChildren().setAll(itemNodes);
        for (int i = 0; i < count; i++) {
            itemNodes.get(i).setRole(i, count);
        }
        applyOrientation();
        applyMetrics();
        applyPosition();
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    // ==================== Orientation ====================

    private void applyOrientation() {
        Orientation orientation = orientationOrDefault();
        itemsBox.setOrientation(orientation);
        for (ItemNode node : itemNodes) {
            node.applyOrientation(orientation);
        }
    }

    private void applyOrientationAndRequestLayout() {
        applyOrientation();
        // Orientation flips the connector geometry and content padding edge.
        applyMetrics();
        applyPosition();
        getSkinnable().requestLayout();
    }

    private Orientation orientationOrDefault() {
        Orientation orientation = getSkinnable().getOrientation();
        return orientation == null ? RXTimelineView.DEFAULT_ORIENTATION : orientation;
    }

    // ==================== Metrics ====================

    private void applyMetrics() {
        double dotSize = RXMath.sanitizeNonNegative(getSkinnable().getDotSize());
        double lineWidth = RXMath.sanitizeNonNegative(getSkinnable().getLineWidth());
        double itemSpacing = RXMath.sanitizeNonNegative(getSkinnable().getItemSpacing());
        double axisSpacing = RXMath.sanitizeNonNegative(getSkinnable().getAxisSpacing());
        for (ItemNode node : itemNodes) {
            node.applyMetrics(dotSize, lineWidth, itemSpacing, axisSpacing);
        }
    }

    private void applyMetricsAndRequestLayout() {
        applyMetrics();
        getSkinnable().requestLayout();
    }

    // ==================== Position ====================

    private void applyPosition() {
        RXTimelineView.Position position = positionOrDefault();
        for (ItemNode node : itemNodes) {
            node.applyPosition(position);
        }
    }

    private void applyPositionAndRequestLayout() {
        applyPosition();
        getSkinnable().requestLayout();
    }

    private RXTimelineView.Position positionOrDefault() {
        RXTimelineView.Position position = getSkinnable().getPosition();
        return position == null ? RXTimelineView.DEFAULT_POSITION : position;
    }

    // The axis is centered between two equal halves (rather than pinned to one edge)
    // whenever ALTERNATE or the view-wide showOppositeContent switch is in effect.
    // Both layoutChildren and ItemNode.applyPosition must agree on this, or a centered
    // row can be sized as if it were edge-pinned and collapse its content.
    private boolean isCentered() {
        return positionOrDefault() == RXTimelineView.Position.ALTERNATE
                || getSkinnable().isShowOppositeContent();
    }

    // ==================== Opposite Content ====================

    private void applyShowOpposite() {
        for (ItemNode node : itemNodes) {
            node.refreshOpposite();
        }
    }

    private void applyShowOppositeAndRequestLayout() {
        applyShowOpposite();
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

    private static final class ItemNode extends RXBox {

        private static final PseudoClass LAST_PSEUDO_CLASS = PseudoClass.getPseudoClass("last");
        private static final PseudoClass PRIMARY_PSEUDO_CLASS = PseudoClass.getPseudoClass("primary");
        private static final PseudoClass SUCCESS_PSEUDO_CLASS = PseudoClass.getPseudoClass("success");
        private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");
        private static final PseudoClass DANGER_PSEUDO_CLASS = PseudoClass.getPseudoClass("danger");
        private static final PseudoClass INFO_PSEUDO_CLASS = PseudoClass.getPseudoClass("info");
        private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
        private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");
        private static final PseudoClass HOLLOW_PSEUDO_CLASS = PseudoClass.getPseudoClass("hollow");

        private final SkinDisposer itemDisposer = new SkinDisposer();
        private final RXTimelineView control;
        private final RXTimelineItem item;
        private final int modelIndex;
        private int displayIndex;
        private boolean first;
        private boolean last;
        private Orientation orientation = Orientation.VERTICAL;
        private RXTimelineView.Position position = RXTimelineView.DEFAULT_POSITION;

        private final StackPane axis = new StackPane();
        private final Region connector = new Region();
        private final StackPane dot = new StackPane();
        private final VBox content = new VBox();
        // Hosts the optional opposite-side content; also reserves the opposite half
        // (empty) whenever the axis is centered (ALTERNATE, or showOppositeContent),
        // so the axis stays aligned across rows even when this item has none.
        private final StackPane oppositeHolder = new StackPane();
        private final Label title = new Label();
        private final Label description = new Label();
        private final Label timestamp = new Label();

        private final ChangeListener<Node> contentListener =
                (observable, oldValue, newValue) -> updateContent(newValue);
        private final ChangeListener<Node> dotGraphicListener =
                (observable, oldValue, newValue) -> updateDotGraphic(newValue);
        // Opposite content may add/remove the third slot, so re-run the position layout.
        private final ChangeListener<Node> oppositeListener =
                (observable, oldValue, newValue) -> refreshOpposite();

        private ItemNode(RXTimelineView control, RXTimelineItem item, int modelIndex) {
            this.control = control;
            this.item = item;
            this.modelIndex = modelIndex;

            getStyleClass().add("item");
            axis.getStyleClass().add("axis");
            connector.getStyleClass().add("connector");
            dot.getStyleClass().add("dot");
            content.getStyleClass().add("content");
            oppositeHolder.getStyleClass().add("opposite");
            title.getStyleClass().add("title");
            description.getStyleClass().add("description");
            timestamp.getStyleClass().add("timestamp");

            dot.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
            // Dot is added after the connector so it paints over the connector's leading end.
            axis.getChildren().setAll(connector, dot);

            content.getChildren().setAll(title, description, timestamp);

            getChildren().setAll(axis, content);
            // Content takes the remaining cross-axis space after the fixed axis column.
            setGrow(content, Priority.ALWAYS);

            itemDisposer.registerBinding(title.textProperty(), item.titleProperty());
            itemDisposer.registerBinding(description.textProperty(), item.descriptionProperty());
            itemDisposer.registerBinding(timestamp.textProperty(), item.timestampTextProperty());
            bindCollapseWhenEmpty(title);
            bindCollapseWhenEmpty(description);
            bindCollapseWhenEmpty(timestamp);

            itemDisposer.registerListener(item.contentProperty(), contentListener);
            itemDisposer.registerListener(item.dotGraphicProperty(), dotGraphicListener);
            itemDisposer.registerListener(item.oppositeContentProperty(), oppositeListener);
            itemDisposer.registerListener(item.typeProperty(), () -> applyTypePseudo(item.getType()));
            itemDisposer.registerListener(item.dotFillProperty(), this::applyDotAppearance);
            itemDisposer.registerListener(item.hollowProperty(), this::applyHollow);
            itemDisposer.registerListener(item.lineFillProperty(), this::applyLineFill);

            updateContent(item.getContent());
            updateDotGraphic(item.getDotGraphic());
            updateOpposite(item.getOppositeContent());
            applyTypePseudo(item.getType());
            applyHollow();
            applyLineFill();
        }

        // Re-hosts the opposite node and re-runs the position layout; called when the
        // item's oppositeContent changes or the view's showOppositeContent toggles.
        private void refreshOpposite() {
            updateOpposite(item.getOppositeContent());
            applyPosition(position);
        }

        private void updateOpposite(Node oppositeContent) {
            // Opposite content is only hosted when the view-wide switch is on; otherwise
            // the holder stays empty (and unused), so a per-item node never shows alone.
            if (oppositeContent != null && control.isShowOppositeContent()) {
                oppositeHolder.getChildren().setAll(oppositeContent);
            } else {
                oppositeHolder.getChildren().clear();
            }
        }

        private void bindCollapseWhenEmpty(Label label) {
            BooleanBinding nonEmpty = label.textProperty().isEmpty().not();
            itemDisposer.registerBinding(label.managedProperty(), nonEmpty);
            itemDisposer.registerBinding(label.visibleProperty(), nonEmpty);
        }

        // Sets the box / axis / dot / wrap orientation. Called before applyMetrics
        // and applyPosition, which both read the stored orientation.
        private void applyOrientation(Orientation orientation) {
            this.orientation = orientation;
            boolean vertical = orientation == Orientation.VERTICAL;
            // The item box runs perpendicular to the timeline so the axis sits
            // beside the content (vertical) or above / below it (horizontal).
            setOrientation(vertical ? Orientation.HORIZONTAL : Orientation.VERTICAL);
            // Dot pinned to the axis leading edge: top (vertical) or left (horizontal).
            StackPane.setAlignment(dot, vertical ? Pos.TOP_CENTER : Pos.CENTER_LEFT);
            // Lock the axis cross-size to its preferred so it never collapses.
            if (vertical) {
                axis.setMinWidth(USE_PREF_SIZE);
                axis.setMinHeight(USE_COMPUTED_SIZE);
            } else {
                axis.setMinHeight(USE_PREF_SIZE);
                axis.setMinWidth(USE_COMPUTED_SIZE);
            }
            // A vertical timeline wraps the description by width; a horizontal one
            // lets items take their natural width.
            title.setWrapText(vertical);
            description.setWrapText(vertical);
            timestamp.setWrapText(vertical);
        }

        private void applyMetrics(double dotSize, double lineWidth, double itemSpacing, double axisSpacing) {
            boolean vertical = orientation == Orientation.VERTICAL;
            dot.setPrefSize(dotSize, dotSize);
            if (vertical) {
                connector.setPrefWidth(lineWidth);
                connector.setPrefHeight(USE_COMPUTED_SIZE);
            } else {
                connector.setPrefHeight(lineWidth);
                connector.setPrefWidth(USE_COMPUTED_SIZE);
            }
            configureConnector(lineWidth, dotSize / 2.0);
            setSpacing(axisSpacing);
            // itemSpacing is the gap between items, carried on the content's
            // main-axis trailing edge; the last item carries none.
            double gap = last ? 0.0 : itemSpacing;
            content.setPadding(vertical
                    ? new Insets(0.0, 0.0, gap, 0.0)
                    : new Insets(0.0, gap, 0.0, 0.0));
        }

        // The connector is one continuous line from the first dot's center to the
        // last dot's center, drawn behind the dots. The first item starts its
        // segment at the dot center (nothing before it), the last item stops at the
        // dot center (nothing after it), and middle items span the full extent.
        private void configureConnector(double lineWidth, double dotCenter) {
            if (first && last) {
                connector.setVisible(false);
                return;
            }
            connector.setVisible(true);
            if (orientation == Orientation.VERTICAL) {
                if (first) {
                    StackPane.setAlignment(connector, Pos.CENTER);
                    StackPane.setMargin(connector, new Insets(dotCenter, 0.0, 0.0, 0.0));
                    connector.setMaxSize(lineWidth, Double.MAX_VALUE);
                } else if (last) {
                    StackPane.setAlignment(connector, Pos.TOP_CENTER);
                    StackPane.setMargin(connector, Insets.EMPTY);
                    connector.setMaxSize(lineWidth, dotCenter);
                } else {
                    StackPane.setAlignment(connector, Pos.CENTER);
                    StackPane.setMargin(connector, Insets.EMPTY);
                    connector.setMaxSize(lineWidth, Double.MAX_VALUE);
                }
            } else {
                if (first) {
                    StackPane.setAlignment(connector, Pos.CENTER);
                    StackPane.setMargin(connector, new Insets(0.0, 0.0, 0.0, dotCenter));
                    connector.setMaxSize(Double.MAX_VALUE, lineWidth);
                } else if (last) {
                    StackPane.setAlignment(connector, Pos.CENTER_LEFT);
                    StackPane.setMargin(connector, Insets.EMPTY);
                    connector.setMaxSize(dotCenter, lineWidth);
                } else {
                    StackPane.setAlignment(connector, Pos.CENTER);
                    StackPane.setMargin(connector, Insets.EMPTY);
                    connector.setMaxSize(Double.MAX_VALUE, lineWidth);
                }
            }
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

        private void applyTypePseudo(RXTimelineItem.Type type) {
            pseudoClassStateChanged(PRIMARY_PSEUDO_CLASS, type == RXTimelineItem.Type.PRIMARY);
            pseudoClassStateChanged(SUCCESS_PSEUDO_CLASS, type == RXTimelineItem.Type.SUCCESS);
            pseudoClassStateChanged(WARNING_PSEUDO_CLASS, type == RXTimelineItem.Type.WARNING);
            pseudoClassStateChanged(DANGER_PSEUDO_CLASS, type == RXTimelineItem.Type.DANGER);
            pseudoClassStateChanged(INFO_PSEUDO_CLASS, type == RXTimelineItem.Type.INFO);
        }

        private void applyHollow() {
            pseudoClassStateChanged(HOLLOW_PSEUDO_CLASS, item.isHollow());
            applyDotAppearance();
        }

        // Per-item dot color (dotFill) is written inline; null clears the inline so
        // the :type / -rx-dot-fill cascade (and the :hollow ring rule) take over.
        // When hollow, a non-null dotFill colors the ring (border) instead of the fill.
        private void applyDotAppearance() {
            Color color = item.getDotFill();
            if (color == null) {
                dot.setStyle("");
            } else if (item.isHollow()) {
                String web = toCssColor(color);
                dot.setStyle("-fx-background-color: transparent; -fx-border-color: " + web
                        + "; -fx-border-width: 2; -fx-border-radius: 50%;");
            } else {
                dot.setStyle("-fx-background-color: " + toCssColor(color) + ";");
            }
        }

        private void applyLineFill() {
            Color color = item.getLineFill();
            if (color == null) {
                connector.setStyle("");
            } else {
                connector.setStyle("-fx-background-color: " + toCssColor(color) + ";");
            }
        }

        private void setRole(int displayIndex, int count) {
            this.displayIndex = displayIndex;
            this.first = displayIndex == 0;
            this.last = displayIndex == count - 1;
            pseudoClassStateChanged(LAST_PSEUDO_CLASS, last);
        }

        // Places content relative to the axis. LEFT / RIGHT pin the axis to one
        // cross-side with content filling the rest; ALTERNATE centers the axis
        // between equal-width halves and puts content on the leading or trailing
        // half by display-order parity. The view-wide showOppositeContent switch
        // also forces the centered three-slot form (content one half, opposite the
        // other) on every row, so the axis stays aligned. Content text hugs the axis.
        private void applyPosition(RXTimelineView.Position position) {
            this.position = position;
            boolean vertical = orientation == Orientation.VERTICAL;
            boolean alternate = position == RXTimelineView.Position.ALTERNATE;
            // Centering is a view-wide decision so the axis stays aligned on every row:
            // ALTERNATE always centers; showOppositeContent centers every row uniformly.
            boolean centered = alternate || control.isShowOppositeContent();
            boolean leftSide;
            boolean contentBeforeAxis;
            if (alternate) {
                leftSide = displayIndex % 2 == 0;
                contentBeforeAxis = leftSide;
            } else {
                leftSide = position != RXTimelineView.Position.RIGHT;
                contentBeforeAxis = !leftSide;
            }

            // Reset overrides; the centered form forces equal halves by zeroing BOTH
            // the main-axis min and preferred size on content and the opposite holder,
            // so grow splits evenly even when natural min would resist shrinking.
            content.setPrefSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);
            content.setMinSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);
            oppositeHolder.setPrefSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);
            oppositeHolder.setMinSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);
            if (centered) {
                if (contentBeforeAxis) {
                    getChildren().setAll(content, axis, oppositeHolder);
                } else {
                    getChildren().setAll(oppositeHolder, axis, content);
                }
                setMainPref(content, 0.0);
                setMainMin(content, 0.0);
                setGrow(content, Priority.ALWAYS);
                setMainPref(oppositeHolder, 0.0);
                setMainMin(oppositeHolder, 0.0);
                setGrow(oppositeHolder, Priority.ALWAYS);
            } else if (contentBeforeAxis) {
                getChildren().setAll(content, axis);
                setGrow(content, Priority.ALWAYS);
            } else {
                getChildren().setAll(axis, content);
                setGrow(content, Priority.ALWAYS);
            }

            // Content hugs the axis; the opposite holder hugs it from the other side.
            if (vertical) {
                content.setAlignment(contentBeforeAxis ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
                oppositeHolder.setAlignment(contentBeforeAxis ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
                Pos labelAlignment = contentBeforeAxis ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
                TextAlignment textAlignment = contentBeforeAxis ? TextAlignment.RIGHT : TextAlignment.LEFT;
                for (Label label : new Label[]{title, description, timestamp}) {
                    label.setAlignment(labelAlignment);
                    label.setTextAlignment(textAlignment);
                }
            } else {
                content.setAlignment(contentBeforeAxis ? Pos.BOTTOM_LEFT : Pos.TOP_LEFT);
                oppositeHolder.setAlignment(contentBeforeAxis ? Pos.TOP_LEFT : Pos.BOTTOM_LEFT);
                for (Label label : new Label[]{title, description, timestamp}) {
                    label.setAlignment(Pos.CENTER_LEFT);
                    label.setTextAlignment(TextAlignment.LEFT);
                }
            }

            pseudoClassStateChanged(LEFT_PSEUDO_CLASS, leftSide);
            pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !leftSide);
        }

        private void setMainPref(Region node, double value) {
            if (orientation == Orientation.VERTICAL) {
                node.setPrefWidth(value);
            } else {
                node.setPrefHeight(value);
            }
        }

        private void setMainMin(Region node, double value) {
            if (orientation == Orientation.VERTICAL) {
                node.setMinWidth(value);
            } else {
                node.setMinHeight(value);
            }
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
            oppositeHolder.getChildren().clear();
        }
    }
}
