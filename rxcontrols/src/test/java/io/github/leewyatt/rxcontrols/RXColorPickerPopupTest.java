package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXColorPickerSkin;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.uncaughtDuring;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXColorPicker} with its real popup: opening and closing by
 * pointer and keyboard, live edits written to the picker with one action per
 * edit, value and format kept in step with the popup's view, a bound value
 * locking input, keys routed away from the picker and dialogs, Escape dropping
 * unfinished text, reopening from a window-side close, actions deferred past
 * the close, skin replacement, and the nested format menu.
 */
public class RXColorPickerPopupTest {

    private Stage stage;
    private RXColorPicker picker;
    private final AtomicInteger actions = new AtomicInteger();

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        PickerTestSupport.startToolkit();
    }

    @AfterEach
    public void closeStage() throws InterruptedException {
        runOnFx(() -> {
            PickerTestSupport.hideAllPopups();
            if (stage != null) {
                stage.hide();
            }
        });
    }

    private void show(RXColorPicker colorPicker) {
        picker = colorPicker;
        picker.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());
        StackPane root = new StackPane(picker);
        stage = new Stage();
        stage.setScene(new Scene(root, 600, 700));
        stage.show();
        root.applyCss();
        root.layout();
    }

    private static PopupControl popup(String styleClass) {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl && window.isShowing()
                    && ((PopupControl) window).getStyleClass().contains(styleClass)) {
                PopupControl popup = (PopupControl) window;
                popup.getScene().getRoot().applyCss();
                popup.getScene().getRoot().layout();
                return popup;
            }
        }
        return null;
    }

    private static RXColorView popupView() {
        PopupControl popup = popup("rx-color-picker-popup");
        assertNotNull(popup, "the picker popup is not showing");
        return (RXColorView) popup.getScene().getRoot().lookup(".rx-color-view");
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, true, false, false, null));
    }

    private static void mouse(Region region, EventType<MouseEvent> type, double fx, double fy) {
        Point2D scene = region.localToScene(fx * region.getWidth(), fy * region.getHeight());
        region.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false, type != MouseEvent.MOUSE_RELEASED,
                false, false, true, false, false, null));
    }

    private static void key(Node node, KeyCode code, boolean alt) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, alt, false));
    }

    private void open() {
        click(picker.lookup(".display"));
        assertTrue(picker.isShowing());
    }

    // ==================== Defaults ====================

    @Test
    public void defaultsToWhiteAndReadsTheColor() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            assertEquals(Color.WHITE, picker.getValue());
            assertEquals("#FFFFFF", picker.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            assertFalse(picker.lookup(".text").isVisible());
            picker.setShowText(true);
            picker.setValue(Color.rgb(22, 119, 255, 128 / 255.0));
            assertEquals("#1677FF80", ((Label) picker.lookup(".text")).getText());
            picker.setFormat(RXColorFormat.RGB);
            assertEquals("rgba(22, 119, 255, 0.5)", ((Label) picker.lookup(".text")).getText());
            picker.setPromptText("No color");
            picker.setValue(null);
            assertTrue(hasPseudoClass(picker, "empty"));
            assertEquals("No color", ((Label) picker.lookup(".text")).getText());
            assertEquals("No color", picker.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        });
    }

    @Test
    public void theTriggerKeepsItsWidthWhileTheValueChanges() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            picker.setShowText(true);
            picker.setPromptText("None");
            List<Color> values = List.of(Color.WHITE, Color.BLACK, Color.web("#1677FF"), Color.web("#DDDDDD"),
                    Color.web("#111111"), Color.rgb(22, 119, 255, 128 / 255.0), Color.rgb(0, 0, 0, 0.01));
            for (RXColorFormat format : RXColorFormat.values()) {
                picker.setFormat(format);
                Double width = null;
                for (Color value : values) {
                    picker.setValue(value);
                    picker.applyCss();
                    double pref = picker.prefWidth(-1);
                    if (width == null) {
                        width = pref;
                    }
                    assertEquals(width, pref, 0.0, format + " " + value);
                }
                picker.setValue(null);
                picker.applyCss();
                assertEquals(width, picker.prefWidth(-1), 0.0, format + " prompt");
            }
        });
    }

    // ==================== Copy button ====================

    // The system clipboard is never written: the content is read from the factory,
    // and actions are fired only at a disabled button, which copies nothing.
    @Test
    public void theCopyButtonCopiesTheFormatTextAndNeverActs() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker(Color.rgb(22, 119, 255, 128 / 255.0)));
            RXCopyButton button = (RXCopyButton) picker.lookup(".copy-button");
            assertFalse(button.isVisible());
            assertFalse(button.isManaged());
            double hiddenWidth = picker.prefWidth(-1);
            KeyEvent copyKey = copyKey();
            picker.fireEvent(copyKey);

            picker.setCopyButtonVisible(true);
            picker.applyCss();
            assertTrue(button.isVisible());
            assertTrue(button.isManaged());
            assertFalse(button.isFocusTraversable());
            assertTrue(picker.prefWidth(-1) > hiddenWidth);
            picker.getParent().layout();
            assertTrue(button.getWidth() > 0.0);
            assertEquals(button.getWidth(), button.getHeight(), 0.0);
            assertEquals("#1677FF80", button.getContentFactory().get().getString());
            picker.setFormat(RXColorFormat.RGB);
            assertEquals("rgba(22, 119, 255, 0.5)", button.getContentFactory().get().getString());

            picker.setValue(null);
            assertTrue(button.isDisabled());
            button.fireEvent(new ActionEvent());
            picker.fireEvent(copyKey());
            assertEquals(0, actions.get());
            assertFalse(picker.isShowing());
        });
    }

    private static KeyEvent copyKey() {
        boolean mac = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
        return new KeyEvent(KeyEvent.KEY_PRESSED, "c", "c", KeyCode.C, false, !mac, false, mac);
    }

    // ==================== Opening and closing ====================

    @Test
    public void clickingTheTriggerOpensAndClosesThePopup() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            assertNotNull(popup("rx-color-picker-popup"));
            assertTrue(hasPseudoClass(picker, "showing"));
            click(picker.lookup(".display"));
            assertFalse(picker.isShowing());
            assertNull(popup("rx-color-picker-popup"));
        });
    }

    @Test
    public void spaceOpensAndEnterIsLeftToTheForm() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
            AtomicBoolean enterReachedRoot = new AtomicBoolean();
            picker.getParent().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    enterReachedRoot.set(true);
                }
            });
            picker.fireEvent(enter);
            assertFalse(picker.isShowing());
            assertTrue(enterReachedRoot.get());
            key(picker, KeyCode.SPACE, false);
            assertTrue(picker.isShowing());
            key(picker, KeyCode.ESCAPE, false);
            assertFalse(picker.isShowing());
            key(picker, KeyCode.DOWN, true);
            assertTrue(picker.isShowing());
        });
    }

    @Test
    public void disablingClosesThePopup() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            picker.setDisable(true);
            assertFalse(picker.isShowing());
            picker.show();
            assertFalse(picker.isShowing());
        });
    }

    @Test
    public void openingMovesTheFocusToTheArea() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
        });
        runOnFx(() -> {
            RXColorView view = popupView();
            Node focused = view.getScene().getFocusOwner();
            assertNotNull(focused);
            assertTrue(focused.getStyleClass().contains("area"), String.valueOf(focused));
        });
    }

    // ==================== Value sync ====================

    @Test
    public void popupEditsWriteThePickerLiveWithOneAction() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker(Color.web("#1677ff")));
            open();
            RXColorView view = popupView();
            Region area = (Region) view.lookup(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            assertEquals(view.getValue(), picker.getValue());
            mouse(area, MouseEvent.MOUSE_DRAGGED, 0.6, 0.6);
            assertEquals(view.getValue(), picker.getValue());
            assertEquals(0, actions.get());
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.6, 0.6);
            assertEquals(1, actions.get());
            assertTrue(picker.isShowing());

            picker.setValue(Color.RED);
            assertEquals(Color.RED, view.getValue());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void aBoundPickerLocksThePopupUntilUnbound() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            picker.valueProperty().bind(new SimpleObjectProperty<>(Color.BLUE));
            open();
            RXColorView view = popupView();
            Region area = (Region) view.lookup(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(Color.BLUE, picker.getValue());
            assertEquals(Color.BLUE, view.getValue());
            assertEquals(0, actions.get());

            picker.valueProperty().unbind();
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(view.getValue(), picker.getValue());
            assertFalse(Color.BLUE.equals(picker.getValue()));
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void theFormatFollowsBothWaysAndABoundFormatWins() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            RXColorView view = popupView();
            picker.setFormat(RXColorFormat.RGB);
            assertEquals(RXColorFormat.RGB, view.getFormat());
            view.setFormat(RXColorFormat.HSB);
            assertEquals(RXColorFormat.HSB, picker.getFormat());

            picker.formatProperty().bind(new SimpleObjectProperty<>(RXColorFormat.HEX));
            List<Throwable> errors = uncaughtDuring(() -> view.setFormat(RXColorFormat.RGB));
            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(RXColorFormat.HEX, view.getFormat());
        });
    }

    // ==================== Keys ====================

    @Test
    public void escapeInAFieldClosesAndDropsTheUnfinishedText() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            RXColorView view = popupView();
            TextField hex = (TextField) view.lookup(".hex-field");
            hex.replaceText(0, hex.getLength(), "abc");
            key(hex, KeyCode.ESCAPE, false);
            assertFalse(picker.isShowing());
            assertEquals(Color.WHITE, picker.getValue());
            assertEquals("FFFFFF", hex.getText());
        });
    }

    @Test
    public void keysThePopupDoesNotUseNeverLeaveIt() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            RXColorView view = popupView();
            AtomicInteger leaked = new AtomicInteger();
            view.getParent().addEventHandler(KeyEvent.KEY_PRESSED, event -> leaked.incrementAndGet());
            key(view.lookup(".area"), KeyCode.SPACE, false);
            key(view.lookup(".area"), KeyCode.ENTER, false);
            key(view.lookup(".hex-field"), KeyCode.ENTER, false);
            assertEquals(0, leaked.get());
            assertTrue(picker.isShowing());
        });
    }

    // ==================== Window-side close ====================

    @Test
    public void reopeningWhenTheWindowClosesThePopupWorks() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            AtomicBoolean reopened = new AtomicBoolean();
            picker.showingProperty().addListener((obs, was, is) -> {
                if (!is && !reopened.get()) {
                    reopened.set(true);
                    picker.show();
                }
            });
            open();
            List<Throwable> errors = uncaughtDuring(() -> popup("rx-color-picker-popup").hide());
            assertTrue(errors.isEmpty(), errors.toString());
        });
        runOnFx(() -> {
            assertTrue(picker.isShowing());
            assertNotNull(popup("rx-color-picker-popup"));
            List<Throwable> errors = uncaughtDuring(() -> popup("rx-color-picker-popup").hide());
            assertTrue(errors.isEmpty(), errors.toString());
            assertFalse(picker.isShowing());
        });
    }

    @Test
    public void anEditEndedByTheCloseActsAfterItAndMayReopen() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            picker.setOnAction(event -> picker.show());
            open();
            Region area = (Region) popupView().lookup(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            List<Throwable> errors = uncaughtDuring(() -> popup("rx-color-picker-popup").hide());
            assertTrue(errors.isEmpty(), errors.toString());
            assertFalse(picker.isShowing());
            assertEquals(0, actions.get());
        });
        runOnFx(() -> {
            assertEquals(1, actions.get());
        });
        runOnFx(() -> {
            assertTrue(picker.isShowing());
            assertNotNull(popup("rx-color-picker-popup"));
        });
    }

    // ==================== Skin ====================

    @Test
    public void aReplacedSkinReopensThePopup() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            picker.setSkin(new RXColorPickerSkin(picker));
            assertTrue(picker.isShowing());
            assertNotNull(popup("rx-color-picker-popup"));
            long shown = Window.getWindows().stream()
                    .filter(window -> window instanceof PopupControl && window.isShowing()
                            && ((PopupControl) window).getStyleClass().contains("rx-color-picker-popup"))
                    .count();
            assertEquals(1, shown);
        });
    }

    // ==================== Nested format menu ====================

    @Test
    public void theFormatMenuClosesAloneAndItsPickKeepsThePopupOpen() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorPicker());
            open();
            RXColorView view = popupView();
            click(view.lookup(".format-button"));
            PopupControl menu = popup("rx-menu-popup");
            assertNotNull(menu);
            RXMenuList list = (RXMenuList) menu.getScene().getRoot().lookup(".rx-menu-list");
            key(list, KeyCode.ESCAPE, false);
            assertNull(popup("rx-menu-popup"));
            assertTrue(picker.isShowing());

            click(view.lookup(".format-button"));
            menu = popup("rx-menu-popup");
            list = (RXMenuList) menu.getScene().getRoot().lookup(".rx-menu-list");
            list.activate(list.getItems().get(1));
            assertEquals(RXColorFormat.RGB, picker.getFormat());
            assertTrue(picker.isShowing());
            assertNotNull(popup("rx-color-picker-popup"));
        });
    }
}
