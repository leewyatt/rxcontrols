package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTaskButton;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Minimal {@link RXTaskButton} demo: a profile form whose Save button shows the
 * save in flight and how it ended. The fields cannot be edited while the save
 * runs, and an empty name stops the handler before it starts a run.
 */
public class RXTaskButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        TextField name = new TextField("Ada Lovelace");
        TextField email = new TextField("ada@example.com");
        CheckBox offline = new CheckBox("Pretend the server is down");
        Label message = new Label();

        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(10.0);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.RIGHT);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, fields);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Email"), email);
        form.add(offline, 1, 2);
        form.add(message, 1, 3);

        RXTaskButton save = new RXTaskButton("Save");
        save.setDefaultButton(true);
        save.setPendingText("Saving…");
        save.setSucceededText("Saved");
        save.setFailedText("Not saved");
        save.setOnAction(event -> {
            // The handler decides whether there is anything to run.
            if (name.getText().isBlank()) {
                message.setText("Enter a name first.");
                name.requestFocus();
                return;
            }
            message.setText("");
            RXTaskButton.Run run = save.start();
            // Stands in for the callback of a real request.
            PauseTransition request = new PauseTransition(Duration.millis(1500.0));
            request.setOnFinished(finished -> {
                if (offline.isSelected()) {
                    run.fail();
                    message.setText("The server did not answer. Try again.");
                } else {
                    run.succeed();
                }
            });
            request.play();
        });

        // running is "the work is in flight", whatever face the button shows.
        // Not editable rather than disabled: the focus stays where it is.
        name.editableProperty().bind(save.runningProperty().not());
        email.editableProperty().bind(save.runningProperty().not());
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.disableProperty().bind(save.runningProperty());
        close.setOnAction(event -> primaryStage.close());

        HBox buttons = new HBox(10.0, close, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        // The form takes the spare height, so the buttons stay at the bottom.
        VBox root = new VBox(20.0, form, buttons);
        VBox.setVgrow(form, Priority.ALWAYS);
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root, 440.0, 260.0));
        primaryStage.setTitle("RXTaskButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
