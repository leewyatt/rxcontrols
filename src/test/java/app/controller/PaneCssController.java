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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.ResourceBundle;

public class PaneCssController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private WebView webView;


    @FXML
    void gotoCssAction(ActionEvent event) {
        webView.getEngine().load(localUrl);
    }

    @FXML
    void gotoFXCssAction(ActionEvent event) {
        webView.getEngine().load("https://docs.oracle.com/javase/8/javafx/api/javafx/scene/doc-files/cssref.html");
    }
    private String localUrl;
    @FXML
    void initialize() {
        localUrl = getClass().getResource("/html/css_reference.html").toExternalForm();
        webView.getEngine().load(localUrl);
    }
}
