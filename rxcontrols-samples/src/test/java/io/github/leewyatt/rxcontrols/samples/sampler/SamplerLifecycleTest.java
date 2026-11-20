package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXTaskButton;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleLifecycle;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the life of a sampler page: shown and hidden as the sampler switches pages,
 * disposed when the page cache drops it or the window closes, and the RXTaskButton page
 * that starts its long run each time it opens.
 */
public class SamplerLifecycleTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        // Closing a sampler window must not end the toolkit for the next test.
        Platform.setImplicitExit(false);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /** Shown and hidden run once per change; dispose hides first, runs once, and ends all events. */
    @Test
    public void lifecycleRunsEachEventOnceAndNothingAfterDispose() {
        SampleLifecycle life = new SampleLifecycle();
        List<String> events = new ArrayList<>();
        life.whenShown(() -> events.add("shown"));
        life.whenHidden(() -> events.add("hidden"));
        life.whenDisposed(() -> events.add("disposed"));

        life.hide();
        life.show();
        life.show();
        life.hide();
        life.hide();
        life.show();
        life.dispose();
        life.dispose();
        life.show();
        life.hide();

        assertEquals(List.of("shown", "hidden", "shown", "hidden", "disposed"), events);
        assertFalse(life.isShown());
    }

    /** A broken action does not keep the other actions from running. */
    @Test
    public void failingActionDoesNotStopTheOthers() {
        SampleLifecycle life = new SampleLifecycle();
        List<String> events = new ArrayList<>();
        life.whenShown(() -> {
            throw new IllegalStateException("broken sample");
        });
        life.whenShown(() -> events.add("shown"));

        life.show();

        assertEquals(List.of("shown"), events);
    }

    /** The animation plays while shown, pauses while hidden and stops on dispose. */
    @Test
    public void playWhileShownFollowsTheLifecycle() throws Exception {
        runOnFx(() -> {
            SampleLifecycle life = new SampleLifecycle();
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.0)));
            timeline.setCycleCount(Animation.INDEFINITE);
            life.playWhileShown(timeline);
            assertEquals(Animation.Status.STOPPED, timeline.getStatus());

            life.show();
            assertEquals(Animation.Status.RUNNING, timeline.getStatus());
            life.hide();
            assertEquals(Animation.Status.PAUSED, timeline.getStatus());
            life.show();
            assertEquals(Animation.Status.RUNNING, timeline.getStatus());
            life.dispose();
            assertEquals(Animation.Status.STOPPED, timeline.getStatus());
        });
    }

    /**
     * The States card of the RXTaskButton page starts its long run each time the page
     * opens, a page taken back from the cache included, but never restarts a run under way.
     */
    @Test
    public void taskButtonPageStartsItsRunEachTimeItOpens() throws Exception {
        runOnFx(() -> withSampler(sampler -> {
            sampler.open(sampler.entry("RXTaskButton"));
            Node page = sampler.host.getContent();
            RXTaskButton pending = runningTaskButton(page);
            int[] fired = {0};
            pending.addEventHandler(ActionEvent.ACTION, event -> fired[0]++);

            sampler.openOthers(1);
            sampler.open(sampler.entry("RXTaskButton"));
            assertSame(page, sampler.host.getContent());
            assertEquals(0, fired[0]);
            assertTrue(pending.isRunning());

            pending.cancel();
            sampler.openOthers(1);
            assertFalse(pending.isRunning());
            sampler.open(sampler.entry("RXTaskButton"));
            assertSame(page, sampler.host.getContent());
            assertEquals(1, fired[0]);
            assertTrue(pending.isRunning());
        }));
    }

    /** A page dropped from the cache is disposed: it no longer reacts to entering a scene. */
    @Test
    public void droppedPageIsDisposed() throws Exception {
        runOnFx(() -> withSampler(sampler -> {
            sampler.open(sampler.entry("RXTaskButton"));
            Node page = sampler.host.getContent();
            RXTaskButton pending = runningTaskButton(page);
            pending.cancel();
            int[] fired = {0};
            pending.addEventHandler(ActionEvent.ACTION, event -> fired[0]++);

            // The cache holds four pages; four others push this one out.
            sampler.openOthers(4);
            new Scene(new StackPane(page));
            assertEquals(0, fired[0]);
            assertFalse(pending.isRunning());

            sampler.open(sampler.entry("RXTaskButton"));
            assertNotSame(page, sampler.host.getContent());
        }));
    }

    /** Closing the sampler window disposes the page on display. */
    @Test
    public void closingTheWindowDisposesThePages() throws Exception {
        runOnFx(() -> withSampler(sampler -> {
            sampler.open(sampler.entry("RXTaskButton"));
            Node page = sampler.host.getContent();
            RXTaskButton pending = runningTaskButton(page);
            pending.cancel();
            int[] fired = {0};
            pending.addEventHandler(ActionEvent.ACTION, event -> fired[0]++);

            sampler.stage.hide();
            sampler.host.setContent(null);
            new Scene(new StackPane(page));
            assertEquals(0, fired[0]);
            assertFalse(pending.isRunning());
        }));
    }

    // ==================== Helpers ====================

    // The only task button running right after the page opens is the second one of States.
    private static RXTaskButton runningTaskButton(Node page) {
        List<RXTaskButton> running = new ArrayList<>();
        for (Node node : page.lookupAll(".rx-task-button")) {
            if (node instanceof RXTaskButton button && button.isRunning()) {
                running.add(button);
            }
        }
        assertEquals(1, running.size());
        return running.get(0);
    }

    private static void withSampler(SamplerAction action) {
        Stage stage = new Stage();
        new RXSampler().start(stage);
        try {
            action.run(new RunningSampler(stage));
        } finally {
            stage.hide();
        }
    }

    private interface SamplerAction {
        void run(RunningSampler sampler);
    }

    /** Drives a started sampler through its navigation, with page transitions as direct cuts. */
    private static final class RunningSampler {

        private final Stage stage;
        private final SamplerNav nav;
        private final RXTransitionPane host;

        RunningSampler(Stage stage) {
            this.stage = stage;
            BorderPane root = (BorderPane) stage.getScene().getRoot();
            nav = (SamplerNav) root.getLeft();
            host = (RXTransitionPane) root.lookup(".page-host");
            host.setAnimated(false);
        }

        SamplerEntry entry(String name) {
            for (SamplerEntry entry : SamplerCatalog.entries()) {
                if (entry.name().equals(name)) {
                    return entry;
                }
            }
            throw new AssertionError("no entry " + name);
        }

        // The pulse a real switch is followed by: the examples sit in a scroll pane, whose
        // content can be looked up only once the pane has its skin.
        void open(SamplerEntry entry) {
            nav.currentProperty().set(entry);
            assertSame(entry, nav.currentProperty().get());
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
        }

        void openOthers(int count) {
            String[] names = {"RXButton", "RXTextField", "RXBadge", "RXAvatar"};
            for (int i = 0; i < count; i++) {
                open(entry(names[i]));
            }
        }
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
