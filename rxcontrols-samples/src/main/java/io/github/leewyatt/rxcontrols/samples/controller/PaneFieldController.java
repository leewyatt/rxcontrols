package io.github.leewyatt.rxcontrols.samples.controller;

import io.github.leewyatt.rxcontrols.OldRXTextField;
import io.github.leewyatt.rxcontrols.event.RXActionEvent;
import javafx.fxml.FXML;
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
    private OldRXTextField copyTextField;

    private FileChooser fileChooser = new FileChooser();

    @FXML
    void initialize() {
        fileChooser.setTitle("选择文件");
        copyTextField.setOnClickButton(event -> {
            copyTextField.selectAll();
            copyTextField.copy();
        });
    }

    @FXML
    void deleteText(RXActionEvent event) {
        OldRXTextField tf = (OldRXTextField) event.getSource();
        tf.clear();
    }

    @FXML
    void openFile(RXActionEvent event) {
        OldRXTextField tf = (OldRXTextField) event.getSource();
        Window window = tf.getScene().getWindow();
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            tf.setText(file.getAbsolutePath());
        }
    }


}
