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
package com.leewyatt.rxcontrols.animation.carousel;

import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 矩形逐渐显示
 */
public class AnimRectangle extends CarouselAnimationBase {
    private double arcWidth;
    private double arcHeight;
    private final Rectangle rect;
    private final Timeline animation;
    private final double opacity;

    public AnimRectangle() {
        this(12, 12, 1.0);
    }

    public AnimRectangle(double arcWidth, double arcHeight, double opacity) {
        this.arcWidth = arcWidth;
        this.arcHeight = arcHeight;
        this.opacity = opacity;
        this.rect = new Rectangle();
        this.animation = new Timeline();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);

        rect.setArcWidth(getArcWidth());
        rect.setArcHeight(getArcHeight());

//        double min = Math.min(paneWidth, paneHeight);

        KeyValue kv1, kv2, kv3, kv4, kv5, kv6, kv7, kv8, kv9, kv10;
        if (direction) {
            nextPane.setClip(rect);
            nextPane.toFront();
            //-------第一帧的keyValues------
            kv1 = new KeyValue(rect.xProperty(), paneWidth / 2d);
            kv2 = new KeyValue(rect.yProperty(), paneHeight / 2d);
            kv3 = new KeyValue(rect.widthProperty(), 0d);
            kv4 = new KeyValue(rect.heightProperty(), 0d);
            kv5 = new KeyValue(currentPane.opacityProperty(), 1.0d);
            //-------第二帧的keyValues------
            kv6 = new KeyValue(rect.widthProperty(), paneWidth);
            kv7 = new KeyValue(rect.heightProperty(), paneHeight);
            kv8 = new KeyValue(rect.xProperty(), 0);
            kv9 = new KeyValue(rect.yProperty(), 0);
            kv10 = new KeyValue(currentPane.opacityProperty(), opacity);
        } else {
            currentPane.toFront();
            currentPane.setClip(rect);
            //-------第一帧的keyValues------
            kv1 = new KeyValue(rect.xProperty(), 0d);
            kv2 = new KeyValue(rect.yProperty(), 0d);
            kv3 = new KeyValue(rect.widthProperty(), paneWidth);
            kv4 = new KeyValue(rect.heightProperty(), paneHeight);
            kv5 = new KeyValue(nextPane.opacityProperty(), opacity);
            //-------第二帧的keyValues------
            kv6 = new KeyValue(rect.widthProperty(), 0);
            kv7 = new KeyValue(rect.heightProperty(), 0);
            kv8 = new KeyValue(rect.xProperty(), paneWidth / 2d);
            kv9 = new KeyValue(rect.yProperty(), paneHeight / 2d);
            kv10 = new KeyValue(nextPane.opacityProperty(), 1);
        }

        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, event -> {
                   nextPane.fireOpening();
                    currentPane.fireClosing();
                }, kv1, kv2, kv3, kv4, kv5),
                new KeyFrame(animationTime, kv6, kv7, kv8, kv9, kv10));
        animation.setOnFinished(event -> {
            nextPane.setClip(null);
            nextPane.setOpacity(1.0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setOpacity(1.0);
            currentPane.setClip(null);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setClip(null);
            pane.setOpacity(1.0);
        }
    }

    public double getArcWidth() {
        return arcWidth;
    }

    public void setArcWidth(double arcWidth) {
        this.arcWidth = arcWidth;
    }

    public double getArcHeight() {
        return arcHeight;
    }

    public void setArcHeight(double arcHeight) {
        this.arcHeight = arcHeight;
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
