package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSkeletonShowcase;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Compact demo for {@link RXSkeleton} and {@link RXSkeletonPane}.
 *
 * <p>For the full property explorer see {@link RXSkeletonShowcase}.</p>
 */
public class RXSkeletonDemo extends Application {

    private static final Duration REFRESH_DURATION = Duration.seconds(2.0);

    /**
     * Starts the demo stage.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        VBox content = new VBox(22.0,
                createProfileCardDemo(),
                createInboxDemo());
        content.setPadding(new Insets(32.0));
        content.getStyleClass().add("demo-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("preview-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scroll, 760.0, 640.0);
        scene.getStylesheets().add(
                RXSkeletonDemo.class
                        .getResource("rx_skeleton_demo.css")
                        .toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXSkeleton Demo");
        primaryStage.show();
    }

    private Node createProfileCardDemo() {
        RXSkeletonPane pane = new RXSkeletonPane(createProfileSkeleton(), createProfileContent(), true);
        pane.getStyleClass().add("social-card");
        pane.setPrefWidth(520.0);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refresh(pane));
        Button toggleButton = new Button("Toggle loading");
        toggleButton.setOnAction(event -> pane.setLoading(!pane.isLoading()));

        HBox controls = new HBox(8.0, refreshButton, toggleButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        return new VBox(12.0, pane, controls);
    }

    private Node createProfileSkeleton() {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);

        RXSkeleton title = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        title.setPrefSize(132.0, 14.0);
        title.setMaxWidth(132.0);

        RXSkeleton paragraph = new RXSkeleton(Variant.TEXT);
        paragraph.setLineCount(2);
        paragraph.setLineHeight(10.0);
        paragraph.setLineSpacing(6.0);
        paragraph.setLastLineFillPercent(68.0);

        VBox textColumn = new VBox(8.0, title, paragraph);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(14.0, avatar, textColumn);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("card-body");
        return row;
    }

    private Node createProfileContent() {
        RXAvatar avatar = createTextAvatar("LW", "real-avatar", 48.0);

        Label name = new Label("Lee Wyatt");
        name.getStyleClass().add("real-name");
        Label body = new Label("Today's weather is great. Took a walk and found "
                + "a quiet cafe near the station. Posting a photo once I get home.");
        body.getStyleClass().add("real-body");
        body.setWrapText(true);

        VBox textColumn = new VBox(6.0, name, body);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(14.0, avatar, textColumn);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("card-body");
        return row;
    }

    private Node createInboxDemo() {
        VBox skeletonRows = new VBox(14.0,
                createMessageSkeleton(0.82),
                createMessageSkeleton(0.64),
                createMessageSkeleton(0.74));
        VBox contentRows = new VBox(12.0,
                createMessageContent("Payment reminder", "Invoice #1042 is ready for review."),
                createMessageContent("Design handoff", "Three assets were added to the board."),
                createMessageContent("Build status", "The nightly verification finished cleanly."));

        RXSkeletonPane pane = new RXSkeletonPane(skeletonRows, contentRows, true);
        pane.getStyleClass().add("inbox-card");

        Button refreshButton = new Button("Reload inbox");
        refreshButton.setOnAction(event -> refresh(pane));

        return new VBox(12.0, pane, refreshButton);
    }

    private Node createMessageSkeleton(double widthRatio) {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setPrefSize(36.0, 36.0);
        avatar.setMaxSize(36.0, 36.0);

        RXSkeleton title = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        title.setPrefSize(120.0, 12.0);
        title.setMaxWidth(120.0);

        RXSkeleton summary = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        summary.setPrefHeight(10.0);
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(title, spacer);
        VBox textColumn = new VBox(7.0, titleRow, summary);
        HBox.setHgrow(textColumn, Priority.ALWAYS);
        textColumn.setMaxWidth(360.0 * widthRatio);

        HBox row = new HBox(12.0, avatar, textColumn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node createMessageContent(String title, String summary) {
        RXAvatar avatar = createTextAvatar(title.substring(0, 1), "message-avatar", 36.0);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("message-title");
        Label summaryLabel = new Label(summary);
        summaryLabel.getStyleClass().add("message-summary");

        VBox textColumn = new VBox(3.0, titleLabel, summaryLabel);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(12.0, avatar, textColumn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private RXAvatar createTextAvatar(String text, String styleClass, double size) {
        RXAvatar avatar = new RXAvatar();
        avatar.setText(text);
        avatar.getStyleClass().add(styleClass);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    private void refresh(RXSkeletonPane pane) {
        pane.setLoading(true);
        PauseTransition wait = new PauseTransition(REFRESH_DURATION);
        wait.setOnFinished(event -> pane.setLoading(false));
        wait.play();
    }

    /**
     * Launches the demo application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
