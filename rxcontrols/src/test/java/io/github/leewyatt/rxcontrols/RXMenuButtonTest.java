package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.popup.RXPlacement;
import io.github.leewyatt.rxcontrols.skins.RXMenuButtonSkin;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXMenuButton}: the {@link javafx.scene.control.ButtonBase}
 * contract (fire toggles the menu, disabled is a no-op), the showing flag and
 * placement, the styleable ripple properties, and — under {@code "ui"} — the
 * skin's arrow child, mouse / keyboard toggling, and the showing&harr;popup bridge.
 */
public class RXMenuButtonTest {

    private Stage stage;

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
        Platform.setImplicitExit(false);
    }

    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    // ==================== Construction / defaults ====================

    @Test
    public void defaultsAndStyleClass() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            assertTrue(button.getStyleClass().contains("rx-menu-button"));
            assertSame(AccessibleRole.MENU_BUTTON, button.getAccessibleRole());
            assertFalse(button.isShowing());
            assertNotNull(button.showingProperty());
            assertTrue(button.getItems().isEmpty());
            assertSame(RXPlacement.BOTTOM_START, button.getPlacement());
        });
    }

    @Test
    public void userAgentStylesheetIsRXControls() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            assertEquals(RXResources.USER_AGENT_STYLESHEET, button.getUserAgentStylesheet());
        });
    }

    @Test
    public void defaultSkinIsRXMenuButtonSkin() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            // A scene + applyCss realizes the skin without needing a shown window.
            new Scene(new StackPane(button));
            button.applyCss();
            assertTrue(button.getSkin() instanceof RXMenuButtonSkin);
        });
    }

    @Test
    public void textAndGraphicConstructors() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton text = new RXMenuButton("File");
            assertEquals("File", text.getText());

            Label graphic = new Label("icon");
            RXMenuButton both = new RXMenuButton("Edit", graphic);
            assertEquals("Edit", both.getText());
            assertSame(graphic, both.getGraphic());
        });
    }

    // ==================== Showing / fire ====================

    @Test
    public void showHideDriveShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.show();
            assertTrue(button.isShowing());
            button.hide();
            assertFalse(button.isShowing());
        });
    }

    @Test
    public void fireTogglesShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.fire();
            assertTrue(button.isShowing(), "fire opens when closed");
            button.fire();
            assertFalse(button.isShowing(), "fire closes when open");
        });
    }

    @Test
    public void disabledShowIsNoOp() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.setDisable(true);
            button.show();
            assertFalse(button.isShowing(), "a disabled button does not open");
            button.fire();
            assertFalse(button.isShowing(), "fire is a no-op while disabled");
        });
    }

    @Test
    @Tag("ui")
    public void showWithoutFocusableItemsReconcilesShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton("Menu");
            button.getItems().addAll(RXMenuSeparator.create(), RXMenuHeader.of("Group"));
            showStage(button);
            // The popup declines (no focusable item) even on a realized button;
            // the bridge must not leave showing pinned true with no popup.
            button.show();
            assertFalse(button.isShowing(), "an all-header/separator menu cannot stay showing");
        });
    }

    @Test
    public void showBeforeWindowRealizedReconcilesShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.getItems().add(RXMenuItem.of("A"));
            // A scene without a shown window: the anchor is not realized, so the popup
            // refuses; the bridge must pull showing back to false.
            new Scene(new StackPane(button));
            button.applyCss();
            button.show();
            assertFalse(button.isShowing(), "show() before the window is realized does not pin showing");
        });
    }

    @Test
    public void executeFireAccessibleActionToggles() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.executeAccessibleAction(AccessibleAction.FIRE);
            assertTrue(button.isShowing());
        });
    }

    @Test
    public void placementIsMutableAndNullTolerated() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.setPlacement(RXPlacement.TOP_START);
            assertSame(RXPlacement.TOP_START, button.getPlacement());
            button.setPlacement(null);
            assertSame(null, button.getPlacement(), "null placement is tolerated");
        });
    }

    // ==================== Ripple styleables ====================

    @Test
    public void rippleDefaultsAndSetters() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, button.isRippleEnabled());
            assertEquals(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED, button.isStateOverlayEnabled());
            assertEquals(RXRipplePane.DEFAULT_RIPPLE_OPACITY, button.getRippleOpacity(), 0.0);
            assertEquals(RXRipplePane.DEFAULT_RIPPLE_FILL, button.getRippleFill());

            button.setRippleFill(Color.RED);
            button.setRippleOpacity(0.5);
            button.setRippleEnabled(false);
            button.setStateOverlayEnabled(false);
            assertEquals(Color.RED, button.getRippleFill());
            assertEquals(0.5, button.getRippleOpacity(), 0.0);
            assertFalse(button.isRippleEnabled());
            assertFalse(button.isStateOverlayEnabled());
        });
    }

    @Test
    public void cssMetadataExposesRippleProperties() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            Set<String> props = new HashSet<>();
            for (CssMetaData<? extends Styleable, ?> meta : button.getControlCssMetaData()) {
                props.add(meta.getProperty());
            }
            assertTrue(props.contains("-rx-ripple-fill"));
            assertTrue(props.contains("-rx-ripple-opacity"));
            assertTrue(props.contains("-rx-ripple-enabled"));
            assertTrue(props.contains("-rx-ripple-state-overlay-enabled"));

            List<CssMetaData<? extends Styleable, ?>> classMeta = RXMenuButton.getClassCssMetaData();
            assertSame(classMeta, button.getControlCssMetaData());
        });
    }

    // ==================== Skin: arrow child ====================

    @Test
    @Tag("ui")
    public void skinAddsArrowChild() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("File", "Edit");
            assertNotNull(button.lookup(".arrow"), "the skin adds a trailing .arrow region");
        });
    }

    // ==================== Skin: mouse / keyboard toggle ====================

    @Test
    @Tag("ui")
    public void primaryClickTogglesShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            clickPrimary(button);
            assertTrue(button.isShowing(), "a primary click opens the menu");
            clickPrimary(button);
            assertFalse(button.isShowing(), "a second primary click closes it");
        });
    }

    @Test
    @Tag("ui")
    public void spaceKeyReleaseToggles() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            fireKey(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertFalse(button.isShowing(), "arming on key-press does not open yet");
            fireKey(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(button.isShowing(), "releasing Space fires and opens");
        });
    }

    @Test
    @Tag("ui")
    public void downKeyOpens() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            fireKey(button, KeyEvent.KEY_PRESSED, KeyCode.DOWN);
            assertTrue(button.isShowing(), "Down opens the menu");
        });
    }

    @Test
    @Tag("ui")
    public void spacePressArmsAndReleaseDisarms() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            fireKey(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertTrue(button.isArmed(), "Space press arms the button");
            fireKey(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertFalse(button.isArmed(), "Space release disarms (and toggles the menu)");
        });
    }

    // The companion focus-loss disarm (onFocusChanged: keyDown && !isFocused() ->
    // disarm) cannot be exercised headlessly: in a test window that never gains OS
    // focus, requestFocus() never makes isFocused() true, so the focusedProperty
    // never transitions and the listener never fires. That path mirrors
    // ButtonBehavior.focusChanged (and RXSwitchButtonSkin.handleFocusChanged), is
    // verified by inspection, and is flagged for real-machine confirmation.

    @Test
    @Tag("ui")
    public void disabledClickDoesNotOpen() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            button.setDisable(true);
            clickPrimary(button);
            assertFalse(button.isShowing(), "a disabled button ignores clicks");
        });
    }

    // ==================== Skin: showing <-> popup bridge ====================

    @Test
    @Tag("ui")
    public void showingOpensAndHidingClosesPopupWindow() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            int before = extraShowingWindows();
            button.show();
            assertTrue(button.isShowing());
            assertTrue(extraShowingWindows() > before, "opening the menu realizes a popup window");

            button.hide();
            assertFalse(button.isShowing());
            assertEquals(before, extraShowingWindows(), "hiding the menu closes the popup window");
        });
    }

    @Test
    @Tag("ui")
    public void disposingSkinReleasesTheOpenPopup() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            int before = extraShowingWindows();
            button.show();
            assertTrue(extraShowingWindows() > before, "precondition: popup open");
            Skin<?> skin = button.getSkin();
            assertNotNull(skin);
            // Disposing the skin must dispose the internal RXPopupMenu (support +
            // animations + listeners), closing the popup window it owns.
            skin.dispose();
            assertEquals(before, extraShowingWindows(), "disposing the skin closes the popup");
        });
    }

    @Test
    @Tag("ui")
    public void popupSelfCloseFlipsControlShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            button.show();
            assertTrue(button.isShowing(), "precondition: showing");
            // Owner leaves the scene: the popup auto-closes and the reverse listener
            // pulls the control's showing flag back to false (popup -> control sync).
            ((StackPane) button.getParent()).getChildren().remove(button);
            assertFalse(button.isShowing(), "a popup that closes on its own resets the button");
        });
    }

    @Test
    @Tag("ui")
    public void itemsAreMirroredIntoTheOpenMenu() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("Run", "Save", "Quit");
            button.show();
            assertTrue(button.isShowing());
            // The skin mirrors the button's items into the popup's menu list, so a
            // command cell is rendered in the open popup window.
            assertNotNull(popupLookup(".rx-menu-item"), "items are rendered in the open popup");
        });
    }

    // ==================== PR5: context menu (showAt / installContextMenu) ====================

    @Test
    public void showAtSetsContextAnchorAndShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = new RXMenuButton();
            button.showAt(100, 150);
            assertTrue(button.isShowing());
            assertEquals(new Point2D(100, 150), button.getContextAnchor());

            button.hide();
            assertNull(button.getContextAnchor(), "hide clears the context anchor");
            button.showAt(10, 20);
            button.show();
            assertNull(button.getContextAnchor(), "opening at the button clears the context anchor");
        });
    }

    @Test
    @Tag("ui")
    public void installContextMenuOpensAtScreenPoint() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            Node root = button.getParent();
            button.installContextMenu(root);
            int before = extraShowingWindows();

            root.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    10, 10, 200, 220, false, null));
            assertTrue(button.isShowing(), "a context-menu request opens the menu");
            assertEquals(new Point2D(200, 220), button.getContextAnchor());
            assertTrue(extraShowingWindows() > before, "the context menu opens a popup window");
        });
    }

    @Test
    @Tag("ui")
    public void installContextMenuReturnsRemovableHandler() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            Node target = button.getParent();
            EventHandler<ContextMenuEvent> handler = button.installContextMenu(target);

            target.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    5, 5, 100, 100, false, null));
            assertTrue(button.isShowing(), "the installed handler opens the menu");
            button.hide();

            target.removeEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, handler);
            target.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    5, 5, 100, 100, false, null));
            assertFalse(button.isShowing(), "after removal the handler no longer opens the menu");
        });
    }

    @Test
    @Tag("ui")
    public void disabledButtonContextMenuConsumesButDoesNotOpen() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton button = shownButton("A", "B");
            Node root = button.getScene().getRoot();
            button.installContextMenu(root);
            button.setDisable(true);
            // A scene-level probe: a consumed request never bubbles up to it.
            AtomicInteger reachedScene = new AtomicInteger();
            button.getScene().addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    e -> reachedScene.incrementAndGet());

            // Fire on the button (a child of root) so the event bubbles through root.
            button.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    5, 5, 100, 100, false, null));
            assertFalse(button.isShowing(), "a disabled button does not open a context menu");
            // The request is still consumed at root (installContextMenu replaces the
            // native menu), so a disabled trigger shows nothing rather than a platform menu.
            assertEquals(0, reachedScene.get(), "the request is consumed, not left to bubble");
        });
    }

    // ==================== PR5: accelerator registration ====================

    @Test
    public void acceleratorRegisteredAndFiresItem() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem item = RXMenuItem.of("Save");
            KeyCombination combo = KeyCombination.keyCombination("Shortcut+S");
            item.setAccelerator(combo);
            AtomicInteger fired = new AtomicInteger();
            item.setOnAction(e -> fired.incrementAndGet());
            RXMenuButton button = attachedButton(item);

            Runnable registered = button.getScene().getAccelerators().get(combo);
            assertNotNull(registered, "the accelerator is registered in the scene");
            registered.run();
            assertEquals(1, fired.get(), "firing the accelerator fires the item");
        });
    }

    @Test
    public void acceleratorUnregisteredOnOwnerDetach() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem item = RXMenuItem.of("Save");
            KeyCombination combo = KeyCombination.keyCombination("Shortcut+S");
            item.setAccelerator(combo);
            RXMenuButton button = attachedButton(item);
            Scene scene = button.getScene();
            assertTrue(scene.getAccelerators().containsKey(combo), "precondition: registered");

            ((StackPane) button.getParent()).getChildren().remove(button);
            assertFalse(scene.getAccelerators().containsKey(combo),
                    "detaching the owner removes the global shortcut (no leak)");
        });
    }

    @Test
    public void acceleratorTracksRuntimeChanges() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem item = RXMenuItem.of("Save");
            RXMenuButton button = attachedButton(item);
            Scene scene = button.getScene();
            KeyCombination combo = KeyCombination.keyCombination("Shortcut+S");

            item.setAccelerator(combo);
            assertTrue(scene.getAccelerators().containsKey(combo), "a runtime accelerator registers");
            item.setAccelerator(null);
            assertFalse(scene.getAccelerators().containsKey(combo), "clearing it unregisters");
        });
    }

    @Test
    public void acceleratorTogglesSelectableItem() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem check = new RXMenuItem("Wrap");
            check.setSelectable(true);
            KeyCombination combo = KeyCombination.keyCombination("Shortcut+W");
            check.setAccelerator(combo);
            RXMenuButton button = attachedButton(check);

            Runnable registered = button.getScene().getAccelerators().get(combo);
            assertNotNull(registered);
            registered.run();
            assertTrue(check.isSelected(), "the accelerator toggles the checkbox");
            registered.run();
            assertFalse(check.isSelected());
        });
    }

    @Test
    public void acceleratorOnSelectedRadioKeepsItSelected() throws InterruptedException {
        runOnFx(() -> {
            ToggleGroup group = new ToggleGroup();
            RXMenuItem a = RXMenuItem.radio("A", group);
            RXMenuItem b = RXMenuItem.radio("B", group);
            KeyCombination comboA = KeyCombination.keyCombination("Shortcut+R");
            a.setAccelerator(comboA);
            RXMenuButton button = attachedButton(a, b);
            a.setSelected(true);

            Runnable registered = button.getScene().getAccelerators().get(comboA);
            assertNotNull(registered);
            // The accelerator path must apply the same radio guard as clicking: firing
            // an already-selected radio must not deselect it and empty the group.
            registered.run();
            assertTrue(a.isSelected(), "firing a selected radio's accelerator keeps it selected");
            assertSame(a, group.getSelectedToggle(), "the toggle group is not emptied");
        });
    }

    @Test
    public void acceleratorResyncOnItemsChange() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem item1 = RXMenuItem.of("A");
            KeyCombination combo1 = KeyCombination.keyCombination("Shortcut+A");
            item1.setAccelerator(combo1);
            RXMenuButton button = attachedButton(item1);
            Scene scene = button.getScene();
            assertTrue(scene.getAccelerators().containsKey(combo1), "precondition: item1 registered");

            RXMenuItem item2 = RXMenuItem.of("B");
            KeyCombination combo2 = KeyCombination.keyCombination("Shortcut+B");
            item2.setAccelerator(combo2);
            button.getItems().add(item2);
            assertTrue(scene.getAccelerators().containsKey(combo2), "an added item's accelerator registers");
            assertTrue(scene.getAccelerators().containsKey(combo1), "the existing accelerator survives resync");

            button.getItems().remove(item1);
            assertFalse(scene.getAccelerators().containsKey(combo1), "a removed item's accelerator unregisters");
            assertTrue(scene.getAccelerators().containsKey(combo2), "the remaining accelerator stays");
        });
    }

    @Test
    public void acceleratorIgnoresDisabledItem() throws InterruptedException {
        runOnFx(() -> {
            RXMenuItem item = RXMenuItem.of("Save");
            item.setDisable(true);
            KeyCombination combo = KeyCombination.keyCombination("Shortcut+S");
            item.setAccelerator(combo);
            AtomicInteger fired = new AtomicInteger();
            item.setOnAction(e -> fired.incrementAndGet());
            RXMenuButton button = attachedButton(item);

            Runnable registered = button.getScene().getAccelerators().get(combo);
            assertNotNull(registered, "a disabled item still registers its accelerator");
            registered.run();
            assertEquals(0, fired.get(), "but a disabled item does not fire");
        });
    }

    // ==================== Helpers ====================

    // A button placed in a (non-shown) scene with the skin realized, so the skin's
    // MenuAcceleratorSupport registers against the scene.
    private RXMenuButton attachedButton(RXMenuItem... items) {
        RXMenuButton button = new RXMenuButton("Menu");
        button.getItems().addAll(items);
        new Scene(new StackPane(button));
        button.applyCss();
        return button;
    }

    private RXMenuButton shownButton(String... labels) {
        RXMenuButton button = new RXMenuButton("Menu");
        for (String label : labels) {
            button.getItems().add(RXMenuItem.of(label));
        }
        return showStage(button);
    }

    private RXMenuButton showStage(RXMenuButton button) {
        StackPane root = new StackPane(button);
        stage = new Stage();
        stage.setScene(new Scene(root, 260, 160));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX(screen.getMinX() + 60);
        stage.setY(screen.getMinY() + 60);
        stage.show();
        root.applyCss();
        root.layout();
        return button;
    }

    // Windows that are showing and are not the test stage — i.e. open popups.
    private int extraShowingWindows() {
        int count = 0;
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window != stage) {
                count++;
            }
        }
        return count;
    }

    private Node popupLookup(String selector) {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window != stage && window.getScene() != null) {
                Node found = window.getScene().getRoot().lookup(selector);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void clickPrimary(RXMenuButton button) {
        button.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 10, 10, 10, 10,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                false, false, false,
                true, false, false, null));
    }

    private static void fireKey(RXMenuButton button, EventType<KeyEvent> type, KeyCode code) {
        button.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
