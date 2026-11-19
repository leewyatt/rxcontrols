package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;

/**
 * The RXPlaceholder page: an empty inbox, an error with retry, a search list with a "no
 * results" placeholder, an empty task list, placeholders with only some slots and a
 * request-access screen.
 */
public final class RXPlaceholderSamples {

    private static final List<String> CITIES = List.of("Amsterdam", "Barcelona", "Copenhagen", "Dublin",
            "Edinburgh", "Florence", "Geneva", "Helsinki", "Istanbul", "Lisbon", "Madrid", "Oslo",
            "Prague", "Rome", "Stockholm", "Vienna");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private RXPlaceholderSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A centered graphic, title, description and actions for a view that has nothing to show.",
                List.of("Empty lists, no search results, missing access, a failed load: say what happened and what to do next.",
                        "To switch a region between content, empty, error and loading on its own, use RXStatePane."),
                List.of(
                        SampleCard.large("Empty inbox",
                                "Graphic, title, description and one action: the full set of slots.",
                                inbox()),
                        SampleCard.large("Load failed",
                                "A red graphic and two actions; Retry updates the description.",
                                failed()),
                        SampleCard.large("No search results",
                                "The placeholder sits in the placeholder slot of an RXListView and names the search text.",
                                search()),
                        SampleCard.large("Empty task list",
                                "The list starts empty; the placeholder's action fills it and Clear all brings the placeholder back.",
                                tasks()),
                        SampleCard.large("Only some slots",
                                "Empty slots collapse: a title alone, and a title with a description.",
                                partial()),
                        SampleCard.large("No access",
                                "The action changes the description after the request is sent.",
                                access())),
                FeedbackKit.STYLESHEET);
    }

    private static Node framed(Node content) {
        StackPane frame = new StackPane(content);
        frame.getStyleClass().add("panel");
        frame.setPrefSize(360.0, 250.0);
        frame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return frame;
    }

    private static Region graphic(String iconClass, String color) {
        Region graphic = icon(iconClass);
        graphic.getStyleClass().addAll("hero", color);
        return graphic;
    }

    // ==================== Empty inbox ====================

    private static Node inbox() {
        RXPlaceholder placeholder = new RXPlaceholder("You're all caught up", graphic("inbox-icon", "sky"));
        placeholder.setDescription("New messages will show up here.");
        RXButton compose = solid("Compose", "sky");
        compose.setGraphic(icon("note-icon"));
        placeholder.getActions().add(compose);
        return framed(placeholder);
    }

    // ==================== Load failed ====================

    private static Node failed() {
        RXPlaceholder placeholder = new RXPlaceholder("Couldn't load comments", graphic("cloud-off-icon", "rose"));
        placeholder.setDescription("Check your connection and try again.");
        RXButton retry = solid("Retry", "rose");
        retry.setOnAction(event -> placeholder.setDescription(
                "Still offline. Last try at " + LocalTime.now().format(TIME) + "."));
        placeholder.getActions().addAll(retry, plain("Report a problem"));
        return framed(placeholder);
    }

    // ==================== No search results ====================

    private static Node search() {
        FilteredList<String> filtered = new FilteredList<>(FXCollections.observableArrayList(CITIES));
        RXListView<String> list = new RXListView<>(filtered);
        list.setPrefSize(340.0, 200.0);

        RXPlaceholder placeholder = new RXPlaceholder("", graphic("search-icon", "amber"));
        RXButton clear = plain("Clear search");
        placeholder.getActions().add(clear);
        list.setPlaceholder(placeholder);

        RXTextField field = new RXTextField();
        field.setPromptText("Search cities");
        field.setLeading(icon("search-icon"));
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(city -> city.toLowerCase(Locale.ROOT).contains(query));
            placeholder.setTitle("No results for “" + newValue.trim() + "”");
            placeholder.setDescription("Check the spelling or try a shorter word.");
        });
        clear.setOnAction(event -> field.clear());
        field.setText("Paris");

        VBox box = new VBox(10.0, field, list);
        box.setPrefWidth(340.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Empty task list ====================

    private static Node tasks() {
        ObservableList<String> items = FXCollections.observableArrayList();
        RXListView<String> list = new RXListView<>(items);
        list.setPrefSize(340.0, 200.0);

        RXPlaceholder placeholder = new RXPlaceholder("No tasks yet", graphic("task-icon", "lime"));
        placeholder.setDescription("Tasks you add will be listed here.");
        // A button that is hidden or disabled while it holds focus hands the focus on by
        // traversal, and the page's scroll pane scrolls to wherever it lands. So each
        // button first moves the focus itself, to where the user goes on.
        RXButton add = solid("Add a task", "lime");
        add.setOnAction(event -> {
            list.requestFocus();
            items.addAll("Book the venue", "Send invitations", "Order the cake");
        });
        placeholder.getActions().add(add);
        list.setPlaceholder(placeholder);

        RXButton clearAll = plain("Clear all");
        clearAll.setOnAction(event -> {
            items.clear();
            add.requestFocus();
        });
        clearAll.disableProperty().bind(Bindings.isEmpty(items));
        VBox box = new VBox(10.0, list, clearAll);
        box.setPrefWidth(340.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Only some slots ====================

    private static Node partial() {
        RXPlaceholder titleOnly = new RXPlaceholder("No files yet");
        RXPlaceholder withText = new RXPlaceholder("No comments");
        withText.setDescription("Be the first to say something.");
        HBox row = new HBox(14.0, small(titleOnly), small(withText));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node small(Node content) {
        StackPane frame = new StackPane(content);
        frame.getStyleClass().add("panel");
        frame.setPrefSize(180.0, 130.0);
        frame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return frame;
    }

    // ==================== No access ====================

    private static Node access() {
        RXPlaceholder placeholder = new RXPlaceholder("You need access", graphic("lock-icon", "violet"));
        placeholder.setDescription("Ask the owner of “Q3 budget” to share it with you.");
        RXButton request = solid("Request access", "violet");
        request.setOnAction(event -> {
            placeholder.setDescription("Request sent. You'll get an email when the owner answers.");
            placeholder.requestFocus();
            request.setDisable(true);
        });
        placeholder.getActions().add(request);
        return framed(placeholder);
    }
}
