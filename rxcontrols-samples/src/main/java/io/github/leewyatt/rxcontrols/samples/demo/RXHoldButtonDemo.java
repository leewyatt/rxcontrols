package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXHoldButton;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Minimal {@link RXHoldButton} demo: a file list where each row is deleted by
 * holding its button, with a "Delete…" button that asks for confirmation
 * instead, as an untimed alternative to the hold.
 */
public class RXHoldButtonDemo extends Application {

    private final VBox rows = new VBox(8.0);

    @Override
    public void start(Stage primaryStage) {
        for (String file : new String[]{"report-2025.pdf", "holiday.jpg", "notes.txt", "backup.zip"}) {
            rows.getChildren().add(createRow(file));
        }

        Label hint = new Label("Hold to delete; release or move the pointer out to cancel.");
        hint.setOpacity(0.7);

        VBox root = new VBox(16.0, rows, hint);
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root, 520.0, 320.0));
        primaryStage.setTitle("RXHoldButton Demo");
        primaryStage.show();
    }

    private HBox createRow(String file) {
        Label name = new Label(file);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10.0, name, spacer);

        RXHoldButton hold = new RXHoldButton("Hold to delete");
        hold.setCompletedText("Deleted");
        hold.setAccessibleHelp("Hold to delete " + file
                + "; release or move out to cancel, or use Delete… to confirm instead");

        Button confirm = new Button("Delete…");
        // The default feedback duration keeps "Deleted" until the row goes:
        // removing the row is the reset.
        hold.setOnAction(event -> {
            confirm.setDisable(true);
            removeLater(row);
        });
        confirm.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + file + "?");
            alert.initOwner(row.getScene().getWindow());
            alert.showAndWait()
                    .filter(answer -> answer == ButtonType.OK)
                    .ifPresent(answer -> remove(row));
        });

        row.getChildren().addAll(hold, confirm);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // Long enough to read "Deleted" before the row disappears.
    private void removeLater(HBox row) {
        PauseTransition delay = new PauseTransition(Duration.millis(700.0));
        delay.setOnFinished(event -> remove(row));
        delay.play();
    }

    // Focus would otherwise jump to the first row's hold button, where a
    // still-held SPACE would start deleting it; the list itself takes it.
    private void remove(HBox row) {
        if (row.getScene() != null && row.getScene().getFocusOwner() != null
                && isInside(row.getScene().getFocusOwner(), row)) {
            rows.requestFocus();
        }
        rows.getChildren().remove(row);
    }

    private static boolean isInside(Node node, HBox row) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == row) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
