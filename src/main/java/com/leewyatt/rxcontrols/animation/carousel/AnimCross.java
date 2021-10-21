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
 * 轮播图效果: 类似于十字逐渐放大显示
 */
public class AnimCross extends CarouselAnimationBase {
    /**
     * rectHor     rectVer
     */
    private final Rectangle rectHor;
    private final Rectangle rectVer;
    /**
     * 在页面显示的时候有一个缩放的动画
     */
    private final Timeline animation;
    private final ObjectBinding<Node> shapeBinding;
    public AnimCross() {
        rectHor = new Rectangle();
        rectVer = new Rectangle();
        animation = new Timeline();
        //如果要更精确那么可以继续添加 rectHor.translateYProperty, rectVer.translateXProperty
        shapeBinding = Bindings.createObjectBinding(
                () ->
                        Shape.union(rectHor, rectVer),
                rectHor.heightProperty(), rectVer.widthProperty());

    }


    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex,currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        double w = paneWidth / 2;
        double h = paneHeight / 2;
        rectVer.setHeight(paneHeight);
        rectHor.setWidth(paneWidth);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);

        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                            if(direction){
                                nextPane.toFront();
                                nextPane.clipProperty().bind(shapeBinding);
                            }else{
                                currentPane.toFront();
                                currentPane.clipProperty().bind(shapeBinding);
                            }
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(rectVer.widthProperty(), direction?0:paneWidth),
                        new KeyValue(rectVer.translateXProperty(), direction?w:0),
                        new KeyValue(rectHor.heightProperty(), direction?0:paneHeight),
                        new KeyValue(rectHor.translateYProperty(), direction?h:0)),

                new KeyFrame(animationTime,
                        new KeyValue(rectVer.widthProperty(),  direction?paneWidth:0),
                        new KeyValue(rectVer.translateXProperty(), direction?0:w),
                        new KeyValue(rectHor.heightProperty(), direction?paneHeight:0),
                        new KeyValue(rectHor.translateYProperty(), direction?0:h)
                )
        );
        animation.setOnFinished(event -> {
            nextPane.setVisible(true);
            nextPane.clipProperty().unbind();
            nextPane.setClip(null);
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
            pane.clipProperty().unbind();
            pane.setClip(null);
        }
    }


}
