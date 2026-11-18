package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXChip page: filter chips with a live count, removable tags, chips with icons and
 * avatars, status colors, quick-reply suggestion chips and font-driven sizes.
 */
public final class RXChipSamples {

    private static final List<String> TAGS = List.of("javafx", "desktop", "ui", "open-source", "java17");

    private RXChipSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A compact pill for a tag, a filter, a person or a quick action, with an optional check and remove button.",
                List.of("Short pieces of information users can toggle, remove or act on, shown inline with other content.",
                        "To keep a group of chips in one selection use RXChipSet; to type new tags use RXChipInput."),
                List.of(
                        SampleCard.large("Filter chips",
                                "selectable chips toggle on a click and show a check while selected.",
                                filters()),
                        SampleCard.large("Removable tags",
                                "removable adds a close button; the onRemove handler takes the chip out of its row.",
                                tags()),
                        SampleCard.large("Icons and avatars",
                                "Any node can be the graphic: an icon for an action, an avatar for a person.",
                                graphics()),
                        SampleCard.large("Status colors",
                                "Style classes recolor the background and text for each order status.",
                                statuses()),
                        SampleCard.large("Suggested replies",
                                "Plain chips act like buttons: clicking one puts its text in the reply.",
                                replies()),
                        SampleCard.large("Sizes",
                                "Paddings and the check are in em, so -fx-font-size scales the whole chip.",
                                sizes())),
                SelectionIcons.STYLESHEET);
    }

    private static Node filters() {
        RXFlowPane row = flow(
                filter("Vegetarian", true), filter("Gluten free", false), filter("Spicy", false),
                filter("Under 30 min", true), filter("Kids", false));
        Label count = text("", "hint");
        Runnable update = () -> {
            long on = row.getChildren().stream().filter(n -> ((RXChip) n).isSelected()).count();
            count.setText(on == 0 ? "Showing all 124 recipes" : "Showing " + (124 / (on * 3)) + " recipes");
        };
        row.getChildren().forEach(n -> ((RXChip) n).selectedProperty().addListener((obs, was, now) -> update.run()));
        update.run();
        return column(row, count);
    }

    private static RXChip filter(String text, boolean selected) {
        RXChip chip = new RXChip(text);
        chip.setSelectable(true);
        chip.setSelected(selected);
        return chip;
    }

    private static Node tags() {
        RXFlowPane row = flow();
        RXButton reset = new RXButton("Restore tags");
        reset.setOnAction(event -> fillTags(row));
        fillTags(row);
        return column(row, reset);
    }

    private static void fillTags(RXFlowPane row) {
        row.getChildren().clear();
        for (String tag : TAGS) {
            RXChip chip = new RXChip(tag);
            chip.setRemovable(true);
            chip.setOnRemove(event -> row.getChildren().remove(chip));
            row.getChildren().add(chip);
        }
    }

    private static Node graphics() {
        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.setPrefSize(22.0, 22.0);
        avatar.getStyleClass().add("chip-avatar");
        RXChip person = new RXChip("Lee Wyatt", avatar);
        person.getStyleClass().add("person");
        person.setRemovable(true);
        return flow(
                new RXChip("Add to calendar", icon("calendar-icon")),
                new RXChip("Directions", icon("directions-icon")),
                new RXChip("Share", icon("share-icon")),
                person);
    }

    private static Node statuses() {
        return flow(status("Paid", "lime"), status("Pending", "amber"), status("Overdue", "rose"),
                status("Draft", "sky"), status("Refunded", "violet"));
    }

    private static RXChip status(String text, String color) {
        RXChip chip = new RXChip(text);
        chip.getStyleClass().addAll("status", color);
        return chip;
    }

    private static Node replies() {
        Label reply = text("Reply: —", "reply");
        RXFlowPane row = flow();
        for (String text : List.of("Sounds good!", "On my way", "Can we talk later?", "Thanks a lot")) {
            RXChip chip = new RXChip(text);
            chip.setOnAction(event -> reply.setText("Reply: " + text));
            row.getChildren().add(chip);
        }
        return column(row, reply);
    }

    private static Node sizes() {
        RXFlowPane row = flow(sized("Small", "chip-small"), sized("Default", null), sized("Large", "chip-large"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXChip sized(String text, String styleClass) {
        RXChip chip = new RXChip(text);
        chip.setSelectable(true);
        chip.setSelected(true);
        if (styleClass != null) {
            chip.getStyleClass().add(styleClass);
        }
        return chip;
    }

    private static RXFlowPane flow(Node... chips) {
        RXFlowPane flow = new RXFlowPane(8.0, 8.0, chips);
        flow.setPrefWrapLength(420.0);
        flow.setMaxWidth(440.0);
        return flow;
    }

    private static Node column(Node... nodes) {
        VBox box = new VBox(12.0, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(440.0);
        return box;
    }
}
