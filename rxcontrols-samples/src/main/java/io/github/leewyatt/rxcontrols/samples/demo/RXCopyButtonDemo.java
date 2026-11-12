package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXCopyButton} demo: an icon-only button copying a read-only
 * field, a "Copy / Copied" button copying a code snippet, and a button
 * copying plain text and HTML in one go.
 */
public class RXCopyButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Icon-only: the factory reads the field at the moment of the click.
        TextField keyField = new TextField("rx-live-51f0c9d2a7");
        keyField.setEditable(false);
        HBox.setHgrow(keyField, Priority.ALWAYS);
        RXCopyButton copyKey = new RXCopyButton();
        copyKey.setAccessibleText("Copy API key");
        copyKey.setContentFactory(() -> RXCopyButton.textContent(keyField.getText()));
        HBox keyRow = new HBox(8.0, new Label("API key"), keyField, copyKey);
        keyRow.setAlignment(Pos.CENTER_LEFT);

        // Text swap: setCopiedText turns "Copy" into "Copied" for two seconds.
        TextArea snippet = new TextArea("mvn -q -pl rxcontrols test");
        snippet.setPrefRowCount(2);
        RXCopyButton copySnippet = new RXCopyButton("Copy");
        copySnippet.setCopiedText("Copied");
        copySnippet.setContentFactory(() -> RXCopyButton.textContent(snippet.getText()));
        // A stale "Copied" is cleared as soon as the snippet changes.
        snippet.textProperty().addListener(observable -> copySnippet.resetFeedback());

        // Several formats in one copy: rich editors paste the HTML, plain ones the text.
        RXCopyButton copyRich = new RXCopyButton("Copy as rich text");
        copyRich.setContentFactory(() -> {
            ClipboardContent content = RXCopyButton.textContent("RXControls for JavaFX");
            content.putHtml("<b>RXControls</b> for <i>JavaFX</i>");
            return content;
        });

        VBox root = new VBox(14.0, keyRow, snippet, new HBox(8.0, copySnippet, copyRich));
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root, 480, 240));
        primaryStage.setTitle("RXCopyButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
