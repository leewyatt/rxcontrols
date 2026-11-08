package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.event.RXTimelineItemEvent;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
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
        disposer.registerListener(control.connectorGapProperty(), metricsAction);
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
        // The view does not scroll, so it never reports less than its items need: a
        // horizontal timeline keeps its full length; a vertical one may narrow until a
        // label would have to cut a word (the height then grows, see computeMinHeight).
        if (orientationOrDefault() == Orientation.HORIZONTAL) {
            return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
        }
        Region active = activeRegion();
        if (active == null) {
            return leftInset + rightInset;
        }
        return leftInset + active.minWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Items never overlap: the minimum height is the preferred height at the given
        // width (a narrower vertical timeline wraps its text taller).
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Sized to its items like a Label: a parent such as StackPane centers it
        // instead of stretching empty space around the items.
        return getSkinnable().prefWidth(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
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
        double connectorGap = RXMath.sanitizeNonNegative(getSkinnable().getConnectorGap());
        for (ItemNode node : itemNodes) {
            node.applyMetrics(dotSize, lineWidth, itemSpacing, axisSpacing, connectorGap);
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
        if (node == null || node.isDisabled()) {
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

    // ==================== Word-keeping label ====================

    // A wrapping label whose minimum width keeps its longest word whole, so a narrowed
    // vertical timeline wraps text onto more lines instead of truncating it. Without
    // wrapping (horizontal timelines) it keeps the Label minimum.
    private static final class WordKeepingLabel extends Label {

        private static final Text MEASURE = new Text();

        @Override
        protected double computeMinWidth(double height) {
            double min = super.computeMinWidth(height);
            String text = getText();
            if (!isWrapText() || text == null || text.isBlank()) {
                return min;
            }
            double longestWord = 0.0;
            for (String word : text.trim().split("\\s+")) {
                longestWord = Math.max(longestWord, textWidth(word));
            }
            // The preferred width is the whole text on one line plus padding; swap the
            // line for the longest word to keep that padding.
            double padding = super.computePrefWidth(height) - textWidth(text);
            return Math.max(min, snapSizeX(padding + longestWord));
        }

        private double textWidth(String value) {
            MEASURE.setFont(getFont());
            MEASURE.setText(value);
            return MEASURE.getLayoutBounds().getWidth();
        }
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
        private final Label title = new WordKeepingLabel();
        private final Label description = new WordKeepingLabel();
        private final Label timestamp = new WordKeepingLabel();
        // Set by applyPosition: whether the row is centered and which halves flank the axis.
        private boolean centered;
        private Region leadingHalf = content;
        private Region trailingHalf = oppositeHolder;

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
            // Positional style-class hook in the JavaFX chart idiom (data0/series0/...: index
            // appended with no separator), keyed by MODEL index so it is stable under reverse
            // — `.item0` always styles getItems().get(0). Authors reach sub-parts via descendant
            // selectors (.item0 > .axis > .dot, etc.).
            getStyleClass().add("item" + modelIndex);
            // A container role (not LIST_ITEM, which expects the virtualized row protocol):
            // the item stays reachable as an ordinary child and carries composed text.
            setAccessibleRole(AccessibleRole.PARENT);
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

            // A disabled item mirrors onto the node's disabled state: it yields the
            // :disabled pseudo-class, dims via CSS, and drops mouse input (the click
            // handler also guards on isDisabled). Screen readers read the composed text.
            itemDisposer.registerBinding(disableProperty(), item.disableProperty());
            itemDisposer.registerBinding(accessibleTextProperty(), Bindings.createStringBinding(
                    this::composeAccessibleText, item.contentProperty(),
                    item.titleProperty(), item.descriptionProperty(), item.timestampTextProperty()));

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

        // Joins the non-empty text lines into one accessible string read by screen
        // readers. Returns null (not "") when there is no text so a custom-content /
        // opposite-only item falls back to its descendants' own accessibility instead
        // of being pinned to an empty accessible name.
        private String composeAccessibleText() {
            // A custom content node replaces the title/description/timestamp labels, so
            // defer to its own descendants' accessibility instead of announcing the
            // now-hidden text fields.
            if (item.getContent() != null) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            appendAccessiblePart(text, item.getTitle());
            appendAccessiblePart(text, item.getDescription());
            appendAccessiblePart(text, item.getTimestampText());
            return text.length() == 0 ? null : text.toString();
        }

        private static void appendAccessiblePart(StringBuilder text, String part) {
            if (part == null || part.isEmpty()) {
                return;
            }
            if (text.length() > 0) {
                // Separate fields with a sentence break, but don't double up when the
                // previous field already ends with its own punctuation.
                char last = text.charAt(text.length() - 1);
                text.append(".;:!?,".indexOf(last) >= 0 ? " " : ". ");
            }
            text.append(part);
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
            // A vertical timeline wraps the description by width; a horizontal one
            // lets items take their natural width.
            title.setWrapText(vertical);
            description.setWrapText(vertical);
            timestamp.setWrapText(vertical);
        }

        private void applyMetrics(double dotSize, double lineWidth, double itemSpacing, double axisSpacing,
                                  double connectorGap) {
            boolean vertical = orientation == Orientation.VERTICAL;
            dot.setPrefSize(dotSize, dotSize);
            // Pin the axis column's cross-size (min AND pref) to the dot so the connector's
            // gap margins never inflate the row — they only shorten the rendered line. The
            // StackPane's own min/pref would otherwise include those margins (so a huge gap
            // would grow the item). fillCrossAxis still stretches the axis to the content-
            // driven row size, within which the connector fills (clamping to zero if huge).
            // The axis column's width across the row (dot size) is also its minimum, so a
            // squeezed row never collapses the dot.
            if (vertical) {
                connector.setPrefWidth(lineWidth);
                connector.setPrefHeight(USE_COMPUTED_SIZE);
                axis.setMinHeight(dotSize);
                axis.setPrefHeight(dotSize);
                axis.setMinWidth(USE_PREF_SIZE);
                axis.setPrefWidth(USE_COMPUTED_SIZE);
            } else {
                connector.setPrefHeight(lineWidth);
                connector.setPrefWidth(USE_COMPUTED_SIZE);
                axis.setMinWidth(dotSize);
                axis.setPrefWidth(dotSize);
                axis.setMinHeight(USE_PREF_SIZE);
                axis.setPrefHeight(USE_COMPUTED_SIZE);
            }
            configureConnector(lineWidth, dotSize, connectorGap);
            setSpacing(axisSpacing);
            // itemSpacing is the gap between items, carried on the trailing edge of
            // BOTH non-axis columns (content and the opposite holder); the last item
            // carries none. Both must carry it because either can be the wider column
            // that drives the item pitch — if only content did, a wider opposite node
            // would set the pitch with no trailing gap and adjacent opposite blocks
            // would touch. The axis column stays gap-free so connectors stay continuous.
            double gap = last ? 0.0 : itemSpacing;
            Insets gapPadding = vertical
                    ? new Insets(0.0, 0.0, gap, 0.0)
                    : new Insets(0.0, gap, 0.0, 0.0);
            content.setPadding(gapPadding);
            oppositeHolder.setPadding(gapPadding);
        }

        // The connector lies in the gap between this item's dot and the next item's
        // dot, abutting their outer edges instead of crossing them: each item draws the
        // segment after its own dot — from the dot's trailing edge (a full diameter from
        // the leading edge of the axis) to the next dot's leading edge (the row's far
        // edge). The last item (and a lone single item) draws nothing. Because the line
        // never enters a dot, a hollow (ring) dot shows no stub through its transparent
        // center; for a solid dot this is pixel-identical to a center-to-center line
        // (the dot covered the crossing part anyway).
        //
        // connectorGap insets the line a further `connectorGap` at each end, so it stops
        // short of both dots. It only trims the rendered line (the dot and the row size
        // are unaffected); if 2*gap exceeds the segment, layoutInArea clamps the line to
        // zero length rather than negative.
        private void configureConnector(double lineWidth, double dotSize, double connectorGap) {
            if (last) {
                connector.setVisible(false);
                return;
            }
            connector.setVisible(true);
            if (orientation == Orientation.VERTICAL) {
                StackPane.setAlignment(connector, Pos.TOP_CENTER);
                StackPane.setMargin(connector, new Insets(dotSize + connectorGap, 0.0, connectorGap, 0.0));
                connector.setMaxSize(lineWidth, Double.MAX_VALUE);
            } else {
                StackPane.setAlignment(connector, Pos.CENTER_LEFT);
                StackPane.setMargin(connector, new Insets(0.0, connectorGap, 0.0, dotSize + connectorGap));
                connector.setMaxSize(Double.MAX_VALUE, lineWidth);
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
                // Only override the ring color inline; the :hollow rule owns the ring
                // geometry (transparent fill, border width, radius) and the inline
                // (highest precedence) replaces just its border color.
                dot.setStyle("-fx-border-color: " + toCssColor(color) + ";");
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

            this.centered = centered;
            leadingHalf = contentBeforeAxis ? content : oppositeHolder;
            trailingHalf = contentBeforeAxis ? oppositeHolder : content;
            if (centered) {
                getChildren().setAll(leadingHalf, axis, trailingHalf);
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

        // ==================== Centered layout ====================

        // The centered form lays out its own row: two equal halves around the axis,
        // each as large as the larger half needs, so the axis lands on the same line
        // in every row. The box's grow distribution cannot do this without zeroing the
        // halves' preferred sizes, which would hide them from the row's own size.
        // "Main" is the row direction: width in a vertical timeline, height in a
        // horizontal one.

        @Override
        protected double computePrefWidth(double height) {
            if (!centered) {
                return super.computePrefWidth(height);
            }
            return isVertical() ? centeredMainSize(height, false) : centeredCrossSize(height, false);
        }

        @Override
        protected double computePrefHeight(double width) {
            if (!centered) {
                return super.computePrefHeight(width);
            }
            return isVertical() ? centeredCrossSize(width, false) : centeredMainSize(width, false);
        }

        @Override
        protected double computeMinWidth(double height) {
            if (!centered) {
                return super.computeMinWidth(height);
            }
            return isVertical() ? centeredMainSize(height, true) : centeredCrossSize(height, true);
        }

        @Override
        protected double computeMinHeight(double width) {
            if (!centered) {
                return super.computeMinHeight(width);
            }
            return isVertical() ? centeredCrossSize(width, true) : centeredMainSize(width, true);
        }

        @Override
        protected void layoutChildren() {
            if (!centered) {
                super.layoutChildren();
                return;
            }
            boolean vertical = isVertical();
            double left = snappedLeftInset();
            double top = snappedTopInset();
            double contentWidth = Math.max(0.0, getWidth() - left - snappedRightInset());
            double contentHeight = Math.max(0.0, getHeight() - top - snappedBottomInset());
            double axisMain = mainPref(axis);
            double space = mainSpacing();
            double half = halfMain(vertical ? getWidth() : getHeight());
            double trailingStart = half + space + axisMain + space;
            // The halves take exactly their slot, even when a parent squeezes the row below
            // its minimum, so an oversized half overflows on its own side through its
            // alignment instead of growing over the axis.
            if (vertical) {
                leadingHalf.resizeRelocate(snapPositionX(left), snapPositionY(top),
                        snapSizeX(half), snapSizeY(contentHeight));
                layoutInArea(axis, left + half + space, top, axisMain, contentHeight,
                        -1, Insets.EMPTY, true, true, HPos.LEFT, VPos.TOP);
                trailingHalf.resizeRelocate(snapPositionX(left + trailingStart), snapPositionY(top),
                        snapSizeX(half), snapSizeY(contentHeight));
            } else {
                leadingHalf.resizeRelocate(snapPositionX(left), snapPositionY(top),
                        snapSizeX(contentWidth), snapSizeY(half));
                layoutInArea(axis, left, top + half + space, contentWidth, axisMain,
                        -1, Insets.EMPTY, true, true, HPos.LEFT, VPos.TOP);
                trailingHalf.resizeRelocate(snapPositionX(left), snapPositionY(top + trailingStart),
                        snapSizeX(contentWidth), snapSizeY(half));
            }
        }

        // Insets + axis + both gaps + two halves sized to the larger half (its minimum
        // for the minimum). Every slot fills the row's cross length, so a given one
        // (-1: unknown) is passed on for children whose main size depends on it.
        private double centeredMainSize(double cross, boolean minimum) {
            double inner = cross < 0 ? -1 : Math.max(0.0, cross - crossInsets());
            double axisMain = minimum ? mainMin(axis, inner) : mainPref(axis, inner);
            // The opposite side holds short metadata such as a time, kept whole.
            double halves = minimum
                    ? 2.0 * Math.max(mainMin(content, inner), mainPref(oppositeHolder, inner))
                    : 2.0 * Math.max(mainPref(content, inner), mainPref(oppositeHolder, inner));
            double size = mainInsets() + axisMain + 2.0 * mainSpacing() + halves;
            return isVertical() ? snapSizeX(size) : snapSizeY(size);
        }

        // Cross size for a given row length (-1: the preferred one), measuring each
        // half at the length it is actually given, so wrapped text sizes correctly.
        private double centeredCrossSize(double main, boolean minimum) {
            double rowMain = main < 0 ? centeredMainSize(-1, false) : main;
            double half = halfMain(rowMain);
            double axisMain = mainPref(axis);
            double cross = Math.max(crossSize(axis, axisMain, minimum),
                    Math.max(crossSize(content, half, minimum), crossSize(oppositeHolder, half, minimum)));
            return crossInsets() + cross;
        }

        private double halfMain(double rowMain) {
            double free = rowMain - mainInsets() - mainPref(axis) - 2.0 * mainSpacing();
            return Math.max(0.0, free / 2.0);
        }

        private boolean isVertical() {
            return orientation == Orientation.VERTICAL;
        }

        private double mainInsets() {
            return isVertical()
                    ? snappedLeftInset() + snappedRightInset()
                    : snappedTopInset() + snappedBottomInset();
        }

        private double crossInsets() {
            return isVertical()
                    ? snappedTopInset() + snappedBottomInset()
                    : snappedLeftInset() + snappedRightInset();
        }

        private double mainSpacing() {
            return isVertical() ? snapSpaceX(getSpacing()) : snapSpaceY(getSpacing());
        }

        private double mainPref(Region node) {
            return mainPref(node, -1);
        }

        private double mainPref(Region node, double cross) {
            return isVertical() ? node.prefWidth(cross) : node.prefHeight(cross);
        }

        private double mainMin(Region node, double cross) {
            return isVertical() ? node.minWidth(cross) : node.minHeight(cross);
        }

        private double crossSize(Region node, double main, boolean minimum) {
            if (isVertical()) {
                return minimum ? node.minHeight(main) : node.prefHeight(main);
            }
            return minimum ? node.minWidth(main) : node.prefWidth(main);
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
