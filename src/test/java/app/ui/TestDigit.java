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
package app.ui;

import com.leewyatt.rxcontrols.controls.RXDigit;
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

public class TestDigit extends Application {
    private int num = 0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane root = new BorderPane();
        RXDigit digit = new RXDigit(num);
        root.setCenter(digit);
        primaryStage.setScene(new Scene(root, 500, 350));
        primaryStage.setTitle("数字");
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
