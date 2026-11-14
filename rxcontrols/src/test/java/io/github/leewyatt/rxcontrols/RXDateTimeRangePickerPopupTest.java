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
 * Draft preview, confirmation and typed text of {@link RXDateTimeRangePicker}.
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
     * Verifies two day picks preview the range, OK commits it and closing discards a draft.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void okCommitsPreviewedRange() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            Button ok = (Button) root.lookup(".ok-button");
            assertTrue(ok.isDisabled());
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            click(dayCell(root, LocalDate.of(2026, 11, 22)));
            assertEquals("11/20/2026 00:00", editor(picker, 0).getText());
            assertNull(picker.getValue());
            ok.fire();
            assertEquals(RXDateTimeRange.of(LocalDate.of(2026, 11, 20).atStartOfDay(),
                    LocalDate.of(2026, 11, 22).atStartOfDay()), picker.getValue());

            picker.show();
            root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 25)));
            click(dayCell(root, LocalDate.of(2026, 11, 26)));
            press(editor(picker, 0), KeyCode.ESCAPE);
            assertEquals(LocalDate.of(2026, 11, 20), picker.getValue().start().toLocalDate());
            assertEquals("11/20/2026 00:00", editor(picker, 0).getText());
        });
    }

    /**
     * Verifies typed endpoints commit together in order.
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
     * Verifies OK is disabled while a new range is half picked over a committed one.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void okDisabledWhileHalfPicked() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangePicker picker = newShownPicker();
            RXDateTimeRange old = RXDateTimeRange.of(LocalDate.of(2026, 11, 1).atStartOfDay(),
                    LocalDate.of(2026, 11, 2).atStartOfDay());
            picker.setValue(old);
            int[] actions = new int[1];
            picker.setOnAction(event -> actions[0]++);
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            Button ok = (Button) root.lookup(".ok-button");
            assertTrue(ok.isDisabled());
            ok.fire();
            press(editor(picker, 1), KeyCode.ENTER);
            assertEquals(old, picker.getValue());
            assertEquals(0, actions[0]);
        });
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
