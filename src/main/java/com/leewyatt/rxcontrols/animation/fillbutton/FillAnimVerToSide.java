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
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public class FillAnimVerToSide implements FillAnimation {
    private Rectangle rectClip;

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

        rectClip = new Rectangle();
        rectClip.widthProperty().bind(region.widthProperty());
        rectClip.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> (region.getHeight() - rectClip.getHeight()) / 2.0,
                rectClip.heightProperty(), region.heightProperty()));
        region.setClip(rectClip);

        initEnterAnim();
        initExitAnim();
    }

    @Override
    public void initEnterAnim() {
        if (control.isHover()) {
            if (animEnter.getStatus() == Animation.Status.RUNNING) {
                animEnter.stop();
            }
            rectClip.setHeight(region.getHeight());
            label.setTextFill(control.getHoverTextFill());
        }

        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.heightProperty(), region.getHeight()),
                        new KeyValue(label.textFillProperty(), control.getHoverTextFill()))

        );
    }

    @Override
    public void initExitAnim() {
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }
            rectClip.setHeight(0);
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.heightProperty(), 0),
                        new KeyValue(label.textFillProperty(), control.getTextFill()))
        );
    }

    @Override
    public void dispose() {
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        region.setClip(null);
        FillAnimationUtil.disposeRectangle(rectClip);
        System.gc();
    }
}
