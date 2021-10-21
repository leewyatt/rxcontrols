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

import com.leewyatt.rxcontrols.animation.carousel.*;
import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.controls.RXCarousel.RXDirection;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class PaneCarouselController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private RXCarousel sceneryCarousel;

    @FXML
    private CheckBox autoPlayBtn;

    @FXML
    private CheckBox autoPause;

    @FXML
    private Spinner<Integer> animationTimeChoose;

    @FXML
    private Spinner<Integer> showTimeChoose;

    @FXML
    private ComboBox<DisplayMode> arrowDisplayModeChoose;

    @FXML
    private ComboBox<DisplayMode> navDisplayModeChoose;


    @FXML
    void setAppear(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimAppear());
    }

    @FXML
    void setAround(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimAround(true));
    }

    @FXML
    void setBlend(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimBlend());
    }

    @FXML
    void setCircle(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimCircle());
    }

    @FXML
    void setCircleArray(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimCircleArray(32,156));
    }

    @FXML
    void setCorner(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimCorner());
    }

    @FXML
    void setCross(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimCross());
    }

    @FXML
    void setFade(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimFade());
    }

    @FXML
    void setGaussianBlur(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimGaussianBlur());
    }

    @FXML
    void setHardPaper(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimPageTurningHard());
    }

    @FXML
    void setHorBox(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimHorBox());
    }

    @FXML
    void setHorBlinds(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimHorBlinds());
    }
    @FXML
    void setHorFlip(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimFlip(RXDirection.HOR));
    }

    @FXML
    void setHorMove(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimHorMove());
    }

    @FXML
    void setHorStack(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimHorStack());
    }

    @FXML
    void setLinePair(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimLinePair());

    }

    @FXML
    void setLineSingle(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimLineSingle());
    }

    @FXML
    void setMotionBlur(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimMotionBlur());
    }

    @FXML
    void setNone(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimNone());
    }

    @FXML
    void setRectangle(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimRectangle());
    }

    @FXML
    void setRotate(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimRotate());
    }

    @FXML
    void setScaleRotate(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimScaleRotate());
    }

    @FXML
    void setScaleRotateShape(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimScaleRotateShape());
    }

    @FXML
    void setSector(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimSector());
    }

    @FXML
    void setSoftPaper(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimPageTurningSoft());
    }

    @FXML
    void setVerBlinds(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimVerBlinds());

    }

    @FXML
    void setVerBox(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimVerBox());
    }

    @FXML
    void setVerFlip(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimFlip(RXDirection.VER));
    }

    @FXML
    void setVerMove(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimVerMove());
    }

    @FXML
    void setVerStack(ActionEvent event) {
        sceneryCarousel.setCarouselAnimation(new AnimVerStack());
    }

    @FXML
    void initialize() {
        initPanes();
        //初始化赋值..
        animationTimeChoose.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 5000,320,100));
        showTimeChoose.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(500, 10000,3000,100));
        arrowDisplayModeChoose.setItems(FXCollections.observableArrayList(DisplayMode.values()));
        arrowDisplayModeChoose.getSelectionModel().select(0);
        navDisplayModeChoose.setItems(FXCollections.observableArrayList(DisplayMode.values()));
        navDisplayModeChoose.getSelectionModel().select(0);
        //属性的绑定
        sceneryCarousel.setCarouselAnimation(new AnimFade());
        sceneryCarousel.hoverPauseProperty().bind(autoPause.selectedProperty());
        sceneryCarousel.autoSwitchProperty().bind(autoPlayBtn.selectedProperty());
        sceneryCarousel.animationTimeProperty().bind(Bindings.createObjectBinding(()->{
            return Duration.millis(animationTimeChoose.getValue());
        }, animationTimeChoose.valueProperty()));

        sceneryCarousel.showTimeProperty().bind(Bindings.createObjectBinding(()->{
            return Duration.millis(showTimeChoose.getValue());
        }, showTimeChoose.valueProperty()));

        sceneryCarousel.arrowDisplayModeProperty().bind(arrowDisplayModeChoose.valueProperty());
        sceneryCarousel.navDisplayModeProperty().bind(navDisplayModeChoose.valueProperty());

    }

    private void initPanes() {
        String[] str = {"莺飞草长", "风雨兼程", "手捧星光", "日暮不赏"};//
        // sp.setCarouselAnimation(new AnimHorMove(true));
        final Duration millis = Duration.millis(3000);
        ArrayList<RXCarouselPane> panes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ImageView iv = new ImageView(getClass().getResource("/scenery/" + (i + 1) + ".png").toExternalForm());
            Label label = new Label(str[i]);
            label.setPrefSize(100, 30);
            label.setStyle("-fx-alignment: center;-fx-background-color:black;-fx-text-fill: white;-fx-font-size: 20");
            //label.setVisible(false);
            label.setTranslateY(100);
            StackPane stackPane = new StackPane(iv,label);
            RXCarouselPane p1 = new RXCarouselPane(stackPane);
            //RXCarouselPane 是继承自 BorderPane的
            //p1.setCenter(stackPane);

            //p1.setText(String.valueOf(i+1));
            p1.setOnClosed(event -> {
                //System.out.println("触发了已经关闭");
                event.consume();
            });
            p1.setOnOpening(event -> {
                // System.out.println("触发了正在打开");
                event.consume();

            });
            TranslateTransition ttOnOpened = new TranslateTransition(Duration.millis(1000), label);
            ttOnOpened.setFromY(100);
            ttOnOpened.setToY(0);
            p1.setOnOpened(event -> {
                //System.out.println("触发了已经打开");

                ttOnOpened.play();
                event.consume();
            });
            TranslateTransition ttOnClosing = new TranslateTransition(Duration.millis(1000), label);
            ttOnClosing.setFromY(0);
            ttOnClosing.setToY(100);
            p1.setOnClosing(event -> {
                //System.out.println("触发了正在关闭");
                ttOnClosing.play();
                event.consume();
            });
            panes.add(p1);
            p1.setPrefSize(650, 353);
        }
        sceneryCarousel.setPaneList(panes);
    }
}
