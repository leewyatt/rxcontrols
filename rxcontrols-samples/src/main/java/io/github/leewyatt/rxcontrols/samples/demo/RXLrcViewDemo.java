package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLrcViewShowcase;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Lightweight player-style demo for {@link RXLrcView}.
 *
 * <p>The demo binds a simulated playback clock to the lyric view and lets
 * clicked lyric lines seek the clock. For the full property explorer see
 * {@link RXLrcViewShowcase}.</p>
 */
public class RXLrcViewDemo extends Application {

    private static final Duration TRACK_DURATION = Duration.seconds(32.0);
    private static final Duration TICK_DURATION = Duration.millis(100.0);
    private static final String LYRICS = """
            [00:00.80]Neon wakes above the avenue
            [00:04.50]Signals fold into the rain
            [00:08.20]Every window keeps a rhythm
            [00:12.00]Every headlight draws a lane
            [00:16.00]We move softly through the static
            [00:20.40]Past the towers and the signs
            [00:24.30]When the final chorus rises
            [00:28.10]The city keeps the time
            """;

    private final DoubleProperty currentMillis = new SimpleDoubleProperty(0.0);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private boolean syncingSeekBar;

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXLrcView lrcView = createLrcView();
        RXSeekBar seekBar = createSeekBar();
        Timeline clock = createClock();

        Button playPauseButton = createPlayPauseButton();
        Button restartButton = new Button("Restart");
        restartButton.setOnAction(event -> setPlaybackMillis(0.0));

        Label titleLabel = new Label("Nocturne Drive");
        titleLabel.getStyleClass().add("track-title");
        Label timeLabel = createTimeLabel();

        HBox controls = new HBox(8.0, playPauseButton, restartButton, timeLabel);
        controls.getStyleClass().add("transport-row");
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox player = new VBox(14.0, titleLabel, lrcView, seekBar, controls);
        player.getStyleClass().add("player");
        player.setMaxWidth(Region.USE_PREF_SIZE);

        VBox root = new VBox(player);
        root.getStyleClass().add("lrc-demo");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(28.0));

        Scene scene = new Scene(root, 540.0, 560.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-lrc-view-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXLrcView Demo");
        primaryStage.setOnHidden(event -> clock.stop());
        primaryStage.show();
    }

    private RXLrcView createLrcView() {
        RXLrcView lrcView = new RXLrcView();
        lrcView.setLyrics(LYRICS);
        lrcView.setPrefSize(430.0, 340.0);
        lrcView.currentTimeProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(currentMillis.get()),
                currentMillis));
        lrcView.setOnLineClicked(event -> setPlaybackMillis(event.getTime().toMillis()));
        return lrcView;
    }

    private RXSeekBar createSeekBar() {
        RXSeekBar seekBar = new RXSeekBar();
        seekBar.setPrefWidth(430.0);
        seekBar.setSecondaryProgress(1.0);

        currentMillis.addListener((obs, oldValue, newValue) -> {
            if (!seekBar.isSeeking()) {
                syncingSeekBar = true;
                seekBar.setProgress(ratio(newValue.doubleValue()));
                syncingSeekBar = false;
            }
        });
        seekBar.progressProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncingSeekBar && seekBar.isSeeking()) {
                setPlaybackMillis(TRACK_DURATION.toMillis() * clamp0To1(newValue.doubleValue()));
            }
        });
        seekBar.seekingProperty().addListener((obs, wasSeeking, seeking) -> {
            if (wasSeeking && !seeking) {
                setPlaybackMillis(TRACK_DURATION.toMillis() * clamp0To1(seekBar.getProgress()));
            }
        });
        return seekBar;
    }

    private Timeline createClock() {
        Timeline clock = new Timeline(new KeyFrame(TICK_DURATION, event -> {
            double next = currentMillis.get() + TICK_DURATION.toMillis();
            if (next >= TRACK_DURATION.toMillis()) {
                setPlaybackMillis(TRACK_DURATION.toMillis());
                playing.set(false);
            } else {
                setPlaybackMillis(next);
            }
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        playing.addListener((obs, wasPlaying, isPlaying) -> {
            if (isPlaying) {
                if (currentMillis.get() >= TRACK_DURATION.toMillis()) {
                    setPlaybackMillis(0.0);
                }
                clock.play();
            } else {
                clock.pause();
            }
        });
        return clock;
    }

    private Button createPlayPauseButton() {
        Button button = new Button();
        button.textProperty().bind(Bindings.when(playing).then("Pause").otherwise("Play"));
        button.setOnAction(event -> playing.set(!playing.get()));
        return button;
    }

    private Label createTimeLabel() {
        Label label = new Label();
        label.getStyleClass().add("time-label");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> formatTime(Duration.millis(currentMillis.get()))
                        + " / " + formatTime(TRACK_DURATION),
                currentMillis));
        return label;
    }

    private void setPlaybackMillis(double millis) {
        currentMillis.set(clamp(millis, 0.0, TRACK_DURATION.toMillis()));
    }

    private double ratio(double millis) {
        return clamp0To1(millis / TRACK_DURATION.toMillis());
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

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
