package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.iconBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.wrapped;

/**
 * The RXRipplePane page: project cards, settings rows, colored quick-action tiles,
 * round player buttons with centered ripples and a card that ripples from code.
 */
public final class RXRipplePaneSamples {

    private RXRipplePaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A single-content pane that shows a press ripple and a hover tint, clipped to its own background shape.",
                List.of("Make cards, list rows and tiles feel clickable without turning them into buttons.",
                        "For a real button with text and actions use RXButton, which has the same ripple built in."),
                List.of(
                        SampleCard.wide("Project cards",
                                "Each card is an RXRipplePane whose rippleFill matches its accent; the ripple starts where you press.",
                                projects()),
                        SampleCard.medium("Settings rows",
                                "Rows in a list ripple in indigo, and the hover tint shows which row the pointer is on.",
                                settings()),
                        SampleCard.medium("Quick actions",
                                "On colored tiles a white rippleFill with rippleOpacity 0.3 stays visible.",
                                quickActions()),
                        SampleCard.medium("Round buttons",
                                "rippleCentered starts every ripple from the middle, and the ripple follows the round background.",
                                player()),
                        SampleCard.medium("Ripple from code",
                                "playRipple plays one centered ripple, here to draw attention to a new message.",
                                inbox())),
                MotionKit.STYLESHEET);
    }

    // ==================== Project cards ====================

    private static Node projects() {
        Label status = text("Pick a project to open it", "muted");
        HBox row = new HBox(14.0,
                project("Website redesign", "12 tasks  ·  due Friday", "palette-icon", "rose", status),
                project("Mobile app", "28 tasks  ·  in review", "phone-icon", "teal", status),
                project("Payments API", "7 tasks  ·  on track", "code-icon", "indigo", status));
        row.setAlignment(Pos.CENTER);
        VBox box = new VBox(12.0, row, status);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node project(String name, String detail, String iconClass, String color, Label status) {
        VBox content = new VBox(10.0, iconBadge(iconClass, color), text(name, "copy-strong"), text(detail, "muted"));
        RXRipplePane card = new RXRipplePane(content);
        card.getStyleClass().addAll("project-card", color);
        card.setPrefWidth(170.0);
        card.setOnMouseClicked(event -> status.setText("Opened " + name));
        return card;
    }

    // ==================== Settings rows ====================

    private static Node settings() {
        Label status = text("Pick a setting to open it", "muted");
        VBox list = new VBox(
                row("Notifications", "Sounds and badges", "bell-icon", "rose", status),
                row("Privacy", "Who can see your profile", "lock-icon", "teal", status),
                row("Storage", "18.4 GB of 50 GB used", "cloud-icon", "sky", status),
                row("Appearance", "Light, dark or automatic", "palette-icon", "violet", status));
        list.getStyleClass().add("settings-list");
        VBox box = new VBox(12.0, list, status);
        box.setAlignment(Pos.CENTER);
        return fixedWidth(box, 250.0);
    }

    private static Node row(String title, String detail, String iconClass, String color, Label status) {
        Region chevron = icon("chevron-icon");
        HBox content = new HBox(10.0, iconBadge(iconClass, color),
                new VBox(1.0, text(title, "copy"), text(detail, "muted")), spacer(), chevron);
        content.setAlignment(Pos.CENTER_LEFT);
        RXRipplePane row = new RXRipplePane(content);
        row.getStyleClass().add("settings-row");
        row.setOnMouseClicked(event -> status.setText("Opened " + title));
        return row;
    }

    // ==================== Quick actions ====================

    private static Node quickActions() {
        Label status = text("Pick an action", "muted");
        TilePane tiles = new TilePane(10.0, 10.0,
                tile("Scan", "scan-icon", "sky", status),
                tile("Pay", "card-icon", "violet", status),
                tile("Transfer", "swap-icon", "orange", status),
                tile("Top up", "plus-icon", "lime", status));
        tiles.setPrefColumns(2);
        tiles.setMaxWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(12.0, tiles, status);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node tile(String name, String iconClass, String color, Label status) {
        VBox content = new VBox(6.0, icon(iconClass, "on-color"), text(name, "on-color-text"));
        content.setAlignment(Pos.CENTER_LEFT);
        RXRipplePane tile = new RXRipplePane(content);
        tile.getStyleClass().addAll("action-tile", color);
        tile.setPrefSize(112.0, 76.0);
        tile.setOnMouseClicked(event -> status.setText("Picked " + name));
        return tile;
    }

    // ==================== Round buttons ====================

    private static Node player() {
        Label status = text("Press play", "muted");
        RXRipplePane play = roundButton("play-icon", "big");
        boolean[] playing = {false};
        play.setOnMouseClicked(event -> {
            playing[0] = !playing[0];
            Node glyph = play.getContent();
            glyph.getStyleClass().removeAll("play-icon", "pause-icon");
            glyph.getStyleClass().add(playing[0] ? "pause-icon" : "play-icon");
            status.setText(playing[0] ? "Playing" : "Paused");
        });
        RXRipplePane previous = roundButton("previous-icon", "small");
        previous.setOnMouseClicked(event -> status.setText("Previous song"));
        RXRipplePane next = roundButton("next-icon", "small");
        next.setOnMouseClicked(event -> status.setText("Next song"));
        HBox controls = new HBox(16.0, previous, play, next);
        controls.setAlignment(Pos.CENTER);
        VBox box = new VBox(12.0, text("Paper Boats", "headline"), text("The Quiet Hours", "muted"), controls,
                status);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static RXRipplePane roundButton(String iconClass, String size) {
        Region glyph = icon(iconClass);
        glyph.setMouseTransparent(true);
        RXRipplePane button = new RXRipplePane(glyph);
        button.getStyleClass().addAll("round-button", size);
        button.setRippleCentered(true);
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return button;
    }

    // ==================== Ripple from code ====================

    private static Node inbox() {
        Label count = text("2 unread", "muted");
        Label preview = wrapped("Lee Wyatt: The cabin is booked for the weekend!", "copy");
        HBox content = new HBox(10.0, iconBadge("mail-icon", "amber"), new VBox(2.0, text("Inbox", "copy-strong"),
                count, preview));
        content.setAlignment(Pos.TOP_LEFT);
        RXRipplePane card = new RXRipplePane(content);
        card.getStyleClass().add("message-card");

        String[] messages = {"Mia Chen: Can you send the photos?", "Sam Ortiz: Lunch at 1 at the usual place?",
            "Lee Wyatt: Bring the blue tent, please."};
        int[] unread = {2};
        RXButton simulate = plain("Simulate a new message");
        simulate.setOnAction(event -> {
            preview.setText(messages[unread[0] % messages.length]);
            unread[0]++;
            count.setText(unread[0] + " unread");
            card.playRipple();
        });
        VBox box = new VBox(14.0, card, simulate);
        box.setAlignment(Pos.CENTER);
        return fixedWidth(box, 250.0);
    }
}
