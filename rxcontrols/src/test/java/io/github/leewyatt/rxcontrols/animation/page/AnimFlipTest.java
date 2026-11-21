package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.Glow;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AnimFlip}: the page swap at the middle, the direction and
 * perspective of the turn, restoring the pages and their own effects on every end
 * path, pages that do not fill the pane or draw past their bounds, invalid
 * settings, and reuse.
 */
public class AnimFlipTest {

    private static final Duration DURATION = Duration.millis(400.0);

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
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

    /** Pages and a content pane laid out by hand. */
    private static final class Fixture {
        final StackPane contentPane = new StackPane();
        final Region current = new Region();
        final Region next = new Region();
        final TransitionContext context;

        Fixture(double pageWidth, double pageHeight) {
            this(pageWidth, pageHeight, TransitionDirection.FORWARD);
        }

        Fixture(double pageWidth, double pageHeight, TransitionDirection direction) {
            contentPane.getChildren().addAll(next, current);
            contentPane.resize(400.0, 300.0);
            current.resize(pageWidth, pageHeight);
            next.resize(pageWidth, pageHeight);
            context = contextFor(current, next, direction);
        }

        TransitionContext contextFor(Region from, Region to, TransitionDirection direction) {
            return new TransitionContext(from, to, 0, 1, 2, direction, DURATION, contentPane, index -> null);
        }
    }

    // The transform the page wears at a share of the flip, read while paused there.
    private static double[] cornersAt(Animation animation, Region page, double share) {
        animation.jumpTo(DURATION.multiply(share));
        PerspectiveTransform pt = (PerspectiveTransform) page.getEffect();
        return new double[]{pt.getUlx(), pt.getUly(), pt.getUrx(), pt.getUry(),
            pt.getLrx(), pt.getLry(), pt.getLlx(), pt.getLly()};
    }

    private static <T> T onFx(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void passingTheMiddleSwapsThePages() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        boolean[] visible = onFx(() -> {
            Animation animation = new AnimFlip().getAnimation(fixture.context);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.45));
            boolean currentBefore = fixture.current.isVisible();
            boolean nextBefore = fixture.next.isVisible();
            animation.jumpTo(DURATION.multiply(0.55));
            boolean[] result = {currentBefore, nextBefore, fixture.current.isVisible(), fixture.next.isVisible()};
            animation.stop();
            return result;
        });
        assertTrue(visible[0], "the current page is hidden before the middle");
        assertFalse(visible[1], "the next page shows before the middle");
        assertFalse(visible[2], "the current page still shows after the middle");
        assertTrue(visible[3], "the next page does not show after the middle");
    }

    @Test
    public void startingShowsTheCurrentPageAndHidesTheNext() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        boolean[] visible = onFx(() -> {
            fixture.current.setVisible(false);
            fixture.next.setVisible(true);
            new AnimFlip().getAnimation(fixture.context);
            return new boolean[]{fixture.current.isVisible(), fixture.next.isVisible()};
        });
        assertTrue(visible[0], "the current page does not show at the start");
        assertFalse(visible[1], "the next page shows at the start");
    }

    @Test
    public void aNaturalFinishRestoresBothPages() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        CountDownLatch finished = new CountDownLatch(1);
        onFx(() -> {
            Animation animation = new AnimFlip().getAnimation(fixture.context);
            runAfterFinish(animation, finished::countDown);
            animation.play();
            return null;
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS), "the flip did not finish");
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
        assertFalse(fixture.current.isVisible());
        assertTrue(fixture.next.isVisible());
    }

    @Test
    public void jumpToEndRestoresBothPages() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        onFx(() -> {
            AnimFlip flip = new AnimFlip(Orientation.VERTICAL);
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.25));
            flip.jumpToEnd();
            return null;
        });
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
        assertFalse(fixture.current.isVisible());
        assertTrue(fixture.next.isVisible());
    }

    @Test
    public void clearEffectsRemovesTheTransformsFromThePages() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        onFx(() -> {
            AnimFlip flip = new AnimFlip();
            flip.getAnimation(fixture.context);
            flip.clearEffects(fixture.context);
            return null;
        });
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
    }

    @Test
    public void aPageSmallerThanThePaneStartsFlatOnItsOwnBounds() throws Exception {
        Fixture fixture = new Fixture(200.0, 100.0);
        PerspectiveTransform start = onFx(() -> {
            new AnimFlip().getAnimation(fixture.context);
            return (PerspectiveTransform) fixture.current.getEffect();
        });
        assertEquals(0.0, start.getUlx(), 1e-9);
        assertEquals(0.0, start.getUly(), 1e-9);
        assertEquals(200.0, start.getLrx(), 1e-9);
        assertEquals(100.0, start.getLry(), 1e-9);
    }

    @Test
    public void aNullInterpolatorFallsBackToEaseBoth() throws Exception {
        double[][] corners = onFx(() -> {
            double[][] result = new double[2][];
            Interpolator[] interpolators = {null, Interpolator.EASE_BOTH};
            for (int i = 0; i < 2; i++) {
                Fixture fixture = new Fixture(400.0, 300.0);
                AnimFlip flip = new AnimFlip();
                flip.setInterpolator(interpolators[i]);
                Animation animation = flip.getAnimation(fixture.context);
                animation.play();
                animation.pause();
                result[i] = cornersAt(animation, fixture.current, 0.3);
                animation.stop();
            }
            return result;
        });
        assertArrayEquals(corners[1], corners[0], 1e-9);
    }

    @Test
    public void everyFrameStaysFiniteAndInsideThePageInBothOrientationsAndDirections() throws Exception {
        for (Orientation orientation : Orientation.values()) {
            for (TransitionDirection direction : TransitionDirection.values()) {
                String label = orientation + " " + direction;
                onFx(() -> {
                    Fixture fixture = new Fixture(400.0, 300.0, direction);
                    AnimFlip flip = new AnimFlip(orientation);
                    Animation animation = flip.getAnimation(fixture.context);
                    animation.play();
                    animation.pause();
                    for (int step = 0; step <= 20; step++) {
                        double share = step / 20.0;
                        Region page = share < 0.5 ? fixture.current : fixture.next;
                        double[] corners = cornersAt(animation, page, share);
                        for (int i = 0; i < corners.length; i++) {
                            double limit = i % 2 == 0 ? 400.0 : 300.0;
                            assertTrue(Double.isFinite(corners[i]) && corners[i] >= -1e-9 && corners[i] <= limit + 1e-9,
                                    label + " corner " + i + " at " + share + " is " + corners[i]);
                        }
                    }
                    animation.stop();
                    return null;
                });
            }
        }
    }

    @Test
    public void eachPageTurnsWithItsNearEdgeOnTheSideTheDirectionAndOrientationGive() throws Exception {
        for (Orientation orientation : Orientation.values()) {
            for (TransitionDirection direction : TransitionDirection.values()) {
                String label = orientation + " " + direction;
                double[][] frames = onFx(() -> {
                    Fixture fixture = new Fixture(400.0, 300.0, direction);
                    Animation animation = new AnimFlip(orientation).getAnimation(fixture.context);
                    animation.play();
                    animation.pause();
                    double[][] result = new double[4][];
                    result[0] = cornersAt(animation, fixture.current, 0.25);
                    result[1] = cornersAt(animation, fixture.current, 0.49);
                    result[2] = cornersAt(animation, fixture.next, 0.51);
                    result[3] = cornersAt(animation, fixture.next, 0.75);
                    animation.stop();
                    return result;
                });
                boolean horizontal = orientation == Orientation.HORIZONTAL;
                boolean forward = direction == TransitionDirection.FORWARD;
                double full = horizontal ? 300.0 : 400.0;
                for (int frame = 0; frame < 4; frame++) {
                    double[] c = frames[frame];
                    // The two edges parallel to the turning axis, and the extent across it.
                    double startEdge = horizontal ? c[2] - c[0] : c[7] - c[1];
                    double endEdge = horizontal ? c[4] - c[6] : c[5] - c[3];
                    double across = horizontal ? c[7] - c[1] : c[2] - c[0];
                    if (frame == 1 || frame == 2) {
                        assertTrue(across >= 0.0 && across < full * 0.1,
                                label + " frame " + frame + " is not near edge on: " + across);
                    } else {
                        assertTrue(across > full * 0.5 && across < full - 1.0,
                                label + " frame " + frame + " is not partly turned: " + across);
                        // The near edge is drawn longer than the far one.
                        boolean startEdgeNear = forward == (frame == 0);
                        assertTrue(startEdgeNear ? startEdge > endEdge + 1.0 : endEdge > startEdge + 1.0,
                                label + " frame " + frame + " has its near edge on the wrong side: "
                                        + startEdge + " / " + endEdge);
                    }
                }
            }
        }
    }

    @Test
    public void aQuarterOfTheFlipTurnsThePageFortyFiveDegrees() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        double[] corners = onFx(() -> {
            AnimFlip flip = new AnimFlip();
            flip.setInterpolator(Interpolator.LINEAR);
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            double[] result = cornersAt(animation, fixture.current, 0.25);
            animation.stop();
            return result;
        });
        // About the horizontal centre line, the eye two page heights (600) away. The top
        // edge comes near and is scaled back to the page width; sine and cosine are equal.
        double turn = Math.sin(Math.PI / 4.0);
        double near = 600.0 / (600.0 - 150.0 * turn);
        double far = 600.0 / (600.0 + 150.0 * turn);
        double fit = 1.0 / near;
        double top = 150.0 - 150.0 * turn * near * fit;
        double bottom = 150.0 + 150.0 * turn * far * fit;
        double[] expected = {0.0, top, 400.0, top,
            200.0 + 200.0 * far * fit, bottom, 200.0 - 200.0 * far * fit, bottom};
        assertArrayEquals(expected, corners, 1e-6);
    }

    @Test
    public void aPageKeepsItsOwnEffectWhileTurningAndGetsItBackAfterwards() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        DropShadow shadow = new DropShadow(20.0, Color.BLACK);
        Effect during = onFx(() -> {
            fixture.current.setEffect(shadow);
            AnimFlip flip = new AnimFlip();
            Animation animation = flip.getAnimation(fixture.context);
            Effect input = ((PerspectiveTransform) fixture.current.getEffect()).getInput();
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.25));
            flip.jumpToEnd();
            return input;
        });
        assertSame(shadow, during);
        assertSame(shadow, fixture.current.getEffect());
    }

    @Test
    public void clearEffectsLeavesAPageItsOwnEffect() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        DropShadow shadow = new DropShadow(20.0, Color.BLACK);
        onFx(() -> {
            fixture.next.setEffect(shadow);
            AnimFlip flip = new AnimFlip();
            flip.getAnimation(fixture.context);
            flip.clearEffects(fixture.context);
            return null;
        });
        assertSame(shadow, fixture.next.getEffect());
        assertNull(fixture.current.getEffect());
    }

    @Test
    public void aShadowedPageStartsFlatOverItsWholeContent() throws Exception {
        Fixture fixture = new Fixture(200.0, 100.0);
        Object[] result = onFx(() -> {
            fixture.current.setEffect(new DropShadow(20.0, Color.BLACK));
            Bounds content = fixture.current.getBoundsInLocal();
            new AnimFlip().getAnimation(fixture.context);
            return new Object[]{content, fixture.current.getEffect()};
        });
        Bounds content = (Bounds) result[0];
        PerspectiveTransform start = (PerspectiveTransform) result[1];
        assertTrue(content.getMinX() < 0.0, "the shadow does not reach past the page; the test would be vacuous");
        assertEquals(content.getMinX(), start.getUlx(), 1e-9);
        assertEquals(content.getMinY(), start.getUly(), 1e-9);
        assertEquals(content.getMaxX(), start.getLrx(), 1e-9);
        assertEquals(content.getMaxY(), start.getLry(), 1e-9);
    }

    @Test
    public void aHugeFocalLengthFactorStillGivesFiniteCorners() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        double[] corners = onFx(() -> {
            AnimFlip flip = new AnimFlip();
            flip.setFocalLengthFactor(1e308);
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            double[] result = cornersAt(animation, fixture.current, 0.3);
            animation.stop();
            return result;
        });
        for (double corner : corners) {
            assertTrue(Double.isFinite(corner), "a corner is " + corner);
        }
    }

    @Test
    public void oneInstanceFlipsBackAndForthAndAStillRunningFlipEndsFirst() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        boolean[] state = onFx(() -> {
            AnimFlip flip = new AnimFlip();
            Animation first = flip.getAnimation(fixture.context);
            first.play();
            // The second flip starts while the first still runs: the first ends first,
            // showing the next page, and the second turns it back.
            Animation second = flip.getAnimation(
                    fixture.contextFor(fixture.next, fixture.current, TransitionDirection.BACKWARD));
            boolean firstStopped = first.getStatus() == Animation.Status.STOPPED;
            second.play();
            second.pause();
            second.jumpTo(DURATION.multiply(0.75));
            boolean[] result = {firstStopped, fixture.current.isVisible(), fixture.next.isVisible()};
            flip.jumpToEnd();
            return result;
        });
        assertTrue(state[0], "the first flip still runs");
        assertTrue(state[1], "the page flipped back to does not show");
        assertFalse(state[2], "the page flipped away from still shows");
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
    }

    @Test
    public void aFlipStoppedFromOutsideEndsBeforeTheNextOneStarts() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        Object[] state = onFx(() -> {
            Region third = new Region();
            fixture.contentPane.getChildren().add(0, third);
            third.resize(400.0, 300.0);
            AnimFlip flip = new AnimFlip();
            Animation first = flip.getAnimation(fixture.context);
            first.play();
            first.pause();
            first.jumpTo(DURATION.multiply(0.25));
            first.stop();
            // The next flip leaves out the page the first one was turning away.
            flip.getAnimation(fixture.contextFor(fixture.next, third, TransitionDirection.FORWARD));
            Object[] result = {fixture.current.getEffect(), fixture.current.isVisible()};
            flip.jumpToEnd();
            return result;
        });
        assertNull(state[0], "the page left behind still wears a transform");
        assertFalse((Boolean) state[1], "the page left behind still shows");
    }

    @Test
    public void aNextPageNotLaidOutAtTheStartEndsOnItsLaidOutBounds() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        double[] corners = onFx(() -> {
            // An intrinsic-size child counts in the page's content before the page has a size.
            StackPane fresh = new StackPane(new Rectangle(400.0, 300.0));
            fixture.contentPane.getChildren().add(0, fresh);
            AnimFlip flip = new AnimFlip();
            Animation animation = flip.getAnimation(
                    fixture.contextFor(fixture.current, fresh, TransitionDirection.FORWARD));
            animation.play();
            animation.pause();
            fresh.resize(400.0, 300.0);
            animation.jumpTo(DURATION.multiply(0.75));
            double[] result = cornersAt(animation, fresh, 1.0 - 1e-9);
            animation.stop();
            return result;
        });
        assertArrayEquals(new double[]{0.0, 0.0, 400.0, 0.0, 400.0, 300.0, 0.0, 300.0}, corners, 1e-3);
    }

    @Test
    public void aShadowedNextPageLaidOutLateEndsFlatOverItsWholeContent() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        Object[] result = onFx(() -> {
            Region fresh = new Region();
            fresh.setEffect(new DropShadow(20.0, Color.BLACK));
            fixture.contentPane.getChildren().add(0, fresh);
            Animation animation = new AnimFlip().getAnimation(
                    fixture.contextFor(fixture.current, fresh, TransitionDirection.FORWARD));
            animation.play();
            animation.pause();
            fresh.resize(400.0, 300.0);
            animation.jumpTo(DURATION.multiply(0.75));
            double[] corners = cornersAt(animation, fresh, 1.0 - 1e-9);
            animation.stop();
            fresh.setEffect(((PerspectiveTransform) fresh.getEffect()).getInput());
            return new Object[]{corners, fresh.getBoundsInLocal()};
        });
        double[] corners = (double[]) result[0];
        Bounds content = (Bounds) result[1];
        assertTrue(content.getMinX() < 0.0, "the shadow does not reach past the page; the test would be vacuous");
        assertEquals(content.getMinX(), corners[0], 1e-3);
        assertEquals(content.getMinY(), corners[1], 1e-3);
        assertEquals(content.getMaxX(), corners[4], 1e-3);
        assertEquals(content.getMaxY(), corners[5], 1e-3);
    }

    @Test
    public void anEffectGivenToTheNextPageWhenItStartsOpeningIsKept() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        Glow glow = new Glow();
        onFx(() -> {
            AnimFlip flip = new AnimFlip();
            Animation animation = flip.getAnimation(fixture.contextFor(fixture.current, fixture.next,
                    TransitionDirection.FORWARD));
            // What an opening handler would do once the flip has started.
            fixture.next.setEffect(glow);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.75));
            flip.jumpToEnd();
            return null;
        });
        assertSame(glow, fixture.next.getEffect());
    }

    @Test
    public void aShadowReachingFarPastANarrowPageNeverTurnsItInsideOut() throws Exception {
        Fixture fixture = new Fixture(200.0, 20.0);
        onFx(() -> {
            fixture.current.setEffect(new DropShadow(25.0, Color.BLACK));
            AnimFlip flip = new AnimFlip();
            flip.setFocalLengthFactor(1.0);
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            for (int step = 0; step < 50; step++) {
                double share = step / 100.0;
                double[] c = cornersAt(animation, fixture.current, share);
                for (double corner : c) {
                    assertTrue(Double.isFinite(corner), "a corner at " + share + " is " + corner);
                }
                // Left stays left of right on both the far and the near edge.
                assertTrue(c[0] < c[2] && c[6] < c[4], "the page turned inside out at " + share);
            }
            animation.stop();
            return null;
        });
    }

    @Test
    public void anEffectGivenToAPageDuringTheFlipIsKept() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        Glow glow = new Glow();
        onFx(() -> {
            AnimFlip flip = new AnimFlip();
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.7));
            fixture.next.setEffect(glow);
            flip.jumpToEnd();
            return null;
        });
        assertSame(glow, fixture.next.getEffect());
    }

    @Test
    public void aNaturalFinishLetsGoOfTheFlipSoALaterJumpToEndDoesNothing() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        AnimFlip flip = new AnimFlip();
        CountDownLatch finished = new CountDownLatch(1);
        onFx(() -> {
            Animation animation = flip.getAnimation(fixture.context);
            runAfterFinish(animation, finished::countDown);
            animation.play();
            return null;
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS), "the flip did not finish");
        boolean[] state = onFx(() -> {
            boolean released = flip.getCurrentAnimation() == null;
            fixture.current.setVisible(true);
            flip.jumpToEnd();
            return new boolean[]{released, fixture.current.isVisible()};
        });
        assertTrue(state[0], "the flip still holds its timeline");
        assertTrue(state[1], "jumpToEnd ran the finished flip's restore again");
    }

    @Test
    public void aTransformLeftByADisposedFlipGivesThePageItsOwnEffectBack() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        DropShadow shadow = new DropShadow(20.0, Color.BLACK);
        Object[] result = onFx(() -> {
            fixture.current.setEffect(shadow);
            AnimFlip flip = new AnimFlip();
            Animation first = flip.getAnimation(fixture.context);
            first.play();
            first.pause();
            first.jumpTo(DURATION.multiply(0.25));
            flip.dispose();
            Animation.Status afterDispose = first.getStatus();
            flip.getAnimation(fixture.context);
            Effect input = ((PerspectiveTransform) fixture.current.getEffect()).getInput();
            flip.jumpToEnd();
            return new Object[]{afterDispose, input};
        });
        assertEquals(Animation.Status.STOPPED, result[0]);
        assertSame(shadow, result[1]);
        assertSame(shadow, fixture.current.getEffect());
    }

    // Runs a task after the animation's own finish handler, keeping that handler.
    private static void runAfterFinish(Animation animation, Runnable task) {
        EventHandler<ActionEvent> own = animation.getOnFinished();
        animation.setOnFinished(event -> {
            if (own != null) {
                own.handle(event);
            }
            task.run();
        });
    }
}
