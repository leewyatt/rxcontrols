package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSmoothScrollSupport;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates installing {@link RXSmoothScrollSupport} on a plain JavaFX
 * {@link ScrollPane} containing a long {@link VBox}.
 */
public class RXSmoothScrollerDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox content = new VBox(8.0);
        content.setPadding(new Insets(16.0));
        for (int i = 1; i <= 72; i++) {
            content.getChildren().add(createRow(i));
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        RXSmoothScrollSupport.install(scrollPane);

        BorderPane root = new BorderPane(scrollPane);
        root.setTop(createHeader());

        primaryStage.setScene(new Scene(root, 420.0, 560.0));
        primaryStage.setTitle("RXSmoothScroller Demo");
        primaryStage.show();
    }

    private StackPane createHeader() {
        Label title = new Label("Smooth ScrollPane");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        StackPane header = new StackPane(title);
        header.setPadding(new Insets(16.0, 16.0, 8.0, 16.0));
        return header;
    }

    private StackPane createRow(int index) {
        Label label = new Label("Scroll item " + index);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-size: 14px;");

        StackPane row = new StackPane(label);
        row.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        row.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 6px;
                -fx-border-color: #d1d5db;
                -fx-border-radius: 6px;
                """);
        return row;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
