package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Sample "set a new password" form demonstrating {@link RXMaterialPasswordField}:
 * the floating label, the built-in reveal (eye) toggle and clear button, a
 * leading lock icon, supporting helper text, and app-driven display-only
 * validation (the confirm field flags {@code invalid} + {@code errorText} on
 * blur when the two entries differ). Icons are shape-backed {@code Region}s.
 */
public class RXMaterialPasswordFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label heading = new Label("Set a new password");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        RXMaterialPasswordField password = new RXMaterialPasswordField();
        password.setLabelText("New password");
        password.setHelperText("At least 8 characters");
        password.setLeadingNode(icon());

        RXMaterialPasswordField confirm = new RXMaterialPasswordField();
        confirm.setLabelText("Confirm password");
        confirm.setHelperText("Re-enter the password");
        confirm.setLeadingNode(icon());
        // Display-only validation, app-driven: flag a mismatch on blur.
        confirm.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                boolean match = Objects.equals(confirm.getText(), password.getText());
                confirm.setInvalid(!match);
                confirm.setErrorText(match ? "" : "Passwords do not match");
            }
        });

        Button submit = new Button("Update password");
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(20.0, heading, password, confirm, submit);
        form.setAlignment(Pos.TOP_LEFT);
        form.setStyle("-fx-padding: 32; -fx-background-color: -fx-background;");
        form.setFillWidth(true);

        primaryStage.setScene(new Scene(form, 380, 360));
        primaryStage.setTitle("RXMaterialPasswordField Demo");
        primaryStage.show();
    }

    // Material lock icon (24x24 viewBox), rendered as a shape-backed Region.
    private static final String LOCK_SHAPE =
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 "
                    + "2-.9 2-2V10c0-1.1-.9-2-2-2zM12 17c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0"
                    + "-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z";

    private static Region icon() {
        Region region = new Region();
        region.setStyle("-fx-background-color: -rx-on-surface-secondary; -fx-shape: \"" + LOCK_SHAPE + "\";"
                + " -fx-pref-width: 18px; -fx-pref-height: 18px;");
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
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
