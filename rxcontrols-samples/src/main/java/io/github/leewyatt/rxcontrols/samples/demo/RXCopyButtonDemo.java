package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXCopyButton} demo: an icon-only button copying a read-only
 * field as text, and a "Copy image" button copying a picture.
 */
public class RXCopyButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1: Copy text
        // Icon-only: the factory reads the field at the moment of the click.
        TextField keyField = new TextField("rx-controls-demo-1234567890");
        keyField.setEditable(false);
        HBox.setHgrow(keyField, Priority.ALWAYS);
        RXCopyButton copyKey = new RXCopyButton();
        // copyKey.setAccessibleText("Copy API key");
        copyKey.setContentFactory(() -> RXCopyButton.textContent(keyField.getText()));
        HBox keyRow = new HBox(8.0, new Label("API key"), keyField, copyKey);
        keyRow.setAlignment(Pos.CENTER_LEFT);

        // 2: Copy image
        // Any clipboard format works: here an image, pasted as a picture.
        Image image = new Image(RXCopyButtonDemo.class.getResource("/scenery/1.png").toExternalForm());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(400.0);
        imageView.setPreserveRatio(true);
        RXCopyButton copyImage = new RXCopyButton("Copy image");
        copyImage.setCopiedText("Copied");
        copyImage.setContentFactory(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(image);
            return content;
        });
        VBox imageBox = new VBox(10.0, imageView, copyImage);
        imageBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(20.0, keyRow, imageBox);
        root.setPadding(new Insets(24.0));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXCopyButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
