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
import com.leewyatt.rxcontrols.controls.RXCarousel.RXDirection;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
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
 * 轮播图效果: 两条线
 */
public class AnimLinePair extends CarouselAnimationBase {
    private RXDirection direction;
    private final Timeline animation;
    private final Rectangle rectClip;
    private final SimpleDoubleProperty rectSizePro;

    public AnimLinePair() {
        this(RXDirection.HOR);
    }

    public AnimLinePair(RXDirection direction) {
        this.direction = direction;
        animation = new Timeline();
        rectSizePro = new SimpleDoubleProperty();
        rectClip = new Rectangle();
    }

    public RXDirection getDirection() {
        return direction;
    }

    public void setDirection(RXDirection direction) {
        this.direction = direction;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        if (direction) {
            nextPane.setClip(rectClip);
            nextPane.toFront();
        } else {
            currentPane.setClip(rectClip);
            currentPane.toFront();
        }
        KeyValue kv;
        if (this.direction == RXDirection.HOR) {
            rectSizePro.set(direction ? 0 : paneWidth);
            rectClip.widthProperty().bind(rectSizePro);
            rectClip.setHeight(paneHeight);
            rectClip.translateXProperty().bind(rectSizePro.negate().add(paneWidth).divide(2.0));
            kv = new KeyValue(rectSizePro, direction ? paneWidth : 0);
        } else {//DIR.VER
            rectSizePro.set(direction ? 0 : paneHeight);
            rectClip.heightProperty().bind(rectSizePro);
            rectClip.setWidth(paneWidth);
            rectClip.translateYProperty().bind(rectSizePro.negate().add(paneHeight).divide(2.0));
            kv = new KeyValue(rectSizePro, direction ? paneHeight : 0);
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        }
                ),
                new KeyFrame(animationTime, kv));
        animation.setOnFinished(event -> {
            nextPane.setClip(null);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setClip(null);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setClip(null);
        }
    }
}
