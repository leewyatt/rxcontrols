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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXSpeedDial}: one persistent main action exposes a
 * small set of actions and reports the last selected action. For the full
 * property explorer see {@link RXSpeedDialShowcase}.
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
        status = new Label("last action: none");
        status.getStyleClass().add("status-label");
        StackPane.setAlignment(status, Pos.CENTER);
        return status;
    }

    private RXSpeedDial createSpeedDial() {
        RXSpeedDialAction save = action("Save", "M9 16.2 L4.8 12 L3.4 13.4 L9 19 L21 7 L19.6 5.6 Z");
        RXSpeedDialAction duplicate = action("Duplicate",
                "M4 3 H11 V5 H6 V14 H4 Z M7 6 H14 V17 H7 Z");
        RXSpeedDialAction share = action("Share",
                "M13 5 A2 2 0 1 0 13 4 M6 9 A2 2 0 1 0 6 8 M13 14 A2 2 0 1 0 13 13 M8 9 L11 6 M8 10 L11 13");
        RXSpeedDialAction delete = action("Delete",
                "M5 6 H14 M7 6 V15 M12 6 V15 M6 6 L7 17 H12 L13 6 M8 4 H11");

        RXSpeedDial dial = new RXSpeedDial(icon("M8 2 H11 V8 H17 V11 H11 V17 H8 V11 H2 V8 H8 Z"),
                save, duplicate, share, delete);
        dial.setDirection(RXSpeedDial.Direction.UP);
        dial.setLabelMode(RXSpeedDial.LabelMode.PERSISTENT);
        return dial;
    }

    private RXSpeedDialAction action(String text, String shape) {
        return new RXSpeedDialAction(text, icon(shape), event -> status.setText("last action: " + text));
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
