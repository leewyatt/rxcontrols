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
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.List;

/**
 *
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * <p>
 * 轮播图效果: 硬纸板翻页效果
 *
 */
public class AnimPageTurningHard extends CarouselAnimationBase {
    /**
     * 影子
     */
    private final Rectangle shadow;
    /**
     * 影子宽度
     */
    private final double shadowWidth;
    /**
     * 影子淡入
     */
    private final FadeTransition shadowShow;
    /**
     * 影子淡出
     */
    private final FadeTransition shadowHide;
    /**
     * 翻转然后隐藏
     */
    private final RotateTransition hideAnimate;
    /**
     * 翻转然后显示
     */
    private final RotateTransition showAnimate;

    /**
     * 暂停动画: 耗时为零的动画,主要是触发 closing, opening 事件
     */
    private final PauseTransition pauseAnim;
    /**
     * 并行动画,影子淡入淡出+翻转页面
     */
    private ParallelTransition animation;

    /**
     * 页面截图
     */
    private WritableImage currentImage, nextImage;
    /**
     * 截图拆分成左右两部分
     */
    private ImageView clImageView, crImageView, nlImageView, nrImageView;

    public AnimPageTurningHard() {
        this(12);
    }

    public AnimPageTurningHard(double shadowWidth) {
        this.shadowWidth = shadowWidth;
        clImageView = new ImageView();
        crImageView = new ImageView();
        nlImageView = new ImageView();
        nrImageView = new ImageView();
        shadow = new Rectangle();
        shadow.setY(0);
        shadow.setWidth(shadowWidth);
        shadow.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
                new Stop(0.5, Color.rgb(0, 0, 0, 0.65)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.0))));
        shadowShow = new FadeTransition();
        shadowShow.setNode(shadow);
        shadowShow.setFromValue(0);
        shadowShow.setToValue(1.0);
        shadowHide = new FadeTransition();
        shadowHide.setNode(shadow);
        shadowHide.setFromValue(1.0);
        shadowHide.setToValue(0);
        //影子淡入淡出(顺序动画里加入一个0毫秒的暂停动画.然后启动closing,opening)
        pauseAnim = new PauseTransition(Duration.ZERO);
        SequentialTransition shadowAnim = new SequentialTransition(pauseAnim, shadowShow, shadowHide);
        hideAnimate = new RotateTransition();
        showAnimate = new RotateTransition();
        //翻页动画
        SequentialTransition pageTurningAnimation = new SequentialTransition(hideAnimate, showAnimate);
        animation = new ParallelTransition(shadowAnim, pageTurningAnimation);
    }

    @Override
    public void dispose() {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
        animation = null;
        disposeImageView();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, effectPane, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);

        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        double paneHeight = CarouselAnimUtil.getPaneHeight(contentPane);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        int width = (int) (paneWidth / 2);

        //当前页面的图像
        currentImage = CarouselAnimUtil.nodeSnapshot(currentPane);
        currentPane.setVisible(false);
        //当前页面左侧
        setLeftImageView(clImageView, currentImage, width, paneHeight, true);
        //当前页面右侧
        setRightImageView(crImageView, currentImage, width, paneHeight, true);

        //下一页的图像
        nextImage = CarouselAnimUtil.nodeSnapshot(nextPane);
        nextPane.setVisible(false);
        //下一页的左侧
        setLeftImageView(nlImageView, nextImage, width, paneHeight, !direction);
        //下一页的右侧
        setRightImageView(nrImageView, nextImage, width, paneHeight, direction);

        //中间的影子
        shadow.setX((paneWidth - shadowWidth) / 2);
        shadow.setHeight(paneHeight);
        //计算耗时
        Duration useTime = animationTime.divide(2);
        shadowShow.setDuration(useTime);
        shadowHide.setDuration(useTime);
        //根据翻页方向,来添加对应的组件
        if (direction) {
            effectPane.getChildren().addAll(clImageView, nrImageView, nlImageView, crImageView, shadow);
        } else {
            effectPane.getChildren().addAll(nlImageView, crImageView, nrImageView, clImageView, shadow);
        }

        //翻页动画: 第一步,隐藏当前的半页
        hideAnimate.setDuration(useTime);
        hideAnimate.setNode(direction ? crImageView : clImageView);
        hideAnimate.setAxis(Rotate.Y_AXIS);
        hideAnimate.setFromAngle(0);
        hideAnimate.setToAngle(direction ? 90 : -90);
        // 第一段动画播放完毕后,隐藏pane1,显示pane2
        hideAnimate.setOnFinished(e -> {
            if (direction) {
                crImageView.setVisible(false);
                nlImageView.setVisible(true);
            } else {
                clImageView.setVisible(false);
                nrImageView.setVisible(true);
            }
        });
        //翻页动画: 第二步: 显示下一页的半页
        showAnimate.setDuration(useTime);
        showAnimate.setNode(direction ? nlImageView : nrImageView);
        showAnimate.setAxis(Rotate.Y_AXIS);
        showAnimate.setFromAngle(direction ? -90 : 90);
        showAnimate.setToAngle(0);

        pauseAnim.setOnFinished(event -> {
            nextPane.fireOpening();
            currentPane.fireClosing();
        });

        animation.setOnFinished(event -> {
            effectPane.getChildren().clear();
            nextPane.setVisible(true);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.fireClosed();
            clImageView.setImage(null);
            crImageView.setImage(null);
            nlImageView.setImage(null);
            nrImageView.setImage(null);
            currentImage = null;
            nextImage = null;
        });
        return animation;
    }

    private void setLeftImageView(ImageView iv, WritableImage wi, double width, double height, boolean vis) {
        iv.setImage(wi);
        iv.setClip(new Rectangle(width, height));
        iv.setVisible(vis);
        iv.setRotate(0);
    }

    private void setRightImageView(ImageView iv, WritableImage wi, double width, double height, boolean vis) {
        iv.setImage(wi);
        Rectangle rect = new Rectangle(width, height);
        rect.setTranslateX(width);
        iv.setClip(rect);
        iv.setVisible(vis);
        iv.setRotate(0);
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, effectPane, nextIndex);
    }

    private void disposeImageView() {
        clImageView.setImage(null);
        clImageView = null;

        crImageView.setImage(null);
        crImageView = null;

        nlImageView.setImage(null);
        nlImageView = null;

        nrImageView.setImage(null);
        nrImageView = null;

        currentImage = null;
        nextImage = null;
    }

    private void reset(List<RXCarouselPane> panes, Pane effectPane, int... showIndexAry) {
        effectPane.getChildren().clear();
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
        }
    }
}
