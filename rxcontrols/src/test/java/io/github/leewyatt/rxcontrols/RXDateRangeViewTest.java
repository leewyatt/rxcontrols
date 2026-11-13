package io.github.leewyatt.rxcontrols;

import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anchor, preview and completion behavior of {@link RXDateRangeView}.
 */
public class RXDateRangeViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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
     * Verifies two linked months are shown, starting at the current month.
     */
    @Test
    public void showsTwoLinkedMonths() {
        RXDateRangeView view = newView();
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        dayCell(view, LocalDate.of(2026, 12, 31));
        click(view.lookup(".next-button"));
        assertEquals(YearMonth.of(2026, 12), view.getDisplayedMonth());
        dayCell(view, LocalDate.of(2027, 1, 31));
    }

    /**
     * Verifies the first pick only anchors and the second completes the range in order.
     */
    @Test
    public void secondPickCompletesReversedRange() {
        RXDateRangeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        click(dayCell(view, LocalDate.of(2026, 12, 3)));
        assertNull(view.getValue());
        assertEquals(0, actions.get());
        assertTrue(hasPseudoClass(dayCell(view, LocalDate.of(2026, 12, 3)), "range-start"));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 3)), view.getValue());
        assertEquals(1, actions.get());
        assertTrue(hasPseudoClass(dayCell(view, LocalDate.of(2026, 11, 25)), "in-range"));
    }

    /**
     * Verifies hovering previews the pending range.
     */
    @Test
    public void hoverPreviewsPendingRange() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 10)));
        DateCell target = dayCell(view, LocalDate.of(2026, 11, 13));
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
        DateCell middle = dayCell(view, LocalDate.of(2026, 11, 12));
        assertTrue(hasPseudoClass(middle, "in-range"));
        assertTrue(hasPseudoClass(middle, "preview"));
        assertTrue(hasPseudoClass(target, "range-end"));
    }

    /**
     * Verifies Escape and a programmatic value drop the pending anchor.
     */
    @Test
    public void escapeDropsAnchor() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 10)));
        press(view, KeyCode.ESCAPE);
        assertFalse(hasPseudoClass(dayCell(view, LocalDate.of(2026, 11, 10)), "range-start"));
        click(dayCell(view, LocalDate.of(2026, 11, 11)));
        view.setValue(RXDateRange.of(TODAY, TODAY));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(RXDateRange.of(TODAY, TODAY), view.getValue());
    }

    /**
     * Verifies the keyboard picks both endpoints.
     */
    @Test
    public void keyboardPicksRange() {
        RXDateRangeView view = newView();
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)), view.getValue());
    }

    /**
     * Verifies only the endpoints must be selectable and a disallowed value is flagged.
     */
    @Test
    public void endpointsAreValidated() {
        RXDateRangeView view = newView();
        view.setDateFilter(date -> date.getDayOfMonth() != 15);
        click(dayCell(view, LocalDate.of(2026, 11, 14)));
        click(dayCell(view, LocalDate.of(2026, 11, 16)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 16)), view.getValue());
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16)));
        assertTrue(hasPseudoClass(view, "invalid"));
    }

    private static RXDateRangeView newView() {
        RXDateRangeView view = new RXDateRangeView();
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 700, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }
}
