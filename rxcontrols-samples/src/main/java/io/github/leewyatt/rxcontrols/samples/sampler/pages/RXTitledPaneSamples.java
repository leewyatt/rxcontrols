package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.area;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.pill;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;

/**
 * The RXTitledPane page: a settings section with an icon, a section whose header
 * switch turns the feature on without opening it, build output that only the arrow
 * opens, and an activity panel whose content is built the first time it opens.
 */
public final class RXTitledPaneSamples {

    private static final double AREA_WIDTH = 420.0;
    private static final double AREA_HEIGHT = 260.0;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private RXTitledPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A section with a title bar that shows or hides its content.",
                List.of("Group related settings or details under a title, closed when the reader does not need them.",
                        "For several sections where only one should be open use RXAccordion."),
                List.of(
                        SampleCard.large("Settings section",
                                "An open pane with an icon in the graphic slot and a form as its content.",
                                appearance()),
                        SampleCard.large("Switch in the header",
                                "headerTrailing holds a switch; a control consumes its own clicks, so turning it "
                                        + "on does not open or close the pane.",
                                backup()),
                        SampleCard.large("Open with the arrow",
                                "toggleTrigger INDICATOR: only the arrow (placed at the END) opens the pane, "
                                        + "so the title can be read and selected.",
                                buildLog()),
                        SampleCard.large("Built on first open",
                                "contentFactory creates the content the first time a pane opens (see the time in the "
                                        + "header), so closed panes cost nothing.",
                                activity())),
                NavKit.STYLESHEET);
    }

    // ==================== Settings section ====================

    private static Node appearance() {
        ToggleGroup theme = new ToggleGroup();
        HBox themes = new HBox(14.0);
        for (String name : List.of("Light", "Dark", "System")) {
            RXRadioButton radio = new RXRadioButton(name);
            radio.setToggleGroup(theme);
            themes.getChildren().add(radio);
        }
        theme.selectToggle(theme.getToggles().get(2));
        VBox content = new VBox(12.0,
                text("Theme", "muted"), themes,
                row("Compact lists", false),
                row("Show file extensions", true));
        RXTitledPane pane = new RXTitledPane("Appearance", content);
        pane.setGraphic(icon("palette-icon", "violet"));
        pane.setExpanded(true);
        pane.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, pane);
    }

    // ==================== Switch in the header ====================

    private static Node backup() {
        RXSwitchButton enabled = new RXSwitchButton();
        enabled.setSelected(true);
        Label state = text("", "muted");
        state.textProperty().bind(Bindings.when(enabled.selectedProperty())
                .then("Next backup tonight at 02:00").otherwise("Backups are off"));
        VBox content = new VBox(12.0,
                state,
                row("Include photos", true),
                row("Only on Wi-Fi", true),
                row("Keep 30 days of versions", false));
        content.disableProperty().bind(enabled.selectedProperty().not());
        RXTitledPane pane = new RXTitledPane("Automatic backup", content);
        pane.setGraphic(icon("cloud-icon", "sky"));
        pane.setHeaderTrailing(enabled);
        pane.setExpanded(true);
        pane.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, pane);
    }

    // ==================== Open with the arrow ====================

    private static Node buildLog() {
        VBox log = new VBox(3.0,
                text("> Task :compileJava", "code"),
                text("> Task :processResources", "code"),
                text("> Task :test  412 tests passed", "code"),
                text("> Task :jar", "code"),
                text("BUILD SUCCESSFUL in 38s", "code", "success"));
        RXTitledPane pane = new RXTitledPane("Build #1842 on main", log);
        pane.setGraphic(icon("check-icon", "lime"));
        pane.setHeaderTrailing(pill("Passed", "lime"));
        pane.setToggleTrigger(ToggleTrigger.INDICATOR);
        pane.setIndicatorPosition(IndicatorPosition.END);
        pane.setExpanded(true);

        RXTitledPane failed = new RXTitledPane("Build #1841 on feature/login", new VBox(3.0,
                text("> Task :test", "code"),
                text("LoginTest > rejectsEmptyPassword FAILED", "code", "failure")));
        failed.setGraphic(icon("close-icon", "rose"));
        failed.setHeaderTrailing(pill("Failed", "rose"));
        failed.setToggleTrigger(ToggleTrigger.INDICATOR);
        failed.setIndicatorPosition(IndicatorPosition.END);

        VBox box = new VBox(12.0, pane, failed);
        box.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, box);
    }

    // ==================== Built on first open ====================

    private static Node activity() {
        RXTitledPane week = lazy("Activity this week", "chart-icon", "orange", RXTitledPaneSamples::chart);
        RXTitledPane pages = lazy("Top pages", "file-icon", "sky", RXTitledPaneSamples::topPages);
        VBox box = new VBox(12.0, week, pages);
        box.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, box);
    }

    private static RXTitledPane lazy(String title, String iconClass, String color, Supplier<Node> content) {
        Label built = text("not built yet", "muted");
        RXTitledPane pane = new RXTitledPane(title);
        pane.setGraphic(icon(iconClass, color));
        pane.setHeaderTrailing(built);
        pane.setContentFactory(() -> {
            built.setText("built at " + LocalTime.now().format(CLOCK));
            return content.get();
        });
        return pane;
    }

    private static Node topPages() {
        VBox rows = new VBox(6.0);
        String[][] pages = {{"/pricing", "1,204"}, {"/blog/release-notes", "866"}, {"/docs/install", "512"}};
        for (String[] page : pages) {
            HBox row = new HBox(text(page[0], "copy"), spacer(), text(page[1], "muted"));
            rows.getChildren().add(row);
        }
        return rows;
    }

    private static Node chart() {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        int[] values = {42, 66, 54, 80, 71, 30, 22};
        HBox bars = new HBox(14.0);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        for (int i = 0; i < days.length; i++) {
            Region bar = new Region();
            bar.getStyleClass().addAll("bar", "orange");
            bar.setPrefSize(26.0, values[i] * 1.2);
            bar.setMaxHeight(Region.USE_PREF_SIZE);
            VBox column = new VBox(4.0, bar, text(days[i], "muted"));
            column.setAlignment(Pos.BOTTOM_CENTER);
            bars.getChildren().add(column);
        }
        return bars;
    }

    // ==================== Shared ====================

    private static Node row(String label, boolean on) {
        RXSwitchButton toggle = new RXSwitchButton();
        toggle.setSelected(on);
        HBox row = new HBox(10.0, text(label, "copy"), spacer(), toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
