package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXLrcLineView;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.lrc.LrcLine;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.LYRICS;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.SONG_ARTIST;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.SONG_LENGTH;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.SONG_TITLE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.setIcon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXLrcLineView page: a now-playing bar with one line of lyrics, the same lyrics
 * with four transitions, a desktop lyric and the placeholder for an instrumental. A
 * pretend clock plays the song, since the sampler plays no audio.
 */
public final class RXLrcLineViewSamples {

    private RXLrcLineViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "One line of song lyrics, the one being sung, that changes with an animated transition.",
                List.of("Now-playing bars, mini players and lock screens with room for a single line.",
                        "To show the whole song scrolling, use RXLrcView."),
                List.of(
                        SampleCard.wide("Now playing",
                                "Press play: the current line slides up in a mini player bar; the skip buttons jump a line back or ahead.",
                                nowPlaying()),
                        SampleCard.wide("Transitions",
                                "Four views on one clock, each with a different animation; AnimFade is the default.",
                                transitions()),
                        SampleCard.large("Desktop lyric",
                                "Large colored text over the desktop; the line views style their text through .label.",
                                desktop()),
                        SampleCard.large("Instrumental",
                                "With no document the placeholder node shows instead.",
                                instrumental())),
                MediaKit.STYLESHEET);
    }

    // ==================== Now playing ====================

    private static Node nowPlaying() {
        RXLrcLineView line = lineView(new AnimSlide(Orientation.VERTICAL));
        line.setPrefHeight(28.0);
        line.setMaxWidth(Double.MAX_VALUE);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(line, false);
        clock.seek(Duration.seconds(13.0));
        line.currentTimeProperty().bind(clock.timeProperty());

        RXAvatar cover = new RXAvatar(scenery(2));
        cover.setShapeType(RXAvatar.ShapeType.SQUARE);
        cover.setArcWidth(8.0);
        cover.setArcHeight(8.0);
        cover.setPrefSize(48.0, 48.0);

        VBox middle = new VBox(2.0, text(SONG_TITLE + "  ·  " + SONG_ARTIST, "muted"), line);
        HBox.setHgrow(middle, Priority.ALWAYS);

        StackPane play = iconButton("play-icon");
        play.getStyleClass().add("round-button");
        play.setOnMouseClicked(event -> clock.toggle());
        clock.playingProperty().addListener((obs, was, playing) ->
                setIcon(play, playing ? "play-icon" : "pause-icon", playing ? "pause-icon" : "play-icon"));

        StackPane previous = iconButton("previous-icon");
        previous.setOnMouseClicked(event -> jump(line, clock, -1));
        StackPane next = iconButton("next-icon");
        next.setOnMouseClicked(event -> jump(line, clock, 1));

        HBox bar = new HBox(14.0, cover, middle, previous, play, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("panel", "now-playing");
        bar.setPrefWidth(560.0);
        bar.setMaxWidth(Region.USE_PREF_SIZE);
        return bar;
    }

    private static void jump(RXLrcLineView line, PlaybackClock clock, int step) {
        List<LrcLine> lines = line.getDocument().lines();
        int index = Math.max(0, Math.min(lines.size() - 1, line.getCurrentLineIndex() + step));
        clock.seek(lines.get(index).time());
    }

    // ==================== Transitions ====================

    private static Node transitions() {
        GridPane grid = new GridPane();
        grid.setHgap(18.0);
        grid.setVgap(12.0);
        grid.setAlignment(Pos.CENTER);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH);
        clock.seek(Duration.seconds(3.0));
        addRow(grid, 0, "AnimFade", new AnimFade(), clock);
        addRow(grid, 1, "AnimSlide vertical", new AnimSlide(Orientation.VERTICAL), clock);
        addRow(grid, 2, "AnimZoom", new AnimZoom(), clock);
        addRow(grid, 3, "AnimFlip vertical", new AnimFlip(Orientation.VERTICAL), clock);
        clock.attach(grid, true);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String name, PageAnimation animation, PlaybackClock clock) {
        RXLrcLineView line = lineView(animation);
        line.setPrefSize(340.0, 30.0);
        line.getStyleClass().add("boxed");
        line.currentTimeProperty().bind(clock.timeProperty());
        grid.add(text(name, "code"), 0, row);
        grid.add(line, 1, row);
    }

    // ==================== Desktop lyric ====================

    private static Node desktop() {
        RXLrcLineView line = lineView(new AnimZoom());
        line.getStyleClass().add("desktop");
        line.setPrefSize(400.0, 40.0);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(line, true);
        clock.seek(Duration.seconds(19.0));
        line.currentTimeProperty().bind(clock.timeProperty());
        StackPane screen = new StackPane(line);
        screen.getStyleClass().addAll("screen", "wallpaper");
        screen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return screen;
    }

    // ==================== Instrumental ====================

    private static Node instrumental() {
        RXLrcLineView line = new RXLrcLineView();
        line.setPrefSize(240.0, 30.0);
        HBox placeholder = new HBox(8.0, icon("music-icon"), text("Instrumental, enjoy the music", "muted"));
        placeholder.setAlignment(Pos.CENTER);
        line.setPlaceholder(placeholder);
        StackPane box = new StackPane(line);
        box.getStyleClass().add("panel");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return box;
    }

    private static RXLrcLineView lineView(PageAnimation animation) {
        RXLrcLineView line = new RXLrcLineView();
        line.setLyrics(LYRICS);
        line.setAnimation(animation);
        return line;
    }
}
