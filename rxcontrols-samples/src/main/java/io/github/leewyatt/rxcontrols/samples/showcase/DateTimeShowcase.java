package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.SampleIcons;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Shared scaffold of the date / time picker and view showcases: a preview with a value
 * readout, and the control-panel rows and sections the family has in common.
 */
abstract class DateTimeShowcase extends RXShowcaseApplication {

    private static final Predicate<LocalDate> WEEKDAYS_ONLY = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
            && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    private static final Predicate<LocalTime> NO_NOON_HOUR = time -> time.getHour() != 12;
    private static final LocalTime OPENING = LocalTime.of(9, 0);
    private static final LocalTime CLOSING = LocalTime.of(18, 0);
    private static final int LIMIT_DAYS = 30;
    private static final int LIMIT_WEEKS = 8;
    private static final int DAYS_PER_WEEK = 7;
    private static final Predicate<LocalDate> NOT_FIRST_WEEK_OF_MONTH = week -> week.getDayOfMonth() > DAYS_PER_WEEK;
    private static final String WEEKDAYS_ONLY_TEXT = "dateFilter: weekdays only";
    private static final String NO_NOON_HOUR_TEXT = "timeFilter: not from 12:00 to 12:59";

    // ==================== Preview ====================

    /**
     * Places a picker at the top of the preview, above its committed value and the
     * number of commits (onAction).
     *
     * @param picker the showcased picker
     * @return the preview node
     */
    protected Node pickerPreview(RXPickerBase<?> picker) {
        VBox box = new VBox(16.0, picker, readout(picker.valueProperty(), picker, "commits"));
        box.setAlignment(Pos.TOP_CENTER);
        box.setFillWidth(false);
        return box;
    }

    /**
     * Places a view in the middle of the preview, above its value and the number of
     * completed selections (onAction).
     *
     * @param view the showcased view
     * @return the preview node
     */
    protected Node viewPreview(RXTemporalViewBase<?> view) {
        view.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(16.0, view, readout(view.valueProperty(), view, "actions"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Label readout(ObservableValue<?> value, Node source, String counterName) {
        int[] count = new int[1];
        Label label = new Label();
        Runnable refresh = () -> label.setText("value: " + value.getValue() + "   " + counterName + ": " + count[0]);
        value.addListener(observable -> refresh.run());
        source.addEventHandler(ActionEvent.ACTION, event -> {
            count[0]++;
            refresh.run();
        });
        refresh.run();
        return label;
    }

    // ==================== Rows ====================

    /**
     * A check box that mirrors a boolean property.
     *
     * @param text the check box text
     * @param flag the property to mirror
     * @return the row
     */
    protected Node[] flagRow(String text, BooleanProperty flag) {
        CheckBox box = new CheckBox(text);
        box.selectedProperty().bindBidirectional(flag);
        return row(box);
    }

    /**
     * A check box that sets a property to a value while selected and to null otherwise.
     *
     * @param text     the check box text
     * @param property the property to set
     * @param value    the value while selected
     * @param <V>      the property type
     * @return the row
     */
    protected <V> Node[] optionRow(String text, ObjectProperty<V> property, V value) {
        CheckBox box = new CheckBox(text);
        box.selectedProperty().addListener((obs, was, now) -> property.set(now ? value : null));
        return row(box);
    }

    /**
     * A slider that drives a whole-number property from 1 to a maximum.
     *
     * @param label    the row label
     * @param property the property to drive
     * @param max      the largest value
     * @return the row
     */
    protected Node[] intRow(String label, IntegerProperty property, int max) {
        Slider slider = createSlider(1.0, max, property.get());
        slider.setMajorTickUnit(1.0);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        property.bind(Bindings.createIntegerBinding(() -> (int) Math.round(slider.getValue()),
                slider.valueProperty()));
        return row(label, slider, createValueLabel(slider, "%.0f"));
    }

    /**
     * A text field that edits a string property.
     *
     * @param label the row label
     * @param text  the property to edit
     * @return the row
     */
    protected Node[] textRow(String label, StringProperty text) {
        TextField field = new TextField();
        field.textProperty().bindBidirectional(text);
        return row(label, field);
    }

    /**
     * A label that shows the current value of an observable.
     *
     * @param label the row label
     * @param value the observable to show
     * @return the row
     */
    protected Node[] readoutRow(String label, ObservableValue<?> value) {
        Label readout = new Label();
        readout.textProperty().bind(Bindings.convert(value));
        return row(label, readout);
    }

    private Node[] localeRow(ObjectProperty<Locale> locale) {
        ComboBox<Locale> box = new ComboBox<>();
        box.getItems().setAll(Locale.US, Locale.CHINA, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN);
        box.setValue(Locale.US);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale value) {
                return value == null ? "" : value.toLanguageTag();
            }

            @Override
            public Locale fromString(String text) {
                return Locale.forLanguageTag(text);
            }
        });
        locale.bind(box.valueProperty());
        return row("Locale", box);
    }

    private Node[] rightToLeftRow(Node control) {
        CheckBox box = new CheckBox("right-to-left");
        box.selectedProperty().addListener((obs, was, now) -> control.setNodeOrientation(
                now ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT));
        return row(box);
    }

    private Node[] firstDayRow(ObjectProperty<WeekFields> weekFields) {
        ComboBox<FirstDay> box = new ComboBox<>();
        box.getItems().setAll(FirstDay.values());
        box.setValue(FirstDay.LOCALE);
        box.setMaxWidth(Double.MAX_VALUE);
        box.valueProperty().addListener((obs, old, choice) -> weekFields.set(choice.rules));
        return row("First day", box);
    }

    // ==================== Sections ====================

    /**
     * The options every picker has: locale, editable, clearable, leading icon, disabled
     * and orientation, followed by the picker's own rows.
     *
     * @param picker    the showcased picker
     * @param extraRows rows specific to the picker
     * @return the section content
     */
    protected Node pickerGrid(RXPickerBase<?> picker, Node[]... extraRows) {
        return grid(List.of(
                localeRow(picker.localeProperty()),
                flagRow("editable", picker.editableProperty()),
                flagRow("clearable", picker.clearableProperty()),
                optionRow("leading icon", picker.leadingProperty(),
                        SampleIcons.icon(SampleIcons.BELL, 14.0, "derive(-fx-text-base-color, 45%)")),
                flagRow("disabled", picker.disableProperty()),
                rightToLeftRow(picker)), extraRows);
    }

    /**
     * The options every view has: locale, disabled, orientation and a button that
     * clears the value, followed by the view's own rows.
     *
     * @param view      the showcased view
     * @param extraRows rows specific to the view
     * @return the section content
     */
    protected Node viewGrid(RXTemporalViewBase<?> view, Node[]... extraRows) {
        Button clear = new Button("Clear value");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> view.setValue(null));
        return grid(List.of(
                localeRow(view.localeProperty()),
                flagRow("disabled", view.disableProperty()),
                rightToLeftRow(view),
                row(clear)), extraRows);
    }

    /**
     * The day-grid options: first day of the week, week numbers and adjacent days.
     *
     * @param weekFields       the week rules property
     * @param showWeekNumbers  the week-number flag
     * @param showAdjacentDays the adjacent-day flag
     * @param extraRows        rows specific to the control
     * @return the section content
     */
    protected Node calendarGrid(ObjectProperty<WeekFields> weekFields, BooleanProperty showWeekNumbers,
                                BooleanProperty showAdjacentDays, Node[]... extraRows) {
        return grid(List.of(
                firstDayRow(weekFields),
                flagRow("showWeekNumbers", showWeekNumbers),
                flagRow("showAdjacentDays", showAdjacentDays)), extraRows);
    }

    /**
     * The week options: first day of the week and week numbers.
     *
     * @param weekFields      the week rules property
     * @param showWeekNumbers the week-number flag
     * @return the section content
     */
    protected Node weekGrid(ObjectProperty<WeekFields> weekFields, BooleanProperty showWeekNumbers) {
        return createGrid(
                firstDayRow(weekFields),
                flagRow("showWeekNumbers", showWeekNumbers));
    }

    /**
     * The time-column options: seconds, 12-hour clock, the three steps and the wheel
     * multiplier.
     *
     * @param showSeconds     the seconds flag
     * @param twelveHourClock the 12-hour flag
     * @param hourStep        the hour step
     * @param minuteStep      the minute step
     * @param secondStep      the second step
     * @param wheelMultiplier the wheel multiplier
     * @param extraRows       rows specific to the control
     * @return the section content
     */
    protected Node timeGrid(BooleanProperty showSeconds, BooleanProperty twelveHourClock, IntegerProperty hourStep,
                            IntegerProperty minuteStep, IntegerProperty secondStep, DoubleProperty wheelMultiplier,
                            Node[]... extraRows) {
        Slider multiplier = createSlider(0.1, 2.0, wheelMultiplier.get());
        multiplier.valueProperty().bindBidirectional(wheelMultiplier);
        return grid(List.of(
                flagRow("showSeconds", showSeconds),
                flagRow("twelveHourClock", twelveHourClock),
                intRow("Hour step", hourStep, 6),
                intRow("Minute step", minuteStep, 30),
                intRow("Second step", secondStep, 30),
                row("Wheel multiplier", multiplier, createValueLabel(multiplier, "%.2fx"))), extraRows);
    }

    /**
     * Limits for a date: a minimum, a maximum and a filter.
     *
     * @param min    the earliest date
     * @param max    the latest date
     * @param filter the date filter
     * @return the section content
     */
    protected Node dateLimitGrid(ObjectProperty<LocalDate> min, ObjectProperty<LocalDate> max,
                                 ObjectProperty<Predicate<LocalDate>> filter) {
        LocalDate today = LocalDate.now();
        return createGrid(
                optionRow("minDate: today", min, today),
                optionRow("maxDate: today + " + LIMIT_DAYS + " days", max, today.plusDays(LIMIT_DAYS)),
                optionRow(WEEKDAYS_ONLY_TEXT, filter, WEEKDAYS_ONLY));
    }

    /**
     * Limits for a week: a minimum, a maximum and a filter.
     *
     * @param min    a day of the earliest week
     * @param max    a day of the latest week
     * @param filter the week filter
     * @return the section content
     */
    protected Node weekLimitGrid(ObjectProperty<LocalDate> min, ObjectProperty<LocalDate> max,
                                 ObjectProperty<Predicate<LocalDate>> filter) {
        LocalDate today = LocalDate.now();
        return createGrid(
                optionRow("minWeek: this week", min, today),
                optionRow("maxWeek: this week + " + LIMIT_WEEKS, max, today.plusWeeks(LIMIT_WEEKS)),
                optionRow("weekFilter: not the first week starting in a month", filter, NOT_FIRST_WEEK_OF_MONTH));
    }

    /**
     * Limits for a time: a minimum, a maximum and a filter.
     *
     * @param min    the earliest time
     * @param max    the latest time
     * @param filter the time filter
     * @return the section content
     */
    protected Node timeLimitGrid(ObjectProperty<LocalTime> min, ObjectProperty<LocalTime> max,
                                 ObjectProperty<Predicate<LocalTime>> filter) {
        return createGrid(
                optionRow("minTime: " + OPENING, min, OPENING),
                optionRow("maxTime: " + CLOSING, max, CLOSING),
                optionRow(NO_NOON_HOUR_TEXT, filter, NO_NOON_HOUR));
    }

    /**
     * Limits for a date-time: a minimum, a maximum, a date filter and a time filter.
     *
     * @param min        the earliest date-time
     * @param max        the latest date-time
     * @param dateFilter the date filter
     * @param timeFilter the time filter
     * @return the section content
     */
    protected Node dateTimeLimitGrid(ObjectProperty<LocalDateTime> min, ObjectProperty<LocalDateTime> max,
                                     ObjectProperty<Predicate<LocalDate>> dateFilter,
                                     ObjectProperty<Predicate<LocalTime>> timeFilter) {
        LocalDate today = LocalDate.now();
        return createGrid(
                optionRow("minDateTime: today " + OPENING, min, today.atTime(OPENING)),
                optionRow("maxDateTime: today + " + LIMIT_DAYS + " days " + CLOSING, max,
                        today.plusDays(LIMIT_DAYS).atTime(CLOSING)),
                optionRow(WEEKDAYS_ONLY_TEXT, dateFilter, WEEKDAYS_ONLY),
                optionRow(NO_NOON_HOUR_TEXT, timeFilter, NO_NOON_HOUR));
    }

    private Node grid(List<Node[]> commonRows, Node[][] extraRows) {
        List<Node[]> rows = new ArrayList<>(commonRows);
        rows.addAll(List.of(extraRows));
        return createGrid(rows.toArray(new Node[0][]));
    }

    // ==================== First day ====================

    private enum FirstDay {
        LOCALE("Locale", null),
        MONDAY("Monday", WeekFields.ISO),
        SUNDAY("Sunday", WeekFields.SUNDAY_START);

        private final String text;
        private final WeekFields rules;

        FirstDay(String text, WeekFields rules) {
            this.text = text;
            this.rules = rules;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
