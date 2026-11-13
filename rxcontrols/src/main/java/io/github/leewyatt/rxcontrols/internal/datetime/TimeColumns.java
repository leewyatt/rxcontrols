package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints.Unit;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Scrolling time columns (hour, minute, optional second, optional AM / PM) shared by
 * the time-based views. The selected item of each column scrolls to the top of its
 * column. Picking an item changes only that unit; the other units snap to the nearest
 * selectable time. Every column keeps a fixed set of item nodes, re-labeled and
 * shown or hidden on refresh, so no nodes are created after construction.
 */
public final class TimeColumns extends HBox {

    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass PSEUDO_FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass PSEUDO_ACTIVE = PseudoClass.getPseudoClass("active");
    private static final Duration SCROLL_DURATION = Duration.millis(160);
    private static final int HALF_DAY = 12;
    private static final int PAGE_ITEMS = 5;

    private enum Kind { HOUR, MINUTE, SECOND, MERIDIEM }

    private final Column hourColumn = new Column(Kind.HOUR, 24);
    private final Column minuteColumn = new Column(Kind.MINUTE, 60);
    private final Column secondColumn = new Column(Kind.SECOND, 60);
    private final Column meridiemColumn = new Column(Kind.MERIDIEM, 2);
    private final List<Column> visibleColumns = new ArrayList<>();

    private TimeConstraints constraints = new TimeConstraints(null, null, null, 1, 1, 1, false);
    private boolean twelveHour;
    private Locale locale;
    private LocalTime value;
    private Consumer<LocalTime> onChange = time -> { };
    private Runnable onCommit = () -> { };
    private int activeColumn;
    private boolean cursorVisible;

    /**
     * Creates the column set.
     */
    public TimeColumns() {
        getStyleClass().add("rx-time-columns");
        rebuildColumns();
    }

    // ==================== Configuration ====================

    /**
     * Applies new constraints and display options.
     *
     * @param constraints the constraints (also deciding whether seconds are shown)
     * @param twelveHour  whether to show a 12-hour clock with an AM / PM column
     * @param locale      the locale for the AM / PM texts, or {@code null}
     */
    public void configure(TimeConstraints constraints, boolean twelveHour, Locale locale) {
        boolean shapeChanged = constraints.isShowSeconds() != this.constraints.isShowSeconds()
                || twelveHour != this.twelveHour;
        this.constraints = constraints;
        this.twelveHour = twelveHour;
        this.locale = locale;
        if (shapeChanged) {
            rebuildColumns();
        }
        refresh(false);
    }

    /**
     * Sets the callback receiving a newly picked time.
     *
     * @param onChange the callback
     */
    public void setOnChange(Consumer<LocalTime> onChange) {
        this.onChange = onChange;
    }

    /**
     * Sets the callback run when the user confirms (Enter or a double click).
     *
     * @param onCommit the callback
     */
    public void setOnCommit(Runnable onCommit) {
        this.onCommit = onCommit;
    }

    /**
     * Shows a time as the selection.
     *
     * @param time    the time, or {@code null} for none
     * @param animate whether the columns scroll smoothly to the selection
     */
    public void setValue(LocalTime time, boolean animate) {
        this.value = time;
        refresh(animate);
    }

    /**
     * Returns the time a pick starts from: the selection, or the first selectable time.
     *
     * @return the base time
     */
    public LocalTime baseTime() {
        if (value != null) {
            return value;
        }
        LocalTime first = constraints.snap(LocalTime.MIDNIGHT, null);
        return first != null ? first : LocalTime.MIDNIGHT;
    }

    /**
     * Stops running scroll animations.
     */
    public void dispose() {
        for (Column column : List.of(hourColumn, minuteColumn, secondColumn, meridiemColumn)) {
            column.stopScroll();
        }
    }

    // ==================== Keyboard ====================

    /**
     * Handles a key: Left / Right choose the column, Up / Down / Page Up / Page Down /
     * Home / End move its selection, Enter / Space confirm. The first navigation key
     * only reveals the keyboard cursor.
     *
     * @param event the key event
     * @return {@code true} if handled
     */
    public boolean handleKey(KeyEvent event) {
        if (event.getEventType() != KeyEvent.KEY_PRESSED || event.isAltDown()
                || event.isControlDown() || event.isMetaDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
            if (!cursorVisible) {
                return false;
            }
            onCommit.run();
            return true;
        }
        if (!CalendarPanel.isNavigationKey(code)) {
            return false;
        }
        if (!cursorVisible) {
            cursorVisible = true;
            activeColumn = Math.min(activeColumn, visibleColumns.size() - 1);
            refresh(false);
            return true;
        }
        boolean rtl = getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        switch (code) {
            case LEFT:
            case RIGHT: {
                int direction = (code == KeyCode.RIGHT) != rtl ? 1 : -1;
                activeColumn = Math.max(0, Math.min(visibleColumns.size() - 1, activeColumn + direction));
                refresh(false);
                return true;
            }
            case UP:
                visibleColumns.get(activeColumn).moveSelection(-1);
                return true;
            case DOWN:
                visibleColumns.get(activeColumn).moveSelection(1);
                return true;
            case PAGE_UP:
                visibleColumns.get(activeColumn).moveSelection(-PAGE_ITEMS);
                return true;
            case PAGE_DOWN:
                visibleColumns.get(activeColumn).moveSelection(PAGE_ITEMS);
                return true;
            case HOME:
                visibleColumns.get(activeColumn).moveSelection(-Integer.MAX_VALUE / 2);
                return true;
            case END:
                visibleColumns.get(activeColumn).moveSelection(Integer.MAX_VALUE / 2);
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns whether the keyboard cursor is shown.
     *
     * @return {@code true} if shown
     */
    public boolean isKeyboardCursorVisible() {
        return cursorVisible;
    }

    /**
     * Hides the keyboard cursor.
     */
    public void clearKeyboardCursor() {
        if (cursorVisible) {
            cursorVisible = false;
            refresh(false);
        }
    }

    // ==================== Internals ====================

    private void rebuildColumns() {
        visibleColumns.clear();
        visibleColumns.add(hourColumn);
        visibleColumns.add(minuteColumn);
        if (constraints.isShowSeconds()) {
            visibleColumns.add(secondColumn);
        }
        if (twelveHour) {
            visibleColumns.add(meridiemColumn);
        }
        activeColumn = Math.min(activeColumn, visibleColumns.size() - 1);
        List<ScrollPane> panes = new ArrayList<>();
        for (Column column : visibleColumns) {
            panes.add(column.scroll);
        }
        getChildren().setAll(panes);
    }

    private void refresh(boolean animate) {
        for (int i = 0; i < visibleColumns.size(); i++) {
            Column column = visibleColumns.get(i);
            column.scroll.pseudoClassStateChanged(PSEUDO_ACTIVE, cursorVisible && i == activeColumn);
            column.update(cursorVisible && i == activeColumn);
            column.scrollToSelection(animate);
        }
    }

    private boolean isPm(LocalTime time) {
        return time.getHour() >= HALF_DAY;
    }

    /** Applies a picked unit value and reports the snapped time. */
    private void pick(Kind kind, int itemValue) {
        LocalTime base = baseTime();
        LocalTime wished;
        Unit fixed;
        switch (kind) {
            case HOUR:
                wished = base.withHour(twelveHour ? itemValue + (isPm(base) ? HALF_DAY : 0) : itemValue);
                fixed = Unit.HOUR;
                break;
            case MINUTE:
                wished = base.withMinute(itemValue);
                fixed = Unit.MINUTE;
                break;
            case SECOND:
                wished = base.withSecond(itemValue);
                fixed = Unit.SECOND;
                break;
            case MERIDIEM:
            default:
                wished = base.withHour(base.getHour() % HALF_DAY + itemValue * HALF_DAY);
                fixed = Unit.HOUR;
                break;
        }
        LocalTime snapped = constraints.snap(wished, fixed);
        if (snapped != null) {
            onChange.accept(snapped);
        }
    }

    // ==================== Column ====================

    private final class Column {

        private final Kind kind;
        private final ScrollPane scroll = new ScrollPane();
        private final VBox content = new VBox();
        private final List<Label> cells = new ArrayList<>();
        private final int[] itemValues;
        private final Region spacer = new Region();
        private int itemCount;
        private int selectedIndex = -1;
        private Timeline scrollAnimation;
        private boolean scrollPending;

        Column(Kind kind, int capacity) {
            this.kind = kind;
            this.itemValues = new int[capacity];
            scroll.getStyleClass().addAll("time-column", kind.name().toLowerCase(Locale.ROOT) + "-column");
            scroll.setFocusTraversable(false);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            content.getStyleClass().add("items");
            for (int i = 0; i < capacity; i++) {
                Label cell = new Label();
                cell.getStyleClass().add("time-cell");
                cell.setMaxWidth(Double.MAX_VALUE);
                int index = i;
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> onCellClicked(index, event));
                cells.add(cell);
            }
            content.getChildren().addAll(cells);
            content.getChildren().add(spacer);
            scroll.setContent(content);
            // The trailing spacer lets the last item scroll up to the top of the column.
            scroll.viewportBoundsProperty().addListener((obs, oldBounds, bounds) -> {
                double cellHeight = cells.get(0).prefHeight(-1);
                spacer.setPrefHeight(Math.max(0.0, bounds.getHeight() - cellHeight));
                if (scrollPending) {
                    scrollToSelection(false);
                }
            });
            content.heightProperty().addListener(observable -> {
                if (scrollPending) {
                    scrollToSelection(false);
                }
            });
        }

        private void onCellClicked(int index, MouseEvent event) {
            if (event.getButton() != MouseButton.PRIMARY || index >= itemCount || cells.get(index).isDisabled()) {
                return;
            }
            if (event.getClickCount() == 2) {
                onCommit.run();
                return;
            }
            activeColumn = visibleColumns.indexOf(this);
            pick(kind, itemValues[index]);
        }

        void update(boolean active) {
            LocalTime base = baseTime();
            itemCount = 0;
            selectedIndex = -1;
            switch (kind) {
                case HOUR:
                    fillHours(base);
                    break;
                case MINUTE:
                    for (int minute = 0; minute < 60; minute += constraints.step(Unit.MINUTE)) {
                        add(minute, twoDigits(minute), constraints.isMinuteAvailable(base.getHour(), minute),
                                value != null && value.getMinute() == minute);
                    }
                    break;
                case SECOND:
                    for (int second = 0; second < 60; second += constraints.step(Unit.SECOND)) {
                        add(second, twoDigits(second),
                                constraints.isSecondAvailable(base.getHour(), base.getMinute(), second),
                                value != null && value.getSecond() == second);
                    }
                    break;
                case MERIDIEM:
                default:
                    DateTimeFormatter marker = DateTimeFormatter.ofPattern("a", DateTimeTexts.locale(locale));
                    for (int half = 0; half < 2; half++) {
                        add(half, marker.format(LocalTime.of(half * HALF_DAY, 0)), isHalfAvailable(half),
                                value != null && (isPm(value) ? 1 : 0) == half);
                    }
                    break;
            }
            for (int i = 0; i < cells.size(); i++) {
                Label cell = cells.get(i);
                boolean shown = i < itemCount;
                cell.setVisible(shown);
                cell.setManaged(shown);
                cell.pseudoClassStateChanged(PSEUDO_SELECTED, shown && i == selectedIndex);
                cell.pseudoClassStateChanged(PSEUDO_FOCUSED, shown && active && i == selectedIndex);
            }
        }

        private void fillHours(LocalTime base) {
            int step = constraints.step(Unit.HOUR);
            if (!twelveHour) {
                for (int hour = 0; hour < 24; hour += step) {
                    add(hour, twoDigits(hour), constraints.isHourAvailable(hour),
                            value != null && value.getHour() == hour);
                }
                return;
            }
            int offset = isPm(base) ? HALF_DAY : 0;
            for (int hour = 0; hour < HALF_DAY; hour++) {
                int actual = hour + offset;
                if (actual % step == 0) {
                    add(hour, twoDigits(hour == 0 ? HALF_DAY : hour), constraints.isHourAvailable(actual),
                            value != null && value.getHour() == actual);
                }
            }
        }

        private boolean isHalfAvailable(int half) {
            for (int hour = half * HALF_DAY; hour < (half + 1) * HALF_DAY; hour++) {
                if (constraints.isHourAvailable(hour)) {
                    return true;
                }
            }
            return false;
        }

        private void add(int itemValue, String text, boolean available, boolean selected) {
            Label cell = cells.get(itemCount);
            itemValues[itemCount] = itemValue;
            cell.setText(text);
            cell.setDisable(!available);
            if (selected) {
                selectedIndex = itemCount;
            }
            itemCount++;
        }

        void moveSelection(int delta) {
            int start = selectedIndex >= 0 ? selectedIndex : (delta > 0 ? -1 : itemCount);
            int target = Math.max(0, Math.min(itemCount - 1, start + delta));
            int direction = delta > 0 ? 1 : -1;
            // Skip unavailable items, first in the move direction, then back.
            int index = target;
            while (index >= 0 && index < itemCount && cells.get(index).isDisabled()) {
                index += direction;
            }
            if (index < 0 || index >= itemCount) {
                index = target;
                while (index >= 0 && index < itemCount && cells.get(index).isDisabled()) {
                    index -= direction;
                }
            }
            if (index >= 0 && index < itemCount && index != selectedIndex) {
                pick(kind, itemValues[index]);
            }
        }

        void scrollToSelection(boolean animate) {
            double viewportHeight = scroll.getViewportBounds().getHeight();
            double cellHeight = cells.get(0).prefHeight(-1);
            if (selectedIndex < 0) {
                scrollPending = false;
                return;
            }
            if (viewportHeight <= 0.0 || cellHeight <= 0.0) {
                scrollPending = true;
                return;
            }
            scrollPending = false;
            // Computed from the uniform item height rather than from layout positions,
            // which lag one pulse behind a refresh that shows or hides items. The
            // trailing spacer is one viewport minus one item tall.
            double pitch = cellHeight + content.getSpacing();
            double insets = content.snappedTopInset() + content.snappedBottomInset();
            double range = insets + itemCount * pitch - cellHeight;
            double offset = content.snappedTopInset() + selectedIndex * pitch;
            // AM / PM both fit in the column; scrolling would hide the other half.
            double target = range <= 0.0 || kind == Kind.MERIDIEM ? 0.0 : Math.min(1.0, offset / range);
            stopScroll();
            if (!animate) {
                scroll.setVvalue(target);
                return;
            }
            scrollAnimation = new Timeline(new KeyFrame(SCROLL_DURATION,
                    new KeyValue(scroll.vvalueProperty(), target, Interpolator.EASE_OUT)));
            scrollAnimation.play();
        }

        void stopScroll() {
            if (scrollAnimation != null) {
                scrollAnimation.stop();
                scrollAnimation = null;
            }
        }
    }

    private static String twoDigits(int number) {
        return number < 10 ? "0" + number : Integer.toString(number);
    }
}
