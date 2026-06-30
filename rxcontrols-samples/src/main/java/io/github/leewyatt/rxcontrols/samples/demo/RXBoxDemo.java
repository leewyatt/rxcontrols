package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXBox;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXBox}: a vertical dialog-card layout beside a
 * horizontal task-row layout.
 */
public class RXBoxDemo extends Application {

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(Stage primaryStage) {
        StackPane verticalPane = pane("Vertical RXBox", createDialogCard());
        StackPane horizontalPane = pane("Horizontal RXBox", createTaskRow());

        HBox root = new HBox(20.0, verticalPane, horizontalPane);
        root.getStyleClass().add("root");
        HBox.setHgrow(verticalPane, Priority.ALWAYS);
        HBox.setHgrow(horizontalPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 960.0, 560.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-box-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXBox Demo");
        primaryStage.show();
    }

    private StackPane pane(String title, Region content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("pane-title");
        StackPane.setAlignment(heading, Pos.TOP_LEFT);

        StackPane pane = new StackPane(content, heading);
        pane.getStyleClass().add("demo-pane");
        StackPane.setMargin(content, new Insets(42.0, 18.0, 18.0, 18.0));
        return pane;
    }

    private Region createDialogCard() {
        Label title = label("Archive project?", "dialog-title");
        Label content = label(
                "The project will move out of the active workspace. You can restore it later from archived items.",
                "dialog-copy");
        content.setWrapText(true);
        content.setMaxWidth(320.0);

        Button cancel = button("Cancel", "secondary-button");
        Button archive = button("Archive", "primary-button");
        RXBox actions = new RXBox(Orientation.HORIZONTAL, 10.0, cancel, archive);
        actions.getStyleClass().add("dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMaxWidth(Region.USE_PREF_SIZE);
        RXBox.setAlignment(actions, Pos.CENTER_RIGHT);

        RXBox card = new RXBox(Orientation.VERTICAL, 12.0, title, content, actions);
        card.getStyleClass().add("dialog-card");
        card.setPadding(new Insets(24.0));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(380.0);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        return card;
    }

    private Region createTaskRow() {
        Region icon = new Region();
        icon.getStyleClass().add("task-icon");

        Label title = label("Quarterly report.pdf", "task-title");
        Label detail = label("12 MB ready to share", "task-detail");
        RXBox text = new RXBox(Orientation.VERTICAL, 3.0, title, detail);
        text.setMaxWidth(Double.MAX_VALUE);

        Label status = label("Synced", "status-pill");
        Button open = button("Open", "secondary-button");

        RXBox row = new RXBox(Orientation.HORIZONTAL, 14.0, icon, text, status, open);
        row.getStyleClass().add("task-row");
        row.setPadding(new Insets(18.0));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(520.0);
        row.setMaxHeight(Region.USE_PREF_SIZE);

        RXBox.setGrow(text, Priority.ALWAYS);
        RXBox.setMargin(open, new Insets(0.0, 0.0, 0.0, 4.0));
        return row;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private Button button(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("demo-button", styleClass);
        return button;
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
