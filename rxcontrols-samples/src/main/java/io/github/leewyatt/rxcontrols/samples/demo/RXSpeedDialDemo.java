package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSpeedDialShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact notes-screen demo for {@link RXSpeedDial}: one persistent main action
 * exposes a small set of document actions without changing the surrounding
 * layout. For the full property explorer see {@link RXSpeedDialShowcase}.
 */
public class RXSpeedDialDemo extends Application {

    private Label status;

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane(createDocument(), createSpeedDial());
        root.getStyleClass().add("speed-dial-demo");
        StackPane.setAlignment(root.getChildren().get(1), Pos.BOTTOM_RIGHT);
        StackPane.setMargin(root.getChildren().get(1), new Insets(0.0, 34.0, 34.0, 0.0));

        Scene scene = new Scene(root, 760.0, 520.0);
        scene.getStylesheets().add(getClass().getResource("rx-speed-dial-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXSpeedDial Demo");
        primaryStage.show();
    }

    private Node createDocument() {
        Label title = new Label("Launch checklist");
        title.getStyleClass().add("doc-title");
        Label meta = new Label("Operations / Release 27");
        meta.getStyleClass().add("doc-meta");
        VBox header = new VBox(2.0, title, meta);

        VBox rows = new VBox(10.0,
                createTask("Verify migration dry run", "Database owner"),
                createTask("Freeze billing imports", "Finance system"),
                createTask("Prepare customer status page", "Support"),
                createTask("Schedule rollout window", "Platform"));
        rows.getStyleClass().add("doc-rows");

        status = new Label("No quick action selected");
        status.getStyleClass().add("status-label");

        VBox document = new VBox(20.0, header, rows, status);
        document.getStyleClass().add("document");
        document.setMaxWidth(430.0);
        StackPane.setAlignment(document, Pos.CENTER);
        return document;
    }

    private Node createTask(String title, String owner) {
        Label name = new Label(title);
        name.getStyleClass().add("task-title");
        Label ownerLabel = new Label(owner);
        ownerLabel.getStyleClass().add("task-owner");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12.0, name, spacer, ownerLabel);
        row.getStyleClass().add("task-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private RXSpeedDial createSpeedDial() {
        RXSpeedDialAction save = action("Save", "M3 8 L7 12 L14 4", "Saved draft");
        RXSpeedDialAction duplicate = action("Duplicate",
                "M4 3 H11 V5 H6 V14 H4 Z M7 6 H14 V17 H7 Z", "Duplicated checklist");
        RXSpeedDialAction share = action("Share",
                "M13 5 A2 2 0 1 0 13 4 M6 9 A2 2 0 1 0 6 8 M13 14 A2 2 0 1 0 13 13 M8 9 L11 6 M8 10 L11 13",
                "Share link copied");
        RXSpeedDialAction delete = action("Delete", "M5 6 H14 M7 6 V15 M12 6 V15 M6 6 L7 17 H12 L13 6 M8 4 H11",
                "Moved to trash");

        RXSpeedDial dial = new RXSpeedDial(icon("M8 2 H11 V8 H17 V11 H11 V17 H8 V11 H2 V8 H8 Z"),
                save, duplicate, share, delete);
        dial.setOpenIcon(icon("M4 4 L16 16 M16 4 L4 16"));
        dial.setDirection(RXSpeedDial.Direction.UP);
        dial.setLabelMode(RXSpeedDial.LabelMode.PERSISTENT);
        return dial;
    }

    private RXSpeedDialAction action(String text, String shape, String message) {
        return new RXSpeedDialAction(text, icon(shape), event -> status.setText(message));
    }

    private static Region icon(String shape) {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setStyle("-fx-shape: \"" + shape + "\";");
        icon.setMinSize(18.0, 18.0);
        icon.setPrefSize(18.0, 18.0);
        icon.setMaxSize(18.0, 18.0);
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
