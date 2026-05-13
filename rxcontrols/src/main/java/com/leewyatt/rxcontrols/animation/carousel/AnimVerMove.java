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
import javafx.util.Pair;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 垂直运动
 */
public class AnimVerMove extends CarouselAnimationBase {
    /**
     * true 跳过两页中间的页面
     * false 不跳过
     */
    private boolean skipMiddlePane;
    private Timeline animation;

    public AnimVerMove() {
        this(false);
    }

    public AnimVerMove(boolean skipMiddlePane) {
        this.skipMiddlePane = skipMiddlePane;
        animation = new Timeline();
    }


    public boolean isSkipMiddlePane() {
        return skipMiddlePane;
    }

    public void setSkipMiddlePane(boolean skipMiddlePane) {
        this.skipMiddlePane = skipMiddlePane;
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        //注意direction 代表的是正序(第1页->第2页) 还是倒序(第2页->第1页);
        //isLoop 如果是上一页 或者 下一页的 点击触发的, 那么isLoop为true
        Pair<List<RXCarouselPane>, Boolean> pair = CarouselAnimUtil.computeSubListAndDirection(panes, currentIndex, nextIndex, foreAndAftJump,isSkipMiddlePane());
        List<RXCarouselPane> subList = pair.getKey();
        boolean direction = pair.getValue();
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);
        for (RXCarouselPane pane : panes) {
            if (!subList.contains(pane)) {
                pane.setVisible(false);
            } else {
                pane.setVisible(true);
            }
            pane.setTranslateY(0);
        }
        //进行偏移
        for (int i = 0; i < subList.size(); i++) {
            if (direction) {
                subList.get(i).setTranslateY(paneHeight * i);
            } else {
                subList.get(i).setTranslateY(-(subList.size() - i - 1) * paneHeight);
            }
        }

        RXCarouselPane nextPane = panes.get(nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);

        //构建动画
        KeyValue[] kvs = new KeyValue[subList.size()];
        for (int i = 0; i < subList.size(); i++) {
            if (direction) {
                kvs[i] = new KeyValue(subList.get(i).translateYProperty(), -(subList.size() - i - 1) * paneHeight);
            } else {
                kvs[i] = new KeyValue(subList.get(i).translateYProperty(), i * paneHeight);
            }
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        }),
                new KeyFrame(animationTime, kvs));

        //动画完毕后的处理
        animation.setOnFinished(e -> {
            nextPane.setTranslateY(0);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setTranslateY(0);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            pane.setTranslateY(0);
            if (i == nextIndex) {
                pane.setVisible(true);
            } else {
                pane.setVisible(false);
            }
        }
    }

    @Override
    public void dispose() {
         CarouselAnimUtil.disposeTimeline(animation);

    }
}
