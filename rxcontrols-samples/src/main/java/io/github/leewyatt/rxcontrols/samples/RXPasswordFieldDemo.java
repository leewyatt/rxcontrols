package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Sample application demonstrating {@link RXPasswordField} — masked text
 * entry with reveal toggle, custom echo character, and optional left/right
 * slots.
 */
public class RXPasswordFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        // 1) Plain — default mask, no slots.
        RXPasswordField plain = new RXPasswordField();
        plain.setPromptText("Plain RXPasswordField");
        plain.setPrefWidth(360);

        // 2) Reveal toggle in the right slot.
        RXPasswordField reveal = new RXPasswordField("hunter2");
        reveal.setPrefWidth(360);
        ToggleButton eye = new ToggleButton("Show");
        eye.setFocusTraversable(false);
        eye.selectedProperty().addListener((obs, oldV, newV) -> eye.setText(newV ? "Hide" : "Show"));
        reveal.showPasswordProperty().bind(eye.selectedProperty());
        reveal.setRight(eye);

        // 3) Custom echo character via API.
        RXPasswordField asterisk = new RXPasswordField("asterisk-mask");
        asterisk.setPrefWidth(360);
        asterisk.setEchoChar('*');

        // 4) Left lock icon + reveal toggle on the right.
        RXPasswordField full = new RXPasswordField("secret");
        full.setPrefWidth(360);
        full.setLeft(lockIcon());
        ToggleButton eye2 = new ToggleButton("Show");
        eye2.setFocusTraversable(false);
        eye2.selectedProperty().addListener((obs, oldV, newV) -> eye2.setText(newV ? "Hide" : "Show"));
        full.showPasswordProperty().bind(eye2.selectedProperty());
        full.setRight(eye2);

        // 5) CSS-driven echo character (rx-password-field-demo.css overrides
        //    `-rx-echo-char`).
        RXPasswordField cssEcho = new RXPasswordField("css-styled");
        cssEcho.setPrefWidth(360);
        cssEcho.getStyleClass().add("css-echo-demo");

        // 6) Reference — JavaFX plain PasswordField to compare layout.
        PasswordField reference = new PasswordField();
        reference.setText("hunter2");
        reference.setPrefWidth(360);

        root.getChildren().addAll(
                new Label("Plain"), plain,
                new Label("Reveal toggle (right slot bound to showPassword)"), reveal,
                new Label("Custom echo char via setEchoChar('*')"), asterisk,
                new Label("Left lock icon + reveal toggle"), full,
                new Label("CSS-driven -rx-echo-char"), cssEcho,
                new Label("Reference — JavaFX PasswordField"), reference
        );

        Scene scene = new Scene(root, 500, 660);
        scene.getStylesheets().add(getClass().getResource("rx-password-field-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXPasswordField Demo");
        primaryStage.show();
    }

    private static StackPane lockIcon() {
        SVGPath path = new SVGPath();
        path.setContent("M8 1a3 3 0 0 0-3 3v3H4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1h-1V4a3 3 0 0 0-3-3zm2 6H6V4a2 2 0 1 1 4 0v3z");
        path.setFill(Color.web("#6c757d"));
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(20);
        return icon;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
