package io.github.leewyatt.rxcontrols.samples.controller;

import io.github.leewyatt.rxcontrols.RXTextField;
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
    private RXTextField copyTextField;

    private FileChooser fileChooser = new FileChooser();

    @FXML
    void initialize() {
        fileChooser.setTitle("选择文件");
        // 给自定义文本框添加事件处理的(event 是RXActionEvent)
        // 方法一: 用Lambda表达式.
        copyTextField.setOnClickButton(event -> {
            copyTextField.selectAll();
            copyTextField.copy();
        });
    }

    /**
     * 给自定义文本框添加事件处理的
     * 方法二: 在FXML里onClickButton="deleteText"
     * 下面的参数注意是RXActionEvent
     * @param event
     */
    @FXML
    void deleteText(RXActionEvent event) {
        RXTextField tf = (RXTextField) event.getSource();
        tf.clear();
    }

    @FXML
    void openFile(RXActionEvent event) {
        RXTextField tf = (RXTextField) event.getSource();
        Window window = tf.getScene().getWindow();
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            tf.setText(file.getAbsolutePath());
        }
    }


}
