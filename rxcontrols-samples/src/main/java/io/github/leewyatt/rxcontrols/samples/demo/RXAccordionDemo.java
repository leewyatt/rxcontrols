package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates the basic use of {@link RXAccordion}: a group of FAQ entries where
 * opening one answer closes the previously opened one.
 */
public class RXAccordionDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXAccordion faq = new RXAccordion(
                entry("How do I reset my password?",
                        "Open the sign-in page, choose \"Forgot password\" and follow the link we email you."),
                entry("Can I change my username?",
                        "Yes. Usernames can be changed once every 30 days from the profile page."),
                entry("Where is my data stored?",
                        "In the region you picked when the workspace was created. It never leaves that region."));

        VBox root = new VBox(faq);
        root.setPadding(new Insets(24.0));
        stage.setScene(new Scene(root, 480, 360));
        stage.setTitle("RXAccordion Demo");
        stage.show();
    }

    private static RXTitledPane entry(String question, String answer) {
        Label label = new Label(answer);
        label.setWrapText(true);
        return new RXTitledPane(question, label);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
