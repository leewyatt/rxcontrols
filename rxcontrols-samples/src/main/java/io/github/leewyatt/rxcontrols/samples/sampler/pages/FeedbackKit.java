package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * Icons, text helpers, buttons and the stylesheet shared by the feedback and status
 * pages. Each icon is a shape-backed region whose shape comes from its style class in
 * the stylesheet.
 */
final class FeedbackKit {

    static final String STYLESHEET =
            FeedbackKit.class.getResource("rx-feedback-samples.css").toExternalForm();

    private FeedbackKit() {
    }

    static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        RXSizing.lockToPrefSize(icon);
        return icon;
    }

    static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    static RXButton solid(String value, String... styleClasses) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("solid");
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    /** A secondary button: an outline the same size and shape as a solid one beside it. */
    static RXButton plain(String value) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("outline");
        return button;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * A rounded "screen" that overlays such as dialogs, snackbars and backdrops open
     * in, clipped so that nothing they draw leaves it.
     */
    static StackPane screen(double prefWidth, double prefHeight, Node... children) {
        StackPane screen = new StackPane(children);
        screen.getStyleClass().add("screen");
        screen.setPrefSize(prefWidth, prefHeight);
        screen.setMinHeight(prefHeight);
        clip(screen, 10.0);
        // The border is drawn over the content rather than under it, so a panel, sheet or
        // dialog that reaches the edge is rounded by the clip and keeps the border; the
        // view order holds it on top of anything added to the screen later.
        Region outline = new Region();
        outline.getStyleClass().add("screen-outline");
        outline.setMouseTransparent(true);
        outline.setViewOrder(-1.0);
        screen.getChildren().add(outline);
        return screen;
    }

    static void clip(Region region, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2.0);
        clip.setArcHeight(radius * 2.0);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }
}
