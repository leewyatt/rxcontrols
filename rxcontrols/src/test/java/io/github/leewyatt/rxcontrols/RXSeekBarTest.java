package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXSeekBar} pointer, keyboard and accessibility seeking, layout and sizing.
 */
public class RXSeekBarTest {

    private static final double EPSILON = 1.0e-9;
    /** Default thumb width; a 212 wide bar gets a track from x 6, 200 long. */
    private static final double THUMB = 12.0;
    private static final double WIDTH = 212.0;

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

    /** Verifies a press seeks to the pointer, a drag follows it and the release ends the seek. */
    @Test
    public void pressDragAndReleaseSeekAndCommitOnce() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            List<Boolean> seeking = recordSeeking(bar);
            Node hitArea = hitArea(bar);
            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            assertEquals(0.25, bar.getProgress(), EPSILON);
            assertTrue(bar.isSeeking());
            fire(hitArea, MouseEvent.MOUSE_DRAGGED, 6.0 + 150.0, MouseButton.PRIMARY);
            assertEquals(0.75, bar.getProgress(), EPSILON);
            fire(hitArea, MouseEvent.MOUSE_RELEASED, 6.0 + 150.0, MouseButton.PRIMARY);
            assertEquals(List.of(true, false), seeking);
        });
    }

    /** Verifies a secondary-button press neither moves the progress nor starts a seek. */
    @Test
    public void secondaryButtonDoesNotSeek() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(0.5), 1.0);
            fire(hitArea(bar), MouseEvent.MOUSE_PRESSED, 6.0 + 150.0, MouseButton.SECONDARY);
            assertEquals(0.5, bar.getProgress(), EPSILON);
            assertFalse(bar.isSeeking());
        });
    }

    /** Verifies pressing the thumb off-centre keeps the progress and a drag moves it relatively. */
    @Test
    public void thumbDragMovesRelativelyWithoutAJump() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(0.5), 1.0);
            Node thumb = bar.lookup(".thumb");
            fire(thumb, MouseEvent.MOUSE_PRESSED, 2.0, MouseButton.PRIMARY);
            assertEquals(0.5, bar.getProgress(), EPSILON);
            fire(thumb, MouseEvent.MOUSE_DRAGGED, 2.0 + 20.0, MouseButton.PRIMARY);
            assertEquals(0.6, bar.getProgress(), EPSILON);
        });
    }

    /** Verifies the arrow keys move the thumb in their own direction, mirrored for right-to-left. */
    @Test
    public void arrowKeysFollowTheLayoutDirection() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(0.5), 1.0);
            List<Boolean> seeking = recordSeeking(bar);
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            assertEquals(0.55, bar.getProgress(), EPSILON);
            assertEquals(List.of(true, false), seeking);
            Event.fireEvent(bar, key(KeyCode.KP_LEFT));
            assertEquals(0.5, bar.getProgress(), EPSILON);

            bar.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            assertEquals(0.45, bar.getProgress(), EPSILON);
            Event.fireEvent(bar, key(KeyCode.LEFT));
            assertEquals(0.5, bar.getProgress(), EPSILON);
            Event.fireEvent(bar, key(KeyCode.END));
            assertEquals(1.0, bar.getProgress(), EPSILON);
        });
    }

    /** Verifies keys are ignored during a pointer seek, so they cannot end it early. */
    @Test
    public void keysAreIgnoredDuringAPointerSeek() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            fire(hitArea(bar), MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            assertEquals(0.25, bar.getProgress(), EPSILON);
            assertTrue(bar.isSeeking());
        });
    }

    /** Verifies removing the skin mid-seek ends the seek. */
    @Test
    public void removingTheSkinMidSeekEndsTheSeek() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            fire(hitArea(bar), MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            assertTrue(bar.isSeeking());
            // JFX 17 ignores a new skin of the installed skin's class; null disposes it.
            bar.setSkin(null);
            assertFalse(bar.isSeeking());
        });
    }

    /** Verifies accessibility reports the clamped value and its increment commits once. */
    @Test
    public void accessibilityReportsClampedValuesAndCommits() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(1.5), 1.0);
            assertEquals(1.0, (double) bar.queryAccessibleAttribute(AccessibleAttribute.VALUE), EPSILON);
            bar.setProgress(0.5);
            List<Boolean> seeking = recordSeeking(bar);
            bar.executeAccessibleAction(AccessibleAction.INCREMENT);
            assertEquals(0.55, bar.getProgress(), EPSILON);
            bar.executeAccessibleAction(AccessibleAction.SET_VALUE, 2.0);
            assertEquals(1.0, bar.getProgress(), EPSILON);
            assertEquals(List.of(true, false, true, false), seeking);
        });
    }

    /** Verifies the minimum width leaves a track two thumbs long. */
    @Test
    public void minimumWidthLeavesATrackTwoThumbsLong() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            assertEquals(3.0 * THUMB, bar.minWidth(-1.0), EPSILON);
            layout((Pane) bar.getParent(), bar, bar.minWidth(-1.0));
            assertEquals(2.0 * THUMB, bar.lookup(".track").getLayoutBounds().getWidth(), EPSILON);
        });
    }

    /** Verifies a disabled seek bar fades. */
    @Test
    public void disabledSeekBarFades() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            bar.setDisable(true);
            bar.getParent().applyCss();
            assertEquals(0.4, bar.getOpacity(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static RXSeekBar laidOut(RXSeekBar bar, double scale) {
        Pane root = new Pane(bar);
        stageFor(root, scale);
        layout(root, bar, WIDTH);
        return bar;
    }

    private static void stageFor(Pane root, double scale) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setRenderScaleX(scale);
        stage.setRenderScaleY(scale);
    }

    private static void layout(Pane root, RXSeekBar bar, double width) {
        root.applyCss();
        bar.resize(width, bar.prefHeight(width));
        bar.layout();
    }

    private static Node hitArea(RXSeekBar bar) {
        // The unstyled first child: the press target that spans the content area.
        return bar.getChildrenUnmodifiable().get(0);
    }

    private static List<Boolean> recordSeeking(RXSeekBar bar) {
        List<Boolean> values = new ArrayList<>();
        bar.seekingProperty().addListener((obs, was, now) -> values.add(now));
        return values;
    }

    private static void fire(Node target, EventType<MouseEvent> type, double localX, MouseButton button) {
        double localY = target.getLayoutBounds().getHeight() / 2.0;
        Point2D scene = target.localToScene(localX, localY);
        boolean primaryDown = button == MouseButton.PRIMARY && type != MouseEvent.MOUSE_RELEASED;
        MouseEvent event = new MouseEvent(type, localX, localY, scene.getX(), scene.getY(), button, 1,
                false, false, false, false,
                primaryDown, false, button == MouseButton.SECONDARY,
                false, false, true,
                new PickResult(target, scene.getX(), scene.getY()));
        Event.fireEvent(target, event);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }
}
