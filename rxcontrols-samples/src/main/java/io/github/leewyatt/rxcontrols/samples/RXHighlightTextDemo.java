package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXHighlightText;
import io.github.leewyatt.rxcontrols.RXHighlightText.MatchRules;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class RXHighlightTextDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        TextField tf = new TextField();
        //下拉框: 匹配规则分别是 1正则表达式匹配,2区分大小写匹配字符串,3忽略大小写匹配字符串
        ComboBox<MatchRules> comboBox = new ComboBox<>(FXCollections.observableArrayList(MatchRules.values()));
        comboBox.getSelectionModel().select(0);
        RXHighlightText text = new RXHighlightText("123人生若只如初见ABC,\nabc何事秋风悲画扇123");
        text.setTextAlignment(TextAlignment.CENTER);
        //匹配规则绑定到下拉框
        text.matchRulesProperty().bind(comboBox.valueProperty());
        //text.setMatchRules(MatchRules.MATCH_CASE);
        //匹配关键字绑定到文本框
        text.keywordsProperty().bind(tf.textProperty());
        //text.setKeywords("[A-Z]+");
        tf.setText("[0-9]+");
        HBox hBox = new HBox(tf, comboBox);
        hBox.setAlignment(Pos.CENTER);
        root.getChildren().add(hBox);
        root.getChildren().add(text);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXHighlightText Demo");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * 高亮文本的 css 参考
     .rx-highlight-text .highlight-label{
     -fx-background-color: red;
     -fx-text-fill: white;
     }
     .rx-highlight-text .plain-text{
     -fx-fill:black;
     }
     */
}
