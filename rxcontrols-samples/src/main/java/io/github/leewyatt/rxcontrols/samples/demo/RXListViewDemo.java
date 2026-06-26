package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXListView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXListView}: a multi-select "choose your interests"
 * list. Switching the selection model to {@code MULTIPLE} lets the default
 * {@code AUTO} visual mode resolve to a checkbox row, so clicking anywhere on a
 * row toggles its checkbox; a live footer reflects the key-based check model. No
 * control panel by design.
 */
public class RXListViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        ObservableList<String> topics = FXCollections.observableArrayList(
                "Design systems", "Accessibility", "Animation", "Data visualization",
                "Performance", "Theming", "Layout", "Typography", "Color", "Icons",
                "Forms & validation", "Internationalization");

        RXListView<String> list = new RXListView<>(topics);
        // MULTIPLE selection: AUTO resolves to the checkbox visual; click a row anywhere
        // to toggle it (the checkbox is just a display of the selection).
        list.setSelectionMode(SelectionMode.MULTIPLE);

        Label count = new Label();
        count.textProperty().bind(Bindings.size(list.getSelectionModel().getSelectedItems())
                .asString("%d selected"));
        Button clear = new Button("Clear");
        clear.setOnAction(e -> list.getSelectionModel().clearSelection());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8.0, count, spacer, clear);
        footer.setPadding(new Insets(10, 14, 10, 14));

        BorderPane root = new BorderPane(list);
        root.setTop(createHeader());
        root.setBottom(footer);

        primaryStage.setScene(new Scene(root, 420.0, 560.0));
        primaryStage.setTitle("RXListView Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Choose your interests");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Click a row anywhere to toggle its checkbox");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
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
