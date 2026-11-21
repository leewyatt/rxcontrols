package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimAround;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.skins.RXCarouselSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * Tests the pages of the carousel: each page node sits in a page wrapper of
 * the skin, animations and the skin change the wrappers only, the skin lets
 * go of the page nodes it evicts or leaves behind, changes to a page list or
 * to setPages reach the cached pages, and the selected index stays within the
 * pages.
 */
public class RXCarouselPagesTest {

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

    // ==================== Wrappers ====================

    @Test
    public void pageNodeSitsInAPageWrapperOfTheContentPane() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(pages.toArray(new Node[0]));
            List<RXPageLifecycleEvent> events = new ArrayList<>();
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, events::add);
            layOut(carousel);

            Parent wrapper = pages.get(0).getParent();
            assertTrue(wrapper.getStyleClass().contains("page"));
            assertSame(carousel.lookup(".content-pane"), wrapper.getParent());
            assertEquals(1, events.size());
            assertSame(pages.get(0), events.get(0).getPage());
        });
    }

    @Test
    public void animationsLeaveThePageNodesOwnPropertiesAlone() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            pages.get(0).setOpacity(0.6);
            pages.get(1).setTranslateX(7.0);
            pages.get(2).visibleProperty().bind(new SimpleBooleanProperty(true));
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimationDuration(Duration.seconds(30.0));
            List<Throwable> errors = new ArrayList<>();
            withUncaughtHandler(errors, () -> {
                carousel.setAnimation(new AnimFade());
                carousel.goToPage(1);
                carousel.goToPage(0);
                carousel.setAnimation(new AnimSlide());
                carousel.goToPage(1);
                carousel.goToPage(2);
            });

            assertEquals(List.of(), errors);
            assertEquals(0.6, pages.get(0).getOpacity());
            assertEquals(7.0, pages.get(1).getTranslateX());
            assertEquals(2, carousel.getSelectedIndex());
            assertTrue(pages.get(2).getParent().isVisible());
            carousel.setSkin(null);
        });
    }

    @Test
    public void multiPageDisplayLaysOutTheWrappersNotThePages() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(4);
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(pages.toArray(new Node[0]));
            carousel.setAnimation(new AnimAround(true));
            layOut(carousel);

            Node side = pages.get(1);
            assertTrue(side.getParent().isVisible());
            assertEquals(0.5, side.getParent().getScaleX());
            assertTrue(side.getParent().getTranslateX() > 0.0);
            assertEquals(1.0, side.getScaleX());
            assertEquals(0.0, side.getTranslateX());
            assertEquals(0.7, pages.get(0).getParent().getScaleX());
            assertEquals(1.0, pages.get(0).getScaleX());
        });
    }

    // ==================== Releasing page nodes ====================

    @Test
    public void evictedPageIsReleasedBeforeItsEvictedEvent() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setCacheDistance(0);
            List<String> evicted = new ArrayList<>();
            carousel.setOnPageEvicted(e -> evicted.add(e.getPageIndex() + ":" + e.getPage().getParent()));

            carousel.goToPage(1, false);

            assertTrue(evicted.contains("0:null"), evicted::toString);
            assertNull(pages.get(0).getParent());
            assertTrue(pages.get(1).getParent().getStyleClass().contains("page"));
        });
    }

    @Test
    public void removingTheSkinReleasesThePageNodes() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            assertTrue(pages.get(1).getParent() != null);

            carousel.setSkin(null);

            for (Label page : pages) {
                assertNull(page.getParent(), page.getText());
            }
        });
    }

    @Test
    public void newSkinShowsThePageWithoutTheLeftoversOfAnInterruptedTransition() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            RXCarousel carousel = laidOutCarousel(pages);
            carousel.setAnimation(new AnimSlide());
            carousel.setAnimationDuration(Duration.seconds(30.0));
            carousel.goToPage(1);

            carousel.setSkin(null);
            carousel.setSkin(new RXCarouselSkin(carousel));
            carousel.getParent().applyCss();
            carousel.getParent().layout();

            Label shown = pages.get(1);
            assertEquals(0.0, shown.getTranslateX());
            assertEquals(1.0, shown.getOpacity());
            assertTrue(shown.isVisible());
            assertTrue(shown.getParent().isVisible());
            carousel.setSkin(null);
        });
    }

    // ==================== Page list ====================

    @Test
    public void addingToThePageListCachesTheNewNeighbourOnly() throws Exception {
        onFx(() -> {
            ObservableList<Node> list = FXCollections.observableArrayList(labels(3));
            RXCarousel carousel = laidOutCarousel(list);
            List<String> events = record(carousel);

            list.add(new Label("P3"));

            assertEquals(List.of("CACHED:3"), events);
            assertEquals(List.of("P0"), shownPages(carousel));
        });
    }

    @Test
    public void removingTheCurrentPageFromTheListCutsToTheNodeNowAtItsIndex() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(3);
            ObservableList<Node> list = FXCollections.observableArrayList(pages);
            RXCarousel carousel = laidOutCarousel(list);
            List<String> events = record(carousel);

            list.remove(0);

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "EVICTED:1", "EVICTED:2",
                    "CACHED:0", "OPENING:0", "OPENED:0", "CACHED:1"), events);
            assertEquals(List.of("P1"), shownPages(carousel));
            assertNull(pages.get(0).getParent());
        });
    }

    @Test
    public void replacingTheCurrentPageInTheListCutsToTheNewNode() throws Exception {
        onFx(() -> {
            ObservableList<Node> list = FXCollections.observableArrayList(labels(3));
            RXCarousel carousel = laidOutCarousel(list);
            List<String> events = record(carousel);

            list.set(0, new Label("X"));

            assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "CACHED:0", "OPENING:0", "OPENED:0"),
                    events);
            assertEquals(List.of("X"), shownPages(carousel));
        });
    }

    @Test
    public void removingACachedPageLeavesTheCurrentPageAlone() throws Exception {
        onFx(() -> {
            ObservableList<Node> list = FXCollections.observableArrayList(labels(4));
            RXCarousel carousel = laidOutCarousel(list);
            List<String> events = record(carousel);
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> list.remove(3));

            assertEquals(List.of(), errors);
            assertEquals(List.of("EVICTED:3", "CACHED:2"), events);
            assertEquals(List.of("P0"), shownPages(carousel));
        });
    }

    @Test
    public void pageListChangedFromTheFirstOpenedHandlerIsFollowed() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(2));
            ObservableList<Node> list = FXCollections.observableArrayList(new Label("A0"), new Label("A1"));
            Label updated = new Label("U0");
            boolean[] armed = {true};
            carousel.addEventHandler(RXPageLifecycleEvent.OPENED, e -> {
                if (armed[0] && e.getPage() == list.get(0)) {
                    armed[0] = false;
                    list.set(0, updated);
                }
            });

            carousel.setPages(list);

            assertEquals(List.of("U0"), shownPages(carousel));
        });
    }

    @Test
    public void factoryReplacedWhileItMakesAPageIsAskedAgain() throws Exception {
        onFx(() -> {
            List<Label> former = List.of(new Label("F0"), new Label("F1"), new Label("F2"));
            List<Label> latest = List.of(new Label("L0"), new Label("L1"), new Label("L2"));
            RXCarousel carousel = new RXCarousel();
            boolean[] armed = {true};
            carousel.setPageCount(3);
            carousel.setPageFactory(index -> {
                if (armed[0] && index == 0) {
                    armed[0] = false;
                    carousel.setPageFactory(latest::get);
                }
                return former.get(index);
            });

            layOut(carousel);

            assertEquals(List.of("L0"), shownPages(carousel));
            assertNull(former.get(0).getParent());
        });
    }

    @Test
    public void clearingASidePageOfAMultiPageLayoutShowsItAgain() throws Exception {
        onFx(() -> {
            List<Label> pages = labels(4);
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(pages.toArray(new Node[0]));
            carousel.setAnimation(new AnimAround(true));
            layOut(carousel);
            assertTrue(pages.get(1).getParent().isVisible());
            List<String> events = record(carousel);

            carousel.clearPageCache(1);

            assertEquals(List.of("EVICTED:1", "CACHED:1"), events);
            Parent wrapper = pages.get(1).getParent();
            assertTrue(wrapper != null && wrapper.isVisible(), "the side page did not come back");
            assertEquals(0.5, wrapper.getScaleX());
        });
    }

    @Test
    public void pageListDoesNotKeepTheCarouselAlive() throws Exception {
        ObservableList<Node> list = FXCollections.observableArrayList(labels(3));
        List<WeakReference<RXCarousel>> carousel = new ArrayList<>();
        onFx(() -> {
            RXCarousel c = new RXCarousel();
            c.setPages(list);
            carousel.add(new WeakReference<>(c));
        });
        for (int i = 0; i < 50 && carousel.get(0).get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(carousel.get(0).get(), "the page list must not keep the carousel reachable");
        assertEquals(3, list.size());
    }

    // ==================== setPages ====================

    @Test
    public void setPagesFromOnePageToTwoKeepsNextUsable() throws Exception {
        for (boolean circular : List.of(true, false)) {
            onFx(() -> {
                RXCarousel carousel = new RXCarousel();
                carousel.setCircular(circular);
                carousel.setPages(new Label("A0"));
                layOut(carousel);
                List<String> events = record(carousel);
                List<Throwable> errors = new ArrayList<>();

                withUncaughtHandler(errors, () -> carousel.setPages(new Label("B0"), new Label("B1")));

                assertEquals(List.of(), errors, "circular " + circular);
                assertEquals(List.of("CLOSING:0", "CLOSED:0", "EVICTED:0", "CACHED:0", "OPENING:0", "OPENED:0",
                        "CACHED:1"), events, "circular " + circular);
                assertFalse(carousel.lookup(".next-button").isDisabled(), "circular " + circular);
                assertEquals(List.of("B0"), shownPages(carousel));
                carousel.next();
                assertEquals(1, carousel.getSelectedIndex(), "circular " + circular);
            });
        }
    }

    @Test
    public void setPagesWithFewerPagesOpensTheClampedPageOnce() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels(5).toArray(new Node[0]));
            layOut(carousel);
            carousel.goToPage(4, false);
            List<String> events = record(carousel);
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(new Label("B0"), new Label("B1"), new Label("B2")));

            assertEquals(List.of(), errors);
            assertEquals(2, carousel.getSelectedIndex());
            List<String> openClose = new ArrayList<>(events);
            openClose.removeIf(event -> event.startsWith("CACHED") || event.startsWith("EVICTED"));
            assertEquals(List.of("CLOSING:4", "CLOSED:4", "OPENING:2", "OPENED:2"), openClose);
            assertTrue(events.indexOf("EVICTED:4") > events.indexOf("CLOSED:4"));
            assertEquals(List.of("B2"), shownPages(carousel));
        });
    }

    @Test
    public void setPagesCalledWhileAnotherSetPagesSetsTheCountWins() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(3));
            List<Label> outer = List.of(new Label("A0"), new Label("A1"));
            List<Label> inner = List.of(new Label("B0"), new Label("B1"), new Label("B2"), new Label("B3"));
            boolean[] armed = {true};
            carousel.pageCountProperty().addListener((observable, was, is) -> {
                if (armed[0] && is.intValue() == 2) {
                    armed[0] = false;
                    carousel.setPages(inner.toArray(new Node[0]));
                }
            });
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(outer.toArray(new Node[0])));

            assertEquals(List.of(), errors);
            assertEquals(4, carousel.getPageCount());
            assertSame(inner.get(3), carousel.getPageFactory().call(3));
            assertEquals(List.of("B0"), shownPages(carousel));
        });
    }

    @Test
    public void pageListSetWhileAnotherPageListSetsTheFactoryWins() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(3));
            ObservableList<Node> outer = FXCollections.observableArrayList(new Label("A0"), new Label("A1"));
            ObservableList<Node> inner = FXCollections.observableArrayList(
                    new Label("B0"), new Label("B1"), new Label("B2"), new Label("B3"));
            boolean[] armed = {true};
            carousel.pageFactoryProperty().addListener((observable, was, is) -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.setPages(inner);
                }
            });
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(outer));

            assertEquals(List.of(), errors);
            assertEquals(4, carousel.getPageCount());
            assertSame(inner.get(3), carousel.getPageFactory().call(3));
            assertEquals(List.of("B0"), shownPages(carousel));
            outer.add(new Label("A2"));
            assertEquals(4, carousel.getPageCount());
        });
    }

    // ==================== Cache distance ====================

    @Test
    public void largestCacheDistanceKeepsEveryPageAndStillPages() throws Exception {
        onFx(() -> {
            RXCarousel carousel = laidOutCarousel(labels(3));
            carousel.setCacheDistance(Integer.MAX_VALUE);
            List<String> events = record(carousel);

            carousel.goToPage(1, false);
            carousel.goToPage(2, false);

            assertEquals(2, carousel.getSelectedIndex());
            assertEquals(List.of("P2"), shownPages(carousel));
            assertFalse(events.stream().anyMatch(event -> event.startsWith("EVICTED")), events::toString);
        });
    }

    @Test
    public void cacheDistanceKeepsTheNearestPagesWhetherOrNotTheCarouselWraps() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels(6).toArray(new Node[0]));
            carousel.setCacheDistance(1);
            List<String> events = record(carousel);
            layOut(carousel);

            assertEquals(Set.of(5, 0, 1), cachedPages(events));
            carousel.goToPage(3, false);
            assertEquals(Set.of(2, 3, 4), cachedPages(events));
            carousel.goToPage(5, false);
            assertEquals(Set.of(4, 5, 0), cachedPages(events));

            carousel.setCircular(false);
            assertEquals(Set.of(4, 5), cachedPages(events));
            carousel.goToPage(0, false);
            assertEquals(Set.of(0, 1), cachedPages(events));

            carousel.setCircular(true);
            carousel.setCacheDistance(2);
            carousel.goToPage(1, false);
            assertEquals(Set.of(5, 0, 1, 2), cachedPages(events));
            carousel.goToPage(3, false);
            assertEquals(Set.of(1, 2, 3, 4, 5), cachedPages(events));
            carousel.setCacheDistance(3);
            carousel.goToPage(0, false);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5), cachedPages(events));
        });
    }

    // ==================== Selected index ====================

    @Test
    public void selectedIndexStaysWithinThePagesWithoutASkin() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels(5).toArray(new Node[0]));
            carousel.goToPage(4);

            carousel.setPages(new Label("B0"), new Label("B1"), new Label("B2"));

            assertEquals(2, carousel.getSelectedIndex());
            carousel.setPageCount(1);
            assertEquals(0, carousel.getSelectedIndex());
            carousel.setPageCount(3);
            carousel.goToPage(2);
            layOut(carousel);
            assertEquals(List.of("B2"), shownPages(carousel));
            assertTrue(carousel.getPseudoClassStates().contains(PseudoClass.getPseudoClass("last-page")));
        });
    }

    // ==================== Helpers ====================

    private static RXCarousel laidOutCarousel(ObservableList<Node> list) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(list);
        layOut(carousel);
        return carousel;
    }

    private static List<String> record(RXCarousel carousel) {
        List<String> events = new ArrayList<>();
        carousel.addEventHandler(RXPageLifecycleEvent.ANY, e -> events.add(
                e.getEventType().getName().substring("RX_PAGE_".length()) + ":" + e.getPageIndex()));
        return events;
    }

    // The indices cached according to the recorded CACHED and EVICTED events.
    private static Set<Integer> cachedPages(List<String> events) {
        Set<Integer> cached = new TreeSet<>();
        for (String event : events) {
            int index = Integer.parseInt(event.substring(event.indexOf(':') + 1));
            if (event.startsWith("CACHED")) {
                cached.add(index);
            } else if (event.startsWith("EVICTED")) {
                cached.remove(index);
            }
        }
        return cached;
    }

    private static List<String> shownPages(RXCarousel carousel) {
        List<String> shown = new ArrayList<>();
        for (Node wrapper : ((Parent) carousel.lookup(".content-pane")).getChildrenUnmodifiable()) {
            if (wrapper.isVisible()) {
                for (Node node : ((Parent) wrapper).getChildrenUnmodifiable()) {
                    shown.add(((Label) node).getText());
                }
            }
        }
        return shown;
    }

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
}
