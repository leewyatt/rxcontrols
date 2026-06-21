package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.enums.RXFieldVariant;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sample sign-up form demonstrating {@link RXMaterialTextField} and
 * {@link RXMaterialPasswordField}: floating labels, a leading icon, supporting
 * helper text, the built-in clear / reveal affordances, the FILLED variant, and
 * app-driven display-only validation (set {@code invalid} + {@code errorText} on
 * blur). Icons are shape-backed {@code Region}s, not text glyphs.
 */
public class RXMaterialTextFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label heading = new Label("Create your account");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        RXMaterialTextField name = new RXMaterialTextField();
        name.setLabelText("Full name");
        name.setHelperText("As it appears on your ID");
        name.setLeadingNode(icon(PERSON_SHAPE));

        RXMaterialTextField email = new RXMaterialTextField();
        email.setLabelText("Email");
        email.setHelperText("We'll send a confirmation link");
        email.setLeadingNode(icon(MAIL_SHAPE));
        // Display-only validation, app-driven (the control bundles no validator):
        // flag an invalid email on blur, clear the flag once it looks valid.
        email.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                boolean ok = email.getText().contains("@");
                email.setInvalid(!ok);
                email.setErrorText(ok ? "" : "Enter a valid email address");
            }
        });

        RXMaterialPasswordField password = new RXMaterialPasswordField();
        password.setLabelText("Password");
        password.setHelperText("At least 8 characters");

        RXMaterialTextField filled = new RXMaterialTextField();
        filled.setLabelText("Display name (filled)");
        filled.setVariant(RXFieldVariant.FILLED);

        Button submit = new Button("Sign up");
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(20.0, heading, name, email, password, filled, submit);
        form.setAlignment(Pos.TOP_LEFT);
        form.setStyle("-fx-padding: 32; -fx-background-color: -fx-background;");
        form.setFillWidth(true);

        primaryStage.setScene(new Scene(form, 380, 460));
        primaryStage.setTitle("RXMaterialTextField Demo");
        primaryStage.show();
    }

    // Material icons (24x24 viewBox), rendered as shape-backed Regions.
    private static final String PERSON_SHAPE =
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 "
                    + "0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    private static final String MAIL_SHAPE =
            "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 "
                    + "4l-8 5-8-5V6l8 5 8-5v2z";

    private static Region icon(String shape) {
        Region region = new Region();
        region.setStyle("-fx-background-color: -rx-on-surface-secondary; -fx-shape: \"" + shape + "\";"
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
