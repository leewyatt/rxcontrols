package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMonthPicker;
import io.github.leewyatt.rxcontrols.RXMonthView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * The RXMonth page: month pickers for a card's expiry date and a billing month, and
 * RXMonthView as a monthly statement and as an archive with gaps.
 */
public final class RXMonthSamples {

    private static final double FIELD_WIDTH = 200.0;
    private static final Set<Month> NO_ISSUE = Set.of(Month.JANUARY, Month.JULY, Month.AUGUST);

    private RXMonthSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field for picking a month and year from a popup grid of months, and the grid as a view.",
                List.of("Card expiry dates, billing months, statements and monthly archives.",
                        "For a run of months use RXMonthRangePicker; for single days use RXDatePicker."),
                List.of(
                        SampleCard.medium("Card expiry",
                                "A converter with the MM/yy pattern and minMonth set to this month.",
                                cardExpiry()).forType(RXMonthPicker.class),
                        SampleCard.medium("Billing month",
                                "maxMonth stops at this month; the invoice period below follows the value.",
                                billingMonth()).forType(RXMonthPicker.class),
                        SampleCard.large("Monthly statement",
                                "An inline RXMonthView next to the figures of the selected month.",
                                statement()).forType(RXMonthView.class),
                        SampleCard.large("Newsletter archive",
                                "A monthFilter disables the months with no issue; maxMonth ends at this month.",
                                archive()).forType(RXMonthView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node cardExpiry() {
        YearMonth now = YearMonth.now();
        RXMonthPicker picker = new RXMonthPicker(now.plusYears(3).withMonth(8));
        picker.setPromptText("MM/YY");
        picker.setMinMonth(now);
        picker.setConverter(DateTimeKit.converter("MM/yy", YearMonth::from));
        return DateTimeKit.field("Expires", DateTimeKit.icon("card-shape", "violet"), picker, FIELD_WIDTH);
    }

    private static Node billingMonth() {
        YearMonth now = YearMonth.now();
        RXMonthPicker picker = new RXMonthPicker(now.minusMonths(1));
        picker.setMaxMonth(now);
        Label period = DateTimeKit.bound("hint", () -> picker.getValue() == null ? "No month"
                        : "Invoice period " + DateTimeKit.SHORT_DAY.format(picker.getValue().atDay(1)) + " – "
                        + DateTimeKit.SHORT_DAY.format(picker.getValue().atEndOfMonth()),
                picker.valueProperty());
        return DateTimeKit.field("Billing month", DateTimeKit.icon("doc-shape", "sky"), picker, FIELD_WIDTH, period);
    }

    private static Node statement() {
        YearMonth now = YearMonth.now();
        RXMonthView view = new RXMonthView(now.minusMonths(1));
        view.getStyleClass().add("accent-teal");
        view.setMaxMonth(now);
        Label spent = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "$" + String.format("%,d", spent(view.getValue())), view.valueProperty());
        Label count = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : String.valueOf(18 + view.getValue().getMonthValue() * 3 % 17), view.valueProperty());
        Label ready = DateTimeKit.text("Statement ready to download", "hint");
        ready.setWrapText(true);
        VBox panel = DateTimeKit.panel("Spent", 160.0, spent, DateTimeKit.pair("Payments", count), ready);
        return row(view, panel);
    }

    private static Node archive() {
        YearMonth now = YearMonth.now();
        YearMonth latest = now;
        while (NO_ISSUE.contains(latest.getMonth())) {
            latest = latest.minusMonths(1);
        }
        RXMonthView view = new RXMonthView(latest);
        view.getStyleClass().add("accent-rose");
        view.setMaxMonth(now);
        view.setMonthFilter(month -> !NO_ISSUE.contains(month.getMonth()));
        Label issue = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "Issue " + issueNumber(view.getValue()), view.valueProperty());
        Label note = DateTimeKit.text("No issues in January and over the summer.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Archive", 160.0, issue, note);
        return row(view, panel);
    }

    private static int issueNumber(YearMonth month) {
        int number = 0;
        for (YearMonth m = YearMonth.of(2019, 1); !m.isAfter(month); m = m.plusMonths(1)) {
            if (!NO_ISSUE.contains(m.getMonth())) {
                number++;
            }
        }
        return number;
    }

    private static int spent(YearMonth month) {
        return 1200 + (month.getMonthValue() * 397 + month.getYear() * 13) % 900;
    }

    private static Node row(Node view, Node panel) {
        HBox row = new HBox(16.0, view, panel);
        row.setAlignment(Pos.CENTER);
        return row;
    }
}
