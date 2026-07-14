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
        RXSpeedDialAction save = action("Save",
                "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z");
        RXSpeedDialAction duplicate = action("Duplicate",
                "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11"
                        + "c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z");
        RXSpeedDialAction share = action("Share",
                "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7"
                        + "l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3"
                        + "c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3"
                        + "c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92"
                        + "s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z");
        RXSpeedDialAction delete = action("Delete",
                "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");

        RXSpeedDial dial = new RXSpeedDial(icon("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
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
