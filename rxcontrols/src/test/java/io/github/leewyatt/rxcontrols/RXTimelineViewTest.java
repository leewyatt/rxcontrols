package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTimelineItem.Type;
import io.github.leewyatt.rxcontrols.RXTimelineView.Position;
import io.github.leewyatt.rxcontrols.event.TimelineItemEvent;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTimelineView control API, CSS metadata, pseudo-class wiring,
 * item-click event contract, placeholder state, and rebuild behavior.
 */
public class RXTimelineViewTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass LEFT = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT = PseudoClass.getPseudoClass("right");
    private static final PseudoClass HOLLOW = PseudoClass.getPseudoClass("hollow");
    private static final PseudoClass DISABLED = PseudoClass.getPseudoClass("disabled");

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    @Test
    public void defaultsAreCorrect() {
        RXTimelineView view = new RXTimelineView();

        assertTrue(view.getStyleClass().contains("rx-timeline-view"));
        assertTrue(view.getItems().isEmpty());
        assertFalse(view.isReverse());
        assertNull(view.getPlaceholder());
        assertNull(view.getOnItemClicked());
        assertEquals(Position.LEFT, view.getPosition());
        assertEquals(Orientation.VERTICAL, view.getOrientation());
        assertEquals(Orientation.HORIZONTAL, view.getContentBias());
        assertEquals(AccessibleRole.PARENT, view.getAccessibleRole());
    }

    @Test
    public void itemDefaultsAreCorrect() {
        RXTimelineItem item = new RXTimelineItem();
        assertEquals("", item.getTitle());
        assertEquals("", item.getDescription());
        assertEquals("", item.getTimestampText());
        assertNull(item.getContent());
        assertNull(item.getDotGraphic());
        assertNull(item.getType());
        assertNull(item.getDotFill());
        assertNull(item.getLineFill());
        assertFalse(item.isHollow());
        assertFalse(item.isDisable());

        RXTimelineItem titled = new RXTimelineItem("hello");
        assertEquals("hello", titled.getTitle());

        RXTimelineItem dual = new RXTimelineItem("hello", "09:00");
        assertEquals("hello", dual.getTitle());
        assertEquals("09:00", dual.getTimestampText());
    }

    @Test
    public void varargsConstructorSkipsNulls() {
        RXTimelineItem a = new RXTimelineItem("a");
        RXTimelineItem b = new RXTimelineItem("b");
        RXTimelineView view = new RXTimelineView(a, null, b);
        assertEquals(List.of(a, b), view.getItems());
    }

    @Test
    public void cssMetadataExposesOnlySizes() {
        RXTimelineView view = new RXTimelineView();
        List<String> properties = view.getControlCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .toList();

        assertTrue(properties.contains("-rx-dot-size"));
        assertTrue(properties.contains("-rx-line-width"));
        assertTrue(properties.contains("-rx-item-spacing"));
        assertTrue(properties.contains("-rx-axis-spacing"));
        assertTrue(properties.contains("-rx-connector-gap"));
        // Colors are pure CSS looked-up colors, not styleable properties.
        assertFalse(properties.contains("-rx-dot-fill"));
        assertFalse(properties.contains("-rx-line-fill"));

        assertEquals(view.getControlCssMetaData().size(),
                RXTimelineView.getClassCssMetaData().size());
    }

    @Test
    public void sizeMetadataIsSettableUntilBound() {
        RXTimelineView view = new RXTimelineView();
        CssMetaData<? extends Styleable, ?> dotSize = findMeta(view, "-rx-dot-size");

        assertTrue(isSettable(dotSize, view));
        view.dotSizeProperty().bind(view.lineWidthProperty());
        assertFalse(isSettable(dotSize, view));
    }

    @Test
    public void reverseFlips() {
        RXTimelineView view = new RXTimelineView();
        assertFalse(view.isReverse());
        view.setReverse(true);
        assertTrue(view.isReverse());
    }

    @Test
    public void emptyPseudoClassTracksList() {
        RXTimelineView view = new RXTimelineView();
        assertTrue(view.getPseudoClassStates().contains(EMPTY));

        view.getItems().add(new RXTimelineItem("a"));
        assertFalse(view.getPseudoClassStates().contains(EMPTY));

        view.getItems().clear();
        assertTrue(view.getPseudoClassStates().contains(EMPTY));
    }

    @Test
    public void itemMutationDoesNotThrow() {
        RXTimelineView view = new RXTimelineView();
        showInScene(view);
        view.getItems().add(new RXTimelineItem("a"));
        view.getItems().add(new RXTimelineItem("b"));
        view.getItems().remove(0);
        view.getItems().setAll(new RXTimelineItem("c"), new RXTimelineItem("d"));
        view.setReverse(true);
        view.getItems().clear();
        assertTrue(view.getItems().isEmpty());
    }

    @Test
    public void lastAndTypePseudoClassesApply() {
        RXTimelineItem first = new RXTimelineItem("first");
        RXTimelineItem success = new RXTimelineItem("ok");
        success.setType(Type.SUCCESS);
        RXTimelineItem last = new RXTimelineItem("last");
        RXTimelineView view = new RXTimelineView(first, success, last);
        showInScene(view);

        List<Node> nodes = itemNodes(view);
        assertEquals(3, nodes.size());
        assertFalse(nodes.get(0).getPseudoClassStates().contains(LAST));
        assertTrue(nodes.get(2).getPseudoClassStates().contains(LAST));
        assertTrue(nodes.get(1).getPseudoClassStates().contains(SUCCESS));
    }

    @Test
    public void reverseRecomputesLastByDisplayOrder() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        view.setReverse(true);
        showInScene(view);

        List<Node> nodes = itemNodes(view);
        // Display order is reversed, so the last displayed node is model item "a".
        assertTrue(nodes.get(2).getPseudoClassStates().contains(LAST));
    }

    @Test
    public void rebuildDoesNotAccumulateNodes() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        showInScene(view);

        for (int i = 0; i < 5; i++) {
            view.getItems().setAll(
                    new RXTimelineItem("x"), new RXTimelineItem("y"), new RXTimelineItem("z"));
            view.setReverse(i % 2 == 0);
            view.getParent().applyCss();
            view.getParent().layout();
        }
        assertEquals(view.getItems().size(), itemNodes(view).size());
    }

    @Test
    public void rebuildDisposesOldItemNodeBindings() {
        RXTimelineItem survivor = new RXTimelineItem("original");
        RXTimelineView view = new RXTimelineView(survivor);
        showInScene(view);

        Node oldItem = itemNodes(view).get(0);
        Label oldTitle = (Label) oldItem.lookup(".title");
        assertEquals("original", oldTitle.getText());

        // A full rebuild discards the old ItemNode and disposes its itemDisposer.
        view.getItems().setAll(survivor);
        view.getParent().applyCss();
        view.getParent().layout();

        // The discarded node leaves the items container, and its per-item bindings
        // were unbound: mutating the surviving model item must not reach the stale
        // node's label. A leaked binding would still track the item (R6 / P0-1).
        assertFalse(itemNodes(view).contains(oldItem));
        survivor.setTitle("changed");
        assertEquals("original", oldTitle.getText());
    }

    @Test
    public void connectorAbutsDotEdgesWithoutCrossing() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        showInScene(view);
        double dotSize = view.getDotSize();

        List<Node> nodes = itemNodes(view);
        Region firstConnector = (Region) nodes.get(0).lookup(".connector");
        Region midConnector = (Region) nodes.get(1).lookup(".connector");
        Region lastConnector = (Region) nodes.get(2).lookup(".connector");

        // Each non-last item draws the gap from its own dot's trailing edge (a full dot
        // diameter inset, so the line never enters the dot) to the next dot's edge.
        assertTrue(firstConnector.isVisible());
        assertEquals(dotSize, StackPane.getMargin(firstConnector).getTop(), EPSILON);
        assertEquals(Double.MAX_VALUE, firstConnector.getMaxHeight(), EPSILON);
        assertEquals(dotSize, StackPane.getMargin(midConnector).getTop(), EPSILON);
        assertEquals(Double.MAX_VALUE, midConnector.getMaxHeight(), EPSILON);
        // The last item draws no segment; the previous item's segment reaches its edge.
        assertFalse(lastConnector.isVisible());
    }

    @Test
    public void axisSpacingDrivesRowGap() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        showInScene(view);

        RXBox firstRow = (RXBox) itemNodes(view).get(0);
        assertEquals(view.getAxisSpacing(), firstRow.getSpacing(), EPSILON);

        view.setAxisSpacing(30.0);
        view.getParent().applyCss();
        view.getParent().layout();
        assertEquals(30.0, firstRow.getSpacing(), EPSILON);
    }

    @Test
    public void positionRightReordersColumnsAndPseudoClass() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"));
        showInScene(view);

        RXBox row = (RXBox) itemNodes(view).get(0);
        // Left (default): axis column first, :left pseudo set.
        assertTrue(row.getChildrenUnmodifiable().get(0).getStyleClass().contains("axis"));
        assertTrue(row.getPseudoClassStates().contains(LEFT));
        assertFalse(row.getPseudoClassStates().contains(RIGHT));

        view.setPosition(Position.RIGHT);
        view.getParent().applyCss();
        view.getParent().layout();

        // Right: axis column moves to the end, :right pseudo set.
        int lastIndex = row.getChildrenUnmodifiable().size() - 1;
        assertTrue(row.getChildrenUnmodifiable().get(lastIndex).getStyleClass().contains("axis"));
        assertTrue(row.getPseudoClassStates().contains(RIGHT));
        assertFalse(row.getPseudoClassStates().contains(LEFT));
    }

    @Test
    public void alternatePositionAlternatesContentSideByParity() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        view.setPosition(Position.ALTERNATE);
        showInScene(view);

        List<Node> nodes = itemNodes(view);
        RXBox even = (RXBox) nodes.get(0);
        RXBox odd = (RXBox) nodes.get(1);

        // Alternate rows are three-slot (content | axis | spacer); the axis is centered.
        assertEquals(3, even.getChildrenUnmodifiable().size());
        assertEquals(3, odd.getChildrenUnmodifiable().size());

        // Even item: content on the leading side (before the axis), :left.
        assertTrue(even.getChildrenUnmodifiable().get(0).getStyleClass().contains("content"));
        assertTrue(even.getChildrenUnmodifiable().get(1).getStyleClass().contains("axis"));
        assertTrue(even.getPseudoClassStates().contains(LEFT));
        assertFalse(even.getPseudoClassStates().contains(RIGHT));

        // Odd item: content on the trailing side (after the axis), :right.
        assertTrue(odd.getChildrenUnmodifiable().get(2).getStyleClass().contains("content"));
        assertTrue(odd.getChildrenUnmodifiable().get(1).getStyleClass().contains("axis"));
        assertTrue(odd.getPseudoClassStates().contains(RIGHT));
        assertFalse(odd.getPseudoClassStates().contains(LEFT));
    }

    @Test
    public void alternateItemHeightMatchesContent() {
        RXTimelineItem withDesc = new RXTimelineItem("Placed", "06-12 09:24");
        withDesc.setDescription("Order created.");
        RXTimelineView view = new RXTimelineView(withDesc, new RXTimelineItem("Paid", "06-12 09:31"));
        view.setPosition(Position.ALTERNATE);
        StackPane root = new StackPane(view);
        new Scene(root, 360, 600);
        root.applyCss();
        root.layout();

        Region first = (Region) itemNodes(view).get(0);
        Region content = (Region) first.lookup(".content");
        // Regression for the RXBox mismeasurement: the old code measured a grow +
        // content-bias child's height at its (zero) pref width, inflating the item to
        // a multi-line height (~280px) while content laid out at the half width (~60px).
        // The item height must match the content measured at its laid-out width.
        assertEquals(content.prefHeight(content.getWidth()), first.getHeight(), 2.0,
                "alternate item height should match its content at the laid-out width");
    }

    @Test
    public void showOppositeContentKeepsAxisAlignedAcrossMixedRows() {
        // Three rows, only some carrying an opposite node: the axis must land on the
        // same x on every row (the whole reason centering is a view-wide decision).
        RXTimelineItem withA = new RXTimelineItem("Alpha", "09:00");
        withA.setOppositeContent(new Label("09:00"));
        RXTimelineItem none = new RXTimelineItem("Bravo");
        RXTimelineItem withC = new RXTimelineItem("Charlie", "09:10");
        withC.setOppositeContent(new Label("09:10"));
        RXTimelineView view = new RXTimelineView(withA, none, withC);
        view.setShowOppositeContent(true);
        showInScene(view);

        List<Node> nodes = itemNodes(view);
        double baseX = dotCenterX((Region) nodes.get(0).lookup(".dot"));
        for (Node node : nodes) {
            // Even the row with no opposite node is centered three-slot.
            assertEquals(3, ((Parent) node).getChildrenUnmodifiable().size());
            double x = dotCenterX((Region) node.lookup(".dot"));
            assertEquals(baseX, x, 0.5, "dot center x must be identical on every row");
        }
    }

    @Test
    public void showOppositeContentHorizontalDoesNotCollapseContent() {
        // Regression for the layoutChildren/applyPosition divergence: a horizontal
        // centered row (showOppositeContent, non-ALTERNATE) must fill the height so the
        // centered axis has room — otherwise content collapses to height 0.
        Region tall = new Region();
        tall.setMinSize(80.0, 120.0);
        tall.setPrefSize(80.0, 120.0);
        RXTimelineItem withContent = new RXTimelineItem("a");
        withContent.setContent(tall);
        RXTimelineItem other = new RXTimelineItem("b", "09:05");
        other.setOppositeContent(new Label("09:05"));
        RXTimelineView view = new RXTimelineView(withContent, other);
        view.setOrientation(Orientation.HORIZONTAL);
        view.setShowOppositeContent(true);
        StackPane root = new StackPane(view);
        new Scene(root, 800.0, 400.0);
        root.applyCss();
        root.layout();

        assertTrue(tall.getHeight() >= 100.0,
                "horizontal centered content must not collapse, was " + tall.getHeight());

        // The dot center sits on the same y on every row (axis aligned along the timeline).
        double baseY = dotCenterY((Region) itemNodes(view).get(0).lookup(".dot"));
        double otherY = dotCenterY((Region) itemNodes(view).get(1).lookup(".dot"));
        assertEquals(baseY, otherY, 0.5, "dot center y must align across horizontal rows");
    }

    @Test
    public void alternateWithOppositeContentHostsOnOppositeSide() {
        Label stamp0 = new Label("t0");
        Label stamp1 = new Label("t1");
        RXTimelineItem even = new RXTimelineItem("a");
        even.setOppositeContent(stamp0);
        RXTimelineItem odd = new RXTimelineItem("b");
        odd.setOppositeContent(stamp1);
        RXTimelineView view = new RXTimelineView(even, odd);
        view.setPosition(Position.ALTERNATE);
        view.setShowOppositeContent(true);
        showInScene(view);

        RXBox evenRow = (RXBox) itemNodes(view).get(0);
        RXBox oddRow = (RXBox) itemNodes(view).get(1);
        // The opposite node lands on the half opposite the content, mirrored by parity.
        assertTrue(evenRow.getChildrenUnmodifiable().get(0).getStyleClass().contains("content"));
        assertTrue(evenRow.getChildrenUnmodifiable().get(2).getStyleClass().contains("opposite"));
        assertTrue(oddRow.getChildrenUnmodifiable().get(0).getStyleClass().contains("opposite"));
        assertTrue(oddRow.getChildrenUnmodifiable().get(2).getStyleClass().contains("content"));
        assertSame(stamp0, ((Parent) evenRow.lookup(".opposite")).getChildrenUnmodifiable().get(0));
        assertSame(stamp1, ((Parent) oddRow.lookup(".opposite")).getChildrenUnmodifiable().get(0));
    }

    @Test
    public void oppositeContentRuntimeChangeRehosts() {
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        view.setShowOppositeContent(true);
        showInScene(view);

        RXBox row = (RXBox) itemNodes(view).get(0);
        Parent holder = (Parent) row.lookup(".opposite");
        assertNotNull(holder);
        // showOppositeContent on but no node yet: the column is reserved but empty.
        assertTrue(holder.getChildrenUnmodifiable().isEmpty());

        Label stamp = new Label("09:00");
        item.setOppositeContent(stamp);
        view.getParent().applyCss();
        view.getParent().layout();
        // The oppositeListener -> refreshOpposite path hosts the node without a rebuild.
        assertSame(stamp, holder.getChildrenUnmodifiable().get(0));

        item.setOppositeContent(null);
        view.getParent().applyCss();
        view.getParent().layout();
        assertTrue(holder.getChildrenUnmodifiable().isEmpty());
        // The column is still reserved while showOppositeContent stays on.
        assertEquals(3, row.getChildrenUnmodifiable().size());
    }

    @Test
    public void oppositeContentSurvivesRebuildSingleOccupancy() {
        Label stamp = new Label("09:00");
        RXTimelineItem survivor = new RXTimelineItem("a");
        survivor.setOppositeContent(stamp);
        RXTimelineView view = new RXTimelineView(survivor);
        view.setShowOppositeContent(true);
        showInScene(view);

        Parent oldHolder = (Parent) itemNodes(view).get(0).lookup(".opposite");
        assertSame(stamp, oldHolder.getChildrenUnmodifiable().get(0));

        // A full rebuild discards the old ItemNode; its dispose() releases the opposite
        // node so the fresh ItemNode can re-parent it (single-occupancy preserved).
        view.getItems().setAll(survivor);
        view.getParent().applyCss();
        view.getParent().layout();

        Parent newHolder = (Parent) itemNodes(view).get(0).lookup(".opposite");
        assertSame(stamp, newHolder.getChildrenUnmodifiable().get(0));
        assertTrue(oldHolder.getChildrenUnmodifiable().isEmpty());
    }

    @Test
    public void nullPositionFallsBackToLeft() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"));
        view.setPosition(null);
        showInScene(view);

        RXBox row = (RXBox) itemNodes(view).get(0);
        assertTrue(row.getChildrenUnmodifiable().get(0).getStyleClass().contains("axis"));
        assertTrue(row.getPseudoClassStates().contains(LEFT));
    }

    @Test
    public void verticalOrientationArrangesBoxes() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        showInScene(view);

        assertEquals(Orientation.HORIZONTAL, view.getContentBias());
        RXBox items = (RXBox) view.lookup(".items");
        assertEquals(Orientation.VERTICAL, items.getOrientation());
        // Each item box runs perpendicular to the timeline (horizontal: axis | content).
        RXBox firstItem = (RXBox) itemNodes(view).get(0);
        assertEquals(Orientation.HORIZONTAL, firstItem.getOrientation());
    }

    @Test
    public void horizontalOrientationFlipsBoxesAndBias() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        view.setOrientation(Orientation.HORIZONTAL);
        showInScene(view);

        // Horizontal timelines do not wrap by width.
        assertNull(view.getContentBias());
        RXBox items = (RXBox) view.lookup(".items");
        assertEquals(Orientation.HORIZONTAL, items.getOrientation());
        RXBox firstItem = (RXBox) itemNodes(view).get(0);
        assertEquals(Orientation.VERTICAL, firstItem.getOrientation());
    }

    @Test
    public void nullOrientationFallsBackToVertical() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"));
        view.setOrientation(null);
        showInScene(view);

        RXBox items = (RXBox) view.lookup(".items");
        assertEquals(Orientation.VERTICAL, items.getOrientation());
        assertEquals(Orientation.HORIZONTAL, view.getContentBias());
    }

    @Test
    public void singleItemHasNoConnector() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("only"));
        showInScene(view);
        Region connector = (Region) itemNodes(view).get(0).lookup(".connector");
        assertFalse(connector.isVisible());
    }

    @Test
    public void dotFillInlineStyleAppliesAndClears() {
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Node dot = itemNodes(view).get(0).lookup(".dot");
        assertNotNull(dot);
        assertEquals("", dot.getStyle());

        item.setDotFill(Color.RED);
        assertTrue(dot.getStyle().contains("-fx-background-color"));

        item.setDotFill(null);
        assertEquals("", dot.getStyle());
    }

    @Test
    public void lineFillInlineStyleAppliesAndClears() {
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Region connector = (Region) itemNodes(view).get(0).lookup(".connector");
        assertNotNull(connector);
        assertEquals("", connector.getStyle());

        item.setLineFill(Color.RED);
        assertTrue(connector.getStyle().contains("-fx-background-color"));

        item.setLineFill(null);
        assertEquals("", connector.getStyle());
    }

    @Test
    public void hollowSetsPseudoClassAndRingStyle() {
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Node itemNode = itemNodes(view).get(0);
        Node dot = itemNode.lookup(".dot");
        assertFalse(itemNode.getPseudoClassStates().contains(HOLLOW));

        item.setHollow(true);
        assertTrue(itemNode.getPseudoClassStates().contains(HOLLOW));

        // A per-item dotFill on a hollow dot colors the ring (border) inline; the ring
        // geometry (transparent fill, width, radius) comes from the :hollow CSS rule.
        item.setDotFill(Color.RED);
        assertTrue(dot.getStyle().contains("-fx-border-color"));
        assertFalse(dot.getStyle().contains("-fx-background-color"));

        item.setHollow(false);
        assertFalse(itemNode.getPseudoClassStates().contains(HOLLOW));
        assertTrue(dot.getStyle().contains("-fx-background-color"));
        assertFalse(dot.getStyle().contains("-fx-border-color"));
    }

    @Test
    public void onItemClickedFiresWithModelIndex() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        showInScene(view);

        AtomicReference<TimelineItemEvent> received = new AtomicReference<>();
        view.setOnItemClicked(received::set);

        Node second = itemNodes(view).get(1);
        Event.fireEvent(second, syntheticClick(second));

        TimelineItemEvent event = received.get();
        assertNotNull(event);
        assertEquals(1, event.getIndex());
        assertSame(view.getItems().get(1), event.getItem());
        assertSame(view, event.getSource());
        assertSame(view, event.getTarget());
        assertSame(view, event.getTimelineView());
    }

    @Test
    public void onItemClickedCarriesModelIndexUnderReverse() {
        RXTimelineItem a = new RXTimelineItem("a");
        RXTimelineItem b = new RXTimelineItem("b");
        RXTimelineItem c = new RXTimelineItem("c");
        RXTimelineView view = new RXTimelineView(a, b, c);
        view.setReverse(true);
        showInScene(view);

        AtomicReference<TimelineItemEvent> received = new AtomicReference<>();
        view.setOnItemClicked(received::set);

        // Display position 0 is model item "c" (index 2) under reverse.
        Node firstDisplayed = itemNodes(view).get(0);
        Event.fireEvent(firstDisplayed, syntheticClick(firstDisplayed));

        TimelineItemEvent event = received.get();
        assertNotNull(event);
        assertEquals(2, event.getIndex());
        assertSame(c, event.getItem());
    }

    @Test
    public void placeholderVisibilityTracksEmptiness() {
        RXTimelineView view = new RXTimelineView();
        Label placeholder = new Label("empty");
        view.setPlaceholder(placeholder);
        showInScene(view);

        Node placeholderRegion = view.lookup(".placeholder");
        Node items = view.lookup(".items");
        assertNotNull(placeholderRegion);
        assertNotNull(items);
        assertTrue(placeholderRegion.isVisible());
        assertTrue(placeholderRegion.isManaged());
        assertFalse(items.isManaged());

        view.getItems().add(new RXTimelineItem("a"));
        view.getParent().applyCss();
        view.getParent().layout();
        assertFalse(placeholderRegion.isVisible());
        assertTrue(items.isManaged());
    }

    @Test
    public void itemNodesExposeAccessibleRoleAndComposedText() {
        RXTimelineItem shipped = new RXTimelineItem("Shipped", "09:00");
        shipped.setDescription("Left the depot.");
        RXTimelineView view = new RXTimelineView(shipped, new RXTimelineItem("Delivered"));
        showInScene(view);

        Node first = itemNodes(view).get(0);
        // A plain container role (not the virtualized LIST_ITEM); items stay reachable.
        assertEquals(AccessibleRole.PARENT, first.getAccessibleRole());
        // Non-empty title + description + timestamp joined into one readable line.
        assertEquals("Shipped. Left the depot. 09:00", first.getAccessibleText());

        // Recomposes reactively when the model text changes.
        shipped.setDescription("");
        assertEquals("Shipped. 09:00", first.getAccessibleText());
    }

    @Test
    public void accessibleTextIsNullWhenItemHasNoText() {
        // A custom-content / opposite-only item has no title/description/timestamp: the
        // composed text must be null (not ""), so AT falls back to the item's descendants.
        RXTimelineItem custom = new RXTimelineItem();
        custom.setContent(new Label("rich"));
        RXTimelineView view = new RXTimelineView(custom);
        showInScene(view);

        assertNull(itemNodes(view).get(0).getAccessibleText());
    }

    @Test
    public void disabledItemSetsPseudoClassAndSuppressesClick() {
        RXTimelineItem enabled = new RXTimelineItem("a");
        RXTimelineItem disabled = new RXTimelineItem("b");
        disabled.setDisable(true);
        RXTimelineView view = new RXTimelineView(enabled, disabled);
        showInScene(view);

        Node enabledNode = itemNodes(view).get(0);
        Node disabledNode = itemNodes(view).get(1);
        assertFalse(enabledNode.getPseudoClassStates().contains(DISABLED));
        assertTrue(disabledNode.getPseudoClassStates().contains(DISABLED));
        assertTrue(disabledNode.isDisabled());

        AtomicReference<TimelineItemEvent> received = new AtomicReference<>();
        view.setOnItemClicked(received::set);

        // A disabled item must not fire onItemClicked, even on a directly dispatched click.
        Event.fireEvent(disabledNode, syntheticClick(disabledNode));
        assertNull(received.get());

        Event.fireEvent(enabledNode, syntheticClick(enabledNode));
        assertNotNull(received.get());
        assertSame(enabled, received.get().getItem());
    }

    @Test
    public void itemDisableTogglesAtRuntime() {
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Node node = itemNodes(view).get(0);
        assertFalse(node.isDisabled());

        item.setDisable(true);
        assertTrue(node.isDisabled());
        assertTrue(node.getPseudoClassStates().contains(DISABLED));

        item.setDisable(false);
        assertFalse(node.isDisabled());
        assertFalse(node.getPseudoClassStates().contains(DISABLED));
    }

    @Test
    public void disabledViewCascadesDisabledOntoItems() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        showInScene(view);

        view.setDisable(true);
        for (Node node : itemNodes(view)) {
            // A disabled view cascades :disabled onto every item (its text dims via the
            // JavaFX default; the dot and connector keep their color).
            assertTrue(node.isDisabled());
            assertTrue(node.getPseudoClassStates().contains(DISABLED));
        }
    }

    @Test
    public void disabledItemKeepsDotAndConnectorVivid() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        view.getItems().get(0).setDisable(true);
        showInScene(view);

        // Decision: a disabled item dims only its text (the JavaFX default on Labels); the
        // dot and the connector keep full color, so the continuous timeline line shows no
        // faded patch. We intentionally add NO .item:disabled opacity rule (a uniform
        // opacity would fade a middle item's line segment — steppers avoid this only because
        // their connectors are discrete, whereas this line is continuous).
        Node node = itemNodes(view).get(0);
        assertEquals(1.0, node.lookup(".dot").getOpacity(), EPSILON);
        assertEquals(1.0, node.lookup(".connector").getOpacity(), EPSILON);
    }

    @Test
    public void itemSpacingSeparatesWiderOppositeColumn() {
        Label wideOpposite = new Label("2026-06-12 09:31:00");
        RXTimelineItem paid = new RXTimelineItem("P");        // narrow content
        paid.setOppositeContent(wideOpposite);                // wide opposite drives the pitch
        RXTimelineItem next = new RXTimelineItem("Next");
        next.setOppositeContent(new Label("t"));
        RXTimelineView view = new RXTimelineView(paid, next);
        view.setOrientation(Orientation.HORIZONTAL);
        view.setPosition(Position.ALTERNATE);
        view.setShowOppositeContent(true);
        view.setItemSpacing(28.0);
        StackPane root = new StackPane(view);
        new Scene(root, 900, 400);
        root.applyCss();
        root.layout();

        // The item pitch (item0 width, since items abut) must clear the wider opposite
        // column PLUS itemSpacing, so adjacent opposite blocks do not touch. Regression:
        // the gap used to live only on the content column, so a wider opposite drove the
        // pitch with zero trailing gap (item0 width == opposite width, no spacing).
        Region item0 = (Region) itemNodes(view).get(0);
        double oppositePref = wideOpposite.prefWidth(-1);
        assertEquals(oppositePref + 28.0, item0.getWidth(), 2.0,
                "item pitch should clear the wider opposite column plus itemSpacing");
    }

    @Test
    public void dotGraphicHostsClearsAndSurvivesRebuild() {
        Label graphic = new Label("✓");
        RXTimelineItem item = new RXTimelineItem("a");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Parent dot = (Parent) itemNodes(view).get(0).lookup(".dot");
        assertTrue(dot.getChildrenUnmodifiable().isEmpty());

        item.setDotGraphic(graphic);   // runtime set via the dotGraphic listener
        view.getParent().applyCss();
        view.getParent().layout();
        assertSame(graphic, dot.getChildrenUnmodifiable().get(0));

        // Survives a rebuild: re-hosted into the fresh dot, old node released.
        view.getItems().setAll(item);
        view.getParent().applyCss();
        view.getParent().layout();
        Parent newDot = (Parent) itemNodes(view).get(0).lookup(".dot");
        assertSame(graphic, newDot.getChildrenUnmodifiable().get(0));
        assertTrue(dot.getChildrenUnmodifiable().isEmpty());

        item.setDotGraphic(null);
        view.getParent().applyCss();
        view.getParent().layout();
        assertTrue(newDot.getChildrenUnmodifiable().isEmpty());
    }

    @Test
    public void customContentSurvivesRebuildSingleOccupancy() {
        Label rich = new Label("rich");
        RXTimelineItem item = new RXTimelineItem("a");
        item.setContent(rich);
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);

        Parent content = (Parent) itemNodes(view).get(0).lookup(".content");
        assertSame(content, rich.getParent());

        view.getItems().setAll(item);
        view.getParent().applyCss();
        view.getParent().layout();

        Parent newContent = (Parent) itemNodes(view).get(0).lookup(".content");
        assertSame(rich, newContent.getChildrenUnmodifiable().get(0));
        assertSame(newContent, rich.getParent());
    }

    @Test
    public void accessibleTextDefersToCustomContentEvenWithTitle() {
        RXTimelineItem item = new RXTimelineItem("Shipped", "09:00");
        RXTimelineView view = new RXTimelineView(item);
        showInScene(view);
        Node node = itemNodes(view).get(0);
        assertEquals("Shipped. 09:00", node.getAccessibleText());

        // Custom content replaces the text fields, so a11y defers to its descendants.
        item.setContent(new Label("rich"));
        assertNull(node.getAccessibleText());

        // Clearing it restores the composed text.
        item.setContent(null);
        assertEquals("Shipped. 09:00", node.getAccessibleText());
    }

    @Test
    public void rebuildReleasesDisableAndAccessibleTextBindings() {
        RXTimelineItem survivor = new RXTimelineItem("orig");
        RXTimelineView view = new RXTimelineView(survivor);
        showInScene(view);

        Node oldNode = itemNodes(view).get(0);
        assertEquals("orig", oldNode.getAccessibleText());

        view.getItems().setAll(survivor);   // rebuild discards oldNode + disposes its bindings
        view.getParent().applyCss();
        view.getParent().layout();
        assertFalse(itemNodes(view).contains(oldNode));

        // The discarded node's disable and accessibleText bindings were released:
        // mutating the surviving model item must not reach the stale node.
        survivor.setDisable(true);
        assertFalse(oldNode.isDisabled());
        survivor.setTitle("changed");
        assertEquals("orig", oldNode.getAccessibleText());
    }

    @Test
    public void alternateParityFollowsDisplayOrderUnderReverse() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        view.setPosition(Position.ALTERNATE);
        view.setReverse(true);
        showInScene(view);

        // Parity is keyed off DISPLAY order, so reverse does not scramble the sides:
        // display 0 leads (:left), 1 trails (:right), 2 leads (:left).
        List<Node> nodes = itemNodes(view);
        assertTrue(nodes.get(0).getPseudoClassStates().contains(LEFT));
        assertTrue(nodes.get(1).getPseudoClassStates().contains(RIGHT));
        assertTrue(nodes.get(2).getPseudoClassStates().contains(LEFT));
    }

    @Test
    public void connectorAbutsDotEdgesInHorizontalOrientation() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        view.setOrientation(Orientation.HORIZONTAL);
        showInScene(view);
        double dotSize = view.getDotSize();

        List<Node> nodes = itemNodes(view);
        Region first = (Region) nodes.get(0).lookup(".connector");
        Region mid = (Region) nodes.get(1).lookup(".connector");
        Region last = (Region) nodes.get(2).lookup(".connector");

        // Horizontal mirror: the gap runs along width, inset a full dot from the leading edge.
        assertTrue(first.isVisible());
        assertEquals(dotSize, StackPane.getMargin(first).getLeft(), EPSILON);
        assertEquals(Double.MAX_VALUE, first.getMaxWidth(), EPSILON);
        assertEquals(dotSize, StackPane.getMargin(mid).getLeft(), EPSILON);
        assertEquals(Double.MAX_VALUE, mid.getMaxWidth(), EPSILON);
        assertFalse(last.isVisible());
    }

    @Test
    public void showOppositeContentWithRightPosition() {
        RXTimelineItem item = new RXTimelineItem("a", "09:00");
        item.setOppositeContent(new Label("09:00"));
        RXTimelineView view = new RXTimelineView(item);
        view.setPosition(Position.RIGHT);
        view.setShowOppositeContent(true);
        showInScene(view);

        // RIGHT + centered mirrors LEFT: content leads, axis centers, opposite trails; :right.
        RXBox row = (RXBox) itemNodes(view).get(0);
        assertEquals(3, row.getChildrenUnmodifiable().size());
        assertTrue(row.getChildrenUnmodifiable().get(0).getStyleClass().contains("content"));
        assertTrue(row.getChildrenUnmodifiable().get(1).getStyleClass().contains("axis"));
        assertTrue(row.getChildrenUnmodifiable().get(2).getStyleClass().contains("opposite"));
        assertTrue(row.getPseudoClassStates().contains(RIGHT));
    }

    @Test
    public void itemNodesCarryModelIndexStyleClass() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        showInScene(view);

        List<Node> nodes = itemNodes(view);
        assertTrue(nodes.get(0).getStyleClass().contains("item0"));
        assertTrue(nodes.get(1).getStyleClass().contains("item1"));
        assertTrue(nodes.get(2).getStyleClass().contains("item2"));
        assertNotNull(view.lookup(".item0"));

        // Model order: under reverse the index follows the model item, so the display-first
        // node (model item "c", index 2) keeps item2 and the display-last keeps item0.
        view.setReverse(true);
        view.getParent().applyCss();
        view.getParent().layout();
        List<Node> reversed = itemNodes(view);
        assertTrue(reversed.get(0).getStyleClass().contains("item2"));
        assertTrue(reversed.get(2).getStyleClass().contains("item0"));
    }

    @Test
    public void connectorGapShortensLineWithoutAffectingLayout() {
        RXTimelineView view = new RXTimelineView(new RXTimelineItem("a"), new RXTimelineItem("b"));
        showInScene(view);
        Region item0 = (Region) itemNodes(view).get(0);
        Region connector = (Region) item0.lookup(".connector");
        double dotSize = view.getDotSize();
        double rowHeight = item0.getHeight();
        double baseLength = connector.getBoundsInParent().getHeight();
        assertTrue(baseLength > 0.0);

        view.setConnectorGap(6.0);
        view.getParent().applyCss();
        view.getParent().layout();
        // The gap insets the line dotSize+gap at the leading end and gap at the trailing
        // end; the line shortens by 2*gap but the row height is unchanged.
        assertEquals(dotSize + 6.0, StackPane.getMargin(connector).getTop(), EPSILON);
        assertEquals(6.0, StackPane.getMargin(connector).getBottom(), EPSILON);
        assertEquals(rowHeight, item0.getHeight(), EPSILON);
        assertEquals(baseLength - 12.0, connector.getBoundsInParent().getHeight(), 1.0);

        // A gap larger than the segment collapses the line to zero (never negative) and
        // still does not grow the row.
        view.setConnectorGap(1000.0);
        view.getParent().applyCss();
        view.getParent().layout();
        assertEquals(0.0, connector.getBoundsInParent().getHeight(), EPSILON);
        assertEquals(rowHeight, item0.getHeight(), EPSILON);
    }

    // ==================== Helpers ====================

    private static void showInScene(RXTimelineView view) {
        StackPane root = new StackPane(view);
        new Scene(root, 420, 480);
        root.applyCss();
        root.layout();
    }

    private static List<Node> itemNodes(RXTimelineView view) {
        Parent itemsBox = (Parent) view.lookup(".items");
        assertNotNull(itemsBox);
        return List.copyOf(itemsBox.getChildrenUnmodifiable());
    }

    private static double dotCenterX(Region dot) {
        Bounds bounds = dot.localToScene(dot.getBoundsInLocal());
        return (bounds.getMinX() + bounds.getMaxX()) / 2.0;
    }

    private static double dotCenterY(Region dot) {
        Bounds bounds = dot.localToScene(dot.getBoundsInLocal());
        return (bounds.getMinY() + bounds.getMaxY()) / 2.0;
    }

    private static CssMetaData<? extends Styleable, ?> findMeta(RXTimelineView view, String property) {
        return view.getControlCssMetaData().stream()
                .filter(meta -> meta.getProperty().equals(property))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static boolean isSettable(CssMetaData<? extends Styleable, ?> meta, RXTimelineView view) {
        return ((CssMetaData<Styleable, ?>) meta).isSettable(view);
    }

    private static MouseEvent syntheticClick(Node target) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0.0, 0.0, 0.0, 0.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                true, false, false,
                new PickResult(target, 0.0, 0.0));
    }
}
