package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXLrcLineView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimCheckerboard;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimNewspaper;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatterRadial;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimWhipPan;
import io.github.leewyatt.rxcontrols.animation.page.AnimWind;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXLrcLineView}.
 *
 * <p>Exercises the document, playback time, offset, and the carousel
 * transition presets (multi-page display animations are excluded — the
 * control falls back to a direct cut for those).</p>
 */
public class RXLrcLineViewShowcase extends RXShowcaseApplication {

    private static final double TRACK_MILLIS = 32_000.0;
    private static final double TICK_MILLIS = 50.0;
    private static final DocumentPreset DEFAULT_DOCUMENT = DocumentPreset.NOCTURNE;
    private static final String DEFAULT_ANIMATION = "Fade";

    private final DoubleProperty playbackMillis = new SimpleDoubleProperty(0.0);
    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();
    private final Timeline playTimeline = createPlayTimeline();

    private RXLrcLineView lineView;
    private RXSeekBar seekBar;
    private Slider timeSlider;
    private boolean syncingSeekBar;
    private boolean syncingTimeSlider;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXLrcLineView";
    }

    @Override
    protected String subtitle() {
        return "Single current lyric line with carousel transitions";
    }

    @Override
    protected String windowTitle() {
        return "RXLrcLineView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 620.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-lrc-line-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        lineView = new RXLrcLineView();
        lineView.getStyleClass().add("showcase-lrc-line-view");
        lineView.setDocument(DEFAULT_DOCUMENT.document());
        lineView.setPrefSize(460.0, 96.0);
        lineView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        lineView.currentTimeProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(playbackMillis.get()),
                playbackMillis));

        seekBar = createSeekBar();
        Label timeLabel = createTimeLabel();
        Label currentLineLabel = createCurrentLineLabel();

        VBox preview = new VBox(14.0, lineView, seekBar, timeLabel, currentLineLabel);
        preview.getStyleClass().add("lrc-line-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Document", buildDocumentGrid()),
                section("Playback", buildPlaybackGrid()),
                section("Timing", buildTimingGrid()),
                section("Animation", buildAnimationGrid()));
    }

    // ==================== Sections ====================

    private Node buildDocumentGrid() {
        ComboBox<DocumentPreset> documentBox = new ComboBox<>();
        documentBox.getItems().setAll(DocumentPreset.values());
        documentBox.setValue(DEFAULT_DOCUMENT);
        documentBox.setMaxWidth(Double.MAX_VALUE);
        documentBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                lineView.setDocument(newValue.document());
                setPlaybackMillis(0.0);
            }
        });

        return createGrid(row("Document", documentBox));
    }

    private Node buildPlaybackGrid() {
        CheckBox playBox = new CheckBox("Auto play");
        playBox.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                playTimeline.play();
            } else {
                playTimeline.pause();
            }
        });

        timeSlider = createSlider(0.0, TRACK_MILLIS, playbackMillis.get());
        timeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncingTimeSlider) {
                setPlaybackMillis(newValue.doubleValue());
            }
        });
        playbackMillis.addListener((obs, oldValue, newValue) -> syncPlaybackControls());
        syncPlaybackControls();

        return createGrid(
                row(playBox),
                row("Current time", timeSlider, createValueLabel(timeSlider, "%.0f ms")));
    }

    private Node buildTimingGrid() {
        Slider offsetSlider = createSlider(-1200.0, 1200.0, 0.0);
        lineView.timeOffsetProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(offsetSlider.getValue()),
                offsetSlider.valueProperty()));

        return createGrid(row("Time offset", offsetSlider, createValueLabel(offsetSlider, "%.0f ms")));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox("Animate line changes");
        animatedBox.setSelected(lineView.isAnimated());
        lineView.animatedProperty().bind(animatedBox.selectedProperty());

        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(animationPresets.keySet());
        animationBox.setValue(DEFAULT_ANIMATION);
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Supplier<PageAnimation> preset = animationPresets.get(newValue);
            if (preset != null) {
                lineView.setAnimation(preset.get());
            }
        });

        Slider durationSlider = createSlider(100.0, 1500.0,
                lineView.getAnimationDuration().toMillis());
        lineView.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty()));

        return createGrid(
                row(animatedBox),
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    // ==================== Preview helpers ====================

    private Timeline createPlayTimeline() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(TICK_MILLIS), event -> {
            double next = playbackMillis.get() + TICK_MILLIS;
            setPlaybackMillis(next >= TRACK_MILLIS ? 0.0 : next);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }

    private RXSeekBar createSeekBar() {
        RXSeekBar bar = new RXSeekBar();
        bar.setPrefWidth(460.0);
        bar.setSecondaryProgress(1.0);

        playbackMillis.addListener((obs, oldValue, newValue) -> {
            if (!bar.isSeeking()) {
                syncingSeekBar = true;
                bar.setProgress(clamp0To1(newValue.doubleValue() / TRACK_MILLIS));
                syncingSeekBar = false;
            }
        });
        bar.progressProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncingSeekBar && bar.isSeeking()) {
                setPlaybackMillis(TRACK_MILLIS * clamp0To1(newValue.doubleValue()));
            }
        });
        bar.seekingProperty().addListener((obs, wasSeeking, seeking) -> {
            if (wasSeeking && !seeking) {
                setPlaybackMillis(TRACK_MILLIS * clamp0To1(bar.getProgress()));
            }
        });
        return bar;
    }

    private Label createTimeLabel() {
        Label label = new Label();
        label.getStyleClass().add("time-label");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> formatTime(Duration.millis(playbackMillis.get()))
                        + " / " + formatTime(Duration.millis(TRACK_MILLIS)),
                playbackMillis));
        return label;
    }

    private Label createCurrentLineLabel() {
        Label label = new Label();
        label.getStyleClass().add("current-line-label");
        label.textProperty().bind(Bindings.createStringBinding(() -> {
            RXLrcLine line = lineView.getCurrentLine();
            if (line == null) {
                return "No current line";
            }
            return "#" + (line.index() + 1) + "  " + line.text();
        }, lineView.currentLineProperty()));
        return label;
    }

    private void syncPlaybackControls() {
        if (seekBar != null && !seekBar.isSeeking()) {
            syncingSeekBar = true;
            seekBar.setProgress(clamp0To1(playbackMillis.get() / TRACK_MILLIS));
            syncingSeekBar = false;
        }
        if (timeSlider != null) {
            syncingTimeSlider = true;
            timeSlider.setValue(playbackMillis.get());
            syncingTimeSlider = false;
        }
    }

    private void setPlaybackMillis(double millis) {
        playbackMillis.set(clamp(millis, 0.0, TRACK_MILLIS));
    }

    private static double clamp0To1(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String formatTime(Duration duration) {
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // Multi-page display animations (e.g. AnimAround) are deliberately
    // excluded: RXLrcLineView falls back to a direct cut for those.
    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("Fade", AnimFade::new);
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Shatter", AnimShatter::new);
        presets.put("Shatter (radial)", AnimShatterRadial::new);
        presets.put("Glitch", AnimGlitch::new);
        presets.put("Blinds", AnimBlinds::new);
        presets.put("Checkerboard", AnimCheckerboard::new);
        presets.put("Whip Pan", AnimWhipPan::new);
        presets.put("Cube", AnimCube::new);
        presets.put("Flip", AnimFlip::new);
        presets.put("Gaussian Blur", AnimGaussianBlur::new);
        presets.put("Newspaper", AnimNewspaper::new);
        presets.put("Wind", AnimWind::new);
        return presets;
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ==================== Document presets ====================

    private enum DocumentPreset {

        NOCTURNE("Nocturne Drive", """
                [00:00.80]Neon wakes above the avenue
                [00:04.50]Signals fold into the rain
                [00:08.20]Every window keeps a rhythm
                [00:12.00]Every headlight draws a lane
                [00:16.00]We move softly through the static
                [00:20.40]Past the towers and the signs
                [00:24.30]When the final chorus rises
                [00:28.10]The city keeps the time
                """),

        DENSE("Dense Chorus", """
                [00:00.00]One
                [00:01.40]Two
                [00:02.80]Three
                [00:04.20]Four
                [00:05.60]Five
                [00:07.00]Six
                [00:08.40]Seven
                [00:09.80]Eight
                [00:11.20]Nine
                [00:12.60]Ten
                """),

        SPARSE("Sparse Verse", """
                [00:02.00]Hold the first line
                [00:10.00]Let the silence carry
                [00:20.00]Return with the hook
                [00:30.00]End on the downbeat
                """),

        EMPTY("Empty Document", null);

        private final String displayName;
        private final RXLrcDocument document;

        DocumentPreset(String displayName, String lyrics) {
            this.displayName = displayName;
            this.document = lyrics == null
                    ? RXLrcDocument.empty()
                    : RXLrcParser.parse(lyrics).document();
        }

        private RXLrcDocument document() {
            return document;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
