package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBorderBeamShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXBorderBeam}: a beam running round
 * a card, and a second one on a button that shows only while the pointer is
 * over it. For a full property explorer see {@link RXBorderBeamShowcase}.
 */
public class RXBorderBeamDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label title = new Label("Workspace overview");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label body = new Label("Review task status, deployment health, and recent automation activity.");
        body.setWrapText(true);
        VBox card = new VBox(10.0, title, body);
        card.setPadding(new Insets(20.0));
        card.setPrefWidth(320.0);
        card.setStyle("-fx-background-color: -fx-control-inner-background; -fx-background-radius: 10px;"
                + " -fx-border-color: -fx-box-border; -fx-border-radius: 10px;");
        RXBorderBeam cardBeam = new RXBorderBeam(card);

        Button upgrade = new Button("Upgrade");
        RXBorderBeam buttonBeam = new RXBorderBeam(upgrade);
        buttonBeam.setBeamSize(40.0);
        buttonBeam.activeProperty().bind(buttonBeam.hoverProperty());

        VBox root = new VBox(32.0, cardBeam, buttonBeam);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXBorderBeam Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
