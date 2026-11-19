package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import javafx.animation.Animation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Icons, text helpers, animation lifecycle and the stylesheet shared by
 * the media and display pages. Each icon is a shape-backed region whose shape comes from
 * its style class in the stylesheet.
 */
final class MediaKit {

    static final String STYLESHEET =
            MediaKit.class.getResource("rx-media-samples.css").toExternalForm();

    /** A portrait of a person in a pink kimono under cherry blossoms. */
    static final String PORTRAIT = "/image/avatar.png";

    /** A cartoon tiger holding a heart. */
    static final String TIGER = "/image/tx.jpeg";

    /** The song the lyric pages pretend to play. */
    static final String SONG_TITLE = "Paper Boats";

    /** The artist of {@link #SONG_TITLE}. */
    static final String SONG_ARTIST = "The Quiet Hours";

    /** How long {@link #SONG_TITLE} plays before it starts over. */
    static final Duration SONG_LENGTH = Duration.seconds(52.0);

    /** Original timed lyrics for {@link #SONG_TITLE}, in LRC format. */
    static final String LYRICS = """
            [ti:Paper Boats]
            [ar:The Quiet Hours]
            [00:01.00]Morning folds the city into blue
            [00:04.80]Steam is writing letters on the glass
            [00:08.60]Every street is learning something new
            [00:12.40]Every hour is a door we pass
            [00:16.20]Leave the window open for the light
            [00:20.00]Paper boats are sailing down the rain
            [00:23.80]Hold the little moments, hold them tight
            [00:27.60]They will never drift this way again
            [00:31.40]Count the steps between the stops
            [00:35.20]Hum the tune the street musician played
            [00:39.00]Every song that softly drops
            [00:42.80]Leaves a light behind where it once stayed
            [00:46.60]Paper boats, paper boats
            """;

    private MediaKit() {
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

    static <T extends Region> T fixedWidth(T region, double width) {
        region.setPrefWidth(width);
        region.setMaxWidth(Region.USE_PREF_SIZE);
        return region;
    }

    /** Clips a region to its own rounded bounds, so pictures inside follow its corners. */
    static <T extends Region> T roundClip(T region, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2.0);
        clip.setArcHeight(radius * 2.0);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
        return region;
    }

    /**
     * Plays an endless animation while the anchor is in a scene and pauses it when the
     * anchor leaves, so a page that is not shown costs nothing.
     */
    static void playWhileShowing(Node anchor, Animation animation) {
        anchor.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                animation.pause();
            } else {
                animation.play();
            }
        });
    }
}
