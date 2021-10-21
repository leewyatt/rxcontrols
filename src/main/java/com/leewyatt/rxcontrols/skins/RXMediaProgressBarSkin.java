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
package com.leewyatt.rxcontrols.skins;

import com.leewyatt.rxcontrols.controls.RXMediaProgressBar;
import javafx.beans.binding.NumberBinding;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * @author LeeWyatt
 * <p>
 * 多媒体进度条组件的皮肤
 */
public class RXMediaProgressBarSkin extends SkinBase<RXMediaProgressBar> {

    /**
     * 指母
     */
    private StackPane thumb;
    /**
     * 轨道
     */
    private StackPane track;
    /**
     * 缓存轨道
     */
    private final StackPane bufferTrack;
    /**
     * 播放进度轨道
     */
    private final StackPane currentTrack;
    /**
     * 容器存放上面的其他组件
     */
    private final Pane contentPane;

    private RXMediaProgressBar control;

    private ChangeListener<Number> numberChangeListener = (ob, ov, nv) -> {
        double paddingH = thumb.getWidth() / 2;
        Insets padding = control.getPadding();
        control.setPadding(new Insets(padding.getTop(), paddingH, padding.getBottom(), paddingH));
    };

    private ChangeListener changeListener = (ob, o, n) -> updateUI();

    private EventHandler<MouseEvent> mouseDraggedHandler = event -> {
        Point2D p = thumb.localToParent(event.getX(), event.getY());
        double x = p.getX() - thumb.getWidth() / 2;
        double minX = -thumb.getWidth() / 2;
        double maxX = track.getWidth() - thumb.getWidth() / 2;
        if (x < minX) {
            x = minX;
        } else if (x > maxX) {
            x = maxX;
        }
        control.setCurrentTime(Duration.millis(getMills(control.getDuration())).multiply((x + thumb.getWidth() / 2) / track.getWidth()));
    };

    private EventHandler<MouseEvent> mouseClickedHandler = event -> {
        double x = track.localToParent(event.getX(), event.getY()).getX();
        control.setCurrentTime(Duration.millis(getMills(control.getDuration())).multiply((x - thumb.getWidth() / 2) / track.getWidth()));
    };

    public RXMediaProgressBarSkin(RXMediaProgressBar control) {
        super(control);
        this.control = control;
        contentPane = new Pane();
        contentPane.getStyleClass().add("content-pane");

        NumberBinding doubleHeightBing = contentPane.heightProperty().multiply(0.387);
        track = new StackPane();
        track.getStyleClass().add("track");
        track.prefHeightProperty().bind(doubleHeightBing);
        track.prefWidthProperty().bind(contentPane.widthProperty());

        bufferTrack = new StackPane();
        bufferTrack.setMouseTransparent(true);
        bufferTrack.prefHeightProperty().bind(doubleHeightBing);
        bufferTrack.getStyleClass().add("buffer-track");

        currentTrack = new StackPane();
        currentTrack.setMouseTransparent(true);
        currentTrack.getStyleClass().add("current-track");
        currentTrack.prefHeightProperty().bind(doubleHeightBing);

        thumb = new StackPane();
        thumb.getStyleClass().add("thumb");
        thumb.layoutXProperty().bind(currentTrack.widthProperty().subtract(thumb.widthProperty().divide(2.0)));

        //调整padding
        thumb.widthProperty().addListener(numberChangeListener);
        thumb.prefHeightProperty().bind(doubleHeightBing.multiply(2.1));
        thumb.prefWidthProperty().bind(thumb.prefHeightProperty());

        layoutYCenter(contentPane, track);
        layoutYCenter(contentPane, bufferTrack);
        layoutYCenter(contentPane, currentTrack);
        layoutYCenter(contentPane, thumb);

        contentPane.getChildren().addAll(track, bufferTrack, currentTrack, thumb);
        getChildren().add(contentPane);

        contentPane.widthProperty().addListener(changeListener);
        control.bufferProgressTimeProperty().addListener(changeListener);
        control.durationProperty().addListener(changeListener);
        control.currentTimeProperty().addListener(changeListener);

        // 缓存进度条最大宽
        bufferTrack.maxWidthProperty().bind(track.prefWidthProperty());
        // 当前进度条最大宽
        currentTrack.maxWidthProperty().bind(track.prefWidthProperty());

        //鼠标拖动,改变进度
        thumb.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        //鼠标点击改变进度
        control.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);

    }

    private void updateUI() {
        double width = contentPane.getWidth();
        //总持续时间
        double total = getMills(control.getDuration());
        //当前播放时间
        double current = getMills(control.getCurrentTime());
        currentTrack.setPrefWidth(current / total * width);
        //缓存时间
        double buffer = getMills(control.getBufferProgressTime());
        bufferTrack.setPrefWidth(buffer / total * width);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(contentPane, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);

    }

    private void layoutYCenter(Pane parentPane, Pane childPane) {
        childPane.layoutYProperty().bind(parentPane.heightProperty().subtract(childPane.heightProperty()).divide(2.0));
    }

    /**
     * 最小宽
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return rightInset + leftInset + 60;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return rightInset + leftInset + 140;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
                                       double leftInset) {
        return topInset + bottomInset + 14.4;
    }

    /**
     * 最大高
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + bottomInset + 30;
    }

    /**
     * 验证Duration 能否获取到一个正浮点数
     */
    private boolean verifyTime(Duration duration) {
        if (duration == null) {
            return false;
        }
        double millis = duration.toMillis();
        return !Double.isNaN(millis) && !Double.isInfinite(millis) && !(millis < 0);
    }

    /**
     * 获取持续时间转成毫秒数
     */
    private double getMills(Duration duration) {
        if (verifyTime(duration)) {
            return duration.toMillis();
        } else {
            return 0;
        }
    }

    @Override
    public void dispose() {
        track.prefHeightProperty().unbind();
        track.prefWidthProperty().unbind();
        bufferTrack.prefHeightProperty().unbind();
        currentTrack.prefHeightProperty().unbind();
        thumb.layoutXProperty().unbind();
        thumb.widthProperty().removeListener(numberChangeListener);
        thumb.prefHeightProperty().unbind();
        thumb.prefWidthProperty().unbind();
        track.layoutYProperty().unbind();
        bufferTrack.layoutYProperty().unbind();
        currentTrack.layoutYProperty().unbind();
        thumb.layoutYProperty().unbind();
        contentPane.widthProperty().removeListener(changeListener);
        control.bufferProgressTimeProperty().removeListener(changeListener);
        control.durationProperty().removeListener(changeListener);
        control.currentTimeProperty().removeListener(changeListener);
        bufferTrack.maxWidthProperty().unbind();
        currentTrack.maxWidthProperty().unbind();
        thumb.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
        getChildren().clear();
        super.dispose();
    }
}
