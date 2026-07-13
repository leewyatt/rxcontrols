package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTab;
import io.github.leewyatt.rxcontrols.RXTabPane;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXTabPane} demo: three content-owning tabs with a sliding
 * underline indicator. Click a tab (or arrow-key through them) and watch the
 * indicator slide while the page swaps. The full property panel lives in
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXTabPaneShowcase}.
 */
public class RXTabPaneDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXTabPane tabs = new RXTabPane(
                RXTab.of("Overview", page("Overview", "#4f6df5")),
                RXTab.of("Analytics", page("Analytics", "#12b886")),
                RXTab.of("Settings", page("Settings", "#f08c00")));
        tabs.setPrefSize(520.0, 300.0);

        primaryStage.setScene(new Scene(tabs, 560.0, 340.0));
        primaryStage.setTitle("RXTabPane Demo");
        primaryStage.show();
    }

    private VBox page(String name, String accent) {
        Label heading = new Label(name);
        heading.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");
        Label body = new Label("The \"" + name + "\" page is owned by its tab and shown only while selected.");
        body.setWrapText(true);
        body.setMaxWidth(400.0);
        VBox box = new VBox(14.0, heading, body);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 28;");
        return box;
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
