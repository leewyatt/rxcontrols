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
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * <p>
 * 轮播图效果: 仿3D效果实现左 [中] 右 的轮播
 * 虽然是3D的SubScene,但是主要使用的是 TranslateTransition 的x轴实现左右位移+缩放动画 ;
 * 而不是使用TranslateTransition 的Z轴; 因为使用Z轴实现缩放动画,虽然简单,但是不方便控制两侧组件的位置
 */

public class AnimAround extends CarouselAnimationBase {

    /**
     * true 代表动画前和动画后都处于缩小状态;
     * false 代表播放完动画,恢复大小;
     * 用于控制 scaleLast 动画是否播放
     */
    private final boolean zoomOut;
    /**
     * 如果zoomOut=false 那么播放完毕会有一个放大的动画,
     * 用于控制放大动画的时间占全部动画时间的百分比
     * min 0.0
     * max 0.6
     * default 0.2
     */
    private double zoomInTimeScale = 0.2;

    /**
     * 中间页面的缩放比例
     */
    private final double middleScale;
    /**
     * 两侧页面的缩放比例
     */
    private final double sideScale;
    /**
     * 暂停动画;用于触发Pane 的 closing ,opening
     */
    private final PauseTransition startAnim;

    /**
     * 主动画播放完毕,最后的放大铺满动画
     */
    private final ScaleTransition scaleLast;

    /**
     * 中间页面(即将移动到旁边时)变小
     */
    private final ScaleTransition scaleMiddleToSmaller;

    /**
     * 旁边的页面(即将显示到中间的页面)变大
     */
    private final ScaleTransition scaleSideToBigger;
    /**
     * 保持缩放比例
     */
    private final ScaleTransition scaleSideToCenterBack;
    /**
     * 保持缩放比例
     */
    private final ScaleTransition scaleCenterBackToSide;
    /**
     * 旁边移动到中间
     */
    private final TranslateTransition moveSideToMiddle;
    /**
     * 中间移动到旁边
     */
    private final TranslateTransition moveMiddleToSide;
    /**
     * 旁边移动到中间的中后部
     * 下面这两个动画的移动的组件可能不是同一个(因为用户点击导航不连续的切换时,比如 当前显示  5 [6] 7  此时用户跳转页面1 ,那么两次移动就不是同一个页面)
     * moveSideToCenterBack
     * moveCenterBackToSide
     */
    private final TranslateTransition moveSideToCenterBack;
    /**
     * 中后部移动到另一边
     */
    private final TranslateTransition moveCenterBackToSide;
    /**
     * 把上面除开scaleLast 以外的动画,全部关联在一起
     */
    private final ParallelTransition aroundAnim;
    /**
     * 如果设置了zoomOut=true.那么 animation = aroundAnim + scaleLast
     */
    private SequentialTransition animation;
    /**
     * 此动画需要的最少页面数量
     */
    private static final int LIMIT_SIZE = 3;

    public AnimAround() {
        this(false);
    }

    public AnimAround(boolean zoomOut) {
        this(0.7, 0.5, zoomOut);
    }

    public AnimAround(double middleScale, double sideScale, boolean zoomOut) {
        if (Double.compare(middleScale, sideScale) < 1 || middleScale >= 1 || sideScale >= 1) {
            throw new IllegalArgumentException("middle scale must be greater than side scale; middle scale must be less than 1;Side scale  must be less than 1");
        }
        this.zoomOut = zoomOut;
        this.middleScale = middleScale;
        this.sideScale = sideScale;
        //---动画初始化-----
        startAnim = new PauseTransition(Duration.ZERO);
        scaleLast = new ScaleTransition();
        scaleMiddleToSmaller = new ScaleTransition();
        scaleSideToBigger = new ScaleTransition();
        moveSideToMiddle = new TranslateTransition();
        moveMiddleToSide = new TranslateTransition();

        moveSideToCenterBack = new TranslateTransition();
        moveCenterBackToSide = new TranslateTransition();
        moveSideToCenterBack.setInterpolator(Interpolator.LINEAR);
        moveCenterBackToSide.setInterpolator(Interpolator.LINEAR);
        // 从一侧移动到另外一侧; 由moveSideToCenterBack 和 moveCenterBackToSide 两个动画组成
        SequentialTransition moveSideToSide = new SequentialTransition(moveSideToCenterBack, moveCenterBackToSide);
        moveSideToSide.setInterpolator(Interpolator.EASE_BOTH);

        aroundAnim = new ParallelTransition();
        animation = new SequentialTransition();
        scaleSideToCenterBack = new ScaleTransition();
        scaleCenterBackToSide = new ScaleTransition();
        aroundAnim.getChildren().setAll(moveSideToMiddle, scaleSideToBigger, moveMiddleToSide, scaleMiddleToSmaller, moveSideToSide, scaleSideToCenterBack, scaleCenterBackToSide);

    }

    public double getZoomInTimeScale() {
        return zoomInTimeScale;
    }

    public void setZoomInTimeScale(double zoomInTimeScale) {
        this.zoomInTimeScale = CarouselAnimUtil.clamp(0.0, 0.6, zoomInTimeScale);
    }

    private void checkPaneListSize(List<RXCarouselPane> panes) {
        //页面数量必须大于或等于3
        if (panes.size() < LIMIT_SIZE) {
            throw new IllegalArgumentException("Use Around effect the RXCarouselPane list size must be greater than or equal to 3");
        }
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        checkPaneListSize(panes);
        for (int i = 0; i < panes.size(); i++) {
            Pane pane = panes.get(i);
            if (zoomOut) {
                if (i == currentIndex) {
                    setScale(middleScale, pane);
                } else {
                    setScale(sideScale, pane);
                }
            } else {
                setScale(1.0, pane);
            }
        }

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        int sideToCenterBackIndex;
        int centerBackToSideIndex;
        int maxIndex = panes.size() - 1;
        if (direction) {
            sideToCenterBackIndex = (currentIndex == 0 ? maxIndex : currentIndex - 1);
            centerBackToSideIndex = (nextIndex == maxIndex ? 0 : nextIndex + 1);
            if (currentIndex == 0 && nextIndex == maxIndex) {
                sideToCenterBackIndex = maxIndex - 1;
                centerBackToSideIndex = sideToCenterBackIndex;
            }
        } else {
            sideToCenterBackIndex = currentIndex == maxIndex ? 0 : currentIndex + 1;
            centerBackToSideIndex = nextIndex == 0 ? maxIndex : nextIndex - 1;
            if (nextIndex == 0 && currentIndex == maxIndex) {
                sideToCenterBackIndex = 1;
                centerBackToSideIndex = sideToCenterBackIndex;
            }
        }
        initAroundAnim(direction, currentIndex, nextIndex, sideToCenterBackIndex, centerBackToSideIndex, panes, animationTime, paneWidth);
        return animation;
    }

    private void initAroundAnim(boolean direction, int currentIndex, int nextIndex, int sideToCenterBackIndex, int centerBackToSideIndex, List<RXCarouselPane> panes, Duration animationTime, double paneWidth) {
        RXCarouselPane sideToCenterBackPane = panes.get(sideToCenterBackIndex);
        sideToCenterBackPane.toBack();
        RXCarouselPane middlePane = panes.get(currentIndex);
        middlePane.toFront();
        RXCarouselPane sideToMiddlePane = panes.get(nextIndex);
        //1. 初始化页面的位置.处理页面缩放,并计算两侧页面距离两边的偏移量
        initPanes(panes, currentIndex, nextIndex, sideToCenterBackIndex, centerBackToSideIndex);
        //2. 基于上面的缩放,才能计算缩小后的宽度, 然后才能计算偏移量
        double offsetX = (paneWidth - sideToCenterBackPane.getBoundsInParent().getWidth()) / 2;

        RXCarouselPane centerBackToSidePane;
        if (panes.size() == LIMIT_SIZE || sideToCenterBackIndex == centerBackToSideIndex) {
            centerBackToSidePane = null;
        } else {
            centerBackToSidePane = panes.get(centerBackToSideIndex);
        }
        settingAroundAnim(animationTime, direction, offsetX, sideToMiddlePane, middlePane, sideToCenterBackPane, centerBackToSidePane);

    }

    /**
     * 设置位移动画
     *
     * @param tt       位移动画
     * @param duration 动画用时
     * @param node     动画节点
     * @param fromX    初始位置
     * @param toX      结束位置
     */
    private void setMoveAnim(TranslateTransition tt, Duration duration, Node node, double fromX, double toX) {
        tt.setDuration(duration);
        tt.setNode(node);
        tt.setFromX(fromX);
        tt.setToX(toX);
    }

    /**
     * 设置缩放动画
     *
     * @param st        缩放动画
     * @param duration  动画用时
     * @param node      动画节点
     * @param formScale 开始的缩放比例
     * @param toScale   结束的缩放比例
     */
    private void setScaleAnim(ScaleTransition st, Duration duration, Node node, double formScale, double toScale) {
        st.setDuration(duration);
        st.setNode(node);
        st.setFromX(formScale);
        st.setFromY(formScale);
        st.setToX(toScale);
        st.setToY(toScale);
    }

    /**
     * 等比例缩放
     *
     * @param nodes 节点
     * @param scale 缩放比例
     */
    private void setScale(double scale, Node... nodes) {
        for (Node node : nodes) {
            if (node != null) {
                node.setScaleX(scale);
                node.setScaleY(scale);
            }
        }
    }

    private void settingAroundAnim(Duration animationTime, boolean direction, double offsetX, RXCarouselPane sideToMiddlePane, RXCarouselPane middlePane, RXCarouselPane sideToCenterBackPane, RXCarouselPane centerBackToSidePane) {
        //计算主动画用时
        Duration mainAnimTime = animationTime.multiply(zoomOut ? 1 : 1 - zoomInTimeScale);
        Duration halfMainTime = mainAnimTime.divide(2.0);
        //----------------运动距离较短的页面动画--------------------------
        //组件向中间移动
        setMoveAnim(moveSideToMiddle, mainAnimTime, sideToMiddlePane, direction ? offsetX : -offsetX, 0);
        //组件放大
        setScaleAnim(scaleSideToBigger, mainAnimTime, sideToMiddlePane, sideScale, middleScale);
        //----------------中间的页面动画-------------------------------------
        //中间组件向一侧
        setMoveAnim(moveMiddleToSide, mainAnimTime, middlePane, 0, direction ? -offsetX : offsetX);
        //中间组件变小
        setScaleAnim(scaleMiddleToSmaller, mainAnimTime, middlePane, middleScale, sideScale);
        //------------运动距离比较长的页面动画-------------
        //第一段动画,从side->center back
        setMoveAnim(moveSideToCenterBack, halfMainTime, sideToCenterBackPane, direction ? -offsetX : offsetX, 0);
        // 保持比例
        setScaleAnim(scaleSideToCenterBack, halfMainTime, sideToCenterBackPane, sideScale, sideScale);
        //第二段动画,从 center back->side
        setMoveAnim(moveCenterBackToSide, halfMainTime, centerBackToSidePane == null ? sideToCenterBackPane : centerBackToSidePane, 0, direction ? offsetX : -offsetX);
        if (centerBackToSidePane != null) {
            setScaleAnim(scaleCenterBackToSide, halfMainTime, sideToCenterBackPane, sideScale, sideScale);
        }
        //主动画运行到一半的时间, 中间的页面靠前显示,后侧的页面显示/隐藏
        moveSideToCenterBack.setOnFinished(event -> {
            sideToMiddlePane.toFront();
            if (centerBackToSidePane != null) {
                sideToCenterBackPane.setVisible(false);
                centerBackToSidePane.toBack();
                centerBackToSidePane.setVisible(true);
                setScale(sideScale, centerBackToSidePane);
            }
        });
        //最后的缩放动画
        setScaleAnim(scaleLast, animationTime.multiply(zoomInTimeScale), sideToMiddlePane, middleScale, 1.0);
        //开始动画,触发opening closing
        startAnim.setOnFinished(event -> {
            sideToMiddlePane.fireOpening();
            middlePane.fireClosing();
        });

        animation.getChildren().setAll(startAnim, aroundAnim);
        if (!zoomOut) {
            animation.getChildren().add(scaleLast);
        }
        animation.setOnFinished(event -> {
            sideToMiddlePane.fireOpened();
            middlePane.fireClosed();
            setScale(sideScale, sideToCenterBackPane, centerBackToSidePane, middlePane);
            if (zoomOut) {
                setScale(middleScale, sideToMiddlePane);
            } else {
                setScale(1.0, sideToMiddlePane);
            }
        });
    }

    private void initPanes(List<RXCarouselPane> panes, int currentIndex, int nextIndex, int sideToCenterBackIndex, int centerBackToSideIndex) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            if (i == currentIndex || i == nextIndex || i == sideToCenterBackIndex) {
                pane.setVisible(true);
                if (i == currentIndex) {
                    setScale(middleScale, pane);
                } else {
                    setScale(sideScale, pane);
                }
            } else if (i == centerBackToSideIndex) {
                //i == centerBackToSideIndex && centerBackToSideIndex != sideToCenterBackIndex
                pane.setVisible(false);
                setScale(sideScale, pane);
            } else {
                pane.setVisible(false);
            }
        }
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    /**
     * 当设置为该轮播图时,还没有执行动画前,先根据zoomOut来决定是否缩放
     *
     * @param contentPane  内容面板 可以用于计算组件的宽
     * @param effectPane   特效面板, 此动画不需要
     * @param panes        页面
     * @param currentIndex 当前索引
     * @param nextIndex    下页索引
     */
    @Override
    public void initAnimation(StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex) {
        checkPaneListSize(panes);
        double paneWidth = CarouselAnimUtil.getPaneWidth(contentPane);
        if (nextIndex < 0) {
            nextIndex = 0;
        }
        int leftIndex = nextIndex == 0 ? panes.size() - 1 : nextIndex - 1;
        int rightIndex = nextIndex == panes.size() - 1 ? 0 : nextIndex + 1;
        // 左侧的组件
        RXCarouselPane leftPane = panes.get(leftIndex);
        setScale(sideScale, leftPane);
        leftPane.setVisible(true);
        //中间的组件
        RXCarouselPane middlePane = panes.get(nextIndex);
        if (zoomOut) {
            setScale(middleScale, middlePane);
        } else {
            setScale(1.0, middlePane);
        }
        middlePane.setVisible(true);
        //中间的组件最前显示
        middlePane.toFront();
        //右侧的组件
        RXCarouselPane rightPane = panes.get(rightIndex);
        setScale(sideScale, rightPane);
        rightPane.setVisible(true);

        rightPane.toBack();//右侧的组件靠后
        // 基于上面的缩放,才能计算缩小后的宽度, 然后计算偏移量
        double offsetX = (paneWidth - leftPane.getBoundsInParent().getWidth()) / 2;
        leftPane.setTranslateX(-offsetX);
        rightPane.setTranslateX(offsetX);

    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setScaleX(1.0);
            pane.setScaleY(1.0);
            pane.setTranslateX(0);
        }
    }

    @Override
    public void dispose() {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            animation.stop();
        }
        animation.getChildren().clear();
        animation = null;
    }
}
