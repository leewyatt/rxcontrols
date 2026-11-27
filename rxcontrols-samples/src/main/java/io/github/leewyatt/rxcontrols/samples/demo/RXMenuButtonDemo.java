package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMenuButtonShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal demo for the command-menu family.
 *
 * <p>Two everyday scenarios: an anchored account menu opened from an
 * {@link RXMenuButton} (leading icons + separator + trailing shortcuts +
 * a danger "Sign out"), and a right-click context menu installed on a card
 * via {@link RXMenuButton#installContextMenu} that opens at
 * the cursor. For the full property explorer see {@link RXMenuButtonShowcase}.</p>
 */
public class RXMenuButtonDemo extends Application {

    private static final String PERSON = "M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-4 0-9 2-9 6v2h18v-2c0-4-5-6-9-6z";
    private static final String GEAR = "M19.14 12.94a7.5 7.5 0 0 0 0-1.88l2-1.56-2-3.46-2.4 1a7 7 0 0 0-1.62-.94L14.7 3h-4l-.42 2.6a7 7 0 0 0-1.62.94l-2.4-1-2 3.46 2 1.56a7.5 7.5 0 0 0 0 1.88l-2 1.56 2 3.46 2.4-1a7 7 0 0 0 1.62.94L10.3 21h4l.42-2.6a7 7 0 0 0 1.62-.94l2.4 1 2-3.46zM12 15.5A3.5 3.5 0 1 1 15.5 12 3.5 3.5 0 0 1 12 15.5z";
    private static final String LOGOUT = "M17 8l-1.4 1.4L17.2 11H9v2h8.2l-1.6 1.6L17 16l4-4zM5 5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7v-2H5z";
    private static final String CUT = "M9.6 8.2A3 3 0 1 0 6 11l3 1.5-3 1.5A3 3 0 1 0 9.6 15.8L14 13l6 3v-1l-4.5-3L20 9V8l-6 3zM6 9a1 1 0 1 1 1-1 1 1 0 0 1-1 1zm0 8a1 1 0 1 1 1-1 1 1 0 0 1-1 1z";
    private static final String COPY = "M16 1H4a2 2 0 0 0-2 2v12h2V3h12zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11z";
    private static final String PASTE = "M19 2h-4.18A3 3 0 0 0 9.18 2H5a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm-7 0a1 1 0 1 1-1 1 1 1 0 0 1 1-1zm7 18H5V4h2v3h10V4h2z";

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        Label status = new Label("Pick a command…");
        status.setStyle("-fx-text-fill: #52606d;");

        VBox root = new VBox(20.0, buildAccountMenu(status), buildContextCard(status), status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36.0, 48.0, 36.0, 48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXMenu Demo");
        primaryStage.show();
    }

    // ==================== Account menu ====================

    private RXMenuButton buildAccountMenu(Label status) {
        RXMenuButton account = new RXMenuButton("Ada Lovelace", icon(PERSON));

        RXMenuItem profile = RXMenuItem.of("Profile", icon(PERSON));
        profile.setAccelerator(KeyCombination.keyCombination("Shortcut+P"));
        profile.setOnAction(e -> status.setText("Open profile"));

        RXMenuItem settings = RXMenuItem.of("Settings", icon(GEAR));
        settings.setAccelerator(KeyCombination.keyCombination("Shortcut+,"));
        settings.setOnAction(e -> status.setText("Open settings"));

        RXMenuItem signOut = RXMenuItem.of("Sign out", icon(LOGOUT));
        signOut.setDanger(true);
        signOut.setOnAction(e -> status.setText("Signed out"));

        account.getItems().addAll(profile, settings, RXMenuItem.separator(), signOut);
        return account;
    }

    // ==================== Context menu ====================

    private StackPane buildContextCard(Label status) {
        Label hint = new Label("Right-click this card");
        hint.setStyle("-fx-text-fill: #486581;");
        StackPane card = new StackPane(hint);
        card.setPrefSize(320.0, 120.0);
        card.setStyle("-fx-background-color: #f0f4f8; -fx-background-radius: 8;"
                + " -fx-border-color: #bcccdc; -fx-border-radius: 8; -fx-border-style: segments(6, 4);");

        RXMenuButton contextTrigger = new RXMenuButton();
        RXMenuItem cut = RXMenuItem.of("Cut", icon(CUT));
        cut.setAccelerator(KeyCombination.keyCombination("Shortcut+X"));
        cut.setOnAction(e -> status.setText("Cut"));
        RXMenuItem copy = RXMenuItem.of("Copy", icon(COPY));
        copy.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        copy.setOnAction(e -> status.setText("Copied"));
        RXMenuItem paste = RXMenuItem.of("Paste", icon(PASTE));
        paste.setAccelerator(KeyCombination.keyCombination("Shortcut+V"));
        paste.setOnAction(e -> status.setText("Pasted"));
        contextTrigger.getItems().addAll(cut, copy, paste);

        // The trigger must be in the scene (it reuses its own popup); keep it
        // invisible but laid out so its window is realized when the request fires.
        contextTrigger.setVisible(false);
        contextTrigger.setManaged(false);
        card.getChildren().add(contextTrigger);
        contextTrigger.installContextMenu(card);
        return card;
    }

    // ==================== Icon helper ====================

    private static Region icon(String svgPath) {
        Region region = new Region();
        region.setStyle("-fx-shape: \"" + svgPath + "\"; -fx-background-color: #486581;"
                + " -fx-min-width: 16; -fx-min-height: 16; -fx-pref-width: 16; -fx-pref-height: 16;"
                + " -fx-max-width: 16; -fx-max-height: 16;");
        return region;
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
