package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTranslationButton;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;


public class RXTranslationButtonDemo extends Application {

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
        primaryStage.setTitle("RXTranslationButton Demo");
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
