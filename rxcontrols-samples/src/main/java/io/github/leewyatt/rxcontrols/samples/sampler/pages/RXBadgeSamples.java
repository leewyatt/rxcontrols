package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXButton;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXBadge page: unread counts on toolbar icons, presence dots on avatars, standalone
 * status dots, count and text pills in a menu, showZero on a cart and the two overlap
 * modes side by side.
 */
public final class RXBadgeSamples {

    private RXBadgeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A count, a short text or a dot pinned to a corner of another node, or shown on its own.",
                List.of("Unread counts on icons, presence on avatars and small status marks next to text.",
                        "To single out a whole card, such as the recommended plan, use RXRibbonBadge."),
                List.of(
                        SampleCard.large("Notification counts",
                                "Counts above maxCount (99 by default) show as 99+. Click an icon to read everything; its badge scales out.",
                                counts()),
                        SampleCard.large("Presence on avatars",
                                "The DOT variant with CIRCULAR overlap sits on the edge of a round avatar; -rx-badge-ring cuts it out from the avatar.",
                                avatars()),
                        SampleCard.large("Service status",
                                "A badge without content is a standalone dot, used here as a label's graphic; pulsing adds a spreading ring.",
                                services()),
                        SampleCard.large("Menu labels",
                                "Standalone badges as pills at the end of menu rows: counts for folders, short text for new features.",
                                menu()),
                        SampleCard.large("Show zero",
                                "A count of 0 hides the badge by default (left); showZero keeps the 0 on screen (right).",
                                showZero()),
                        SampleCard.large("Square and round content",
                                "RECTANGULAR overlap (the default) puts the badge on the bounding box corner; CIRCULAR moves it onto the circle.",
                                overlap())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Notification counts ====================

    private static Node counts() {
        RXBadge bell = toolBadge("bell-icon", 3, null);
        RXBadge mail = toolBadge("mail-icon", 12, "primary");
        RXBadge cart = toolBadge("cart-icon", 128, "warning");
        HBox tools = new HBox(28.0, bell, mail, cart);
        tools.setAlignment(Pos.CENTER);

        RXButton receive = plain("Receive new items");
        receive.setOnAction(event -> {
            bell.setCount(bell.getCount() + 1);
            mail.setCount(mail.getCount() + 4);
            cart.setCount(cart.getCount() + 1);
        });
        VBox box = new VBox(26.0, tools, receive);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static RXBadge toolBadge(String iconClass, int count, String color) {
        RXButton tool = new RXButton(null, icon(iconClass));
        tool.getStyleClass().add("tool");
        RXBadge badge = new RXBadge(tool);
        badge.setCount(count);
        if (color != null) {
            badge.getStyleClass().add(color);
        }
        tool.setOnAction(event -> badge.setCount(0));
        return badge;
    }

    // ==================== Presence on avatars ====================

    private static Node avatars() {
        HBox row = new HBox(22.0,
                person("LW", "Lee Wyatt", "Online", "teal", "success"),
                person("RV", "Reviewer", "Away", "violet", "warning"),
                person("SP", "Support", "Busy", "orange", "danger"),
                person("GU", "Guest", "Offline", "sky", "info"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node person(String initials, String name, String status, String fill, String color) {
        RXAvatar avatar = new RXAvatar();
        avatar.setText(initials);
        avatar.getStyleClass().add(fill);
        avatar.setPrefSize(52.0, 52.0);
        RXBadge badge = new RXBadge(avatar);
        badge.setVariant(RXBadge.Variant.DOT);
        badge.setOverlap(RXBadge.Overlap.CIRCULAR);
        badge.setPosition(RXBadge.Position.BOTTOM_END);
        badge.getStyleClass().addAll("presence", color);
        VBox box = new VBox(4.0, badge, text(name, "copy"), text(status, "muted"));
        box.setAlignment(Pos.CENTER);
        VBox.setMargin(badge, new Insets(0, 0, 6.0, 0));
        return box;
    }

    // ==================== Service status ====================

    private static Node services() {
        VBox list = new VBox(0.0,
                service("API", "Operational", "success", false),
                service("Search", "Slow responses", "warning", false),
                service("Payments", "Outage", "danger", false),
                service("Deploy", "Rolling out v2.4", "primary", true));
        list.getStyleClass().add("panel");
        list.setPrefWidth(340.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static Node service(String name, String status, String color, boolean pulsing) {
        RXBadge dot = new RXBadge();
        dot.setVariant(RXBadge.Variant.DOT);
        dot.setPulsing(pulsing);
        dot.getStyleClass().addAll("status-dot", color);
        Label label = new Label(status, dot);
        label.getStyleClass().add("muted");
        label.setGraphicTextGap(10.0);
        HBox row = new HBox(text(name, "copy"), spacer(), label);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Menu labels ====================

    private static Node menu() {
        VBox list = new VBox(0.0,
                menuRow("inbox-icon", "Inbox", countPill(24, "primary")),
                menuRow("note-icon", "Drafts", countPill(2, "info")),
                menuRow("chart-icon", "Reports", textPill("New", "success")),
                menuRow("bolt-icon", "Automations", textPill("Beta", "warning")));
        list.getStyleClass().add("panel");
        list.setPrefWidth(300.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static Node menuRow(String iconClass, String name, Node badge) {
        Label label = new Label(name, icon(iconClass));
        label.getStyleClass().add("copy");
        label.setGraphicTextGap(12.0);
        HBox row = new HBox(label, spacer(), badge);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static RXBadge countPill(int count, String color) {
        RXBadge badge = new RXBadge();
        badge.setCount(count);
        badge.getStyleClass().addAll("pill", color);
        return badge;
    }

    private static RXBadge textPill(String value, String color) {
        RXBadge badge = new RXBadge();
        badge.setText(value);
        badge.getStyleClass().addAll("pill", color);
        return badge;
    }

    // ==================== Show zero ====================

    private static Node showZero() {
        IntegerProperty count = new SimpleIntegerProperty(0);
        RXBadge hidden = cartBadge(count, false);
        RXBadge shown = cartBadge(count, true);

        RXButton minus = plain("-");
        minus.setOnAction(event -> count.set(Math.max(0, count.get() - 1)));
        RXButton plus = plain("+");
        minus.setPrefWidth(36.0);
        plus.setPrefWidth(36.0);
        plus.setOnAction(event -> count.set(count.get() + 1));
        Label value = new Label();
        value.getStyleClass().add("figure");
        value.textProperty().bind(count.asString("%d in cart"));
        value.setMinWidth(90.0);
        value.setAlignment(Pos.CENTER);
        HBox stepper = new HBox(10.0, minus, value, plus);
        stepper.setAlignment(Pos.CENTER);

        HBox carts = new HBox(56.0, labeled(hidden, "Default"), labeled(shown, "showZero"));
        carts.setAlignment(Pos.CENTER);
        VBox box = new VBox(22.0, carts, stepper);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static RXBadge cartBadge(IntegerProperty count, boolean showZero) {
        RXButton tool = new RXButton(null, icon("cart-icon"));
        tool.getStyleClass().add("tool");
        RXBadge badge = new RXBadge(tool);
        badge.countProperty().bind(count);
        badge.setShowZero(showZero);
        badge.getStyleClass().add("primary");
        return badge;
    }

    private static Node labeled(Node node, String caption) {
        VBox box = new VBox(10.0, node, text(caption, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Square and round content ====================

    private static Node overlap() {
        StackPane tile = new StackPane(icon("photo-icon"));
        tile.getStyleClass().add("app-tile");
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXBadge square = new RXBadge(tile);
        square.setCount(5);

        RXBadge roundRect = new RXBadge(roundAvatar());
        roundRect.setCount(5);

        RXBadge roundCircle = new RXBadge(roundAvatar());
        roundCircle.setCount(5);
        roundCircle.setOverlap(RXBadge.Overlap.CIRCULAR);

        HBox row = new HBox(40.0,
                labeled(square, "Square, RECTANGULAR"),
                labeled(roundRect, "Round, RECTANGULAR"),
                labeled(roundCircle, "Round, CIRCULAR"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXAvatar roundAvatar() {
        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.getStyleClass().add("indigo");
        avatar.setPrefSize(56.0, 56.0);
        return avatar;
    }
}
