package io.github.leewyatt.rxcontrols.samples.support.controller;

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
