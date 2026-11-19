package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXSeekBar page: a video player with buffering and a time preview, a compact music
 * player, buffered against played progress, chapter names in the preview bubble and
 * colored bars.
 */
public final class RXSeekBarSamples {

    private static final double VIDEO_SECONDS = 754.0;
    private static final double SONG_SECONDS = 214.0;
    private static final String[] CHAPTERS = {"Intro", "Getting started", "Layouts", "Styling", "Wrap-up"};

    private RXSeekBarSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A media seek bar with played and buffered progress, a hover preview and a draggable thumb.",
                List.of("The timeline of a video, a song or a podcast, where users jump to a point and see what has loaded.",
                        "For a setting with a value and ticks use RXSlider; for progress nobody drags use a progress bar."),
                List.of(
                        SampleCard.wide("Video player",
                                "Press play: the bar follows playback, the buffer runs ahead, and dragging seeks; the hover bubble shows the time.",
                                videoPlayer()),
                        SampleCard.large("Music player",
                                "A compact player with elapsed and remaining time on both sides of the bar.",
                                musicPlayer()),
                        SampleCard.large("Buffered progress",
                                "secondaryProgress shows what has loaded; the thumb can still be dragged past it.",
                                buffering()),
                        SampleCard.large("Chapter preview",
                                "indicatorTextFactory turns the hovered position into any text, here the chapter name.",
                                chapters()),
                        SampleCard.large("Colors",
                                "-rx-primary on the bar recolors the played part and the thumb.",
                                colors())),
                SelectionIcons.STYLESHEET);
    }

    private static Node videoPlayer() {
        RXImageView frame = new RXImageView(SampleImages.scenery(2));
        frame.setPrefSize(520.0, 240.0);
        frame.setImageRadius(12.0);

        RXSeekBar seek = new RXSeekBar(0.18);
        seek.setSecondaryProgress(0.42);
        seek.setIndicatorTextFactory(p -> time(p * VIDEO_SECONDS));
        HBox.setHgrow(seek, Priority.ALWAYS);

        Label elapsed = text("", "player-time");
        elapsed.textProperty().bind(Bindings.createStringBinding(
                () -> time(seek.getProgress() * VIDEO_SECONDS) + " / " + time(VIDEO_SECONDS),
                seek.progressProperty()));
        elapsed.setMinWidth(Region.USE_PREF_SIZE);

        Region glyph = icon("play-icon");
        glyph.setMouseTransparent(true);
        StackPane play = new StackPane(glyph);
        play.getStyleClass().add("icon-button");

        Timeline playback = new Timeline(new KeyFrame(Duration.millis(250.0), event -> {
            if (!seek.isSeeking()) {
                seek.setProgress(Math.min(1.0, seek.getProgress() + 0.25 / VIDEO_SECONDS * 4.0));
            }
            seek.setSecondaryProgress(Math.min(1.0, Math.max(seek.getSecondaryProgress(),
                    seek.getProgress()) + 0.002));
        }));
        playback.setCycleCount(Animation.INDEFINITE);
        play.setOnMouseClicked(event -> {
            boolean playing = playback.getStatus() == Animation.Status.RUNNING;
            if (playing) {
                playback.pause();
            } else {
                playback.play();
            }
            glyph.getStyleClass().removeAll("play-icon", "pause-icon");
            glyph.getStyleClass().add(playing ? "play-icon" : "pause-icon");
        });
        frame.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                playback.pause();
                glyph.getStyleClass().removeAll("play-icon", "pause-icon");
                glyph.getStyleClass().add("play-icon");
            }
        });

        HBox controls = new HBox(12.0, play, seek, elapsed);
        controls.getStyleClass().add("player-controls");
        controls.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        controls.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane player = new StackPane(frame, controls);
        player.getStyleClass().add("video-player");
        player.setMaxWidth(Region.USE_PREF_SIZE);
        return player;
    }

    private static Node musicPlayer() {
        RXSeekBar seek = new RXSeekBar(0.37);
        seek.getStyleClass().add("pink");
        Label elapsed = text("", "value");
        elapsed.textProperty().bind(Bindings.createStringBinding(
                () -> time(seek.getProgress() * SONG_SECONDS), seek.progressProperty()));
        Label remaining = text("", "value");
        remaining.textProperty().bind(Bindings.createStringBinding(
                () -> "-" + time((1.0 - seek.getProgress()) * SONG_SECONDS), seek.progressProperty()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox times = new HBox(elapsed, spacer, remaining);

        Region cover = new Region();
        cover.getStyleClass().add("album-cover");
        VBox titles = new VBox(2.0, text("Paper Lanterns", "body"), text("The Quiet Hours", "hint"));
        HBox head = new HBox(12.0, cover, titles);
        head.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(18.0, transport("previous-icon"), transport("pause-icon"), transport("next-icon"));
        buttons.setAlignment(Pos.CENTER);

        VBox player = new VBox(10.0, head, new VBox(4.0, seek, times), buttons);
        player.getStyleClass().add("form-panel");
        player.setPrefWidth(300.0);
        player.setMaxWidth(Region.USE_PREF_SIZE);
        return player;
    }

    private static Node transport(String iconClass) {
        Region glyph = icon(iconClass);
        glyph.setMouseTransparent(true);
        StackPane button = new StackPane(glyph);
        button.getStyleClass().add("icon-button");
        return button;
    }

    private static Node buffering() {
        RXSeekBar loading = new RXSeekBar(0.2);
        loading.setSecondaryProgress(0.35);
        RXSeekBar ahead = new RXSeekBar(0.75);
        ahead.setSecondaryProgress(0.5);
        VBox box = new VBox(8.0,
                text("Played 20 %, loaded 35 %", "hint"), loading,
                text("Seeked to 75 %, still loading from 50 %", "hint"), ahead);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node chapters() {
        RXSeekBar seek = new RXSeekBar(0.3);
        seek.setSecondaryProgress(0.6);
        seek.setIndicatorTextFactory(p -> CHAPTERS[Math.min(CHAPTERS.length - 1, (int) (p * CHAPTERS.length))]);
        seek.getStyleClass().add("teal");
        Label current = text("", "body");
        current.textProperty().bind(Bindings.createStringBinding(
                () -> "Now: " + CHAPTERS[Math.min(CHAPTERS.length - 1, (int) (seek.getProgress() * CHAPTERS.length))],
                seek.progressProperty()));
        VBox box = new VBox(10.0, current, seek, text("Hover the bar to see each chapter", "hint"));
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node colors() {
        VBox box = new VBox(6.0,
                text("Podcast", "hint"), colored("orange", 0.25),
                text("Audiobook", "hint"), colored("sky", 0.5),
                text("Lecture", "hint"), colored("violet", 0.8));
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXSeekBar colored(String color, double progress) {
        RXSeekBar seek = new RXSeekBar(progress);
        seek.setSecondaryProgress(Math.min(1.0, progress + 0.2));
        seek.getStyleClass().add(color);
        return seek;
    }

    private static String time(double seconds) {
        long total = Math.round(seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
