package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.icon;

/**
 * The RXFillLabel page: colored topic tags, a side menu, section headings, a choice of
 * sweep directions and a label flashed from code when a value changes.
 */
public final class RXFillLabelSamples {

    private static final String[][] TAGS = {
            {"JavaFX", "indigo"}, {"Design", "pink"}, {"Animation", "violet"}, {"Open source", "teal"},
            {"Performance", "orange"}, {"Accessibility", "sky"}, {"Testing", "lime"}, {"Release notes", "rose"}};

    private RXFillLabelSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A label whose background fills with a color sweep on hover, for text that is not a button.",
                List.of("Tags, menu entries, list items and headings that should react to the pointer without being actions.",
                        "For a clickable action with the same sweep use RXFillButton."),
                List.of(
                        SampleCard.wide("Topic tags",
                                "Hover a tag: each color class sets -rx-fill, which colors the sweep and, in this stylesheet, the outline and text.",
                                tags()),
                        SampleCard.medium("Side menu",
                                "Full-width menu rows with icons; the text turns white while the fill covers it.",
                                menu()),
                        SampleCard.medium("Section headings",
                                "A heading underlined in color fills from the bottom up with BOTTOM_TO_TOP.",
                                headings()),
                        SampleCard.medium("Sweep directions",
                                "fillAnimation picks the sweep; LEFT_TO_RIGHT is the default.",
                                directions()),
                        SampleCard.medium("Flash from code",
                                "With the NONE trigger, playAnimation() plays the sweep once; here when a new order arrives.",
                                flash())),
                MediaKit.STYLESHEET);
    }

    // ==================== Topic tags ====================

    private static Node tags() {
        RXFlowPane flow = new RXFlowPane(10.0, 10.0);
        flow.setAlignment(Pos.CENTER);
        flow.setPrefWrapLength(450.0);
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        for (String[] tag : TAGS) {
            RXFillLabel label = new RXFillLabel("#" + tag[0]);
            label.getStyleClass().addAll("tag", tag[1]);
            label.setFillAnimation(FillAnimation.CENTER_OUT);
            flow.getChildren().add(label);
        }
        return flow;
    }

    // ==================== Side menu ====================

    private static Node menu() {
        VBox menu = new VBox(2.0,
                menuItem("Inbox", "inbox-icon"),
                menuItem("Starred", "star-icon"),
                menuItem("Drafts", "draft-icon"),
                menuItem("Archive", "archive-icon"));
        menu.getStyleClass().add("menu-panel");
        return fixedWidth(menu, 210.0);
    }

    private static Node menuItem(String name, String iconClass) {
        RXFillLabel item = new RXFillLabel(name, icon(iconClass, "menu-icon"));
        item.getStyleClass().addAll("menu-item", "indigo");
        item.setGraphicTextGap(12.0);
        item.setMaxWidth(Double.MAX_VALUE);
        return item;
    }

    // ==================== Section headings ====================

    private static Node headings() {
        VBox box = new VBox(14.0, heading("Getting started", "orange"), heading("Components", "teal"),
                heading("Theming", "violet"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node heading(String value, String color) {
        RXFillLabel heading = new RXFillLabel(value);
        heading.getStyleClass().addAll("heading-fill", color);
        heading.setFillAnimation(FillAnimation.BOTTOM_TO_TOP);
        return heading;
    }

    // ==================== Sweep directions ====================

    private static Node directions() {
        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.add(direction("Left to right", FillAnimation.LEFT_TO_RIGHT, "sky"), 0, 0);
        grid.add(direction("Circle", FillAnimation.CIRCLE, "pink"), 1, 0);
        grid.add(direction("Edges in", FillAnimation.EDGES_IN, "amber"), 0, 1);
        grid.add(direction("Zigzag", FillAnimation.ZIGZAG, "lime"), 1, 1);
        grid.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node direction(String value, FillAnimation animation, String color) {
        RXFillLabel label = new RXFillLabel(value);
        label.getStyleClass().addAll("tag", "square", color);
        label.setFillAnimation(animation);
        label.setPrefWidth(120.0);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    // ==================== Flash from code ====================

    private static Node flash() {
        RXFillLabel orders = new RXFillLabel("Orders today: 24");
        orders.getStyleClass().addAll("tag", "square", "lime");
        orders.setAnimationTrigger(AnimationTrigger.NONE);
        orders.setFillAnimation(FillAnimation.CENTER_OUT);
        int[] count = {24};
        RXButton order = new RXButton("Simulate an order");
        order.setOnAction(event -> {
            count[0]++;
            orders.setText("Orders today: " + count[0]);
            orders.playAnimation();
        });
        VBox box = new VBox(14.0, orders, order);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
