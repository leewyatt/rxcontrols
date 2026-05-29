package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImagePaneShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal real-world demo for {@link RXImagePane}.
 *
 * <p>Shows an image-backed card with overlay nodes managed through
 * {@link RXImagePane#getOverlayChildren()}. For the full property explorer see
 * {@link RXImagePaneShowcase}.</p>
 */
public class RXImagePaneDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        RXImagePane imagePane = new RXImagePane(image("/scenery/4.png"));
        imagePane.getStyleClass().add("destination-pane");
        imagePane.setImageRadius(24.0);
        imagePane.setPrefSize(720.0, 420.0);
        imagePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Region scrim = new Region();
        scrim.getStyleClass().add("scrim");
        scrim.setMouseTransparent(true);

        Label badge = label("FEATURED", "badge");
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        VBox content = createContent();
        content.setMaxWidth(520.0);

        imagePane.getOverlayChildren().addAll(scrim, badge, content);
        RXImagePane.setAlignment(badge, Pos.TOP_RIGHT);
        RXImagePane.setMargin(badge, new Insets(24.0));
        RXImagePane.setAlignment(content, Pos.BOTTOM_LEFT);
        RXImagePane.setMargin(content, new Insets(32.0));

        StackPane root = new StackPane(imagePane);
        root.getStyleClass().add("rx-image-pane-demo");

        Scene scene = new Scene(root, 900.0, 560.0);
        scene.getStylesheets().add(
                getClass().getResource("rx_image_pane_demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXImagePane Demo");
        primaryStage.show();
    }

    private VBox createContent() {
        Label eyebrow = label("MOUNTAIN RETREAT", "eyebrow");
        Label title = label("Quiet cabins above the valley.", "title");
        title.setWrapText(true);
        Label copy = label("Wake to pale light, pine air, and a trailhead just beyond the porch.",
                "copy");
        copy.setWrapText(true);

        HBox actions = new HBox(10.0,
                label("View stay", "primary-action"),
                label("Save", "secondary-action"));
        actions.getStyleClass().add("actions");

        VBox content = new VBox(9.0, eyebrow, title, copy, actions);
        content.getStyleClass().add("content");
        return content;
    }

    private Image image(String resource) {
        return new Image(RXImagePaneDemo.class.getResource(resource).toExternalForm(), true);
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
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
