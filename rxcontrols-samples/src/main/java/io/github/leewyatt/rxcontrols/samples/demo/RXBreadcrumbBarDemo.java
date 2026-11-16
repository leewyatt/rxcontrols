package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXBreadcrumbBar.BranchMode;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Minimal {@link RXBreadcrumbBar} demo: the same three-level path shown three ways.
 * The first bar lists the siblings of each level in its arrow menu, the second
 * lists the level below, and the third has no arrow menus at all. Clicking an
 * ancestor or choosing a menu entry navigates. The full property panel lives in
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXBreadcrumbBarShowcase}.
 */
public class RXBreadcrumbBarDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXBreadcrumbBar<String> siblings = createBreadcrumb();
        siblings.setChildrenFactory(RXBreadcrumbBarDemo::childrenOf);
        siblings.setBranchMode(BranchMode.SIBLINGS);

        RXBreadcrumbBar<String> children = createBreadcrumb();
        children.setChildrenFactory(RXBreadcrumbBarDemo::childrenOf);
        children.setBranchMode(BranchMode.CHILDREN);

        RXBreadcrumbBar<String> plain = createBreadcrumb();

        VBox root = new VBox(8.0,
                new Label("Arrows list siblings"), siblings,
                new Label("Arrows list children"), children,
                new Label("No arrows"), plain);
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root, 560.0, 260.0));
        primaryStage.setTitle("RXBreadcrumbBar Demo");
        primaryStage.show();
    }

    private static RXBreadcrumbBar<String> createBreadcrumb() {
        RXBreadcrumbBar<String> breadcrumb = new RXBreadcrumbBar<>("Components", "Data Display", "Badge");
        // Clicks and menu choices change the path without an onNavigate handler.
        breadcrumb.setAutoNavigate(true);
        return breadcrumb;
    }

    // What lies directly below a path; the bar derives sibling menus from it too.
    private static List<String> childrenOf(List<String> path) {
        if (path.isEmpty()) {
            return List.of();
        }
        String parent = path.get(path.size() - 1);
        return switch (parent) {
            case "Components" -> List.of("Buttons", "Data Display", "Inputs");
            case "Buttons" -> List.of("RXButton", "RXFillButton");
            case "Data Display" -> List.of("Badge", "Chip", "Avatar");
            case "Inputs" -> List.of("RXTextField", "RXSlider");
            default -> List.of();
        };
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
