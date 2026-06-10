package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.samples.support.SampleColors;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXDigit}: the full 0–9 set shown side by side, each
 * glyph tinted with a random dark color from {@link SampleColors} so it reads
 * clearly on the light background. For the full interactive property panel see
 * {@code RXDigitShowcase}.
 */
public class RXDigitDemo extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        HBox root = new HBox(10);
        for (int i = 0; i < 10; i++) {
            RXDigit digit = new RXDigit(i);
            digit.setLitFill(SampleColors.randomDark());
            root.getChildren().add(digit);
        }
        root.setAlignment(Pos.CENTER);
        primaryStage.setScene(new Scene(root, 680, 320));
        primaryStage.setTitle("RXDigit Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
