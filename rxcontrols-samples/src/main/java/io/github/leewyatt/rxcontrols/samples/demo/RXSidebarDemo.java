package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal {@link RXSidebar} demo: a documentation-style navigation with two
 * levels of groups, a caption, and a search field in the header slot. Typing
 * narrows the tree by item text and keywords (try "menu" or "date"); clicking a
 * page selects it and the content area shows its name. The full property panel
 * lives in {@link io.github.leewyatt.rxcontrols.samples.showcase.RXSidebarShowcase}.
 */
public class RXSidebarDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXSidebar sidebar = new RXSidebar();

        RXSidebarItem overview = RXSidebarItem.of("Overview", icon("M0 0h7v7H0zM9 0h7v7H9zM0 9h7v7H0zM9 9h7v7H9z"));

        RXSidebarItem sidebarPage = RXSidebarItem.of("Sidebar");
        sidebarPage.getKeywords().addAll("Menu", "Navigation");
        RXSidebarItem navigation = RXSidebarItem.group("Navigation", icon("M0 1h16v2H0zM0 7h16v2H0zM0 13h16v2H0z"),
                sidebarPage, RXSidebarItem.of("Breadcrumb"), RXSidebarItem.of("Tabs"));

        RXSidebarItem pickers = RXSidebarItem.group("Pickers", null,
                RXSidebarItem.of("Date picker"), RXSidebarItem.of("Time picker"));
        RXSidebarItem input = RXSidebarItem.group("Data entry", icon("M0 3h16v10H0zM2 5v6h1V5z"),
                RXSidebarItem.of("Text field"), RXSidebarItem.of("Slider"), pickers);

        sidebar.getItems().addAll(overview, new RXSidebarHeader("Components"), navigation, input);
        sidebar.selectItem(sidebarPage);

        RXTextField search = new RXTextField();
        search.setPromptText("Search");
        search.textProperty().addListener((obs, old, text) -> sidebar.setFilter(RXSidebar.textFilter(text)));
        StackPane header = new StackPane(search);
        header.setPadding(new Insets(12.0));
        sidebar.setHeader(header);
        sidebar.setPlaceholder(new Label("No matches"));

        Label page = new Label();
        page.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarItem selected = sidebar.getSelectedItem();
            return selected == null ? "Nothing selected" : selected.getText();
        }, sidebar.selectedItemProperty()));
        BorderPane root = new BorderPane(page);
        BorderPane.setAlignment(page, Pos.CENTER);
        root.setLeft(sidebar);

        primaryStage.setScene(new Scene(root, 640.0, 440.0));
        primaryStage.setTitle("RXSidebar Demo");
        primaryStage.show();
    }

    private static Region icon(String shape) {
        Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + shape + "\"; -fx-background-color: -fx-text-base-color;"
                + " -fx-pref-width: 16; -fx-pref-height: 16;");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
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
