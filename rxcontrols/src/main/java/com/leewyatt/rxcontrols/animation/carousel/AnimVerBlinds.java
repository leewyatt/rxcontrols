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
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 垂直百叶窗效果
 */
public class AnimVerBlinds extends CarouselAnimationBase {
    /**
     * 百叶窗块的数量 ;
     */
    private final int blockNum;
    /**
     * 默认块数量
     */
    private static final int DEFAULT_BLOCK_NUM = 10;
    /**
     * 用于clip的绑定
     */
    private ObjectBinding<Node> clipBinding;
    /**
     * 矩形数组,用于组合成一个图形,然后去clip组件
     */
    private Rectangle[] rects;
    /**
     * 动画
     */
    private Timeline animation;

    /**
     * 用于矩形高度的绑定
     */
    private SimpleDoubleProperty widthBinding;

    public AnimVerBlinds() {
        this(DEFAULT_BLOCK_NUM);
    }

    public AnimVerBlinds(int blockNum) {
        this.blockNum = blockNum;
        init();
    }

    private void init() {
        animation = new Timeline();
        widthBinding = new SimpleDoubleProperty(0);
        rects = new Rectangle[blockNum];
        for (int i = 0; i < blockNum; i++) {
            rects[i] = new Rectangle();
            rects[i].widthProperty().bind(widthBinding);
            rects[i].setY(0);
        }
        clipBinding = Bindings.createObjectBinding(() -> {
            Shape shape = rects[0];
            for (int i = 1; i < blockNum; i++) {
                shape = Shape.union(shape, rects[i]);
            }
            return shape;
        }, widthBinding);
    }



    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        for (int i = 0; i < blockNum; i++) {
            rects[i].setX(paneWidth / blockNum * i);
            rects[i].setHeight(paneHeight);
        }

        if (direction) {
            nextPane.toFront();
            nextPane.clipProperty().bind(clipBinding);
        } else {
            currentPane.toFront();
            currentPane.clipProperty().bind(clipBinding);
        }
        //每一块的宽度
        double blockWidth = CarouselAnimUtil.getPaneWidth(contentPane) / blockNum;
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(widthBinding, direction ? 0 : blockWidth))
                , new KeyFrame(animationTime, new KeyValue(widthBinding, direction ? blockWidth : 0)));

        animation.setOnFinished(event -> onFinished(nextPane, currentPane));
        return animation;
    }

    private void onFinished(RXCarouselPane nextPane, RXCarouselPane currentPane) {
        nextPane.clipProperty().unbind();
        nextPane.setClip(null);
        nextPane.setVisible(true);
        nextPane.fireOpened();
        currentPane.clipProperty().unbind();
        currentPane.setClip(null);
        currentPane.setVisible(false);
        currentPane.fireClosed();
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
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    @Override
    public void dispose() {
        if (rects != null) {
            for (Rectangle rect : rects) {
                rect.heightProperty().unbind();
                rect.xProperty().unbind();
                rect.widthProperty().unbind();
            }
            rects = null;
        }
        if (clipBinding != null) {
            clipBinding.dispose();
        }
         CarouselAnimUtil.disposeTimeline(animation);
    }
}
