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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 随机多个圆形 逐渐显示
 */
public class AnimCircleArray extends CarouselAnimationBase {
    /**
     * 圆形的个数
     */
    private int circleNum;
    /**
     * 圆形的最大半价
     */
    private double maxRadius;
    /**
     * 圆形数组
     */
    private Circle[] circles;

    /**
     * 圆形的半径绑定
     */
    private SimpleDoubleProperty radiusBinding;
    /**
     * 用于clip的绑定
     */
    private ObjectBinding<Node> clipBinding;

    /**
     * 动画
     */
    private Timeline animation;

    private Random random;

    public AnimCircleArray() {
        this(20, 200d);
    }

    public AnimCircleArray(int circleNum, double maxRadius) {
        this.circleNum = (int) CarouselAnimUtil.clamp(2, 100, circleNum);
        this.maxRadius = maxRadius;
        random = new Random();
        radiusBinding = new SimpleDoubleProperty();
        circles = new Circle[this.circleNum];
        for (int i = 0; i < this.circleNum; i++) {
            circles[i] = new Circle();
            circles[i].radiusProperty().bind(radiusBinding);
        }
        animation = new Timeline();
        clipBinding = Bindings.createObjectBinding(
                () -> {
                    Shape shape = circles[0];
                    for (int i = 1; i < this.circleNum; i++) {
                        shape = Shape.union(shape, circles[i]);
                    }
                    return shape;
                }, radiusBinding);
    }

    /**
     * circleNum 只提供getter ,不提供setter
     * @return
     */
    public int getCircleNum() {
        return circleNum;
    }

    public double getMaxRadius() {
        return maxRadius;
    }

    public void setMaxRadius(double maxRadius) {
        this.maxRadius = maxRadius;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);

        for (int i = 0; i < circleNum; i++) {
            circles[i].setCenterX(random.nextDouble() * paneWidth);
            circles[i].setCenterY(random.nextDouble() * paneHeight);
        }
        if (direction) {
            nextPane.toFront();
            nextPane.clipProperty().bind(clipBinding);
        } else {
            currentPane.toFront();
            currentPane.clipProperty().bind(clipBinding);
        }

        double mr = maxRadius<=0?Math.max(paneWidth, paneHeight)/3:maxRadius;
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(radiusBinding, direction ? 0 : mr)),
                new KeyFrame(animationTime, new KeyValue(radiusBinding, direction ? mr : 0)));
        animation.setOnFinished(event -> {
            nextPane.clipProperty().unbind();
            nextPane.setClip(null);
            nextPane.setVisible(true);
            nextPane.fireOpened();
            currentPane.clipProperty().unbind();
            currentPane.setClip(null);
            currentPane.setVisible(false);
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
            pane.clipProperty().unbind();
            pane.setClip(null);
        }
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
        clipBinding.dispose();
    }
}
