package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXListView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Compact demo for collapsible sections in {@link RXListView}: a settings navigation
 * grouped by category with sticky, collapsible headers. Clicking a header (or using
 * Left / Right on it) collapses and expands its group. The search field filters by
 * page or category name; a search opens every group once when it starts, and
 * clearing it restores the groups the user had collapsed and the page that was open
 * — the list only offers the collapsed-key set, the search policy lives here.
 */
public class RXListViewCollapsibleDemo extends Application {

    private static final String NO_PAGE = "Pick a page";

    private record Entry(String category, String name) {
    }

    // The collapsed keys before the current search, or null when no search is active.
    private Set<Object> collapsedBeforeSearch;
    // The open page; kept while a search filters it out of the list.
    private Entry currentPage;

    @Override
    public void start(Stage primaryStage) {
        FilteredList<Entry> filtered = new FilteredList<>(entries());
        RXListView<Entry> list = new RXListView<>(filtered);
        list.setSectionKeyFactory(Entry::category);
        list.setCollapsibleSections(true);
        list.setPlaceholder(new Label("No matching settings"));
        list.setConverter(new StringConverter<>() {
            @Override
            public String toString(Entry entry) {
                return entry == null ? "" : entry.name();
            }

            // The list only renders text; it never parses it back.
            @Override
            public Entry fromString(String string) {
                return null;
            }
        });
        list.setSectionExpanded("Advanced", false);

        Label status = new Label(NO_PAGE);
        status.setPadding(new Insets(10, 14, 10, 14));
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, entry) -> {
            // A search that filters the open page out clears the selection; the page
            // itself stays open.
            if (entry != null) {
                currentPage = entry;
                status.setText(entry.category() + " › " + entry.name());
            }
        });

        TextField search = new TextField();
        search.setPromptText("Search settings");
        search.textProperty().addListener((obs, old, text) -> applySearch(list, filtered, text));

        VBox top = new VBox(8.0, createHeader(), search);
        top.setPadding(new Insets(16, 16, 10, 16));

        BorderPane root = new BorderPane(list);
        root.setTop(top);
        root.setBottom(status);

        primaryStage.setScene(new Scene(root, 380.0, 600.0));
        primaryStage.setTitle("RXListView Collapsible Sections Demo");
        primaryStage.show();
    }

    private void applySearch(RXListView<Entry> list, FilteredList<Entry> filtered, String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filtered.setPredicate(null);
            if (collapsedBeforeSearch != null) {
                list.getCollapsedSectionKeys().clear();
                list.getCollapsedSectionKeys().addAll(collapsedBeforeSearch);
                collapsedBeforeSearch = null;
            }
        } else {
            if (collapsedBeforeSearch == null) {
                // The search starts: remember the groups and open them all once. A group
                // the user collapses while searching stays collapsed until the search ends.
                collapsedBeforeSearch = new HashSet<>(list.getCollapsedSectionKeys());
                list.expandAllSections();
            }
            filtered.setPredicate(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.category().toLowerCase(Locale.ROOT).contains(query));
        }
        if (currentPage != null && list.getSelectionModel().getSelectedItem() == null
                && filtered.contains(currentPage)) {
            list.getSelectionModel().select(currentPage);
        }
    }

    private static ObservableList<Entry> entries() {
        ObservableList<Entry> entries = FXCollections.observableArrayList();
        add(entries, "General", "About", "Software update", "Storage", "Date & time", "Language & region");
        add(entries, "Appearance", "Theme", "Accent color", "Font size", "Wallpaper");
        add(entries, "Network", "Wi-Fi", "Ethernet", "VPN", "Proxy", "Firewall");
        add(entries, "Devices", "Displays", "Keyboard", "Mouse", "Trackpad", "Printers", "Sound");
        add(entries, "Accounts", "Users", "Sign-in options", "Family", "Email & accounts");
        add(entries, "Privacy", "Location", "Camera", "Microphone", "Notifications", "Diagnostics");
        add(entries, "Apps", "Default apps", "Startup", "Installed apps", "Optional features");
        add(entries, "Advanced", "Developer mode", "Logs", "Experimental features", "Reset");
        return entries;
    }

    private static void add(ObservableList<Entry> entries, String category, String... names) {
        for (String name : names) {
            entries.add(new Entry(category, name));
        }
    }

    private VBox createHeader() {
        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Click a category to collapse it");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        return new VBox(2.0, title, subtitle);
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
