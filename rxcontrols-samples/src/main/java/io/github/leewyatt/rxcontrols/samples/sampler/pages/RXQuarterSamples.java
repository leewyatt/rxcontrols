package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXQuarterPicker;
import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * The RXQuarter page: quarter pickers for a report and with a custom text, and
 * RXQuarterView next to a quarter's goals and limited to the quarters with published
 * results.
 */
public final class RXQuarterSamples {

    private static final double FIELD_WIDTH = 200.0;

    private RXQuarterSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field for picking a quarter of a year from a popup of four quarters per year, "
                        + "and the quarters as a view.",
                List.of("Quarterly reports, goals, filings and earnings: anything counted per quarter.",
                        "For several quarters in a row use RXQuarterRangePicker; for months use RXMonthPicker."),
                List.of(
                        SampleCard.medium("Quarterly report",
                                "maxQuarter stops at the current quarter; the days it covers follow the value.",
                                report()).forType(RXQuarterPicker.class),
                        SampleCard.medium("Custom text",
                                "A converter writes and reads the Q4 2026 form instead of the default 2026-Q4.",
                                customText()).forType(RXQuarterPicker.class),
                        SampleCard.large("Team goals",
                                "An inline RXQuarterView; the goals below it follow the selected quarter.",
                                goals()).forType(RXQuarterView.class),
                        SampleCard.large("Published results",
                                "minQuarter and maxQuarter allow only the quarters whose results are out.",
                                results()).forType(RXQuarterView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static RXYearQuarter current() {
        return RXYearQuarter.now(Clock.systemDefaultZone());
    }

    private static Node report() {
        RXQuarterPicker picker = new RXQuarterPicker(current().minusQuarters(1));
        picker.setMaxQuarter(current());
        Label days = DateTimeKit.bound("hint", () -> picker.getValue() == null ? "No quarter"
                        : DateTimeKit.SHORT_DAY.format(picker.getValue().firstDay()) + " – "
                        + DateTimeKit.SHORT_DAY.format(picker.getValue().lastDay()),
                picker.valueProperty());
        return DateTimeKit.field("Report for", DateTimeKit.icon("chart-shape", "indigo"), picker, FIELD_WIDTH, days);
    }

    private static Node customText() {
        RXQuarterPicker picker = new RXQuarterPicker(current().plusQuarters(1));
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(RXYearQuarter value) {
                return value == null ? "" : "Q" + value.quarter() + " " + value.year();
            }

            @Override
            public RXYearQuarter fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                String[] parts = text.trim().split("\\s+");
                if (parts.length != 2 || !parts[0].toUpperCase(Locale.ROOT).startsWith("Q")) {
                    throw new IllegalArgumentException("Expected Q1 2026");
                }
                return RXYearQuarter.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0].substring(1)));
            }
        });
        return DateTimeKit.field("Tax filing", DateTimeKit.icon("doc-shape", "lime"), picker, FIELD_WIDTH);
    }

    private static Node goals() {
        RXQuarterView view = new RXQuarterView(current());
        view.getStyleClass().add("accent-violet");
        Label heading = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "Q" + view.getValue().quarter() + " goals", view.valueProperty());
        Label first = DateTimeKit.bound("agenda-item", () -> goal(view.getValue(), 0), view.valueProperty());
        Label second = DateTimeKit.bound("agenda-item", () -> goal(view.getValue(), 1), view.valueProperty());
        VBox panel = DateTimeKit.panel("Design team", 246.0, heading, first, second);
        return new VBox(12.0, view, panel);
    }

    private static Node results() {
        RXYearQuarter latest = current().minusQuarters(1);
        RXQuarterView view = new RXQuarterView(latest);
        view.getStyleClass().add("accent-orange");
        view.setMinQuarter(latest.minusQuarters(5));
        view.setMaxQuarter(latest);
        Label note = DateTimeKit.text("Results are published a few weeks after a quarter ends.", "hint");
        note.setWrapText(true);
        note.setMaxWidth(246.0);
        return new VBox(12.0, view, note);
    }

    private static String goal(RXYearQuarter quarter, int slot) {
        if (quarter == null) {
            return "";
        }
        String[][] goals = {
                {"•  Refresh the icon set", "•  Usability study for search"},
                {"•  Ship the dark theme", "•  Audit color contrast"},
                {"•  New onboarding flow", "•  Motion guidelines"},
                {"•  Design tokens 2.0", "•  Year-end review"}};
        return goals[quarter.quarter() - 1][slot];
    }
}
