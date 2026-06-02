package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.Locale;

/**
 * Media-player demo for {@link RXSeekBar}.
 *
 * <p>The seek bar API remains normalized. This sample performs the
 * {@link Duration} to ratio conversion at the application boundary.</p>
 */
public class RXSeekBarMediaDemo extends Application {

    private static final boolean MAC_OS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private final FileChooser fileChooser = new FileChooser();
    private final RXSeekBar seekBar = new RXSeekBar();
    private final Label fileLabel = new Label("No media loaded");
    private final Button playPauseButton = new Button("Play");
    private MediaPlayer player;

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.m4a", "*.wav", "*.aac"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.m4v"));

        seekBar.setPrefWidth(460.0);
        seekBar.seekingProperty().addListener((obs, wasSeeking, seeking) -> {
            if (wasSeeking && !seeking) {
                seekCurrentPlayer();
            }
        });

        Button openButton = new Button("Open media");
        openButton.setOnAction(e -> openMedia(primaryStage));

        playPauseButton.setDisable(true);
        playPauseButton.setOnAction(e -> togglePlayback());

        HBox actions = new HBox(10.0, openButton, playPauseButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(16.0, fileLabel, seekBar, actions);
        root.setPadding(new Insets(28.0));
        root.setAlignment(Pos.CENTER_LEFT);

        primaryStage.setScene(new Scene(root, 540.0, 180.0));
        primaryStage.setTitle("RXSeekBar Media Demo");
        primaryStage.show();
    }

    private void openMedia(Stage owner) {
        File file = fileChooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.isDirectory()) {
            fileChooser.setInitialDirectory(parent);
        }
        if (player != null) {
            player.dispose();
        }
        player = new MediaPlayer(new Media(file.toURI().toString()));
        fileLabel.setText(file.getName());
        playPauseButton.setText("Pause");
        playPauseButton.setDisable(false);
        wirePlayer(player);
        player.play();
    }

    private void wirePlayer(MediaPlayer mediaPlayer) {
        seekBar.secondaryProgressProperty().unbind();
        seekBar.setProgress(0.0);
        seekBar.setSecondaryProgress(0.0);

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!seekBar.isSeeking()) {
                seekBar.setProgress(ratio(newTime, mediaPlayer.getTotalDuration()));
            }
        });
        seekBar.secondaryProgressProperty().bind(Bindings.createDoubleBinding(
                () -> ratio(mediaPlayer.getBufferProgressTime(), mediaPlayer.getTotalDuration()),
                mediaPlayer.bufferProgressTimeProperty(), mediaPlayer.totalDurationProperty()));
    }

    private void togglePlayback() {
        if (player == null) {
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
            playPauseButton.setText("Play");
        } else {
            player.play();
            playPauseButton.setText("Pause");
        }
    }

    private void seekCurrentPlayer() {
        if (player == null) {
            return;
        }
        Duration total = player.getTotalDuration();
        if (total == null || total.isUnknown() || total.isIndefinite()
                || !Double.isFinite(total.toMillis()) || total.toMillis() <= 0.0) {
            return;
        }
        player.seek(toSeekTime(total, seekBar.getProgress()));
    }

    private static Duration toSeekTime(Duration total, double progress) {
        double millis = total.toMillis() * RXMath.clamp0To1(progress);
        if (!Double.isFinite(millis) || millis <= 0.0) {
            return Duration.ZERO;
        }
        if (MAC_OS) {
            // JavaFX's macOS media backend logs CoreMedia warnings for fractional second seeks.
            return Duration.seconds(Math.round(millis / 1000.0));
        }
        return Duration.millis(millis);
    }

    private static double ratio(Duration time, Duration total) {
        if (time == null || total == null) {
            return 0.0;
        }
        double millis = time.toMillis();
        double totalMillis = total.toMillis();
        if (!Double.isFinite(millis) || !Double.isFinite(totalMillis) || totalMillis <= 0.0) {
            return 0.0;
        }
        return RXMath.clamp0To1(millis / totalMillis);
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        if (player != null) {
            player.dispose();
            player = null;
        }
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
