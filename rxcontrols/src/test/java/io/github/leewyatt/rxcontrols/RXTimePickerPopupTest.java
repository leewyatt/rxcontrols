package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field and popup behavior of {@link RXTimePicker}: draft preview, confirmation,
 * discard, Now, and typed text.
 */
@Tag("ui")
public class RXTimePickerPopupTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atTime(15, 47, 31).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private Stage stage;

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        PickerTestSupport.startToolkit();
    }

    /**
     * Hides popups and the stage between tests.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            hideAllPopups();
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * Verifies picks preview in the field and only OK commits them.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void picksPreviewUntilOk() throws InterruptedException {
        runOnFx(() -> {
            RXTimePicker picker = newShownPicker();
            AtomicInteger actions = new AtomicInteger();
            picker.setOnAction(event -> actions.incrementAndGet());
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(cell(root, ".hour-column", "08"));
            click(cell(root, ".minute-column", "45"));
            assertEquals("08:45", editor(picker, 0).getText());
            assertNull(picker.getValue());
            assertEquals(0, actions.get());
            ((Button) root.lookup(".ok-button")).fire();
            assertEquals(LocalTime.of(8, 45), picker.getValue());
            assertEquals(1, actions.get());
            assertFalse(picker.isShowing());
        });
    }

    /**
     * Verifies closing without OK discards the draft and restores the field text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void closingDiscardsDraft() throws InterruptedException {
        runOnFx(() -> {
            RXTimePicker picker = newShownPicker();
            picker.setValue(LocalTime.of(10, 0));
            TextField editor = editor(picker, 0);
            editor.requestFocus();
            picker.show();
            click(cell(findPopup().getScene().getRoot(), ".hour-column", "11"));
            assertEquals("11:00", editor.getText());
            press(editor, KeyCode.ESCAPE);
            assertEquals(LocalTime.of(10, 0), picker.getValue());
            assertEquals("10:00", editor.getText());
            picker.show();
            click(cell(findPopup().getScene().getRoot(), ".hour-column", "12"));
            picker.hide();
            assertEquals("10:00", editor.getText());
        });
    }

    /**
     * Verifies Enter commits the popup draft.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void enterCommitsDraft() throws InterruptedException {
        runOnFx(() -> {
            RXTimePicker picker = newShownPicker();
            TextField editor = editor(picker, 0);
            editor.requestFocus();
            picker.show();
            click(cell(findPopup().getScene().getRoot(), ".hour-column", "07"));
            press(editor, KeyCode.ENTER);
            assertEquals(LocalTime.of(7, 0), picker.getValue());
            assertFalse(picker.isShowing());
        });
    }

    /**
     * Verifies Now commits the clock time truncated to the steps.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void nowCommitsTruncatedClockTime() throws InterruptedException {
        runOnFx(() -> {
            RXTimePicker picker = newShownPicker();
            picker.setMinuteStep(15);
            picker.show();
            ((Button) findPopup().getScene().getRoot().lookup(".now-button")).fire();
            assertEquals(LocalTime.of(15, 45), picker.getValue());
        });
    }

    /**
     * Verifies typed off-step text inside the bounds commits, while out-of-bounds text is flagged.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedTextFollowsBoundsNotSteps() throws InterruptedException {
        runOnFx(() -> {
            RXTimePicker picker = newShownPicker();
            picker.setMinuteStep(15);
            picker.setMaxTime(LocalTime.of(18, 0));
            TextField editor = editor(picker, 0);
            editor.setText("9:07");
            press(editor, KeyCode.ENTER);
            assertEquals(LocalTime.of(9, 7), picker.getValue());
            editor.setText("19:00");
            press(editor, KeyCode.ENTER);
            assertEquals(LocalTime.of(9, 7), picker.getValue());
            assertTrue(hasPseudoClass(picker, "invalid"));
        });
    }

    private RXTimePicker newShownPicker() {
        RXTimePicker picker = new RXTimePicker();
        picker.setLocale(Locale.US);
        picker.setClock(CLOCK);
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, new Button("other")), 360, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }

    private static Label cell(Node root, String column, String text) {
        return root.lookup(column).lookupAll(".time-cell").stream()
                .filter(node -> node.isVisible() && text.equals(((Label) node).getText()))
                .map(node -> (Label) node).findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " in " + column));
    }
}
