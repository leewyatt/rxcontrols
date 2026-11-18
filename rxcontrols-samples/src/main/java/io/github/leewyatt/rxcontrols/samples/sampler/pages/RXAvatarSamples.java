package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.PORTRAIT;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.TIGER;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.image;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.wrapped;

/**
 * The RXAvatar page: an overlapping team stack, the image / initials / icon fallback,
 * sizes, rounded squares, a contact list with presence and a comment thread.
 */
public final class RXAvatarSamples {

    private RXAvatarSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A round or rounded-square picture of a person or a team that falls back to initials or an icon.",
                List.of("Show who wrote, owns or joined something: comments, contact lists, team members, account menus.",
                        "For a decorative photo with a custom outline use RXClipPathImageView; for any picture use RXImageView."),
                List.of(
                        SampleCard.medium("Team stack",
                                "Avatars overlap in a row and a last one counts the members who do not fit.",
                                teamStack()),
                        SampleCard.medium("Sizes",
                                "The avatar stays at its preferred size, so setPrefSize is all a size takes; the initials scale with -fx-font-size.",
                                sizes()),
                        SampleCard.large("Fallbacks",
                                "An image wins over text and text wins over the icon; a broken image link falls back to the initials.",
                                fallbacks()),
                        SampleCard.large("Rounded squares",
                                "ShapeType.SQUARE with arcWidth and arcHeight suits teams, workspaces and apps.",
                                squares()),
                        SampleCard.medium("Contacts",
                                "An RXBadge dot on each avatar shows whether the person is online.",
                                contacts()),
                        SampleCard.medium("Comment thread",
                                "A small avatar beside each comment; replies sit one step in.",
                                thread())),
                MediaKit.STYLESHEET);
    }

    // ==================== Team stack ====================

    private static Node teamStack() {
        HBox stack = new HBox(-12.0,
                ringed(photo(PORTRAIT, 48.0)),
                ringed(initials("LW", "teal", 48.0)),
                ringed(photo(TIGER, 48.0)),
                ringed(initials("MK", "violet", 48.0)),
                ringed(initials("JS", "orange", 48.0)),
                ringed(initials("+5", "more", 48.0)));
        stack.setAlignment(Pos.CENTER);
        VBox box = new VBox(10.0, stack, text("Design team, 10 members", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node ringed(RXAvatar avatar) {
        StackPane ring = new StackPane(avatar);
        ring.getStyleClass().add("avatar-ring");
        return ring;
    }

    // ==================== Fallbacks ====================

    private static Node fallbacks() {
        RXAvatar broken = new RXAvatar(new Image("file:/missing/lee-wyatt.png", true));
        broken.setText("LW");
        broken.getStyleClass().addAll("rose", fontClass(56.0));
        broken.setPrefSize(56.0, 56.0);
        RXAvatar icon = new RXAvatar();
        icon.setPrefSize(56.0, 56.0);
        HBox row = new HBox(22.0,
                labeled(photo(PORTRAIT, 56.0), "Image"),
                labeled(initials("LW", "indigo", 56.0), "Text"),
                labeled(broken, "Broken link"),
                labeled(icon, "Nothing set"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node labeled(Node node, String caption) {
        VBox box = new VBox(8.0, node, text(caption, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Sizes ====================

    private static Node sizes() {
        HBox row = new HBox(10.0, initials("LW", "sky", 24.0), initials("LW", "sky", 32.0),
                initials("LW", "sky", 40.0), initials("LW", "sky", 56.0), initials("LW", "sky", 64.0));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ==================== Rounded squares ====================

    private static Node squares() {
        HBox row = new HBox(16.0,
                labeled(square(initials("DS", "violet", 56.0), 14.0), "Design"),
                labeled(square(initials("MK", "amber", 56.0), 14.0), "Marketing"),
                labeled(square(photo(TIGER, 56.0), 14.0), "Mascot"),
                labeled(square(photo(PORTRAIT, 56.0), 28.0), "Arc 28"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXAvatar square(RXAvatar avatar, double arc) {
        avatar.setShapeType(RXAvatar.ShapeType.SQUARE);
        avatar.setArcWidth(arc);
        avatar.setArcHeight(arc);
        return avatar;
    }

    // ==================== Contacts ====================

    private static Node contacts() {
        VBox list = new VBox(12.0,
                contact(photo(PORTRAIT, 40.0), "Lee Wyatt", "@LeeWyatt_7788", "success"),
                contact(initials("AN", "teal", 40.0), "Ana Novak", "Product designer", "warning"),
                contact(initials("TO", "pink", 40.0), "Tom Okafor", "Support lead", "info"));
        return fixedWidth(list, 230.0);
    }

    private static Node contact(RXAvatar avatar, String name, String detail, String status) {
        RXBadge badge = new RXBadge(avatar);
        badge.setVariant(RXBadge.Variant.DOT);
        badge.setOverlap(RXBadge.Overlap.CIRCULAR);
        badge.setPosition(RXBadge.Position.BOTTOM_END);
        badge.getStyleClass().addAll("presence", status);
        VBox names = new VBox(1.0, text(name, "copy-strong"), text(detail, "muted"));
        HBox row = new HBox(12.0, badge, names);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Comment thread ====================

    private static Node thread() {
        Node first = comment(photo(PORTRAIT, 32.0), "Lee Wyatt", "2 h",
                "The new palette reads much better on dark screens.");
        Node reply = comment(initials("AN", "teal", 28.0), "Ana Novak", "1 h",
                "Agreed, shipping it with the next build.");
        VBox.setMargin(reply, new Insets(0, 0, 0, 42.0));
        VBox thread = new VBox(12.0, first, reply);
        return fixedWidth(thread, 260.0);
    }

    private static Node comment(RXAvatar avatar, String name, String age, String body) {
        HBox head = new HBox(6.0, text(name, "copy-strong"), text(age, "muted"));
        head.setAlignment(Pos.BASELINE_LEFT);
        VBox texts = new VBox(2.0, head, wrapped(body, "copy"));
        HBox row = new HBox(10.0, avatar, texts);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    // ==================== Helpers ====================

    private static RXAvatar photo(String path, double size) {
        RXAvatar avatar = new RXAvatar(image(path));
        avatar.setPrefSize(size, size);
        return avatar;
    }

    private static RXAvatar initials(String value, String color, double size) {
        RXAvatar avatar = new RXAvatar();
        avatar.setText(value);
        avatar.getStyleClass().addAll(color, fontClass(size));
        avatar.setPrefSize(size, size);
        return avatar;
    }

    private static String fontClass(double size) {
        if (size < 30.0) {
            return "size-xs";
        }
        if (size < 38.0) {
            return "size-sm";
        }
        if (size < 46.0) {
            return "size-md";
        }
        if (size < 64.0) {
            return "size-lg";
        }
        return "size-xl";
    }
}
