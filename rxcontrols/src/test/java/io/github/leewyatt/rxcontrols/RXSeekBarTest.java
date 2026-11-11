package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Verifies the bubble shows only with a factory, tracks the drag and hides when the factory goes. */
    @Test
    public void bubbleShowsWhileSeekingOnlyWithAFactory() throws Exception {
        AtomicReference<RXSeekBar> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            ref.set(bar);
            Node hitArea = hitArea(bar);
            Node indicator = bar.lookup(".value-indicator");
            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            assertFalse(indicator.isVisible());
            fire(hitArea, MouseEvent.MOUSE_RELEASED, 6.0 + 50.0, MouseButton.PRIMARY);

            bar.setIndicatorTextFactory(p -> "at " + p);
            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            assertTrue(indicator.isVisible());
            assertEquals("at 0.25", bubbleText(bar));
            fire(hitArea, MouseEvent.MOUSE_DRAGGED, 6.0 + 150.0, MouseButton.PRIMARY);
            assertEquals("at 0.75", bubbleText(bar));
            bar.setIndicatorTextFactory(p -> null);
            assertNull(bubbleText(bar));
            bar.setIndicatorTextFactory(null);
        });
        // The hide fades out on the animation timer.
        Thread.sleep(600);
        runOnFx(() -> assertFalse(ref.get().lookup(".value-indicator").isVisible()));
    }

    /** Verifies the bubble sits above the thumb and its caret points at the thumb, even at the ends. */
    @Test
    public void caretPointsAtTheThumbNearTheEnds() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(), 1.0);
            bar.setIndicatorTextFactory(p -> "00:00 / 03:45");
            Region indicator = (Region) bar.lookup(".value-indicator");
            Region caret = (Region) indicator.lookup(".caret");
            Region thumb = (Region) bar.lookup(".thumb");
            for (double progress : new double[] {0.0, 0.5, 1.0}) {
                bar.setProgress(progress);
                bar.layout();
                indicator.layout();
                // Layout positions only: the show transition scales the bubble.
                double caretX = indicator.getLayoutX() + caret.getLayoutX() + caret.getWidth() / 2.0;
                double thumbX = thumb.getLayoutX() + thumb.getWidth() / 2.0;
                assertEquals(thumbX, caretX, 1.0, "progress " + progress);
                assertTrue(indicator.getLayoutY() + indicator.getHeight() <= thumb.getLayoutY(),
                        "above the thumb at progress " + progress);
            }
        });
    }

    /** Verifies the bubble shows for keyboard focus but not for focus alone. */
    @Test
    public void bubbleShowsForTheKeyboard() throws Exception {
        runOnFx(() -> {
            FocusableSeekBar bar = new FocusableSeekBar();
            laidOut(bar, 1.0);
            bar.setIndicatorTextFactory(p -> "at " + p);
            Node indicator = bar.lookup(".value-indicator");
            bar.requestFocus();
            assertTrue(bar.isFocused());
            assertFalse(indicator.isVisible());
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            assertTrue(indicator.isVisible());
            assertEquals("at 0.05", bubbleText(bar));
        });
    }

    /** Verifies a click shows no focus shadow, a key shows it, and removing the skin clears the state. */
    @Test
    public void focusCueShowsOnlyForTheKeyboard() throws Exception {
        runOnFx(() -> {
            FocusableSeekBar bar = new FocusableSeekBar();
            laidOut(bar, 1.0);
            Node hitArea = hitArea(bar);
            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            fire(hitArea, MouseEvent.MOUSE_RELEASED, 6.0 + 50.0, MouseButton.PRIMARY);
            Node thumb = bar.lookup(".thumb");
            assertTrue(bar.isFocused());
            assertFalse(focusShown(bar));
            bar.applyCss();
            assertNull(thumb.getEffect());
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            assertTrue(focusShown(bar));
            bar.applyCss();
            assertNotNull(thumb.getEffect());
            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 50.0, MouseButton.PRIMARY);
            assertFalse(focusShown(bar));
            fire(hitArea, MouseEvent.MOUSE_RELEASED, 6.0 + 50.0, MouseButton.PRIMARY);
            Event.fireEvent(bar, key(KeyCode.RIGHT));
            bar.setSkin(null);
            assertFalse(focusShown(bar));
        });
    }

    private static boolean focusShown(RXSeekBar bar) {
        return bar.getPseudoClassStates().contains(PseudoClass.getPseudoClass("focus-shown"));
    }

    /** Verifies the hover layer runs to the pointer, hides for a press, returns on release and leaves on exit. */
    @Test
    public void hoverLayerFollowsThePointer() throws Exception {
        runOnFx(() -> {
            RXSeekBar bar = laidOut(new RXSeekBar(0.2), 1.0);
            Node hitArea = hitArea(bar);
            Region hoverBar = (Region) bar.lookup(".hover-bar");
            assertFalse(hoverBar.isVisible());

            fire(hitArea, MouseEvent.MOUSE_MOVED, 6.0 + 150.0, MouseButton.NONE);
            bar.layout();
            assertTrue(hoverBar.isVisible());
            assertEquals(150.0, hoverBar.getWidth(), EPSILON);

            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 100.0, MouseButton.PRIMARY);
            bar.layout();
            assertFalse(hoverBar.isVisible());
            fire(hitArea, MouseEvent.MOUSE_RELEASED, 6.0 + 100.0, MouseButton.PRIMARY);
            bar.layout();
            assertTrue(hoverBar.isVisible());
            assertEquals(100.0, hoverBar.getWidth(), EPSILON);

            fire(bar, MouseEvent.MOUSE_EXITED, 6.0 + 100.0, MouseButton.NONE);
            bar.layout();
            assertFalse(hoverBar.isVisible());
        });
    }

    /** Verifies the bubble shows the hovered position at the pointer, and a key hands it back to the thumb. */
    @Test
    public void bubbleShowsTheHoveredPosition() throws Exception {
        runOnFx(() -> {
            FocusableSeekBar bar = new FocusableSeekBar();
            bar.setProgress(0.2);
            laidOut(bar, 1.0);
            bar.requestFocus();
            bar.setIndicatorTextFactory(p -> "at " + p);
            Region indicator = (Region) bar.lookup(".value-indicator");
            Region caret = (Region) indicator.lookup(".caret");

            fire(hitArea(bar), MouseEvent.MOUSE_MOVED, 6.0 + 150.0, MouseButton.NONE);
            bar.layout();
            indicator.layout();
            assertTrue(indicator.isVisible());
            assertEquals("at 0.75", bubbleText(bar));
            double caretX = indicator.getLayoutX() + caret.getLayoutX() + caret.getWidth() / 2.0;
            assertEquals(6.0 + 150.0, caretX, 1.0);
            assertEquals(0.2, bar.getProgress(), EPSILON);

            Event.fireEvent(bar, key(KeyCode.RIGHT));
            bar.layout();
            assertEquals("at 0.25", bubbleText(bar));
            assertFalse(bar.lookup(".hover-bar").isVisible());
        });
    }

    /** Verifies touch input never leaves a hover behind, and a key that does not seek keeps the hover. */
    @Test
    public void touchNeverHoversAndOtherKeysKeepTheHover() throws Exception {
        runOnFx(() -> {
            FocusableSeekBar bar = new FocusableSeekBar();
            laidOut(bar, 1.0);
            bar.requestFocus();
            Node hitArea = hitArea(bar);
            Region hoverBar = (Region) bar.lookup(".hover-bar");

            fire(hitArea, MouseEvent.MOUSE_MOVED, 6.0 + 50.0, MouseButton.NONE);
            Event.fireEvent(bar, key(KeyCode.SHIFT));
            bar.layout();
            assertTrue(hoverBar.isVisible());

            fire(hitArea, MouseEvent.MOUSE_PRESSED, 6.0 + 100.0, MouseButton.PRIMARY);
            Point2D scene = hitArea.localToScene(6.0 + 100.0, hitArea.getLayoutBounds().getHeight() / 2.0);
            Event.fireEvent(hitArea, new MouseEvent(MouseEvent.MOUSE_RELEASED, scene.getX(), scene.getY(),
                    scene.getX(), scene.getY(), MouseButton.PRIMARY, 1,
                    false, false, false, false,
                    false, false, false,
                    true, false, true,
                    new PickResult(hitArea, scene.getX(), scene.getY())));
            bar.layout();
            assertFalse(bar.isSeeking());
            assertFalse(hoverBar.isVisible());
        });
    }

    /** A seek bar whose focus request takes effect in a headless scene. */
    private static final class FocusableSeekBar extends RXSeekBar {

        @Override
        public void requestFocus() {
            super.requestFocus();
            if (getScene() != null && getScene().getFocusOwner() == this) {
                setFocused(true);
            }
        }
    }

    // ==================== Helpers ====================

    private static String bubbleText(RXSeekBar bar) {
        return ((Label) bar.lookup(".value-indicator > .label")).getText();
    }

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
