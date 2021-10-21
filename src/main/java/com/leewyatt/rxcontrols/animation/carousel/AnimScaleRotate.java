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
import javafx.geometry.Point3D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * 轮播图效果: 缩放淡出旋转
 */
public class AnimScaleRotate extends CarouselAnimationBase {
    /**
     * 缩放倍数
     */
    private double scale;
    private double rotate;
    private Point3D rotationAxis;
    private final Timeline animation;

    public AnimScaleRotate() {
        this(10);
    }
    public AnimScaleRotate(double scale) {
        this(scale,0,Rotate.X_AXIS);
    }

    public AnimScaleRotate(double rotate,Point3D rotationAxis) {
        this(10,rotate,rotationAxis);
    }

    public AnimScaleRotate(double scale,double rotate,Point3D rotationAxis) {
        this.scale = scale;
        this.rotate =rotate;
        this.rotationAxis=rotationAxis;
        animation = new Timeline();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, currentIndex, nextIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        currentPane.toFront();
        currentPane.setRotationAxis(rotationAxis);
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        }),
                new KeyFrame(animationTime,
                        new KeyValue(currentPane.scaleXProperty(), getScale()),
                        new KeyValue(currentPane.scaleYProperty(), getScale()),
                        new KeyValue(currentPane.rotateProperty(), rotate),
                        new KeyValue(currentPane.opacityProperty(), 0.0)));
        animation.setOnFinished(event -> {
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setScaleX(1.0);
            currentPane.setScaleY(1.0);
            currentPane.setOpacity(1.0);
            currentPane.setRotate(0);
            currentPane.fireClosed();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getRotate() {
        return rotate;
    }

    public void setRotate(double rotate) {
        this.rotate = rotate;
    }

    public Point3D getRotationAxis() {
        return rotationAxis;
    }

    public void setRotationAxis(Point3D rotationAxis) {
        this.rotationAxis = rotationAxis;
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setScaleX(1.0);
            pane.setScaleY(1.0);
            pane.setOpacity(1.0);
            pane.setRotate(0);
        }
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
