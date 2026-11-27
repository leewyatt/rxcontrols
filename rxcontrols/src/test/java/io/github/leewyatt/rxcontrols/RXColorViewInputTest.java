package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the input fields of {@link RXColorView}: live application of complete
 * values, lenient finishing on Enter (hex shorthand, clamping, restoring
 * invalid text), Escape, pasted text, number stepping, the opacity kept by six
 * digit hex, fields shown per format, and the fields' own actions kept away
 * from the view's handlers.
 */
public class RXColorViewInputTest {

    private Stage stage;
    private RXColorView view;
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

    private void show(RXColorView colorView) {
        view = colorView;
        view.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());
        stage = new Stage();
        stage.setScene(new Scene(new StackPane(view), 400, 500));
        stage.show();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private TextField field(String name) {
        return (TextField) view.lookup("." + name + "-field");
    }

    private static void type(TextField field, String text) {
        field.replaceText(0, field.getLength(), text);
    }

    private static void key(TextField field, KeyCode code, boolean shift) {
        field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
    }

    // ==================== Hex ====================

    @Test
    public void sixHexDigitsApplyWhileTypingAndKeepTheOpacity() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(10, 20, 30, 128 / 255.0)));
            TextField hex = field("hex");
            assertEquals("0A141E", hex.getText());
            type(hex, "1677f");
            assertEquals(Color.rgb(10, 20, 30, 128 / 255.0), view.getValue());
            assertTrue(hasPseudoClass(hex, "invalid"));
            type(hex, "1677ff");
            assertEquals(Color.rgb(0x16, 0x77, 0xff, 128 / 255.0), view.getValue());
            assertFalse(hasPseudoClass(hex, "invalid"));
            assertEquals(0, actions.get());

            key(hex, KeyCode.ENTER, false);
            assertEquals(1, actions.get());
            assertEquals("1677FF", hex.getText());
        });
    }

    @Test
    public void eightHexDigitsSetTheOpacityUnlessAlphaIsDisabled() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            TextField hex = field("hex");
            type(hex, "1677ff80");
            assertEquals(Color.rgb(0x16, 0x77, 0xff, 0x80 / 255.0), view.getValue());

            view.setAlphaEnabled(false);
            assertFalse(field("alpha").isVisible());
            type(hex, "2ddcb440");
            assertEquals(Color.rgb(0x2d, 0xdc, 0xb4), view.getValue());
        });
    }

    @Test
    public void shorthandIsAcceptedOnlyWhenTheEditIsFinished() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            TextField hex = field("hex");
            type(hex, "abc");
            assertEquals(Color.WHITE, view.getValue());
            assertTrue(hasPseudoClass(hex, "invalid"));
            key(hex, KeyCode.ENTER, false);
            assertEquals(Color.web("#aabbcc"), view.getValue());
            assertEquals("AABBCC", hex.getText());
            assertFalse(hasPseudoClass(hex, "invalid"));
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void textThatIsNotAValueIsReplacedWhenTheEditIsFinished() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#1677ff")));
            TextField hex = field("hex");
            type(hex, "12");
            key(hex, KeyCode.ENTER, false);
            assertEquals("1677FF", hex.getText());
            assertEquals(Color.web("#1677ff"), view.getValue());
            assertEquals(0, actions.get());
        });
    }

    @Test
    public void escapeRestoresTheCurrentText() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#1677ff")));
            TextField hex = field("hex");
            type(hex, "12");
            key(hex, KeyCode.ESCAPE, false);
            assertEquals("1677FF", hex.getText());
            assertFalse(hasPseudoClass(hex, "invalid"));
        });
    }

    @Test
    public void pastedTextKeepsItsHexDigits() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            TextField hex = field("hex");
            type(hex, " #2ddcb4\n");
            assertEquals("2ddcb4", hex.getText());
            assertEquals(Color.web("#2ddcb4"), view.getValue());
        });
    }

    // ==================== Numbers ====================

    @Test
    public void numbersOutOfRangeWaitAndAreClampedWhenFinished() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(10, 20, 30)));
            view.setFormat(RXColorFormat.RGB);
            TextField red = field("red");
            assertTrue(red.isVisible());
            assertFalse(field("hex").isVisible());
            type(red, "300");
            assertEquals(Color.rgb(10, 20, 30), view.getValue());
            assertTrue(hasPseudoClass(red, "invalid"));
            key(red, KeyCode.ENTER, false);
            assertEquals(Color.rgb(255, 20, 30), view.getValue());
            assertEquals("255", red.getText());
        });
    }

    @Test
    public void anHsbFieldWritesOnlyItsChannel() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(128, 128, 128)));
            view.setFormat(RXColorFormat.HSB);
            TextField hue = field("hue");
            TextField saturation = field("saturation");
            type(hue, "210");
            // Gray has no hue: the value stays, but the panel keeps 210 degrees.
            assertEquals(Color.rgb(128, 128, 128), view.getValue());
            key(hue, KeyCode.ENTER, false);
            assertEquals("210", hue.getText());
            type(saturation, "50");
            double[] hsb = ColorMath.toHsb(view.getValue());
            assertEquals(210.0, hsb[0], 1.0);
            assertEquals(0.5, hsb[1], 0.01);
        });
    }

    @Test
    public void upAndDownStepANumber() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(10, 20, 30, 128 / 255.0)));
            TextField alpha = field("alpha");
            assertEquals("50", alpha.getText());
            key(alpha, KeyCode.UP, false);
            assertEquals(51, ColorMath.toPercent(view.getValue().getOpacity()));
            key(alpha, KeyCode.UP, true);
            assertEquals(61, ColorMath.toPercent(view.getValue().getOpacity()));
            key(alpha, KeyCode.DOWN, true);
            key(alpha, KeyCode.DOWN, true);
            assertEquals(41, ColorMath.toPercent(view.getValue().getOpacity()));
            assertEquals(0, actions.get());
            key(alpha, KeyCode.ENTER, false);
            assertEquals(1, actions.get());
        });
    }

    // ==================== Refresh ====================

    @Test
    public void codeUpdatesFieldsButNotTheOneBeingEdited() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.setFormat(RXColorFormat.RGB);
            TextField red = field("red");
            TextField green = field("green");
            view.setValue(Color.rgb(1, 2, 3));
            assertEquals("1", red.getText());
            type(red, "30");
            view.setValue(Color.rgb(4, 5, 6));
            assertEquals("30", red.getText());
            assertEquals("5", green.getText());
        });
    }

    // ==================== Actions ====================

    @Test
    public void aFieldsOwnActionNeverReachesTheView() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#1677ff")));
            TextField hex = field("hex");
            // Enter without an edit: the field fires its own action, the value is unchanged.
            key(hex, KeyCode.ENTER, false);
            assertEquals(0, actions.get());
        });
    }

    @Test
    public void switchingTheFormatKeepsThePreferredSize() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#1677ff80")));
            double width = view.prefWidth(-1);
            double height = view.prefHeight(-1);
            for (RXColorFormat format : RXColorFormat.values()) {
                view.setFormat(format);
                view.applyCss();
                view.layout();
                assertEquals(width, view.prefWidth(-1), 0.5, format.name());
                assertEquals(height, view.prefHeight(-1), 0.5, format.name());
            }
        });
    }

    // ==================== Format menu ====================

    @Test
    public void theFormatButtonOpensAMenu() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            javafx.scene.Node button = view.lookup(".format-button");
            assertNotNull(button);
            button.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, true, false, false, null));
            boolean menuShowing = false;
            for (Window window : Window.getWindows()) {
                if (window instanceof PopupControl && window.isShowing()
                        && ((PopupControl) window).getStyleClass().contains("rx-menu-popup")) {
                    menuShowing = true;
                }
            }
            assertTrue(menuShowing);
        });
    }
}
