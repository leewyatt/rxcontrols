package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDialogLayout;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXDialog}: two realistic, in-scene modal dialogs shown
 * over the page. "Delete file…" opens a centered confirmation built from an
 * {@link RXDialogLayout} with {@code Cancel} / {@code Delete} actions and reports
 * the choice through {@code onResult}; "Show report…" opens a sliding dialog with
 * a close (X) button and an expandable "details" region.
 *
 * <p>Everything uses only the public API — {@code content} + {@code buttonTypes} +
 * {@code resultConverter} + the asynchronous {@code onResult} — to keep the demo an
 * honest illustration of the control's surface.</p>
 */
public class RXDialogDemo extends Application {

    private final StringProperty status = new SimpleStringProperty("No action yet.");

    @Override
    public void start(Stage primaryStage) {
        Button deleteButton = new Button("Delete file…");
        deleteButton.setOnAction(e -> showConfirmation(deleteButton));

        Button reportButton = new Button("Show report…");
        reportButton.setOnAction(e -> showReport(reportButton));

        Label statusLabel = new Label();
        statusLabel.textProperty().bind(status);

        Label title = new Label("RXDialog");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label hint = new Label("In-scene modal dialogs shown over this page.");

        VBox root = new VBox(16.0, title, hint, new HBox(12.0, deleteButton, reportButton), statusLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 760.0, 480.0));
        primaryStage.setTitle("RXDialog Demo");
        primaryStage.show();
    }

    private void showConfirmation(Node owner) {
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setContent(new RXDialogLayout("Delete file?",
                "“report.pdf” will be permanently removed. This cannot be undone."));
        ButtonType delete = new ButtonType("Delete", ButtonData.OK_DONE);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, delete);
        dialog.setResultConverter(buttonType -> buttonType);
        dialog.setOnResult(result -> status.set(
                result == delete ? "File deleted." : "Deletion cancelled."));
        dialog.show(owner);
    }

    private void showReport(Node owner) {
        TextArea details = new TextArea("""
                [12:01] Resolving dependencies… ok
                [12:02] Compiling 142 sources… ok
                [12:03] Running 1254 tests… ok""");
        details.setEditable(false);
        details.setPrefRowCount(4);

        RXDialogLayout layout = new RXDialogLayout("Build succeeded", "All 1254 tests passed.");
        layout.setExpandableContent(details);

        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setContent(layout);
        dialog.setTransition(RXDialogTransition.SLIDE_TOP);
        dialog.setShowCloseButton(true);
        dialog.getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.setOnResult(result -> status.set("Report dismissed."));
        dialog.show(owner);
    }

    /**
     * Launches the demo.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
