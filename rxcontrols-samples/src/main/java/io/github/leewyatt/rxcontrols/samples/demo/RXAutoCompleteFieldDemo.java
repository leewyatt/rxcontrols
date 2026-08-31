package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAutoCompleteField;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;

/**
 * Minimal sample application for {@link RXAutoCompleteField}.
 *
 * <p>Shows two lightweight, realistic scenarios: a fruit field driven by the
 * default case-insensitive substring filter, and an email field with a custom
 * filter + completion handler that completes the domain after {@code @}.
 *
 * <p>For the property explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXAutoCompleteFieldShowcase}.
 */
public class RXAutoCompleteFieldDemo extends Application {

    private static final List<String> FRUITS = List.of(
            "Apple", "Apricot", "Avocado", "Banana", "Blackberry", "Blueberry",
            "Cherry", "Coconut", "Cranberry", "Date", "Dragon Fruit", "Fig",
            "Grape", "Grapefruit", "Guava", "Kiwi", "Lemon", "Lime", "Lychee",
            "Mango", "Melon", "Nectarine", "Orange", "Papaya", "Passion Fruit",
            "Peach", "Pear", "Persimmon", "Pineapple", "Plum", "Pomegranate",
            "Raspberry", "Strawberry", "Watermelon");

    private static final List<String> EMAIL_DOMAINS = List.of(
            "gmail.com", "googlemail.com", "outlook.com", "hotmail.com",
            "yahoo.com", "icloud.com", "protonmail.com", "qq.com", "163.com");

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        RXAutoCompleteField fruit = new RXAutoCompleteField();
        fruit.setPromptText("Start typing a fruit");
        fruit.getSuggestions().setAll(FRUITS);

        RXAutoCompleteField email = new RXAutoCompleteField();
        email.setPromptText("you@example.com");
        email.getSuggestions().setAll(EMAIL_DOMAINS);
        // Custom filter: only suggest once an '@' is typed, matching the domain part.
        email.setFilterFactory(query -> {
            int at = query.lastIndexOf('@');
            if (at < 0) {
                return domain -> false;
            }
            String typedDomain = query.substring(at + 1).toLowerCase(Locale.ROOT);
            return domain -> domain.toLowerCase(Locale.ROOT).startsWith(typedDomain);
        });
        // Custom write-back strategy: keep the local part, replace the domain.
        email.setCompletionHandler(domain -> {
            String text = email.getText() == null ? "" : email.getText();
            int at = text.lastIndexOf('@');
            String local = at < 0 ? text : text.substring(0, at);
            String full = local + "@" + domain;
            email.setText(full);
            email.positionCaret(full.length());
        });

        root.getChildren().setAll(
                new Label("Fruit (default substring filter)"), fruit,
                new Label("Email (custom domain completion after @)"), email
        );

        Scene scene = new Scene(root, 480, 260);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXAutoCompleteField Demo");
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
