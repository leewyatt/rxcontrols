package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text typed while the popup is closed, shown in the popup of the pickers that commit on a pick.
 */
@Tag("ui")
public class RXPickerTypedTextPopupTest {

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
     * Verifies the date picker opens on a typed date without committing it or changing the text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void datePickerOpensOnTypedDate() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = shown(new RXDatePicker(LocalDate.of(2026, 11, 10)));
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2/3/2027");
            picker.show();
            Node root = findPopup().getScene().getRoot();
            assertTrue(dayCell(root, LocalDate.of(2027, 2, 3)).isSelected());
            assertEquals(LocalDate.of(2026, 11, 10), picker.getValue());
            assertEquals("2/3/2027", field.getText());
        });
    }

    /**
     * Verifies the date picker opens on its value when the typed text is not a selectable date.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void datePickerIgnoresUnusableTypedText() throws InterruptedException {
        runOnFx(() -> {
            RXDatePicker picker = shown(new RXDatePicker(LocalDate.of(2026, 11, 10)));
            picker.setMaxDate(LocalDate.of(2026, 12, 31));
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2/3/2027");
            picker.show();
            assertTrue(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 10)).isSelected());
            picker.hide();
            field.setText("soon");
            picker.show();
            assertTrue(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 10)).isSelected());
        });
    }

    /**
     * Verifies the week picker opens on a typed week.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void weekPickerOpensOnTypedWeek() throws InterruptedException {
        runOnFx(() -> {
            RXWeekPicker picker = shown(new RXWeekPicker(LocalDate.of(2026, 11, 10)));
            picker.setWeekFields(WeekFields.ISO);
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2026-W03");
            picker.show();
            RXWeekView view = (RXWeekView) findPopup().getScene().getRoot().lookup(".rx-week-view");
            assertEquals(LocalDate.of(2026, 1, 12), view.getValue());
            assertEquals(LocalDate.of(2026, 11, 10), picker.getValue());
        });
    }

    /**
     * Verifies the month picker opens on a typed month.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void monthPickerOpensOnTypedMonth() throws InterruptedException {
        runOnFx(() -> {
            RXMonthPicker picker = shown(new RXMonthPicker(YearMonth.of(2026, 3)));
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2027-1");
            picker.show();
            RXMonthView view = (RXMonthView) findPopup().getScene().getRoot().lookup(".rx-month-view");
            assertEquals(YearMonth.of(2027, 1), view.getValue());
            assertEquals(YearMonth.of(2026, 3), picker.getValue());
        });
    }

    /**
     * Verifies the date range picker opens on a typed range.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void dateRangePickerOpensOnTypedRange() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = shown(new RXDateRangePicker());
            editor(picker, 0).requestFocus();
            editor(picker, 0).setText("12/1/2026");
            editor(picker, 1).setText("12/5/2026");
            picker.show();
            RXDateRangeView view =
                    (RXDateRangeView) findPopup().getScene().getRoot().lookup(".rx-date-range-view");
            assertEquals(RXDateRange.of(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)), view.getValue());
            assertNull(picker.getValue());
            assertEquals("12/5/2026", editor(picker, 1).getText());
        });
    }

    /**
     * Verifies the month range picker opens on a typed range.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void monthRangePickerOpensOnTypedRange() throws InterruptedException {
        runOnFx(() -> {
            RXMonthRangePicker picker = shown(new RXMonthRangePicker());
            editor(picker, 0).requestFocus();
            editor(picker, 0).setText("2027-01");
            editor(picker, 1).setText("2027-03");
            picker.show();
            RXMonthRangeView view =
                    (RXMonthRangeView) findPopup().getScene().getRoot().lookup(".rx-month-range-view");
            assertEquals(RXMonthRange.of(YearMonth.of(2027, 1), YearMonth.of(2027, 3)), view.getValue());
            assertNull(picker.getValue());
        });
    }

    private <P extends RXPickerBase<?>> P shown(P picker) {
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
