package io.github.leewyatt.rxcontrols.samples.test;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Temporary playground for the native JavaFX {@link Alert} API, including every
 * {@link AlertType}, constructor button variants, custom buttons, custom
 * graphics, content nodes, expandable details, modality, modeless display,
 * lifecycle events, close-request filtering, and live alert-type switching.
 */
public class AlertDemo extends Application {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextArea logArea = new TextArea();

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        logArea.setEditable(false);
        logArea.setWrapText(true);

        Label title = new Label("Native Alert API Demo");
        Label hint = new Label("Click a button to open one native JavaFX Alert variant.");

        VBox header = new VBox(8.0, title, hint, createButtons(primaryStage));
        header.setPadding(new Insets(12.0));

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(logArea);
        BorderPane.setMargin(logArea, new Insets(0.0, 12.0, 12.0, 12.0));

        primaryStage.setScene(new Scene(root, 940.0, 620.0));
        primaryStage.setTitle("AlertDemo");
        primaryStage.show();

        log("Ready. The first row covers all native AlertType values.");
    }

    private FlowPane createButtons(Stage owner) {
        FlowPane buttons = new FlowPane(8.0, 8.0);
        buttons.getChildren().addAll(
                button("AlertType.NONE", () -> showTypedAlert(owner, AlertType.NONE)),
                button("AlertType.INFORMATION", () -> showTypedAlert(owner, AlertType.INFORMATION)),
                button("AlertType.WARNING", () -> showTypedAlert(owner, AlertType.WARNING)),
                button("AlertType.CONFIRMATION", () -> showTypedAlert(owner, AlertType.CONFIRMATION)),
                button("AlertType.ERROR", () -> showTypedAlert(owner, AlertType.ERROR)),
                button("Constructor varargs", () -> showConstructorAlert(owner)),
                button("Custom buttons", () -> showCustomButtonsAlert(owner)),
                button("Delete gate", () -> showButtonLookupAlert(owner)),
                button("Custom graphic", () -> showCustomGraphicAlert(owner)),
                button("Content Node", () -> showContentNodeAlert(owner)),
                button("Expandable error", () -> showExpandableErrorAlert(owner)),
                button("Header/graphic null", () -> showNoHeaderAlert(owner)),
                button("Modeless show()", () -> showModelessAlert(owner)),
                button("Window modal", () -> showWindowModalAlert(owner)),
                button("Close request veto", () -> showCloseRequestAlert(owner)),
                button("Live AlertType switch", () -> showAlertTypeSwitcher(owner))
        );
        return buttons;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showTypedAlert(Stage owner, AlertType type) {
        Alert alert = new Alert(type);
        initAlert(alert, owner, "AlertType." + type.name());
        alert.setHeaderText(type.name() + " header");
        alert.setContentText("This alert was created with new Alert(AlertType." + type.name() + ").");

        if (type == AlertType.NONE) {
            alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        }

        registerLifecycle(alert, "AlertType." + type.name());
        Optional<ButtonType> result = alert.showAndWait();
        logResult("AlertType." + type.name(), result);
    }

    private void showConstructorAlert(Stage owner) {
        Alert alert = new Alert(AlertType.CONFIRMATION,
                "The content text and button list were supplied through the Alert constructor.",
                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        initAlert(alert, owner, "Alert Constructor");
        alert.setHeaderText("Alert(AlertType, String, ButtonType...)");

        registerLifecycle(alert, "Constructor Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Constructor Alert", result);
    }

    private void showCustomButtonsAlert(Stage owner) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        initAlert(alert, owner, "Custom Button Alert");
        alert.setHeaderText("Custom ButtonType values with ButtonData");
        alert.setContentText("Each button reports its semantic ButtonData.");

        ButtonType archive = new ButtonType("Archive", ButtonData.OK_DONE);
        ButtonType skip = new ButtonType("Skip", ButtonData.NEXT_FORWARD);
        ButtonType help = new ButtonType("Help", ButtonData.HELP);
        alert.getButtonTypes().setAll(help, archive, skip, ButtonType.CANCEL);

        registerLifecycle(alert, "Custom Buttons Alert");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            ButtonType buttonType = result.get();
            log("Custom Buttons Alert result = "
                    + buttonType.getText() + " / " + buttonType.getButtonData());
        } else {
            log("Custom Buttons Alert result = empty");
        }
    }

    private void showButtonLookupAlert(Stage owner) {
        Alert alert = new Alert(AlertType.WARNING);
        initAlert(alert, owner, "Button Lookup Alert");

        CheckBox confirmed = new CheckBox("I understand this cannot be undone");
        DialogPane pane = alert.getDialogPane();
        pane.setHeaderText("lookupButton(ButtonType) can customize a native button");
        pane.setContent(new VBox(8.0,
                new Label("The Delete button is disabled until the checkbox is selected."),
                confirmed));

        ButtonType delete = new ButtonType("Delete", ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(delete, ButtonType.CANCEL);
        pane.lookupButton(delete).disableProperty().bind(confirmed.selectedProperty().not());

        registerLifecycle(alert, "Button Lookup Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Button Lookup Alert", result);
    }

    private void showCustomGraphicAlert(Stage owner) {
        Alert alert = new Alert(AlertType.INFORMATION);
        initAlert(alert, owner, "Custom Graphic Alert");
        alert.setHeaderText("Alert.setGraphic(Node)");
        alert.setContentText("The default AlertType graphic is replaced by a custom Node.");
        alert.setGraphic(createGraphic(Color.MEDIUMPURPLE));

        registerLifecycle(alert, "Custom Graphic Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Custom Graphic Alert", result);
    }

    private void showContentNodeAlert(Stage owner) {
        Alert alert = new Alert(AlertType.INFORMATION);
        initAlert(alert, owner, "Content Node Alert");
        alert.setHeaderText("DialogPane.setContent(Node)");

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.addRow(0, new Label("Field"), new Label("Value"));
        grid.addRow(1, new Label("Alert type"), new Label(alert.getAlertType().name()));
        grid.addRow(2, new Label("Resizable"), new Label(Boolean.toString(alert.isResizable())));

        alert.getDialogPane().setContent(grid);

        registerLifecycle(alert, "Content Node Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Content Node Alert", result);
    }

    private void showExpandableErrorAlert(Stage owner) {
        Alert alert = new Alert(AlertType.ERROR);
        initAlert(alert, owner, "Expandable Error Alert");
        alert.setHeaderText("Operation failed");
        alert.setContentText("Open Details to inspect the expandable content node.");
        alert.setResizable(true);

        TextArea details = new TextArea("""
                java.lang.IllegalStateException: Simulated failure
                    at demo.NativeApiProbe.run(NativeApiProbe.java:42)
                    at demo.AlertDemo.showExpandableErrorAlert(AlertDemo.java:1)

                DialogPane expandable content is a normal Node, so it can be
                a TextArea, TreeView, TableView, or any custom content.
                """);
        details.setEditable(false);
        details.setPrefColumnCount(72);
        details.setPrefRowCount(10);

        DialogPane pane = alert.getDialogPane();
        pane.setExpandableContent(details);
        pane.setExpanded(false);

        registerLifecycle(alert, "Expandable Error Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Expandable Error Alert", result);
    }

    private void showNoHeaderAlert(Stage owner) {
        Alert alert = new Alert(AlertType.INFORMATION);
        initAlert(alert, owner, "Content-only Alert");
        alert.setHeaderText(null);
        alert.setGraphic(null);
        alert.setContentText("Header text and graphic are both null, so the alert uses a compact content-only layout.");

        registerLifecycle(alert, "Content-only Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Content-only Alert", result);
    }

    private void showModelessAlert(Stage owner) {
        Alert alert = new Alert(AlertType.WARNING);
        initAlert(alert, owner, "Modeless Alert");
        alert.initModality(Modality.NONE);
        alert.setHeaderText("Modeless alert opened with show()");
        alert.setContentText("The owner window remains interactive while this alert is open.");

        registerLifecycle(alert, "Modeless Alert",
                () -> log("Modeless Alert final result = " + alert.getResult()));
        alert.show();
        log("Modeless Alert show() returned immediately.");
    }

    private void showWindowModalAlert(Stage owner) {
        Alert alert = new Alert(AlertType.INFORMATION);
        initAlert(alert, owner, "Window Modal Alert");
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setHeaderText("Window-modal alert");
        alert.setContentText("This alert is owned by the primary stage and blocks only that window.");

        registerLifecycle(alert, "Window Modal Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Window Modal Alert", result);
    }

    private void showCloseRequestAlert(Stage owner) {
        Alert alert = new Alert(AlertType.WARNING);
        initAlert(alert, owner, "Close Request Alert");

        CheckBox allowClose = new CheckBox("Allow this alert to close");
        alert.setHeaderText("DIALOG_CLOSE_REQUEST can be consumed");
        alert.getDialogPane().setContent(new VBox(8.0,
                new Label("Try the window close button before enabling the checkbox."),
                allowClose));
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.getDialogPane().lookupButton(ButtonType.CLOSE)
                .disableProperty().bind(allowClose.selectedProperty().not());

        alert.setOnCloseRequest(event -> {
            if (!allowClose.isSelected()) {
                event.consume();
                log("Close Request Alert consumed DIALOG_CLOSE_REQUEST.");
            }
        });

        registerLifecycle(alert, "Close Request Alert");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("Close Request Alert", result);
    }

    private void showAlertTypeSwitcher(Stage owner) {
        Alert alert = new Alert(AlertType.INFORMATION);
        initAlert(alert, owner, "Live AlertType Switcher");
        alert.setHeaderText("Change AlertType while the alert is open");
        alert.setContentText("The default graphic and button semantics come from AlertType.");

        ComboBox<AlertType> typeBox = new ComboBox<>(FXCollections.observableArrayList(AlertType.values()));
        typeBox.setValue(AlertType.INFORMATION);
        Label current = new Label("Current type: INFORMATION");
        typeBox.valueProperty().addListener((observable, oldType, newType) -> {
            if (newType != null) {
                alert.setAlertType(newType);
                alert.setHeaderText("Current AlertType." + newType.name());
                current.setText("Current type: " + newType.name());
                alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            }
        });

        alert.getDialogPane().setContent(new VBox(8.0,
                new Label("Select a type and watch the built-in graphic change."),
                typeBox,
                current));
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        registerLifecycle(alert, "AlertType Switcher");
        Optional<ButtonType> result = alert.showAndWait();
        logResult("AlertType Switcher", result);
    }

    private void initAlert(Alert alert, Stage owner, String title) {
        alert.initOwner(owner);
        alert.setTitle(title);
    }

    private void registerLifecycle(Alert alert, String name) {
        registerLifecycle(alert, name, null);
    }

    private void registerLifecycle(Alert alert, String name, Runnable hiddenAction) {
        alert.setOnShowing(event -> log(name + " event: SHOWING"));
        alert.setOnShown(event -> log(name + " event: SHOWN"));
        alert.setOnHiding(event -> log(name + " event: HIDING"));
        alert.setOnHidden(event -> {
            log(name + " event: HIDDEN");
            if (hiddenAction != null) {
                hiddenAction.run();
            }
        });
    }

    private Node createGraphic(Color color) {
        Circle circle = new Circle(18.0, color);
        circle.setStroke(Color.gray(0.35));
        return new StackPane(circle);
    }

    private void logResult(String label, Optional<ButtonType> result) {
        log(label + " result = " + result.map(ButtonType::getText).orElse("empty"));
    }

    private void log(String message) {
        logArea.appendText(LocalTime.now().format(TIME_FORMATTER)
                + "  " + message + System.lineSeparator());
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
