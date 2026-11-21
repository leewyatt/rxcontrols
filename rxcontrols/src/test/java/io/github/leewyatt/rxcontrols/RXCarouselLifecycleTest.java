package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimAround;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the page lifecycle the carousel skin fires: the order of the events
 * and the carousel state at each of them whatever the animation, page changes
 * made from handlers and listeners, interrupted and externally stopped
 * transitions, throwing handlers, and evictions of open pages.
 */
public class RXCarouselLifecycleTest {

    private static final String TYPE_PREFIX = "RX_PAGE_";

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

    // ==================== Sequence ====================

    @Test
    public void animatedChangeFiresTheSequenceAroundThePlay() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);

            carousel.goToPage(1);
            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true"), openClose(recorder.take()));

            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:0", "OPENED:1"), openClose(recorder.take()));
            assertEquals(List.of("P1"), attachedPages(carousel));
            assertEquals(List.of("P1"), shownPages(carousel));
        });
    }

    @Test
    public void eventsSeeThePagesInPlace() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            List<String> states = new ArrayList<>();
            carousel.addEventHandler(RXPageLifecycleEvent.ANY, e -> {
                if (e.getEventType() == RXPageLifecycleEvent.CACHED || e.getEventType() == RXPageLifecycleEvent.EVICTED) {
                    return;
                }
                Parent wrapper = e.getPage().getParent();
                boolean attached = wrapper != null && wrapper.getParent() == carousel.lookup(".content-pane");
                states.add(describe(e) + " t=" + carousel.isPageTransitioning()
                        + " attached=" + attached + " visible=" + (attached && wrapper.isVisible()));
            });

            carousel.goToPage(1);
            animation.finishNaturally();

            assertEquals(List.of(
                    "CLOSING:0 t=false attached=true visible=true",
                    "OPENING:1 t=false attached=true visible=false",
                    "CLOSED:0 t=false attached=true visible=false",
                    "OPENED:1 t=false attached=true visible=true"), states);
        });
    }

    @Test
    public void interruptCompletesThePairsBeforeTheNextChange() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.goToPage(1);

            carousel.goToPage(2);

            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1",
                    "CLOSING:1", "OPENING:2", "transitioning=true"), openClose(recorder.take()));
            assertEquals(2, animation.contexts.size());
            assertEquals(1, animation.running());
        });
    }

    @Test
    public void everyPresetGetsTheSameSequence() throws Exception {
        onFx(() -> {
            List<PageAnimation> presets = presets();
            for (PageAnimation preset : presets) {
                String name = preset.getClass().getSimpleName();
                RXCarousel carousel = laidOutCarousel(4);
                carousel.setAnimation(preset);
                carousel.setAnimationDuration(Duration.seconds(30.0));
                Recorder recorder = new Recorder(carousel);

                carousel.goToPage(1);
                carousel.goToPage(2, false);

                assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                        "transitioning=false", "CLOSED:0", "OPENED:1",
                        "CLOSING:1", "CLOSED:1", "OPENING:2", "OPENED:2"), openClose(recorder.take()), name);
                assertFalse(carousel.isPageTransitioning(), name);
                assertTrue(shownPages(carousel).contains("P2"), name);
                carousel.setSkin(null);
            }
        });
    }

    // ==================== Re-entry ====================

    @Test
    public void pageChangedWhenATransitionEndsWaitsForItToSettle() throws Exception {
        for (String trigger : List.of("OPENED:1", "CLOSED:0", "transitioning=false")) {
            onFx(() -> {
                RXCarousel carousel = laidOutCarousel(4);
                ManualAnimation animation = manual(carousel);
                Recorder recorder = new Recorder(carousel);
                recorder.once(trigger, () -> carousel.goToPage(2));
                carousel.goToPage(1);

                animation.finishNaturally();

                List<String> log = openClose(recorder.take());
                assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                        "transitioning=false", "CLOSED:0", "OPENED:1",
                        "CLOSING:1", "OPENING:2", "transitioning=true"), log, trigger);
                TransitionContext running = animation.contexts.get(1);
                assertSame(page(carousel, 1).getParent(), running.getCurrentPage(), trigger);
                assertSame(page(carousel, 2).getParent(), running.getNextPage(), trigger);
                assertEquals(List.of("P1", "P2"), attachedPages(carousel), trigger);
                assertTrue(carousel.isPageTransitioning(), trigger);
            });
        }
    }

    @Test
    public void pagingOnFromAHandlerKeepsTheNewPageWithCacheDistanceZero() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            carousel.setCacheDistance(0);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            recorder.once("OPENED:1", () -> carousel.goToPage(2));
            carousel.goToPage(1);
            animation.finishNaturally();

            animation.finishNaturally();

            assertEquals(List.of("P2"), shownPages(carousel));
            assertEquals(List.of("P2"), attachedPages(carousel));
            List<String> log = recorder.take();
            assertTrue(log.indexOf("OPENED:2") >= 0, log::toString);
            assertFalse(log.contains("EVICTED:2"), log::toString);
        });
    }

    @Test
    public void pageChangedFromAClosingHandlerReplacesTheTarget() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            recorder.once("CLOSING:0", () -> carousel.goToPage(2));

            carousel.goToPage(1);

            assertEquals(List.of("CLOSING:0", "OPENING:2", "transitioning=true"), openClose(recorder.take()));
            assertEquals(1, animation.contexts.size());
            assertSame(page(carousel, 2).getParent(), animation.contexts.get(0).getNextPage());
            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:0", "OPENED:2"), openClose(recorder.take()));
            assertEquals(List.of("P2"), shownPages(carousel));
        });
    }

    @Test
    public void pageChangedFromAnOpeningHandlerAbandonsThePageThatNeverShowed() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            recorder.once("OPENING:1", () -> carousel.goToPage(2));

            carousel.goToPage(1);

            assertEquals(List.of("CLOSING:0", "OPENING:1", "OPENED:1", "CLOSING:1", "CLOSED:1",
                    "OPENING:2", "transitioning=true"), openClose(recorder.take()));
            assertEquals(1, animation.contexts.size());
            assertSame(page(carousel, 0).getParent(), animation.contexts.get(0).getCurrentPage());
            assertSame(page(carousel, 2).getParent(), animation.contexts.get(0).getNextPage());
            assertFalse(attachedPages(carousel).contains("P1"));
            animation.finishNaturally();
            assertEquals(List.of("transitioning=false", "CLOSED:0", "OPENED:2"), openClose(recorder.take()));
            assertEquals(List.of("P2"), shownPages(carousel));
        });
    }

    @Test
    public void pagesReplacedFromAClosingHandlerNeverOpenTheReplacedPage() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            manual(carousel);
            List<String> opened = new ArrayList<>();
            carousel.addEventHandler(RXPageLifecycleEvent.ANY, e -> {
                if (e.getEventType() == RXPageLifecycleEvent.OPENING
                        || e.getEventType() == RXPageLifecycleEvent.OPENED) {
                    opened.add(describe(e) + "=" + ((Label) e.getPage()).getText());
                }
            });
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.CLOSING, e -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.setPages(new Label("N0"), new Label("N1"), new Label("N2"), new Label("N3"));
                }
            });

            carousel.goToPage(1);

            assertEquals(List.of("OPENING:1=N1", "OPENED:1=N1"), opened);
            assertEquals(List.of("N1"), shownPages(carousel));
        });
    }

    @Test
    public void closingHandlerReturningToThePageReopensItWithoutAnimation() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            recorder.once("CLOSING:0", () -> carousel.goToPage(0));

            carousel.goToPage(1);

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:0", "OPENED:0"), openClose(recorder.take()));
            assertEquals(0, animation.contexts.size());
            assertEquals(List.of("P0"), shownPages(carousel));
        });
    }

    // ==================== External stop ====================

    @Test
    public void externalStopCompletesThePairsOnTheNextTurn() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        Recorder[] recorder = new Recorder[1];
        onFx(() -> {
            carousel[0] = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel[0]);
            recorder[0] = new Recorder(carousel[0]);
            carousel[0].goToPage(1);

            animation.jumpToEnd();

            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true"), openClose(recorder[0].take()));
        });
        onFx(() -> {
            assertEquals(List.of("transitioning=false", "CLOSED:0", "OPENED:1"), openClose(recorder[0].take()));
            assertEquals(List.of("P1"), attachedPages(carousel[0]));
        });
    }

    @Test
    public void pageChangeBeforeTheDeferredCompletionCompletesFirst() throws Exception {
        Recorder[] recorder = new Recorder[1];
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            recorder[0] = new Recorder(carousel);
            carousel.goToPage(1);
            animation.jumpToEnd();

            carousel.goToPage(2);

            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1",
                    "CLOSING:1", "OPENING:2", "transitioning=true"), openClose(recorder[0].take()));
        });
        onFx(() -> assertEquals(List.of(), openClose(recorder[0].take())));
    }

    @Test
    public void resizeGuardStopIsCompletedAndLeavesOnlyTheShownPage() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        Recorder[] recorder = new Recorder[1];
        onFx(() -> {
            carousel[0] = laidOutCarousel(4);
            carousel[0].setAnimation(new AnimAround());
            carousel[0].setAnimationDuration(Duration.seconds(30.0));
            recorder[0] = new Recorder(carousel[0]);
            carousel[0].goToPage(1);
            assertTrue(carousel[0].isPageTransitioning());

            Parent root = carousel[0].getParent();
            ((StackPane) root).resize(420.0, 260.0);
            root.layout();
        });
        onFx(() -> {
            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1"), openClose(recorder[0].take()));
            assertFalse(carousel[0].isPageTransitioning());
            assertEquals(List.of("P1"), attachedPages(carousel[0]));
            carousel[0].setSkin(null);
        });
    }

    // ==================== Exceptions ====================

    @Test
    public void throwingOpenedHandlerIsReportedAndTheTransitionStillSettles() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, e -> {
                throw new IllegalStateException("page failed to start");
            });
            carousel.goToPage(1);
            List<Throwable> reported = new ArrayList<>();

            withUncaughtHandler(reported, animation::finishNaturally);

            assertEquals(1, reported.size());
            assertEquals("page failed to start", reported.get(0).getMessage());
            assertFalse(carousel.isPageTransitioning());
            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1"), openClose(recorder.take()));
            assertEquals(List.of("P1"), shownPages(carousel));
        });
    }

    @Test
    public void throwingClosingHandlerStillPlaysTheTransition() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.addEventHandler(RXPageLifecycleEvent.CLOSING, e -> {
                throw new IllegalStateException("page refused to close");
            });
            List<Throwable> reported = new ArrayList<>();

            withUncaughtHandler(reported, () -> carousel.goToPage(1));

            assertEquals(1, reported.size());
            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true"), openClose(recorder.take()));
            assertEquals(1, animation.contexts.size());
            animation.finishNaturally();
            assertEquals(List.of("P1"), shownPages(carousel));
        });
    }

    @Test
    public void throwingClosingHandlerStillCutsToThePage() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            Recorder recorder = new Recorder(carousel);
            carousel.addEventHandler(RXPageLifecycleEvent.CLOSING, e -> {
                throw new IllegalStateException("page refused to close");
            });
            List<Throwable> reported = new ArrayList<>();

            withUncaughtHandler(reported, () -> carousel.goToPage(1, false));

            assertEquals(1, reported.size());
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), openClose(recorder.take()));
            assertEquals(List.of("P1"), shownPages(carousel));
        });
    }

    // ==================== Stops from listeners ====================

    @Test
    public void pageChangedWhenAnInterruptedTransitionStopsLeavesNoStaleAnimation() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.goToPage(1);
            recorder.once("transitioning=false", () -> carousel.goToPage(3));

            carousel.goToPage(2);

            assertEquals(1, animation.running());
            assertEquals(3, carousel.getSelectedIndex());
            assertSame(page(carousel, 3).getParent(), animation.contexts.get(animation.contexts.size() - 1).getNextPage());
            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1",
                    "CLOSING:1", "OPENING:3", "transitioning=true"), openClose(recorder.take()));
        });
    }

    @Test
    public void pageChangedWhenAnExternallyStoppedTransitionCompletesLeavesNoStaleAnimation() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        ManualAnimation[] animation = new ManualAnimation[1];
        onFx(() -> {
            carousel[0] = laidOutCarousel(4);
            animation[0] = manual(carousel[0]);
            Recorder recorder = new Recorder(carousel[0]);
            carousel[0].goToPage(1);
            recorder.once("transitioning=false", () -> carousel[0].goToPage(2));
            animation[0].jumpToEnd();
        });
        onFx(() -> carousel[0].goToPage(3));
        onFx(() -> {
            assertEquals(1, animation[0].running());
            assertEquals(3, animation[0].contexts.size());
            assertSame(page(carousel[0], 3).getParent(),
                    animation[0].contexts.get(2).getNextPage());
        });
    }

    // ==================== Structure changes ====================

    @Test
    public void shrinkingThePageCountMidTransitionOpensTheNewPageOnce() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(5);
            manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.goToPage(4);

            carousel.setPageCount(3);

            List<String> log = openClose(recorder.take());
            assertEquals(List.of("CLOSING:0", "OPENING:4", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:4", "CLOSING:4", "CLOSED:4",
                    "OPENING:2", "OPENED:2"), log);
            assertEquals(2, carousel.getSelectedIndex());
            assertEquals(List.of("P2"), shownPages(carousel));
        });
    }

    @Test
    public void clearingTheCacheClosesTheOpenPageBeforeEvictingIt() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            Recorder recorder = new Recorder(carousel);

            carousel.clearPageCache();

            List<String> log = recorder.take();
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:3",
                    "CACHED:0", "OPENING:0", "OPENED:0"), log.subList(0, 8));
            assertEquals(List.of("P0"), shownPages(carousel));
        });
    }

    @Test
    public void clearingTheOutgoingPageMidTransitionClosesItOnce() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            manual(carousel);
            Recorder recorder = new Recorder(carousel);
            carousel.goToPage(1);

            carousel.clearPageCache(0);

            assertEquals(List.of("CLOSING:0", "OPENING:1", "transitioning=true",
                    "transitioning=false", "CLOSED:0", "OPENED:1", "EVICTED:0"), withoutCached(recorder.take()));
            assertEquals(List.of("P1"), shownPages(carousel));
        });
    }

    @Test
    public void replacingTheFactoryClosesTheOpenPageBeforeEvictingIt() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            Recorder recorder = new Recorder(carousel);

            carousel.setPageFactory(index -> new Label("N" + index));

            List<String> log = recorder.take();
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:3",
                    "CACHED:0", "OPENING:0", "OPENED:0"), log.subList(0, 8));
            assertEquals(List.of("N0"), shownPages(carousel));
        });
    }

    // ==================== Dispatch scope ====================

    @Test
    public void eventsReachTheCarouselsHandlersAndOnlyFiltersAbove() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(3);
            carousel.setAnimationDuration(Duration.ZERO);
            Parent root = carousel.getParent();
            List<String> own = new ArrayList<>();
            List<String> property = new ArrayList<>();
            List<String> ancestorHandler = new ArrayList<>();
            List<String> ancestorFilter = new ArrayList<>();
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, e -> own.add(describe(e)));
            carousel.setOnPageOpened(e -> property.add(describe(e)));
            root.addEventHandler(RXPageLifecycleEvent.ANY, e -> ancestorHandler.add(describe(e)));
            root.addEventFilter(RXPageLifecycleEvent.OPENED, e -> ancestorFilter.add(describe(e)));

            carousel.goToPage(1);

            assertEquals(List.of("OPENED:1"), own);
            assertEquals(List.of("OPENED:1"), property);
            assertEquals(List.of(), ancestorHandler);
            assertEquals(List.of("OPENED:1"), ancestorFilter);
        });
    }

    @Test
    public void nestedCarouselEventsStayWithTheInnerCarousel() throws Exception {
        onFx(() -> {
            RXCarousel inner = new RXCarousel();
            inner.setPages(new Label("I0"), new Label("I1"));
            RXCarousel outer = new RXCarousel();
            outer.setPages(new StackPane(inner), new Label("P1"));
            StackPane root = new StackPane(outer);
            new Scene(root, 320.0, 200.0);
            root.resize(320.0, 200.0);
            root.applyCss();
            root.layout();
            List<String> outerSaw = new ArrayList<>();
            List<String> innerSaw = new ArrayList<>();
            outer.addEventHandler(RXPageLifecycleEvent.ANY, e -> outerSaw.add(describe(e)));
            inner.addEventHandler(RXPageLifecycleEvent.ANY, e -> innerSaw.add(describe(e)));

            inner.goToPage(1, false);

            assertEquals(List.of(), outerSaw);
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), openClose(innerSaw));
        });
    }

    // ==================== Skin removal ====================

    @Test
    public void skinRemovedFromAClosingHandlerPlaysNoAnimation() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(4);
            ManualAnimation animation = manual(carousel);
            carousel.setOnPageClosing(e -> carousel.setSkin(null));

            carousel.goToPage(1);

            assertEquals(0, animation.contexts.size());
            assertFalse(carousel.isPageTransitioning());
        });
    }

    // ==================== Helpers ====================

    private static RXCarousel laidOutCarousel(int count) {
        List<Node> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(new Label("P" + i));
        }
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(pages.toArray(new Node[0]));
        StackPane root = new StackPane(carousel);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
        return carousel;
    }

    private static ManualAnimation manual(RXCarousel carousel) {
        ManualAnimation animation = new ManualAnimation();
        carousel.setAnimation(animation);
        return animation;
    }

    private static Node page(RXCarousel carousel, int index) {
        return carousel.getPageFactory().call(index);
    }

    private static List<String> attachedPages(RXCarousel carousel) {
        List<String> attached = new ArrayList<>();
        for (Node wrapper : ((Parent) carousel.lookup(".content-pane")).getChildrenUnmodifiable()) {
            for (Node node : ((Parent) wrapper).getChildrenUnmodifiable()) {
                attached.add(((Label) node).getText());
            }
        }
        attached.sort(null);
        return attached;
    }

    private static List<String> shownPages(RXCarousel carousel) {
        List<String> shown = new ArrayList<>();
        for (Node wrapper : ((Parent) carousel.lookup(".content-pane")).getChildrenUnmodifiable()) {
            if (wrapper.isVisible() && wrapper.getOpacity() > 0.0) {
                for (Node node : ((Parent) wrapper).getChildrenUnmodifiable()) {
                    shown.add(((Label) node).getText());
                }
            }
        }
        shown.sort(null);
        return shown;
    }

    private static String describe(RXPageLifecycleEvent event) {
        return event.getEventType().getName().substring(TYPE_PREFIX.length()) + ":" + event.getPageIndex();
    }

    private static List<String> openClose(List<String> log) {
        List<String> filtered = new ArrayList<>();
        for (String entry : log) {
            if (!entry.startsWith("CACHED") && !entry.startsWith("EVICTED")) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<String> withoutCached(List<String> log) {
        List<String> filtered = new ArrayList<>();
        for (String entry : log) {
            if (!entry.startsWith("CACHED")) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static void withUncaughtHandler(List<Throwable> errors, Runnable body) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
        thread.setUncaughtExceptionHandler((t, e) -> errors.add(e));
        try {
            body.run();
        } finally {
            thread.setUncaughtExceptionHandler(previous);
        }
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

    // Every concrete Anim* preset with a public no-argument constructor, plus
    // the multi-page AnimAround.
    private static List<PageAnimation> presets() {
        try {
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
                if (preset.getMinimumPageCount() <= 4) {
                    presets.add(preset);
                }
            }
            presets.add(new AnimAround(true));
            return presets;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Records the carousel's page events and pageTransitioning flips, in order. */
    private static final class Recorder {

        private final List<String> log = new ArrayList<>();
        private final List<String> triggers = new ArrayList<>();
        private final List<Runnable> actions = new ArrayList<>();

        Recorder(RXCarousel carousel) {
            // Per type: a handler for a type runs before the handlers for its
            // super type, so recording on ANY would log after a setOnPageXxx
            // handler of the same event.
            for (EventType<RXPageLifecycleEvent> type : List.of(RXPageLifecycleEvent.CACHED,
                    RXPageLifecycleEvent.OPENING, RXPageLifecycleEvent.OPENED, RXPageLifecycleEvent.CLOSING,
                    RXPageLifecycleEvent.CLOSED, RXPageLifecycleEvent.EVICTED)) {
                carousel.addEventHandler(type, e -> add(describe(e)));
            }
            carousel.pageTransitioningProperty().addListener(
                    (observable, was, is) -> add("transitioning=" + is));
        }

        // Runs the action right after the given entry is logged for the first time.
        void once(String entry, Runnable action) {
            triggers.add(entry);
            actions.add(action);
        }

        private void add(String entry) {
            log.add(entry);
            int at = triggers.indexOf(entry);
            if (at >= 0) {
                triggers.remove(at);
                actions.remove(at).run();
            }
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
        private final List<PauseTransition> animations = new ArrayList<>();
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
            animations.add(pause);
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

        int running() {
            int count = 0;
            for (PauseTransition animation : animations) {
                if (animation.getStatus() != Animation.Status.STOPPED) {
                    count++;
                }
            }
            return count;
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
    }
}
