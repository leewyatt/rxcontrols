package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXPagination;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Callback;

public class RXPaginationDemo extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {


        BorderPane root=new BorderPane();
        //分页组件. 简单的修改了原始分页组件, 添加了文本框和按钮,可以快速跳到指定页
        RXPagination pn=new RXPagination(100,0);
        pn.setPageFactory(new Callback<Integer, Node>() {
            @Override
            public Node call(Integer param) {
                return new Label("abc Page"+(param+1));
            }
        });
        root.setCenter(pn);
        primaryStage.setScene(new Scene(root,500,380));
        primaryStage.setTitle("RXPagination Demo");
        primaryStage.show();
    }
    public static void main(String[] args){
        launch(args);
    }

}
