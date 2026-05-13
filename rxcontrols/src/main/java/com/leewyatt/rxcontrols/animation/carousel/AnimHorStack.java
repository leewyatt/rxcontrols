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
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 水平层叠切换
 */
public class AnimHorStack extends CarouselAnimationBase {
    /**
     * 如果offsetX没有指定,那么会根据宽度计算一个默认的偏移量
     */
    private double offsetX;

    /**
     * 第一个时间轴动画
     */
    Timeline timeline;

    /**
     * 第二个时间轴动画
     */
    Timeline timeline2;

    /**
     * 顺序动画,把前两个动画串在一起
     */
    SequentialTransition animation;

    public AnimHorStack() {
        timeline = new Timeline();
        timeline2 = new Timeline();
        animation = new SequentialTransition();
    }

    public AnimHorStack(double offsetX) {
        this();
        this.offsetX = offsetX;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        RXCarouselPane currentPane = panes.get(currentIndex);
        currentPane.toFront();//当前页面放在前面
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        //计算左侧动画的偏移量[取绝对值]
        double showOffset = Math.abs(getOffsetX() == 0 ? paneWidth / 5 : getOffsetX());

        //因为是顺序动画, 所以先平分时间
        Duration useTime = animationTime.divide(2);
        KeyValue kv1 , kv2, kv3 , kv4 ;
        if (direction) {
            //第一帧的关键值
            kv1 = new KeyValue(nextPane.translateXProperty(), 0);
            kv2 = new KeyValue(currentPane.translateXProperty(), 0);

            //第二帧的关键值
            kv3 = new KeyValue(nextPane.translateXProperty(), -showOffset);
            kv4 = new KeyValue(currentPane.translateXProperty(), paneWidth);
        }else{
            //第一帧的关键值
            kv1 = new KeyValue(currentPane.translateXProperty(), 0);
            kv2 = new KeyValue(nextPane.translateXProperty(), 0);

            //第二帧的关键值
            kv3 = new KeyValue(currentPane.translateXProperty(), -showOffset);
            kv4 = new KeyValue(nextPane.translateXProperty(), paneWidth);
        }

         timeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        }, kv1, kv2),
                new KeyFrame(useTime,event->{
                    currentPane.toBack();//当前页面放到后面
                },kv3,kv4));
        //timeline2 基本就是翻转的timeline ,但是没有event事件
         timeline2.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                       kv3, kv4),
                new KeyFrame(useTime,kv1,kv2)
        );

        animation.getChildren().setAll(timeline,timeline2);

        animation.setOnFinished(e -> {
            nextPane.setTranslateX(0);
            nextPane.fireOpened();
            currentPane.setTranslateX(0);
            currentPane.setVisible(false);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);

    }

    private double getOffsetX() {
        return offsetX;
    }

    private void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setTranslateX(0);
        }
    }

    @Override
    public void dispose() {
        if (animation.getStatus()== Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
        CarouselAnimUtil.disposeTimeline(timeline,timeline2);
    }
}
