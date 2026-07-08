package io.github.leewyatt.rxcontrols.samples.demo;

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

    private static final String EYE_OPEN_PATH =
            "M16 8s-3-5.5-8-5.5S0 8 0 8s3 5.5 8 5.5S16 8 16 8zM1.173 8a13.133 13.133 0 0 1 1.66-2.043C4.12 4.668 5.88 3.5 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.133 13.133 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755C11.879 11.332 10.119 12.5 8 12.5c-2.12 0-3.879-1.168-5.168-2.457A13.134 13.134 0 0 1 1.172 8zM8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM4.5 8a3.5 3.5 0 1 1 7 0 3.5 3.5 0 0 1-7 0z";

    private static final String EYE_SLASH_PATH =
            "M13.359 11.238C15.06 9.72 16 8 16 8s-3-5.5-8-5.5a7.028 7.028 0 0 0-2.79.588l.77.771A5.944 5.944 0 0 1 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.134 13.134 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755-.165.165-.337.328-.517.486l.708.709zM11.297 9.176a3.5 3.5 0 0 0-4.474-4.474l.823.823a2.5 2.5 0 0 1 2.829 2.829l.822.822zm-2.943 1.299.822.822a3.5 3.5 0 0 1-4.474-4.474l.823.823a2.5 2.5 0 0 0 2.829 2.829zM3.35 5.47c-.18.16-.353.322-.518.487A13.134 13.134 0 0 0 1.172 8l.195.288c.335.48.83 1.12 1.465 1.755C4.121 11.332 5.881 12.5 8 12.5c.716 0 1.39-.133 2.02-.36l.77.772A7.029 7.029 0 0 1 8 13.5C3 13.5 0 8 0 8s.939-1.721 2.641-3.238l.708.709zM13.646 14.354l-12-12 .708-.708 12 12-.708.708z";

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        RXPasswordField plain = new RXPasswordField();
        plain.setPromptText("Plain RXPasswordField");
        plain.setPrefWidth(360);

        RXPasswordField reveal = new RXPasswordField("hunter2");
        reveal.setPrefWidth(360);
        ToggleButton eye = eyeToggle();
        reveal.revealPasswordProperty().bind(eye.selectedProperty());
        reveal.setRight(eye);

        RXPasswordField asterisk = new RXPasswordField("asterisk-mask");
        asterisk.setPrefWidth(360);
        asterisk.setEchoChar('☆');

        RXPasswordField full = new RXPasswordField("secret");
        full.setPrefWidth(360);
        full.getStyleClass().add("lock-field");
        full.setLeft(lockIcon());
        ToggleButton eye2 = eyeToggle();
        full.revealPasswordProperty().bind(eye2.selectedProperty());
        full.setRight(eye2);

        RXPasswordField cssEcho = new RXPasswordField("css-styled");
        cssEcho.setPrefWidth(360);
        cssEcho.getStyleClass().add("css-echo-demo");

        PasswordField reference = new PasswordField();
        reference.setText("hunter2");
        reference.setPrefWidth(360);

        root.getChildren().addAll(
                new Label("Plain"), plain,
                new Label("Reveal toggle (right slot bound to revealPassword)"), reveal,
                new Label("Custom echo char via setEchoChar('☆')"), asterisk,
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

    private static ToggleButton eyeToggle() {
        ToggleButton btn = new ToggleButton();
        btn.setFocusTraversable(false);
        btn.getStyleClass().add("eye-toggle");
        btn.setGraphic(eyeGraphic(false));
        btn.selectedProperty().addListener((obs, oldV, newV) ->
                btn.setGraphic(eyeGraphic(Boolean.TRUE.equals(newV))));
        return btn;
    }

    private static SVGPath eyeGraphic(boolean revealing) {
        SVGPath path = new SVGPath();
        path.setContent(revealing ? EYE_OPEN_PATH : EYE_SLASH_PATH);
        path.setFill(Color.web("#495057"));
        return path;
    }

    private static StackPane lockIcon() {
        SVGPath path = new SVGPath();
        path.setContent("M8 1a3 3 0 0 0-3 3v3H4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1h-1V4a3 3 0 0 0-3-3zm2 6H6V4a2 2 0 1 1 4 0v3z");
        path.setFill(Color.web("#6c757d"));
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(16);
        return icon;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
