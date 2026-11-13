package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Date and time composition, combined constraints and keyboard flow of {@link RXDateTimeView}.
 */
public class RXDateTimeViewTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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
     * Verifies a day pick keeps the time and a time pick keeps the day, without firing the action.
     */
    @Test
    public void picksKeepTheOtherPart() {
        RXDateTimeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(LocalDate.of(2026, 11, 20).atStartOfDay(), view.getValue());
        click(timeCell(view, ".hour-column", "15"));
        assertEquals(LocalDate.of(2026, 11, 20).atTime(15, 0), view.getValue());
        click(dayCell(view, LocalDate.of(2026, 11, 21)));
        assertEquals(LocalDate.of(2026, 11, 21).atTime(15, 0), view.getValue());
        assertEquals(0, actions.get());
        assertEquals("15:00", ((Label) view.lookup(".time-header")).getText());
    }

    /**
     * Verifies a time pick without a value uses today.
     */
    @Test
    public void timeFirstUsesToday() {
        RXDateTimeView view = newView();
        click(timeCell(view, ".hour-column", "08"));
        assertEquals(LocalDate.of(2026, 11, 14).atTime(8, 0), view.getValue());
    }

    /**
     * Verifies the minimum's own day limits its times and picking it snaps the kept time.
     */
    @Test
    public void minimumDayLimitsTimes() {
        RXDateTimeView view = newView();
        view.setMinDateTime(LocalDateTime.of(2026, 11, 14, 15, 30));
        view.setValue(LocalDateTime.of(2026, 11, 16, 10, 0));
        assertTrue(dayCell(view, LocalDate.of(2026, 11, 13)).isDisabled());
        assertFalse(dayCell(view, LocalDate.of(2026, 11, 14)).isDisabled());
        assertFalse(timeCell(view, ".hour-column", "10").isDisabled());
        click(dayCell(view, LocalDate.of(2026, 11, 14)));
        assertEquals(LocalDateTime.of(2026, 11, 14, 15, 30), view.getValue());
        view.applyCss();
        assertTrue(timeCell(view, ".hour-column", "14").isDisabled());
    }

    /**
     * Verifies Enter on a day moves the keyboard to the time columns and Enter there confirms.
     */
    @Test
    public void keyboardFlowsFromDayToTime() {
        RXDateTimeView view = newView();
        view.setValue(LocalDateTime.of(2026, 11, 10, 9, 0));
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        press(view, KeyCode.DOWN);
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.ENTER);
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), view.getValue());
        assertTrue(hasPseudoClass(view.lookup(".hour-column"), "active"));
        press(view, KeyCode.DOWN);
        assertEquals(LocalDateTime.of(2026, 11, 11, 10, 0), view.getValue());
        press(view, KeyCode.ENTER);
        assertEquals(1, actions.get());
        press(view, KeyCode.LEFT);
        assertFalse(hasPseudoClass(view.lookup(".hour-column"), "active"));
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 11, 12), view.getValue().toLocalDate());
    }

    private static RXDateTimeView newView() {
        RXDateTimeView view = new RXDateTimeView();
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 600, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    private static Label timeCell(Node root, String column, String text) {
        return root.lookup(column).lookupAll(".time-cell").stream()
                .filter(node -> node.isVisible() && text.equals(((Label) node).getText()))
                .map(node -> (Label) node).findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " in " + column));
    }
}
