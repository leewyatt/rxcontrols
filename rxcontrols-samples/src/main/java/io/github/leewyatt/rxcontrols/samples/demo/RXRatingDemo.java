package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRatingShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXRating}: a stay review rated in half stars, with a label
 * that follows the hover preview, above the read-only average of all guests. For the
 * property explorer see {@link RXRatingShowcase}.
 */
public class RXRatingDemo extends Application {

    private static final String[] WORDS = {"Terrible", "Poor", "Okay", "Good", "Excellent"};

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXRating mine = new RXRating();
        mine.setStep(RXRating.Step.HALF);
        mine.setAccessibleText("Your rating");

        // The label previews the hovered value and falls back to the committed one.
        Label word = new Label();
        word.textProperty().bind(Bindings.createStringBinding(
                () -> describe(mine.getHoverValue() != null ? mine.getHoverValue() : mine.getValue()),
                mine.hoverValueProperty(), mine.valueProperty()));
        HBox editor = new HBox(12.0, mine, word);
        editor.setAlignment(Pos.CENTER_LEFT);

        Label saved = new Label("Nothing saved yet");
        mine.setOnAction(event -> saved.setText(mine.getValue() == null
                ? "Saved: rating removed"
                : "Saved: " + mine.getValue() + " stars"));

        RXRating average = new RXRating(3.7);
        average.setReadOnly(true);
        average.setAccessibleText("Average rating");
        HBox summary = new HBox(8.0, average, new Label("3.7 from 128 guests"));
        summary.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12.0, new Label("How was your stay?"), editor, saved, summary);
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(36.0));

        primaryStage.setScene(new Scene(root, 380.0, 220.0));
        primaryStage.setTitle("RXRating Demo");
        primaryStage.show();
    }

    private static String describe(Double value) {
        if (value == null) {
            return "Tap a star to rate";
        }
        return WORDS[(int) Math.ceil(value) - 1];
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
