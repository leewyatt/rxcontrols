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
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图效果: 软纸张翻页
 *
 */
public class AnimPageTurningSoft extends CarouselAnimationBase {
    /**
     * 影子开始的时候,从小到大
     */
    private final ScaleTransition shadowStartAnim;
    /**
     * 影子左右移动
     */
    private TranslateTransition shadowMoveAnim;
    /**
     * 影子结束时透明度调整动画
     */
    private final FadeTransition shadowEndAnim;
    private final TranslateTransition currentClipAnim;
    private final TranslateTransition currentBackAnim;
    private final TranslateTransition backClipAnim;
    /**
     * 暂停0毫秒的动画,主要是为了触发closing,opening
     */
    private final PauseTransition pauseAnim;
    private ParallelTransition animation;
    private final Rectangle shadow;
    private final double shadowWidth;
    /**
     * 页面背面的图片
     */
    private final ImageView backImage;
    /**
     * 翻页的页面的背面
     */
    private final Group backGroup;

    public AnimPageTurningSoft() {
        this(50);
    }

    public AnimPageTurningSoft(double shadowWidth) {
        this.shadowWidth = shadowWidth;
        currentClipAnim = new TranslateTransition();
        currentBackAnim = new TranslateTransition();
        backClipAnim = new TranslateTransition();
        shadowStartAnim = new ScaleTransition();
        shadowStartAnim.setFromX(0.0);
        shadowStartAnim.setToX(1.0);
        shadowMoveAnim = new TranslateTransition();
        shadowEndAnim = new FadeTransition();
        shadowEndAnim.setFromValue(1.0);
        shadowEndAnim.setToValue(0.0);
        shadow = new Rectangle();
        shadow.setY(0);
        shadow.setWidth(shadowWidth);
        shadow.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
                new Stop(0.5, Color.rgb(0, 0, 0, 0.6)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.0))));
        backImage = new ImageView();
        animation = new ParallelTransition();
        backGroup = new Group();
        pauseAnim = new PauseTransition(Duration.ZERO);
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, effectPane, nextIndex, currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        currentPane.toFront();
        RXCarouselPane nextPane = panes.get(nextIndex);
        double width = CarouselAnimUtil.getPaneWidth(currentPane);
        double height = CarouselAnimUtil.getPaneHeight(currentPane);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        // 影子
        shadow.setX(width - shadowWidth / 2);
        shadow.setHeight(height);
        // 影子大小的动画
        shadowStartAnim.setDuration(animationTime.divide(5.0));
        shadowStartAnim.setNode(shadow);
        // 影子左右移动的动画
        shadowMoveAnim = new TranslateTransition(animationTime, shadow);
        shadowMoveAnim.setFromX(direction ? 0 : -width / 2);
        shadowMoveAnim.setToX(direction ? -width / 2 : 0);
        // 结束时,影子的消失动画
        shadowEndAnim.setDuration(animationTime.divide(5.0));
        shadowEndAnim.setNode(shadow);
        //设置 影子消失的延迟时间
        shadowEndAnim.setDelay(animationTime.multiply(4.0 / 5.0));
        // 当前页面裁切
        Rectangle currentClip = new Rectangle(0, 0, width, height);
        currentPane.setClip(currentClip);
        // 当前页面裁切动画
        currentClipAnim.setDuration(animationTime);
        currentClipAnim.setNode(currentClip);
        currentClipAnim.setFromX(0);
        currentClipAnim.setToX(direction ? -width : width);
        // 当前页面背面(下一页的快照)
        backImage.setImage(CarouselAnimUtil.nodeSnapshot(direction ? nextPane : currentPane));
        backGroup.getChildren().setAll(backImage);
        // 背面的裁切
        Rectangle backClip = new Rectangle(0, 0, width, height);
        backGroup.setClip(backClip);
        // 背面的移动动画
        currentBackAnim.setDuration(animationTime);
        currentBackAnim.setNode(backGroup);
        currentBackAnim.setFromX(direction ? width : 0);
        currentBackAnim.setToX(direction ? 0 : width);
        // 背面裁切矩形的动画
        backClipAnim.setDuration(animationTime);
        backClipAnim.setNode(backClip);
        backClipAnim.setFromX(direction ? -width : -width / 2);
        backClipAnim.setToX(direction ? -width / 2 : -width);
        //shadow要放在最上面;
        effectPane.getChildren().addAll(backGroup, shadow);
        pauseAnim.setOnFinished(event -> {
           nextPane.fireOpening();
            currentPane.fireClosing();
        });
        animation.getChildren().setAll(
                pauseAnim,
                currentClipAnim,
                currentBackAnim,
                backClipAnim,
                shadowStartAnim,
                shadowMoveAnim,
                shadowEndAnim
        );

        animation.setOnFinished(event -> {
            effectPane.getChildren().clear();
            nextPane.setTranslateX(0);
            nextPane.setClip(null);
            nextPane.setVisible(true);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setClip(null);
            currentPane.setTranslateX(0);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, effectPane, nextIndex);
    }

    private void reset(List<RXCarouselPane> panes, Pane effectPane, int... showIndexAry) {
        effectPane.getChildren().clear();
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setClip(null);
            pane.setTranslateX(0);
        }
    }

    @Override
    public void dispose() {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
        animation=null;
    }
}
