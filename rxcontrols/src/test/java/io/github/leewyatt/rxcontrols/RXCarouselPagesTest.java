package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimAround;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.skins.RXCarouselSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the page structure of the carousel: each page node sits in a page
 * wrapper of the skin, animations and the skin change the wrappers only, and
 * the skin lets go of the page nodes it evicts or leaves behind.
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
