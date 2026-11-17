package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Manual check of where Escape goes while an {@link RXSidebar} flyout is open.
 * The window stands in for a dialog: it has a cancel button, which the platform
 * fires for any Escape that reaches the scene unconsumed. The log shows every
 * Escape the scene receives and every time the cancel button fires, so opening
 * a flyout with the mouse or with the keyboard and then pressing Escape shows
 * whether the key closed only the flyout or went on to cancel the "dialog".
 */
public class RXSidebarEscapeVerification extends Application {

    private final ListView<String> log = new ListView<>();
    private int escapes;
    private int cancels;

    @Override
    public void start(Stage primaryStage) {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setMode(SidebarMode.MINI);
        sidebar.getItems().addAll(
                RXSidebarItem.of("Home", icon("M0 7L8 0l8 7v9H0z")),
                RXSidebarItem.group("Files", icon("M0 2h6l2 2h8v10H0z"),
                        RXSidebarItem.of("Documents"),
                        RXSidebarItem.group("Media", null, RXSidebarItem.of("Images"), RXSidebarItem.of("Videos")),
                        RXSidebarItem.of("Shared")),
                RXSidebarItem.group("Reports", icon("M0 8h4v8H0zM6 0h4v16H6zM12 5h4v11h-4z"),
                        RXSidebarItem.of("Sales"), RXSidebarItem.of("Traffic")));

        ComboBox<FlyoutTrigger> trigger = new ComboBox<>(FXCollections.observableArrayList(FlyoutTrigger.values()));
        trigger.setValue(sidebar.getFlyoutTrigger());
        trigger.valueProperty().addListener((obs, old, value) -> sidebar.setFlyoutTrigger(value));

        Button cancel = new Button("Cancel (cancel button)");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> append("CANCEL fired #" + ++cancels + "  <- Escape reached the dialog"));

        Button clear = new Button("Clear log");
        clear.setOnAction(event -> log.getItems().clear());

        Label steps = new Label("""
                1. Hover a folder or chart icon so its flyout opens (or switch the trigger to CLICK and click it).
                2. Without clicking anything else, press Escape.
                   Expected: the flyout closes and nothing is logged as CANCEL.
                3. Open "Files", move onto "Media" so the second flyout opens, press Escape. Same expectation.
                4. Compare with the keyboard: Tab to an icon, press Enter to open, press Escape.
                5. With no flyout open, press Escape: CANCEL should fire (that is the dialog's own behaviour).""");
        steps.setWrapText(true);

        HBox controls = new HBox(10.0, new Label("Flyout trigger"), trigger, cancel, clear);
        VBox content = new VBox(12.0, steps, controls, log);
        content.setPadding(new Insets(16.0));
        VBox.setVgrow(log, Priority.ALWAYS);

        BorderPane root = new BorderPane(content);
        root.setLeft(sidebar);

        Scene scene = new Scene(root, 860.0, 520.0);
        // A handler, not a filter: it runs after the sidebar and the focused
        // node had their chance, so "consumed" here is what the cancel button sees.
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                append("Escape #" + ++escapes + " reached the scene, consumed=" + event.isConsumed());
            }
        });

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXSidebar: Escape with an open flyout");
        primaryStage.show();
    }

    private void append(String line) {
        log.getItems().add(line);
        log.scrollTo(log.getItems().size() - 1);
    }

    private static Region icon(String shape) {
        Region icon = new Region();
        icon.getStyleClass().add("graphic");
        icon.setStyle("-fx-shape: \"" + shape + "\"; -fx-background-color: -fx-text-base-color;");
        return icon;
    }

    /**
     * Launches the check.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
