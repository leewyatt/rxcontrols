package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates the basic use of a standalone {@link RXTitledPane}: a titled form
 * section that collapses on a header click, with a switch at the header's trailing
 * edge that can be used without toggling the pane.
 */
public class RXTitledPaneDemo extends Application {

    @Override
    public void start(Stage stage) {
        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(8.0);
        form.addRow(0, new Label("Email"), new TextField("me@example.com"));
        form.addRow(1, new Label("Quiet hours"), new TextField("22:00 - 07:00"));

        RXSwitchButton enabled = new RXSwitchButton();
        enabled.setSelected(true);

        RXTitledPane notifications = new RXTitledPane("Notifications", form);
        notifications.setHeaderTrailing(enabled);
        notifications.setExpanded(true);

        VBox root = new VBox(notifications);
        root.setPadding(new Insets(24.0));
        stage.setScene(new Scene(root, 460, 260));
        stage.setTitle("RXTitledPane Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
