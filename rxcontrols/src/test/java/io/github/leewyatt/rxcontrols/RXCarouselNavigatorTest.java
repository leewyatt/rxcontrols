package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests when the carousel tells its navigator about a page change: paging at
 * once with the page left, and a change of the page count, the page factory
 * or a page list once, after the pages are consistent again, with -1 as the
 * old index. The navigator reads every page through the factory each time.
 */
public class RXCarouselNavigatorTest {

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

    // ==================== Paging ====================

    @Test
    public void pagingNotifiesAtOnceWithThePageLeft() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels("P", 3));
            ReadingNavigator navigator = install(carousel);

            carousel.goToPage(2, false);
            carousel.previous();

            assertEquals(List.of("0->2/3[P0, P1, P2]", "2->1/3[P0, P1, P2]"), navigator.calls);
        });
    }

    // ==================== setPages ====================

    @Test
    public void setPagesWithMorePagesNotifiesOnceWithTheNewPages() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(new Label("old0"));
            ReadingNavigator navigator = install(carousel);
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(labels("new", 3)));

            assertEquals(List.of(), errors);
            assertEquals(List.of("-1->0/3[new0, new1, new2]"), navigator.calls);
        });
    }

    @Test
    public void setPagesWithFewerPagesNotifiesOnceAtTheClampedPage() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels("old", 5));
            ReadingNavigator navigator = install(carousel);
            carousel.goToPage(4, false);
            navigator.calls.clear();
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(labels("new", 3)));

            assertEquals(List.of(), errors);
            assertEquals(2, carousel.getSelectedIndex());
            assertEquals(List.of("-1->2/3[new0, new1, new2]"), navigator.calls);
        });
    }

    @Test
    public void navigatorSetWhileSetPagesRunsHearsOnlyTheNewPages() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(new Label("old0"));
            layOut(carousel);
            ReadingNavigator navigator = new ReadingNavigator();
            boolean[] armed = {true};
            carousel.pageCountProperty().addListener((observable, was, is) -> {
                if (armed[0]) {
                    armed[0] = false;
                    carousel.setNavigator(navigator);
                }
            });
            List<Throwable> errors = new ArrayList<>();

            withUncaughtHandler(errors, () -> carousel.setPages(labels("new", 3)));

            assertEquals(List.of(), errors);
            assertEquals(List.of("-1->0/3[new0, new1, new2]"), navigator.calls);
        });
    }

    // ==================== Count and factory ====================

    @Test
    public void pageCountDroppingBelowTheSelectedPageNotifiesOnce() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPageCount(5);
            carousel.setPageFactory(index -> new Label("P" + index));
            ReadingNavigator navigator = install(carousel);
            carousel.goToPage(4, false);
            navigator.calls.clear();

            carousel.setPageCount(3);

            assertEquals(List.of("-1->2/3[P0, P1, P2]"), navigator.calls);
        });
    }

    @Test
    public void newPageFactoryNotifiesTheNavigator() throws Exception {
        onFx(() -> {
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(labels("P", 3));
            ReadingNavigator navigator = install(carousel);

            carousel.setPageFactory(index -> new Label("N" + index));

            assertEquals(List.of("-1->0/3[N0, N1, N2]"), navigator.calls);
        });
    }

    // ==================== Page list ====================

    @Test
    public void replacingAPageListItemNotifiesTheNavigator() throws Exception {
        onFx(() -> {
            ObservableList<Node> list = FXCollections.observableArrayList(labels("L", 3));
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(list);
            ReadingNavigator navigator = install(carousel);

            list.set(1, new Label("L1b"));

            assertEquals(List.of("-1->0/3[L0, L1b, L2]"), navigator.calls);
        });
    }

    @Test
    public void removingTheSelectedLastPageFromAPageListNotifiesOnce() throws Exception {
        onFx(() -> {
            ObservableList<Node> list = FXCollections.observableArrayList(labels("L", 3));
            RXCarousel carousel = new RXCarousel();
            carousel.setPages(list);
            ReadingNavigator navigator = install(carousel);
            carousel.goToPage(2, false);
            navigator.calls.clear();

            list.remove(2);

            assertEquals(1, carousel.getSelectedIndex());
            assertEquals(List.of("-1->1/2[L0, L1]"), navigator.calls);
        });
    }

    // ==================== Helpers ====================

    // Installs a reading navigator on a laid-out carousel and forgets the
    // call the navigator gets when its node is created.
    private static ReadingNavigator install(RXCarousel carousel) {
        ReadingNavigator navigator = new ReadingNavigator();
        carousel.setNavigator(navigator);
        layOut(carousel);
        assertEquals(1, navigator.calls.size(), navigator.calls::toString);
        navigator.calls.clear();
        return navigator;
    }

    /** Records each call together with the text of every page it can read. */
    private static final class ReadingNavigator implements CarouselNavigator {

        final List<String> calls = new ArrayList<>();
        private RXCarousel carousel;

        @Override
        public Node createNode(RXCarousel owner) {
            carousel = owner;
            return new StackPane();
        }

        @Override
        public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
            List<String> seen = new ArrayList<>();
            try {
                for (int i = 0; i < pageCount; i++) {
                    seen.add(((Label) carousel.getPageFactory().call(i)).getText());
                }
            } catch (RuntimeException e) {
                seen.add(e.getClass().getSimpleName());
                throw e;
            } finally {
                calls.add(oldIndex + "->" + newIndex + "/" + pageCount + seen);
            }
        }

        @Override
        public void dispose() {
        }
    }

    private static Node[] labels(String prefix, int count) {
        Node[] pages = new Node[count];
        for (int i = 0; i < count; i++) {
            pages[i] = new Label(prefix + i);
        }
        return pages;
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
