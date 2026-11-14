package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import io.github.leewyatt.rxcontrols.RXDateTimeRangeView;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.RXMonthView;
import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.RXYearView;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Showcase for the standalone date / time views that the pickers host in their popups:
 * {@link RXCalendarView}, {@link RXDateRangeView}, {@link RXTimeView},
 * {@link RXDateTimeView}, {@link RXMonthView}, {@link RXYearView},
 * {@link RXQuarterView} and {@link RXDateTimeRangeView}. Each view shows its value
 * and the number of completed selections (onAction); the control panel drives the
 * shared locale, week rules and time options.
 */
public class RXDateTimeViewsShowcase extends RXShowcaseApplication {

    private final RXCalendarView calendarView = new RXCalendarView();
    private final RXDateRangeView dateRangeView = new RXDateRangeView();
    private final RXTimeView timeView = new RXTimeView();
    private final RXDateTimeView dateTimeView = new RXDateTimeView();
    private final RXMonthView monthView = new RXMonthView();
    private final RXYearView yearView = new RXYearView();
    private final RXQuarterView quarterView = new RXQuarterView();
    private final RXDateTimeRangeView dateTimeRangeView = new RXDateTimeRangeView();
    private final List<RXTemporalViewBase<?>> views = List.of(calendarView, dateRangeView, timeView, dateTimeView,
            monthView, yearView, quarterView, dateTimeRangeView);

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "Date & Time Views";
    }

    @Override
    protected String subtitle() {
        return "Popup-independent selection views";
    }

    @Override
    protected String windowTitle() {
        return "RX Date / Time Views Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1280.0;
    }

    @Override
    protected double sceneHeight() {
        return 820.0;
    }

    @Override
    protected Node createPreview() {
        FlowPane flow = new FlowPane(24.0, 24.0);
        flow.getChildren().addAll(
                titled("RXCalendarView", calendarView),
                titled("RXTimeView", timeView),
                titled("RXMonthView", monthView),
                titled("RXYearView", yearView),
                titled("RXQuarterView", quarterView),
                titled("RXDateRangeView", dateRangeView),
                titled("RXDateTimeView", dateTimeView),
                titled("RXDateTimeRangeView", dateTimeRangeView));
        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private Node titled(String name, RXTemporalViewBase<?> view) {
        int[] actions = new int[1];
        Label value = new Label();
        Runnable refresh = () -> value.setText(view.getValue() + "   actions: " + actions[0]);
        view.valueProperty().addListener(observable -> refresh.run());
        view.addEventHandler(ActionEvent.ACTION, event -> {
            actions[0]++;
            refresh.run();
        });
        refresh.run();
        view.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        return new VBox(6.0, new Label(name), view, value);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("All views", buildGeneralGrid()),
                section("Calendars", buildCalendarGrid()),
                section("Time", buildTimeGrid()));
    }

    // ==================== Sections ====================

    private Node buildGeneralGrid() {
        ComboBox<Locale> locale = new ComboBox<>();
        locale.getItems().setAll(Locale.US, Locale.CHINA, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN);
        locale.setValue(Locale.US);
        locale.setMaxWidth(Double.MAX_VALUE);
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
        CheckBox disabled = new CheckBox("disabled");
        for (RXTemporalViewBase<?> view : views) {
            view.localeProperty().bind(locale.valueProperty());
            view.disableProperty().bind(disabled.selectedProperty());
        }
        return createGrid(
                row("Locale", locale),
                row(disabled));
    }

    private Node buildCalendarGrid() {
        ComboBox<String> firstDay = new ComboBox<>();
        firstDay.getItems().setAll("Locale", "Monday", "Sunday");
        firstDay.setValue("Locale");
        firstDay.setMaxWidth(Double.MAX_VALUE);
        CheckBox weekNumbers = new CheckBox("showWeekNumbers");
        CheckBox adjacent = new CheckBox("showAdjacentDays");
        adjacent.setSelected(true);
        Function<String, WeekFields> rules = choice -> "Monday".equals(choice) ? WeekFields.of(DayOfWeek.MONDAY, 4)
                : "Sunday".equals(choice) ? WeekFields.of(DayOfWeek.SUNDAY, 1) : null;
        List<ObjectProperty<WeekFields>> weekRules = List.of(calendarView.weekFieldsProperty(),
                dateRangeView.weekFieldsProperty(), dateTimeView.weekFieldsProperty(),
                dateTimeRangeView.weekFieldsProperty());
        List<BooleanProperty> weekNumberFlags = List.of(calendarView.showWeekNumbersProperty(),
                dateRangeView.showWeekNumbersProperty(), dateTimeView.showWeekNumbersProperty(),
                dateTimeRangeView.showWeekNumbersProperty());
        List<BooleanProperty> adjacentFlags = List.of(calendarView.showAdjacentDaysProperty(),
                dateRangeView.showAdjacentDaysProperty(), dateTimeView.showAdjacentDaysProperty(),
                dateTimeRangeView.showAdjacentDaysProperty());
        for (int i = 0; i < weekRules.size(); i++) {
            weekRules.get(i).bind(Bindings.createObjectBinding(() -> rules.apply(firstDay.getValue()),
                    firstDay.valueProperty()));
            weekNumberFlags.get(i).bind(weekNumbers.selectedProperty());
            adjacentFlags.get(i).bind(adjacent.selectedProperty());
        }
        return createGrid(
                row("First day", firstDay),
                row(weekNumbers),
                row(adjacent));
    }

    private Node buildTimeGrid() {
        CheckBox seconds = new CheckBox("showSeconds");
        CheckBox twelveHour = new CheckBox("twelveHourClock");
        List<BooleanProperty> secondFlags = List.of(timeView.showSecondsProperty(),
                dateTimeView.showSecondsProperty(), dateTimeRangeView.showSecondsProperty());
        List<BooleanProperty> twelveFlags = List.of(timeView.twelveHourClockProperty(),
                dateTimeView.twelveHourClockProperty(), dateTimeRangeView.twelveHourClockProperty());
        for (int i = 0; i < secondFlags.size(); i++) {
            secondFlags.get(i).bind(seconds.selectedProperty());
            twelveFlags.get(i).bind(twelveHour.selectedProperty());
        }
        return createGrid(
                row(seconds),
                row(twelveHour));
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
