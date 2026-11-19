package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Icons, captions, side panels, a small bar chart and the stylesheet shared by the date
 * and time pages. Each icon is a shape-backed region whose shape comes from its style
 * class in the stylesheet.
 */
final class DateTimeKit {

    static final String STYLESHEET =
            DateTimeKit.class.getResource("rx-datetime-samples.css").toExternalForm();

    static final DateTimeFormatter DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
    static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

    private static final PseudoClass IN_RANGE = PseudoClass.getPseudoClass("in-range");

    private DateTimeKit() {
    }

    static Region icon(String shape, String color) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", shape, color);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    static Label bound(String styleClass, Supplier<String> text, Observable... dependencies) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(Bindings.createStringBinding(text::get, dependencies));
        return label;
    }

    /**
     * A caption with an icon above a field, and optional lines below it.
     */
    static VBox field(String caption, Region icon, Region control, double width, Node... below) {
        control.setPrefWidth(width);
        control.setMaxWidth(Region.USE_PREF_SIZE);
        HBox title = new HBox(6.0, icon, text(caption, "body"));
        title.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6.0, title, control);
        box.getChildren().addAll(below);
        box.setFillWidth(false);
        return box;
    }

    static boolean isWeekday(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    static long workdays(LocalDate start, LocalDate end) {
        long count = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (isWeekday(day)) {
                count++;
            }
        }
        return count;
    }

    static String duration(Duration length) {
        long hours = length.toHours();
        long minutes = length.toMinutesPart();
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }

    /**
     * A bordered panel with a heading, for the content that sits beside an inline view.
     */
    static VBox panel(String heading, double width, Node... rows) {
        VBox panel = new VBox(10.0, text(heading, "panel-heading"));
        panel.getChildren().addAll(rows);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(width);
        panel.setMinWidth(Region.USE_PREF_SIZE);
        panel.setMaxHeight(Double.MAX_VALUE);
        return panel;
    }

    /**
     * A row with a name on the left and a value on the right.
     */
    static HBox pair(String name, Label value) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8.0, text(name, "hint"), spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * A converter that formats with a pattern in English and parses with the same
     * pattern; text it cannot parse throws, so the picker flags it and never commits it.
     */
    static <T extends TemporalAccessor> StringConverter<T> converter(String pattern, TemporalQuery<T> query) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : format.format(value);
            }

            @Override
            public T fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                return format.parse(text.trim(), query);
            }
        };
    }

    /**
     * A row of labeled bars whose highlighted bars follow a range.
     */
    static final class Bars extends HBox {

        private static final double MAX_BAR_HEIGHT = 84.0;

        private final List<Region> bars;

        Bars(String color, List<String> labels, double[] values) {
            super(6.0);
            getStyleClass().addAll("bars", color);
            setAlignment(Pos.BOTTOM_CENTER);
            double max = 0.0;
            for (double value : values) {
                max = Math.max(max, value);
            }
            Region[] made = new Region[labels.size()];
            for (int i = 0; i < made.length; i++) {
                Region bar = new Region();
                bar.getStyleClass().add("bar");
                bar.setPrefSize(22.0, MAX_BAR_HEIGHT * values[i] / max);
                bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                Label label = text(labels.get(i), "bar-label");
                VBox column = new VBox(4.0, bar, label);
                column.setAlignment(Pos.BOTTOM_CENTER);
                getChildren().add(column);
                made[i] = bar;
            }
            bars = List.of(made);
        }

        void mark(IntPredicate inRange) {
            for (int i = 0; i < bars.size(); i++) {
                bars.get(i).pseudoClassStateChanged(IN_RANGE, inRange.test(i));
            }
        }
    }
}
