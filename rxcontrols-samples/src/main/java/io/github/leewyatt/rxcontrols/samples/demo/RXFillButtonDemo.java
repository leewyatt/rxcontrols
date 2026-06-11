package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Minimal {@link RXFillButton} demo.
 */
public class RXFillButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        RXFillButton button = new RXFillButton("填充色按钮/文本");
        button.setPrefSize(230, 50);
        button.setMaxSize(230, 50);
        button.setOnAction(event -> System.out.println("FillButton onAction"));
        // 填充效果; 也可用 CSS: -rx-fill-animation: top-to-bottom
        button.setFillAnimation(FillAnimation.TOP_TO_BOTTOM);
        // 自定义参数走构造器, 例如 setFillAnimation(new FillAnimZigzag(6))
        // 触发方式默认 HOVER; 也可 setAnimationTrigger(RXAnimationTrigger.PRESSED)
        // 动画时长默认 200ms; 也可 setAnimationDuration(Duration.millis(300))

        root.setCenter(button);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXFillButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
