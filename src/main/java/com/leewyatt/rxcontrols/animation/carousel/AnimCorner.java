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
 * 轮播图效果: 从四个角开始往中间移动
 */
public class AnimCorner extends CarouselAnimationBase {
    /**
     * rectTopLeft     rectTopRight
     * rectBottomLeft  rectBottomRight
     */
    private Rectangle rectTopLeft, rectTopRight, rectBottomLeft, rectBottomRight;
    /**
     * 在页面显示的时候有一个缩放的动画
     */
    private double scaleX;
    private double scaleY;

    private Timeline animation;
    private ObjectBinding<Node> shapeBinding;

    public AnimCorner() {
        this(1.5, 1.5);
    }

    public AnimCorner(double scaleX, double scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        rectTopLeft = new Rectangle();
        rectTopRight = new Rectangle();
        rectBottomLeft = new Rectangle();
        rectBottomRight = new Rectangle();
        animation = new Timeline();
        shapeBinding = Bindings.createObjectBinding(
                () ->
                        Shape.union(Shape.union(rectTopLeft, rectBottomLeft), Shape.union(rectTopRight, rectBottomRight)),
                rectTopLeft.translateYProperty(), rectBottomLeft.translateXProperty(), rectTopRight.translateYProperty(), rectBottomRight.translateXProperty());
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        double w = paneWidth / 2;
        double h = paneHeight / 2;
        setRectSize(w, h, rectTopLeft, rectBottomLeft, rectTopRight, rectBottomRight);
        rectBottomLeft.setTranslateY(h);
        rectBottomRight.setTranslateX(w);

        nextPane.toFront();
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(rectTopLeft.translateYProperty(), -h),
                        new KeyValue(rectBottomLeft.translateXProperty(), -w),
                        new KeyValue(rectBottomRight.translateYProperty(), paneHeight),
                        new KeyValue(rectTopRight.translateXProperty(), paneWidth),
                        new KeyValue(nextPane.scaleXProperty(), scaleX),
                        new KeyValue(nextPane.scaleYProperty(), scaleY)),
                new KeyFrame(animationTime,
                        new KeyValue(rectTopLeft.translateYProperty(), 0),
                        new KeyValue(rectBottomLeft.translateXProperty(), 0),
                        new KeyValue(rectBottomRight.translateYProperty(), h),
                        new KeyValue(rectTopRight.translateXProperty(), w),
                        new KeyValue(nextPane.scaleXProperty(), 1.0),
                        new KeyValue(nextPane.scaleYProperty(), 1.0))
        );

        nextPane.clipProperty().bind(shapeBinding);

        animation.setOnFinished(event -> {
            nextPane.setVisible(true);
            nextPane.clipProperty().unbind();
            nextPane.setClip(null);
            nextPane.setScaleX(1.0);
            nextPane.setScaleY(1.0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
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
        shapeBinding.dispose();
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setScaleX(1.0);
            pane.setScaleY(1.0);
            pane.clipProperty().unbind();
            pane.setClip(null);
        }
    }

    private void setRectSize(double w, double h, Rectangle... rects) {
        for (Rectangle rect : rects) {
            rect.setWidth(w);
            rect.setHeight(h);
        }
    }
}
