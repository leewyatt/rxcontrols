package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillButton.FillMode;
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
        // 填充方向; 也可用 CSS: -rx-fill-mode: top-to-bottom
        button.setFillMode(FillMode.TOP_TO_BOTTOM);
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
