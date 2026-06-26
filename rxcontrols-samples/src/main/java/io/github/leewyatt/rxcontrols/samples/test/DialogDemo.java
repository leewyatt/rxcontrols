package io.github.leewyatt.rxcontrols.samples.test;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Temporary playground for the native JavaFX {@link Dialog} API, including
 * custom content, graphics, expandable content, result conversion, custom
 * button data, modality, stage style, lifecycle events, {@link TextInputDialog},
 * and {@link ChoiceDialog}.
 */
public class DialogDemo extends Application {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextArea logArea = new TextArea();

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        logArea.setEditable(false);
        logArea.setWrapText(true);

        Label title = new Label("Native Dialog API Demo");
        Label hint = new Label("Click a button to open one native JavaFX Dialog variant.");

        VBox header = new VBox(8.0, title, hint, createButtons(primaryStage));
        header.setPadding(new Insets(12.0));

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(logArea);
        BorderPane.setMargin(logArea, new Insets(0.0, 12.0, 12.0, 12.0));

        primaryStage.setScene(new Scene(root, 940.0, 620.0));
        primaryStage.setTitle("DialogDemo");
        primaryStage.show();

        log("Ready. All dialogs are native javafx.scene.control.Dialog variants.");
    }

    private FlowPane createButtons(Stage owner) {
        FlowPane buttons = new FlowPane(8.0, 8.0);
        buttons.getChildren().addAll(
                button("Basic message", () -> showBasicMessage(owner)),
                button("Graphic + details", () -> showGraphicAndDetails(owner)),
                button("Custom login content", () -> showLoginDialog(owner)),
                button("ButtonData layout", () -> showButtonDataDialog(owner)),
                button("Resizable editor", () -> showResizableEditor(owner)),
                button("Modeless show()", () -> showModelessDialog(owner)),
                button("Window modal", () -> showWindowModalDialog(owner)),
                button("StageStyle.UTILITY", () -> showStageStyleDialog(owner, StageStyle.UTILITY)),
                button("StageStyle.UNDECORATED", () -> showStageStyleDialog(owner, StageStyle.UNDECORATED)),
                button("Close request veto", () -> showCloseRequestDialog(owner)),
                button("TextInputDialog", () -> showTextInputDialog(owner)),
                button("ChoiceDialog", () -> showChoiceDialog(owner))
        );
        return buttons;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showBasicMessage(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog, owner, "Basic Dialog");

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Plain Dialog<ButtonType>");
        pane.setContentText("This uses only title, header text, content text, and one OK button.");
        pane.getButtonTypes().setAll(ButtonType.OK);

        registerLifecycle(dialog, "Basic Dialog");
        Optional<ButtonType> result = dialog.showAndWait();
        logResult("Basic Dialog", result);
    }

    private void showGraphicAndDetails(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog, owner, "Graphic and Expandable Content");

        TextArea details = new TextArea("""
                DialogPane.setExpandableContent(Node)
                DialogPane.setExpanded(boolean)
                DialogPane.setGraphic(Node)
                DialogPane.setHeaderText(String)
                DialogPane.setContentText(String)
                """);
        details.setEditable(false);
        details.setPrefRowCount(6);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("A dialog with a graphic and expandable details");
        pane.setContentText("Open the disclosure area to see a simple API checklist.");
        pane.setGraphic(createGraphic(Color.CORNFLOWERBLUE));
        pane.setExpandableContent(details);
        pane.setExpanded(false);
        pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CLOSE);

        registerLifecycle(dialog, "Graphic Dialog");
        Optional<ButtonType> result = dialog.showAndWait();
        logResult("Graphic Dialog", result);
    }

    private void showLoginDialog(Stage owner) {
        Dialog<LoginResult> dialog = new Dialog<>();
        initDialog(dialog, owner, "Custom Result Dialog");

        TextField userName = new TextField("lee");
        PasswordField password = new PasswordField();
        CheckBox remember = new CheckBox("Remember this account");

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.addRow(0, new Label("User name:"), userName);
        grid.addRow(1, new Label("Password:"), password);
        grid.add(remember, 1, 2);

        ButtonType login = new ButtonType("Login", ButtonData.OK_DONE);
        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Custom content + resultConverter");
        pane.setContent(grid);
        pane.getButtonTypes().setAll(login, ButtonType.CANCEL);

        Node loginButton = pane.lookupButton(login);
        loginButton.disableProperty().bind(userName.textProperty().isEmpty()
                .or(password.textProperty().isEmpty()));

        dialog.setResultConverter(buttonType -> {
            if (buttonType == login) {
                return new LoginResult(userName.getText(), remember.isSelected());
            }
            return null;
        });

        registerLifecycle(dialog, "Login Dialog");
        Optional<LoginResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            LoginResult loginResult = result.get();
            log("Login Dialog result = userName=" + loginResult.userName()
                    + ", remember=" + loginResult.remember());
        } else {
            log("Login Dialog result = empty");
        }
    }

    private void showButtonDataDialog(Stage owner) {
        Dialog<String> dialog = new Dialog<>();
        initDialog(dialog, owner, "ButtonData Layout Dialog");

        ButtonType back = new ButtonType("Back", ButtonData.BACK_PREVIOUS);
        ButtonType next = new ButtonType("Next", ButtonData.NEXT_FORWARD);
        ButtonType apply = new ButtonType("Apply", ButtonData.APPLY);
        ButtonType finish = new ButtonType("Finish", ButtonData.FINISH);
        ButtonType help = new ButtonType("Help", ButtonData.HELP);
        CheckBox allowCommit = new CheckBox("Enable Apply and Finish");

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("ButtonBar.ButtonData controls placement and semantic behavior");
        pane.setContent(new VBox(8.0,
                new Label("Try the different buttons and watch the result log."),
                allowCommit));
        pane.getButtonTypes().setAll(help, back, next, apply, finish, ButtonType.CANCEL);

        pane.lookupButton(apply).disableProperty().bind(allowCommit.selectedProperty().not());
        pane.lookupButton(finish).disableProperty().bind(allowCommit.selectedProperty().not());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == null) {
                return "null button";
            }
            return buttonType.getText() + " / " + buttonType.getButtonData();
        });

        registerLifecycle(dialog, "ButtonData Dialog");
        Optional<String> result = dialog.showAndWait();
        logResult("ButtonData Dialog", result);
    }

    private void showResizableEditor(Stage owner) {
        Dialog<String> dialog = new Dialog<>();
        initDialog(dialog, owner, "Resizable Editor Dialog");
        dialog.setResizable(true);

        TextArea editor = new TextArea("""
                This dialog is resizable.

                Drag the window edges and observe how the DialogPane content grows.
                The Save button returns the current text through resultConverter.
                """);
        editor.setPrefColumnCount(64);
        editor.setPrefRowCount(14);

        ButtonType save = new ButtonType("Save", ButtonData.OK_DONE);
        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Resizable Dialog + large custom Node content");
        pane.setContent(editor);
        pane.setPrefSize(760.0, 520.0);
        pane.getButtonTypes().setAll(save, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == save) {
                return editor.getText();
            }
            return null;
        });

        registerLifecycle(dialog, "Resizable Dialog");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            log("Resizable Dialog saved text length = " + result.get().length());
        } else {
            log("Resizable Dialog result = empty");
        }
    }

    private void showModelessDialog(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog, owner, "Modeless Dialog");
        dialog.initModality(Modality.NONE);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Modeless dialog opened with show()");
        pane.setContentText("The owner window remains interactive while this dialog is open.");
        pane.getButtonTypes().setAll(ButtonType.CLOSE);

        registerLifecycle(dialog, "Modeless Dialog",
                () -> log("Modeless Dialog final result = " + dialog.getResult()));
        dialog.show();
        log("Modeless Dialog show() returned immediately.");
    }

    private void showWindowModalDialog(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog, owner, "Window Modal Dialog");
        dialog.initModality(Modality.WINDOW_MODAL);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Window-modal dialog");
        pane.setContentText("This dialog is owned by the primary stage and blocks only that window.");
        pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        registerLifecycle(dialog, "Window Modal Dialog");
        Optional<ButtonType> result = dialog.showAndWait();
        logResult("Window Modal Dialog", result);
    }

    private void showStageStyleDialog(Stage owner, StageStyle style) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initStyle(style);
        dialog.setTitle("StageStyle." + style.name());

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("Dialog.initStyle(StageStyle." + style.name() + ")");
        pane.setContentText("This dialog uses a different native StageStyle.");
        pane.getButtonTypes().setAll(ButtonType.CLOSE);

        registerLifecycle(dialog, "StageStyle." + style.name());
        Optional<ButtonType> result = dialog.showAndWait();
        logResult("StageStyle." + style.name(), result);
    }

    private void showCloseRequestDialog(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog, owner, "Close Request Dialog");

        CheckBox allowClose = new CheckBox("Allow this dialog to close");
        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText("DIALOG_CLOSE_REQUEST can be consumed");
        pane.setContent(new VBox(8.0,
                new Label("Try the window close button before enabling the checkbox."),
                allowClose));
        pane.getButtonTypes().setAll(ButtonType.CLOSE);
        pane.lookupButton(ButtonType.CLOSE).disableProperty().bind(allowClose.selectedProperty().not());

        dialog.setOnCloseRequest(event -> {
            if (!allowClose.isSelected()) {
                event.consume();
                log("Close Request Dialog consumed DIALOG_CLOSE_REQUEST.");
            }
        });

        registerLifecycle(dialog, "Close Request Dialog");
        Optional<ButtonType> result = dialog.showAndWait();
        logResult("Close Request Dialog", result);
    }

    private void showTextInputDialog(Stage owner) {
        TextInputDialog dialog = new TextInputDialog("default text");
        initDialog(dialog, owner, "TextInputDialog");
        dialog.getDialogPane().setHeaderText("Built-in TextInputDialog");
        dialog.getDialogPane().setContentText("Value:");
        dialog.getEditor().selectAll();

        registerLifecycle(dialog, "TextInputDialog");
        Optional<String> result = dialog.showAndWait();
        logResult("TextInputDialog", result);
    }

    private void showChoiceDialog(Stage owner) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Medium",
                List.of("Small", "Medium", "Large", "Extra Large"));
        initDialog(dialog, owner, "ChoiceDialog");
        dialog.getDialogPane().setHeaderText("Built-in ChoiceDialog");
        dialog.getDialogPane().setContentText("Size:");

        registerLifecycle(dialog, "ChoiceDialog");
        Optional<String> result = dialog.showAndWait();
        logResult("ChoiceDialog", result);
    }

    private void initDialog(Dialog<?> dialog, Stage owner, String title) {
        dialog.initOwner(owner);
        dialog.setTitle(title);
    }

    private void registerLifecycle(Dialog<?> dialog, String name) {
        registerLifecycle(dialog, name, null);
    }

    private void registerLifecycle(Dialog<?> dialog, String name, Runnable hiddenAction) {
        dialog.setOnShowing(event -> log(name + " event: SHOWING"));
        dialog.setOnShown(event -> log(name + " event: SHOWN"));
        dialog.setOnHiding(event -> log(name + " event: HIDING"));
        dialog.setOnHidden(event -> {
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

    private void logResult(String label, Optional<?> result) {
        log(label + " result = " + result.map(Object::toString).orElse("empty"));
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

    private record LoginResult(String userName, boolean remember) {
    }
}
