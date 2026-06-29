package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCheckBoxShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXCheckBox}.
 *
 * <p>A small sign-up panel: three labelled check boxes whose {@code onAction}
 * updates a status line, plus one tri-state ("Select all") check box that cycles
 * unchecked &rarr; indeterminate &rarr; checked to show the mixed state. This is
 * the everyday use of a check box as a deferred-commit boolean in a form. For the
 * property explorer see {@link RXCheckBoxShowcase}.</p>
 */
public class RXCheckBoxDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        Label status = new Label("Pick your options…");
        status.setStyle("-fx-text-fill: #52606d;");

        RXCheckBox terms = new RXCheckBox("Accept terms and conditions");
        terms.setOnAction(event -> status.setText("Terms " + onOff(terms.isSelected())));

        RXCheckBox newsletter = new RXCheckBox("Subscribe to the newsletter");
        newsletter.setOnAction(event -> status.setText("Newsletter " + onOff(newsletter.isSelected())));

        RXCheckBox remember = new RXCheckBox("Remember me on this device");
        remember.setSelected(true);
        remember.setOnAction(event -> status.setText("Remember me " + onOff(remember.isSelected())));

        RXCheckBox selectAll = new RXCheckBox("Select all (tri-state)");
        selectAll.setAllowIndeterminate(true);
        selectAll.setIndeterminate(true);
        selectAll.setOnAction(event -> status.setText("Select all: " + tristate(selectAll)));

        VBox root = new VBox(16.0, terms, newsletter, remember, selectAll, status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36.0, 48.0, 36.0, 48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXCheckBox Demo");
        primaryStage.show();
    }

    private static String onOff(boolean selected) {
        return selected ? "on" : "off";
    }

    private static String tristate(RXCheckBox checkBox) {
        if (checkBox.isIndeterminate()) {
            return "mixed";
        }
        return checkBox.isSelected() ? "checked" : "unchecked";
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
