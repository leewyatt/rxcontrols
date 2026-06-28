package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSwitchButtonShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXSwitchButton}.
 *
 * <p>A small "Settings" panel: three labelled switches whose {@code onAction}
 * updates a status line, the everyday use of a switch as an immediately
 * effective boolean setting. For the property explorer see
 * {@link RXSwitchButtonShowcase}.</p>
 */
public class RXSwitchButtonDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        Label status = new Label("Toggle a setting…");
        status.setStyle("-fx-text-fill: #52606d;");

        RXSwitchButton notifications = new RXSwitchButton("Notifications");
        notifications.setSelected(true);
        notifications.setOnAction(event ->
                status.setText("Notifications " + onOff(notifications.isSelected())));

        RXSwitchButton darkMode = new RXSwitchButton("Dark mode");
        darkMode.setOnAction(event ->
                status.setText("Dark mode " + onOff(darkMode.isSelected())));

        RXSwitchButton wifi = new RXSwitchButton("Wi-Fi");
        wifi.setSelected(true);
        wifi.setOnAction(event -> status.setText("Wi-Fi " + onOff(wifi.isSelected())));

        VBox root = new VBox(16.0, notifications, darkMode, wifi, status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36.0, 48.0, 36.0, 48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXSwitchButton Demo");
        primaryStage.show();
    }

    private static String onOff(boolean selected) {
        return selected ? "on" : "off";
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
