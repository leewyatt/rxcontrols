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
package com.leewyatt.rxcontrols.animation.fillbutton;

import com.leewyatt.rxcontrols.controls.RXFillButton;
import com.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public class FillAnimVerZigzag implements FillAnimation {
    private Rectangle rectClip1;
    private Rectangle rectClip2;
    private Rectangle rectClip3;
    private Rectangle rectClip4;
    private SimpleDoubleProperty rectHeightPro;

    private RXFillButton control;
    private Region region;
    private Label label;
    private Timeline animEnter;
    private Timeline animExit;

    @Override
    public void init(RXFillButtonSkin skin) {
        control = skin.getSkinnable();
        region = skin.getFillRegion();
        label = skin.getLabel();
        animEnter = skin.getAnimEnter();
        animExit = skin.getAnimExit();

        rectHeightPro = new SimpleDoubleProperty(0);
        rectClip1 = new Rectangle();
        rectClip2 = new Rectangle();
        rectClip3 = new Rectangle();
        rectClip4 = new Rectangle();

        region.clipProperty().bind(Bindings.createObjectBinding(
                () -> {
                    double rectH = rectHeightPro.get();
                    double regionH = region.getHeight();
                    double regionW = region.getWidth();
                    FillAnimationUtil.setSize(regionW/4.0, rectH, rectClip1, rectClip2, rectClip3, rectClip4);
                    rectClip2.setTranslateX(regionW/4.0);
                    rectClip2.setTranslateY(region.getHeight() - rectHeightPro.get());
                    rectClip3.setTranslateX(regionW/2.0);
                    rectClip4.setTranslateX(regionW*(3.0 / 4.0));
                    rectClip4.setTranslateY(regionH - rectH);

                    return Shape.union(
                            Shape.union(rectClip1, rectClip2),
                            Shape.union(rectClip3, rectClip4));
                }, rectHeightPro, region.widthProperty(), region.heightProperty()));

        initEnterAnim();
        initExitAnim();
}

    @Override
    public void initEnterAnim() {
        //如果重置动画时,鼠标处于组件上方,那么先停止动画, 并且调整此矩形,以及文字颜色
        if (control.isHover()) {
            if (animEnter.getStatus() == Animation.Status.RUNNING) {
                animEnter.stop();
            }
            rectHeightPro.set(region.getHeight());
            label.setTextFill(control.getHoverTextFill());
        }
        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectHeightPro, region.getHeight()),
                        new KeyValue(label.textFillProperty(), control.getHoverTextFill()))

        );
    }

    @Override
    public void initExitAnim() {
//如果重置退出动画时,鼠标不在此处, 那么调整文本的颜色与矩形位置
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }
            rectHeightPro.set(0);
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectHeightPro, 0),
                        new KeyValue(label.textFillProperty(), control.getTextFill()))
        );

    }

    @Override
    public void dispose() {
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        region.clipProperty().unbind();
        region.setClip(null);
        FillAnimationUtil.disposeRectangle(rectClip1, rectClip2, rectClip3, rectClip4);
        rectHeightPro = null;
        System.gc();
    }
}
