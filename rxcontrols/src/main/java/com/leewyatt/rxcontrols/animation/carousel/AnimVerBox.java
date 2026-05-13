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
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * 轮播图效果: 盒子垂直方向的翻动
 * 主要通过调整PerspectiveTransform的四个顶点的值来模拟3D效果
 */ 
public class AnimVerBox extends CarouselAnimationBase {
    private final Timeline animation;
    private final PerspectiveTransform pt1 ;
    private final PerspectiveTransform pt2 ;

    public AnimVerBox() {
        animation = new Timeline();
        pt1 = new PerspectiveTransform();
        pt2 = new PerspectiveTransform();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        double offset = Math.sqrt(paneHeight * paneHeight / 2);
        nextPane.setTranslateY(direction ? paneHeight : -paneHeight);
        KeyValue kv1=null, kv2, kv3, kv4, kv5, kv6, kv7, kv8, kv9, kv10;
        kv2 =  new KeyValue(nextPane.translateYProperty(), 0);
        initPt1(paneWidth, paneHeight);
        currentPane.setEffect(pt1);
        if (direction) {
            //direction= true时,kv1有值; direction =false 时kv1 =null ,才能正常运行
            kv1 = new KeyValue(currentPane.translateYProperty(), -paneHeight);
            kv3 = new KeyValue(pt1.ulxProperty(), offset);
            kv4 = new KeyValue(pt1.ulyProperty(), paneHeight);
            kv5 = new KeyValue(pt1.urxProperty(), paneWidth + offset);
            kv6 = new KeyValue(pt1.uryProperty(), paneHeight);
            pt2.setUlx(0);
            pt2.setUly(0);
            pt2.setUrx(paneWidth);
            pt2.setUry(0);
            pt2.setLrx(paneWidth + offset);
            pt2.setLry(paneHeight - offset);
            pt2.setLlx(offset);
            pt2.setLly(paneHeight - offset);
            nextPane.setEffect(pt2);
            kv7 = new KeyValue(pt2.lrxProperty(), paneWidth);
            kv8 = new KeyValue(pt2.lryProperty(), paneHeight);
            kv9 = new KeyValue(pt2.llxProperty(), 0);
            kv10 = new KeyValue(pt2.llyProperty(), paneHeight);
        } else {
            kv3 = new KeyValue(pt1.ulyProperty(), paneHeight);
            kv4 = new KeyValue(pt1.uryProperty(), paneHeight);
            kv5 = new KeyValue(pt1.llxProperty(), offset);
            kv6 = new KeyValue(pt1.lrxProperty(), paneWidth + offset);
            pt2.setUlx(offset);
            pt2.setUly(offset);
            pt2.setUrx(paneWidth + offset);
            pt2.setUry(offset);
            pt2.setLrx(paneWidth);
            pt2.setLry(paneHeight);
            pt2.setLlx(0);
            pt2.setLly(paneHeight);
            nextPane.setEffect(pt2);
            kv7 = new KeyValue(pt2.ulxProperty(), 0);
            kv8 = new KeyValue(pt2.ulyProperty(), 0);
            kv9 = new KeyValue(pt2.urxProperty(), paneWidth);
            kv10 = new KeyValue(pt2.uryProperty(), 0);
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, event -> {
                   nextPane.fireOpening();
                    currentPane.fireClosing();
                }),
                new KeyFrame(animationTime, kv1, kv2, kv3, kv4, kv5, kv6, kv7, kv8, kv9, kv10));
        animation.setOnFinished(event -> {
            nextPane.setEffect(null);
            nextPane.setTranslateY(0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setTranslateY(0);
            currentPane.setEffect(null);
            currentPane.fireClosed();
        });
        return animation;
    }

    private void initPt1(double paneWidth, double paneHeight) {
        pt1.setUlx(0);
        pt1.setUly(0);
        pt1.setUrx(paneWidth);
        pt1.setUry(0);
        pt1.setLrx(paneWidth);
        pt1.setLry(paneHeight);
        pt1.setLlx(0);
        pt1.setLly(paneHeight);
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
            pane.setEffect(null);
            pane.setTranslateY(0);
        }
    }
}
