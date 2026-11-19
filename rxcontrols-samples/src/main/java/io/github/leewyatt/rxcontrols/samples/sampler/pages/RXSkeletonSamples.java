package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ImageFit;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXSkeleton page: a post that loads, an inbox that refreshes, the three variants
 * forming a comment, a photo grid whose tiles load one by one and skeletons recolored
 * for a dark card.
 */
public final class RXSkeletonSamples {

    private static final Duration LOAD_TIME = Duration.seconds(1.6);

    private RXSkeletonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A gray placeholder shape with a moving shimmer, shown where content is still loading.",
                List.of("Keep the layout of a card or list steady while its data loads, so nothing jumps when it arrives.",
                        "For a whole region with empty and error states too, use RXStatePane."),
                List.of(
                        SampleCard.large("Post",
                                "RXSkeletonPane swaps a skeleton for the real post when loading turns false; its size stays the same.",
                                post()),
                        SampleCard.large("Inbox",
                                "Refresh shows skeleton rows shaped like the messages for a moment.",
                                inbox()),
                        SampleCard.wide("Variants",
                                "CIRCULAR for the avatar, ROUNDED_RECTANGLE for the name and a picture, TEXT with lineCount 3 for a paragraph.",
                                variants()),
                        SampleCard.large("Photo grid",
                                "Every tile is its own skeleton pane, so photos appear one by one.",
                                photos()),
                        SampleCard.large("On a dark card",
                                "-rx-base-color and a shimmerFill from createShimmerGradient fit the skeleton to a dark surface.",
                                darkCard())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Post ====================

    private static Node post() {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);
        RXSkeleton name = new RXSkeleton();
        name.setPrefSize(130.0, 14.0);
        name.setMaxWidth(130.0);
        RXSkeleton lines = new RXSkeleton(Variant.TEXT);
        lines.setLineCount(3);
        lines.setLineHeight(10.0);
        lines.setLineSpacing(7.0);
        VBox skeletonText = new VBox(10.0, name, lines);
        HBox.setHgrow(skeletonText, Priority.ALWAYS);
        HBox skeleton = new HBox(14.0, avatar, skeletonText);

        RXAvatar realAvatar = new RXAvatar();
        realAvatar.setText("LW");
        realAvatar.getStyleClass().add("teal");
        realAvatar.setPrefSize(48.0, 48.0);
        Label body = text("Found a quiet cafe near the station. Good coffee, fast Wi-Fi and a window seat. "
                + "Working from here today.", "copy");
        body.setWrapText(true);
        VBox realText = new VBox(4.0, text("Lee Wyatt", "headline"), text("@LeeWyatt_7788 · 5 min", "muted"), body);
        HBox.setHgrow(realText, Priority.ALWAYS);
        HBox content = new HBox(14.0, realAvatar, realText);

        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);
        pane.getStyleClass().addAll("panel", "padded");
        pane.setPrefWidth(360.0);
        pane.setMaxWidth(Region.USE_PREF_SIZE);

        RXButton load = solid("Load", "teal");
        load.setOnAction(event -> reload(pane, load));
        VBox box = new VBox(14.0, pane, load);
        FocusHandOff.onDisable(load, box);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static void reload(RXSkeletonPane pane, RXButton trigger) {
        pane.setLoading(true);
        trigger.setDisable(true);
        PauseTransition wait = new PauseTransition(LOAD_TIME);
        wait.setOnFinished(event -> {
            pane.setLoading(false);
            trigger.setDisable(false);
            trigger.setText("Reload");
        });
        wait.play();
    }

    // ==================== Inbox ====================

    private static Node inbox() {
        VBox skeleton = new VBox(0.0, messageSkeleton(0.8), messageSkeleton(0.6), messageSkeleton(0.7));
        VBox content = new VBox(0.0,
                message("P", "Payment reminder", "Invoice #1042 is ready for review.", "orange"),
                message("D", "Design handoff", "Three new assets were added to the board.", "violet"),
                message("B", "Build status", "The nightly build finished cleanly.", "lime"));
        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, false);
        pane.getStyleClass().add("panel");
        pane.setPrefWidth(360.0);
        pane.setMaxWidth(Region.USE_PREF_SIZE);

        RXButton refresh = plain("Refresh");
        refresh.setOnAction(event -> reload(pane, refresh));
        VBox box = new VBox(14.0, pane, refresh);
        FocusHandOff.onDisable(refresh, box);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node messageSkeleton(double widthRatio) {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setPrefSize(36.0, 36.0);
        avatar.setMaxSize(36.0, 36.0);
        RXSkeleton title = new RXSkeleton();
        title.setPrefSize(120.0, 12.0);
        title.setMaxWidth(120.0);
        RXSkeleton summary = new RXSkeleton();
        summary.setPrefSize(260.0 * widthRatio, 10.0);
        summary.setMaxWidth(260.0 * widthRatio);
        VBox texts = new VBox(8.0, title, summary);
        HBox row = new HBox(12.0, avatar, texts);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node message(String initial, String title, String summary, String color) {
        RXAvatar avatar = new RXAvatar();
        avatar.setText(initial);
        avatar.getStyleClass().add(color);
        avatar.setPrefSize(36.0, 36.0);
        VBox texts = new VBox(2.0, text(title, "copy-strong"), text(summary, "muted"));
        HBox row = new HBox(12.0, avatar, texts);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Variants ====================

    private static Node variants() {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setPrefSize(56.0, 56.0);
        avatar.setMaxSize(56.0, 56.0);
        RXSkeleton name = new RXSkeleton();
        name.setPrefSize(160.0, 16.0);
        name.setMaxWidth(160.0);
        RXSkeleton paragraph = new RXSkeleton(Variant.TEXT);
        paragraph.setLineCount(3);
        paragraph.setLastLineFillPercent(55.0);
        RXSkeleton picture = new RXSkeleton();
        picture.setCornerRadius(10.0);
        picture.setPrefSize(180.0, 104.0);
        picture.setMaxSize(180.0, 104.0);
        VBox texts = new VBox(12.0, name, paragraph);
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox row = new HBox(18.0, avatar, texts, picture);
        row.setPrefWidth(540.0);
        row.setMaxWidth(Region.USE_PREF_SIZE);
        return row;
    }

    // ==================== Photo grid ====================

    private static Node photos() {
        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        List<RXSkeletonPane> tiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            RXSkeleton skeleton = new RXSkeleton();
            skeleton.setCornerRadius(10.0);
            RXImageView photo = new RXImageView();
            photo.setImageFit(ImageFit.COVER);
            photo.setImageRadius(10.0);
            photo.setImage(SampleImages.scenery(i + 1));
            RXSkeletonPane tile = new RXSkeletonPane(skeleton, photo, true);
            tile.setPrefSize(150.0, 92.0);
            tiles.add(tile);
            grid.add(tile, i % 2, i / 2);
        }
        RXButton load = solid("Load photos", "sky");
        load.setGraphic(icon("photo-icon"));
        load.setOnAction(event -> {
            load.setDisable(true);
            for (int i = 0; i < tiles.size(); i++) {
                RXSkeletonPane tile = tiles.get(i);
                tile.setLoading(true);
                PauseTransition wait = new PauseTransition(Duration.millis(600.0 + i * 450.0));
                boolean last = i == tiles.size() - 1;
                wait.setOnFinished(done -> {
                    tile.setLoading(false);
                    if (last) {
                        load.setDisable(false);
                    }
                });
                wait.play();
            }
        });
        VBox box = new VBox(14.0, grid, load);
        FocusHandOff.onDisable(load, box);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== On a dark card ====================

    private static Node darkCard() {
        RXSkeleton cover = new RXSkeleton();
        cover.getStyleClass().add("on-dark");
        cover.setCornerRadius(8.0);
        cover.setPrefSize(72.0, 72.0);
        cover.setMaxSize(72.0, 72.0);
        cover.setShimmerFill(RXSkeleton.createShimmerGradient(Color.web("#ffffff", 0.18)));
        RXSkeleton lines = new RXSkeleton(Variant.TEXT);
        lines.getStyleClass().add("on-dark");
        lines.setLineCount(3);
        lines.setLineHeight(11.0);
        lines.setShimmerFill(RXSkeleton.createShimmerGradient(Color.web("#ffffff", 0.18)));
        HBox.setHgrow(lines, Priority.ALWAYS);
        HBox row = new HBox(16.0, cover, lines);
        row.setAlignment(Pos.CENTER_LEFT);

        Label title = text("Up next", "on-dark-text");
        StackPane card = new StackPane(new VBox(14.0, title, row));
        card.getStyleClass().add("dark-card");
        card.setPrefWidth(340.0);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        return card;
    }
}
