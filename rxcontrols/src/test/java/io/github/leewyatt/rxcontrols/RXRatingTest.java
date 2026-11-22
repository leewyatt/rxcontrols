package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXRatingSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXRating}: exact drawing, hover preview against the committed value,
 * pointer, keyboard and accessibility input, clearing, read-only and skin replacement.
 */
public class RXRatingTest {

    private static final double EPSILON = 1.0e-9;
    /** Default icon width and the distance between the starts of two icons. */
    private static final double ICON = 20.0;
    private static final double PITCH = 24.0;
    /** The clip starts this far before the icon. */
    private static final double CLIP_MARGIN = 2.0;

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

    // ==================== Contract ====================

    /** Verifies the defaults: not rated, five whole-step icons, clearable, editable. */
    @Test
    public void defaults() throws Exception {
        runOnFx(() -> {
            RXRating rating = new RXRating();
            assertNull(rating.getValue());
            assertNull(rating.getHoverValue());
            assertEquals(5, rating.getMax());
            assertEquals(RXRating.Step.WHOLE, rating.getStep());
            assertNull(rating.getKeyboardStep());
            assertTrue(rating.isClearable());
            assertFalse(rating.isReadOnly());
            assertEquals(AccessibleRole.SLIDER, rating.getAccessibleRole());
            assertTrue(rating.getStyleClass().contains("rx-rating"));
            List<Node> items = items(laidOut(rating));
            assertTrue(items.get(0).getStyleClass().contains("item-1"));
            assertTrue(items.get(4).getStyleClass().contains("item-5"));
            assertEquals(3.5, new RXRating(3.5).getValue(), EPSILON);
        });
    }

    /** Verifies the preferred size follows the icon count and the gap, and is also the maximum. */
    @Test
    public void sizeFollowsIconsAndGap() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            assertEquals(5 * ICON + 4 * 4.0, rating.prefWidth(-1), EPSILON);
            assertEquals(rating.prefWidth(-1), rating.maxWidth(-1), EPSILON);
            rating.setGap(10.0);
            assertEquals(5 * ICON + 4 * 10.0, rating.prefWidth(-1), EPSILON);
            rating.setGap(Double.NaN);
            assertEquals(5 * ICON, rating.prefWidth(-1), EPSILON);
            assertTrue(RXRating.getClassCssMetaData().stream().anyMatch(m -> "-rx-gap".equals(m.getProperty())));
        });
    }

    /** Verifies a read-only rating carries the pseudo-class and leaves the focus traversal. */
    @Test
    public void readOnlyLeavesFocusTraversal() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            assertTrue(rating.isFocusTraversable());
            rating.setReadOnly(true);
            rating.applyCss();
            assertTrue(rating.getPseudoClassStates().contains(PseudoClass.getPseudoClass("read-only")));
            assertFalse(rating.isFocusTraversable());
            rating.setReadOnly(false);
            rating.applyCss();
            assertTrue(rating.isFocusTraversable());
        });
    }

    // ==================== Drawing ====================

    /** Verifies 3.7 fills three icons and 70% of the fourth, whatever the step. */
    @Test
    public void fractionalValueIsDrawnExactly() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(3.7));
            assertFills(rating, 1.0, 1.0, 1.0, 0.7, 0.0);
            rating.setStep(RXRating.Step.HALF);
            relayout(rating);
            assertFills(rating, 1.0, 1.0, 1.0, 0.7, 0.0);
            assertEquals(3.7, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a missing, zero, negative, non-finite or too large value draws without changing it. */
    @Test
    public void outOfRangeValuesAreDrawnClampedAndKept() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            assertFills(rating, 0.0, 0.0, 0.0, 0.0, 0.0);
            for (double empty : new double[] {0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY}) {
                rating.setValue(empty);
                assertFills(rating, 0.0, 0.0, 0.0, 0.0, 0.0);
            }
            rating.setValue(9.0);
            assertFills(rating, 1.0, 1.0, 1.0, 1.0, 1.0);
            assertEquals(9.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a new max rebuilds the icons with the value still drawn, and keeps the value. */
    @Test
    public void maxChangeKeepsTheValueDrawn() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(3.0));
            rating.setMax(8);
            relayout(rating);
            assertFills(rating, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            rating.setMax(2);
            relayout(rating);
            assertFills(rating, 1.0, 1.0);
            assertEquals(3.0, rating.getValue(), EPSILON);
            rating.setMax(-1);
            relayout(rating);
            assertEquals(0, items(rating).size());
            assertEquals(0.0, rating.prefWidth(-1), EPSILON);
            fire(rating, MouseEvent.MOUSE_PRESSED, 10.0, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_RELEASED, 10.0, MouseButton.PRIMARY, false);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(3.0, rating.getValue(), EPSILON);
        });
    }

    // ==================== Preview and commit ====================

    /** Verifies hovering previews a value without touching the committed one, and leaving ends it. */
    @Test
    public void hoverPreviewsWithoutCommitting() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(2.0));
            Recorder recorder = new Recorder(rating);
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            assertEquals(2.0, rating.getValue(), EPSILON);
            assertFills(rating, 1.0, 1.0, 1.0, 1.0, 0.0);
            assertTrue(isTarget(items(rating).get(3)));
            assertTrue(rating.getPseudoClassStates().contains(PseudoClass.getPseudoClass("previewing")));
            fire(rating, MouseEvent.MOUSE_EXITED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertNull(rating.getHoverValue());
            assertFills(rating, 1.0, 1.0, 0.0, 0.0, 0.0);
            assertFalse(isTarget(items(rating).get(3)));
            assertEquals(0, recorder.values.size());
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies a click commits once and the action sees the new value; code writes fire no action. */
    @Test
    public void clickCommitsOnceAndCodeWritesStaySilent() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            Recorder recorder = new Recorder(rating);
            click(rating, 2 * PITCH + 10.0);
            assertEquals(3.0, rating.getValue(), EPSILON);
            assertEquals(List.of(3.0), recorder.actions);
            assertEquals(3.0, rating.getHoverValue(), EPSILON);
            rating.setValue(1.0);
            assertEquals(List.of(3.0), recorder.actions);
        });
    }

    /** Verifies the whole-step and half-step values picked at the edges and the middle of an icon. */
    @Test
    public void pointerPicksTheStepAtOrAboveIt() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            rating.setClearable(false);
            assertEquals(1.0, pick(rating, 0.0), EPSILON);
            assertEquals(5.0, pick(rating, 4 * PITCH + ICON), EPSILON);
            rating.setStep(RXRating.Step.HALF);
            assertEquals(0.5, pick(rating, 0.0), EPSILON);
            assertEquals(2.5, pick(rating, 2 * PITCH + 5.0), EPSILON);
            assertEquals(2.5, pick(rating, 2 * PITCH + 10.0), EPSILON);
            assertEquals(3.0, pick(rating, 2 * PITCH + 15.0), EPSILON);
            assertEquals(5.0, pick(rating, 4 * PITCH + ICON), EPSILON);
        });
    }

    /** Verifies the any step picks the exact place in an icon, never zero, and whole values in a gap. */
    @Test
    public void anyStepPicksTheExactPlace() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            rating.setStep(RXRating.Step.ANY);
            fire(rating, MouseEvent.MOUSE_MOVED, 2 * PITCH + 5.0, MouseButton.NONE, false);
            assertEquals(2.25, rating.getHoverValue(), EPSILON);
            fire(rating, MouseEvent.MOUSE_MOVED, 2 * PITCH + 6.0, MouseButton.NONE, false);
            assertEquals(2.3, rating.getHoverValue(), EPSILON);
            rating.setClearable(false);
            assertEquals(0.1, pick(rating, 0.0), EPSILON);
            assertEquals(3.7, pick(rating, 3 * PITCH + 14.0), EPSILON);
            assertEquals(2.0, pick(rating, PITCH + ICON + 1.0), EPSILON);
            assertEquals(2.0, pick(rating, PITCH + ICON + 3.0), EPSILON);
            assertEquals(5.0, pick(rating, 4 * PITCH + ICON), EPSILON);

            rating.setClearable(true);
            rating.setValue(null);
            click(rating, 3 * PITCH + 14.0);
            click(rating, 3 * PITCH + 14.0);
            assertNull(rating.getValue());
        });
    }

    /** Verifies the keyboard moves in tenths under the any step, and by its own step once set. */
    @Test
    public void keyboardStepFollowsTheStepUnlessSet() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(0.2));
            rating.setStep(RXRating.Step.ANY);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(0.3, rating.getValue(), 0.0);
            rating.setValue(3.46);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(3.5, rating.getValue(), 0.0);
            rating.setValue(3.46);
            Event.fireEvent(rating, key(KeyCode.LEFT));
            assertEquals(3.4, rating.getValue(), 0.0);
            Event.fireEvent(rating, key(KeyCode.HOME));
            assertEquals(0.1, rating.getValue(), 0.0);
            Event.fireEvent(rating, key(KeyCode.LEFT));
            assertNull(rating.getValue());

            rating.setKeyboardStep(RXRating.Step.HALF);
            rating.setValue(3.46);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(3.5, rating.getValue(), 0.0);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(4.0, rating.getValue(), 0.0);

            rating.setStep(RXRating.Step.HALF);
            rating.setKeyboardStep(RXRating.Step.WHOLE);
            rating.setValue(2.5);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(3.0, rating.getValue(), 0.0);
            rating.setKeyboardStep(RXRating.Step.ANY);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(3.1, rating.getValue(), 0.0);
            rating.executeAccessibleAction(AccessibleAction.SET_VALUE, 2.26);
            assertEquals(2.3, rating.getValue(), 0.0);
        });
    }

    /** Verifies a point in the gap belongs to the nearest icon, the middle to the earlier one. */
    @Test
    public void gapBelongsToTheNearestIcon() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            rating.setClearable(false);
            assertEquals(2.0, pick(rating, PITCH + ICON + 1.0), EPSILON);
            assertEquals(2.0, pick(rating, PITCH + ICON + 2.0), EPSILON);
            assertEquals(3.0, pick(rating, PITCH + ICON + 3.0), EPSILON);
        });
    }

    /** Verifies clicking the current value clears it and holds the preview back until the pointer moves on. */
    @Test
    public void clickingTheCurrentValueClearsAndHoldsThePreview() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(3.0));
            Recorder recorder = new Recorder(rating);
            click(rating, 2 * PITCH + 10.0);
            assertNull(rating.getValue());
            assertEquals(1, recorder.actions.size());
            assertNull(recorder.actions.get(0));
            assertNull(rating.getHoverValue());
            fire(rating, MouseEvent.MOUSE_MOVED, 2 * PITCH + 12.0, MouseButton.NONE, false);
            assertNull(rating.getHoverValue());
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            fire(rating, MouseEvent.MOUSE_MOVED, 2 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(3.0, rating.getHoverValue(), EPSILON);
        });
    }

    /** Verifies a rating that is not clearable keeps its value and stays silent on a repeated click. */
    @Test
    public void notClearableIgnoresARepeatedClick() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(3.0));
            rating.setClearable(false);
            Recorder recorder = new Recorder(rating);
            click(rating, 2 * PITCH + 10.0);
            assertEquals(3.0, rating.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies dragging out before the release commits nothing, and other buttons do nothing. */
    @Test
    public void releaseOutsideAndOtherButtonsCommitNothing() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(2.0));
            Recorder recorder = new Recorder(rating);
            fire(rating, MouseEvent.MOUSE_PRESSED, 3 * PITCH + 10.0, MouseButton.PRIMARY, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            fire(rating, MouseEvent.MOUSE_DRAGGED, 400.0, MouseButton.PRIMARY, false);
            assertNull(rating.getHoverValue());
            fire(rating, MouseEvent.MOUSE_RELEASED, 400.0, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_PRESSED, 3 * PITCH + 10.0, MouseButton.SECONDARY, false);
            fire(rating, MouseEvent.MOUSE_RELEASED, 3 * PITCH + 10.0, MouseButton.SECONDARY, false);
            assertEquals(2.0, rating.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies a touch commits once and leaves no preview, and a synthesized move previews nothing. */
    @Test
    public void touchCommitsWithoutLeavingAPreview() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            Recorder recorder = new Recorder(rating);
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, true);
            assertNull(rating.getHoverValue());
            fire(rating, MouseEvent.MOUSE_PRESSED, 3 * PITCH + 10.0, MouseButton.PRIMARY, true);
            fire(rating, MouseEvent.MOUSE_RELEASED, 3 * PITCH + 10.0, MouseButton.PRIMARY, true);
            assertEquals(4.0, rating.getValue(), EPSILON);
            assertEquals(List.of(4.0), recorder.actions);
            assertNull(rating.getHoverValue());
        });
    }

    /** Verifies the focus mark changes no size, and a point outside the layout bounds previews and commits nothing. */
    @Test
    public void onlyTheLayoutBoundsTakeInput() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            double below = rating.getHeight() + 4.0;
            double x = 3 * PITCH + 10.0;
            assertTrue(rating.contains(x, 1.0));
            assertTrue(rating.contains(ICON + 2.0, 9.0));
            assertFalse(rating.contains(x, below));
            assertFalse(rating.contains(-4.0, 9.0));

            rating.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-shown"), true);
            rating.applyCss();
            assertEquals(5 * ICON + 4 * 4.0, rating.prefWidth(-1), EPSILON);
            fireAt(rating, MouseEvent.MOUSE_MOVED, x, below, MouseButton.NONE, false);
            assertNull(rating.getHoverValue());
            fireAt(rating, MouseEvent.MOUSE_PRESSED, x, below, MouseButton.PRIMARY, false);
            fireAt(rating, MouseEvent.MOUSE_RELEASED, x, below, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_PRESSED, x, MouseButton.PRIMARY, false);
            fireAt(rating, MouseEvent.MOUSE_RELEASED, x, below, MouseButton.PRIMARY, false);
            assertNull(rating.getValue());
        });
    }

    /** Verifies a modified primary press, such as a macOS Control-click, rates and clears nothing. */
    @Test
    public void modifiedPressIsNotARating() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(3.0));
            Recorder recorder = new Recorder(rating);
            for (double x : new double[] {10.0, 2 * PITCH + 10.0}) {
                Point2D scene = rating.localToScene(x, 9.0);
                Event.fireEvent(rating, new MouseEvent(MouseEvent.MOUSE_PRESSED, x, 9.0, scene.getX(), scene.getY(),
                        MouseButton.PRIMARY, 1, false, true, false, false, true, false, false, false, true, true,
                        new PickResult(rating, scene.getX(), scene.getY())));
                fire(rating, MouseEvent.MOUSE_RELEASED, x, MouseButton.PRIMARY, false);
            }
            assertEquals(3.0, rating.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies a rating removed from its scene mid-press answers the keyboard once it is back. */
    @Test
    public void leavingTheSceneEndsThePress() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(1.0));
            Pane parent = (Pane) rating.getParent();
            fire(rating, MouseEvent.MOUSE_PRESSED, 10.0, MouseButton.PRIMARY, false);
            parent.getChildren().remove(rating);
            assertNull(rating.getHoverValue());
            parent.getChildren().add(rating);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(2.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a listener that makes the rating read-only or bound mid-input stops that input. */
    @Test
    public void inputStopsWhenAListenerClosesTheRating() throws Exception {
        runOnFx(() -> {
            RXRating readOnly = laidOut(new RXRating(1.0));
            Recorder recorder = new Recorder(readOnly);
            fire(readOnly, MouseEvent.MOUSE_MOVED, 2 * PITCH + 10.0, MouseButton.NONE, false);
            readOnly.hoverValueProperty().addListener((obs, was, now) -> {
                if (now == null) {
                    readOnly.setReadOnly(true);
                }
            });
            Event.fireEvent(readOnly, key(KeyCode.RIGHT));
            assertEquals(1.0, readOnly.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());

            RXRating bound = laidOut(new RXRating(1.0));
            fire(bound, MouseEvent.MOUSE_MOVED, 2 * PITCH + 10.0, MouseButton.NONE, false);
            SimpleObjectProperty<Double> source = new SimpleObjectProperty<>(4.0);
            bound.hoverValueProperty().addListener((obs, was, now) -> {
                if (now == null && !bound.valueProperty().isBound()) {
                    bound.valueProperty().bind(source);
                }
            });
            Event.fireEvent(bound, key(KeyCode.RIGHT));
            assertEquals(4.0, bound.getValue(), EPSILON);

            RXRating onFocus = laidOut(new RXRating(1.0));
            onFocus.getScene().focusOwnerProperty().addListener((obs, was, now) -> onFocus.setReadOnly(true));
            fire(onFocus, MouseEvent.MOUSE_PRESSED, 2 * PITCH + 10.0, MouseButton.PRIMARY, false);
            assertTrue(onFocus.isReadOnly());
            assertNull(onFocus.getHoverValue());
        });
    }

    /** Verifies an enter that arrives just ahead of the release does not drop the click. */
    @Test
    public void enterAheadOfTheReleaseKeepsTheClick() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            fire(rating, MouseEvent.MOUSE_PRESSED, 10.0, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_DRAGGED, 400.0, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_ENTERED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            fire(rating, MouseEvent.MOUSE_RELEASED, 3 * PITCH + 10.0, MouseButton.PRIMARY, false);
            assertEquals(4.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a move with the button up after a press whose release was lost previews again. */
    @Test
    public void lostReleaseDoesNotBlockThePreview() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            fire(rating, MouseEvent.MOUSE_PRESSED, 10.0, MouseButton.PRIMARY, false);
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(1.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a listener that puts the old value back leaves the action unfired. */
    @Test
    public void revertedCommitFiresNoAction() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(1.0));
            Recorder recorder = new Recorder(rating);
            rating.valueProperty().addListener((obs, was, now) -> {
                if (now == null || now != 1.0) {
                    rating.setValue(1.0);
                }
            });
            click(rating, 3 * PITCH + 10.0);
            assertEquals(1.0, rating.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies binding the value ends a preview, and the bound source redraws without an action. */
    @Test
    public void bindingEndsThePreviewAndTheSourceRedraws() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(1.0));
            Recorder recorder = new Recorder(rating);
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            // A code write under the preview must not hide the later bind from the skin.
            rating.setValue(1.5);
            SimpleObjectProperty<Double> source = new SimpleObjectProperty<>(2.0);
            rating.valueProperty().bind(source);
            assertNull(rating.getHoverValue());
            assertFills(rating, 1.0, 1.0, 0.0, 0.0, 0.0);
            source.set(3.5);
            assertFills(rating, 1.0, 1.0, 1.0, 0.5, 0.0);
            assertEquals(0, recorder.actions.size());
        });
    }

    /** Verifies a null step picks and steps in whole icons. */
    @Test
    public void nullStepActsAsWhole() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            rating.setStep(null);
            click(rating, 2 * PITCH + 5.0);
            assertEquals(3.0, rating.getValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(4.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies a read-only, a disabled and a bound rating take no pointer or keyboard input. */
    @Test
    public void readOnlyDisabledAndBoundTakeNoInput() throws Exception {
        runOnFx(() -> {
            RXRating readOnly = laidOut(new RXRating(2.0));
            readOnly.setReadOnly(true);
            RXRating disabled = laidOut(new RXRating(2.0));
            disabled.setDisable(true);
            RXRating bound = laidOut(new RXRating());
            bound.valueProperty().bind(new SimpleObjectProperty<>(2.0));
            for (RXRating rating : List.of(readOnly, disabled, bound)) {
                Recorder recorder = new Recorder(rating);
                fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
                assertNull(rating.getHoverValue());
                click(rating, 3 * PITCH + 10.0);
                Event.fireEvent(rating, key(KeyCode.RIGHT));
                rating.executeAccessibleAction(AccessibleAction.INCREMENT);
                assertEquals(2.0, rating.getValue(), EPSILON);
                assertEquals(0, recorder.actions.size());
            }
        });
    }

    /** Verifies switching to read-only ends a running preview. */
    @Test
    public void readOnlyEndsThePreview() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            fire(rating, MouseEvent.MOUSE_MOVED, 10.0, MouseButton.NONE, false);
            assertEquals(1.0, rating.getHoverValue(), EPSILON);
            rating.setReadOnly(true);
            assertNull(rating.getHoverValue());
        });
    }

    /** Verifies the pointer picks from the leading edge under right-to-left orientation. */
    @Test
    public void rightToLeftPicksFromTheRight() throws Exception {
        runOnFx(() -> {
            RXRating rating = new RXRating();
            rating.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            laidOut(rating);
            List<Node> items = items(rating);
            double first = items.get(0).localToScene(items.get(0).getLayoutBounds()).getMinX();
            double last = items.get(4).localToScene(items.get(4).getLayoutBounds()).getMinX();
            assertTrue(first > last);
            Point2D local = rating.sceneToLocal(first + ICON / 2.0, 5.0);
            click(rating, local.getX());
            assertEquals(1.0, rating.getValue(), EPSILON);
        });
    }

    // ==================== Keyboard ====================

    /** Verifies the arrow keys step along the grid, snap an off-grid value and stop at the ends. */
    @Test
    public void arrowKeysStepAlongTheGrid() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            Recorder recorder = new Recorder(rating);
            Event.fireEvent(rating, key(KeyCode.DOWN));
            assertNull(rating.getValue());
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(1.0, rating.getValue(), EPSILON);
            rating.setStep(RXRating.Step.HALF);
            rating.setValue(3.7);
            Event.fireEvent(rating, key(KeyCode.UP));
            assertEquals(4.0, rating.getValue(), EPSILON);
            rating.setValue(3.7);
            Event.fireEvent(rating, key(KeyCode.LEFT));
            assertEquals(3.5, rating.getValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.END));
            assertEquals(5.0, rating.getValue(), EPSILON);
            int actions = recorder.actions.size();
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(actions, recorder.actions.size());
            Event.fireEvent(rating, key(KeyCode.HOME));
            assertEquals(0.5, rating.getValue(), EPSILON);
        });
    }

    /** Verifies Left and Right swap under right-to-left orientation while Up and Down do not. */
    @Test
    public void horizontalArrowsMirrorUnderRightToLeft() throws Exception {
        runOnFx(() -> {
            RXRating rating = new RXRating(2.0);
            rating.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            laidOut(rating);
            Event.fireEvent(rating, key(KeyCode.LEFT));
            assertEquals(3.0, rating.getValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertEquals(2.0, rating.getValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.UP));
            assertEquals(3.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies lowering the smallest value and Delete clear only a clearable rating. */
    @Test
    public void keyboardClearsOnlyWhenClearable() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(1.0));
            Event.fireEvent(rating, key(KeyCode.LEFT));
            assertNull(rating.getValue());
            rating.setValue(3.0);
            Event.fireEvent(rating, key(KeyCode.DELETE));
            assertNull(rating.getValue());

            rating.setClearable(false);
            rating.setValue(1.0);
            Event.fireEvent(rating, key(KeyCode.LEFT));
            KeyEvent delete = key(KeyCode.DELETE);
            Event.fireEvent(rating, delete);
            assertEquals(1.0, rating.getValue(), EPSILON);
            rating.setValue(0.0);
            Event.fireEvent(rating, key(KeyCode.DOWN));
            assertEquals(0.0, rating.getValue(), EPSILON);
        });
    }

    /** Verifies each key commit fires one action that sees the new value, and a chord does nothing. */
    @Test
    public void keyCommitFiresOnceAndChordsAreLeftAlone() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(1.0));
            Recorder recorder = new Recorder(rating);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            Event.fireEvent(rating, key(KeyCode.END));
            Event.fireEvent(rating, key(KeyCode.BACK_SPACE));
            List<Double> expected = new ArrayList<>(List.of(2.0, 5.0));
            expected.add(null);
            assertEquals(expected, recorder.actions);

            rating.setValue(1.0);
            KeyEvent chord = altKey(KeyCode.RIGHT);
            Event.fireEvent(rating, chord);
            assertEquals(1.0, rating.getValue(), EPSILON);
            assertFalse(chord.isConsumed());
            assertEquals(expected, recorder.actions);
        });
    }

    /** Verifies a key commit takes the icons back from a hover preview. */
    @Test
    public void keyCommitEndsThePreview() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(2.0));
            fire(rating, MouseEvent.MOUSE_MOVED, 4 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(5.0, rating.getHoverValue(), EPSILON);
            Event.fireEvent(rating, key(KeyCode.RIGHT));
            assertNull(rating.getHoverValue());
            assertFills(rating, 1.0, 1.0, 1.0, 0.0, 0.0);
        });
    }

    // ==================== Accessibility ====================

    /** Verifies the slider attributes and that the actions commit along the step grid. */
    @Test
    public void accessibilityReportsAndCommits() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            rating.setStep(RXRating.Step.HALF);
            Recorder recorder = new Recorder(rating);
            assertEquals(0.0, (double) rating.queryAccessibleAttribute(AccessibleAttribute.VALUE), EPSILON);
            assertEquals(0.0, (double) rating.queryAccessibleAttribute(AccessibleAttribute.MIN_VALUE), EPSILON);
            assertEquals(5.0, (double) rating.queryAccessibleAttribute(AccessibleAttribute.MAX_VALUE), EPSILON);
            rating.executeAccessibleAction(AccessibleAction.INCREMENT);
            assertEquals(0.5, rating.getValue(), EPSILON);
            rating.executeAccessibleAction(AccessibleAction.SET_VALUE, 3.3);
            assertEquals(3.5, rating.getValue(), EPSILON);
            rating.executeAccessibleAction(AccessibleAction.SET_VALUE, 3.25);
            assertEquals(3.5, rating.getValue(), EPSILON);
            rating.executeAccessibleAction(AccessibleAction.SET_VALUE, 99.0);
            assertEquals(5.0, rating.getValue(), EPSILON);
            rating.executeAccessibleAction(AccessibleAction.DECREMENT);
            assertEquals(4.5, rating.getValue(), EPSILON);
            assertEquals(List.of(0.5, 3.5, 5.0, 4.5), recorder.actions);
            rating.setValue(9.0);
            assertEquals(5.0, (double) rating.queryAccessibleAttribute(AccessibleAttribute.VALUE), EPSILON);
        });
    }

    // ==================== Skin replacement ====================

    /** Verifies a skin that is built but never installed adds no nodes and answers no input. */
    @Test
    public void uninstalledSkinStaysInert() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            int children = rating.getChildrenUnmodifiable().size();
            RXRatingSkin spare = new RXRatingSkin(rating);
            rating.setSkin(spare);
            assertEquals(children, rating.getChildrenUnmodifiable().size());
            Recorder recorder = new Recorder(rating);
            click(rating, 2 * PITCH + 10.0);
            assertEquals(List.of(3.0), recorder.actions);
        });
    }

    /** Verifies removing the skin releases its nodes and preview, and a new skin works once per input. */
    @Test
    public void replacedSkinReleasesAndTheNewOneTakesOver() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating(2.0));
            fire(rating, MouseEvent.MOUSE_MOVED, 3 * PITCH + 10.0, MouseButton.NONE, false);
            assertEquals(4.0, rating.getHoverValue(), EPSILON);
            rating.setSkin(null);
            assertEquals(0, rating.getChildrenUnmodifiable().size());
            assertNull(rating.getHoverValue());
            Recorder recorder = new Recorder(rating);
            click(rating, 3 * PITCH + 10.0);
            assertEquals(0, recorder.actions.size());

            rating.setSkin(new RXRatingSkin(rating));
            relayout(rating);
            assertFills(rating, 1.0, 1.0, 0.0, 0.0, 0.0);
            click(rating, 3 * PITCH + 10.0);
            assertEquals(List.of(4.0), recorder.actions);
        });
    }

    /** Verifies a skin replaced from a value listener stops before it fires the action. */
    @Test
    public void skinReplacedDuringCommitStops() throws Exception {
        runOnFx(() -> {
            RXRating rating = laidOut(new RXRating());
            Recorder recorder = new Recorder(rating);
            rating.valueProperty().addListener((obs, was, now) -> rating.setSkin(null));
            click(rating, 10.0);
            assertEquals(1.0, rating.getValue(), EPSILON);
            assertEquals(0, recorder.actions.size());
            assertNull(rating.getHoverValue());
        });
    }

    /** Verifies listeners registered before the skin may remove it without the old skin failing. */
    @Test
    public void skinRemovedByAnEarlierListenerDoesNotFail() throws Exception {
        runOnFx(() -> {
            List<Throwable> errors = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, error) -> errors.add(error));
            try {
                RXRating byValue = new RXRating();
                byValue.valueProperty().addListener(observable -> byValue.setSkin(null));
                click(laidOut(byValue), 10.0);
                assertEquals(1.0, byValue.getValue(), EPSILON);

                RXRating byHover = new RXRating();
                byHover.hoverValueProperty().addListener(observable -> byHover.setSkin(null));
                fire(laidOut(byHover), MouseEvent.MOUSE_MOVED, 10.0, MouseButton.NONE, false);
                assertNull(byHover.getHoverValue());

                RXRating byMax = new RXRating();
                byMax.maxProperty().addListener(observable -> byMax.setSkin(null));
                laidOut(byMax).setMax(8);
                assertEquals(0, byMax.getChildrenUnmodifiable().size());

                // The preview a key or a new max ends reports to a listener that removes the skin.
                RXRating byKey = laidOut(new RXRating());
                fire(byKey, MouseEvent.MOUSE_MOVED, 10.0, MouseButton.NONE, false);
                byKey.hoverValueProperty().addListener(observable -> byKey.setSkin(null));
                Event.fireEvent(byKey, key(KeyCode.RIGHT));
                assertNull(byKey.getValue());

                RXRating byNewMax = laidOut(new RXRating());
                fire(byNewMax, MouseEvent.MOUSE_MOVED, 10.0, MouseButton.NONE, false);
                byNewMax.hoverValueProperty().addListener(observable -> byNewMax.setSkin(null));
                byNewMax.setMax(8);
                assertEquals(0, byNewMax.getChildrenUnmodifiable().size());
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }
            assertEquals(List.of(), errors);
        });
    }

    // ==================== Helpers ====================

    /** Records every committed-value change and the value each action saw. */
    private static final class Recorder {

        private final List<Double> values = new ArrayList<>();
        private final List<Double> actions = new ArrayList<>();

        private Recorder(RXRating rating) {
            rating.valueProperty().addListener((obs, was, now) -> values.add(now));
            rating.setOnAction(event -> actions.add(rating.getValue()));
        }
    }

    private static RXRating laidOut(RXRating rating) {
        Pane root = new Pane(rating);
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        relayout(rating);
        return rating;
    }

    private static void relayout(RXRating rating) {
        rating.getParent().applyCss();
        rating.resize(rating.prefWidth(-1), rating.prefHeight(-1));
        rating.layout();
    }

    private static List<Node> items(RXRating rating) {
        List<Node> items = new ArrayList<>();
        for (Node child : rating.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("item")) {
                items.add(child);
            }
        }
        return items;
    }

    private static boolean isTarget(Node item) {
        return item.getPseudoClassStates().contains(PseudoClass.getPseudoClass("target"));
    }

    /** Asserts the part of each icon the filled shape shows, from its clip. */
    private static void assertFills(RXRating rating, double... expected) {
        List<Node> items = items(rating);
        assertEquals(expected.length, items.size());
        for (int i = 0; i < expected.length; i++) {
            Node filled = items.get(i).lookup(".filled");
            double shown = 0.0;
            if (filled.isVisible()) {
                Rectangle clip = (Rectangle) filled.getClip();
                shown = Math.min(1.0, (clip.getX() + clip.getWidth()) / ICON);
                assertEquals(-CLIP_MARGIN, clip.getX(), EPSILON);
            }
            assertEquals(expected[i], shown, EPSILON, "icon " + i);
        }
    }

    /** Clicks at the given x and returns the committed value. */
    private static double pick(RXRating rating, double x) {
        rating.setValue(null);
        click(rating, x);
        return rating.getValue();
    }

    private static void click(RXRating rating, double x) {
        fire(rating, MouseEvent.MOUSE_PRESSED, x, MouseButton.PRIMARY, false);
        fire(rating, MouseEvent.MOUSE_RELEASED, x, MouseButton.PRIMARY, false);
    }

    private static void fire(Node target, EventType<MouseEvent> type, double localX, MouseButton button,
                             boolean synthesized) {
        fireAt(target, type, localX, target.getLayoutBounds().getHeight() / 2.0, button, synthesized);
    }

    private static void fireAt(Node target, EventType<MouseEvent> type, double localX, double localY,
                               MouseButton button, boolean synthesized) {
        Point2D scene = target.localToScene(localX, localY);
        boolean primaryDown = button == MouseButton.PRIMARY && type != MouseEvent.MOUSE_RELEASED;
        MouseEvent event = new MouseEvent(type, localX, localY, scene.getX(), scene.getY(), button, 1,
                false, false, false, false,
                primaryDown, false, button == MouseButton.SECONDARY,
                synthesized, false, true,
                new PickResult(target, scene.getX(), scene.getY()));
        Event.fireEvent(target, event);
    }

    private static KeyEvent altKey(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, true, false);
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
