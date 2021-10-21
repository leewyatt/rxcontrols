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
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 高斯模糊
 */
public class AnimGaussianBlur extends CarouselAnimationBase {
    private final Timeline animation;
    private final GaussianBlur gaussianBlur;
    private final GaussianBlur gaussianBlur2;
    private double radius;

    public AnimGaussianBlur() {
        this(30);
    }

    public AnimGaussianBlur(double radius) {
        this.radius = radius;
        this.animation = new Timeline();
        this.gaussianBlur = new GaussianBlur();
        this.gaussianBlur2 = new GaussianBlur();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        nextPane.toBack();
        currentPane.setEffect(gaussianBlur);
        nextPane.setEffect(gaussianBlur2);
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(gaussianBlur.radiusProperty(), 0.0),
                        new KeyValue(currentPane.opacityProperty(), 1.0),
                        new KeyValue(gaussianBlur2.radiusProperty(), 30),
                        new KeyValue(nextPane.opacityProperty(), 0.0)
                ),
                new KeyFrame(animationTime,
                        new KeyValue(gaussianBlur.radiusProperty(), 30),
                        new KeyValue(currentPane.opacityProperty(), 0),
                        new KeyValue(gaussianBlur2.radiusProperty(), 0),
                        new KeyValue(nextPane.opacityProperty(), 1.0)
                )
        );
        animation.setOnFinished(event -> {
            nextPane.setEffect(null);
            nextPane.setOpacity(1.0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setEffect(null);
            currentPane.setOpacity(1.0);
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
            pane.setEffect(null);
            pane.setOpacity(1.0);
        }
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
