package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.RXChipInput.CustomInputPolicy;
import io.github.leewyatt.rxcontrols.samples.showcase.RXChipInputShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Minimal demo for {@link RXChipInput}.
 *
 * <p>An email "To:" field: type a recipient and press Enter (or comma) to turn it
 * into a removable chip, with autocomplete from a small address book. Because the
 * policy is {@link CustomInputPolicy#FREE} a hand-typed address commits even when it
 * is not in the suggestions. "Send" reads back the recipient list. For the full
 * property explorer see {@link RXChipInputShowcase}.</p>
 */
public class RXChipInputDemo extends Application {

    private static final List<String> ADDRESS_BOOK = List.of(
            "ada@example.com", "grace@example.com", "alan@example.com",
            "linus@example.com", "margaret@example.com", "dennis@example.com");

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        Label to = new Label("To:");
        to.setStyle("-fx-font-weight: bold; -fx-text-fill: #52606d;");

        RXChipInput<String> recipients = new RXChipInput<>();
        recipients.getSuggestions().setAll(ADDRESS_BOOK);
        recipients.setPromptText("Add a recipient…");
        recipients.setCustomInputPolicy(CustomInputPolicy.FREE);
        recipients.getSeparatorKeys().add(KeyCode.COMMA);
        HBox.setHgrow(recipients, Priority.ALWAYS);

        HBox field = new HBox(12.0, to, recipients);
        field.setAlignment(Pos.TOP_LEFT);

        Label status = new Label("No recipients yet.");
        status.setStyle("-fx-text-fill: #52606d;");
        status.setWrapText(true);

        Button send = new Button("Send");
        send.setDefaultButton(false);
        send.setOnAction(event -> {
            if (recipients.getChips().isEmpty()) {
                status.setText("Add at least one recipient.");
            } else {
                status.setText("Sending to " + recipients.getChips().size() + ": "
                        + String.join(", ", recipients.getChips()));
            }
        });

        VBox root = new VBox(18.0, field, send, status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36.0, 48.0, 36.0, 48.0));
        root.setPrefWidth(520.0);

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXChipInput Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
