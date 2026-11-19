package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Icons, text helpers, buttons, framed "windows" and the stylesheet shared by the
 * navigation and pane pages. Each icon is a shape-backed region whose shape comes from
 * its style class in the stylesheet.
 */
final class NavKit {

    static final String STYLESHEET =
            NavKit.class.getResource("rx-navigation-samples.css").toExternalForm();

    // The inner radius of the frame's border in the stylesheet (10px outside, 1px wide).
    private static final double INNER_RADIUS = 9.0;

    private NavKit() {
    }

    static Region icon(String styleClass, String... more) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        icon.getStyleClass().addAll(more);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    /** A clickable icon: a transparent wrapper takes the clicks, the icon inside ignores them. */
    static StackPane iconButton(String styleClass) {
        Region icon = icon(styleClass);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("icon-button");
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return button;
    }

    static Label text(String value, String... styleClasses) {
        Label label = new Label(value);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    static Label wrapped(String value, String... styleClasses) {
        Label label = text(value, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static RXButton solid(String value, String... styleClasses) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("solid");
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    static RXButton plain(String value) {
        return new RXButton(value);
    }

    /** A small colored pill, used as a count or status next to a name. */
    static Label pill(String value, String color) {
        Label pill = text(value, "pill", color);
        pill.setMinWidth(Region.USE_PREF_SIZE);
        return pill;
    }

    /**
     * A rounded "window" of a fixed size, clipped so that nothing its content draws or
     * slides in leaves it.
     */
    static StackPane frame(double prefWidth, double prefHeight, Node content) {
        StackPane frame = new StackPane(clipped(content));
        frame.getStyleClass().add("frame");
        frame.setPrefSize(prefWidth, prefHeight);
        frame.setMinHeight(prefHeight);
        frame.setMaxSize(prefWidth, prefHeight);
        return frame;
    }

    /** A frame that fills the width of a canvas card and keeps a fixed height. */
    static StackPane wideFrame(double prefHeight, Node content) {
        StackPane frame = new StackPane(clipped(content));
        frame.getStyleClass().add("frame");
        frame.setPrefHeight(prefHeight);
        frame.setMinHeight(prefHeight);
        frame.setMaxHeight(prefHeight);
        return frame;
    }

    /**
     * A borderless area of a fixed size whose content sits at the top, so content that
     * grows and shrinks (an accordion opening) does not resize the card.
     */
    static StackPane area(double prefWidth, double prefHeight, Node content) {
        StackPane area = new StackPane(content);
        area.setAlignment(Pos.TOP_CENTER);
        area.setPrefSize(prefWidth, prefHeight);
        area.setMinHeight(prefHeight);
        area.setMaxSize(prefWidth, prefHeight);
        return area;
    }

    // The content sits inside the frame's 1px border, so it is clipped to the border's
    // inner curve. A clip on the frame itself follows the outer curve instead, and the
    // content's square corners would cover the border there. The clip goes on a holder
    // of our own, not on the content: a control's skin may set the control's clip (the
    // drawer pane clips its sliding panel) and would replace a rounded one.
    private static StackPane clipped(Node content) {
        StackPane holder = new StackPane(content);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(INNER_RADIUS * 2.0);
        clip.setArcHeight(INNER_RADIUS * 2.0);
        clip.widthProperty().bind(holder.widthProperty());
        clip.heightProperty().bind(holder.heightProperty());
        holder.setClip(clip);
        return holder;
    }
}
