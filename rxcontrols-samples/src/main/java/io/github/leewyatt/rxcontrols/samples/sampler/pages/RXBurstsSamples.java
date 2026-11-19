package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.RXBursts;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.AMBER;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.LIME;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ORANGE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.PARTY;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ROSE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.SKY;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.text;

/**
 * The RXBursts page: confetti for a placed order, sparkles on a star rating, bursts
 * wherever the pointer clicks and every preset on its own button.
 */
public final class RXBurstsSamples {

    private static final int PRESETS_PER_ROW = 5;
    private static final double PRESET_WIDTH = 104.0;
    private static final double PRESET_GAP = 10.0;
    private static final PseudoClass FILLED = PseudoClass.getPseudoClass("filled");

    private static final RXBurstOptions CONFETTI = RXBurstOptions.builder()
            .animation(BurstAnimation.CONFETTI).palette(PARTY)
            .duration(Duration.millis(1400.0)).scale(2.0).build();
    private static final RXBurstOptions STAR = RXBurstOptions.builder()
            .animation(BurstAnimation.SPARKLES).color(AMBER).build();
    private static final RXBurstOptions FIVE_STARS = RXBurstOptions.builder()
            .animation(BurstAnimation.FIREWORKS).palette(List.of(AMBER, ORANGE, ROSE)).scale(1.6).build();

    private RXBurstsSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A utility that plays a short particle burst from any node or point, drawn above the whole scene without changing the node.",
                List.of("Celebrate small moments: a like, a saved item, a finished order, a top rating.",
                        "For a like button with its own count and states use RXReactionButton."),
                List.of(
                        SampleCard.medium("Order placed",
                                "CONFETTI with a party palette, scale 2 and a 1.4 second duration for a moment worth celebrating.",
                                order()),
                        SampleCard.medium("Star rating",
                                "Every star sparkles from its own center; five stars set off FIREWORKS.",
                                rating()),
                        SampleCard.canvas("Click anywhere",
                                "RXBursts.playAt bursts at the clicked point of the pane, cycling through a few presets and colors.",
                                playground()),
                        SampleCard.wide("Presets",
                                "Every BurstAnimation preset, played from the button that names it in the theme's accent color.",
                                presets())),
                MotionKit.STYLESHEET);
    }

    // ==================== Order placed ====================

    private static Node order() {
        Label status = text("2 items  ·  $86.00", "muted");
        RXButton place = solid("Click here", "violet");
        place.setMaxWidth(Double.MAX_VALUE);
        int[] number = {1041};
        place.setOnAction(event -> {
            number[0]++;
            status.setText("Order #" + number[0] + " is on its way");
            status.getStyleClass().add("success");
            RXBursts.play(place, CONFETTI);
        });
        HBox line = new HBox(10.0, MotionKit.iconBadge("bag-icon", "amber"),
                new VBox(2.0, text("Checkout", "headline"), status));
        line.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(16.0, line, place);
        box.getStyleClass().add("panel");
        return fixedWidth(box, 240.0);
    }

    // ==================== Star rating ====================

    private static Node rating() {
        HBox stars = new HBox(4.0);
        stars.setAlignment(Pos.CENTER);
        Label caption = text("Tap a star to rate", "muted");
        String[] words = {"Poor", "Fair", "Good", "Great", "Loved it"};
        List<StackPane> buttons = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            StackPane star = iconButton("star-icon");
            star.getStyleClass().add("star");
            int value = i + 1;
            star.setOnMouseClicked(event -> {
                for (int k = 0; k < buttons.size(); k++) {
                    buttons.get(k).pseudoClassStateChanged(FILLED, k < value);
                }
                caption.setText(words[value - 1]);
                RXBursts.play(star, value == words.length ? FIVE_STARS : STAR);
            });
            buttons.add(star);
            stars.getChildren().add(star);
        }
        VBox box = new VBox(10.0, text("How was your stay?", "headline"), stars, caption);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Click anywhere ====================

    private static Node playground() {
        List<RXBurstOptions> cycle = List.of(
                RXBurstOptions.builder().animation(BurstAnimation.FIREWORKS).palette(PARTY).build(),
                RXBurstOptions.builder().animation(BurstAnimation.BUBBLES).color(SKY).build(),
                RXBurstOptions.builder().animation(BurstAnimation.SPARKLES).color(AMBER).build(),
                RXBurstOptions.builder().animation(BurstAnimation.CLASSIC).color(LIME).build(),
                RXBurstOptions.builder().animation(BurstAnimation.EXPLOSION).color(ORANGE).build());
        VBox hint = new VBox(8.0, icon("touch-icon", "large"), text("Click anywhere in this area", "muted"));
        hint.setAlignment(Pos.CENTER);
        hint.setMouseTransparent(true);
        StackPane pad = new StackPane(hint);
        pad.getStyleClass().add("playground");
        pad.setPrefHeight(180.0);
        int[] next = {0};
        pad.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            RXBursts.playAt(pad, event.getX(), event.getY(), cycle.get(next[0] % cycle.size()));
            next[0]++;
        });
        return pad;
    }

    // ==================== Presets ====================

    private static Node presets() {
        Map<String, BurstAnimation> all = new LinkedHashMap<>();
        all.put("RING", BurstAnimation.RING);
        all.put("DOTS", BurstAnimation.DOTS);
        all.put("CLASSIC", BurstAnimation.CLASSIC);
        all.put("SPARKLES", BurstAnimation.SPARKLES);
        all.put("RAYS", BurstAnimation.RAYS);
        all.put("HEARTS", BurstAnimation.HEARTS);
        all.put("BUBBLES", BurstAnimation.BUBBLES);
        all.put("CONFETTI", BurstAnimation.CONFETTI);
        all.put("FIREWORKS", BurstAnimation.FIREWORKS);
        all.put("EXPLOSION", BurstAnimation.EXPLOSION);
        VBox rows = new VBox(PRESET_GAP);
        rows.setAlignment(Pos.CENTER);
        HBox row = null;
        for (Map.Entry<String, BurstAnimation> entry : all.entrySet()) {
            if (row == null || row.getChildren().size() == PRESETS_PER_ROW) {
                row = new HBox(PRESET_GAP);
                row.setAlignment(Pos.CENTER);
                rows.getChildren().add(row);
            }
            RXButton button = MotionKit.plain(entry.getKey());
            button.getStyleClass().add("preset");
            button.setPrefWidth(PRESET_WIDTH);
            button.setOnAction(event -> RXBursts.play(button, entry.getValue()));
            row.getChildren().add(button);
        }
        return rows;
    }
}
