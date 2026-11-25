package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.datetime.TimeEndpointTarget;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
     * Verifies the first keyboard move without a value lands on the other endpoint's time, snapped to the steps.
     */
    @Test
    public void otherEndpointSeedsKeyboardMoves() {
        RXTimeView view = newView();
        view.setMinuteStep(15);
        ((TimeEndpointTarget) view.getSkin()).setOtherEndpoint(LocalTime.of(18, 37));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.DOWN);
        assertEquals(LocalTime.of(18, 30), view.getValue());
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

    /**
     * Verifies the columns stay centered in a view wider than they need, as in a picker
     * popup whose Now / OK footer is wider than two columns.
     */
    @Test
    public void columnsStayCenteredInWiderView() {
        RXTimeView view = newView();
        Bounds viewBounds = view.localToScene(view.getLayoutBounds());
        Node hourColumn = view.lookup(".hour-column");
        Node minuteColumn = view.lookup(".minute-column");
        Bounds hour = hourColumn.localToScene(hourColumn.getLayoutBounds());
        Bounds minute = minuteColumn.localToScene(minuteColumn.getLayoutBounds());

        assertTrue(viewBounds.getWidth() > view.prefWidth(-1) + 1.0, "the view is wider than its columns");
        assertEquals(hour.getMinX() - viewBounds.getMinX(), viewBounds.getMaxX() - minute.getMaxX(), 1.0);
    }

    /**
     * Verifies the wheel scrolls a column smoothly, without picking.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void wheelScrollsColumnSmoothly() throws InterruptedException {
        AtomicReference<RXTimeView> viewRef = new AtomicReference<>();
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            AtomicBoolean bubbled = new AtomicBoolean();
            view.getParent().addEventHandler(ScrollEvent.SCROLL, event -> bubbled.set(true));

            cell(view, ".minute-column", "05").fireEvent(wheel(-120.0));

            assertFalse(bubbled.get(), "the column uses the wheel");
            assertEquals(0.0, column(view, ".minute-column").getVvalue(), 1e-9, "no immediate jump");
            viewRef.set(view);
        });
        waitForFx(300.0);
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = viewRef.get();
            assertTrue(column(view, ".minute-column").getVvalue() > 0.0, "the wheel momentum scrolls the column");
            assertNull(view.getValue(), "scrolling does not pick");
        });
    }

    /**
     * Verifies picking an item while the wheel momentum runs scrolls that item to the top.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void pickDuringWheelMomentumScrollsPickToTop() throws InterruptedException {
        AtomicReference<RXTimeView> viewRef = new AtomicReference<>();
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            cell(view, ".minute-column", "05").fireEvent(wheel(-120.0));
            viewRef.set(view);
        });
        waitForFx(60.0);
        PickerTestSupport.runOnFx(() -> PickerTestSupport.click(cell(viewRef.get(), ".minute-column", "10")));
        waitForFx(500.0);
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = viewRef.get();
            view.getScene().getRoot().layout();
            assertAtColumnTop(view, "10");
        });
    }

    /**
     * Verifies the wheel takes over from the scroll to a just-picked item.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void wheelTakesOverFromScrollToPick() throws InterruptedException {
        AtomicReference<RXTimeView> viewRef = new AtomicReference<>();
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            PickerTestSupport.click(cell(view, ".minute-column", "30"));
            cell(view, ".minute-column", "05").fireEvent(wheel(-120.0));
            viewRef.set(view);
        });
        waitForFx(500.0);
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = viewRef.get();
            view.getScene().getRoot().layout();
            assertTrue(offsetFromColumnTop(view, "30") > 5.0, "the scroll to the pick stopped short of it");
        });
    }

    /**
     * Verifies wheel input a column cannot use goes on to the parent.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void wheelAtColumnTopGoesToParent() throws InterruptedException {
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            AtomicBoolean bubbled = new AtomicBoolean();
            view.getParent().addEventHandler(ScrollEvent.SCROLL, event -> bubbled.set(true));

            cell(view, ".hour-column", "00").fireEvent(wheel(120.0));

            assertTrue(bubbled.get());
            assertEquals(0.0, column(view, ".hour-column").getVvalue(), 1e-9);
        });
    }

    /**
     * Verifies a direct touch drag goes to the ScrollPane itself and follows the finger,
     * without the wheel multiplier.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void directTouchDragFollowsFinger() throws InterruptedException {
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            double before = offsetFromColumnTop(view, "05");

            cell(view, ".minute-column", "05").fireEvent(scrollEvent(0.0, -40.0, true));
            view.getScene().getRoot().layout();

            assertTrue(before - offsetFromColumnTop(view, "05") > 20.0, "the column follows most of the drag");
        });
    }

    /**
     * Verifies a press stops the wheel momentum, so the release lands on the pressed item.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void pressStopsWheelMomentum() throws InterruptedException {
        AtomicReference<RXTimeView> viewRef = new AtomicReference<>();
        double[] atPress = new double[1];
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            cell(view, ".minute-column", "05").fireEvent(wheel(-120.0));
            viewRef.set(view);
        });
        waitForFx(60.0);
        PickerTestSupport.runOnFx(() -> {
            ScrollPane minutes = column(viewRef.get(), ".minute-column");
            PickerTestSupport.pressMouse(cell(viewRef.get(), ".minute-column", "08"));
            atPress[0] = minutes.getVvalue();
        });
        waitForFx(300.0);
        PickerTestSupport.runOnFx(() ->
                assertEquals(atPress[0], column(viewRef.get(), ".minute-column").getVvalue(), 1e-9));
    }

    /**
     * Verifies scroll input a column does not use leaves the scroll to a just-picked item running.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void unusedScrollKeepsScrollToPick() throws InterruptedException {
        AtomicReference<RXTimeView> viewRef = new AtomicReference<>();
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = newView();
            PickerTestSupport.click(cell(view, ".minute-column", "30"));
            cell(view, ".minute-column", "05").fireEvent(scrollEvent(-40.0, 0.0, false));
            viewRef.set(view);
        });
        waitForFx(500.0);
        PickerTestSupport.runOnFx(() -> {
            RXTimeView view = viewRef.get();
            view.getScene().getRoot().layout();
            assertAtColumnTop(view, "30");
        });
    }

    /**
     * Verifies the wheel multiplier scales how far a wheel scroll moves a column.
     *
     * @throws InterruptedException if a wait is interrupted
     */
    @Test
    public void wheelMultiplierScalesColumnScroll() throws InterruptedException {
        AtomicReference<RXTimeView> slowRef = new AtomicReference<>();
        AtomicReference<RXTimeView> fastRef = new AtomicReference<>();
        PickerTestSupport.runOnFx(() -> {
            RXTimeView slow = newView();
            RXTimeView fast = newView();
            assertEquals(RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER, slow.getWheelMultiplier());
            fast.setWheelMultiplier(4.0 * RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER);
            cell(slow, ".minute-column", "05").fireEvent(wheel(-40.0));
            cell(fast, ".minute-column", "05").fireEvent(wheel(-40.0));
            slowRef.set(slow);
            fastRef.set(fast);
        });
        waitForFx(300.0);
        PickerTestSupport.runOnFx(() -> {
            double slow = column(slowRef.get(), ".minute-column").getVvalue();
            double fast = column(fastRef.get(), ".minute-column").getVvalue();
            assertTrue(slow > 0.0, "the default multiplier scrolls");
            assertTrue(fast > 3.0 * slow, "four times the multiplier scrolls about four times as far");
        });
    }

    // ==================== Helpers ====================

    private static ScrollPane column(RXTimeView view, String selector) {
        return (ScrollPane) view.lookup(selector);
    }

    // The scroll to a pick is computed from the item height, so across many items pixel
    // snapping can leave the item up to a pixel off the column's top (its border inset).
    private static void assertAtColumnTop(RXTimeView view, String minute) {
        assertEquals(column(view, ".minute-column").snappedTopInset(), offsetFromColumnTop(view, minute), 1.5);
    }

    /** The minute cell's top relative to the top of its column; negative when scrolled above it. */
    private static double offsetFromColumnTop(RXTimeView view, String minute) {
        ScrollPane column = column(view, ".minute-column");
        Label cell = cell(view, ".minute-column", minute);
        return cell.localToScene(cell.getLayoutBounds()).getMinY()
                - column.localToScene(column.getLayoutBounds()).getMinY();
    }

    private static ScrollEvent wheel(double deltaY) {
        return scrollEvent(0.0, deltaY, false);
    }

    private static ScrollEvent scrollEvent(double deltaX, double deltaY, boolean direct) {
        return new ScrollEvent(ScrollEvent.SCROLL, 0.0, 0.0, 0.0, 0.0,
                false, false, false, false, direct, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0, null);
    }

    private static void waitForFx(double millis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> latch.countDown());
            pause.play();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX pulses");
        }
    }

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
