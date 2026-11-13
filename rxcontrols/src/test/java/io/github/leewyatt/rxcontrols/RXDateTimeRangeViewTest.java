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
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day and time editing of {@link RXDateTimeRangeView}.
 */
public class RXDateTimeRangeViewTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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
     * Verifies times chosen before the range is complete are applied to it.
     */
    @Test
    public void pendingTimesApplyOnCompletion() {
        RXDateTimeRangeView view = newView();
        click(view.lookup(".start-tab"));
        click(timeCell(view, ".hour-column", "09"));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertTrue(hasPseudoClass(view.lookup(".end-tab"), "selected"));
        click(timeCell(view, ".hour-column", "17"));
        assertNull(view.getValue());
        click(dayCell(view, LocalDate.of(2026, 11, 18)));
        assertEquals(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 18, 9, 0), LocalDateTime.of(2026, 11, 20, 17, 0)),
                view.getValue());
    }

    /**
     * Verifies on a single-day range the end column cannot go before the start time.
     */
    @Test
    public void sameDayEndCannotPrecedeStart() {
        RXDateTimeRangeView view = newView();
        view.setValue(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 20, 10, 0),
                LocalDateTime.of(2026, 11, 20, 12, 0)));
        click(view.lookup(".end-tab"));
        view.applyCss();
        assertTrue(timeCell(view, ".hour-column", "09").isDisabled());
        click(timeCell(view, ".hour-column", "11"));
        assertEquals(LocalDateTime.of(2026, 11, 20, 11, 0), view.getValue().end());
    }

    /**
     * Verifies the keyboard picks days, then the start and end times, then confirms.
     */
    @Test
    public void keyboardFlowConfirmsAfterEndTime() {
        RXDateTimeRangeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.ENTER);
        assertEquals(RXDateTimeRange.of(LocalDate.of(2026, 11, 14).atStartOfDay(),
                LocalDate.of(2026, 11, 15).atStartOfDay()), view.getValue());
        assertTrue(hasPseudoClass(view.lookup(".start-tab"), "selected"));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        assertEquals(0, actions.get());
        assertTrue(hasPseudoClass(view.lookup(".end-tab"), "selected"));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        assertEquals(1, actions.get());
        assertEquals(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 14, 1, 0), LocalDateTime.of(2026, 11, 15, 2, 0)),
                view.getValue());
    }

    private static RXDateTimeRangeView newView() {
        RXDateTimeRangeView view = new RXDateTimeRangeView();
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 900, 420);
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
