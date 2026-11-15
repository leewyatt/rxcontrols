package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * Minimal {@link RXBreadcrumbBar} demo: a three-level path the application
 * controls. Clicking an ancestor reports a request and the handler commits it;
 * the arrow next to each level lists its siblings, so switching from
 * "Data Display" to "Buttons" lands on "Components / Buttons". The full property
 * panel lives in
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXBreadcrumbBarShowcase}.
 */
public class RXBreadcrumbBarDemo extends Application {

    private static final Map<String, List<String>> CHILDREN = Map.of(
            "Components", List.of("Buttons", "Data Display", "Inputs"),
            "Data Display", List.of("Badge", "Chip", "Avatar"));

    @Override
    public void start(Stage primaryStage) {
        RXBreadcrumbBar<String> breadcrumb = new RXBreadcrumbBar<>("Components", "Data Display", "Badge");
        breadcrumb.setBranchFactory(prefix -> prefix.size() < 2
                ? List.of()
                : CHILDREN.getOrDefault(prefix.get(prefix.size() - 2), List.of()));

        Label location = new Label();
        breadcrumb.setOnNavigate(event -> breadcrumb.getItems().setAll(event.getPath()));
        breadcrumb.getItems().addListener((InvalidationListener) obs ->
                location.setText("Showing: " + breadcrumb.getItems()));
        location.setText("Showing: " + breadcrumb.getItems());

        VBox root = new VBox(24.0, breadcrumb, location);
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root, 560.0, 200.0));
        primaryStage.setTitle("RXBreadcrumbBar Demo");
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
