package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSnackbarRequest;
import io.github.leewyatt.rxcontrols.RXSnackbars;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@code RXSnackbarHost}: a realistic "file saved / file
 * deleted with Undo" flow driven entirely through the {@code RXSnackbars}
 * facade — no host setup, no layout wiring. "Save" pops a plain auto-hiding
 * confirmation; "Delete" pops a snackbar whose single Undo action restores the
 * file and reports through the status label. Messages sent while one is visible
 * queue up and display one at a time.
 */
public class RXSnackbarHostDemo extends Application {

    private final StringProperty status = new SimpleStringProperty("myfile.txt is present.");

    @Override
    public void start(Stage primaryStage) {
        Button saveButton = new Button("Save file");
        saveButton.setOnAction(event -> RXSnackbars.show(saveButton, "File saved"));

        Button deleteButton = new Button("Delete file…");
        deleteButton.setOnAction(event -> {
            status.set("myfile.txt deleted.");
            RXSnackbars.show(deleteButton, RXSnackbarRequest.builder("File deleted")
                    .action("Undo", () -> status.set("myfile.txt restored."))
                    .build());
        });

        Label statusLabel = new Label();
        statusLabel.textProperty().bind(status);

        VBox root = new VBox(14.0, new HBox(10.0, saveButton, deleteButton), statusLabel);
        root.setAlignment(Pos.CENTER);
        primaryStage.setScene(new Scene(root, 520.0, 360.0));
        primaryStage.setTitle("RXSnackbarHost Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
