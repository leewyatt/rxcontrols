package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;

/**
 * The RXFlowPane page: a centered tag cloud next to a FlowPane with the same tags,
 * filter chips that reflow in a resizable frame, editable tags, shortcuts in a vertical
 * flow, mixed text sizes on one baseline and dialog buttons that wrap to the right.
 */
public final class RXFlowPaneSamples {

    private static final double GAP = 8.0;
    private static final double CLOUD_WIDTH = 280.0;
    private static final String[] TAGS = {"JavaFX", "Layout", "CSS", "Charts", "Animation",
        "Controls", "Themes", "Open source"};
    private static final String[] COLORS = {"rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet",
        "pink"};

    private RXFlowPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A flow layout that wraps its children into rows or columns, aligns the whole block once and keeps a short last row in line with the rows above it.",
                List.of("Tags, chips, filters and button groups whose items keep their own size and wrap when space runs out.",
                        "For same-size tiles use RXTilePane; for a responsive column grid use RXRow and RXCol."),
                List.of(
                        SampleCard.wide("Centered tag cloud",
                                "Both panes center their content; FlowPane centers the short last row on its own, RXFlowPane keeps it at the left edge of the centered block (rowHalignment LEFT).",
                                comparison()),
                        SampleCard.canvas("Filter chips",
                                "Drag the frame edge: the chips wrap into new rows and glide there, because animated is on by default.",
                                filters()),
                        SampleCard.large("Editable tags",
                                "Add a tag or remove one with its close button; the other tags glide into the freed space.",
                                editableTags()),
                        SampleCard.large("Vertical flow",
                                "orientation VERTICAL fills a column to the pane's height and then starts the next column.",
                                shortcuts()),
                        SampleCard.medium("Mixed sizes on a baseline",
                                "rowValignment BASELINE lines up text of different sizes in every row.",
                                baseline()),
                        SampleCard.medium("Dialog buttons",
                                "alignment TOP_RIGHT with rowHalignment RIGHT keeps each row of wrapped buttons flush right.",
                                dialog())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Tag cloud ====================

    private static Node[] comparison() {
        FlowPane plain = new FlowPane(GAP, GAP);
        plain.setAlignment(Pos.TOP_CENTER);
        RXFlowPane fixed = new RXFlowPane(GAP, GAP);
        fixed.setAlignment(Pos.TOP_CENTER);
        for (int i = 0; i < TAGS.length; i++) {
            plain.getChildren().add(tag(TAGS[i], COLORS[i]));
            fixed.getChildren().add(tag(TAGS[i], COLORS[i]));
        }
        return new Node[] {labeled("FlowPane", plain), labeled("RXFlowPane", fixed)};
    }

    private static Node labeled(String name, Pane pane) {
        pane.setPrefWidth(CLOUD_WIDTH);
        pane.setMaxWidth(Region.USE_PREF_SIZE);
        pane.getStyleClass().add("cloud");
        VBox box = new VBox(8.0, text(name, "code"), pane);
        return box;
    }

    private static Label tag(String name, String color) {
        Label tag = text(name, "chip", color);
        tag.setMinWidth(Region.USE_PREF_SIZE);
        return tag;
    }

    // ==================== Filter chips ====================

    private static Node filters() {
        String[][] chips = {
            {"Vegetarian", "lime"}, {"Under 30 min", "orange"}, {"Gluten free", "amber"},
            {"High protein", "rose"}, {"Budget", "teal"}, {"One pot", "sky"},
            {"Kid friendly", "pink"}, {"Spicy", "rose"}, {"Meal prep", "indigo"},
            {"No oven", "violet"}, {"Dairy free", "teal"}, {"Breakfast", "amber"}};
        RXFlowPane flow = new RXFlowPane(GAP, GAP);
        for (String[] chip : chips) {
            flow.getChildren().add(filterChip(chip[0], chip[1]));
        }
        return widthFrame(flow, 220.0);
    }

    private static Node filterChip(String name, String color) {
        Region dot = new Region();
        dot.getStyleClass().addAll("chip-dot", color);
        dot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox chip = new HBox(6.0, dot, text(name, "copy"));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("filter-chip");
        chip.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        chip.setOnMouseClicked(event -> {
            if (chip.getStyleClass().contains("on")) {
                chip.getStyleClass().remove("on");
            } else {
                chip.getStyleClass().add("on");
            }
        });
        return chip;
    }

    // ==================== Editable tags ====================

    private static Node editableTags() {
        RXFlowPane flow = new RXFlowPane(6.0, 6.0);
        flow.setPrefWidth(340.0);
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        String[] start = {"travel", "photography", "hiking", "coffee", "maps", "film"};
        for (int i = 0; i < start.length; i++) {
            flow.getChildren().add(removable(flow, start[i], COLORS[i]));
        }

        TextField input = new TextField();
        input.setPromptText("New tag");
        input.setPrefColumnCount(12);
        RXButton add = solid("Add", "teal");
        int[] next = {start.length};
        Runnable addTag = () -> {
            String value = input.getText() == null ? "" : input.getText().trim();
            if (value.isEmpty()) {
                value = "tag " + (next[0] + 1);
            }
            flow.getChildren().add(removable(flow, value, COLORS[next[0] % COLORS.length]));
            next[0]++;
            input.clear();
        };
        add.setOnAction(event -> addTag.run());
        input.setOnAction(event -> addTag.run());
        HBox entry = new HBox(8.0, input, add);
        entry.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(14.0, entry, flow);
        box.setPrefHeight(150.0);
        return box;
    }

    private static Node removable(RXFlowPane flow, String name, String color) {
        StackPane close = iconButton("close-icon");
        HBox tag = new HBox(2.0, text(name, "copy"), close);
        tag.setAlignment(Pos.CENTER_LEFT);
        tag.getStyleClass().addAll("removable-tag", color);
        tag.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        close.setOnMouseClicked(event -> flow.getChildren().remove(tag));
        return tag;
    }

    // ==================== Vertical flow ====================

    private static Node shortcuts() {
        String[][] keys = {
            {"Ctrl+N", "New file"}, {"Ctrl+O", "Open"}, {"Ctrl+S", "Save"}, {"Ctrl+P", "Print"},
            {"Ctrl+Z", "Undo"}, {"Ctrl+Y", "Redo"}, {"Ctrl+F", "Find"}, {"Ctrl+H", "Replace"},
            {"F5", "Run"}, {"F11", "Full screen"}};
        RXFlowPane flow = new RXFlowPane(24.0, 10.0);
        flow.setOrientation(Orientation.VERTICAL);
        flow.setPrefWrapLength(150.0);
        for (String[] key : keys) {
            Label cap = text(key[0], "key-cap");
            cap.setMinWidth(64.0);
            HBox item = new HBox(8.0, cap, text(key[1], "copy"));
            item.setAlignment(Pos.CENTER_LEFT);
            flow.getChildren().add(item);
        }
        flow.setPrefHeight(150.0);
        flow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return flow;
    }

    // ==================== Baseline ====================

    private static Node baseline() {
        RXFlowPane flow = new RXFlowPane(10.0, 6.0);
        flow.setRowValignment(VPos.BASELINE);
        flow.getChildren().addAll(text("Lee Wyatt", "name-large"), text("@LeeWyatt_7788", "muted"),
                tag("Pro", "violet"), text("Lisbon", "copy"), text("128 posts", "price-small"),
                text("joined 2019", "muted"));
        flow.setPrefWidth(250.0);
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        flow.getStyleClass().add("panel");
        return flow;
    }

    // ==================== Dialog buttons ====================

    private static Node dialog() {
        RXFlowPane buttons = new RXFlowPane(8.0, 8.0, plain("Don't save"), plain("Save as..."),
                plain("Cancel"), solid("Save", "indigo"));
        buttons.setAlignment(Pos.TOP_RIGHT);
        buttons.setRowHalignment(HPos.RIGHT);

        HBox head = new HBox(10.0, icon("warning-icon", "amber", "large"), text("Save changes?", "headline"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10.0, head, wrapped("Your edits to \"Trip plan.md\" will be lost if you close without saving.", "muted"),
                buttons);
        box.getStyleClass().add("panel");
        box.setPrefWidth(250.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }
}
