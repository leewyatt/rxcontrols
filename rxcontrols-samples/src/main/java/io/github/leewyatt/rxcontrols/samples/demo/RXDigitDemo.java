package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDigit;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Random;

public class RXDigitDemo extends Application {
    private int num = 0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane root = new BorderPane();
        RXDigit digit = new RXDigit(num);
        root.setCenter(digit);
        primaryStage.setScene(new Scene(root, 500, 350));
        primaryStage.setTitle("RXDigit Demo");
        primaryStage.show();
        Random random = new Random();
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            num++;
            if(num>9){
                num=0;
            }
            digit.setDigit(num);

            int r =random.nextInt(256);
            int g =random.nextInt(256);
            int b =random.nextInt(256);
            //设置显示的部分的颜色
            digit.setLightFill(Color.rgb(r,g ,b));
            //设置隐藏暗淡部分的颜色
            //digit.setDarkFill(Color.rgb(r, g, b ));
        }));
        tl.setDelay(Duration.millis(500));
        tl.setCycleCount(Animation.INDEFINITE);

        tl.play();


    }

    public static void main(String[] args) {
        launch(args);
    }
}
