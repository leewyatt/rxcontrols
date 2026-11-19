package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSelectionBox;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXSelectionBox page: a searchable time zone list, multiple labels with select-all
 * and clear buttons, grouped cities, ten thousand orders, a leading icon next to a
 * read-only box, and a popup footer with an action.
 */
public final class RXSelectionBoxSamples {

    private static final double BOX_WIDTH = 240.0;

    private static final List<String> TIME_ZONES = List.of(
            "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver",
            "America/Chicago", "America/New_York", "America/Sao_Paulo", "Atlantic/Azores",
            "Europe/London", "Europe/Paris", "Europe/Berlin", "Africa/Cairo", "Europe/Moscow",
            "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo",
            "Australia/Sydney", "Pacific/Auckland");

    private static final List<String> LABELS = List.of(
            "Bug", "Feature", "Design", "Docs", "Performance", "Security", "Good first issue", "Help wanted");

    private static final List<String[]> CITIES = List.of(
            new String[]{"Canada", "Montreal"}, new String[]{"Canada", "Toronto"}, new String[]{"Canada", "Vancouver"},
            new String[]{"China", "Beijing"}, new String[]{"China", "Shanghai"}, new String[]{"China", "Shenzhen"},
            new String[]{"Germany", "Berlin"}, new String[]{"Germany", "Hamburg"}, new String[]{"Germany", "Munich"},
            new String[]{"Japan", "Kyoto"}, new String[]{"Japan", "Osaka"}, new String[]{"Japan", "Tokyo"},
            new String[]{"Spain", "Barcelona"}, new String[]{"Spain", "Madrid"}, new String[]{"Spain", "Seville"},
            new String[]{"United States", "New York"}, new String[]{"United States", "San Francisco"},
            new String[]{"United States", "Seattle"});

    private RXSelectionBoxSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A drop-down that picks one or several items from a searchable, virtualized list.",
                List.of("Choosing from a list too long to show at once: time zones, labels, people, countries.",
                        "For a few options that fit on screen, radio buttons or a segmented control are quicker."),
                List.of(
                        SampleCard.medium("Searchable list",
                                "The search field in the popup filters the list as you type; it is on by default.",
                                timeZone()),
                        SampleCard.medium("Multiple choice",
                                "MULTIPLE puts a check box on every row; the popup offers Select All and Clear.",
                                labels()),
                        SampleCard.medium("Grouped items",
                                "sectionKeyFactory puts a header above each run of items with the same key.",
                                cities()),
                        SampleCard.medium("Ten thousand items",
                                "The popup list is virtualized, so even 10,000 orders open instantly.",
                                orders()),
                        SampleCard.medium("Icon and read-only",
                                "A graphic sits before the value; readOnly shows a value that cannot be changed.",
                                assignees()),
                        SampleCard.medium("Popup footer",
                                "popupFooter adds any node below the list, such as a link to manage the options.",
                                footer())),
                SelectionIcons.STYLESHEET);
    }

    private static Node timeZone() {
        RXSelectionBox<String> box = sized(new RXSelectionBox<>(FXCollections.observableArrayList(TIME_ZONES)));
        box.setPromptText("Choose a time zone");
        box.setSearchPromptText("Search time zones");
        box.getSelectionModel().select("Europe/Berlin");
        return withSummary(box, "Meetings are shown in ");
    }

    private static Node labels() {
        RXSelectionBox<String> box = sized(new RXSelectionBox<>(FXCollections.observableArrayList(LABELS)));
        box.setSelectionMode(SelectionMode.MULTIPLE);
        box.setPromptText("Add labels");
        box.setShowSelectAllButton(true);
        box.setShowClearButton(true);
        box.setSelectedItemsConverter(new StringConverter<>() {
            @Override
            public String toString(List<String> items) {
                return items == null || items.isEmpty() ? "" : String.join(", ", items);
            }

            @Override
            public List<String> fromString(String text) {
                return List.of();
            }
        });
        box.getSelectionModel().select("Bug");
        box.getSelectionModel().select("Performance");
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.size(box.getSelectionModel().getSelectedItems())
                .asString("%d labels on this issue"));
        return column(box, count);
    }

    private static Node cities() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (String[] city : CITIES) {
            items.add(city[1] + ", " + city[0]);
        }
        RXSelectionBox<String> box = sized(new RXSelectionBox<>(items));
        box.setPromptText("Pick an office");
        box.setSectionKeyFactory(item -> item.substring(item.indexOf(", ") + 2));
        box.getSelectionModel().select("New York, United States");
        return withSummary(box, "Office: ");
    }

    private static Node orders() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 1; i <= 10_000; i++) {
            items.add(String.format("Order #%05d", i));
        }
        RXSelectionBox<String> box = sized(new RXSelectionBox<>(items));
        box.setPromptText("Find an order");
        box.setSearchPromptText("Type an order number");
        box.setMaxVisibleRows(10);
        return withSummary(box, "Selected: ");
    }

    private static Node assignees() {
        List<String> people = List.of("Lee Wyatt", "Ada Brooks", "Sam Ortega", "Mia Chen");
        RXSelectionBox<String> owner = sized(new RXSelectionBox<>(FXCollections.observableArrayList(people)));
        owner.setGraphic(icon("person-icon"));
        owner.setPromptText("Assign to");
        owner.getSelectionModel().select("Lee Wyatt");
        RXSelectionBox<String> reporter = sized(new RXSelectionBox<>(FXCollections.observableArrayList(people)));
        reporter.setGraphic(icon("flag-icon"));
        reporter.getSelectionModel().select("Mia Chen");
        reporter.setReadOnly(true);
        return column(text("Assignee", "hint"), owner, text("Reporter (read-only)", "hint"), reporter);
    }

    private static Node footer() {
        RXSelectionBox<String> box = sized(new RXSelectionBox<>(FXCollections.observableArrayList(
                "Inbox", "Receipts", "Travel", "Newsletters", "Work")));
        box.setPromptText("Move to folder");
        box.setSearchable(false);
        box.setGraphic(icon("folder-icon"));
        RXButton manage = new RXButton("Manage folders…");
        manage.getStyleClass().add("flat");
        manage.setMaxWidth(Double.MAX_VALUE);
        manage.setAlignment(Pos.CENTER_LEFT);
        manage.setOnAction(event -> box.hide());
        box.setPopupFooter(manage);
        box.getSelectionModel().select("Receipts");
        return withSummary(box, "Folder: ");
    }

    private static RXSelectionBox<String> sized(RXSelectionBox<String> box) {
        box.setPrefWidth(BOX_WIDTH);
        box.setMaxWidth(BOX_WIDTH);
        return box;
    }

    private static Node withSummary(RXSelectionBox<String> box, String prefix) {
        Label summary = text("", "hint");
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> box.getSelectedItem() == null ? prefix + "nothing yet" : prefix + box.getSelectedItem(),
                box.getSelectionModel().selectedItemProperty()));
        return column(box, summary);
    }

    private static Node column(Node... nodes) {
        VBox column = new VBox(8.0, nodes);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
