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

import com.leewyatt.rxcontrols.controls.RXTranslationButton;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;


public class TestTranslationButton extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        RXTranslationButton button = new RXTranslationButton("邮箱Email");
        button.getStyleClass().add("btn-email");
        button.setMaxSize(150, 60);
        ImageView imageView = new ImageView(new Image(getClass().getResource("/image/email.png").toExternalForm(), true));
        imageView.setFitWidth(25);
        //设置按钮的图形
        button.setGraphic(imageView);
        root.setCenter(button);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("TestTranslationButton Window");
        primaryStage.show();

        /**
         * 如果想要修改背景色等 CSS样式参考.
         * .btn-email{
         *     -fx-background-color:red;
         *     -fx-background-radius: 5;
         * }
         * .btn-email .hover-label{
         *     -fx-background-color: #ab99ff;
         *     -fx-background-radius: 5;
         * }
         */
    }

    public static void main(String[] args) {
        launch(args);
    }
}
