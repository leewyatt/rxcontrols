package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXLrcLineEvent;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the PR-2 RXLrcView control API and line click event contract.
 */
public class RXLrcViewTest {

    private static final double EPSILON = 0.0001;

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
    public void defaultsAndCssMetadataUseNewApi() {
        RXLrcView view = new RXLrcView();

        assertTrue(view.getStyleClass().contains(RXLrcView.DEFAULT_STYLE_CLASS));
        assertNull(view.getDocument());
        assertEquals(Duration.ZERO, view.getCurrentTime());
        assertEquals(Duration.ZERO, view.getTimeOffset());
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());
        assertTrue(view.isAnimated());
        assertEquals(RXLrcView.DEFAULT_ANIMATION_DURATION, view.getAnimationDuration());
        assertEquals(RXLrcView.DEFAULT_CURRENT_LINE_POSITION,
                view.getCurrentLinePosition(), EPSILON);
        assertEquals(RXLrcView.DEFAULT_LINE_SPACING, view.getLineSpacing(), EPSILON);
        assertEquals(RXLrcView.DEFAULT_CURRENT_LINE_SCALE, view.getCurrentLineScale(), EPSILON);
        assertTrue(view.isManualBrowseEnabled());
        assertTrue(view.isMouseWheelBrowseEnabled());
        assertEquals(RXLrcView.DEFAULT_BROWSE_RECOVER_DELAY, view.getBrowseRecoverDelay());

        Label placeholder = assertInstanceOf(Label.class, view.getPlaceholder());
        assertEquals(RXLrcView.DEFAULT_PLACEHOLDER_TEXT, placeholder.getText());
        assertTrue(placeholder.getStyleClass().contains("placeholder"));

        List<String> cssProperties = view.getControlCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .toList();
        assertTrue(cssProperties.contains("-rx-animation-duration"));
        assertTrue(cssProperties.contains("-rx-current-line-position"));
        assertTrue(cssProperties.contains("-rx-line-spacing"));
        assertTrue(cssProperties.contains("-rx-current-line-scale"));
    }

    @Test
    public void manualBrowseApiIsPassThrough() {
        RXLrcView view = new RXLrcView();

        view.setManualBrowseEnabled(false);
        view.setMouseWheelBrowseEnabled(false);
        view.setBrowseRecoverDelay(null);

        assertFalse(view.isManualBrowseEnabled());
        assertFalse(view.isMouseWheelBrowseEnabled());
        assertNull(view.getBrowseRecoverDelay());

        view.setBrowseRecoverDelay(Duration.seconds(1.25));

        assertEquals(Duration.seconds(1.25), view.getBrowseRecoverDelay());
    }

    @Test
    public void currentLineDerivesFromDocumentCurrentTimeAndOffset() {
        RXLrcView view = new RXLrcView();
        RXLrcDocument document = RXLrcParser.parse("""
                [00:01.00]A
                [00:03.00]B
                """).document();

        view.setDocument(document);
        view.setCurrentTime(Duration.millis(500.0));
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());

        view.setCurrentTime(Duration.millis(1000.0));
        assertEquals(0, view.getCurrentLineIndex());
        assertEquals("A", view.getCurrentLine().text());

        view.setCurrentTime(Duration.millis(2500.0));
        view.setTimeOffset(Duration.millis(600.0));
        assertEquals(1, view.getCurrentLineIndex());
        assertEquals("B", view.getCurrentLine().text());

        view.setTimeOffset(null);
        assertEquals(0, view.getCurrentLineIndex());

        view.setCurrentTime(Duration.UNKNOWN);
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());
    }

    @Test
    public void setLyricsThinConvenienceSetsParsedDocument() {
        RXLrcView view = new RXLrcView();

        view.setLyrics("[00:01.00]A");

        assertNotNull(view.getDocument());
        assertEquals("A", view.getDocument().lines().get(0).text());
    }

    @Test
    public void emptyPseudoClassTracksDocumentState() {
        RXLrcView view = new RXLrcView();

        assertTrue(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));

        view.setDocument(RXLrcParser.parse("""
                [00:01.00]A
                [00:03.00]B
                """).document());

        assertTrue(!view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));

        view.setDocument(null);

        assertTrue(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));
    }

    @Test
    public void lineEventTypeHierarchyMatchesContract() {
        RXLrcView view = new RXLrcView();
        RXLrcLine line = RXLrcParser.parse("[00:01.00]A").document().lines().get(0);

        RXLrcLineEvent event = new RXLrcLineEvent(
                view, RXLrcLineEvent.LINE_CLICKED, line, 0, line.time());

        assertEquals(RXLrcLineEvent.ANY, RXLrcLineEvent.LINE_CLICKED.getSuperType());
        assertEquals("RX_LRC_LINE", RXLrcLineEvent.ANY.getName());
        assertSame(view, event.getLrcView());
        assertSame(line, event.getLine());
        assertEquals(0, event.getIndex());
        assertEquals(line.time(), event.getTime());
    }

    @Test
    public void onLineClickedPropertyInstallsEventHandler() {
        RXLrcView view = new RXLrcView();
        RXLrcLine line = RXLrcParser.parse("[00:01.00]A").document().lines().get(0);
        RXLrcLineEvent event = new RXLrcLineEvent(
                view, RXLrcLineEvent.LINE_CLICKED, line, 0, line.time());
        AtomicReference<RXLrcLineEvent> eventRef = new AtomicReference<>();

        view.setOnLineClicked(eventRef::set);
        view.fireEvent(event);

        assertSame(event, eventRef.get());
    }

    @Test
    public void dragDisabledDoesNotBrowseOrConsume() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setManualBrowseEnabled(false);
        Pane content = content(view);
        Node viewport = viewport(view);
        double before = content.getTranslateY();

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
        MouseEvent drag = mouseEvent(MouseEvent.MOUSE_DRAGGED, 120.0);
        viewport.fireEvent(drag);

        assertEquals(before, content.getTranslateY(), EPSILON);
        assertFalse(drag.isConsumed());
    }

    @Test
    public void dragBrowseKeepsDisplayStableAcrossCurrentLineChange() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        Pane content = content(view);
        Node viewport = viewport(view);
        AtomicBoolean dragBubbled = new AtomicBoolean(false);
        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> dragBubbled.set(true));

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
        MouseEvent drag = mouseEvent(MouseEvent.MOUSE_DRAGGED, 120.0);
        viewport.fireEvent(drag);
        double browsedTranslate = content.getTranslateY();

        view.setCurrentTime(Duration.seconds(8.0));

        assertFalse(dragBubbled.get());
        assertEquals(browsedTranslate, content.getTranslateY(), EPSILON);
    }

    @Test
    public void releaseWithZeroRecoverDelayReturnsToCurrentLineImmediately() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setBrowseRecoverDelay(Duration.ZERO);
        Pane content = content(view);
        Node viewport = viewport(view);
        double before = content.getTranslateY();

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 45.0));
        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 45.0));

        assertEquals(before, content.getTranslateY(), EPSILON);
    }

    @Test
    public void invalidRecoverDelayFallsBackToIdleDelay() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setBrowseRecoverDelay(Duration.UNKNOWN);
        Pane content = content(view);
        Node viewport = viewport(view);
        double before = content.getTranslateY();

        try {
            viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
            viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 45.0));
            viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 45.0));

            assertTrue(Math.abs(content.getTranslateY() - before) > EPSILON);
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void wheelDisabledDoesNotBrowseOrConsume() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setMouseWheelBrowseEnabled(false);
        Pane content = content(view);
        Node viewport = viewport(view);
        AtomicBoolean scrollBubbled = new AtomicBoolean(false);
        view.addEventHandler(ScrollEvent.SCROLL, event -> scrollBubbled.set(true));
        double before = content.getTranslateY();

        ScrollEvent scroll = scrollEvent(-35.0);
        viewport.fireEvent(scroll);

        assertEquals(before, content.getTranslateY(), EPSILON);
        assertTrue(scrollBubbled.get());
    }

    @Test
    public void wheelBrowseChangesTranslateAndConsumesEvent() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setBrowseRecoverDelay(Duration.UNKNOWN);
        Pane content = content(view);
        Node viewport = viewport(view);
        AtomicBoolean scrollBubbled = new AtomicBoolean(false);
        view.addEventHandler(ScrollEvent.SCROLL, event -> scrollBubbled.set(true));
        double before = content.getTranslateY();

        try {
            ScrollEvent scroll = scrollEvent(-35.0);
            viewport.fireEvent(scroll);

            assertFalse(scrollBubbled.get());
            assertTrue(Math.abs(content.getTranslateY() - before) > EPSILON);
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void emptyPlaceholderCanDragAndReboundsToCenter() {
        RXLrcView view = createLaidOutView(RXLrcDocument.empty(), Duration.ZERO);
        Node placeholder = placeholder(view);
        Node viewport = viewport(view);

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 130.0));

        assertTrue(Math.abs(placeholder.getTranslateY()) > EPSILON);

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 130.0));

        assertEquals(0.0, placeholder.getTranslateY(), EPSILON);
    }

    @Test
    public void emptyWheelDoesNotConsumeParentScroll() {
        RXLrcView view = createLaidOutView(RXLrcDocument.empty(), Duration.ZERO);
        Node viewport = viewport(view);

        ScrollEvent scroll = scrollEvent(-35.0);
        viewport.fireEvent(scroll);

        assertFalse(scroll.isConsumed());
    }

    @Test
    public void currentLinePositionUsesCenterAnchorAndUpdatesOnNextLayout() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));

        view.setCurrentLinePosition(0.0);
        relayout(view);
        assertCurrentLineCenterAt(view, 0.0);

        view.setCurrentLinePosition(0.5);
        relayout(view);
        assertCurrentLineCenterAt(view, 0.5);

        view.setCurrentLinePosition(1.0);
        relayout(view);
        assertCurrentLineCenterAt(view, 1.0);
    }

    @Test
    public void resizePreservesCurrentLinePositionAnchor() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setCurrentLinePosition(0.5);
        relayout(view);
        assertCurrentLineCenterAt(view, 0.5);

        resizeRoot(view, 260.0, 260.0);

        assertCurrentLineCenterAt(view, 0.5);
    }

    @Test
    public void lineSpacingChangeReanchorsCurrentLine() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));

        view.setLineSpacing(22.0);
        relayout(view);

        assertCurrentLineCenterAt(view, RXLrcView.DEFAULT_CURRENT_LINE_POSITION);
    }

    @Test
    public void currentLineScaleChangeAppliesWithoutLayoutMetricChange() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        Node currentLine = currentLineNode(view);

        view.setCurrentLineScale(1.25);

        assertEquals(1.25, currentLine.getScaleX(), EPSILON);
        assertEquals(1.25, currentLine.getScaleY(), EPSILON);
    }

    @Test
    public void documentReplacementClearsManualBrowseOffset() {
        RXLrcView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        view.setBrowseRecoverDelay(Duration.UNKNOWN);
        Pane content = content(view);
        Node viewport = viewport(view);
        double before = content.getTranslateY();

        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 80.0));
        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 45.0));
        assertTrue(Math.abs(content.getTranslateY() - before) > EPSILON);

        view.setDocument(alternateDocument());
        relayout(view);

        assertCurrentLineCenterAt(view, RXLrcView.DEFAULT_CURRENT_LINE_POSITION);
    }

    @Test
    public void lineNodesKeepViewportWidthAndWrapLongText() {
        RXLrcView view = createLaidOutView(wrappingDocument(), Duration.seconds(2.0));
        Pane content = content(view);
        Node viewport = viewport(view);
        Node shortLine = content.getChildren().get(0);
        Node longLine = content.getChildren().get(1);

        assertEquals(viewport.getLayoutBounds().getWidth(),
                shortLine.getLayoutBounds().getWidth(), EPSILON);
        assertEquals(viewport.getLayoutBounds().getWidth(),
                longLine.getLayoutBounds().getWidth(), EPSILON);
        assertTrue(longLine.getLayoutBounds().getHeight()
                > shortLine.getLayoutBounds().getHeight());
        assertCurrentLineCenterAt(view, RXLrcView.DEFAULT_CURRENT_LINE_POSITION);
    }

    private static RXLrcDocument longDocument() {
        return RXLrcParser.parse("""
                [00:00.00]Line 1
                [00:02.00]Line 2
                [00:04.00]Line 3
                [00:06.00]Line 4
                [00:08.00]Line 5
                [00:10.00]Line 6
                [00:12.00]Line 7
                [00:14.00]Line 8
                """).document();
    }

    private static RXLrcDocument alternateDocument() {
        return RXLrcParser.parse("""
                [00:00.00]Intro
                [00:02.00]Verse
                [00:04.00]Hook
                [00:06.00]Bridge
                [00:08.00]Outro
                """).document();
    }

    private static RXLrcDocument wrappingDocument() {
        return RXLrcParser.parse("""
                [00:00.00]Short
                [00:02.00]This is a deliberately long lyric line that should wrap inside the viewport width instead of expanding the line node beyond the viewport.
                [00:04.00]After
                """).document();
    }

    private static RXLrcView createLaidOutView(RXLrcDocument document, Duration currentTime) {
        RXLrcView view = new RXLrcView();
        view.setAnimated(false);
        view.setDocument(document);
        view.setCurrentTime(currentTime);
        StackPane root = new StackPane(view);
        new Scene(root, 260.0, 180.0);
        root.resize(260.0, 180.0);
        root.applyCss();
        root.layout();
        return view;
    }

    private static Pane content(RXLrcView view) {
        return assertInstanceOf(Pane.class, view.lookup(".content"));
    }

    private static Node viewport(RXLrcView view) {
        Node viewport = view.lookup(".viewport");
        assertNotNull(viewport);
        return viewport;
    }

    private static Node placeholder(RXLrcView view) {
        Node placeholder = view.lookup(".placeholder");
        assertNotNull(placeholder);
        return placeholder;
    }

    private static Node currentLineNode(RXLrcView view) {
        Pane content = content(view);
        int index = view.getCurrentLineIndex();
        assertTrue(index >= 0 && index < content.getChildren().size());
        return content.getChildren().get(index);
    }

    private static void assertCurrentLineCenterAt(RXLrcView view, double ratio) {
        Pane content = content(view);
        Node viewport = viewport(view);
        Node line = currentLineNode(view);
        double centerY = content.getTranslateY()
                + line.getLayoutY()
                + line.getLayoutBounds().getHeight() / 2.0;
        double expectedY = viewport.getLayoutBounds().getHeight() * ratio;
        assertEquals(expectedY, centerY, 0.75);
    }

    private static void relayout(RXLrcView view) {
        view.getScene().getRoot().applyCss();
        view.getScene().getRoot().layout();
    }

    private static void resizeRoot(RXLrcView view, double width, double height) {
        view.getScene().getRoot().resize(width, height);
        relayout(view);
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> eventType, double y) {
        boolean primaryDown = eventType == MouseEvent.MOUSE_PRESSED
                || eventType == MouseEvent.MOUSE_DRAGGED;
        return new MouseEvent(eventType, 20.0, y, 20.0, y,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, false,
                new PickResult(null, 20.0, y));
    }

    private static ScrollEvent scrollEvent(double deltaY) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                20.0, 80.0, 20.0, 80.0,
                false, false, false, false,
                false, false,
                0.0, deltaY, 0.0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0,
                null);
    }
}
