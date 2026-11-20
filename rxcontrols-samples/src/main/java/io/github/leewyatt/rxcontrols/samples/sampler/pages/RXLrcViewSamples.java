package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.setIcon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXLrcView page: a music player with scrolling lyrics and a seek bar, a karaoke
 * screen, lyrics anchored near the top, a time offset and the placeholder for a song
 * without lyrics. A pretend clock plays the song, since the sampler plays no audio.
 */
public final class RXLrcViewSamples {

    private static final Duration OFFSET_STEP = Duration.millis(500.0);

    private RXLrcViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @param life the lifecycle of the page
     * @return the content
     */
    public static SampleContent create(SampleLifecycle life) {
        return new SampleContent(
                "Scrolling song lyrics in LRC format that follow a playback time and highlight the current line.",
                List.of("Music players and karaoke screens that show the words as the song plays.",
                        "To show only the current line in a small space, use RXLrcLineView."),
                List.of(
                        SampleCard.wide("Music player",
                                "Press play: the lyrics follow currentTime; click a line or drag the bar to jump, scroll to browse.",
                                player(life)),
                        SampleCard.large("Karaoke",
                                "currentLineScale enlarges the line being sung; CSS colors the current line and the others.",
                                karaoke(life)),
                        SampleCard.large("Top anchored",
                                "currentLinePosition 0.2 keeps the current line near the top, so more of what comes next shows.",
                                topAnchored(life)),
                        SampleCard.medium("Time offset",
                                "A positive timeOffset shows every line earlier, a negative one later, for files that do not match the audio.",
                                offset(life)),
                        SampleCard.medium("No lyrics",
                                "Without a document the placeholder node shows instead.",
                                noLyrics())),
                MediaKit.STYLESHEET);
    }

    // ==================== Music player ====================

    private static Node player(SampleLifecycle life) {
        RXLrcView lyrics = lyricView();
        lyrics.setPrefSize(320.0, 230.0);
        HBox.setHgrow(lyrics, Priority.ALWAYS);

        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(life, false);
        lyrics.currentTimeProperty().bind(clock.timeProperty());
        lyrics.setOnLineClicked(event -> clock.seek(event.getTime()));
        clock.seek(Duration.seconds(9.0));

        RXImageView cover = new RXImageView(scenery(3));
        cover.setPrefSize(150.0, 150.0);
        cover.setImageRadius(12.0);

        RXSeekBar seek = new RXSeekBar(clock.progress());
        seek.getStyleClass().add("teal");
        clock.timeProperty().addListener((obs, old, time) -> {
            if (!seek.isSeeking()) {
                seek.setProgress(clock.progress());
            }
        });
        seek.seekingProperty().addListener((obs, was, seeking) -> {
            if (!seeking) {
                clock.seek(SONG_LENGTH.multiply(seek.getProgress()));
            }
        });
        Label elapsed = text("", "time-code");
        elapsed.textProperty().bind(Bindings.createStringBinding(
                () -> PlaybackClock.format(clock.timeProperty().get()) + " / " + PlaybackClock.format(SONG_LENGTH),
                clock.timeProperty()));

        StackPane play = playButton(clock);
        HBox transport = new HBox(10.0, play, elapsed);
        transport.setAlignment(Pos.CENTER_LEFT);

        VBox side = new VBox(8.0, cover, text(SONG_TITLE, "headline"), text(SONG_ARTIST, "muted"), transport);
        side.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(24.0, side, lyrics);
        VBox player = new VBox(14.0, top, seek);
        player.getStyleClass().add("panel");
        player.setMaxWidth(620.0);
        return player;
    }

    private static StackPane playButton(PlaybackClock clock) {
        StackPane play = iconButton("play-icon");
        play.getStyleClass().add("round-button");
        play.setOnMouseClicked(event -> clock.toggle());
        clock.playingProperty().addListener((obs, was, playing) ->
                setIcon(play, playing ? "play-icon" : "pause-icon", playing ? "pause-icon" : "play-icon"));
        return play;
    }

    // ==================== Karaoke ====================

    private static Node karaoke(SampleLifecycle life) {
        RXLrcView lyrics = lyricView();
        lyrics.getStyleClass().add("karaoke");
        lyrics.setCurrentLineScale(1.35);
        lyrics.setLineSpacing(12.0);
        lyrics.setPrefSize(300.0, 200.0);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(life, true);
        clock.seek(Duration.seconds(16.5));
        lyrics.currentTimeProperty().bind(clock.timeProperty());
        StackPane screen = new StackPane(lyrics);
        screen.getStyleClass().add("screen");
        screen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return screen;
    }

    // ==================== Top anchored ====================

    private static Node topAnchored(SampleLifecycle life) {
        RXLrcView lyrics = lyricView();
        lyrics.setCurrentLinePosition(0.2);
        lyrics.getStyleClass().add("compact");
        lyrics.setPrefSize(300.0, 200.0);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(life, true);
        clock.seek(Duration.seconds(5.0));
        lyrics.currentTimeProperty().bind(clock.timeProperty());
        StackPane sheet = new StackPane(lyrics);
        sheet.getStyleClass().add("panel");
        sheet.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return sheet;
    }

    // ==================== Time offset ====================

    private static Node offset(SampleLifecycle life) {
        RXLrcView lyrics = lyricView();
        lyrics.getStyleClass().add("compact");
        lyrics.setPrefSize(236.0, 110.0);
        PlaybackClock clock = new PlaybackClock(SONG_LENGTH).attach(life, true);
        clock.seek(Duration.seconds(26.0));
        lyrics.currentTimeProperty().bind(clock.timeProperty());

        Label value = text("", "time-code");
        value.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("Offset %+.1f s", lyrics.getTimeOffset().toSeconds()),
                lyrics.timeOffsetProperty()));
        RXButton later = new RXButton("Later");
        later.setOnAction(event -> lyrics.setTimeOffset(lyrics.getTimeOffset().subtract(OFFSET_STEP)));
        RXButton earlier = new RXButton("Earlier");
        earlier.setOnAction(event -> lyrics.setTimeOffset(lyrics.getTimeOffset().add(OFFSET_STEP)));
        HBox controls = new HBox(10.0, later, value, earlier);
        controls.setAlignment(Pos.CENTER);
        StackPane sheet = new StackPane(lyrics);
        sheet.getStyleClass().add("panel");
        sheet.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(12.0, sheet, controls);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== No lyrics ====================

    private static Node noLyrics() {
        RXLrcView lyrics = new RXLrcView();
        lyrics.setPrefSize(220.0, 130.0);
        VBox placeholder = new VBox(8.0, icon("music-icon"), text("No lyrics for this song yet", "muted"));
        placeholder.setAlignment(Pos.CENTER);
        lyrics.setPlaceholder(placeholder);
        StackPane sheet = new StackPane(lyrics);
        sheet.getStyleClass().add("panel");
        sheet.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return sheet;
    }

    private static RXLrcView lyricView() {
        RXLrcView lyrics = new RXLrcView();
        lyrics.setLyrics(LYRICS);
        return lyrics;
    }
}
