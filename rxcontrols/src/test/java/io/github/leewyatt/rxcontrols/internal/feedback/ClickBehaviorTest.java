package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the standard button behavior wired onto a button whose skin brings
 * none: mouse and key activation, the arming veto, event consumption, focus
 * loss and the default and cancel accelerators.
 */
public class ClickBehaviorTest {

    private static final boolean MAC = RXOS.isMacOS();
    private static final KeyCombination ENTER = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCombination ESCAPE = new KeyCodeCombination(KeyCode.ESCAPE);

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
    public void primaryPressArmsAndReleaseFiresOnce() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        assertEquals(1, fixture.actions.get());

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(1, fixture.actions.get());

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.SECONDARY));
        assertEquals(1, fixture.actions.get());

        // Every mouse event the behavior handles is consumed.
        assertEquals(0, fixture.unconsumedMouseEvents.get());
    }

    @Test
    public void throwingActionDoesNotLeaveTheButtonArmed() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        button.setOnAction(event -> {
            throw new IllegalStateException("failed on purpose");
        });

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        assertThrows(IllegalStateException.class, () ->
                Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY)));

        assertFalse(button.isArmed());
    }

    @Test
    public void pressThatMayNotArmNeitherArmsNorFires() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        fixture.canArm.set(false);

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertFalse(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));

        assertEquals(0, fixture.actions.get());
        // Still the focused button's key: nothing else may act on it.
        assertEquals(0, fixture.unconsumedKeyPresses.get());
        assertEquals(0, fixture.unconsumedKeyReleases.get());

        fixture.canArm.set(true);
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));
        assertEquals(1, fixture.actions.get());
    }

    @Test
    public void draggingBackInArmsAgainOnlyWhileThePressMayArm() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        button.press(true);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        fixture.canArm.set(false);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());

        // Entering without the mouse button down never arms.
        fixture.canArm.set(true);
        button.press(false);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        assertEquals(0, fixture.actions.get());
    }

    @Test
    public void pressThatCouldNotArmStaysWithoutEffectUntilReleased() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        fixture.canArm.set(false);

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        button.press(true);
        fixture.canArm.set(true);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(0, fixture.actions.get());

        // A new press works again, and so does dragging it out and back in.
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(1, fixture.actions.get());
    }

    @Test
    public void pressAfterARefusedOneWhoseReleaseNeverArrivedWorksInFull() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        fixture.canArm.set(false);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        fixture.canArm.set(true);

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        button.press(true);
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(1, fixture.actions.get());
    }

    @Test
    public void releasingAnotherButtonDoesNotEndARefusedPress() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        fixture.canArm.set(false);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        button.press(true);

        // A click of the secondary button while the primary one stays down,
        // which keeps the button pressed.
        Event.fireEvent(button, secondaryWhilePrimaryIsDown(MouseEvent.MOUSE_PRESSED));
        Event.fireEvent(button, secondaryWhilePrimaryIsDown(MouseEvent.MOUSE_RELEASED));
        assertTrue(button.isPressed());
        fixture.canArm.set(true);
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.PRIMARY));

        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(0, fixture.actions.get());
    }

    @Test
    public void disposedBehaviorLetsGoOfAKeyThatIsStillDown() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertTrue(button.isArmed());

        fixture.disposer.dispose();

        assertFalse(button.isArmed());
        assertEquals(0, fixture.actions.get());
    }

    @Test
    public void keyHeldSinceItCouldNotArmStaysWithoutEffectUntilReleased() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        fixture.canArm.set(false);

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        fixture.canArm.set(true);
        // The repeats of the held key.
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertFalse(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));
        assertEquals(0, fixture.actions.get());
        assertEquals(0, fixture.unconsumedKeyPresses.get());

        // A new stroke works again.
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertTrue(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));
        assertEquals(1, fixture.actions.get());
    }

    @Test
    public void focusLossEndsAStrokeThatCouldNotArm() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        button.focus(true);
        fixture.canArm.set(false);

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        // The release goes to whatever took the focus.
        button.focus(false);
        button.focus(true);
        fixture.canArm.set(true);
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));

        assertTrue(button.isArmed());
    }

    @Test
    public void activationKeysFireAndAreConsumed() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertTrue(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));
        assertFalse(button.isArmed());
        assertEquals(1, fixture.actions.get());
        assertEquals(0, fixture.unconsumedKeyPresses.get());
        assertEquals(0, fixture.unconsumedKeyReleases.get());

        // ENTER activates everywhere but on a Mac, where it is left alone.
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER, false));
        assertEquals(MAC ? 1 : 2, fixture.actions.get());
        assertEquals(MAC ? 1 : 0, fixture.unconsumedKeyPresses.get());
        assertEquals(MAC ? 1 : 0, fixture.unconsumedKeyReleases.get());
    }

    @Test
    public void otherKeysAndModifiedActivationKeysStayUnconsumed() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.TAB, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.TAB, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.RIGHT, false));
        assertEquals(2, fixture.unconsumedKeyPresses.get());
        assertEquals(1, fixture.unconsumedKeyReleases.get());

        // Both halves of a modified stroke are left for a shortcut.
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, true));
        assertFalse(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, true));
        assertEquals(3, fixture.unconsumedKeyPresses.get());
        assertEquals(2, fixture.unconsumedKeyReleases.get());
        assertEquals(0, fixture.actions.get());

        // A plain release is consumed even when the press was not seen here.
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));
        assertEquals(2, fixture.unconsumedKeyReleases.get());
    }

    @Test
    public void releaseOfAnArmingStrokeFiresEvenWithAModifierDown() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, true));

        assertEquals(1, fixture.actions.get());
        assertEquals(0, fixture.unconsumedKeyReleases.get());
    }

    @Test
    public void focusLossWhileKeyDownDisarmsWithoutFiring() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;
        button.focus(true);

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));
        assertTrue(button.isArmed());
        button.focus(false);
        assertFalse(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE, false));

        assertEquals(0, fixture.actions.get());
    }

    @Test
    public void disposedBehaviorNoLongerReacts() {
        Fixture fixture = new Fixture();
        BareButton button = fixture.button;

        fixture.disposer.dispose();
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE, false));

        assertFalse(button.isArmed());
        assertEquals(1, fixture.unconsumedMouseEvents.get());
        assertEquals(1, fixture.unconsumedKeyPresses.get());
    }

    // The accelerators only fire for a button in a showing window, so this
    // test needs a real stage.
    @Test
    public void defaultAndCancelAcceleratorsFollowTheCurrentScene() throws InterruptedException {
        onFx(() -> {
            BareButton button = new BareButton();
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            button.setDefaultButton(true);
            Scene first = new Scene(new StackPane(button), 200.0, 100.0);
            SkinDisposer disposer = new SkinDisposer();
            new ClickBehavior(button, () -> true).install(disposer);
            Stage stage = new Stage();
            stage.setScene(first);
            stage.show();
            try {
                // Installed for the scene the button was already in.
                assertTrue(first.getAccelerators().containsKey(ENTER));
                button.setCancelButton(true);
                first.getAccelerators().get(ENTER).run();
                first.getAccelerators().get(ESCAPE).run();
                assertEquals(2, actions.get());

                button.setCancelButton(false);
                assertFalse(first.getAccelerators().containsKey(ESCAPE));

                // Hidden through an ancestor: still in the scene, but not activated.
                first.getRoot().setVisible(false);
                first.getAccelerators().get(ENTER).run();
                assertEquals(2, actions.get());
                first.getRoot().setVisible(true);

                button.setDisable(true);
                first.getAccelerators().get(ENTER).run();
                assertEquals(2, actions.get());
                button.setDisable(false);

                ((StackPane) first.getRoot()).getChildren().clear();
                Scene second = new Scene(new StackPane(button), 200.0, 100.0);
                assertFalse(first.getAccelerators().containsKey(ENTER));
                assertTrue(second.getAccelerators().containsKey(ENTER));

                // An accelerator someone else installed since is not removed.
                Runnable foreign = () -> { };
                button.setCancelButton(true);
                second.getAccelerators().put(ESCAPE, foreign);
                disposer.dispose();
                assertFalse(second.getAccelerators().containsKey(ENTER));
                assertEquals(foreign, second.getAccelerators().get(ESCAPE));
            } finally {
                stage.hide();
            }
        });
    }

    // ==================== Helpers ====================

    private static MouseEvent secondaryWhilePrimaryIsDown(EventType<MouseEvent> type) {
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, MouseButton.SECONDARY, 1,
                false, false, false, false,
                true, false, type == MouseEvent.MOUSE_PRESSED,
                false, false, false, null);
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> type, MouseButton button) {
        boolean pressed = type == MouseEvent.MOUSE_PRESSED;
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, button, 1,
                false, false, false, false,
                pressed && button == MouseButton.PRIMARY, false,
                pressed && button == MouseButton.SECONDARY,
                false, false, false, null);
    }

    private static KeyEvent keyEvent(EventType<KeyEvent> type, KeyCode code, boolean controlDown) {
        return new KeyEvent(type, "", "", code, false, controlDown, false, false);
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

    // A button in a scene with the behavior installed. Dispatch works on
    // event copies, so consumption is observed from the parent: its handlers
    // only see what the button left unconsumed.
    private static final class Fixture {

        final BareButton button = new BareButton();
        final SkinDisposer disposer = new SkinDisposer();
        final AtomicBoolean canArm = new AtomicBoolean(true);
        final AtomicInteger actions = new AtomicInteger();
        final AtomicInteger unconsumedMouseEvents = new AtomicInteger();
        final AtomicInteger unconsumedKeyPresses = new AtomicInteger();
        final AtomicInteger unconsumedKeyReleases = new AtomicInteger();

        Fixture() {
            StackPane parent = new StackPane(button);
            new Scene(parent, 200.0, 100.0);
            parent.addEventHandler(MouseEvent.ANY, event -> unconsumedMouseEvents.incrementAndGet());
            parent.addEventHandler(KeyEvent.KEY_PRESSED, event -> unconsumedKeyPresses.incrementAndGet());
            parent.addEventHandler(KeyEvent.KEY_RELEASED, event -> unconsumedKeyReleases.incrementAndGet());
            button.setOnAction(event -> actions.incrementAndGet());
            new ClickBehavior(button, canArm::get).install(disposer);
        }
    }

    // A button whose skin brings no behavior of its own. Without a focused
    // window Node.focused never turns true, so the test drives it through
    // the protected setter.
    private static final class BareButton extends Button {

        BareButton() {
            super("Go");
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new SkinBase<Button>(this) {
            };
        }

        void focus(boolean value) {
            setFocused(value);
        }

        // Node.pressed is set by the scene's mouse handling, which a
        // synthetic event fired at the button does not go through.
        void press(boolean value) {
            setPressed(value);
        }
    }
}
