package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.COMPACT_PROFILE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.breakpointBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.chip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;

/**
 * The RXMasonryPane page: a photo wall and a notes board that reflow in resizable
 * frames, a board where notes fade in and out, a featured post that spans two columns
 * and fixed-width columns centered in a wider pane.
 */
public final class RXMasonryPaneSamples {

    private static final String[] NOTE_COLORS = {"amber", "lime", "sky", "pink", "violet", "orange", "teal"};
    private static final String[] NOTES = {
        "Buy oat milk",
        "Call the landlord about the leaking tap in the kitchen before Friday",
        "Book flights to Porto",
        "Ideas for the talk: start with the demo, then explain the layout model, end with questions",
        "Water the plants",
        "Return library books",
        "Draft the newsletter and pick three photos from the hike",
        "Dentist, Tuesday 9:30"};

    private RXMasonryPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A masonry layout that stacks children of different heights into equal-width columns, always filling the shortest column next.",
                List.of("Photo walls, pinboards and feeds of cards whose heights differ.",
                        "For thousands of items use the virtualized RXMasonryView; for same-size tiles use RXTilePane."),
                List.of(
                        SampleCard.canvas("Photo wall",
                                "Drag the frame edge: columnWidth 150 sets how many columns fit, and the photos glide to their new columns.",
                                photoWall()),
                        SampleCard.canvas("Columns per breakpoint",
                                "setXs(1), setSm(2), setMd(3) and setLg(4) fix the column count for each breakpoint of the pane's width.",
                                breakpoints()),
                        SampleCard.canvas("Add and remove",
                                "New notes fade in; removeAnimated fades a note out while the rest move up to fill the gap.",
                                board()),
                        SampleCard.large("Featured post",
                                "RXMasonryPane.setColumnSpan(post, 2) lets the featured post cover two of the three columns.",
                                featured()),
                        SampleCard.large("Fixed-width columns",
                                "With fillWidth off the columns keep columnWidth, and alignment TOP_CENTER centers them.",
                                centered())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Photo wall ====================

    private static Node photoWall() {
        RXMasonryPane wall = new RXMasonryPane();
        wall.setColumnWidth(150.0);
        wall.setHgap(10.0);
        wall.setVgap(10.0);
        String[] captions = {"Palm beach", "Maple bridge", "River walk", "Cherry lane", "Turquoise bay",
            "Old bridge", "Riverside town", "Blossom path"};
        double[] heights = {120.0, 170.0, 100.0, 150.0, 110.0, 160.0, 130.0, 100.0};
        for (int i = 0; i < captions.length; i++) {
            RXImageView photo = new RXImageView(scenery(i % 4 + 1));
            photo.setPrefHeight(heights[i]);
            photo.setMinHeight(heights[i]);
            photo.setImageRadius(8.0);
            VBox card = new VBox(6.0, photo, text(captions[i], "copy"));
            wall.getChildren().add(card);
        }
        Label columns = text("", "frame-size");
        columns.setMinWidth(Region.USE_PREF_SIZE);
        wall.actualColumnCountProperty().addListener((obs, old, count) -> columns.setText(count + " columns"));
        return widthFrame(wall, 220.0, columns);
    }

    // ==================== Breakpoints ====================

    private static Node breakpoints() {
        RXMasonryPane board = new RXMasonryPane();
        board.setBreakpointProfile(COMPACT_PROFILE);
        board.setXs(1);
        board.setSm(2);
        board.setMd(3);
        board.setLg(4);
        for (int i = 0; i < NOTES.length; i++) {
            board.getChildren().add(note(NOTES[i], NOTE_COLORS[i % NOTE_COLORS.length], null));
        }
        return widthFrame(board, 260.0, breakpointBadge(board.activeBreakpointProperty()));
    }

    // ==================== Add and remove ====================

    private static Node board() {
        RXMasonryPane board = new RXMasonryPane();
        board.setColumnWidth(180.0);
        board.setMaxColumns(4);
        for (int i = 0; i < 5; i++) {
            board.getChildren().add(note(NOTES[i], NOTE_COLORS[i], board));
        }
        int[] next = {5};
        RXButton add = solid("Add note", "amber");
        add.setOnAction(event -> {
            int index = next[0]++;
            board.getChildren().add(note(NOTES[index % NOTES.length], NOTE_COLORS[index % NOTE_COLORS.length], board));
        });
        RXButton clear = LayoutKit.plain("Clear all");
        clear.setOnAction(event -> board.clearAnimated());
        HBox actions = new HBox(8.0, add, clear);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(14.0, actions, board);
        box.setMinHeight(200.0);
        return box;
    }

    private static Node note(String body, String color, RXMasonryPane removable) {
        Label text = wrapped(body, "note-text");
        VBox note = new VBox(6.0);
        note.getStyleClass().addAll("note", color);
        if (removable != null) {
            StackPane close = iconButton("close-icon");
            close.setMinWidth(Region.USE_PREF_SIZE);
            close.setOnMouseClicked(event -> removable.removeAnimated(note));
            HBox top = new HBox(4.0, text, close);
            HBox.setHgrow(text, Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            top.setAlignment(Pos.TOP_LEFT);
            note.getChildren().add(top);
        } else {
            note.getChildren().add(text);
        }
        return note;
    }

    // ==================== Featured post ====================

    private static Node featured() {
        RXMasonryPane feed = new RXMasonryPane();
        feed.setColumnCount(3);
        feed.setHgap(8.0);
        feed.setVgap(8.0);

        RXImageView photo = new RXImageView(scenery(1));
        photo.setPrefHeight(110.0);
        photo.setMinHeight(110.0);
        photo.setImageRadius(8.0);
        VBox post = new VBox(6.0, photo, chip("Featured", "rose"), text("Ten quiet lakes to visit", "copy-strong"));
        post.getStyleClass().add("post");
        RXMasonryPane.setColumnSpan(post, 2);
        feed.getChildren().add(post);

        String[][] items = {{"Packing list", "sky"}, {"Trail map", "lime"}, {"Night sky", "indigo"},
            {"Camp recipes", "orange"}, {"Gear review", "teal"}};
        double[] heights = {70.0, 110.0, 60.0, 90.0, 70.0};
        for (int i = 0; i < items.length; i++) {
            Region color = new Region();
            color.getStyleClass().addAll("product-photo", items[i][1]);
            color.setPrefHeight(heights[i]);
            VBox item = new VBox(6.0, color, text(items[i][0], "copy"));
            item.getStyleClass().add("post");
            feed.getChildren().add(item);
        }
        feed.setPrefWidth(380.0);
        feed.setMaxWidth(Region.USE_PREF_SIZE);
        return feed;
    }

    // ==================== Fixed-width columns ====================

    private static Node centered() {
        RXMasonryPane pane = new RXMasonryPane();
        pane.setColumnWidth(100.0);
        pane.setFillWidth(false);
        pane.setAlignment(Pos.TOP_CENTER);
        String[] colors = {"rose", "amber", "teal", "violet", "sky", "lime", "orange"};
        double[] heights = {60.0, 90.0, 40.0, 70.0, 50.0, 80.0, 45.0};
        for (int i = 0; i < colors.length; i++) {
            Region block = new Region();
            block.getStyleClass().addAll("block", colors[i]);
            block.setPrefSize(100.0, heights[i]);
            block.setMinHeight(heights[i]);
            pane.getChildren().add(block);
        }
        pane.setPrefWidth(380.0);
        pane.setMaxWidth(Region.USE_PREF_SIZE);
        pane.getStyleClass().add("grid-area");
        return pane;
    }
}
