package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXTransitionPaneEvent;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the RXTransitionPane content lifecycle: event types, dispatch scope,
 * and the onContentXxx handler properties.
 */
public class RXTransitionPaneLifecycleTest {

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

    // ==================== Event Types ====================

    @Test
    public void eventTypesHangUnderOneRxPrefixedRoot() {
        assertSame(Event.ANY, RXTransitionPaneEvent.ANY.getSuperType());
        assertEquals("RX_TRANSITION_PANE_CONTENT", RXTransitionPaneEvent.ANY.getName());
        List<EventType<RXTransitionPaneEvent>> types = List.of(RXTransitionPaneEvent.OPENING,
                RXTransitionPaneEvent.OPENED, RXTransitionPaneEvent.CLOSING, RXTransitionPaneEvent.CLOSED);
        List<String> names = List.of("OPENING", "OPENED", "CLOSING", "CLOSED");
        for (int i = 0; i < types.size(); i++) {
            assertSame(RXTransitionPaneEvent.ANY, types.get(i).getSuperType());
            assertEquals("RX_TRANSITION_PANE_CONTENT_" + names.get(i), types.get(i).getName());
        }
    }

    // ==================== Dispatch Scope ====================

    @Test
    public void eventsReachContentAndPaneButNotAncestorHandlers() {
        Label content = new Label("A");
        RXTransitionPane pane = new RXTransitionPane(content);
        StackPane root = layOut(pane);
        List<String> log = new ArrayList<>();
        content.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("content"));
        pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("pane handler"));
        pane.setOnContentOpened(e -> log.add("onContentOpened"));
        root.addEventFilter(RXTransitionPaneEvent.ANY, e -> log.add("root filter"));
        root.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("root handler"));

        try {
            content.fireEvent(new RXTransitionPaneEvent(RXTransitionPaneEvent.OPENED, pane, content));

            assertEquals(Set.of("root filter", "content", "pane handler", "onContentOpened"),
                    Set.copyOf(log));
            assertEquals(4, log.size());
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void nestedPaneEventsStopAtInnerPane() {
        Label innerContent = new Label("inner");
        RXTransitionPane inner = new RXTransitionPane(innerContent);
        VBox outerPage = new VBox(inner);
        RXTransitionPane outer = new RXTransitionPane(outerPage);
        layOut(outer);
        List<String> log = new ArrayList<>();
        outerPage.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("outer page"));
        outer.setOnContentOpening(e -> log.add("outer onContentOpening"));
        inner.setOnContentOpening(e -> log.add("inner onContentOpening"));

        try {
            innerContent.fireEvent(
                    new RXTransitionPaneEvent(RXTransitionPaneEvent.OPENING, inner, innerContent));

            assertEquals(List.of("inner onContentOpening"), log);
        } finally {
            inner.getSkin().dispose();
            outer.getSkin().dispose();
        }
    }

    @Test
    public void eventKeepsPaneAndContentWhileItTravels() {
        Label content = new Label("A");
        RXTransitionPane pane = new RXTransitionPane(content);
        layOut(pane);
        List<RXTransitionPaneEvent> received = new ArrayList<>();
        content.addEventHandler(RXTransitionPaneEvent.CLOSING, received::add);
        pane.setOnContentClosing(received::add);

        try {
            content.fireEvent(new RXTransitionPaneEvent(RXTransitionPaneEvent.CLOSING, pane, content));

            assertEquals(2, received.size());
            for (RXTransitionPaneEvent event : received) {
                assertSame(pane, event.getPane());
                assertSame(content, event.getContent());
                assertSame(content, event.getTarget());
            }
            assertSame(pane, received.get(1).getSource());
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Handler Properties ====================

    @Test
    public void handlerPropertiesAreLazyAndBoundToTheirEventType() {
        RXTransitionPane pane = new RXTransitionPane();
        assertNull(pane.getOnContentOpening());
        assertNull(pane.getOnContentOpened());
        assertNull(pane.getOnContentClosing());
        assertNull(pane.getOnContentClosed());
        assertSame(pane, pane.onContentClosedProperty().getBean());
        assertEquals("onContentClosed", pane.onContentClosedProperty().getName());

        Label content = new Label("A");
        pane.setContent(content);
        layOut(pane);
        List<String> log = new ArrayList<>();
        pane.setOnContentOpening(e -> log.add("opening"));
        pane.setOnContentOpened(e -> log.add("opened"));
        pane.setOnContentClosing(e -> log.add("closing"));
        pane.setOnContentClosed(e -> log.add("closed"));

        try {
            for (EventType<RXTransitionPaneEvent> type : List.of(RXTransitionPaneEvent.CLOSED,
                    RXTransitionPaneEvent.CLOSING, RXTransitionPaneEvent.OPENED,
                    RXTransitionPaneEvent.OPENING)) {
                content.fireEvent(new RXTransitionPaneEvent(type, pane, content));
            }
            assertEquals(List.of("closed", "closing", "opened", "opening"), log);

            pane.setOnContentClosed(null);
            content.fireEvent(new RXTransitionPaneEvent(RXTransitionPaneEvent.CLOSED, pane, content));
            assertEquals(4, log.size());
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Helpers ====================

    private static StackPane layOut(Node node) {
        StackPane root = new StackPane(node);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
        return root;
    }
}
