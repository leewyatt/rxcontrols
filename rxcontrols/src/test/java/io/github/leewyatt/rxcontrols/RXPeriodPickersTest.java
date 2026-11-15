package io.github.leewyatt.rxcontrols;

import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.time.YearMonth;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field formatting, typed commits and constraints of the month, year and quarter pickers.
 */
public class RXPeriodPickersTest {

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
     * Verifies the month picker formats, parses and validates its field.
     */
    @Test
    public void monthPickerField() {
        RXMonthPicker picker = show(new RXMonthPicker(YearMonth.of(2026, 3)));
        TextField editor = editor(picker, 0);
        assertEquals("2026-03", editor.getText());
        editor.setText("2027-1");
        press(editor, KeyCode.ENTER);
        assertEquals(YearMonth.of(2027, 1), picker.getValue());
        picker.setMaxMonth(YearMonth.of(2027, 6));
        editor.setText("2027-7");
        press(editor, KeyCode.ENTER);
        assertEquals(YearMonth.of(2027, 1), picker.getValue());
        assertTrue(hasPseudoClass(picker, "invalid"));
    }

    /**
     * Verifies the year and quarter pickers commit typed values.
     */
    @Test
    public void yearAndQuarterPickerFields() {
        RXYearPicker years = show(new RXYearPicker());
        editor(years, 0).setText("1999");
        press(editor(years, 0), KeyCode.ENTER);
        assertEquals(Year.of(1999), years.getValue());

        RXQuarterPicker quarters = show(new RXQuarterPicker(RXYearQuarter.of(2026, 1)));
        assertEquals("2026-Q1", editor(quarters, 0).getText());
        editor(quarters, 0).setText("2026-Q5");
        press(editor(quarters, 0), KeyCode.ENTER);
        assertTrue(hasPseudoClass(quarters, "invalid"));
        press(editor(quarters, 0), KeyCode.ESCAPE);
        assertEquals("2026-Q1", editor(quarters, 0).getText());
        editor(quarters, 0).setText("2026-q4");
        press(editor(quarters, 0), KeyCode.ENTER);
        assertEquals(RXYearQuarter.of(2026, 4), quarters.getValue());
        assertEquals("2026-Q4", editor(quarters, 0).getText());
    }

    private static <P extends RXPickerBase<?>> P show(P picker) {
        picker.setLocale(Locale.US);
        Scene scene = new Scene(new StackPane(picker), 300, 200);
        picker.applyCss();
        scene.getRoot().layout();
        return picker;
    }
}
