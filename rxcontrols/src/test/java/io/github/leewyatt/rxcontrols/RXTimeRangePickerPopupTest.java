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

import java.time.LocalTime;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.doubleClick;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.pressMouse;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoint-by-endpoint confirmation, closing and typed text of {@link RXTimeRangePicker}.
 */
@Tag("ui")
public class RXTimeRangePickerPopupTest {

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
     * Verifies OK confirms the start, moves on to the end and commits the range once the end is confirmed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void okConfirmsStartThenCommitsRange() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            assertTrue(hasPseudoClass(editor(picker, 0), "active"));
            assertTrue(ok.isDisabled());

            click(timeCell(root, ".hour-column", "09"));
            assertEquals("09:00", editor(picker, 0).getText());
            assertNull(picker.getValue());
            ok.fire();
            assertTrue(picker.isShowing());
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            assertFalse(hasPseudoClass(editor(picker, 0), "active"));
            assertTrue(ok.isDisabled());

            click(timeCell(root, ".hour-column", "17"));
            click(timeCell(root, ".minute-column", "30"));
            assertEquals("17:30", editor(picker, 1).getText());
            ok.fire();
            assertFalse(picker.isShowing());
            assertEquals(RXTimeRange.of(LocalTime.of(9, 0), LocalTime.of(17, 30)), picker.getValue());
            assertEquals(1, actions[0]);
        });
    }

    /**
     * Verifies an end picked before the start is committed in order.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void endBeforeStartIsOrdered() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            click(timeCell(root, ".hour-column", "22"));
            ok.fire();
            click(timeCell(root, ".hour-column", "06"));
            ok.fire();
            assertEquals(hours(6, 22), picker.getValue());
        });
    }

    /**
     * Verifies a pick for an empty end starts from the start's time, and the same time twice is a range.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void emptyEndStartsFromStartTime() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            click(timeCell(root, ".hour-column", "18"));
            click(timeCell(root, ".minute-column", "30"));
            ok.fire();
            click(timeCell(root, ".minute-column", "45"));
            assertEquals("18:45", editor(picker, 1).getText());
            click(timeCell(root, ".minute-column", "30"));
            ok.fire();
            assertEquals(RXTimeRange.of(LocalTime.of(18, 30), LocalTime.of(18, 30)), picker.getValue());
        });
    }

    /**
     * Verifies a double click on a time confirms the endpoint.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void doubleClickConfirmsEndpoint() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(timeCell(root, ".hour-column", "09"));
            doubleClick(root.lookup(".hour-column").lookup(".items"));
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            click(timeCell(root, ".hour-column", "12"));
            doubleClick(root.lookup(".hour-column").lookup(".items"));
            assertEquals(hours(9, 12), picker.getValue());
        });
    }

    /**
     * Verifies Escape discards a confirmed start and the fields return to the committed range.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void escapeDiscardsDrafts() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(timeCell(root, ".hour-column", "05"));
            ((Button) root.lookup(".ok-button")).fire();
            press(editor(picker, 1), KeyCode.ESCAPE);
            assertFalse(picker.isShowing());
            assertEquals(hours(10, 20), picker.getValue());
            assertEquals("10:00", editor(picker, 0).getText());
        });
    }

    /**
     * Verifies the other field cannot take the focus before the current endpoint is confirmed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void otherFieldLockedUntilConfirmed() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            editor(picker, 0).requestFocus();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            editor(picker, 1).requestFocus();
            assertSame(editor(picker, 0), picker.getScene().getFocusOwner());
            ((Button) root.lookup(".ok-button")).fire();
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
            editor(picker, 0).requestFocus();
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies a press on the end field opens the popup on the end, and confirming it goes on to the start.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void pressOnEndFieldOpensOnEnd() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            editor(picker, 0).requestFocus();
            pressMouse(editor(picker, 1));
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            Node root = findPopup().getScene().getRoot();
            assertTrue(hasPseudoClass(timeCell(root, ".hour-column", "20"), "selected"));
            click(timeCell(root, ".hour-column", "22"));
            assertEquals("22:00", editor(picker, 1).getText());
            ((Button) root.lookup(".ok-button")).fire();
            assertTrue(picker.isShowing());
            assertSame(editor(picker, 0), picker.getScene().getFocusOwner());
            ((Button) root.lookup(".ok-button")).fire();
            assertEquals(hours(10, 22), picker.getValue());
        });
    }

    /**
     * Verifies closing commits a confirmed change and discards an unconfirmed one.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void closingCommitsOnlyConfirmedChange() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(timeCell(root, ".hour-column", "05"));
            ((Button) root.lookup(".ok-button")).fire();
            picker.hide();
            assertEquals(hours(5, 20), picker.getValue());
            assertEquals(1, actions[0]);

            picker.show();
            root = findPopup().getScene().getRoot();
            click(timeCell(root, ".hour-column", "07"));
            picker.hide();
            assertEquals(hours(5, 20), picker.getValue());
            assertEquals("05:00", editor(picker, 0).getText());
            assertEquals(1, actions[0]);
        });
    }

    /**
     * Verifies Tab confirms the endpoint it leaves and commits once both endpoints were gone through.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void tabConfirmsEndpointItLeaves() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            editor(picker, 0).requestFocus();
            picker.show();
            click(timeCell(findPopup().getScene().getRoot(), ".hour-column", "05"));
            press(editor(picker, 0), KeyCode.TAB);
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            assertTrue(picker.isShowing());
            press(editor(picker, 1), KeyCode.TAB);
            assertFalse(picker.isShowing());
            assertEquals(hours(5, 20), picker.getValue());
        });
    }

    /**
     * Verifies the keyboard alone picks and confirms both endpoints from the fields.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardConfirmsBothEndpoints() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.requestFocus();
            press(start, KeyCode.F4);
            assertTrue(picker.isShowing());
            press(start, KeyCode.DOWN);
            press(start, KeyCode.DOWN);
            assertEquals("11:00", start.getText());
            press(start, KeyCode.ENTER);
            assertSame(end, picker.getScene().getFocusOwner());
            assertTrue(picker.isShowing());

            press(end, KeyCode.DOWN);
            press(end, KeyCode.DOWN);
            assertEquals("21:00", end.getText());
            press(end, KeyCode.ENTER);
            assertFalse(picker.isShowing());
            assertEquals(hours(11, 21), picker.getValue());
        });
    }

    /**
     * Verifies typed text becomes the endpoint's draft, Enter confirms it and unusable text is flagged.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedTextConfirmsEndpoint() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            start.setText("08:15");
            assertTrue(hasPseudoClass(timeCell(root, ".hour-column", "08"), "selected"));
            press(start, KeyCode.ENTER);
            assertSame(end, picker.getScene().getFocusOwner());

            end.setText("soon");
            press(end, KeyCode.ENTER);
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(end, "invalid"));
            assertNull(picker.getValue());

            end.setText("17:30");
            press(end, KeyCode.ENTER);
            assertFalse(picker.isShowing());
            assertEquals(RXTimeRange.of(LocalTime.of(8, 15), LocalTime.of(17, 30)), picker.getValue());
        });
    }

    /**
     * Verifies a typed time outside the bounds cannot be confirmed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedTimeOutsideBoundsCannotBeConfirmed() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setMinTime(LocalTime.of(9, 0));
            TextField start = editor(picker, 0);
            picker.show();
            Button ok = (Button) findPopup().getScene().getRoot().lookup(".ok-button");
            start.setText("09:30");
            assertFalse(ok.isDisabled());
            start.setText("08:30");
            assertTrue(ok.isDisabled());
            press(start, KeyCode.ENTER);
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(start, "invalid"));
            assertTrue(hasPseudoClass(start, "active"));
        });
    }

    /**
     * Verifies text typed over an open popup commits both fields in order when focus leaves the picker.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedTextCommitsOnFocusLoss() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            picker.setValue(hours(10, 20));
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            editor(picker, 0).requestFocus();
            picker.show();
            editor(picker, 0).setText("22:00");
            picker.getParent().getChildrenUnmodifiable().get(1).requestFocus();
            assertFalse(picker.isShowing());
            assertEquals(hours(20, 22), picker.getValue());
            assertEquals(1, actions[0]);
        });
    }

    /**
     * Verifies typed endpoints commit together in order while the popup is closed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedEndpointsCommitInOrder() throws InterruptedException {
        runOnFx(() -> {
            RXTimeRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.setText("18:00");
            end.setText("08:30");
            press(end, KeyCode.ENTER);
            assertEquals(RXTimeRange.of(LocalTime.of(8, 30), LocalTime.of(18, 0)), picker.getValue());
        });
    }

    private static RXTimeRange hours(int startHour, int endHour) {
        return RXTimeRange.of(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static Label timeCell(Node root, String column, String text) {
        return root.lookup(column).lookupAll(".time-cell").stream()
                .filter(node -> node.isVisible() && text.equals(((Label) node).getText()))
                .map(node -> (Label) node).findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " in " + column));
    }

    private RXTimeRangePicker newShownPicker() {
        RXTimeRangePicker picker = new RXTimeRangePicker();
        picker.setLocale(Locale.US);
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, new Button("other")), 480, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }
}
