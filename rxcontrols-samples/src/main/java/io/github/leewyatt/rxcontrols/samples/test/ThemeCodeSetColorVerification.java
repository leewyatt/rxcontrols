package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.theme.RXTheme;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manual check for colors set in code under the RxControls dark theme. On JavaFX 17–23 such a
 * color can be reset to the theme color once a default control of the same kind has filled the
 * shared CSS style cache; on JavaFX 24 and later every case keeps red.
 */
public class ThemeCodeSetColorVerification extends Application {

    private static final String DARK_ROOT_CLASS = "demo-dark";
    private static final double CHANNEL_MAX = 255.0;
    private static final double CASE_WIDTH = 300.0;

    private Stage stage;
    private boolean dark = true;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("Code-set colors under RXTheme");
        rebuild();
        stage.show();
    }

    // ==================== Window ====================

    private void rebuild() {
        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildCases());
        Scene scene = new Scene(root, 1000, 760);
        scene.getStylesheets().add(getClass().getResource("theme-code-set-color-verification.css").toExternalForm());
        applyTheme(scene);
        stage.setScene(scene);
    }

    private Node buildToolbar() {
        Label version = new Label(String.format(Locale.ROOT, "JavaFX %s  ·  Java %s",
                System.getProperty("javafx.runtime.version"), System.getProperty("java.version")));
        version.getStyleClass().add("case-title");

        Button darkButton = new Button("RX Dark");
        darkButton.setOnAction(event -> {
            dark = true;
            applyTheme(stage.getScene());
        });
        Button lightButton = new Button("RX Light");
        lightButton.setOnAction(event -> {
            dark = false;
            applyTheme(stage.getScene());
        });
        Button rebuildButton = new Button("Rebuild (new scene)");
        rebuildButton.setOnAction(event -> rebuild());

        HBox buttons = new HBox(8, version, darkButton, lightButton, rebuildButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Switching themes re-runs CSS from scratch: JavaFX first restores the colors set "
                + "in code, then every case is decided again. Rebuild starts over with fresh controls.");
        hint.getStyleClass().add("case-hint");

        VBox toolbar = new VBox(6, buttons, hint);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private void applyTheme(Scene scene) {
        RXTheme.install(scene, dark ? RXTheme.Variant.DARK : RXTheme.Variant.LIGHT);
        List<String> classes = scene.getRoot().getStyleClass();
        classes.remove(DARK_ROOT_CLASS);
        if (dark) {
            classes.add(DARK_ROOT_CLASS);
        }
    }

    private Node buildCases() {
        FlowPane cases = new FlowPane(12, 12,
                caseDefaultFirst(),
                caseCustomFirst(),
                caseInlineStyle(),
                caseOwnStyleClass(),
                caseDigitClock(),
                caseHighlightResults());
        cases.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(cases);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // ==================== Cases ====================

    private Node caseDefaultFirst() {
        RXCheckBox accept = new RXCheckBox("Accept terms");
        RXCheckBox delete = new RXCheckBox("Delete all data");
        delete.setTextFill(Color.RED);
        return casePane(1, "Default control first",
                "\"Delete all data\" is set to red with setTextFill. "
                        + "Under RX Dark: JavaFX 17–23 reset at once, JavaFX 24+ red.",
                new VBox(6, accept, delete),
                status("Delete all data", delete.textFillProperty()));
    }

    private Node caseCustomFirst() {
        RXCheckBox delete = new RXCheckBox("Delete all data");
        delete.setTextFill(Color.RED);
        RXCheckBox accept = new RXCheckBox("Accept terms");
        VBox boxes = new VBox(6, delete, accept);

        Button readd = new Button("Remove and add back");
        readd.setOnAction(event -> {
            boxes.getChildren().remove(delete);
            boxes.getChildren().add(0, delete);
        });
        Button resetClasses = new Button("Re-set the same style classes");
        resetClasses.setOnAction(event -> delete.getStyleClass().setAll(new ArrayList<>(delete.getStyleClass())));

        return casePane(2, "Red control first",
                "Starts red. Under RX Dark on JavaFX 17–23 it is reset after any of: check \"Accept terms\" and then "
                        + "\"Delete all data\"; hover \"Accept terms\" and then \"Delete all data\"; either button. "
                        + "Checking only \"Delete all data\" keeps red.",
                boxes,
                status("Delete all data", delete.textFillProperty()),
                new HBox(6, readd, resetClasses));
    }

    private Node caseInlineStyle() {
        RXCheckBox accept = new RXCheckBox("Accept terms");
        RXCheckBox delete = new RXCheckBox("Delete all data");
        delete.setStyle("-fx-text-fill: red;");
        return casePane(3, "Inline style",
                "Default control first; red comes from setStyle(\"-fx-text-fill: red;\"). "
                        + "Red on every version, whatever you check or hover.",
                new VBox(6, accept, delete),
                status("Delete all data", delete.textFillProperty()));
    }

    private Node caseOwnStyleClass() {
        RXCheckBox accept = new RXCheckBox("Accept terms");
        RXCheckBox delete = new RXCheckBox("Delete all data");
        delete.setTextFill(Color.RED);
        delete.getStyleClass().add("danger-check");
        return casePane(4, "Own style class",
                "Default control first; the red one also carries the style class \"danger-check\". "
                        + "Red on every version.",
                new VBox(6, accept, delete),
                status("Delete all data", delete.textFillProperty()));
    }

    private Node caseDigitClock() {
        RXDigit first = digit(1);
        RXDigit second = digit(2);
        RXDigit third = digit(3);
        third.setLitFill(Color.RED);
        return casePane(5, "RXDigit clock",
                "The third digit is set to red with setLitFill. Under RX Dark: JavaFX 17–23 reset, JavaFX 24+ red.",
                new HBox(6, first, second, third),
                status("Third digit", third.litFillProperty()));
    }

    private Node caseHighlightResults() {
        RXHighlightTextView first = result("JavaFX search result one");
        RXHighlightTextView second = result("JavaFX search result two");
        second.setHighlightFill(Color.RED);
        return casePane(6, "RXHighlightTextView results",
                "The second result sets its highlight to red with setHighlightFill. "
                        + "Under RX Dark: JavaFX 17–23 reset, JavaFX 24+ red.",
                new VBox(6, first, second),
                status("Second highlight", second.highlightFillProperty()));
    }

    // ==================== Helpers ====================

    private static VBox casePane(int number, String title, String description, Node... content) {
        Label titleLabel = new Label(number + ". " + title);
        titleLabel.getStyleClass().add("case-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("case-hint");
        descriptionLabel.setWrapText(true);

        VBox pane = new VBox(8, titleLabel, descriptionLabel);
        pane.getChildren().addAll(content);
        // A class per case keeps the cases from sharing one CSS style cache.
        pane.getStyleClass().addAll("case", "case-" + number);
        pane.setPrefWidth(CASE_WIDTH);
        return pane;
    }

    private static Label status(String name, ObservableValue<Paint> fill) {
        Label label = new Label();
        label.getStyleClass().add("status");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> name + ": " + describe(fill.getValue()), fill));
        return label;
    }

    private static String describe(Paint paint) {
        if (Color.RED.equals(paint)) {
            return "✔ red, kept";
        }
        return "✘ reset to " + hex(paint);
    }

    private static String hex(Paint paint) {
        if (!(paint instanceof Color color)) {
            return String.valueOf(paint);
        }
        String rgb = String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(color.getRed() * CHANNEL_MAX),
                Math.round(color.getGreen() * CHANNEL_MAX),
                Math.round(color.getBlue() * CHANNEL_MAX));
        if (color.getOpacity() < 1.0) {
            return rgb + String.format(Locale.ROOT, " @ %.2f", color.getOpacity());
        }
        return rgb;
    }

    private static RXDigit digit(int value) {
        RXDigit digit = new RXDigit(value);
        digit.setPrefSize(30, 60);
        return digit;
    }

    private static RXHighlightTextView result(String text) {
        RXHighlightTextView view = new RXHighlightTextView(text);
        view.getKeywords().setAll("JavaFX");
        return view;
    }

    /**
     * Launches the verification window.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
