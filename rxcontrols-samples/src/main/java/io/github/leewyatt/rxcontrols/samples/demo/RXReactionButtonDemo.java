package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXReactionButton} demo: a photo post with like, favorite
 * and bookmark buttons. The like count follows the button, and a double click
 * on the photo likes it with a burst, as photo feeds usually do.
 */
public class RXReactionButtonDemo extends Application {

    private int likes = 128;

    @Override
    public void start(Stage primaryStage) {
        RXReactionButton like = new RXReactionButton(String.valueOf(likes));
        like.setAccessibleText("Like");
        like.setOnAction(event -> updateLikes(like));

        RXReactionButton favorite = new RXReactionButton();
        favorite.getStyleClass().add("star");
        favorite.setBurstAnimation(BurstAnimation.SPARKLES);
        favorite.setAccessibleText("Favorite");

        RXReactionButton bookmark = new RXReactionButton();
        bookmark.getStyleClass().add("bookmark");
        bookmark.setBurstAnimation(BurstAnimation.RAYS);
        bookmark.setAccessibleText("Bookmark");

        RXImageView photo = new RXImageView(new Image(
                getClass().getResource("/scenery/1.png").toExternalForm(), true));
        photo.setPrefSize(360.0, 240.0);
        // A double click likes the post; liking twice only plays the burst again.
        photo.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (like.isSelected()) {
                    like.playBurst();
                } else {
                    like.fire();
                }
            }
        });

        Label caption = new Label("Morning at the beach. Double-click the photo to like it.");
        caption.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(4.0, like, favorite, spacer, bookmark);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox post = new VBox(10.0, photo, actions, caption);
        post.setMaxWidth(360.0);
        StackPane root = new StackPane(post);
        root.setPadding(new Insets(32.0));

        primaryStage.setScene(new Scene(root, 460.0, 440.0));
        primaryStage.setTitle("RXReactionButton Demo");
        primaryStage.show();
    }

    private void updateLikes(RXReactionButton like) {
        likes += like.isSelected() ? 1 : -1;
        like.setText(String.valueOf(likes));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
