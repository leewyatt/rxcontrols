/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package app.controller;

import com.leewyatt.rxcontrols.controls.RXTextField;
import com.leewyatt.rxcontrols.event.RXActionEvent;
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
