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

import com.leewyatt.rxcontrols.animation.carousel.AnimFlip;
import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController {

    @FXML
    private HBox topBar;

    @FXML
    private VBox navBar;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private ToggleGroup navGroup;

    @FXML
    private RXCarousel mainCarousel;

    private double offsetX,offsetY;

    @FXML
    void initialize() throws IOException {
        Pane p1 = FXMLLoader.load(getClass().getResource("/fxml/pane_about.fxml"));
        RXCarouselPane aboutPane = new RXCarouselPane(p1);
        Pane p2 = FXMLLoader.load(getClass().getResource("/fxml/pane_avatar.fxml"));
        RXCarouselPane avatarPane = new RXCarouselPane(p2);
        Pane p3 = FXMLLoader.load(getClass().getResource("/fxml/pane_buttons.fxml"));
        RXCarouselPane buttonsPane = new RXCarouselPane(p3);
        Pane p4 = FXMLLoader.load(getClass().getResource("/fxml/pane_carousel.fxml"));
        RXCarouselPane carouselPane = new RXCarouselPane(p4);
        Pane p5 = FXMLLoader.load(getClass().getResource("/fxml/pane_highlight_text.fxml"));
        RXCarouselPane highlightTextPane = new RXCarouselPane(p5);
        Pane p6 = FXMLLoader.load(getClass().getResource("/fxml/pane_field.fxml"));
        RXCarouselPane fieldPane = new RXCarouselPane(p6);
        Pane p7 = FXMLLoader.load(getClass().getResource("/fxml/pane_css_reference.fxml"));
        RXCarouselPane cssPane = new RXCarouselPane(p7);
        Pane p8 = FXMLLoader.load(getClass().getResource("/fxml/pane_digit.fxml"));
        RXCarouselPane digitPane = new RXCarouselPane(p8);
        Pane p9 = FXMLLoader.load(getClass().getResource("/fxml/pane_svgview.fxml"));
        RXCarouselPane svgPane = new RXCarouselPane(p9);
        Pane p10 = FXMLLoader.load(getClass().getResource("/fxml/pane_media.fxml"));
        RXCarouselPane mediaPane = new RXCarouselPane(p10);
        mainCarousel.setPaneList(aboutPane, avatarPane, buttonsPane, carouselPane,digitPane, highlightTextPane,fieldPane,svgPane,mediaPane,cssPane);
        mainCarousel.setCarouselAnimation(new AnimFlip());
        navGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
            int index = navGroup.getToggles().indexOf(nv);
            mainCarousel.setSelectedIndex(index);
        });

    }

    @FXML
    void exitAction(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }


    @FXML
    void topBarDraggedAction(MouseEvent event) {
        Window window = topBar.getScene().getWindow();
        window.setX(event.getScreenX()-offsetX);
        window.setY(event.getScreenY()-offsetY);
    }

    @FXML
    void topBarPressedAction(MouseEvent event) {
        offsetX = event.getSceneX();
        offsetY=event.getSceneY();
    }
}