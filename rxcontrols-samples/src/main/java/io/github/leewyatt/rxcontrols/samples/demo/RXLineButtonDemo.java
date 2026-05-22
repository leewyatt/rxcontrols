package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.RXLineButton.LineType;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class RXLineButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        // 1----线条上升
        RXLineButton button1 = new RXLineButton("=线条文本框=");
        button1.setStyle("-fx-border-color: #ffcb8b;-fx-max-width: 150;-fx-max-height:50");
        //设置 线条的类型 (上升/下降)
        button1.setLineType(LineType.RISE);
        //设置 线条 和 按钮中文本 之间的距离
        button1.setSpacing(5);
        //设置 线条动画 起始的位移
        button1.setOffsetYPro(10);

        //2---线条下降
        RXLineButton button2 = new RXLineButton("=线条文本框=");
        button2.setStyle("-fx-border-color: #ffa3e6;-fx-max-width: 150;-fx-max-height:50");
        //设置 线条的类型 (上升/下降)
        button2.setLineType(LineType.RISE);
        //设置 线条 和 按钮中文本 之间的距离
        button2.setSpacing(5);
        //设置 线条动画 起始的位移
        button2.setOffsetYPro(-15);

        //3---线条淡入淡出
        RXLineButton button3 = new RXLineButton("=线条文本框=");
        button3.setStyle("-fx-border-color: #ff2e11;-fx-max-width: 150;-fx-max-height:50");
        //设置 线条的类型 (上升/下降)
        button3.setLineType(LineType.RISE);
        //设置动画时间
        button3.setAnimationTime(Duration.millis(300));
        button3.setOffsetYPro(0);

        //4---线条延伸
        RXLineButton button4 = new RXLineButton("=线条文本框=");
        button4.setStyle("-fx-border-color: #81a7ff;-fx-max-width: 150;-fx-max-height:50");
        //设置 线条的类型 延伸
        button4.setLineType(LineType.EXTEND);


        root.getChildren().addAll(button1,button2,button3,button4);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXLineButton Demo");
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
    /**
     如果想要改变线条颜色等, 使用下面的css参考案例
     .rx-line-button .prompt-line{
            -fx-stroke:read;
     }
     */
}
