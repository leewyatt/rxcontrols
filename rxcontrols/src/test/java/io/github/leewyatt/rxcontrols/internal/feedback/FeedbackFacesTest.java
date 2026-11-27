package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the four-faces-on-two-pages surface: which face shows, how faces
 * move between the pages, the size of the surface, animated swaps, direct
 * cuts and dispose.
 */
public class FeedbackFacesTest {

    private static final Duration SWAP = Duration.millis(200.0);

    private final Region idle = face(40.0, 10.0);
    private final Region pending = face(60.0, 12.0);
    private final Region succeeded = face(50.0, 30.0);
    private final Region failed = face(20.0, 8.0);
    private final FeedbackFaces faces = new FeedbackFaces(idle, pending, succeeded, failed);

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
        Platform.setImplicitExit(false);
    }

    @Test
    public void firstFaceShowsAndTheOthersWaitOnTheOtherPage() {
        assertSame(idle, faces.getCurrentFace());
        assertEquals(List.of(idle), showing());
        assertNotSame(idle.getParent(), pending.getParent());
        assertSame(pending.getParent(), succeeded.getParent());
        assertSame(pending.getParent(), failed.getParent());
        assertEquals(2, faces.getChildren().size());
    }

    @Test
    public void everyStepShowsExactlyOneFaceAndKeepsTheSize() {
        Parent home = idle.getParent();
        List<Region> steps = List.of(pending, succeeded, idle, pending, failed, pending, idle,
                failed, succeeded, idle);

        for (Region step : steps) {
            cut(step);

            assertSame(step, faces.getCurrentFace());
            assertEquals(List.of(step), showing());
            // The initial face holds the application's graphic and stays put.
            assertSame(home, idle.getParent());
            // The largest face decides, whichever page it is on.
            assertEquals(60.0, faces.prefWidth(-1));
            assertEquals(30.0, faces.prefHeight(-1));
            assertEquals(30.0, faces.minWidth(-1));
            assertEquals(15.0, faces.minHeight(-1));
        }
    }

    @Test
    public void faceOnThePageBeingShownMovesToTheOtherPage() {
        cut(pending);
        Parent pendingPage = pending.getParent();

        cut(succeeded);

        assertNotSame(pendingPage, succeeded.getParent());
        assertSame(pendingPage, pending.getParent());
        assertSame(pendingPage, failed.getParent());
    }

    @Test
    public void faceOnShowStepsAsideForTheInitialFace() {
        cut(pending);
        cut(succeeded);
        Parent home = idle.getParent();
        assertSame(home, succeeded.getParent());

        cut(idle);

        assertNotSame(home, succeeded.getParent());
        assertEquals(List.of(idle), showing());
    }

    @Test
    public void showingTheCurrentFaceDoesNothing() throws InterruptedException {
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation();

            faces.show(idle, TransitionDirection.FORWARD, animation, SWAP, true);
            assertTrue(animation.contexts.isEmpty());

            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, true);
            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, true);
            assertEquals(1, animation.contexts.size());
            assertEquals(0, animation.jumpToEndCalls);
            faces.dispose(animation);
        });
    }

    @Test
    public void animatedSwapRunsFromThePageOnShowToThePageOfTheFace() throws InterruptedException {
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation();

            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, true);
            TransitionContext first = animation.contexts.get(0);
            assertSame(idle.getParent(), first.getCurrentPage());
            assertSame(pending.getParent(), first.getNextPage());
            assertEquals(TransitionDirection.FORWARD, first.getDirection());
            assertEquals(SWAP, first.getDuration());
            // Both faces are on stage while the swap runs.
            assertEquals(List.of(idle, pending), showing());

            // The next swap ends the running one first.
            faces.show(succeeded, TransitionDirection.FORWARD, animation, SWAP, true);
            assertEquals(1, animation.jumpToEndCalls);
            TransitionContext second = animation.contexts.get(1);
            assertSame(pending.getParent(), second.getCurrentPage());
            assertSame(succeeded.getParent(), second.getNextPage());

            // Back to the initial face, which shares its page with the face on show.
            faces.show(idle, TransitionDirection.BACKWARD, animation, SWAP, true);
            TransitionContext third = animation.contexts.get(2);
            assertSame(succeeded.getParent(), third.getCurrentPage());
            assertSame(idle.getParent(), third.getNextPage());
            assertNotSame(third.getCurrentPage(), third.getNextPage());
            assertEquals(TransitionDirection.BACKWARD, third.getDirection());
            assertEquals(List.of(idle, succeeded), showing());
            faces.dispose(animation);
        });
    }

    @Test
    public void swapWithoutAUsableAnimationCutsDirectly() throws InterruptedException {
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation();

            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, false);
            assertEquals(List.of(pending), showing());
            faces.show(succeeded, TransitionDirection.FORWARD, animation, Duration.ZERO, true);
            assertEquals(List.of(succeeded), showing());
            faces.show(failed, TransitionDirection.FORWARD, null, SWAP, true);
            assertEquals(List.of(failed), showing());

            assertTrue(animation.contexts.isEmpty());
        });
    }

    @Test
    public void cutDuringASwapSettlesOnTheLatestFace() throws InterruptedException {
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation();
            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, true);

            faces.show(failed, TransitionDirection.FORWARD, animation, SWAP, false);

            assertEquals(1, animation.jumpToEndCalls);
            assertEquals(1, animation.contexts.size());
            assertEquals(List.of(failed), showing());
        });
    }

    @Test
    public void disposeEndsARunningSwapWithoutStartingAnother() throws InterruptedException {
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation();
            faces.show(pending, TransitionDirection.FORWARD, animation, SWAP, true);

            faces.dispose(animation);

            assertEquals(1, animation.contexts.size());
            assertEquals(1, animation.jumpToEndCalls);
            assertEquals(1, animation.disposeCalls);
            assertSame(idle, faces.getCurrentFace());
            assertTrue(idle.isVisible());
            assertTrue(faces.getChildren().isEmpty());
            assertNull(faces.getClip());
        });
    }

    // Carrying a face to the other page between two swaps must not trip a
    // preset: some work on a snapshot of the outgoing page, some clip or
    // re-parent the pages while they run.
    @Test
    public void everyPresetSettlesOnOneFaceWhenSwapsInterruptEachOther() throws Exception {
        List<String> presets = presetClassNames();
        AtomicInteger played = new AtomicInteger();
        onFx(() -> {
            for (String preset : presets) {
                PageAnimation animation = newPreset(preset);
                if (animation == null) {
                    continue;
                }
                played.incrementAndGet();
                Region first = face(40.0, 10.0);
                Region second = face(60.0, 12.0);
                Region third = face(50.0, 30.0);
                Region fourth = face(20.0, 8.0);
                List<Node> all = List.of(first, second, third, fourth);
                FeedbackFaces surface = new FeedbackFaces(first, second, third, fourth);
                StackPane root = new StackPane(surface);
                new Scene(root, 200.0, 100.0);
                root.applyCss();
                root.layout();

                for (Region step : List.of(second, third, first, second, fourth, first)) {
                    TransitionDirection direction = step == first
                            ? TransitionDirection.BACKWARD
                            : TransitionDirection.FORWARD;
                    surface.show(step, direction, animation, SWAP, true);
                    assertSame(step, surface.getCurrentFace(), preset);
                    assertTrue(step.isVisible() && step.getParent() != null, preset);
                }
                surface.show(third, TransitionDirection.FORWARD, animation, SWAP, false);

                assertEquals(List.of(third), showing(all), preset);
                Node page = third.getParent();
                assertEquals(1.0, page.getOpacity(), preset);
                assertEquals(0.0, page.getTranslateX(), preset);
                assertEquals(0.0, page.getTranslateY(), preset);
                assertEquals(1.0, page.getScaleX(), preset);
                assertEquals(1.0, page.getScaleY(), preset);
                assertEquals(0.0, page.getRotate(), preset);
                assertNull(page.getEffect(), preset);
                assertEquals(2, surface.getChildren().size(), preset);
                assertEquals(60.0, surface.prefWidth(-1), preset);
                surface.dispose(animation);
            }
        });
        assertTrue(played.get() > 60, "presets played: " + played.get() + " of " + presets.size());
    }

    private static List<String> presetClassNames() throws IOException {
        Path directory = Paths.get("src", "main", "java", "io", "github", "leewyatt", "rxcontrols",
                "animation", "page");
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("Anim") && name.endsWith(".java")) {
                    names.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        return names;
    }

    // Null for a preset the surface would not play: one without a no-argument
    // constructor, or one that needs more than two pages or shows several.
    private static PageAnimation newPreset(String simpleName) {
        try {
            Class<?> type = Class.forName(PageAnimation.class.getPackageName() + "." + simpleName);
            Constructor<?> constructor;
            try {
                constructor = type.getConstructor();
            } catch (NoSuchMethodException ex) {
                return null;
            }
            PageAnimation animation = (PageAnimation) constructor.newInstance();
            return PageTransitionEngine.canAnimate(animation, true, 2, SWAP, false) ? animation : null;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(simpleName, ex);
        }
    }

    // ==================== Helpers ====================

    private static Region face(double width, double height) {
        Region face = new Region();
        face.setPrefSize(width, height);
        face.setMinSize(width / 2.0, height / 2.0);
        return face;
    }

    private void cut(Node face) {
        faces.show(face, TransitionDirection.FORWARD, null, SWAP, false);
    }

    private List<Node> showing() {
        return showing(List.of(idle, pending, succeeded, failed));
    }

    // The faces a user would see: visible, on a visible page.
    private static List<Node> showing(List<Node> all) {
        List<Node> showing = new ArrayList<>();
        for (Node face : all) {
            if (face.isVisible() && face.getParent() != null && face.getParent().isVisible()) {
                showing.add(face);
            }
        }
        return showing;
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    // Never finishes on its own: a swap only ends through jumpToEnd or dispose.
    private static final class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int disposeCalls;

        private Animation running;
        private Runnable finishAction;

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            incoming.setVisible(true);
            finishAction = () -> {
                outgoing.setVisible(false);
                incoming.setVisible(true);
            };
            running = new PauseTransition(Duration.seconds(30.0));
            return running;
        }

        @Override
        public void jumpToEnd() {
            jumpToEndCalls++;
            stop();
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
            stop();
        }

        private void stop() {
            if (running != null) {
                running.stop();
                running = null;
            }
        }
    }
}
