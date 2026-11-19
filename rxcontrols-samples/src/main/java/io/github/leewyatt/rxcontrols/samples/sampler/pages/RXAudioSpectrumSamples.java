package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.spectrum.BandLayout;
import io.github.leewyatt.rxcontrols.spectrum.VisBarsMirror;
import io.github.leewyatt.rxcontrols.spectrum.VisLine;
import io.github.leewyatt.rxcontrols.spectrum.VisRadial;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.playWhileShowing;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.setIcon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.image;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXAudioSpectrum page: a music player whose spectrum settles when paused, mirrored
 * bars, a line, a radial ring around a cover and a centered layout with glow. Every
 * spectrum is fed made-up magnitudes, since the sampler plays no audio.
 */
public final class RXAudioSpectrumSamples {

    private static final int RAW_BANDS = 128;
    private static final double MIN_DECIBELS = -60.0;
    private static final Duration FRAME = Duration.millis(40.0);
    private static final double BEATS_PER_SECOND = 2.0;

    private RXAudioSpectrumSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "An audio visualizer that draws the magnitude frames you feed it as bars, a line or a ring.",
                List.of("Music and podcast players, voice recorders and any screen that shows sound as it plays.",
                        "For a level that only shows progress, use a progress bar or RXSeekBar."),
                List.of(
                        SampleCard.wide("Music player",
                                "The default VisBars with peak caps; pause and the bars settle to silence on their own.",
                                player()),
                        SampleCard.medium("Mirrored bars",
                                "VisBarsMirror reflects the bars below the middle line; -rx-bar-fill sets the gradient.",
                                screen(mirrored())),
                        SampleCard.medium("Line",
                                "VisLine draws one smooth curve through the band levels.",
                                screen(line())),
                        SampleCard.medium("Radial ring",
                                "VisRadial grows bars outwards around a hole, here filled with the album cover.",
                                screen(radial())),
                        SampleCard.medium("Centered with glow",
                                "BandLayout.CENTER_FOLD puts the bass in the middle; glow adds a soft light.",
                                screen(centered()))),
                MediaKit.STYLESHEET);
    }

    // ==================== Music player ====================

    private static Node player() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.setBandCount(64);
        spectrum.setPrefHeight(150.0);
        spectrum.setMinHeight(150.0);
        HBox.setHgrow(spectrum, Priority.ALWAYS);

        RXAvatar cover = new RXAvatar(scenery(3));
        cover.setShapeType(RXAvatar.ShapeType.SQUARE);
        cover.setPrefSize(64.0, 64.0);
        VBox titles = new VBox(2.0, text("Harbor Lights", "screen-title"), text("The Quiet Hours", "screen-text"));
        StackPane play = iconButton("pause-icon", "screen-icon");
        play.getStyleClass().add("screen-button");
        HBox head = new HBox(14.0, cover, titles, spacer(), play);
        head.setAlignment(Pos.CENTER_LEFT);

        Feed feed = new Feed(spectrum, 7L);
        playWhileShowing(spectrum, feed.timeline);
        boolean[] playing = {true};
        play.setOnMouseClicked(event -> {
            playing[0] = !playing[0];
            feed.silent = !playing[0];
            setIcon(play, playing[0] ? "play-icon" : "pause-icon", playing[0] ? "pause-icon" : "play-icon");
        });

        VBox box = new VBox(16.0, head, spectrum);
        box.getStyleClass().add("screen");
        box.setPrefWidth(600.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Variants ====================

    private static Node mirrored() {
        RXAudioSpectrum spectrum = spectrum(48, 11L);
        spectrum.setVisualization(new VisBarsMirror());
        spectrum.getStyleClass().add("sunset");
        spectrum.setShowPeaks(false);
        return spectrum;
    }

    private static Node line() {
        RXAudioSpectrum spectrum = spectrum(64, 23L);
        spectrum.setVisualization(new VisLine(2.5));
        spectrum.getStyleClass().add("aqua");
        return spectrum;
    }

    private static Node radial() {
        RXAudioSpectrum spectrum = spectrum(72, 31L);
        VisRadial ring = new VisRadial();
        ring.setInnerRadiusRatio(0.5);
        spectrum.setVisualization(ring);
        spectrum.getStyleClass().add("violet");
        spectrum.setPrefSize(170.0, 170.0);
        RXAvatar cover = new RXAvatar(image(MediaKit.SHIBA));
        cover.setPrefSize(64.0, 64.0);
        StackPane stack = new StackPane(spectrum, cover);
        stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return stack;
    }

    private static Node centered() {
        RXAudioSpectrum spectrum = spectrum(40, 47L);
        spectrum.setBandLayout(BandLayout.CENTER_FOLD);
        spectrum.setGlow(true);
        spectrum.setBarGapRatio(0.35);
        spectrum.getStyleClass().add("lime");
        return spectrum;
    }

    private static RXAudioSpectrum spectrum(int bands, long seed) {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.setBandCount(bands);
        spectrum.setPrefSize(240.0, 130.0);
        Feed feed = new Feed(spectrum, seed);
        playWhileShowing(spectrum, feed.timeline);
        return spectrum;
    }

    private static Node screen(Node content) {
        StackPane screen = new StackPane(content);
        screen.getStyleClass().add("screen");
        screen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return screen;
    }

    // ==================== Made-up music ====================

    /**
     * Feeds one spectrum with frames that look like music: a kick on every beat in the
     * low bands, a few drifting melody peaks and some noise; silent sends quiet frames.
     */
    private static final class Feed {

        private final RXAudioSpectrum spectrum;
        private final Random random;
        private final float[] frame = new float[RAW_BANDS];
        private final Timeline timeline;
        private double time;
        private boolean silent;

        Feed(RXAudioSpectrum spectrum, long seed) {
            this.spectrum = spectrum;
            this.random = new Random(seed);
            this.time = seed;
            timeline = new Timeline(new KeyFrame(FRAME, event -> next()));
            timeline.setCycleCount(Animation.INDEFINITE);
        }

        private void next() {
            time += FRAME.toSeconds();
            double beatPhase = (time * BEATS_PER_SECOND) % 1.0;
            double kick = Math.exp(-beatPhase * 7.0);
            double energy = 0.75 + 0.25 * Math.sin(time * 0.7);
            for (int i = 0; i < RAW_BANDS; i++) {
                double level = silent ? 0.0 : level(i / (double) RAW_BANDS, kick) * energy;
                frame[i] = (float) (MIN_DECIBELS * (1.0 - Math.min(1.0, level)));
            }
            spectrum.updateSpectrum(frame);
        }

        private double level(double position, double kick) {
            double body = 0.15 + Math.pow(1.0 - position, 1.2) * 0.45;
            double bass = position < 0.12 ? kick * 0.4 : 0.0;
            double hats = position > 0.6 ? Math.exp(-((time * BEATS_PER_SECOND * 2.0) % 1.0) * 5.0) * 0.25 : 0.0;
            double melody = 0.0;
            for (int voice = 0; voice < 4; voice++) {
                double center = 0.15 + 0.2 * voice + 0.06 * Math.sin(time * (0.9 + voice * 0.4) + voice);
                double distance = (position - center) / 0.05;
                melody += 0.45 * Math.exp(-distance * distance) * (0.55 + 0.45 * Math.sin(time * 3.0 + voice * 2.0));
            }
            double noise = 0.7 + 0.3 * random.nextDouble();
            return (body + bass + hats + melody) * noise;
        }
    }
}
