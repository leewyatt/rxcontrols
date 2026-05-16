package io.github.leewyatt.rxcontrols.samples.controller;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class PaneFieldController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;


    @FXML
    private RXTextField clearTextField;

    @FXML
    private RXTextField copyTextField;

    @FXML
    private RXTextField fileTextField;

    private final FileChooser fileChooser = new FileChooser();

    @FXML
    void initialize() {
        fileChooser.setTitle("选择文件");

        Button clearButton = createSideButton("清除");
        clearButton.setOnAction(event -> clearTextField.clear());
        clearTextField.setRight(clearButton);

        Button copyButton = createSideButton("复制");
        copyButton.setOnAction(event -> {
            copyTextField.selectAll();
            copyTextField.copy();
        });
        copyTextField.setRight(copyButton);

        Button fileButton = createSideButton("选择");
        fileButton.setOnAction(event -> {
            Window window = fileTextField.getScene().getWindow();
            File file = fileChooser.showOpenDialog(window);
            if (file != null) {
                fileTextField.setText(file.getAbsolutePath());
            }
        });
        fileTextField.setRight(fileButton);
    }

    private Button createSideButton(String text) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        button.getStyleClass().add("field-side-button");
        return button;
    }


}
