package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.COMPACT_PROFILE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.breakpointBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.columnGuide;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;

/**
 * The RXRow / RXCol page: a dashboard and a sign-up form that reflow inside a
 * resizable frame, a panel centered with an offset, gutters, justify and align.
 */
public final class RXRowSamples {

    private static final int COLUMNS = 24;
    private static final double GRID_WIDTH = 400.0;

    private RXRowSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A 24-column responsive grid: RXRow wraps its RXCol cells into lines, and each cell picks its span, offset, order and visibility per breakpoint of the row's own width.",
                List.of("Dashboards, forms and page sections that must rearrange between narrow and wide windows.",
                        "For a single row or column use RXBox; for same-size tiles use RXTilePane."),
                List.of(
                        SampleCard.canvas("Responsive dashboard",
                                "Drag the frame edge: the stat cards go from one to two to four per line, the alerts move first on narrow widths (order) and the shortcuts bar shows only from MD (hidden).",
                                dashboard()),
                        SampleCard.canvas("Sign-up form",
                                "Fields pair up from SM and the address shares a line from MD; the button takes a full line on narrow widths and an offset of 16 columns on wide ones.",
                                signUp()),
                        SampleCard.large("Centered with an offset",
                                "span 12 with offset 6 centers a panel on the 24 columns shown behind it.",
                                centered()),
                        SampleCard.large("Gutters",
                                "gutter sets the space between columns and rowGap the space between wrapped lines; both follow the picker here.",
                                gutters()),
                        SampleCard.large("Justify",
                                "justify spreads the columns a line leaves unused: at the start, centered, at the end or between the cells.",
                                justify()),
                        SampleCard.large("Align",
                                "align places cells of different heights at the top, the middle or the bottom of their line.",
                                align())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Responsive dashboard ====================

    private static Node dashboard() {
        RXRow row = new RXRow();
        row.setBreakpointProfile(COMPACT_PROFILE);
        row.setGutter(12.0);
        row.setRowGap(12.0);

        row.getChildren().addAll(
                stat("Revenue", "$48.2K", "+12%", "teal"),
                stat("Orders", "1,284", "+4%", "indigo"),
                stat("Visitors", "32.9K", "+18%", "sky"),
                stat("Refunds", "0.8%", "-2%", "rose"));

        RXCol chart = new RXCol(chart());
        chart.setXs(RXColSpec.of(24));
        chart.setMd(RXColSpec.of(16));

        RXCol alerts = new RXCol(alerts());
        alerts.setXs(RXColSpec.builder().span(24).order(-1).build());
        alerts.setMd(RXColSpec.builder().span(8).order(0).build());

        RXCol shortcuts = new RXCol(shortcuts());
        shortcuts.setXs(RXColSpec.builder().span(24).hidden(true).build());
        shortcuts.setMd(RXColSpec.builder().hidden(false).build());

        row.getChildren().addAll(chart, alerts, shortcuts);
        return widthFrame(row, 300.0, breakpointBadge(row.activeBreakpointProperty()));
    }

    private static RXCol stat(String name, String value, String change, String color) {
        Region dot = new Region();
        dot.getStyleClass().addAll("stat-dot", color);
        dot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox top = new HBox(8.0, dot, text(name, "muted"), spacer(), text(change, "trend",
                change.startsWith("-") ? "down" : "up"));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6.0, top, text(value, "stat-value"));
        box.getStyleClass().add("tile");

        RXCol col = new RXCol(box);
        col.setXs(RXColSpec.of(24));
        col.setSm(RXColSpec.of(12));
        col.setLg(RXColSpec.of(6));
        return col;
    }

    private static Node chart() {
        double[] values = {0.45, 0.62, 0.38, 0.8, 0.7, 0.92, 0.58};
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        HBox bars = new HBox(10.0);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        for (int i = 0; i < values.length; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("bar");
            bar.setPrefHeight(110.0 * values[i]);
            bar.setMaxSize(Double.MAX_VALUE, Region.USE_PREF_SIZE);
            VBox column = new VBox(6.0, bar, text(days[i], "muted"));
            column.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(column, Priority.ALWAYS);
            column.setPrefWidth(0.0);
            bars.getChildren().add(column);
        }
        VBox box = new VBox(12.0, text("Sales this week", "copy-strong"), bars);
        box.getStyleClass().add("tile");
        return box;
    }

    private static Node alerts() {
        VBox box = new VBox(10.0, text("Alerts", "copy-strong"),
                alert("Payment service is slow", "amber"),
                alert("3 orders need review", "rose"),
                alert("Backup finished", "lime"));
        box.getStyleClass().add("tile");
        return box;
    }

    private static Node alert(String message, String color) {
        Region dot = new Region();
        dot.getStyleClass().addAll("stat-dot", color);
        dot.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox row = new HBox(8.0, dot, wrapped(message, "copy"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node shortcuts() {
        HBox box = new HBox(10.0, icon("bolt-icon", "violet"), text("Shortcuts", "copy-strong"), spacer(),
                plain("New invoice"), plain("Export CSV"), solid("Invite team", "violet"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("tile");
        return box;
    }

    // ==================== Sign-up form ====================

    private static Node signUp() {
        RXRow row = new RXRow();
        row.setBreakpointProfile(COMPACT_PROFILE);
        row.setGutter(14.0);
        row.setRowGap(12.0);

        row.getChildren().addAll(
                field("First name", "Lee", 24, 12, 12),
                field("Last name", "Wyatt", 24, 12, 12),
                field("Email", "lee@example.com", 24, 24, 24),
                field("City", "Lisbon", 24, 24, 12),
                field("Region", "Lisboa", 12, 12, 6),
                field("Postal code", "1100-148", 12, 12, 6));

        RXButton create = solid("Create account", "indigo");
        create.setMaxWidth(Double.MAX_VALUE);
        RXCol submit = new RXCol(create);
        submit.setXs(RXColSpec.of(24));
        submit.setMd(RXColSpec.of(8, 16));
        row.getChildren().add(submit);

        return widthFrame(row, 300.0, breakpointBadge(row.activeBreakpointProperty()));
    }

    private static RXCol field(String name, String prompt, int xs, int sm, int md) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        VBox box = new VBox(5.0, text(name, "field-label"), input);
        RXCol col = new RXCol(box);
        col.setXs(RXColSpec.of(xs));
        col.setSm(RXColSpec.of(sm));
        col.setMd(RXColSpec.of(md));
        return col;
    }

    // ==================== Offset ====================

    private static Node centered() {
        RXRow row = new RXRow();
        row.setGutter(8.0);
        VBox panel = new VBox(8.0, icon("inbox-icon", "sky", "large"), text("All caught up", "headline"),
                wrapped("New messages will show up here.", "muted"));
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("tile");
        RXCol col = new RXCol(panel);
        col.setSpan(12);
        col.setOffset(6);
        row.getChildren().add(col);
        return guided(row);
    }

    // ==================== Gutters ====================

    private static Node gutters() {
        RXRow row = new RXRow();
        String[] colors = {"rose", "orange", "amber", "lime", "teal", "sky"};
        for (String color : colors) {
            Region block = new Region();
            block.getStyleClass().addAll("block", color);
            block.setPrefHeight(44.0);
            RXCol col = new RXCol(block);
            col.setSpan(8);
            row.getChildren().add(col);
        }
        RXSegmentedControl<Integer> picker = picker(gap -> {
            row.setGutter(gap);
            row.setRowGap(gap);
        }, 0, 8, 16, 24);
        picker.setValue(16);
        StackPane grid = new StackPane(row);
        grid.getStyleClass().add("grid-area");
        grid.setPrefWidth(GRID_WIDTH);
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return withPicker(picker, grid);
    }

    // ==================== Justify ====================

    private static Node justify() {
        RXRow row = new RXRow();
        String[] names = {"Draft", "Review", "Done"};
        String[] colors = {"amber", "sky", "lime"};
        for (int i = 0; i < names.length; i++) {
            Label cell = text(names[i], "cell", colors[i]);
            cell.setMaxWidth(Double.MAX_VALUE);
            RXCol col = new RXCol(cell);
            col.setSpan(5);
            row.getChildren().add(col);
        }
        row.setGutter(8.0);
        RXSegmentedControl<RXRow.Justify> picker = picker(row::setJustify,
                RXRow.Justify.START, RXRow.Justify.CENTER, RXRow.Justify.END, RXRow.Justify.SPACE_BETWEEN);
        return withPicker(picker, guided(row));
    }

    // ==================== Align ====================

    private static Node align() {
        RXRow row = new RXRow();
        row.setGutter(8.0);
        double[] heights = {50.0, 96.0, 70.0};
        String[] colors = {"indigo", "violet", "pink"};
        for (int i = 0; i < heights.length; i++) {
            Region block = new Region();
            block.getStyleClass().addAll("block", colors[i]);
            block.setPrefHeight(heights[i]);
            block.setMaxHeight(Region.USE_PREF_SIZE);
            RXCol col = new RXCol(block);
            col.setSpan(8);
            row.getChildren().add(col);
        }
        RXSegmentedControl<RXRow.Align> picker = picker(row::setAlign,
                RXRow.Align.TOP, RXRow.Align.CENTER, RXRow.Align.BOTTOM);
        return withPicker(picker, guided(row));
    }

    // ==================== Helpers ====================

    private static Node guided(RXRow row) {
        StackPane grid = new StackPane(columnGuide(row.gutterProperty(), COLUMNS), row);
        grid.getStyleClass().add("grid-area");
        grid.setPrefWidth(GRID_WIDTH);
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node withPicker(Node picker, Node grid) {
        VBox box = new VBox(14.0, picker, grid);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }
}
