package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.pressMouse;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.shownPages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoint-by-endpoint confirmation, closing and typed text of {@link RXDateTimeRangePicker}.
 */
@Tag("ui")
public class RXDateTimeRangePickerPopupTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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
            RXDateTimeRangePicker picker = newShownPicker();
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            assertEquals(1, shownPages(root));
            assertTrue(hasPseudoClass(editor(picker, 0), "active"));
            assertTrue(ok.isDisabled());

            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            assertEquals("11/20/2026 00:00", editor(picker, 0).getText());
            assertNull(picker.getValue());
            ok.fire();
            assertTrue(picker.isShowing());
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            assertFalse(hasPseudoClass(editor(picker, 0), "active"));
            assertTrue(ok.isDisabled());

            click(dayCell(root, LocalDate.of(2026, 11, 22)));
            click(timeCell(root, ".hour-column", "10"));
            assertEquals("11/22/2026 10:00", editor(picker, 1).getText());
            assertTrue(hasPseudoClass(dayCell(root, LocalDate.of(2026, 11, 21)), "in-range"));
            ok.fire();
            assertFalse(picker.isShowing());
            assertEquals(RXDateTimeRange.of(LocalDate.of(2026, 11, 20).atStartOfDay(),
                    LocalDateTime.of(2026, 11, 22, 10, 0)), picker.getValue());
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            click(dayCell(root, LocalDate.of(2026, 11, 22)));
            ok.fire();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            ok.fire();
            assertEquals(RXDateTimeRange.of(LocalDate.of(2026, 11, 20).atStartOfDay(),
                    LocalDate.of(2026, 11, 22).atStartOfDay()), picker.getValue());
        });
    }

    /**
     * Verifies a time picked for an empty end falls on the start's day.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void timePickedForEmptyEndUsesStartDay() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            ((Button) root.lookup(".ok-button")).fire();
            click(timeCell(root, ".hour-column", "10"));
            assertEquals("11/20/2026 10:00", editor(picker, 1).getText());
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
            RXDateTimeRangePicker picker = newShownPicker();
            RXDateTimeRange old = novemberRange(10, 20);
            picker.setValue(old);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 5)));
            ((Button) root.lookup(".ok-button")).fire();
            press(editor(picker, 1), KeyCode.ESCAPE);
            assertFalse(picker.isShowing());
            assertEquals(old, picker.getValue());
            assertEquals("11/10/2026 00:00", editor(picker, 0).getText());
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
            editor(picker, 0).requestFocus();
            pressMouse(editor(picker, 1));
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 25)));
            assertEquals("11/25/2026 00:00", editor(picker, 1).getText());
            ((Button) root.lookup(".ok-button")).fire();
            assertTrue(picker.isShowing());
            assertSame(editor(picker, 0), picker.getScene().getFocusOwner());
            ((Button) root.lookup(".ok-button")).fire();
            assertEquals(novemberRange(10, 25), picker.getValue());
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 5)));
            ((Button) root.lookup(".ok-button")).fire();
            picker.hide();
            assertEquals(novemberRange(5, 20), picker.getValue());
            assertEquals(1, actions[0]);

            picker.show();
            root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 25)));
            picker.hide();
            assertEquals(novemberRange(5, 20), picker.getValue());
            assertEquals("11/20/2026 00:00", editor(picker, 1).getText());
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
            editor(picker, 0).requestFocus();
            picker.show();
            click(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 5)));
            press(editor(picker, 0), KeyCode.TAB);
            assertSame(editor(picker, 1), picker.getScene().getFocusOwner());
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            assertTrue(picker.isShowing());
            press(editor(picker, 1), KeyCode.TAB);
            assertFalse(picker.isShowing());
            assertEquals(novemberRange(5, 20), picker.getValue());
        });
    }

    /**
     * Verifies moving on to the other endpoint keeps the month on screen.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void movingOnKeepsMonth() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(RXDateTimeRange.of(LocalDate.of(2026, 11, 10).atStartOfDay(),
                    LocalDate.of(2027, 1, 5).atStartOfDay()));
            picker.show();
            Node root = findPopup().getScene().getRoot();
            ((Button) root.lookup(".ok-button")).fire();
            assertTrue(hasPseudoClass(editor(picker, 1), "active"));
            assertTrue(hasPseudoClass(dayCell(root, LocalDate.of(2026, 11, 10)), "range-start"));
        });
    }

    /**
     * Verifies a hover preview does not outlive the popup it was shown in.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void hoverPreviewEndsWithPopup() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
            picker.show();
            Node root = findPopup().getScene().getRoot();
            dayCell(root, LocalDate.of(2026, 11, 25)).fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0,
                    MouseButton.NONE, 0, false, false, false, false, false, false, false, false, false, false, null));
            assertTrue(hasPseudoClass(dayCell(root, LocalDate.of(2026, 11, 22)), "preview"));
            picker.hide();
            picker.show();
            root = findPopup().getScene().getRoot();
            assertFalse(hasPseudoClass(dayCell(root, LocalDate.of(2026, 11, 22)), "preview"));
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
            RXDateTimeRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            start.setText("11/20/2026 08:00");
            assertTrue(hasPseudoClass(dayCell(root, LocalDate.of(2026, 11, 20)), "selected"));
            press(start, KeyCode.ENTER);
            assertSame(end, picker.getScene().getFocusOwner());

            end.setText("soon");
            press(end, KeyCode.ENTER);
            assertTrue(picker.isShowing());
            assertTrue(hasPseudoClass(end, "invalid"));
            assertNull(picker.getValue());

            end.setText("11/25/2026 10:00");
            press(end, KeyCode.ENTER);
            assertFalse(picker.isShowing());
            assertEquals(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 20, 8, 0),
                    LocalDateTime.of(2026, 11, 25, 10, 0)), picker.getValue());
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
            RXDateTimeRangePicker picker = newShownPicker();
            picker.setValue(novemberRange(10, 20));
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            editor(picker, 0).requestFocus();
            picker.show();
            editor(picker, 0).setText("11/25/2026 00:00");
            picker.getParent().getChildrenUnmodifiable().get(1).requestFocus();
            assertFalse(picker.isShowing());
            assertEquals(novemberRange(20, 25), picker.getValue());
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
            RXDateTimeRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.setText("11/20/2026 18:00");
            end.setText("11/20/2026 08:30");
            press(end, KeyCode.ENTER);
            assertEquals(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 20, 8, 30),
                    LocalDateTime.of(2026, 11, 20, 18, 0)), picker.getValue());
        });
    }

    /**
     * Verifies repeated day picks in the popup do not ask the time filter about a time again.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void dayPicksDoNotAskTimeFilterAgain() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            int[] asked = new int[24 * 60];
            picker.setTimeFilter(time -> {
                asked[time.toSecondOfDay() / 60]++;
                return time.getHour() >= 9 && time.getHour() < 18;
            });
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            click(dayCell(root, LocalDate.of(2026, 11, 22)));
            ((Button) root.lookup(".ok-button")).fire();
            click(dayCell(root, LocalDate.of(2026, 11, 24)));
            click(dayCell(root, LocalDate.of(2026, 11, 27)));
            assertEquals("11/27/2026 09:00", editor(picker, 1).getText());
            // The picker and its popup view each remember the answers.
            assertTrue(Arrays.stream(asked).max().getAsInt() <= 2);
        });
    }

    private static RXDateTimeRange novemberRange(int startDay, int endDay) {
        return RXDateTimeRange.of(LocalDate.of(2026, 11, startDay).atStartOfDay(),
                LocalDate.of(2026, 11, endDay).atStartOfDay());
    }

    private static Label timeCell(Node root, String column, String text) {
        return root.lookup(column).lookupAll(".time-cell").stream()
                .filter(node -> node.isVisible() && text.equals(((Label) node).getText()))
                .map(node -> (Label) node).findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " in " + column));
    }

    private RXDateTimeRangePicker newShownPicker() {
        RXDateTimeRangePicker picker = new RXDateTimeRangePicker();
        picker.setLocale(Locale.US);
        picker.setClock(CLOCK);
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, new Button("other")), 640, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }
}
