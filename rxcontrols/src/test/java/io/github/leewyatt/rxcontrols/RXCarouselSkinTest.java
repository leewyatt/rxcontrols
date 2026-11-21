package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.carousel.SegmentedProgressNavigator;
import io.github.leewyatt.rxcontrols.skins.RXCarouselSkin;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how a carousel skin takes the carousel over and lets it go: only an
 * installed skin adds nodes and fires events, a replacement continues the
 * replaced skin's pages without events, and a removed skin completes the
 * started pairs, closes the current page and empties its cache.
 */
public class RXCarouselSkinTest {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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

    // ==================== Never installed ====================

    @Test
    public void ignoredSkinOfTheInstalledClassStaysInert() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(4);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimationDuration(Duration.ZERO);
            List<String> events = record(carousel);
            int children = carousel.getChildrenUnmodifiable().size();
            Parent wrapper = pages.get(0).getParent();

            RXCarouselSkin ignored = new RXCarouselSkin(carousel);
            carousel.setSkin(ignored);

            assertNotSame(ignored, carousel.getSkin());
            assertEquals(children, carousel.getChildrenUnmodifiable().size());
            assertSame(wrapper, pages.get(0).getParent());
            assertEquals(List.of(), events);

            Event.fireEvent(carousel, keyPressed(KeyCode.RIGHT));

            assertEquals(1, carousel.getSelectedIndex());
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), openClose(events));
        });
    }

    @Test
    public void ignoredSkinOfTheInstalledClassCanBeCollected() throws Exception {
        List<WeakReference<RXCarouselSkin>> ignored = new ArrayList<>();
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = laidOutCarousel(labels(3));
            RXCarouselSkin skin = new RXCarouselSkin(carousel[0]);
            carousel[0].setSkin(skin);
            ignored.add(new WeakReference<>(skin));
        });
        for (int i = 0; i < 50 && ignored.get(0).get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(ignored.get(0).get(), "an ignored skin must not stay reachable from the carousel");
        onFx(() -> carousel[0].setSkin(null));
    }

    @Test
    public void disposingASkinThatWasNeverInstalledLeavesTheCarouselAlone() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(4));
            ManualAnimation animation = new ManualAnimation();
            carousel.setAnimation(animation);

            new RXCarouselSkin(carousel).dispose();

            assertEquals(0, animation.disposeCalls);
            assertEquals(0, selectedDot(carousel));
            carousel.goToPage(1);
            assertEquals(1, animation.contexts.size());
            assertTrue(carousel.isPageTransitioning());
            assertEquals(1, selectedDot(carousel));
            carousel.setSkin(null);
        });
    }

    // ==================== Replacement ====================

    @Test
    public void replacingTheSkinWhileIdleFiresNothingAndKeepsThePages() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(4);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimationDuration(Duration.ZERO);
            List<String> events = record(carousel);
            int children = carousel.getChildrenUnmodifiable().size();

            // A skin of the installed class is ignored by setSkin, hence the subclass.
            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertEquals(List.of(), events);
            assertEquals(children, carousel.getChildrenUnmodifiable().size());
            assertEquals(List.of("P0"), shownPages(carousel));
            assertEquals(4, carousel.lookupAll(".dot").size());
            assertEquals(0, selectedDot(carousel));

            carousel.next();

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), openClose(events));
            assertEquals(List.of("P1"), shownPages(carousel));
            assertEquals(1, selectedDot(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void replacingTheSkinKeepsTheSegmentedNavigatorDrawing() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels(4).toArray(new Node[0]));
            carousel.setNavigator(new SegmentedProgressNavigator());
            layOut(carousel);

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            RXSegmentedStepIndicator indicator =
                    (RXSegmentedStepIndicator) carousel.lookup(".rx-segmented-step-indicator");
            assertTrue(indicator != null, "the navigator lost its indicator");
            assertEquals(4, indicator.getStepCount());
            assertEquals(1, carousel.lookupAll(".carousel-navigator").size());
            carousel.setSkin(null);
        });
    }

    @Test
    public void replacingTheSkinMidTransitionCompletesThePairsOnce() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(4);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimation(new AnimFade());
            carousel.setAnimationDuration(Duration.seconds(30.0));
            List<String> events = record(carousel);
            carousel.goToPage(1);
            assertEquals(List.of("CLOSING:0", "OPENING:1"), openClose(events));
            events.clear();

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertEquals(List.of("CLOSED:0", "OPENED:1"), openClose(events));
            assertFalse(carousel.isPageTransitioning());
            assertEquals(List.of("P1"), shownPages(carousel));
            events.clear();

            carousel.goToPage(2, false);
            assertEquals(List.of("CLOSING:1", "CLOSED:1", "OPENING:2", "OPENED:2"), openClose(events));
            carousel.setSkin(null);
        });
    }

    @Test
    public void factoryReplacedWhileAReplacedSkinCompletesItsPairsReachesTheNewSkin() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(4));
            List<Label> fresh = List.of(new Label("N0"), new Label("N1"), new Label("N2"), new Label("N3"));
            carousel.setAnimation(new AnimFade());
            carousel.setAnimationDuration(Duration.seconds(30.0));
            carousel.goToPage(1);
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, e -> {
                if (armed[0] && e.getPageIndex() == 1) {
                    armed[0] = false;
                    carousel.setPageFactory(fresh::get);
                }
            });

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertEquals(List.of("N1"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void navigatorCodeRunDuringATakeOverOpensNoPageOfItsOwn() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPageCount(3);
            carousel.setPageFactory(index -> new Label("P" + index));
            ScriptedNavigator navigator = new ScriptedNavigator();
            carousel.setNavigator(navigator);
            layOut(carousel);
            Node first = shownNode(carousel);
            List<String> events = record(carousel);
            navigator.onCreate = (owner, node) -> owner.getProperties().put("navigator-node", node);

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertEquals(List.of(), events);
            assertSame(first, shownNode(carousel));
            carousel.setSkin(null);
            assertEquals(List.of("CLOSING:0", "CLOSED:0"), openClose(events));
        });
    }

    @Test
    public void cacheClearRequestedByNavigatorCodeDuringATakeOverIsCarriedOut() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPageCount(3);
            carousel.setPageFactory(index -> new Label("P" + index));
            ScriptedNavigator navigator = new ScriptedNavigator();
            carousel.setNavigator(navigator);
            layOut(carousel);
            List<String> events = record(carousel);
            navigator.onCreate = (owner, node) -> owner.clearPageCache(1);

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertEquals(List.of("EVICTED:1", "CACHED:1"), events);
            carousel.setSkin(null);
        });
    }

    @Test
    public void skinReplacedByAFactoryListenerDropsThePagesOfTheOldFactory() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels(3).toArray(new Node[0]));
            List<Label> fresh = List.of(new Label("N0"), new Label("N1"), new Label("N2"));
            boolean[] armed = {true};
            // Registered before the skin exists, so it runs before the skin's own factory listener.
            carousel.pageFactoryProperty().addListener(observable -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.setSkin(new RXCarouselSkin(carousel) {
                    });
                }
            });
            layOut(carousel);

            carousel.setPageFactory(fresh::get);
            layOutAgain(carousel);

            assertEquals(List.of("N0"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void cacheClearRequestedWhileAnEvictionClosesThePageSurvivesASkinReplacement() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPageCount(3);
            carousel.setPageFactory(index -> new Label("P" + index));
            layOut(carousel);
            Node first = shownNode(carousel);
            List<String> events = record(carousel);
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.CLOSING, e -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.setSkin(new RXCarouselSkin(carousel) {
                    });
                }
            });

            carousel.clearPageCache(0);
            layOutAgain(carousel);

            assertTrue(events.contains("EVICTED:0"), events::toString);
            assertNotSame(first, shownNode(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void cacheClearRequestedWhileTheReplacedSkinCompletesItsPairsReachesTheNewSkin() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(3));
            carousel.setAnimation(new AnimFade());
            carousel.setAnimationDuration(Duration.seconds(30.0));
            carousel.goToPage(1);
            List<String> events = record(carousel);
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.CLOSED, e -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.clearPageCache();
                }
            });

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            assertTrue(events.containsAll(List.of("EVICTED:0", "EVICTED:1", "EVICTED:2")), events::toString);
            assertEquals(List.of("CLOSED:0", "OPENED:1", "CLOSING:1", "CLOSED:1", "OPENING:1", "OPENED:1"),
                    openClose(events));
            assertEquals(List.of("P1"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void cacheClearedJustBeforeTheSkinIsReplacedIsClearedByTheNewSkin() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimationDuration(Duration.ZERO);
            List<String> events = record(carousel);
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, e -> {
                if (armed[0] && e.getPageIndex() == 1) {
                    armed[0] = false;
                    carousel.clearPageCache();
                    carousel.setSkin(new RXCarouselSkin(carousel) {
                    });
                }
            });

            carousel.goToPage(1);
            layOutAgain(carousel);

            List<String> afterOpened = events.subList(events.indexOf("OPENED:1") + 1, events.size());
            assertTrue(afterOpened.containsAll(List.of("EVICTED:0", "EVICTED:1", "EVICTED:2")), afterOpened::toString);
            assertEquals(List.of("CLOSING:1", "CLOSED:1", "OPENING:1", "OPENED:1"), openClose(afterOpened));
            assertEquals(List.of("P1"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void replacingTheSkinWhileIdleKeepsTheNextPageChangeAnimated() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(3));
            ManualAnimation animation = new ManualAnimation();
            carousel.setAnimation(animation);
            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            layOutAgain(carousel);

            carousel.goToPage(1);

            assertEquals(1, animation.contexts.size());
            assertTrue(carousel.isPageTransitioning());
            carousel.setSkin(null);
        });
    }

    @Test
    public void pageChangedWhileTheSkinIsReplacedIsCutToByTheNewSkin() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimationDuration(Duration.ZERO);
            List<String> events = record(carousel);
            // Notified between the replaced skin's dispose and the new skin's take-over.
            InvalidationListener jump = observable -> carousel.goToPage(2);
            carousel.skinProperty().addListener(jump);

            carousel.setSkin(new RXCarouselSkin(carousel) {
            });
            carousel.skinProperty().removeListener(jump);
            layOutAgain(carousel);

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:2", "OPENED:2"), openClose(events));
            assertEquals(List.of("P2"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    // ==================== Removal ====================

    @Test
    public void removingTheSkinClosesTheCurrentPageAndEmptiesTheCache() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            List<String> events = record(carousel);

            carousel.setSkin(null);

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:2"), events);
            assertEquals(0, carousel.getChildrenUnmodifiable().size());
            events.clear();

            carousel.setSkin(new RXCarouselSkin(carousel));
            layOutAgain(carousel);

            assertEquals(List.of("OPENING:0", "OPENED:0"), openClose(events));
            assertTrue(events.contains("CACHED:0"));
            assertEquals(List.of("P0"), shownPages(carousel));
            carousel.setSkin(null);
        });
    }

    @Test
    public void removingTheSkinMidTransitionCompletesThePairsAndClosesThePage() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimation(new AnimFade());
            carousel.setAnimationDuration(Duration.seconds(30.0));
            List<String> events = record(carousel);
            carousel.goToPage(1);
            events.clear();

            carousel.setSkin(null);

            assertEquals(List.of("CLOSED:0", "OPENED:1", "CLOSING:1", "CLOSED:1",
                    "EVICTED:0", "EVICTED:1", "EVICTED:2"), events);
            assertFalse(carousel.isPageTransitioning());
            for (Label page : pages) {
                assertNull(page.getParent(), page.getText());
            }
        });
    }

    @Test
    public void skinRemovedFromAClosingHandlerFiresNothingAfterwards() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        List<List<String>> recorded = new ArrayList<>();
        onFx(() -> {
            carousel[0] = laidOutCarousel(labels(4));
            carousel[0].setAnimation(new AnimFade());
            carousel[0].setAnimationDuration(Duration.millis(60.0));
            recorded.add(record(carousel[0]));
            carousel[0].setOnPageClosing(e -> carousel[0].setSkin(null));
        });
        List<String> events = recorded.get(0);
        onFx(() -> {
            carousel[0].goToPage(1);

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:3"), events);
            assertFalse(carousel[0].isPageTransitioning());
        });
        Thread.sleep(300L);
        onFx(() -> {
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:3"), events);
            assertFalse(carousel[0].isPageTransitioning());
        });
    }

    // ==================== Helpers ====================

    private static List<Label> labels(int count) {
        List<Label> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(new Label("P" + i));
        }
        return pages;
    }

    private static RXCarousel laidOutCarousel(List<Label> pages) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(pages.toArray(new Node[0]));
        layOut(carousel);
        return carousel;
    }

    private static void layOut(Node node) {
        StackPane root = new StackPane(node);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
    }

    private static void layOutAgain(RXCarousel carousel) {
        Parent root = carousel.getParent();
        root.applyCss();
        root.layout();
    }

    // Registered per type: a handler for a type runs before the handlers for
    // its super type, so recording on ANY would log after a setOnPageXxx
    // handler of the same event.
    private static List<String> record(RXCarousel carousel) {
        List<String> events = new ArrayList<>();
        for (EventType<RXPageLifecycleEvent> type : List.of(RXPageLifecycleEvent.CACHED,
                RXPageLifecycleEvent.OPENING, RXPageLifecycleEvent.OPENED, RXPageLifecycleEvent.CLOSING,
                RXPageLifecycleEvent.CLOSED, RXPageLifecycleEvent.EVICTED)) {
            carousel.addEventHandler(type, e -> events.add(describe(e)));
        }
        return events;
    }

    private static String describe(RXPageLifecycleEvent event) {
        return event.getEventType().getName().substring("RX_PAGE_".length()) + ":" + event.getPageIndex();
    }

    private static List<String> openClose(List<String> events) {
        List<String> filtered = new ArrayList<>();
        for (String event : events) {
            if (!event.startsWith("CACHED") && !event.startsWith("EVICTED")) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private static Node shownNode(RXCarousel carousel) {
        for (Node pane : carousel.lookupAll(".content-pane")) {
            for (Node wrapper : ((Parent) pane).getChildrenUnmodifiable()) {
                if (wrapper.isVisible() && !((Parent) wrapper).getChildrenUnmodifiable().isEmpty()) {
                    return ((Parent) wrapper).getChildrenUnmodifiable().get(0);
                }
            }
        }
        return null;
    }

    // Texts of the page nodes whose wrapper shows in the content pane.
    private static List<String> shownPages(RXCarousel carousel) {
        List<String> shown = new ArrayList<>();
        for (Node pane : carousel.lookupAll(".content-pane")) {
            for (Node wrapper : ((Parent) pane).getChildrenUnmodifiable()) {
                if (wrapper.isVisible() && wrapper.getOpacity() > 0.0) {
                    for (Node node : ((Parent) wrapper).getChildrenUnmodifiable()) {
                        shown.add(((Label) node).getText());
                    }
                }
            }
        }
        return shown;
    }

    private static int selectedDot(RXCarousel carousel) {
        List<Node> dots = new ArrayList<>(carousel.lookupAll(".dot"));
        int selected = -1;
        for (int i = 0; i < dots.size(); i++) {
            if (dots.get(i).getPseudoClassStates().contains(SELECTED)) {
                assertEquals(-1, selected, "more than one dot is selected");
                selected = i;
            }
        }
        return selected;
    }

    private static KeyEvent keyPressed(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

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

    /** A navigator that runs the test's code whenever a skin creates its node. */
    private static final class ScriptedNavigator implements CarouselNavigator {

        BiConsumer<RXCarousel, Node> onCreate = (carousel, node) -> {
        };

        @Override
        public Node createNode(RXCarousel carousel) {
            StackPane node = new StackPane();
            onCreate.accept(carousel, node);
            return node;
        }

        @Override
        public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
        }

        @Override
        public void dispose() {
        }
    }

    /** Plays until the test ends it; fires none of the context's lifecycle callbacks. */
    private static final class ManualAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int disposeCalls;
        private PauseTransition running;
        private Runnable finishAction;

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            incoming.setVisible(true);
            Runnable finish = () -> {
                if (outgoing != null) {
                    outgoing.setVisible(false);
                }
                incoming.setVisible(true);
            };
            finishAction = finish;
            PauseTransition pause = new PauseTransition(Duration.hours(1.0));
            pause.setOnFinished(e -> finish.run());
            running = pause;
            return pause;
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
            disposeCalls++;
            if (running != null) {
                running.stop();
                running = null;
            }
        }
    }
}
