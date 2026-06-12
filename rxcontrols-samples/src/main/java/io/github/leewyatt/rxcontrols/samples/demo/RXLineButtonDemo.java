package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Minimal {@link RXLineButton} demo.
 */
public class RXLineButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        RXLineButton button = new RXLineButton("线条按钮/文本");
        button.setOnAction(event -> System.out.println("LineButton onAction"));
        // 线条效果; 也可用 CSS: -rx-line-animation: top-bottom-converge
        button.setLineAnimation(LineAnimation.TOP_BOTTOM_CONVERGE);
        // 自定义参数走构造器, 例如 setLineAnimation(new LineAnimSlide(LineEdges.BOTTOM, 20))
        // 触发方式默认 HOVER; 也可 setAnimationTrigger(RXAnimationTrigger.PRESSED)
        // 动画时长默认 200ms; 也可 setAnimationDuration(Duration.millis(300))

        root.setCenter(button);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXLineButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
