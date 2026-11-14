package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
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
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.cells;
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
 * Two-field and popup behavior of {@link RXMonthRangePicker}, {@link RXQuarterRangePicker}
 * and {@link RXYearRangePicker}.
 */
@Tag("ui")
public class RXPeriodRangePickersPopupTest {

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
     * Verifies two month picks commit the whole range once, after previewing the start.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void twoPicksCommitWholeMonthRange() throws InterruptedException {
        runOnFx(() -> {
            RXMonthRangePicker picker = shown(new RXMonthRangePicker());
            AtomicInteger changes = new AtomicInteger();
            picker.valueProperty().addListener(observable -> changes.incrementAndGet());
            AtomicInteger actions = new AtomicInteger();
            picker.setOnAction(event -> actions.incrementAndGet());
            editor(picker, 0).requestFocus();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            assertEquals(24, cells(root, ".month-cell").size());
            click(cell(root, ".month-cell", LocalDate.of(2026, 11, 1)));
            assertEquals("2026-11", editor(picker, 0).getText());
            assertTrue(stage.getScene().getFocusOwner() == editor(picker, 1));
            assertNull(picker.getValue());
            click(cell(root, ".month-cell", LocalDate.of(2027, 2, 1)));
            assertEquals(RXMonthRange.of(YearMonth.of(2026, 11), YearMonth.of(2027, 2)), picker.getValue());
            assertEquals(1, changes.get());
            assertEquals(1, actions.get());
            assertFalse(picker.isShowing());
            assertEquals("2027-02", editor(picker, 1).getText());
        });
    }

    /**
     * Verifies closing after one pick discards it and restores the fields.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void halfPickIsDiscardedOnClose() throws InterruptedException {
        runOnFx(() -> {
            RXYearRange old = RXYearRange.of(Year.of(2020), Year.of(2033));
            RXYearRangePicker picker = shown(new RXYearRangePicker(old));
            editor(picker, 0).requestFocus();
            picker.show();
            click(cell(findPopup().getScene().getRoot(), ".year-cell", LocalDate.of(2025, 1, 1)));
            assertEquals("2025", editor(picker, 0).getText());
            press(editor(picker, 1), KeyCode.ESCAPE);
            assertEquals(old, picker.getValue());
            assertEquals("2020", editor(picker, 0).getText());
            assertEquals("2033", editor(picker, 1).getText());
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(cell(root, ".year-cell", LocalDate.of(2031, 1, 1)));
            click(cell(root, ".year-cell", LocalDate.of(2022, 1, 1)));
            assertEquals(RXYearRange.of(Year.of(2022), Year.of(2031)), picker.getValue());
        });
    }

    /**
     * Verifies typed quarters commit together in order, and a lone endpoint is reverted.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedQuartersCommitTogether() throws InterruptedException {
        runOnFx(() -> {
            RXQuarterRangePicker picker = shown(new RXQuarterRangePicker());
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.requestFocus();
            start.setText("2026-Q3");
            press(start, KeyCode.ENTER);
            assertTrue(stage.getScene().getFocusOwner() == end, "Enter with an empty end moves to the end field");
            assertNull(picker.getValue());
            end.setText("2025-q1");
            press(end, KeyCode.ENTER);
            assertEquals(RXQuarterRange.of(RXYearQuarter.of(2025, 1), RXYearQuarter.of(2026, 3)), picker.getValue());
            assertEquals("2025-Q1", start.getText());

            end.setText("");
            press(end, KeyCode.ENTER);
            assertTrue(hasPseudoClass(picker, "invalid"));
            stage.getScene().getRoot().lookup(".button").requestFocus();
            assertEquals("2026-Q3", end.getText());
            assertFalse(hasPseudoClass(picker, "invalid"));
        });
    }

    /**
     * Verifies a typed endpoint outside the bounds is flagged and not committed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedEndpointOutsideBoundsIsRejected() throws InterruptedException {
        runOnFx(() -> {
            RXYearRange old = RXYearRange.of(Year.of(2020), Year.of(2025));
            RXYearRangePicker picker = shown(new RXYearRangePicker(old));
            picker.setMaxYear(Year.of(2030));
            TextField end = editor(picker, 1);
            end.requestFocus();
            end.setText("2031");
            press(end, KeyCode.ENTER);
            assertEquals(old, picker.getValue());
            assertTrue(hasPseudoClass(picker, "invalid"));
            end.setText("2030");
            press(end, KeyCode.ENTER);
            assertEquals(RXYearRange.of(Year.of(2020), Year.of(2030)), picker.getValue());
            assertFalse(hasPseudoClass(picker, "invalid"));
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

    private static DateCell cell(Node root, String selector, LocalDate first) {
        return cells(root, selector).stream()
                .filter(cell -> first.equals(cell.getItem()) && !hasPseudoClass(cell, "adjacent"))
                .findFirst().orElseThrow(() -> new AssertionError("no " + first + " in " + selector));
    }
}
