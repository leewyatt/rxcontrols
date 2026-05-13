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
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * 轮播图效果: 扇形
 */
public class AnimSector extends CarouselAnimationBase {
    private double startAngle;
    private Timeline animation;
    private Arc arcClip;

    public AnimSector() {
        this(90);
    }

    public AnimSector(double startAngle) {
        this.startAngle = startAngle;
        animation = new Timeline();
        arcClip = new Arc();
        arcClip.setType(ArcType.ROUND);
        arcClip.setStartAngle(startAngle);
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        //计算外接圆半径
        double r = Math.sqrt(paneWidth * paneWidth + paneHeight * paneHeight) / 2.0;

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        arcClip.setCenterX(paneWidth / 2);
        arcClip.setCenterY(paneHeight / 2);
        arcClip.setRadiusX(r);
        arcClip.setRadiusY(r);

        if (direction) {
            currentPane.toFront();
            currentPane.setClip(arcClip);
        } else {
            nextPane.toFront();
            nextPane.setClip(arcClip);
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();},
                        new KeyValue(arcClip.lengthProperty(), direction ? 360 : 0)),
                new KeyFrame(animationTime,
                        new KeyValue(arcClip.lengthProperty(), direction ? 0 : 360)));
        animation.setOnFinished(event -> {
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.fireClosed();
            if(direction){
                currentPane.setClip(null);
            }else{
                nextPane.setClip(null);
            }
        });
        return animation;
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
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
        }
    }
}
