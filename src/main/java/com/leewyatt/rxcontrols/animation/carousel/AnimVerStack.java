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
 * 轮播图效果: 垂直层叠切换
 */
public class AnimVerStack extends CarouselAnimationBase {
    /**
     * 如果offsetY没有指定,那么会根据宽度计算一个默认的偏移量
     */
    private double offsetY;

    /**
     * 第一个时间轴动画
     */
    private final Timeline timeline;

    /**
     * 第二个时间轴动画
     */
    private final Timeline timeline2;

    /**
     * 顺序动画,把前两个动画串在一起
     */
    SequentialTransition animation;

    public AnimVerStack() {
        timeline = new Timeline();
        timeline2 = new Timeline();
        animation = new SequentialTransition();
    }

    public AnimVerStack(double offsetY) {
        this();
        this.offsetY = offsetY;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        RXCarouselPane currentPane = panes.get(currentIndex);
        currentPane.toFront();//当前页面放在前面
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        //计算左侧动画的偏移量[取绝对值]
        double showOffset = Math.abs(getOffsetY() == 0 ? paneHeight*0.25 : getOffsetY());
        //因为是顺序动画, 所以先平分时间
        Duration useTime = animationTime.divide(2);
        KeyValue kv1 , kv2, kv3 , kv4 ;
        if (direction) {
            //第一帧的关键值
            kv1 = new KeyValue(nextPane.translateYProperty(), 0);
            kv2 = new KeyValue(currentPane.translateYProperty(), 0);

            //第二帧的关键值
            kv3 = new KeyValue(nextPane.translateYProperty(), showOffset);
            kv4 = new KeyValue(currentPane.translateYProperty(), -paneHeight);
        }else{
            //第一帧的关键值
            kv1 = new KeyValue(currentPane.translateYProperty(), 0);
            kv2 = new KeyValue(nextPane.translateYProperty(), 0);

            //第二帧的关键值
            kv3 = new KeyValue(currentPane.translateYProperty(), showOffset);
            kv4 = new KeyValue(nextPane.translateYProperty(), -paneHeight);
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
            nextPane.setTranslateY(0);
            nextPane.fireOpened();
            currentPane.setTranslateY(0);
            currentPane.setVisible(false);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);

    }

    private double getOffsetY() {
        return offsetY;
    }

    private void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setTranslateY(0);
        }
    }

    @Override
    public void dispose() {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
        animation=null;
        CarouselAnimUtil.disposeTimeline(timeline,timeline2);
    }
}
