package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draft preview, confirmation and typed text of {@link RXDateTimePicker}.
 */
@Tag("ui")
public class RXDateTimePickerPopupTest {

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
     * Verifies a day pick previews in the field without committing until OK.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void dayPickPreviewsUntilOk() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            assertTrue(picker.isShowing());
            assertNull(picker.getValue());
            assertEquals("11/20/2026 00:00", editor(picker, 0).getText());
            ((Button) root.lookup(".ok-button")).fire();
            assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), picker.getValue());
        });
    }

    /**
     * Verifies Now commits the clock moment truncated to the minute.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void nowCommitsTruncatedMoment() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimePicker picker = newShownPicker();
            picker.show();
            ((Button) findPopup().getScene().getRoot().lookup(".now-button")).fire();
            assertEquals(LocalDateTime.of(2026, 11, 14, 15, 47), picker.getValue());
        });
    }

    /**
     * Verifies typed text respects the combined bounds.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedTextRespectsBounds() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimePicker picker = newShownPicker();
            picker.setMinDateTime(LocalDateTime.of(2026, 11, 14, 15, 30));
            TextField editor = editor(picker, 0);
            editor.setText("11/14/2026 15:00");
            press(editor, KeyCode.ENTER);
            assertNull(picker.getValue());
            editor.setText("11/14/2026 16:05");
            press(editor, KeyCode.ENTER);
            assertEquals(LocalDateTime.of(2026, 11, 14, 16, 5), picker.getValue());
        });
    }

    private RXDateTimePicker newShownPicker() {
        RXDateTimePicker picker = new RXDateTimePicker();
        picker.setLocale(Locale.US);
        picker.setClock(CLOCK);
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, new Button("other")), 520, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }
}
