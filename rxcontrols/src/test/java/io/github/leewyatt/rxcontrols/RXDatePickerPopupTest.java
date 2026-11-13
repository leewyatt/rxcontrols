package io.github.leewyatt.rxcontrols;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PopupControl;
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
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.pressAlt;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.pressMouse;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Popup behavior of {@link RXDatePicker} in a shown window: picking, draft discard,
 * Escape containment, keyboard routing and focus mirroring.
 */
@Tag("ui")
public class RXDatePickerPopupTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private Stage stage;
    private AtomicInteger cancels;

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
     * Verifies clicking a day in the popup commits it, fires one action and closes the popup.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void pickingDayCommitsAndCloses() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            AtomicInteger actions = new AtomicInteger();
            picker.setOnAction(event -> actions.incrementAndGet());
            picker.show();
            PopupControl popup = findPopup();
            assertNotNull(popup);
            click(dayCell(popup.getScene().getRoot(), LocalDate.of(2026, 11, 20)));
            assertEquals(LocalDate.of(2026, 11, 20), picker.getValue());
            assertEquals(1, actions.get());
            assertFalse(picker.isShowing());
            assertNull(findPopup());
        });
    }

    /**
     * Verifies Escape closes the popup without letting the enclosing cancel button fire.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void escapeClosesPopupWithoutCancellingDialog() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            TextField editor = editor(picker, 0);
            editor.requestFocus();
            picker.show();
            press(editor, KeyCode.ESCAPE);
            assertFalse(picker.isShowing());
            assertEquals(0, cancels.get());
            press(editor, KeyCode.ESCAPE);
            assertEquals(1, cancels.get(), "a closed picker lets Escape through");
        });
    }

    /**
     * Verifies Down seeds the calendar cursor, arrows then move it and Enter commits.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardNavigatesPopupCalendar() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            picker.setValue(LocalDate.of(2026, 11, 10));
            TextField editor = editor(picker, 0);
            editor.requestFocus();
            pressAlt(editor, KeyCode.DOWN);
            assertTrue(picker.isShowing());
            press(editor, KeyCode.DOWN);
            press(editor, KeyCode.DOWN);
            press(editor, KeyCode.RIGHT);
            press(editor, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 11, 18), picker.getValue());
            assertFalse(picker.isShowing());
        });
    }

    /**
     * Verifies typing a valid date previews it in the popup and closing discards it.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedDraftPreviewsAndClosingDiscards() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            picker.setValue(LocalDate.of(2026, 11, 10));
            TextField editor = editor(picker, 0);
            editor.requestFocus();
            picker.show();
            editor.setText("2/3/2027");
            PopupControl popup = findPopup();
            assertTrue(dayCell(popup.getScene().getRoot(), LocalDate.of(2027, 2, 3)).isSelected());
            press(editor, KeyCode.ESCAPE);
            assertEquals(LocalDate.of(2026, 11, 10), picker.getValue());
            assertEquals("11/10/2026", editor.getText());
        });
    }

    /**
     * Verifies the field's focus is mirrored onto the picker and the popup never takes focus.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void focusIsMirroredAndPopupStaysUnfocused() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            picker.requestFocus();
            assertTrue(editor(picker, 0).isFocused() || stage.getScene().getFocusOwner() == editor(picker, 0));
            assertTrue(picker.isFocused());
            assertTrue(hasPseudoClass(picker, "focused"));
            pressMouse(editor(picker, 0));
            assertTrue(picker.isShowing());
            PopupControl popup = findPopup();
            click(popup.getScene().getRoot().lookup(".next-button"));
            assertNull(popup.getScene().getFocusOwner());
            assertTrue(picker.isShowing());
        });
    }

    /**
     * Verifies the Today button commits the clock's date.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void todayButtonCommitsClockDate() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = newShownPicker();
            picker.show();
            Button today = (Button) findPopup().getScene().getRoot().lookup(".today-button");
            today.fire();
            assertEquals(TODAY, picker.getValue());
            assertFalse(picker.isShowing());
        });
    }

    private RXDatePicker newShownPicker() {
        RXDatePicker picker = new RXDatePicker();
        picker.setLocale(Locale.US);
        picker.setClock(CLOCK);
        cancels = new AtomicInteger();
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> cancels.incrementAndGet());
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, cancel), 360, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }
}
