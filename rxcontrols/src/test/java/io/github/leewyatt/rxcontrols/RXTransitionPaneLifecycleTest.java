package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimAround;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.event.RXTransitionPaneEvent;
import io.github.leewyatt.rxcontrols.skins.RXTransitionPaneSkin;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTransitionPane content lifecycle: event types, dispatch scope,
 * the onContentXxx handler properties, event order and pane state for every
 * kind of change, re-entrant changes from handlers, external stops,
 * exceptions, and the page animation presets.
 */
public class RXTransitionPaneLifecycleTest {

    private static final String TYPE_PREFIX = "RX_TRANSITION_PANE_CONTENT_";

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
            assertEquals(TYPE_PREFIX + names.get(i), types.get(i).getName());
        }
    }

    // ==================== Dispatch Scope ====================

    @Test
    public void eventsReachContentAndPaneButNotAncestorHandlers() throws Exception {
        onFx(() -> {
            Label content = new Label("A");
            RXTransitionPane pane = new RXTransitionPane(content);
            StackPane root = layOut(pane);
            List<String> log = new ArrayList<>();
            content.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("content"));
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("pane handler"));
            pane.setOnContentOpened(e -> log.add("onContentOpened"));
            root.addEventFilter(RXTransitionPaneEvent.ANY, e -> log.add("root filter"));
            root.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("root handler"));

            content.fireEvent(new RXTransitionPaneEvent(RXTransitionPaneEvent.OPENED, pane, content));

            assertEquals(Set.of("root filter", "content", "pane handler", "onContentOpened"),
                    Set.copyOf(log));
            assertEquals(4, log.size());
        });
    }

    @Test
    public void contentConsumingStopsPaneHandlersButNotPaneFilters() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = new RXTransitionPane(a);
            layOut(pane);
            pane.setAnimated(false);
            List<String> log = new ArrayList<>();
            Label b = new Label("B");
            b.addEventHandler(RXTransitionPaneEvent.ANY, Event::consume);
            pane.addEventFilter(RXTransitionPaneEvent.ANY, e -> log.add("filter " + describe(e)));
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("handler " + describe(e)));

            pane.setContent(b);

            assertEquals(List.of("filter CLOSING:A", "handler CLOSING:A",
                    "filter CLOSED:A", "handler CLOSED:A",
                    "filter OPENING:B", "filter OPENED:B"), log);
        });
    }

    @Test
    public void nestedPaneEventsStopAtInnerPane() throws Exception {
        onFx(() -> {
            RXTransitionPane inner = new RXTransitionPane(new Label("A"));
            inner.setAnimated(false);
            VBox outerPage = new VBox(inner);
            RXTransitionPane outer = new RXTransitionPane(outerPage);
            layOut(outer);
            List<String> log = new ArrayList<>();
            outerPage.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add("outer page " + describe(e)));
            outer.setOnContentOpening(e -> log.add("outer " + describe(e)));
            outer.setOnContentClosed(e -> log.add("outer " + describe(e)));
            inner.setOnContentOpening(e -> log.add("inner " + describe(e)));
            inner.setOnContentClosed(e -> log.add("inner " + describe(e)));

            inner.setContent(new Label("B"));

            assertEquals(List.of("inner CLOSED:A", "inner OPENING:B"), log);
        });
    }

    @Test
    public void eventKeepsPaneAndContentWhileItTravels() throws Exception {
        onFx(() -> {
            Label content = new Label("A");
            RXTransitionPane pane = new RXTransitionPane(content);
            layOut(pane);
            List<RXTransitionPaneEvent> received = new ArrayList<>();
            content.addEventHandler(RXTransitionPaneEvent.CLOSING, received::add);
            pane.setOnContentClosing(received::add);

            content.fireEvent(new RXTransitionPaneEvent(RXTransitionPaneEvent.CLOSING, pane, content));

            assertEquals(2, received.size());
            for (RXTransitionPaneEvent event : received) {
                assertSame(pane, event.getPane());
                assertSame(content, event.getContent());
                assertSame(content, event.getTarget());
            }
            assertSame(pane, received.get(1).getSource());
        });
    }

    // ==================== Handler Properties ====================

    @Test
    public void handlerPropertiesAreLazyAndCoexistWithAddedHandlers() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = new RXTransitionPane();
            assertNull(pane.getOnContentOpening());
            assertNull(pane.getOnContentOpened());
            assertNull(pane.getOnContentClosing());
            assertNull(pane.getOnContentClosed());
            assertSame(pane, pane.onContentClosedProperty().getBean());
            assertEquals("onContentClosed", pane.onContentClosedProperty().getName());

            pane.setAnimated(false);
            layOut(pane);
            List<String> log = new ArrayList<>();
            pane.setOnContentOpening(e -> log.add("onContentOpening"));
            pane.setOnContentOpened(e -> log.add("onContentOpened"));
            pane.setOnContentClosing(e -> log.add("onContentClosing"));
            pane.setOnContentClosed(e -> log.add("onContentClosed"));
            pane.addEventHandler(RXTransitionPaneEvent.OPENING, e -> log.add("added 1"));
            pane.addEventHandler(RXTransitionPaneEvent.OPENING, e -> log.add("added 2"));

            pane.setContent(new Label("A"));
            pane.setContent(null);
            assertEquals(List.of("added 1", "added 2", "onContentOpening", "onContentOpened",
                    "onContentClosing", "onContentClosed"), log);

            pane.setOnContentOpening(null);
            log.clear();
            pane.setContent(new Label("B"));
            assertEquals(List.of("added 1", "added 2", "onContentOpened"), log);
        });
    }

    // ==================== Initial Content ====================

    @Test
    public void initialContentOpensWhenTheSkinIsInstalled() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = new RXTransitionPane(new Label("A"));
            Recorder recorder = new Recorder(pane);
            pane.setContent(new Label("B"));
            assertEquals(List.of(), recorder.take());

            layOut(pane);

            assertEquals(List.of("OPENING:B", "OPENED:B"), recorder.take());
            assertEquals("B", visibleText(pane));
        });
    }

    // ==================== Animated Change ====================

    @Test
    public void animatedChangeFiresStartPairThenCompletionPair() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Recorder recorder = new Recorder(pane);
            Label b = new Label("B");

            pane.setContent(b);
            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true"), recorder.take());
            assertEquals(1, animation.contexts.size());

            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B"), recorder.take());
            assertNull(a.getParent());
            assertEquals("B", visibleText(pane));
        });
    }

    @Test
    public void eachEventSeesTheDocumentedPaneState() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            List<String> log = new ArrayList<>();
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add(stateAt(e)));

            pane.setContent(new Label("B"));
            animation.finishNaturally();
            pane.setAnimated(false);
            pane.setContent(new Label("C"));

            // in = attached under the pane, page = its wrapper is visible,
            // own = the content node's own visible flag
            assertEquals(List.of(
                    "CLOSING:A t=false in=true page=true own=true",
                    "OPENING:B t=false in=true page=false own=true",
                    "CLOSED:A t=false in=true page=false own=true",
                    "OPENED:B t=false in=true page=true own=true",
                    "CLOSING:B t=false in=true page=true own=true",
                    "CLOSED:B t=false in=true page=false own=true",
                    "OPENING:C t=false in=true page=false own=true",
                    "OPENED:C t=false in=true page=true own=true"), log);
        });
    }

    @Test
    public void zeroLengthAnimationCompletesInsideTheChange() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            pane.setAnimation(new ZeroLengthAnimation());
            Recorder recorder = new Recorder(pane);

            pane.setContent(new Label("B"));

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B"), recorder.take());
            assertFalse(pane.isTransitioning());
            assertNull(a.getParent());
            assertEquals("B", visibleText(pane));
        });
    }

    @Test
    public void interruptedTransitionCompletesBeforeTheNextStarts() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Recorder recorder = new Recorder(pane);
            Label b = new Label("B");

            pane.setContent(b);
            pane.setContent(new Label("C"));

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
            assertSame(b, pageContent(animation.contexts.get(1).getCurrentPage()));

            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:B", "OPENED:C"), recorder.take());
        });
    }

    // ==================== Direct Cuts ====================

    @Test
    public void directCutsClosePreviousContentBeforeOpening() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            pane.setAnimated(false);
            Recorder recorder = new Recorder(pane);

            pane.setContent(new Label("B"));
            assertEquals(List.of("CLOSING:A", "CLOSED:A", "OPENING:B", "OPENED:B"), recorder.take());
            assertNull(a.getParent());

            pane.setContent(null);
            assertEquals(List.of("CLOSING:B", "CLOSED:B"), recorder.take());
            assertNull(visibleText(pane));

            pane.setContent(a);
            assertEquals(List.of("OPENING:A", "OPENED:A"), recorder.take());

            pane.setContent(a);
            assertEquals(List.of(), recorder.take());
        });
    }

    @Test
    public void gatedChangesUseTheDirectCutOrder() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            Recorder recorder = new Recorder(pane);
            List<String> cut = List.of("CLOSING:A", "CLOSED:A", "OPENING:B", "OPENED:B",
                    "CLOSING:B", "CLOSED:B", "OPENING:A", "OPENED:A");
            Label a = (Label) pane.getContent();

            pane.setAnimationDuration(Duration.ZERO);
            pane.setContent(new Label("B"));
            pane.setContent(a);
            assertEquals(cut, recorder.take());

            pane.setAnimationDuration(Duration.millis(500.0));
            pane.setAnimation(null);
            pane.setContent(new Label("B"));
            pane.setContent(a);
            assertEquals(cut, recorder.take());

            ManualAnimation multiPage = new ManualAnimation();
            multiPage.multiPageDisplay = true;
            pane.setAnimation(multiPage);
            pane.setContent(new Label("B"));
            pane.setContent(a);
            assertEquals(cut, recorder.take());
            assertEquals(0, multiPage.contexts.size());
        });
    }

    // ==================== Re-entrant Changes ====================

    @Test
    public void changeFromSettlingTransitioningListenerStartsAfterCompletion() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            Label c = new Label("C");
            pane.transitioningProperty().addListener((observable, was, is) -> {
                if (!is && pane.getContent() == b) {
                    pane.setContent(c);
                }
            });
            Recorder recorder = new Recorder(pane);

            pane.setContent(b);
            animation.finishNaturally();

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
            TransitionContext running = animation.contexts.get(1);
            assertSame(b, pageContent(running.getCurrentPage()));
            assertSame(pane.lookup(".content-pane"), running.getCurrentPage().getParent());
            assertSame(c, pageContent(running.getNextPage()));
        });
    }

    @Test
    public void changeInOpeningRedirectsWithoutShowingTheAbandonedContent() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            Label login = new Label("L");
            List<Boolean> bPageVisible = new ArrayList<>();
            b.addEventHandler(RXTransitionPaneEvent.ANY, e -> bPageVisible.add(b.getParent().isVisible()));
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> pane.setContent(login));
            Recorder recorder = new Recorder(pane);

            pane.setContent(b);

            assertEquals(List.of("CLOSING:A", "OPENING:B", "OPENED:B", "CLOSING:B", "CLOSED:B",
                    "OPENING:L", "transitioning=true"), recorder.take());
            assertEquals(List.of(false, false, false, false), bPageVisible);
            assertNull(b.getParent());
            assertEquals(1, animation.contexts.size());
            assertSame(login, pageContent(animation.contexts.get(0).getNextPage()));

            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:L"), recorder.take());
        });
    }

    @Test
    public void changeBackInClosingKeepsTheContentWithoutAnimating() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            a.addEventHandler(RXTransitionPaneEvent.CLOSING, e -> pane.setContent(a));
            Recorder recorder = new Recorder(pane);
            Label b = new Label("B");

            pane.setContent(b);

            assertEquals(List.of("CLOSING:A", "CLOSED:A", "OPENING:A", "OPENED:A"), recorder.take());
            assertEquals(0, animation.contexts.size());
            assertNull(b.getParent());
            assertEquals("A", visibleText(pane));
        });
    }

    @Test
    public void changeInClosingPlaysTowardsTheLatestContent() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label c = new Label("C");
            a.addEventHandler(RXTransitionPaneEvent.CLOSING, e -> pane.setContent(c));
            Recorder recorder = new Recorder(pane);

            pane.setContent(new Label("B"));

            assertEquals(List.of("CLOSING:A", "OPENING:C", "transitioning=true"), recorder.take());
            assertEquals(1, animation.contexts.size());
            assertSame(c, pageContent(animation.contexts.get(0).getNextPage()));
        });
    }

    @Test
    public void changeInClosedOrOpenedRunsAfterThePairCompletes() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            a.addEventHandler(RXTransitionPaneEvent.CLOSED, e -> pane.setContent(new Label("C")));
            b.addEventHandler(RXTransitionPaneEvent.OPENED, e -> pane.setContent(null));
            Recorder recorder = new Recorder(pane);

            pane.setContent(b);
            animation.finishNaturally();

            // CLOSED:A asks for C, OPENED:B (still due) asks for null: latest wins
            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B", "CLOSING:B", "CLOSED:B"),
                    recorder.take());
            assertNull(visibleText(pane));
        });
    }

    @Test
    public void changeInDirectCutOpeningCompletesTheAbandonedPair() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimated(false);
            Label b = new Label("B");
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> pane.setContent(new Label("C")));
            Recorder recorder = new Recorder(pane);

            pane.setContent(b);

            assertEquals(List.of("CLOSING:A", "CLOSED:A", "OPENING:B", "OPENED:B",
                    "CLOSING:B", "CLOSED:B", "OPENING:C", "OPENED:C"), recorder.take());
            assertEquals("C", visibleText(pane));
        });
    }

    @Test
    public void changeWhenTransitionStartsJumpsTheNewTransitionToItsEnd() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label c = new Label("C");
            pane.transitioningProperty().addListener((observable, was, is) -> {
                if (is && pane.getContent() != c) {
                    pane.setContent(c);
                }
            });
            Recorder recorder = new Recorder(pane);

            pane.setContent(new Label("B"));

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
        });
    }

    // ==================== External Stops ====================

    @Test
    public void externalStopCompletesOnTheNextPulseTurn() throws Exception {
        RXTransitionPane[] pane = new RXTransitionPane[1];
        ManualAnimation animation = new ManualAnimation();
        Recorder[] recorder = new Recorder[1];
        onFx(() -> {
            pane[0] = laidOutPane(new Label("A"));
            pane[0].setAnimation(animation);
            recorder[0] = new Recorder(pane[0]);
            pane[0].setContent(new Label("B"));

            // A resize guard or the application stops the animation directly
            animation.jumpToEnd();

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true"), recorder[0].take());
        });
        onFx(() -> {
            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B"), recorder[0].take());
            assertEquals("B", visibleText(pane[0]));
        });
    }

    @Test
    public void changeAfterExternalStopCompletesTheStoppedTransitionFirst() throws Exception {
        Recorder[] recorder = new Recorder[1];
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            recorder[0] = new Recorder(pane);
            pane.setContent(new Label("B"));
            animation.jumpToEnd();

            pane.setContent(new Label("C"));

            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder[0].take());
        });
        onFx(() -> assertEquals(List.of(), recorder[0].take()));
    }

    // ==================== Exceptions ====================

    @Test
    public void throwingHandlerIsReportedAndTheSequenceStillCompletes() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> {
                throw new IllegalStateException("page failed to load");
            });
            List<String> afterThrow = new ArrayList<>();
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> afterThrow.add("skipped"));
            Recorder recorder = new Recorder(pane);
            List<Throwable> reported = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, e) -> reported.add(e));
            try {
                pane.setContent(b);
                animation.finishNaturally();
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }

            assertEquals(1, reported.size());
            assertEquals("page failed to load", reported.get(0).getMessage());
            assertEquals(List.of(), afterThrow);
            // The throwing handler ends that event's dispatch, so the pane
            // handler misses OPENING:B; the pairs still complete.
            assertEquals(List.of("CLOSING:A", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B"), recorder.take());
            assertFalse(pane.isTransitioning());
            assertEquals("B", visibleText(pane));
        });
    }

    // ==================== Skin Lifecycle ====================

    @Test
    public void replacingTheSkinWhileIdleFiresNothing() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimated(false);
            Recorder recorder = new Recorder(pane);

            pane.setSkin(new RXTransitionPaneSkin(pane) {
            });

            assertEquals(List.of(), recorder.take());
            assertEquals(1, pane.getChildrenUnmodifiable().size());
            assertEquals("A", visibleText(pane));

            pane.setContent(new Label("B"));
            assertEquals(List.of("CLOSING:A", "CLOSED:A", "OPENING:B", "OPENED:B"), recorder.take());
        });
    }

    @Test
    public void removingTheSkinClosesTheContentAndTheNextSkinOpensTheCurrentOne() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            Recorder recorder = new Recorder(pane);

            pane.setSkin(null);
            assertEquals(List.of("CLOSING:A", "CLOSED:A"), recorder.take());
            assertNull(a.getParent());

            pane.setContent(new Label("C"));
            assertEquals(List.of(), recorder.take());

            pane.setSkin(new RXTransitionPaneSkin(pane));
            assertEquals(List.of("OPENING:C", "OPENED:C"), recorder.take());
            assertEquals("C", visibleText(pane));
        });
    }

    @Test
    public void replacingTheSkinMidTransitionCompletesThePairsOnce() throws Exception {
        onFx(() -> {
            Label a = new Label("A");
            RXTransitionPane pane = laidOutPane(a);
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            List<Boolean> closedInPane = new ArrayList<>();
            a.addEventHandler(RXTransitionPaneEvent.CLOSED, e -> closedInPane.add(a.getScene() != null));
            Recorder recorder = new Recorder(pane);
            pane.setContent(new Label("B"));
            assertEquals(List.of("CLOSING:A", "OPENING:B", "transitioning=true"), recorder.take());

            pane.setSkin(new RXTransitionPaneSkin(pane) {
            });

            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B"), recorder.take());
            assertEquals(List.of(true), closedInPane);
            assertEquals(1, pane.getChildrenUnmodifiable().size());
            assertEquals("B", visibleText(pane));
            assertNull(a.getParent());

            pane.setContent(new Label("C"));
            assertEquals(List.of("CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
        });
    }

    @Test
    public void changeDuringSkinReplacementIsPlayedByTheNewSkin() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            Label c = new Label("C");
            b.addEventHandler(RXTransitionPaneEvent.OPENED, e -> pane.setContent(c));
            Recorder recorder = new Recorder(pane);
            pane.setContent(b);
            recorder.take();

            pane.setSkin(new RXTransitionPaneSkin(pane) {
            });

            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
            assertEquals(1, pane.getChildrenUnmodifiable().size());
            TransitionContext running = animation.contexts.get(animation.contexts.size() - 1);
            assertSame(b, pageContent(running.getCurrentPage()));
            assertSame(c, pageContent(running.getNextPage()));
            assertSame(pane.lookup(".content-pane"), running.getCurrentPage().getParent());
        });
    }

    @Test
    public void carriedContentMovedElsewhereIsClosedWhereItIs() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimation(new ManualAnimation());
            Label b = new Label("B");
            VBox elsewhere = new VBox();
            List<String> bLog = new ArrayList<>();
            b.addEventHandler(RXTransitionPaneEvent.ANY, e -> bLog.add(describe(e)));
            b.addEventHandler(RXTransitionPaneEvent.OPENED, e -> {
                elsewhere.getChildren().add(b);
                pane.setContent(new Label("C"));
            });
            Recorder recorder = new Recorder(pane);
            pane.setContent(b);
            recorder.take();

            pane.setSkin(new RXTransitionPaneSkin(pane) {
            });

            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B",
                    "OPENING:C", "OPENED:C"), recorder.take());
            assertEquals(List.of("OPENING:B", "OPENED:B", "CLOSING:B", "CLOSED:B"), bLog);
            assertSame(elsewhere, b.getParent());
            assertEquals("C", visibleText(pane));
        });
    }

    @Test
    public void ignoredSkinOfTheInstalledClassStaysInert() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimated(false);
            Recorder recorder = new Recorder(pane);

            RXTransitionPaneSkin ignored = new RXTransitionPaneSkin(pane);
            pane.setSkin(ignored);

            assertFalse(pane.getSkin() == ignored);
            assertEquals(List.of(), recorder.take());
            assertEquals("A", visibleText(pane));

            pane.setContent(new Label("B"));
            assertEquals(List.of("CLOSING:A", "CLOSED:A", "OPENING:B", "OPENED:B"), recorder.take());
            assertEquals("B", visibleText(pane));
        });
    }

    @Test
    public void skinReplacedFromAHandlerTakesTheChangeOverAfterTheStep() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Label b = new Label("B");
            Label c = new Label("C");
            b.addEventHandler(RXTransitionPaneEvent.OPENED, e -> {
                pane.setContent(c);
                pane.setSkin(new RXTransitionPaneSkin(pane) {
                });
            });
            Recorder recorder = new Recorder(pane);
            pane.setContent(b);
            recorder.take();

            animation.finishNaturally();

            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true"), recorder.take());
            assertEquals(1, pane.getChildrenUnmodifiable().size());
            TransitionContext running = animation.contexts.get(animation.contexts.size() - 1);
            assertSame(b, pageContent(running.getCurrentPage()));
            assertSame(c, pageContent(running.getNextPage()));
        });
    }

    @Test
    public void ignoredSkinOfTheInstalledClassCanBeCollected() throws Exception {
        RXTransitionPane[] pane = new RXTransitionPane[1];
        List<WeakReference<RXTransitionPaneSkin>> ignored = new ArrayList<>();
        onFx(() -> {
            pane[0] = laidOutPane(new Label("A"));
            RXTransitionPaneSkin skin = new RXTransitionPaneSkin(pane[0]);
            pane[0].setSkin(skin);
            assertFalse(pane[0].getSkin() == skin);
            ignored.add(new WeakReference<>(skin));
        });

        for (int i = 0; i < 50 && ignored.get(0).get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(ignored.get(0).get(), "an ignored skin must not stay reachable from the pane");
        onFx(() -> assertEquals("A", visibleText(pane[0])));
    }

    @Test
    public void skinReplacedInOpeningShowsTheContentAtOpened() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimation(new ManualAnimation());
            Label b = new Label("B");
            boolean[] replaced = {false};
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> {
                if (!replaced[0]) {
                    replaced[0] = true;
                    pane.setSkin(new RXTransitionPaneSkin(pane) {
                    });
                }
            });
            List<String> log = new ArrayList<>();
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add(stateAt(e)));

            pane.setContent(b);

            assertEquals(List.of(
                    "CLOSING:A t=false in=true page=true own=true",
                    "OPENING:B t=false in=true page=false own=true",
                    "CLOSED:A t=false in=true page=false own=true",
                    "OPENED:B t=false in=true page=true own=true"), log);
            assertEquals("B", visibleText(pane));
        });
    }

    @Test
    public void skinRemovedInDirectCutOpeningShowsTheContentAtOpened() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            pane.setAnimated(false);
            Label b = new Label("B");
            b.addEventHandler(RXTransitionPaneEvent.OPENING, e -> pane.setSkin(null));
            List<String> log = new ArrayList<>();
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add(stateAt(e)));

            pane.setContent(b);

            // The handler removed the skin, and with it the pages, while
            // OPENING:B was still on its way to the pane.
            assertEquals(List.of(
                    "CLOSING:A t=false in=true page=true own=true",
                    "CLOSED:A t=false in=true page=false own=true",
                    "OPENING:B t=false in=false page=false own=true",
                    "OPENED:B t=false in=true page=true own=true",
                    "CLOSING:B t=false in=true page=true own=true",
                    "CLOSED:B t=false in=true page=false own=true"), log);
        });
    }

    @Test
    public void disposingTheInstalledSkinClosesTheContent() throws Exception {
        onFx(() -> {
            RXTransitionPane pane = laidOutPane(new Label("A"));
            ManualAnimation animation = new ManualAnimation();
            pane.setAnimation(animation);
            Recorder recorder = new Recorder(pane);
            pane.setContent(new Label("B"));
            recorder.take();

            pane.getSkin().dispose();

            assertEquals(List.of("transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "CLOSED:B"), recorder.take());
            pane.setContent(new Label("C"));
            assertEquals(List.of(), recorder.take());
        });
    }

    // ==================== Presets ====================

    @Test
    public void everyApplicablePresetFiresEachPairExactlyOnce() throws Exception {
        List<PageAnimation> presets = applicablePresets();
        assertTrue(presets.size() > 60, "presets found: " + presets.size());
        CountDownLatch settled = new CountDownLatch(presets.size());
        List<Recorder> recorders = new ArrayList<>();
        List<RXTransitionPane> panes = new ArrayList<>();
        onFx(() -> {
            for (PageAnimation preset : presets) {
                RXTransitionPane pane = laidOutPane(new Label("A"));
                pane.setAnimation(preset);
                pane.setAnimationDuration(Duration.millis(40.0));
                Recorder recorder = new Recorder(pane);
                pane.addEventHandler(RXTransitionPaneEvent.OPENED, e -> {
                    if ("D".equals(text(e.getContent()))) {
                        settled.countDown();
                    }
                });
                panes.add(pane);
                recorders.add(recorder);
                pane.setContent(new Label("B"));
                pane.setContent(new Label("C"));
                pane.setContent(new Label("D"));
            }
        });
        assertTrue(settled.await(10, TimeUnit.SECONDS), "transitions did not settle");
        onFx(() -> {
            List<String> expected = List.of(
                    "CLOSING:A", "OPENING:B", "transitioning=true",
                    "transitioning=false", "CLOSED:A", "OPENED:B",
                    "CLOSING:B", "OPENING:C", "transitioning=true",
                    "transitioning=false", "CLOSED:B", "OPENED:C",
                    "CLOSING:C", "OPENING:D", "transitioning=true",
                    "transitioning=false", "CLOSED:C", "OPENED:D");
            for (int i = 0; i < presets.size(); i++) {
                String name = presets.get(i).getClass().getSimpleName();
                List<String> log = recorders.get(i).take();
                assertEquals(expected, log, name);
                assertEquals("D", visibleText(panes.get(i)), name);
                assertFalse(panes.get(i).isTransitioning(), name);
            }
        });
    }

    // ==================== Helpers ====================

    private static void onFx(Runnable body) throws Exception {
        FutureTask<Void> task = new FutureTask<>(body, null);
        Platform.runLater(task);
        try {
            task.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private static StackPane layOut(Node node) {
        StackPane root = new StackPane(node);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
        return root;
    }

    private static RXTransitionPane laidOutPane(Node content) {
        RXTransitionPane pane = new RXTransitionPane(content);
        layOut(pane);
        return pane;
    }

    private static String text(Node node) {
        return ((Label) node).getText();
    }

    private static String describe(RXTransitionPaneEvent event) {
        return event.getEventType().getName().substring(TYPE_PREFIX.length())
                + ":" + text(event.getContent());
    }

    private static String stateAt(RXTransitionPaneEvent event) {
        Node content = event.getContent();
        RXTransitionPane pane = event.getPane();
        boolean inPane = false;
        for (Parent parent = content.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == pane) {
                inPane = true;
                break;
            }
        }
        return describe(event) + " t=" + pane.isTransitioning() + " in=" + inPane
                + " page=" + content.getParent().isVisible() + " own=" + content.isVisible();
    }

    private static String visibleText(RXTransitionPane pane) {
        StackPane contentPane = (StackPane) pane.lookup(".content-pane");
        String shown = null;
        for (Node page : contentPane.getChildren()) {
            if (page.isVisible() && !((StackPane) page).getChildren().isEmpty()) {
                assertNull(shown, "more than one page is visible");
                shown = text(((StackPane) page).getChildren().get(0));
            }
        }
        return shown;
    }

    private static Node pageContent(Node page) {
        StackPane wrapper = (StackPane) page;
        return wrapper.getChildren().isEmpty() ? null : wrapper.getChildren().get(0);
    }

    // Every concrete Anim* preset with a public no-argument constructor that
    // a two-page, single-display host can play, plus AnimAround behind an
    // AnimSelector, which lets it past the page-count gate.
    private static List<PageAnimation> applicablePresets() throws Exception {
        URL url = AnimFade.class.getResource("AnimFade.class");
        File[] files = new File(url.toURI()).getParentFile().listFiles();
        List<PageAnimation> presets = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("Anim") || !name.endsWith(".class") || name.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(AnimFade.class.getPackageName() + "."
                    + name.substring(0, name.length() - ".class".length()));
            if (!PageAnimation.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            Constructor<?> constructor;
            try {
                constructor = type.getConstructor();
            } catch (NoSuchMethodException needsArguments) {
                continue;
            }
            PageAnimation preset = (PageAnimation) constructor.newInstance();
            if (!preset.isMultiPageDisplay() && preset.getMinimumPageCount() <= 2) {
                presets.add(preset);
            }
        }
        AnimAround around = new AnimAround();
        presets.add(new AnimSelector((from, to) -> around));
        return presets;
    }

    /** Records the pane's content events and transitioning flips, in order. */
    private static final class Recorder {

        private final List<String> log = new ArrayList<>();

        Recorder(RXTransitionPane pane) {
            pane.addEventHandler(RXTransitionPaneEvent.ANY, e -> log.add(describe(e)));
            pane.transitioningProperty().addListener(
                    (observable, was, is) -> log.add("transitioning=" + is));
        }

        List<String> take() {
            List<String> copy = List.copyOf(log);
            log.clear();
            return copy;
        }
    }

    /**
     * Plays until the test ends it, either naturally or with jumpToEnd; fires
     * none of the context's lifecycle callbacks.
     */
    private static final class ManualAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        boolean multiPageDisplay;
        private PauseTransition running;
        private Runnable finishAction;

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            incoming.setVisible(true);
            Runnable finish = () -> {
                outgoing.setVisible(false);
                incoming.setVisible(true);
            };
            finishAction = finish;
            PauseTransition pause = new PauseTransition(Duration.hours(1.0));
            pause.setOnFinished(e -> finish.run());
            running = pause;
            return pause;
        }

        // What a pulse does when the animation reaches its end.
        void finishNaturally() {
            PauseTransition pause = running;
            running = null;
            finishAction = null;
            pause.getOnFinished().handle(new ActionEvent());
            pause.stop();
        }

        @Override
        public void jumpToEnd() {
            if (running != null) {
                running.stop();
                running = null;
            }
            if (finishAction != null) {
                finishAction.run();
                finishAction = null;
            }
        }

        @Override
        public void clearEffects(TransitionContext context) {
        }

        @Override
        public void dispose() {
            if (running != null) {
                running.stop();
                running = null;
            }
        }

        @Override
        public boolean isMultiPageDisplay() {
            return multiPageDisplay;
        }
    }

    /** Completes inside play(), as AnimNone does. */
    private static final class ZeroLengthAnimation implements PageAnimation {

        @Override
        public Animation getAnimation(TransitionContext context) {
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            PauseTransition pause = new PauseTransition(Duration.ZERO);
            pause.setOnFinished(e -> {
                outgoing.setVisible(false);
                incoming.setVisible(true);
            });
            return pause;
        }

        @Override
        public void jumpToEnd() {
        }

        @Override
        public void clearEffects(TransitionContext context) {
        }

        @Override
        public void dispose() {
        }
    }
}
