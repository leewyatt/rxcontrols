package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXHighlightText;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates RXHighlightText as a search-as-you-type highlighter: the text field
 * feeds whitespace-separated keywords into the control's keyword list, and the
 * read-only matched state drives a found / not-found label.
 */
public class RXHighlightTextDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXHighlightText highlightText = new RXHighlightText(
                "JavaFX is a modern UI toolkit for desktop and rich client applications.\n"
                        + "RXHighlightText highlights one or more keywords inside a paragraph.\n"
                        + "It supports literal and regular expression matching with case-sensitive "
                        + "or case-insensitive modes.");
        highlightText.setLineSpacing(6);
        highlightText.setMaxWidth(420);

        TextField search = new TextField();
        search.setPromptText("Enter keywords separated by spaces, for example: JavaFX keyword");
        search.textProperty().addListener((obs, old, value) -> {
            List<String> words = (value == null || value.isBlank())
                    ? List.of()
                    : Arrays.asList(value.trim().split("\\s+"));
            highlightText.getKeywords().setAll(words);
        });
        search.setText("JavaFX keyword");

        Label status = new Label();
        status.textProperty().bind(Bindings.when(highlightText.matchedProperty())
                .then("Keywords found").otherwise("No keywords found"));

        VBox root = new VBox(16, search, status, highlightText);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(24));
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXHighlightText Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
