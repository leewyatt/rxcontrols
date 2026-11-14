package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBadgeShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXBadge}: an unread count on a
 * button that grows with each click, an online dot on a round avatar, and a
 * status dot beside a label. For a full property explorer see
 * {@link RXBadgeShowcase}.
 */
public class RXBadgeDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Button inbox = new Button("Inbox");
        RXBadge unread = new RXBadge(inbox);
        unread.setCount(98);
        inbox.setOnAction(event -> unread.setCount(unread.getCount() + 1));

        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        RXBadge online = new RXBadge(avatar);
        online.setVariant(RXBadge.Variant.DOT);
        online.setOverlap(RXBadge.Overlap.CIRCULAR);
        online.setPosition(RXBadge.Position.BOTTOM_END);
        online.getStyleClass().add("success");

        RXBadge dot = new RXBadge();
        dot.setVariant(RXBadge.Variant.DOT);
        dot.setPulsing(true);
        dot.getStyleClass().add("primary");
        Label status = new Label("Syncing", dot);
        status.setGraphicTextGap(8.0);

        HBox root = new HBox(48.0, unread, online, status);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXBadge Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
