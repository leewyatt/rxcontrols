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

import javafx.animation.Animation;
import javafx.scene.shape.Rectangle;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public class FillAnimationUtil {
    /**
     * 设置矩形大小
     */
    public static void setSize(Double width, Double height, Rectangle... rects) {
        for (Rectangle rect : rects) {
            if (width != null) {
                rect.setWidth(width);
            }
            if (height != null) {
                rect.setHeight(height);
            }
        }
    }

    /**
     * 停止动画
     *
     * @param animations 需要停止的动画
     */
    public static void stopAnimation(Animation... animations) {
        for (Animation animation : animations) {
            if (animation != null && animation.getStatus() != Animation.Status.STOPPED) {
                animation.stop();
            }
        }
    }


    /**
     * 解除宽高位移绑定,并且设置为null,
     */
    public static void disposeRectangle(Rectangle... rects) {
        for (Rectangle rect : rects) {
            if (rect != null) {
                if(rect.translateYProperty().isBound()){
                    rect.translateYProperty().unbind();
                }
                if (rect.translateXProperty().isBound()) {
                    rect.translateXProperty().unbind();
                }
                if (rect.heightProperty().isBound()) {
                    rect.heightProperty().unbind();
                }
                if (rect.widthProperty().isBound()) {
                    rect.widthProperty().unbind();
                }
                rect = null;
            }
        }

    }

}
