package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDatePicker;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXMonthPicker;
import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXQuarterPicker;
import io.github.leewyatt.rxcontrols.RXTimePicker;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.RXYearPicker;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.event.ActionEvent;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Showcase for the eight date / time pickers: {@link RXDatePicker},
 * {@link RXDateRangePicker}, {@link RXTimePicker}, {@link RXDateTimePicker},
 * {@link RXMonthPicker}, {@link RXYearPicker}, {@link RXQuarterPicker} and
 * {@link RXDateTimeRangePicker}, laid out as a form with their committed values and
 * commit (onAction) counts. The control panel drives the shared picker properties
 * (locale, editable, clearable, disabled, right-to-left) and the calendar, time and
 * footer options of the pickers that have them.
 */
public class RXDateTimePickersShowcase extends RXShowcaseApplication {

    private final RXDatePicker datePicker = new RXDatePicker();
    private final RXDateRangePicker dateRangePicker = new RXDateRangePicker();
    private final RXTimePicker timePicker = new RXTimePicker();
    private final RXDateTimePicker dateTimePicker = new RXDateTimePicker();
    private final RXMonthPicker monthPicker = new RXMonthPicker();
    private final RXYearPicker yearPicker = new RXYearPicker();
    private final RXQuarterPicker quarterPicker = new RXQuarterPicker();
    private final RXDateTimeRangePicker dateTimeRangePicker = new RXDateTimeRangePicker();
    private final List<RXPickerBase<?>> pickers = List.of(datePicker, dateRangePicker, timePicker, dateTimePicker,
            monthPicker, yearPicker, quarterPicker, dateTimeRangePicker);

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "Date & Time Pickers";
    }

    @Override
    protected String subtitle() {
        return "Date, range, time, date-time, month, year and quarter pickers";
    }

    @Override
    protected String windowTitle() {
        return "RX Date / Time Pickers Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected Node createPreview() {
        datePicker.setPromptText("Select date");
        timePicker.setPromptText("Select time");
        dateTimePicker.setPromptText("Select date and time");
        monthPicker.setPromptText("Select month");
        yearPicker.setPromptText("Select year");
        quarterPicker.setPromptText("Select quarter");

        GridPane form = new GridPane();
        form.setHgap(14.0);
        form.setVgap(14.0);
        addRow(form, "RXDatePicker", datePicker);
        addRow(form, "RXDateRangePicker", dateRangePicker);
        addRow(form, "RXTimePicker", timePicker);
        addRow(form, "RXDateTimePicker", dateTimePicker);
        addRow(form, "RXMonthPicker", monthPicker);
        addRow(form, "RXYearPicker", yearPicker);
        addRow(form, "RXQuarterPicker", quarterPicker);
        addRow(form, "RXDateTimeRangePicker", dateTimeRangePicker);
        return form;
    }

    private void addRow(GridPane form, String name, RXPickerBase<?> picker) {
        int row = form.getRowCount();
        int[] commits = new int[1];
        Label value = new Label();
        Runnable refresh = () -> value.setText("value: " + picker.getValue() + "   commits: " + commits[0]);
        picker.valueProperty().addListener(observable -> refresh.run());
        picker.addEventHandler(ActionEvent.ACTION, event -> {
            commits[0]++;
            refresh.run();
        });
        refresh.run();
        form.addRow(row, new Label(name), picker, value);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("All pickers", buildGeneralGrid()),
                section("Calendar", buildCalendarGrid()),
                section("Time", buildTimeGrid()),
                section("Footer", buildFooterGrid()));
    }

    // ==================== Sections ====================

    private Node buildGeneralGrid() {
        ComboBox<Locale> locale = new ComboBox<>();
        locale.getItems().setAll(Locale.US, Locale.CHINA, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN);
        locale.setMaxWidth(Double.MAX_VALUE);
        locale.setValue(Locale.US);
        locale.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale value) {
                return value == null ? "" : value.toLanguageTag();
            }

            @Override
            public Locale fromString(String text) {
                return Locale.forLanguageTag(text);
            }
        });
        CheckBox editable = new CheckBox("editable");
        editable.setSelected(true);
        CheckBox clearable = new CheckBox("clearable");
        CheckBox disabled = new CheckBox("disabled");
        CheckBox rtl = new CheckBox("right-to-left");
        for (RXPickerBase<?> picker : pickers) {
            picker.localeProperty().bind(locale.valueProperty());
            picker.editableProperty().bind(editable.selectedProperty());
            picker.clearableProperty().bind(clearable.selectedProperty());
            picker.disableProperty().bind(disabled.selectedProperty());
            picker.nodeOrientationProperty().bind(Bindings.when(rtl.selectedProperty())
                    .then(NodeOrientation.RIGHT_TO_LEFT).otherwise(NodeOrientation.LEFT_TO_RIGHT));
        }
        return createGrid(
                row("Locale", locale),
                row(editable),
                row(clearable),
                row(disabled),
                row(rtl));
    }

    private Node buildCalendarGrid() {
        ComboBox<String> firstDay = new ComboBox<>();
        firstDay.getItems().setAll("Locale", "Monday", "Sunday");
        firstDay.setValue("Locale");
        firstDay.setMaxWidth(Double.MAX_VALUE);
        CheckBox weekNumbers = new CheckBox("showWeekNumbers");
        CheckBox adjacent = new CheckBox("showAdjacentDays");
        adjacent.setSelected(true);
        CheckBox noWeekends = new CheckBox("dateFilter: no weekends");
        CheckBox futureOnly = new CheckBox("min: today");

        List<ObjectProperty<WeekFields>> weekRules = List.of(datePicker.weekFieldsProperty(),
                dateRangePicker.weekFieldsProperty(), dateTimePicker.weekFieldsProperty(),
                dateTimeRangePicker.weekFieldsProperty());
        List<BooleanProperty> weekNumberFlags = List.of(datePicker.showWeekNumbersProperty(),
                dateRangePicker.showWeekNumbersProperty(), dateTimePicker.showWeekNumbersProperty(),
                dateTimeRangePicker.showWeekNumbersProperty());
        List<BooleanProperty> adjacentFlags = List.of(datePicker.showAdjacentDaysProperty(),
                dateRangePicker.showAdjacentDaysProperty(), dateTimePicker.showAdjacentDaysProperty(),
                dateTimeRangePicker.showAdjacentDaysProperty());
        List<ObjectProperty<Predicate<LocalDate>>> dateFilters = List.of(datePicker.dateFilterProperty(),
                dateRangePicker.dateFilterProperty(), dateTimePicker.dateFilterProperty(),
                dateTimeRangePicker.dateFilterProperty());
        Function<String, WeekFields> rules = choice -> "Monday".equals(choice) ? WeekFields.of(DayOfWeek.MONDAY, 4)
                : "Sunday".equals(choice) ? WeekFields.of(DayOfWeek.SUNDAY, 1) : null;
        Predicate<LocalDate> weekdays = date -> date.getDayOfWeek().getValue() < DayOfWeek.SATURDAY.getValue();
        for (int i = 0; i < weekRules.size(); i++) {
            weekRules.get(i).bind(Bindings.createObjectBinding(() -> rules.apply(firstDay.getValue()),
                    firstDay.valueProperty()));
            weekNumberFlags.get(i).bind(weekNumbers.selectedProperty());
            adjacentFlags.get(i).bind(adjacent.selectedProperty());
            dateFilters.get(i).bind(Bindings.when(noWeekends.selectedProperty())
                    .<Predicate<LocalDate>>then(weekdays).otherwise((Predicate<LocalDate>) null));
        }
        futureOnly.selectedProperty().addListener((obs, was, now) -> {
            LocalDate today = now ? LocalDate.now() : null;
            datePicker.setMinDate(today);
            dateRangePicker.setMinDate(today);
            dateTimePicker.setMinDateTime(today == null ? null : today.atStartOfDay());
            dateTimeRangePicker.setMinDateTime(today == null ? null : today.atStartOfDay());
            monthPicker.setMinMonth(today == null ? null : YearMonth.from(today));
            yearPicker.setMinYear(today == null ? null : Year.from(today));
            quarterPicker.setMinQuarter(today == null ? null : RXYearQuarter.from(today));
        });
        return createGrid(
                row("First day", firstDay),
                row(weekNumbers),
                row(adjacent),
                row(noWeekends),
                row(futureOnly));
    }

    private Node buildTimeGrid() {
        CheckBox seconds = new CheckBox("showSeconds");
        CheckBox twelveHour = new CheckBox("twelveHourClock");
        CheckBox officeHours = new CheckBox("timeFilter: 09:00 - 18:00");
        Slider minuteStep = createSlider(1.0, 30.0, 1.0);
        minuteStep.setMajorTickUnit(5.0);
        minuteStep.setSnapToTicks(false);
        Label minuteValue = createValueLabel(minuteStep, "%.0f");

        List<BooleanProperty> secondFlags = List.of(timePicker.showSecondsProperty(),
                dateTimePicker.showSecondsProperty(), dateTimeRangePicker.showSecondsProperty());
        List<BooleanProperty> twelveFlags = List.of(timePicker.twelveHourClockProperty(),
                dateTimePicker.twelveHourClockProperty(), dateTimeRangePicker.twelveHourClockProperty());
        List<IntegerProperty> minuteSteps = List.of(timePicker.minuteStepProperty(),
                dateTimePicker.minuteStepProperty(), dateTimeRangePicker.minuteStepProperty());
        List<ObjectProperty<Predicate<LocalTime>>> timeFilters = List.of(timePicker.timeFilterProperty(),
                dateTimePicker.timeFilterProperty(), dateTimeRangePicker.timeFilterProperty());
        Predicate<LocalTime> office = time -> !time.isBefore(LocalTime.of(9, 0)) && !time.isAfter(LocalTime.of(18, 0));
        for (int i = 0; i < secondFlags.size(); i++) {
            secondFlags.get(i).bind(seconds.selectedProperty());
            twelveFlags.get(i).bind(twelveHour.selectedProperty());
            minuteSteps.get(i).bind(Bindings.createIntegerBinding(() -> (int) Math.round(minuteStep.getValue()),
                    minuteStep.valueProperty()));
            timeFilters.get(i).bind(Bindings.when(officeHours.selectedProperty())
                    .<Predicate<LocalTime>>then(office).otherwise((Predicate<LocalTime>) null));
        }
        return createGrid(
                row(seconds),
                row(twelveHour),
                row(officeHours),
                row("Minute step", minuteStep, minuteValue));
    }

    private Node buildFooterGrid() {
        CheckBox showToday = new CheckBox("showToday (date)");
        showToday.selectedProperty().bindBidirectional(datePicker.showTodayProperty());
        CheckBox showNow = new CheckBox("showNow (time, date-time)");
        showNow.setSelected(true);
        List<BooleanProperty> nowFlags = List.of(timePicker.showNowProperty(), dateTimePicker.showNowProperty());
        for (BooleanProperty flag : nowFlags) {
            flag.bind(showNow.selectedProperty());
        }
        return createGrid(
                row(showToday),
                row(showNow));
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
