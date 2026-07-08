package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class PaneFieldController {

    private static final String CLEAR_ICON =
            "M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z";

    private static final String COPY_ICON =
            "M4 2a2 2 0 0 0-2 2v7h1V4a1 1 0 0 1 1-1h7V2H4zm2 3a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H6zm0 1h7a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z";

    private static final String FOLDER_ICON =
            "M1.5 3A1.5 1.5 0 0 0 0 4.5v7A1.5 1.5 0 0 0 1.5 13h13a1.5 1.5 0 0 0 1.5-1.5v-6A1.5 1.5 0 0 0 14.5 4H8.2L6.7 2.8A1.5 1.5 0 0 0 5.76 2H1.5z";

    private static final String EYE_OPEN_ICON =
            "M16 8s-3-5.5-8-5.5S0 8 0 8s3 5.5 8 5.5S16 8 16 8zM8 5.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z";

    private static final String EYE_SLASH_ICON =
            "M13.359 11.238C15.06 9.72 16 8 16 8s-3-5.5-8-5.5a7.028 7.028 0 0 0-2.79.588l.77.771A5.944 5.944 0 0 1 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.134 13.134 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755-.165.165-.337.328-.517.486l.708.709zM11.297 9.176a3.5 3.5 0 0 0-4.474-4.474l.823.823a2.5 2.5 0 0 1 2.829 2.829l.822.822zm-2.943 1.299.822.822a3.5 3.5 0 0 1-4.474-4.474l.823.823a2.5 2.5 0 0 0 2.829 2.829zM13.646 14.354l-12-12 .708-.708 12 12-.708.708z";

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;


    @FXML
    private RXTextField clearTextField;

    @FXML
    private RXPasswordField loginPasswordField;

    @FXML
    private RXTextField copyTextField;

    @FXML
    private RXTextField fileTextField;

    private final FileChooser fileChooser = new FileChooser();

    @FXML
    void initialize() {
        fileChooser.setTitle("选择文件");

        Button clearButton = createIconButton(CLEAR_ICON, "清除");
        clearButton.setOnAction(event -> clearTextField.clear());
        clearTextField.setTrailing(clearButton);

        ToggleButton revealButton = createEyeToggle();
        loginPasswordField.revealPasswordProperty().bind(revealButton.selectedProperty());
        loginPasswordField.setTrailing(revealButton);

        Button copyButton = createIconButton(COPY_ICON, "复制");
        copyButton.setOnAction(event -> {
            copyTextField.selectAll();
            copyTextField.copy();
        });
        copyTextField.setTrailing(copyButton);

        Button fileButton = createIconButton(FOLDER_ICON, "选择文件");
        fileButton.setOnAction(event -> {
            Window window = fileTextField.getScene().getWindow();
            File file = fileChooser.showOpenDialog(window);
            if (file != null) {
                fileTextField.setText(file.getAbsolutePath());
            }
        });
        fileTextField.setTrailing(fileButton);
    }

    private Button createIconButton(String iconPath, String tooltip) {
        Button button = new Button();
        button.setFocusTraversable(false);
        button.getStyleClass().add("field-icon-button");
        button.setGraphic(createIcon(iconPath));
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }

    private ToggleButton createEyeToggle() {
        ToggleButton button = new ToggleButton();
        button.setFocusTraversable(false);
        button.getStyleClass().add("field-icon-button");
        button.getStyleClass().add("eye-toggle");
        button.setGraphic(createIcon(EYE_SLASH_ICON));
        button.setTooltip(new Tooltip("显示密码"));
        button.setAccessibleText("显示密码");
        button.selectedProperty().addListener((obs, oldValue, selected) -> {
            boolean showing = Boolean.TRUE.equals(selected);
            button.setGraphic(createIcon(showing ? EYE_OPEN_ICON : EYE_SLASH_ICON));
            button.setTooltip(new Tooltip(showing ? "隐藏密码" : "显示密码"));
            button.setAccessibleText(showing ? "隐藏密码" : "显示密码");
        });
        return button;
    }

    private SVGPath createIcon(String content) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFill(Color.web("#495057"));
        return path;
    }

}
