package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.lrc.LrcDocument;
import io.github.leewyatt.rxcontrols.lrc.LrcLine;
import io.github.leewyatt.rxcontrols.lrc.LrcParser;
import io.github.leewyatt.rxcontrols.samples.demo.RXLrcViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXLrcView}.
 *
 * <p>Exercises the document, playback-time, offset, line-click, manual browse,
 * and all styleable lyric-view controls. For a lightweight player integration see
 * {@link RXLrcViewDemo}.</p>
 */
public class RXLrcViewShowcase extends RXShowcaseApplication {

    private static final double TRACK_MILLIS = 32_000.0;
    private static final DocumentPreset DEFAULT_DOCUMENT = DocumentPreset.NOCTURNE;

    private final DoubleProperty playbackMillis = new SimpleDoubleProperty(0.0);

    private RXLrcView lrcView;
    private RXSeekBar seekBar;
    private Slider timeSlider;
    private boolean syncingSeekBar;
    private boolean syncingTimeSlider;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXLrcView";
    }

    @Override
    protected String subtitle() {
        return "Timed LRC viewer with current-line tracking";
    }

    @Override
    protected String windowTitle() {
        return "RXLrcView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 700.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-lrc-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        lrcView = new RXLrcView();
        lrcView.getStyleClass().add("showcase-lrc-view");
        lrcView.setDocument(DEFAULT_DOCUMENT.document());
        lrcView.setPrefSize(430.0, 400.0);
        lrcView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        lrcView.currentTimeProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(playbackMillis.get()),
                playbackMillis));
        lrcView.setOnLineClicked(event -> setPlaybackTime(event.getTime()));

        seekBar = createSeekBar();
        Label timeLabel = createTimeLabel();
        Label currentLineLabel = createCurrentLineLabel();

        VBox preview = new VBox(14.0, lrcView, seekBar, timeLabel, currentLineLabel);
        preview.getStyleClass().add("lrc-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Document", buildDocumentGrid()),
                section("Playback", buildPlaybackGrid()),
                section("Timing", buildTimingGrid()),
                section("Browse", buildBrowseGrid()),
                section("Styleable", buildStyleGrid()));
    }

    // ==================== Sections ====================

    private Node buildDocumentGrid() {
        ComboBox<DocumentPreset> documentBox = new ComboBox<>();
        documentBox.getItems().setAll(DocumentPreset.values());
        documentBox.setValue(DEFAULT_DOCUMENT);
        documentBox.setMaxWidth(Double.MAX_VALUE);
        documentBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                lrcView.setDocument(newValue.document());
                setPlaybackMillis(0.0);
            }
        });

        return createGrid(row("Document", documentBox));
    }

    private Node buildPlaybackGrid() {
        timeSlider = createSlider(0.0, TRACK_MILLIS, playbackMillis.get());
        timeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncingTimeSlider) {
                setPlaybackMillis(newValue.doubleValue());
            }
        });
        playbackMillis.addListener((obs, oldValue, newValue) -> syncPlaybackControls());
        syncPlaybackControls();

        return createGrid(row("Current time", timeSlider, createValueLabel(timeSlider, "%.0f ms")));
    }

    private Node buildTimingGrid() {
        Slider offsetSlider = createSlider(-1200.0, 1200.0, 0.0);
        lrcView.timeOffsetProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(offsetSlider.getValue()),
                offsetSlider.valueProperty()));

        return createGrid(row("Time offset", offsetSlider, createValueLabel(offsetSlider, "%.0f ms")));
    }

    private Node buildBrowseGrid() {
        CheckBox manualBrowseBox = new CheckBox("Enable drag browse");
        manualBrowseBox.setSelected(lrcView.isManualBrowseEnabled());
        lrcView.manualBrowseEnabledProperty().bind(manualBrowseBox.selectedProperty());

        CheckBox wheelBrowseBox = new CheckBox("Enable wheel browse");
        wheelBrowseBox.setSelected(lrcView.isMouseWheelBrowseEnabled());
        lrcView.mouseWheelBrowseEnabledProperty().bind(wheelBrowseBox.selectedProperty());

        Slider recoverSlider = createSlider(0.0, 10.0,
                RXLrcView.DEFAULT_BROWSE_RECOVER_DELAY.toSeconds());
        lrcView.browseRecoverDelayProperty().bind(Bindings.createObjectBinding(
                () -> Duration.seconds(recoverSlider.getValue()),
                recoverSlider.valueProperty()));

        return createGrid(
                row(manualBrowseBox),
                row(wheelBrowseBox),
                row("Recover delay", recoverSlider, createValueLabel(recoverSlider, "%.1f s")));
    }

    private Node buildStyleGrid() {
        CheckBox animatedBox = new CheckBox("Animate current-line changes");
        animatedBox.setSelected(lrcView.isAnimated());
        lrcView.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider = createSlider(0.0, 1200.0,
                lrcView.getAnimationDuration().toMillis());
        lrcView.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty()));

        Slider positionSlider = createSlider(0.15, 0.85,
                lrcView.getCurrentLinePosition());
        lrcView.currentLinePositionProperty().bind(positionSlider.valueProperty());

        Slider spacingSlider = createSlider(0.0, 24.0, lrcView.getLineSpacing());
        lrcView.lineSpacingProperty().bind(spacingSlider.valueProperty());

        Slider scaleSlider = createSlider(1.0, 1.8, lrcView.getCurrentLineScale());
        lrcView.currentLineScaleProperty().bind(scaleSlider.valueProperty());

        return createGrid(
                row(animatedBox),
                row("Animation", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Line position", positionSlider, createValueLabel(positionSlider, "%.2f")),
                row("Line spacing", spacingSlider, createValueLabel(spacingSlider, "%.0f px")),
                row("Line scale", scaleSlider, createValueLabel(scaleSlider, "%.2f")));
    }

    // ==================== Preview helpers ====================

    private RXSeekBar createSeekBar() {
        RXSeekBar bar = new RXSeekBar();
        bar.setPrefWidth(430.0);
        bar.setSecondaryProgress(1.0);

        playbackMillis.addListener((obs, oldValue, newValue) -> {
            if (!bar.isSeeking()) {
                syncingSeekBar = true;
                bar.setProgress(RXMath.clampFinite(newValue.doubleValue() / TRACK_MILLIS,
                        0.0, 1.0, 0.0));
                syncingSeekBar = false;
            }
        });
        bar.progressProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncingSeekBar && bar.isSeeking()) {
                setPlaybackMillis(TRACK_MILLIS * RXMath.clampFinite(newValue.doubleValue(),
                        0.0, 1.0, 0.0));
            }
        });
        bar.seekingProperty().addListener((obs, wasSeeking, seeking) -> {
            if (wasSeeking && !seeking) {
                setPlaybackMillis(TRACK_MILLIS * RXMath.clampFinite(bar.getProgress(),
                        0.0, 1.0, 0.0));
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
            LrcLine line = lrcView.getCurrentLine();
            if (line == null) {
                return "No current line";
            }
            return "#" + (line.index() + 1) + "  " + line.text();
        }, lrcView.currentLineProperty()));
        return label;
    }

    private void syncPlaybackControls() {
        if (seekBar != null && !seekBar.isSeeking()) {
            syncingSeekBar = true;
            seekBar.setProgress(RXMath.clampFinite(playbackMillis.get() / TRACK_MILLIS,
                    0.0, 1.0, 0.0));
            syncingSeekBar = false;
        }
        if (timeSlider != null) {
            syncingTimeSlider = true;
            timeSlider.setValue(playbackMillis.get());
            syncingTimeSlider = false;
        }
    }

    private void setPlaybackTime(Duration time) {
        if (time == null || time.isUnknown() || time.isIndefinite()
                || !Double.isFinite(time.toMillis())) {
            return;
        }
        setPlaybackMillis(time.toMillis());
    }

    private void setPlaybackMillis(double millis) {
        playbackMillis.set(RXMath.clampFinite(millis, 0.0, TRACK_MILLIS, 0.0));
    }

    private static String formatTime(Duration duration) {
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
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
        private final LrcDocument document;

        DocumentPreset(String displayName, String lyrics) {
            this.displayName = displayName;
            this.document = lyrics == null
                    ? LrcDocument.empty()
                    : LrcParser.parse(lyrics).document();
        }

        private LrcDocument document() {
            return document;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
