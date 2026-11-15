package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.datetime.TimeEndpointTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Column picking, constraints and keyboard behavior of {@link RXTimeView}.
 */
public class RXTimeViewTest {

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
     * Verifies clicking an hour with no value picks that hour with zero minutes.
     */
    @Test
    public void pickingHourStartsFromZeroMinutes() {
        RXTimeView view = newView();
        PickerTestSupport.click(cell(view, ".hour-column", "14"));
        assertEquals(LocalTime.of(14, 0), view.getValue());
        PickerTestSupport.click(cell(view, ".minute-column", "35"));
        assertEquals(LocalTime.of(14, 35), view.getValue());
    }

    /**
     * Verifies picks fire no action while a double click does.
     */
    @Test
    public void doubleClickConfirms() {
        RXTimeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        PickerTestSupport.click(cell(view, ".hour-column", "09"));
        assertEquals(0, actions.get());
        Label cell = cell(view, ".minute-column", "30");
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2, false, false, false, false, true, false, false,
                true, false, false, null));
        assertEquals(1, actions.get());
    }

    /**
     * Verifies unavailable items are disabled and picks snap into the bounds.
     */
    @Test
    public void boundsDisableItemsAndSnapPicks() {
        RXTimeView view = newView();
        view.setMinTime(LocalTime.of(9, 30));
        view.setMaxTime(LocalTime.of(17, 0));
        assertTrue(cell(view, ".hour-column", "08").isDisabled());
        assertFalse(cell(view, ".hour-column", "09").isDisabled());
        PickerTestSupport.click(cell(view, ".hour-column", "09"));
        assertEquals(LocalTime.of(9, 30), view.getValue());
        assertTrue(cell(view, ".minute-column", "15").isDisabled());
    }

    /**
     * Verifies the steps limit the offered items and the second column follows showSeconds.
     */
    @Test
    public void stepsAndSecondsShapeColumns() {
        RXTimeView view = newView();
        view.setMinuteStep(15);
        assertEquals(List.of("00", "15", "30", "45"), texts(view, ".minute-column"));
        assertTrue(view.lookup(".second-column") == null);
        view.setShowSeconds(true);
        view.applyCss();
        assertEquals(60, texts(view, ".second-column").size());
    }

    /**
     * Verifies picks and a bound change ask a time filter once per time, and a new filter is asked.
     */
    @Test
    public void timeFilterIsAskedOncePerTime() {
        RXTimeView view = newView();
        int[] asked = new int[24 * 60];
        view.setTimeFilter(time -> {
            asked[time.toSecondOfDay() / 60]++;
            return time.getHour() != 12;
        });
        view.applyCss();
        PickerTestSupport.click(cell(view, ".hour-column", "09"));
        PickerTestSupport.click(cell(view, ".minute-column", "30"));
        view.setMinTime(LocalTime.of(8, 0));
        view.applyCss();
        assertTrue(cell(view, ".hour-column", "12").isDisabled());
        assertEquals(1, Arrays.stream(asked).max().getAsInt());

        AtomicInteger calls = new AtomicInteger();
        view.setTimeFilter(time -> {
            calls.incrementAndGet();
            return true;
        });
        view.applyCss();
        assertTrue(calls.get() > 0);
        assertFalse(cell(view, ".hour-column", "12").isDisabled());
    }

    /**
     * Verifies the 12-hour clock offers 12 hours of the current half plus an AM / PM switch.
     */
    @Test
    public void twelveHourClockSwitchesHalves() {
        RXTimeView view = newView();
        view.setTwelveHourClock(true);
        view.setValue(LocalTime.of(9, 15));
        view.applyCss();
        assertEquals("12", texts(view, ".hour-column").get(0));
        assertEquals(12, texts(view, ".hour-column").size());
        PickerTestSupport.click(cell(view, ".meridiem-column", "PM"));
        assertEquals(LocalTime.of(21, 15), view.getValue());
    }

    /**
     * Verifies keyboard seeding, column switching, value moves and Enter confirmation.
     */
    @Test
    public void keyboardMovesColumns() {
        RXTimeView view = newView();
        view.setValue(LocalTime.of(10, 20));
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        press(view, KeyCode.DOWN);
        assertTrue(hasPseudoClass(view.lookup(".hour-column"), "active"));
        press(view, KeyCode.DOWN);
        assertEquals(LocalTime.of(11, 20), view.getValue());
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.UP);
        assertEquals(LocalTime.of(11, 19), view.getValue());
        press(view, KeyCode.ENTER);
        assertEquals(1, actions.get());
    }

    /**
     * Verifies Enter and a double click fire no action while there is no value.
     */
    @Test
    public void confirmWithoutValueFiresNothing() {
        RXTimeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        PickerTestSupport.doubleClick(view.lookup(".hour-column").lookup(".items"));
        assertEquals(0, actions.get());
    }

    /**
     * Verifies a value outside the bounds is kept and flagged invalid.
     */
    @Test
    public void invalidValueFlagged() {
        RXTimeView view = newView();
        view.setMaxTime(LocalTime.of(12, 0));
        view.setValue(LocalTime.of(13, 0));
        assertTrue(hasPseudoClass(view, "invalid"));
        assertEquals(LocalTime.of(13, 0), view.getValue());
    }

    /**
     * Verifies a view without a value picks from the other endpoint's time, and from the first time without one.
     */
    @Test
    public void otherEndpointSeedsPicks() {
        RXTimeView view = newView();
        TimeEndpointTarget endpoint = (TimeEndpointTarget) view.getSkin();
        endpoint.setOtherEndpoint(LocalTime.of(18, 30));
        PickerTestSupport.click(cell(view, ".minute-column", "45"));
        assertEquals(LocalTime.of(18, 45), view.getValue());

        view.setValue(null);
        endpoint.setOtherEndpoint(null);
        PickerTestSupport.click(cell(view, ".minute-column", "45"));
        assertEquals(LocalTime.of(0, 45), view.getValue());
    }

    /**
     * Verifies keyboard moves without a value start from the other endpoint's time, snapped to the steps.
     */
    @Test
    public void otherEndpointSeedsKeyboardMoves() {
        RXTimeView view = newView();
        view.setMinuteStep(15);
        ((TimeEndpointTarget) view.getSkin()).setOtherEndpoint(LocalTime.of(18, 37));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.DOWN);
        assertEquals(LocalTime.of(19, 30), view.getValue());
    }

    /**
     * Verifies a 12-hour view without a value offers the half of the day of the other endpoint.
     */
    @Test
    public void otherEndpointChoosesHalfOfDay() {
        RXTimeView view = newView();
        view.setTwelveHourClock(true);
        view.applyCss();
        ((TimeEndpointTarget) view.getSkin()).setOtherEndpoint(LocalTime.of(18, 30));
        PickerTestSupport.click(cell(view, ".hour-column", "07"));
        assertEquals(LocalTime.of(19, 30), view.getValue());
    }

    // ==================== Helpers ====================

    private static RXTimeView newView() {
        RXTimeView view = new RXTimeView();
        view.setLocale(Locale.US);
        Scene scene = new Scene(new StackPane(view), 300, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    private static List<Label> cellsOf(RXTimeView view, String column) {
        Node columnNode = view.lookup(column);
        return columnNode.lookupAll(".time-cell").stream()
                .filter(Node::isVisible).map(node -> (Label) node).collect(Collectors.toList());
    }

    private static List<String> texts(RXTimeView view, String column) {
        return cellsOf(view, column).stream().map(Label::getText).collect(Collectors.toList());
    }

    private static Label cell(RXTimeView view, String column, String text) {
        return cellsOf(view, column).stream().filter(label -> text.equals(label.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " in " + column));
    }
}
