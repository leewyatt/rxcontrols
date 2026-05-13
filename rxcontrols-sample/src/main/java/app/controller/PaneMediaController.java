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
package app.controller;

import com.leewyatt.rxcontrols.controls.RXAudioSpectrum;
import com.leewyatt.rxcontrols.controls.RXLrcView;
import com.leewyatt.rxcontrols.controls.RXMediaProgressBar;
import com.leewyatt.rxcontrols.pojo.LrcDoc;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.leewyatt.rxcontrols.utils.StyleUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.BoxBlur;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class PaneMediaController {

    /**
     *  歌词文件的编码格式
     *  可以自己写代码判断编码或者使用第三方库判断编码. 这里偷懒硬编码 gbk
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
    private RXMediaProgressBar progressBar;

    @FXML
    private RXAudioSpectrum spectrum;


    private FileChooser fileChooser = new FileChooser();

    private MediaPlayer player;

    @FXML
    void openFileAction(ActionEvent event) {

        Window window = lrcPane.getScene().getWindow();
        File mp3File = fileChooser.showOpenDialog(window);
        if (mp3File != null) {

            if(player!=null){
                player.dispose();
                lrcPane.setLrcDoc(null);
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
        player.setAudioSpectrumThreshold(-65);
        spectrum.audioSpectrumNumBandsProperty().bind(player.audioSpectrumNumBandsProperty());
        spectrum.audioSpectrumThresholdProperty().bind(player.audioSpectrumThresholdProperty());
        player.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
            spectrum.setMagnitudes(magnitudes);
        });

    }

    private void initProgressBar(MediaPlayer player) {
        progressBar.setCurrentTime(Duration.ZERO);
        progressBar.durationProperty().bind(player.getMedia().durationProperty());
        progressBar.bufferProgressTimeProperty().bind(player.bufferProgressTimeProperty());
        player.currentTimeProperty().addListener((ob1, ov1, nv1) -> {
            progressBar.setCurrentTime(nv1);
        });

        progressBar.setOnMouseDragged(event1 -> {
            if ( player.getStatus() == MediaPlayer.Status.PLAYING) {
                player.seek(progressBar.getCurrentTime());
            }
        });

        progressBar.setOnMouseClicked(event1 -> {
            if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                player.seek(progressBar.getCurrentTime());

            }
        });
    }

    private void initLrc(File file) {
        String lrcPath = file.getAbsolutePath().replaceAll("mp3$","lrc");
        File lrcFile = new File(lrcPath);
        if (lrcFile.exists()) {
            String lrc = "";
            try {

                 lrc = new String(Files.readAllBytes(Paths.get(lrcPath)), LRC_CODE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            lrcPane.setLrcDoc(LrcDoc.parseLrcDoc(lrc));
            lrcPane.currentTimeProperty().bind(player.currentTimeProperty());
        }
    }



    @FXML
    void initialize() {
        fileChooser.setTitle("打开mp3文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("mp3", "*.mp3"));

        //实现切换样式功能
        String[] styleSheets = {
                RXResources.load("/css/spectrum_01.css").toExternalForm(),
                RXResources.load("/css/spectrum_02.css").toExternalForm(),
                RXResources.load("/css/spectrum_03.css").toExternalForm(),
                RXResources.load("/css/spectrum_04.css").toExternalForm(),
                RXResources.load("/css/spectrum_05.css").toExternalForm(),
                RXResources.load("/css/spectrum_06.css").toExternalForm()
                };
        BoxBlur barEffect = new BoxBlur(2.5,2.5,1);
        Shape shape = new Polygon(5,0,0,5,15,10,15,10);
        styleGroup.selectedToggleProperty().addListener((ob, ov, nv) ->{
            int index = styleGroup.getToggles().indexOf(nv);
            if(index==6){
                spectrum.setBarShape(shape);
            }else{
                spectrum.setBarShape(null);
            }
            if(index==0){
                StyleUtil.removeSheets(spectrum,styleSheets);
            }else {
                StyleUtil.toggleSheets(spectrum,styleSheets,styleSheets[index-1]);
            }
        });
    }
}
