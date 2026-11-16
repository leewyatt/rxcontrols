package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates the two everyday uses: a standalone {@link RXTitledPane} with a switch
 * in its action area, and a single-expansion {@link RXAccordion} of FAQ entries whose
 * answers are created the first time they are opened.
 */
public class RXAccordionDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXSwitchButton notifications = new RXSwitchButton();
        notifications.setSelected(true);
        RXTitledPane settings = new RXTitledPane("Notifications", notificationForm());
        settings.getActions().add(notifications);
        settings.setExpanded(true);

        RXAccordion faq = new RXAccordion(
                faq("How do I reset my password?",
                        "Open the sign-in page, choose \"Forgot password\" and follow the link we email you."),
                faq("Can I change my username?",
                        "Yes. Usernames can be changed once every 30 days from the profile page."),
                faq("Where is my data stored?",
                        "In the region you picked when the workspace was created. It never leaves that region."));

        VBox root = new VBox(16.0, settings, new Label("Frequently asked questions"), faq);
        root.setPadding(new Insets(20.0));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);

        stage.setScene(new Scene(scroll, 520, 560));
        stage.setTitle("RXAccordion Demo");
        stage.show();
    }

    private static GridPane notificationForm() {
        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(8.0);
        form.addRow(0, new Label("Email"), new TextField("me@example.com"));
        form.addRow(1, new Label("Quiet hours"), new TextField("22:00 - 07:00"));
        return form;
    }

    private static RXTitledPane faq(String question, String answer) {
        RXTitledPane pane = new RXTitledPane(question);
        pane.setContentFactory(() -> {
            Label label = new Label(answer);
            label.setWrapText(true);
            return label;
        });
        return pane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
