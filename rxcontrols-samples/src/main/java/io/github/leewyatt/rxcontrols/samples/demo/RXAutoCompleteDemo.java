package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAutoComplete;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;

/**
 * Minimal sample application for {@link RXAutoComplete}.
 *
 * <p>Shows two lightweight, realistic scenarios: a country field driven by the
 * default case-insensitive substring filter, and an email field with a custom
 * filter + write-back that completes the domain after {@code @}.
 *
 * <p>For the property explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXAutoCompleteShowcase}.
 */
public class RXAutoCompleteDemo extends Application {

    private static final List<String> COUNTRIES = List.of(
            "Argentina", "Australia", "Austria", "Belgium", "Brazil", "Canada",
            "Chile", "China", "Denmark", "Egypt", "Finland", "France", "Germany",
            "Greece", "India", "Indonesia", "Ireland", "Italy", "Japan", "Mexico",
            "Netherlands", "New Zealand", "Norway", "Poland", "Portugal", "Singapore",
            "South Korea", "Spain", "Sweden", "Switzerland", "Thailand", "Turkey",
            "United Kingdom", "United States", "Vietnam");

    private static final List<String> EMAIL_DOMAINS = List.of(
            "gmail.com", "googlemail.com", "outlook.com", "hotmail.com",
            "yahoo.com", "icloud.com", "protonmail.com", "qq.com", "163.com");

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        RXAutoComplete country = new RXAutoComplete();
        country.setPromptText("Start typing a country");
        country.getSuggestions().setAll(COUNTRIES);

        RXAutoComplete email = new RXAutoComplete();
        email.setPromptText("you@example.com");
        email.getSuggestions().setAll(EMAIL_DOMAINS);
        // Custom filter: only suggest once an '@' is typed, matching the domain part.
        email.setFilterFunction(query -> {
            int at = query.lastIndexOf('@');
            if (at < 0) {
                return domain -> false;
            }
            String typedDomain = query.substring(at + 1).toLowerCase(Locale.ROOT);
            return domain -> domain.toLowerCase(Locale.ROOT).startsWith(typedDomain);
        });
        // Custom write-back: keep the local part, replace the domain with the choice.
        email.setOnAutoCompleted(domain -> {
            String text = email.getText() == null ? "" : email.getText();
            int at = text.lastIndexOf('@');
            String local = at < 0 ? text : text.substring(0, at);
            String full = local + "@" + domain;
            email.setText(full);
            email.positionCaret(full.length());
        });

        root.getChildren().setAll(
                new Label("Country (default substring filter)"), country,
                new Label("Email (custom domain completion after @)"), email
        );

        Scene scene = new Scene(root, 480, 260);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXAutoComplete Demo");
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
