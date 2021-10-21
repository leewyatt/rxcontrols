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

import com.leewyatt.rxcontrols.controls.RXAvatar;
import com.leewyatt.rxcontrols.controls.RXAvatar.Type;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class TestAvatar extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane root=new BorderPane();
        Image image = new Image(getClass().getResource("/scenery/2.png").toExternalForm());
        RXAvatar avatar = new RXAvatar(image);
        // 设置头像类型
        avatar.setShapeType(Type.SQUARE);
        //设置圆角大小
        avatar.setArcWidth(15);
        avatar.setArcHeight(15);
        avatar.setEffect(new DropShadow());
        root.setCenter(avatar);
        primaryStage.setScene(new Scene(root,380,320));
        primaryStage.setTitle("头像组件");
        primaryStage.show();

        Timeline timeline = new Timeline(
                new KeyFrame(
                        Duration.millis(3000),
                        new KeyValue(avatar.arcWidthProperty(),100, Interpolator.EASE_BOTH),
        new KeyValue(avatar.arcHeightProperty(), 100)));
        timeline.play();

    }
    public static void main(String[] args){
        launch(args);
    }
}
