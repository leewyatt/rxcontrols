package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.Type;
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

public class AvatarApp extends Application {
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
