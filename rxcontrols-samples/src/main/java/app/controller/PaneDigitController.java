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

import com.leewyatt.rxcontrols.controls.RXDigit;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class PaneDigitController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private AnchorPane rootPane;

    @FXML
    private BorderPane bgPane;

    @FXML
    private HBox dayOfWeek;

    @FXML
    private Label ampm;

    @FXML
    private RXDigit t1;

    @FXML
    private RXDigit t2;

    @FXML
    private RXDigit t3;

    @FXML
    private RXDigit t4;

    @FXML
    private RXDigit t5;

    @FXML
    private RXDigit t6;

    @FXML
    void initialize() {
        refreshTime();
    }

    // 设置星期几的样式
    private void setTodayStyle(int index) {
        ObservableList<Node> nodes = dayOfWeek.getChildren();
        for (Node node : nodes) {
            node.getStyleClass().setAll("planday");
        }
        nodes.get(index).getStyleClass().setAll("today");
    }

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HHmmss");

    public void refreshTime() {
        Timer secondTimer = new Timer(true);
        long delay1 = 1000 - System.currentTimeMillis() % 1000;
        secondTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                LocalTime lt = LocalTime.now();
                int hour = lt.getHour();
                Platform.runLater(() -> {
                    String time = fmt.format(lt);
                    if (hour >= 12) {
                        ampm.setText("PM");
                    } else {
                        ampm.setText("AM");
                    }
                    t1.setDigit(Integer.parseInt(String.valueOf(time.charAt(0))));
                    t2.setDigit(Integer.parseInt(String.valueOf(time.charAt(1))));
                    t3.setDigit(Integer.parseInt(String.valueOf(time.charAt(2))));
                    t4.setDigit(Integer.parseInt(String.valueOf(time.charAt(3))));
                    t5.setDigit(Integer.parseInt(String.valueOf(time.charAt(4))));
                    t6.setDigit(Integer.parseInt(String.valueOf(time.charAt(5))));
                    //time = null;
                });

            }
        }, delay1, 1000);

        //首次执行时修改
        setDayCss();
        //Timer 定期修改
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDate.now().plusDays(1).atTime(0, 0, 0, 0);
        long delay2 = ChronoUnit.MILLIS.between(startDate, endDate);
        Timer dateTimer = new Timer(true);
        dateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(()->{
                    setDayCss();
                });
            }
        }, delay2, 24 * 60 * 60 * 1000);

    }

    private void setDayCss() {
        LocalDate ld = LocalDate.now();
        //计算好星期几,设置样式
        setTodayStyle(ld.getDayOfWeek().getValue() - 1);
    }
}
