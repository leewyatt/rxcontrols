package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRadioButtonShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXRadioButton}.
 *
 * <p>A payment-method picker: three labelled radio buttons in one
 * {@link ToggleGroup} whose selection updates a status line. This is the everyday
 * use of a radio button — pick exactly one option from a mutually exclusive group.
 * For the property explorer see {@link RXRadioButtonShowcase}.</p>
 */
public class RXRadioButtonDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        Label status = new Label("Choose a payment method…");
        status.setStyle("-fx-text-fill: #52606d;");

        ToggleGroup group = new ToggleGroup();

        RXRadioButton card = new RXRadioButton("Credit card");
        RXRadioButton alipay = new RXRadioButton("Alipay");
        RXRadioButton wechat = new RXRadioButton("WeChat Pay");
        card.setToggleGroup(group);
        alipay.setToggleGroup(group);
        wechat.setToggleGroup(group);
        card.setSelected(true);

        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                status.setText("Choose a payment method…");
            } else {
                status.setText("Paying with " + ((RadioButton) selected).getText());
            }
        });

        VBox root = new VBox(16.0, card, alipay, wechat, status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36.0, 48.0, 36.0, 48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXRadioButton Demo");
        primaryStage.show();
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
