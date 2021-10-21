/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package app.ui;

import com.leewyatt.rxcontrols.controls.RXAudioSpectrum;
import com.leewyatt.rxcontrols.controls.RXAudioSpectrum.CrestPos;
import com.leewyatt.rxcontrols.controls.RXMediaProgressBar;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;

/**
 *
 */
public class TestMediaProgressBar extends Application {
    MediaView mv = new MediaView();

    @Override
    public void start(Stage primaryStage) {

        VBox root = new VBox();

        FileChooser fc = new FileChooser();
        fc.setTitle("播放视频或音频文件");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("视频/音频", "*.wav", "*.mp3", "*.mp4", "*.m3u8"));
        Button btnOpen = new Button("打开文件");
        Label label = new Label();

        btnOpen.setOnAction(event -> {
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) {
                File dir = new File(file.getParent());
                if (dir.isDirectory()) {
                    fc.setInitialDirectory(dir);
                }
                label.setText(file.getAbsolutePath());
                mv.setMediaPlayer(new MediaPlayer(new Media(file.toURI().toString())));
//                mv.getMediaPlayer().play();

            }
        });

        HBox boxTop = new HBox(btnOpen, label);


        mv.setFitWidth(500);

        RXMediaProgressBar progressbar = new RXMediaProgressBar();
//        progressbar.setPrefHeight(10);
        progressbar.setStyle("-fx-border-color: GREEN");
        RXAudioSpectrum audioSpectrum = new RXAudioSpectrum();
        audioSpectrum.setAudioSpectrumNumBands(30);
        audioSpectrum.setCrestPos(CrestPos.DOUBLE);
//        audioSpectrum.setAudioSpectrumThreshold(200);
        HBox controlBox = new HBox();
        Button buttonPosM = new Button("波峰在中");
        Button buttonPosL = new Button("波峰在左");
        Button buttonPosR = new Button("波峰在右");
        Button buttonS = new Button("波峰在两侧");
        Button buttonD = new Button("Double波峰");
        Button buttonHeight = new Button("调整高度");
        Button buttonNum = new Button("调整数量");
        Button buttonPause = new Button("暂停");
        Button buttonStop = new Button("停止");
        Button buttonPlay = new Button("播放");
        controlBox.getChildren().addAll(buttonPlay,buttonPause,buttonStop,buttonPosM, buttonPosL, buttonPosR, buttonS,buttonD,buttonHeight,buttonNum);
        buttonPlay.setOnAction(event -> {
            MediaPlayer player = mv.getMediaPlayer();
            if(player==null){
                return;
            }
            player.play();
        });
        buttonPause.setOnAction(event -> {
            MediaPlayer player = mv.getMediaPlayer();
            if(player==null){
                return;
            }
            player.pause();
        });

        buttonStop.setOnAction(event -> {
            MediaPlayer player = mv.getMediaPlayer();
            if(player==null){
                return;
            }
            player.stop();
        });


        buttonPosM.setOnAction(event -> {
            audioSpectrum.setCrestPos(CrestPos.MIDDLE);
        });

        buttonPosL.setOnAction(event -> {
            audioSpectrum.setCrestPos(CrestPos.LEFT);
        });
        buttonPosR.setOnAction(event -> {
            audioSpectrum.setCrestPos(CrestPos.RIGHT);
        });
        buttonS.setOnAction(event -> {
            audioSpectrum.setCrestPos(CrestPos.SIDE);
        });
        buttonHeight.setOnAction(event -> {
            MediaPlayer player = mv.getMediaPlayer();
            if(player==null){
                return;
            }
            player.setAudioSpectrumThreshold(-(int)(Math.random()*200));
        });
        buttonD.setOnAction(event -> {
            audioSpectrum.setCrestPos(CrestPos.DOUBLE);
        });

        buttonNum.setOnAction(event -> {

            MediaPlayer player = mv.getMediaPlayer();
            if(player==null){
                return;
            }
            player.setAudioSpectrumNumBands((int)(Math.random()*150+50));
//            mv.getMediaPlayer().setAudioSpectrumNumBands(200);
        });

        root.getChildren().addAll(boxTop,progressbar,controlBox,audioSpectrum, mv);

        Button btnPlay = new Button("开始");
        btnPlay.getProperties().forEach((k, v) -> System.out.println(k + "\t" + v));
        btnPlay.setOnAction(event -> {
            if (mv.getMediaPlayer() != null) {
                Duration currentTime = progressbar.getCurrentTime();
                mv.getMediaPlayer().play();
                mv.getMediaPlayer().seek(currentTime);
            }
        });

        mv.mediaPlayerProperty().addListener((ob, ov, nv) -> {
            if (ov != null) {
                ov.dispose();
            }
            if (nv == null) {
                return;
            }
            progressbar.setCurrentTime(Duration.ZERO);

            progressbar.durationProperty().bind(nv.getMedia().durationProperty());
            progressbar.bufferProgressTimeProperty().bind(nv.bufferProgressTimeProperty());
            nv.currentTimeProperty().addListener((ob1, ov1, nv1) -> {
                progressbar.setCurrentTime(nv1);
            });

            progressbar.setOnMouseDragged(event -> {
                if (nv != null && nv.getStatus() == MediaPlayer.Status.PLAYING) {

                              nv.seek(progressbar.getCurrentTime());

                }
            });

            progressbar.setOnMouseClicked(event -> {
                if (nv != null && nv.getStatus() == MediaPlayer.Status.PLAYING) {
                            nv.seek(progressbar.getCurrentTime());
                }
            });


            audioSpectrum.audioSpectrumThresholdProperty().bind(nv.audioSpectrumThresholdProperty());
            audioSpectrum.audioSpectrumNumBandsProperty().bind(nv.audioSpectrumNumBandsProperty());
            nv.setAudioSpectrumNumBands(30);
            nv.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
//                for (int i = 0; i < magnitudes.length; i++) {
//                    magnitudes[i] = magnitudes[i]*1.2f+10;
//                }
                audioSpectrum.setMagnitudes(magnitudes);
            });
        });
        Scene scene = new Scene(root, 1000, 620);
        scene.getStylesheets().add(getClass().getResource("/css/test_bar.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("Cache TestProgressBar Window");
        primaryStage.show();
    }

}
