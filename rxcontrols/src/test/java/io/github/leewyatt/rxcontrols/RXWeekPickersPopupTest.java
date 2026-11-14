package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
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
 * Field text and popup behavior of {@link RXWeekPicker} and {@link RXWeekRangePicker}.
 * Weeks follow the ISO rules: they start on Monday.
 */
@Tag("ui")
public class RXWeekPickersPopupTest {

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
     * Verifies picking a day in the popup commits the first day of its week and closes the popup.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void pickCommitsFirstDayOfWeek() throws InterruptedException {
        runOnFx(() -> {
            RXWeekPicker picker = shown(new RXWeekPicker());
            picker.setWeekFields(WeekFields.ISO);
            AtomicInteger actions = new AtomicInteger();
            picker.setOnAction(event -> actions.incrementAndGet());
            editor(picker, 0).requestFocus();
            picker.show();
            click(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 12)));
            assertEquals(LocalDate.of(2026, 11, 9), picker.getValue());
            assertEquals(1, actions.get());
            assertFalse(picker.isShowing());
            assertEquals("2026-W46", editor(picker, 0).getText());
        });
    }

    /**
     * Verifies week text is parsed to the first day of the week, and a week the year lacks is rejected.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedWeekIsParsed() throws InterruptedException {
        runOnFx(() -> {
            RXWeekPicker picker = shown(new RXWeekPicker());
            picker.setWeekFields(WeekFields.ISO);
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2026-w3");
            press(field, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 1, 12), picker.getValue());
            assertEquals("2026-W03", field.getText());

            field.setText("2026W53");
            press(field, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 12, 28), picker.getValue());

            field.setText("2025-W53");
            press(field, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 12, 28), picker.getValue());
            assertTrue(hasPseudoClass(picker, "invalid"));
        });
    }

    /**
     * Verifies the field shows the week of any value, numbered by the week rules in effect.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void fieldShowsWeekOfAnyDay() throws InterruptedException {
        runOnFx(() -> {
            RXWeekPicker picker = shown(new RXWeekPicker(LocalDate.of(2027, 1, 2)));
            picker.setWeekFields(WeekFields.ISO);
            assertEquals("2026-W53", editor(picker, 0).getText());
            picker.setWeekFields(WeekFields.SUNDAY_START);
            assertEquals("2027-W01", editor(picker, 0).getText());
            assertEquals(LocalDate.of(2027, 1, 2), picker.getValue());
        });
    }

    /**
     * Verifies a custom converter that parses to a mid-week day still commits the week's first day.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void customConverterDayIsTakenAsItsWeek() throws InterruptedException {
        runOnFx(() -> {
            RXWeekPicker picker = shown(new RXWeekPicker());
            picker.setWeekFields(WeekFields.ISO);
            picker.setMaxWeek(LocalDate.of(2026, 11, 17));
            picker.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate date) {
                    return date == null ? "" : date.toString();
                }

                @Override
                public LocalDate fromString(String text) {
                    return LocalDate.parse(text);
                }
            });
            TextField field = editor(picker, 0);
            field.requestFocus();
            field.setText("2026-11-20");
            press(field, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 11, 16), picker.getValue(), "20 Nov is in the last allowed week");
            assertEquals("2026-11-16", field.getText());
            field.setText("2026-11-23");
            press(field, KeyCode.ENTER);
            assertEquals(LocalDate.of(2026, 11, 16), picker.getValue());
            assertTrue(hasPseudoClass(picker, "invalid"));
        });
    }

    /**
     * Verifies two picks commit a range of whole weeks once, after previewing the first week.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void twoPicksCommitWeekRange() throws InterruptedException {
        runOnFx(() -> {
            RXWeekRangePicker picker = shown(new RXWeekRangePicker());
            picker.setWeekFields(WeekFields.ISO);
            AtomicInteger changes = new AtomicInteger();
            picker.valueProperty().addListener(observable -> changes.incrementAndGet());
            editor(picker, 0).requestFocus();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 18)));
            assertEquals("2026-W47", editor(picker, 0).getText());
            assertTrue(stage.getScene().getFocusOwner() == editor(picker, 1));
            assertNull(picker.getValue());
            click(dayCell(root, LocalDate.of(2026, 12, 9)));
            assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 13)), picker.getValue());
            assertEquals(1, changes.get());
            assertFalse(picker.isShowing());
            assertEquals("2026-W50", editor(picker, 1).getText());
        });
    }

    /**
     * Verifies typed weeks commit together in order, and a week past the bound is rejected.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedWeeksCommitTogether() throws InterruptedException {
        runOnFx(() -> {
            RXWeekRangePicker picker = shown(new RXWeekRangePicker());
            picker.setWeekFields(WeekFields.ISO);
            picker.setMaxWeek(LocalDate.of(2026, 12, 9));
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.requestFocus();
            start.setText("2026-W50");
            press(start, KeyCode.ENTER);
            assertTrue(stage.getScene().getFocusOwner() == end, "Enter with an empty end moves to the end field");
            end.setText("2026-W47");
            press(end, KeyCode.ENTER);
            RXDateRange committed = RXDateRange.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 13));
            assertEquals(committed, picker.getValue());
            assertEquals("2026-W47", start.getText());
            assertEquals("2026-W50", end.getText());

            end.setText("2026-W51");
            press(end, KeyCode.ENTER);
            assertEquals(committed, picker.getValue());
            assertTrue(hasPseudoClass(picker, "invalid"));
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
