package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSelectableText;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates RXSelectableText as a selectable, copyable paragraph: drag to select a
 * range, double-click a word, triple-click a line, then Ctrl / Cmd + C to copy — none of
 * which a plain Label offers.
 */
public class RXSelectableTextDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXSelectableText text = new RXSelectableText(
                "RXSelectableText is a non-editable, wrapping block of text the user can "
                        + "select and copy.\n"
                        + "Drag to select a range, double-click to select a word, triple-click "
                        + "to select a line, then press Ctrl or Cmd + C to copy.\n"
                        + "Unlike a Label, the selection and caret are real and observable.");
        text.setLineSpacing(6);
        text.setMaxWidth(440);

        Label hint = new Label("Tip: select some text above and copy it with Ctrl / Cmd + C.");

        VBox root = new VBox(16, text, hint);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(24));
        primaryStage.setScene(new Scene(root, 520, 300));
        primaryStage.setTitle("RXSelectableText Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
