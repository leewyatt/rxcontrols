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
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.effect.MotionBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 仿QQ音乐切换页面
 */
public class AnimAppear extends CarouselAnimationBase {
    private final Timeline animation;
    private final MotionBlur blur;
    private final double offsetX;
    private final double blurRadius;

    public AnimAppear() {
        this(10, 5);
    }

    public AnimAppear(double offsetX, double blurRadius) {
        animation = new Timeline();
        blur = new MotionBlur();
        this.offsetX = offsetX;
        this.blurRadius = blurRadius;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        //清理动画对页面造成的影响,比如模糊,位移等
        reset(panes, nextIndex);
        //获取当前页面
        RXCarouselPane currentPane = panes.get(currentIndex);
        //获取下一页
        RXCarouselPane nextPane = panes.get(nextIndex);
        nextPane.setEffect(blur);

        animation.getKeyFrames().setAll(
                //动画第一帧.开始前的各种准备,初始位置的设置等. 以及第一帧放完后的事件触发(比如正在打开,正在关闭,已经关闭)
                new KeyFrame(Duration.ZERO, event -> {
                   nextPane.fireOpening();
                    currentPane.fireClosing();
                    currentPane.setVisible(false);
                    currentPane.fireClosed();
                }, new KeyValue(nextPane.translateXProperty(), offsetX), new KeyValue(blur.radiusProperty(), blurRadius)),
                //动画第二帧,真正的动画内容
                new KeyFrame(animationTime, new KeyValue(nextPane.translateXProperty(), 0), new KeyValue(blur.radiusProperty(), 0)));

        animation.setOnFinished(event -> {
            nextPane.setEffect(null);
            nextPane.setTranslateX(0);
            nextPane.fireOpened();//动画播完后,页面正式打开,OPEND
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
            pane.setTranslateX(0);
            pane.setEffect(null);
        }
    }
}
