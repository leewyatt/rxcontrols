package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.TimelineItemType;
import io.github.leewyatt.rxcontrols.event.RXTimelineItemEvent;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
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

        assertTrue(view.getStyleClass().contains(RXTimelineView.DEFAULT_STYLE_CLASS));
        assertTrue(view.getItems().isEmpty());
        assertFalse(view.isReverse());
        assertEquals(RXTimelineView.DEFAULT_DOT_SIZE, view.getDotSize(), EPSILON);
        assertEquals(RXTimelineView.DEFAULT_LINE_WIDTH, view.getLineWidth(), EPSILON);
        assertEquals(RXTimelineView.DEFAULT_ITEM_SPACING, view.getItemSpacing(), EPSILON);
        assertNull(view.getPlaceholder());
        assertNull(view.getOnItemClicked());
        assertEquals(Orientation.HORIZONTAL, view.getContentBias());
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
        success.setType(TimelineItemType.SUCCESS);
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
    public void onItemClickedFiresWithModelIndex() {
        RXTimelineView view = new RXTimelineView(
                new RXTimelineItem("a"), new RXTimelineItem("b"), new RXTimelineItem("c"));
        showInScene(view);

        AtomicReference<RXTimelineItemEvent> received = new AtomicReference<>();
        view.setOnItemClicked(received::set);

        Node second = itemNodes(view).get(1);
        Event.fireEvent(second, syntheticClick(second));

        RXTimelineItemEvent event = received.get();
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

        AtomicReference<RXTimelineItemEvent> received = new AtomicReference<>();
        view.setOnItemClicked(received::set);

        // Display position 0 is model item "c" (index 2) under reverse.
        Node firstDisplayed = itemNodes(view).get(0);
        Event.fireEvent(firstDisplayed, syntheticClick(firstDisplayed));

        RXTimelineItemEvent event = received.get();
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
