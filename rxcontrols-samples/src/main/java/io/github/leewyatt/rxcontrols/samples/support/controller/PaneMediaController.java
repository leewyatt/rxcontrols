package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleGroup;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ResourceBundle;

public class PaneMediaController {

    private static final boolean MAC_OS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    /**
     * 歌词文件的编码格式
     * 可以自己写代码判断编码或者使用第三方库判断编码. 这里偷懒硬编码 gbk
     */
    private final String LRC_CODE = "gbk";
    @FXML
    private ToggleGroup styleGroup;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private RXLrcView lrcPane;

    @FXML
    private RXSeekBar progressBar;

    @FXML
    private RXAudioSpectrum spectrum;


    private FileChooser fileChooser = new FileChooser();

    private MediaPlayer player;

    @FXML
    void openFileAction(ActionEvent event) {

        Window window = lrcPane.getScene().getWindow();
        File mp3File = fileChooser.showOpenDialog(window);
        if (mp3File != null) {

            if (player != null) {
                player.dispose();
                lrcPane.setDocument(null);
                lrcPane.currentTimeProperty().unbind();
            }

            File dir = new File(mp3File.getParent());
            if (dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
            player = new MediaPlayer(new Media(mp3File.toURI().toString()));
            initProgressBar(player);
            initLrc(mp3File);
            initSpectrum(player);

            player.play();
        }
    }

    private void initSpectrum(MediaPlayer player) {
        player.setAudioSpectrumThreshold((int) spectrum.getMinDecibels());
        player.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) ->
                spectrum.updateSpectrum(magnitudes));
    }

    private void initProgressBar(MediaPlayer player) {
        progressBar.secondaryProgressProperty().unbind();
        progressBar.setProgress(0.0);
        progressBar.setSecondaryProgress(0.0);
        player.currentTimeProperty().addListener((ob1, ov1, nv1) -> {
            if (!progressBar.isSeeking()) {
                progressBar.setProgress(ratio(nv1, player.getTotalDuration()));
            }
        });

        progressBar.secondaryProgressProperty().bind(Bindings.createDoubleBinding(
                () -> ratio(player.getBufferProgressTime(), player.getTotalDuration()),
                player.bufferProgressTimeProperty(), player.totalDurationProperty()));
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

    private void commitSeek() {
        if (player == null) {
            return;
        }
        Duration total = player.getTotalDuration();
        if (total == null || total.isUnknown() || total.isIndefinite()
                || !Double.isFinite(total.toMillis()) || total.toMillis() <= 0.0) {
            return;
        }
        player.seek(toSeekTime(total, progressBar.getProgress()));
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

    private void initLrc(File file) {
        String lrcPath = file.getAbsolutePath().replaceAll("mp3$", "lrc");
        File lrcFile = new File(lrcPath);
        if (lrcFile.exists()) {
            String lrc = "";
            try {

                lrc = new String(Files.readAllBytes(Paths.get(lrcPath)), LRC_CODE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            lrcPane.setDocument(RXLrcParser.parse(lrc).document());
            lrcPane.currentTimeProperty().bind(player.currentTimeProperty());
        } else {
            lrcPane.setDocument(null);
        }
    }


    @FXML
    void initialize() {
        fileChooser.setTitle("打开mp3文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("mp3", "*.mp3"));
        progressBar.seekingProperty().addListener((ob, wasSeeking, seeking) -> {
            if (wasSeeking && !seeking) {
                commitSeek();
            }
        });

        lrcPane.setCurrentLineScale(1.5);
        lrcPane.setAnimationDuration(Duration.millis(1000));
        lrcPane.setAnimated(true);

        lrcPane.setOnLineClicked(event -> {
            if (player != null) {
                player.seek(event.getTime());
            }
        });

        // 实现切换样式功能
        String[] styleSheets = {
                PaneMediaController.class.getResource("/css/spectrum-theme-sunset.css").toExternalForm(),
                PaneMediaController.class.getResource("/css/spectrum-theme-ocean.css").toExternalForm()
        };
        styleGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
            int index = styleGroup.getToggles().indexOf(nv);
            if (index == 0) {
                RXStyles.removeSheets(spectrum, styleSheets);
            } else {
                RXStyles.replaceSheets(spectrum, styleSheets, styleSheets[index - 1]);
            }
        });
    }
}
