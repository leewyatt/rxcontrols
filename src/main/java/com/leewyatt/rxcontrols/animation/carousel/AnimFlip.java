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
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Point3D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 翻转页面
 */
public class AnimFlip extends CarouselAnimationBase {
    /**
     * 暂停动画,用时0毫秒,主要是启动closing
     */
    private final PauseTransition pt;
    /**
     * 当前页面旋转,结束时隐藏
     */
    private final RotateTransition hideAnimate;
    /**
     * 下一页面显示,旋转
     */
    private final RotateTransition showAnimate;
    /**
     * 顺序动画, 把上面的动画窜在一起,按顺序执行
     */
    private final SequentialTransition animation;
    /**
     * 旋转轴
     */
    private final Point3D axis;

    public AnimFlip() {
        this(RXCarousel.RXDirection.HOR);
    }

    public AnimFlip(RXCarousel.RXDirection direction) {
        if (direction == RXCarousel.RXDirection.HOR) {
            axis = Rotate.X_AXIS;
        } else {
            axis = Rotate.Y_AXIS;
        }
        hideAnimate = new RotateTransition();
        showAnimate = new RotateTransition();
        animation = new SequentialTransition();
        pt = new PauseTransition(Duration.ZERO);
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        nextPane.toBack();
        Duration useTime = animationTime.divide(2);
        hideAnimate.setDuration(useTime);
        hideAnimate.setNode(currentPane);
        hideAnimate.setAxis(axis);
        hideAnimate.setFromAngle(0);
        hideAnimate.setToAngle(direction ? 90 : -90);
        // 第一段动画播放完毕后,隐藏pane1,显示pane2
        hideAnimate.setOnFinished(e -> {
            //第一段动画播放完毕. 当前页面就关闭了
            currentPane.setVisible(false);
            currentPane.fireClosed();
            //第一段动画播放完毕,下一页面就开始展示了
            nextPane.setVisible(true);
           nextPane.fireOpening();
        });
        showAnimate.setDuration(useTime);
        showAnimate.setNode(nextPane);
        showAnimate.setAxis(axis);
        showAnimate.setFromAngle(direction ? -90 : 90);
        showAnimate.setToAngle(0);

        pt.setOnFinished(event ->currentPane.fireClosing());

        animation.getChildren().setAll(pt, hideAnimate, showAnimate);
        animation.setOnFinished(event -> {
            nextPane.setVisible(true);
            nextPane.setRotate(0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setRotate(0);
        });

        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
//        animation.stop();animation.getChildren().clear();

    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setRotate(0);
        }
    }

    @Override
    public void dispose() {
        if (animation.getStatus()== Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
    }
}
