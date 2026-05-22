package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillButton.FillType;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class RXFillButtonDemo extends Application {

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
        primaryStage.setTitle("RXFillButton Demo");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
