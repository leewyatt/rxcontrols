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
 *
 * 轮播图效果: 盒子水平方向的翻动
 * 主要通过调整PerspectiveTransform的四个顶点的值来模拟3D效果
 */
public class AnimHorBox extends CarouselAnimationBase {
    private Timeline animation;
    private PerspectiveTransform pt1;
    private PerspectiveTransform pt2;

    public AnimHorBox() {
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
        double offset = Math.sqrt(paneWidth * paneWidth / 2);
        nextPane.setTranslateX(direction ? paneWidth : -paneWidth);
        //-----------创建keyValue------------
        KeyValue kv1 = new KeyValue(currentPane.translateXProperty(), direction ? -paneWidth : paneWidth);
        KeyValue kv2 = new KeyValue(nextPane.translateXProperty(), 0);
        KeyValue kv3, kv4, kv5, kv6, kv7, kv8, kv9, kv10;
        if (direction) {
            // 源的左上角x映射到该坐标
            pt1.setUlx(0.0);
            // 源的左上角y映射到该坐标
            pt1.setUly(0.0);
            // 源右上角x坐标映射到该坐标
            pt1.setUrx(0);
            // 源右上角y坐标映射到该坐标
            pt1.setUry(-offset);
            // 源的右下角x映射到该坐标。
            pt1.setLrx(0);
            // 源的右下角y映射到该坐标。
            pt1.setLry(nextPane.heightProperty().get() - offset);
            // 源的左下角x映射到该坐标。
            pt1.setLlx(0.0);
            // 源的左下角y映射到该坐标。
            pt1.setLly(nextPane.heightProperty().get());
            nextPane.setEffect(pt1);

            kv3 = new KeyValue(pt1.lryProperty(), nextPane.heightProperty().get());
            kv4 = new KeyValue(pt1.uryProperty(), 0);
            kv5 = new KeyValue(pt1.urxProperty(), nextPane.widthProperty().get());
            kv6 = new KeyValue(pt1.lrxProperty(), nextPane.widthProperty().get());

            // 源的左上角x映射到该坐标
            pt2.setUlx(0.0);
            // 源的左上角y映射到该坐标
            pt2.setUly(0.0);
            // 源右上角x坐标映射到该坐标
            pt2.setUrx(currentPane.widthProperty().get());
            // 源右上角y坐标映射到该坐标
            pt2.setUry(0);
            // 源的右下角x映射到该坐标。
            pt2.setLrx(currentPane.widthProperty().get());
            // 源的右下角y映射到该坐标。
            pt2.setLry(currentPane.heightProperty().get());
            // 源的左下角x映射到该坐标。
            pt2.setLlx(0.0);
            // 源的左下角y映射到该坐标。
            pt2.setLly(currentPane.heightProperty().get());
            currentPane.setEffect(pt2);
            kv7 = new KeyValue(pt2.llyProperty(), currentPane.heightProperty().get() - offset);
            kv8 = new KeyValue(pt2.ulyProperty(), -offset);
            kv9 = new KeyValue(pt2.ulxProperty(), currentPane.widthProperty().get());
            kv10 = new KeyValue(pt2.llxProperty(), currentPane.widthProperty().get());
        } else {
            // 源左上角x坐标映射到该坐标
            pt1.setUlx(paneWidth);
            // 源左上角y坐标映射到该坐标
            pt1.setUly(-offset);
            // 源的右上角x映射到该坐标
            pt1.setUrx(paneWidth);
            // 源的右上角y映射到该坐标
            pt1.setUry(0.0);
            // 源的右下角x映射到该坐标。
            pt1.setLlx(paneWidth);
            // 源的右下角y映射到该坐标。
            pt1.setLly(paneHeight - offset);
            // 源的左下角x映射到该坐标。
            pt1.setLrx(paneWidth);
            // 源的左下角y映射到该坐标。
            pt1.setLry(paneHeight);
            nextPane.setEffect(pt1);
            kv3 = new KeyValue(pt1.ulxProperty(), 0);
            kv4 = new KeyValue(pt1.ulyProperty(), 0);
            kv5 = new KeyValue(pt1.llxProperty(), 0);
            kv6 = new KeyValue(pt1.llyProperty(), paneHeight);
            // 源的左上角x映射到该坐标
            pt2.setUlx(0.0);
            // 源的左上角y映射到该坐标
            pt2.setUly(0.0);
            // 源右上角x坐标映射到该坐标
            pt2.setUrx(paneWidth);
            // 源右上角y坐标映射到该坐标
            pt2.setUry(0);
            // 源的右下角x映射到该坐标。
            pt2.setLrx(paneWidth);
            // 源的右下角y映射到该坐标。
            pt2.setLry(paneHeight);
            // 源的左下角x映射到该坐标。
            pt2.setLlx(0.0);
            // 源的左下角y映射到该坐标。
            pt2.setLly(paneHeight);
            currentPane.setEffect(pt2);
            kv7 = new KeyValue(pt2.uryProperty(), -offset);
            kv8 = new KeyValue(pt2.lryProperty(), paneHeight - offset);
            kv9 = new KeyValue(pt2.urxProperty(), 0);
            kv10 = new KeyValue(pt2.lrxProperty(), 0);
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        }),
                new KeyFrame(animationTime, kv1, kv2, kv3, kv4, kv5, kv6, kv7, kv8, kv9, kv10));
        animation.setOnFinished(event -> {
            nextPane.setEffect(null);
            nextPane.setTranslateX(0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setTranslateX(0);
            currentPane.setEffect(null);
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
            pane.setTranslateX(0);
        }
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
