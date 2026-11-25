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
import java.util.function.DoubleBinaryOperator;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.setIcon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.image;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXAudioSpectrum page: a music player whose spectrum settles when paused, an aurora,
 * a radial ring around a cover, a flower, a double flower, mirrored bars, a line, a
 * centered layout with glow and a corona. The aurora, the flowers and the corona are drawn
 * with made-up waves; every spectrum is fed made-up magnitudes, since the sampler plays
 * no audio.
 */
public final class RXAudioSpectrumSamples {

    private static final int RAW_BANDS = 128;
    private static final double MIN_DECIBELS = -60.0;
    private static final Duration FRAME = Duration.millis(40.0);
    private static final double BEATS_PER_SECOND = 2.0;
    private static final Duration WAVE_FRAME = Duration.millis(100.0);
    private static final double FLOWER_SIZE = 200.0;

    private RXAudioSpectrumSamples() {
    }

    /**
     * Builds the page content.
     *
     * @param life the lifecycle of the page
     * @return the content
     */
    public static SampleContent create(SampleLifecycle life) {
        return new SampleContent(
                "An audio visualizer that draws the magnitude frames you feed it as bars, a line or a ring.",
                List.of("Music and podcast players, voice recorders and any screen that shows sound as it plays.",
                        "For a level that only shows progress, use a progress bar or RXSeekBar."),
                List.of(
                        SampleCard.wide("Music player",
                                "The default VisBars with peak caps; pause and the bars settle to silence on their own.",
                                player(life)),
                        SampleCard.medium("Aurora",
                                "Three VisLine spectrums stacked, each with its own color, wave and glow.",
                                screen(aurora(life))),
                        SampleCard.medium("Radial ring",
                                "VisRadial grows bars outwards around a hole, here filled with the album cover.",
                                screen(radial(life))),
                        SampleCard.medium("Flower",
                                "VisRadial with a small hole; a wave travelling across the bands turns the petals.",
                                screen(flower(life))),
                        SampleCard.medium("Double flower",
                                "Two VisRadial spectrums, one inside the hole of the other, turning opposite ways.",
                                screen(doubleFlower(life))),
                        SampleCard.medium("Mirrored bars",
                                "VisBarsMirror reflects the bars below the middle line; -rx-bar-fill sets the gradient.",
                                screen(mirrored(life))),
                        SampleCard.medium("Line",
                                "VisLine draws one smooth curve through the band levels.",
                                screen(line(life))),
                        SampleCard.medium("Centered with glow",
                                "BandLayout.CENTER_FOLD puts the bass in the middle; glow adds a soft light.",
                                screen(centered(life))),
                        SampleCard.medium("Corona",
                                "VisRadial with CENTER_FOLD and glow: both halves of the ring move alike.",
                                screen(corona(life)))),
                MediaKit.STYLESHEET);
    }

    // ==================== Music player ====================

    private static Node player(SampleLifecycle life) {
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
        life.playWhileShown(feed.timeline);
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

    private static Node mirrored(SampleLifecycle life) {
        RXAudioSpectrum spectrum = spectrum(48, 11L, life);
        spectrum.setVisualization(new VisBarsMirror());
        spectrum.getStyleClass().add("sunset");
        spectrum.setShowPeaks(false);
        return spectrum;
    }

    private static Node line(SampleLifecycle life) {
        RXAudioSpectrum spectrum = spectrum(64, 23L, life);
        spectrum.setVisualization(new VisLine(2.5));
        spectrum.getStyleClass().add("aqua");
        return spectrum;
    }

    private static Node radial(SampleLifecycle life) {
        RXAudioSpectrum spectrum = spectrum(72, 31L, life);
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

    private static Node centered(SampleLifecycle life) {
        RXAudioSpectrum spectrum = spectrum(40, 47L, life);
        spectrum.setBandLayout(BandLayout.CENTER_FOLD);
        spectrum.setGlow(true);
        spectrum.setBarGapRatio(0.35);
        spectrum.getStyleClass().add("lime");
        return spectrum;
    }

    private static RXAudioSpectrum spectrum(int bands, long seed, SampleLifecycle life) {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.setBandCount(bands);
        spectrum.setPrefSize(240.0, 130.0);
        Feed feed = new Feed(spectrum, seed);
        life.playWhileShown(feed.timeline);
        return spectrum;
    }

    private static Node screen(Node content) {
        StackPane screen = new StackPane(content);
        screen.getStyleClass().add("screen");
        screen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return screen;
    }

    // ==================== Wave shapes ====================

    private static Node flower(SampleLifecycle life) {
        return petals(FLOWER_SIZE, 0.15, 1.0, life);
    }

    private static Node doubleFlower(SampleLifecycle life) {
        RXAudioSpectrum outer = petals(FLOWER_SIZE, 0.5, 1.0, life);
        outer.getStyleClass().add("orchid");
        // The inner flower is as large as the hole of the outer one.
        RXAudioSpectrum inner = petals(FLOWER_SIZE * 0.5, 0.2, -1.0, life);
        inner.setBandCount(64);
        inner.getStyleClass().add("ice");
        StackPane stack = new StackPane(outer, inner);
        stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return stack;
    }

    // A sine wave moving along the bands, louder in the bass, breathing slowly.
    private static RXAudioSpectrum petals(double size, double hole, double direction, SampleLifecycle life) {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        VisRadial radial = new VisRadial();
        radial.setInnerRadiusRatio(hole);
        spectrum.setVisualization(radial);
        spectrum.setPrefSize(size, size);
        spectrum.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wave(spectrum, life, (time, position) -> {
            double wave = Math.max(0.0, Math.sin(direction * time * 4.0 + position * RAW_BANDS * 0.25));
            double tilt = 1.0 - 0.6 * position;
            double pulse = 0.6 + 0.4 * Math.sin(time * 2.0 * Math.PI * 0.4);
            return wave * tilt * pulse;
        });
        return spectrum;
    }

    private static Node corona(SampleLifecycle life) {
        RXAudioSpectrum spectrum = spectrum(96, 59L, life);
        VisRadial ring = new VisRadial();
        ring.setInnerRadiusRatio(0.3);
        // The fold puts the bass half a turn after the start, so start at the bottom.
        ring.setStartAngle(90.0);
        spectrum.setVisualization(ring);
        spectrum.setBandLayout(BandLayout.CENTER_FOLD);
        spectrum.setGlow(true);
        spectrum.setShowPeaks(false);
        spectrum.getStyleClass().add("sun");
        spectrum.setPrefSize(FLOWER_SIZE, FLOWER_SIZE);
        return spectrum;
    }

    private static Node aurora(SampleLifecycle life) {
        StackPane stack = new StackPane(
                ribbon("lime", 0.0, 0.9, life), ribbon("aqua", 2.1, 1.3, life), ribbon("violet", 4.2, 0.6, life));
        stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return stack;
    }

    // Two slow sines of different lengths, so the ribbon never repeats itself exactly.
    private static RXAudioSpectrum ribbon(String color, double phase, double speed, SampleLifecycle life) {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.setBandCount(48);
        spectrum.setVisualization(new VisLine(3.0));
        spectrum.setGlow(true);
        spectrum.getStyleClass().add(color);
        spectrum.setPrefSize(240.0, 130.0);
        wave(spectrum, life, (time, position) -> 0.5
                + 0.28 * Math.sin(position * 7.0 + time * speed + phase)
                + 0.14 * Math.sin(position * 17.0 - time * speed * 1.7 + phase));
        return spectrum;
    }

    /** Feeds a spectrum with levels from 0 to 1 computed from the time and the band position. */
    private static void wave(RXAudioSpectrum spectrum, SampleLifecycle life, DoubleBinaryOperator level) {
        float[] frame = new float[RAW_BANDS];
        double[] time = {0.0};
        Runnable feed = () -> {
            time[0] += WAVE_FRAME.toSeconds();
            for (int i = 0; i < RAW_BANDS; i++) {
                double value = level.applyAsDouble(time[0], i / (double) RAW_BANDS);
                frame[i] = (float) (MIN_DECIBELS * (1.0 - Math.max(0.0, Math.min(1.0, value))));
            }
            spectrum.updateSpectrum(frame);
        };
        feed.run();
        Timeline timeline = new Timeline(new KeyFrame(WAVE_FRAME, event -> feed.run()));
        timeline.setCycleCount(Animation.INDEFINITE);
        life.playWhileShown(timeline);
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
