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

import com.leewyatt.rxcontrols.controls.RXFillButton;
import com.leewyatt.rxcontrols.controls.RXFillButton.FillType;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class TestFillButton extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        RXFillButton button = new RXFillButton("填充色按钮/文本");
        button.setMaxSize(230, 50);
        button.setOnAction(event -> {
            System.out.println("FillButton onAction");
        });
        //设置 填充色动画---方法1 这种方法的好处,是可以自己实现FillAnimation接口,创建自己的自定义动画效果
        //button.setFillAnimation(new FillAnimLeftToRight());
        //设置 填充色动画---方法2 这种方法只能调用枚举类定义的几种效果
        button.setFillType(FillType.TOP_TO_BOTTOM);
        //设置填充动画方法3利用css样式 -rx-fill-type:CIRCLE_TO_SIDE
        //动画时间默认是130ms.
        //修改动画时间方法1:
        //button.setAnimationTime(Duration.millis(80));

        root.setCenter(button);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("TestFillButton Window");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
