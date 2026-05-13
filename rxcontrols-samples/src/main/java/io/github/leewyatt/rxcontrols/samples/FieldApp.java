package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class FieldApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();

        //带有按钮的文本框
        RXTextField userName = new RXTextField("userName");
        userName.setMaxWidth(160);
        userName.setOnClickButton(event -> {
            userName.clear();
        });
        //按钮 显示
        userName.setButtonDisplayMode(DisplayMode.SHOW);
        //带有显示与隐藏密码的密码框
        RXPasswordField passwordField = new RXPasswordField("123456abc");
        passwordField.setMaxWidth(160);
        //设置密码 字符
        passwordField.setEchochar("&");
        //设置密码框的按钮显示
        passwordField.setButtonDisplayMode(DisplayMode.AUTO);


        //设置密码 是否显示为明文
        passwordField.setShowPassword(false);
        root.getChildren().addAll(userName,passwordField);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("FieldApp Window");
        primaryStage.show();


    }

    public static void main(String[] args) {
        launch(args);
    }
    /**
     * CSS示例
     .rx-password-field{

     }
     //当密码框显示时的伪类
     .rx-password-field:showing{

     }
     */
}
