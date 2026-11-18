package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.List;

/**
 * Icons, text helpers, buttons, colors and the stylesheet shared by the motion and
 * effects pages. Each icon is a shape-backed region whose shape comes from its style
 * class in the stylesheet.
 */
final class MotionKit {

    static final String STYLESHEET =
            MotionKit.class.getResource("rx-motion-samples.css").toExternalForm();

    static final Color ROSE = Color.web("#f43f5e");
    static final Color ORANGE = Color.web("#f97316");
    static final Color AMBER = Color.web("#f59e0b");
    static final Color LIME = Color.web("#65a30d");
    static final Color TEAL = Color.web("#14b8a6");
    static final Color SKY = Color.web("#0ea5e9");
    static final Color VIOLET = Color.web("#8b5cf6");
    static final Color PINK = Color.web("#ec4899");

    /** Bright party colors for confetti and fireworks. */
    static final List<Paint> PARTY = List.of(ROSE, AMBER, LIME, SKY, VIOLET, PINK);

    private MotionKit() {
    }

    static Region icon(String styleClass, String... more) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        icon.getStyleClass().addAll(more);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    /** A clickable icon: a transparent wrapper takes the clicks, the icon inside ignores them. */
    static StackPane iconButton(String styleClass, String... more) {
        Region icon = icon(styleClass, more);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("icon-button");
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return button;
    }

    /** Swaps the icon inside a button made by {@link #iconButton(String, String...)}. */
    static void setIcon(StackPane button, String from, String to) {
        Node icon = button.getChildren().get(0);
        icon.getStyleClass().remove(from);
        if (!icon.getStyleClass().contains(to)) {
            icon.getStyleClass().add(to);
        }
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
        RXButton button = new RXButton(value);
        button.getStyleClass().add("plain");
        return button;
    }

    static Label chip(String value, String color) {
        Label chip = text(value, "chip", color);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** A round badge with an icon inside, in one of the palette colors. */
    static StackPane iconBadge(String iconClass, String color, String... more) {
        StackPane badge = new StackPane(icon(iconClass, "on-color"));
        badge.getStyleClass().addAll("icon-badge", color);
        badge.getStyleClass().addAll(more);
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return badge;
    }

    /** Loads a classpath image in the background. */
    static Image image(String path) {
        return new Image(MotionKit.class.getResource(path).toExternalForm(), true);
    }

    static <T extends Region> T fixedWidth(T region, double width) {
        region.setPrefWidth(width);
        region.setMaxWidth(Region.USE_PREF_SIZE);
        return region;
    }
}
