package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImageViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal real-world demo for {@link RXImageView}.
 *
 * <p>Shows fixed image radius and image insets in a small editorial layout.
 * For the full property explorer see {@link RXImageViewShowcase}.</p>
 */
public class RXImageViewDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(18.0, createHero(), createCards());
        root.getStyleClass().add("rx-image-view-demo");
        root.setFillWidth(true);

        Scene scene = new Scene(root, 960.0, 640.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-image-view-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXImageView Demo");
        primaryStage.show();
    }

    private Node createHero() {
        RXImageView imageView = new RXImageView(image("/scenery/4.png"));
        imageView.setImageRadius(22.0);
        imageView.setPrefSize(860.0, 310.0);
        imageView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Region scrim = new Region();
        scrim.getStyleClass().add("hero-scrim");
        scrim.setMouseTransparent(true);

        Label eyebrow = label("FIELD NOTES", "eyebrow");
        Label title = label("Rounded cover imagery for dashboard cards.", "hero-title");
        title.setWrapText(true);
        Label copy = label("A single image node handles responsive cover fit and fixed pixel corners.",
                "hero-copy");
        copy.setWrapText(true);
        VBox text = new VBox(8.0, eyebrow, title, copy);
        text.getStyleClass().add("hero-text");

        StackPane hero = new StackPane(imageView, scrim, text);
        hero.getStyleClass().add("hero");
        StackPane.setAlignment(text, Pos.BOTTOM_LEFT);
        VBox.setVgrow(hero, Priority.ALWAYS);
        return hero;
    }

    private Node createCards() {
        HBox row = new HBox(18.0,
                createCard("Inset crop", "Image padding leaves room for card chrome.",
                        "/scenery/1.png", new Insets(14.0), 16.0),
                createCard("Bleed crop", "Negative insets let imagery extend past the content box.",
                        "/scenery/3.png", new Insets(-16.0), 16.0),
                createCard("Soft tile", "The radius remains stable while the image scales.",
                        "/scenery/2.png", Insets.EMPTY, 28.0));
        row.getStyleClass().add("card-row");
        return row;
    }

    private Node createCard(String title, String copy, String resource, Insets imageInsets, double imageRadius) {
        RXImageView imageView = new RXImageView(image(resource));
        imageView.setImageInsets(imageInsets);
        imageView.setImageRadius(imageRadius);
        imageView.setPrefHeight(168.0);
        imageView.setMaxWidth(Double.MAX_VALUE);
        imageView.getStyleClass().add("card-image");

        Label titleLabel = label(title, "card-title");
        Label copyLabel = label(copy, "card-copy");
        copyLabel.setWrapText(true);

        VBox card = new VBox(12.0, imageView, titleLabel, copyLabel);
        card.getStyleClass().add("demo-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Image image(String resource) {
        return new Image(RXImageViewDemo.class.getResource(resource).toExternalForm(), true);
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
