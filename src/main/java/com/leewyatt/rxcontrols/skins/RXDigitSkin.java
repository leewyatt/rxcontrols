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
package com.leewyatt.rxcontrols.skins;

import com.leewyatt.rxcontrols.controls.RXDigit;
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.shape.Polygon;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 数字组件的皮肤
 */
public class RXDigitSkin extends SkinBase<RXDigit> {
    private RXDigit control;
    /**
     * 0,1,2....9 数字的数组
     * ***
     * *-*  这就代表0
     * ***
     */
    private static final boolean[][] DIGIT_BOOLEAN = new boolean[][]{
            new boolean[]{true, false, true, true, true, true, true},
            new boolean[]{false, false, false, false, true, false, true},
            new boolean[]{true, true, true, false, true, true, false},
            new boolean[]{true, true, true, false, true, false, true},
            new boolean[]{false, true, false, true, true, false, true},
            new boolean[]{true, true, true, true, false, false, true},
            new boolean[]{true, true, true, true, false, true, true},
            new boolean[]{true, false, false, false, true, false, true},
            new boolean[]{true, true, true, true, true, true, true},
            new boolean[]{true, true, true, true, true, false, true}};
    private boolean flag;
    private Polygon[] polygons;
    private Group digitShape = new Group();
    /**
     * 尺寸改变监听器
     */
    private InvalidationListener sizeListener = ob -> flag = true;
    /**
     * 其他改变监听器
     */
    private InvalidationListener otherListener = ob -> {
        if (digitShape != null) {
            updateShape(control.getWidth(), control.getHeight());
            getSkinnable().requestLayout();
        }
    };


    public RXDigitSkin(RXDigit control) {
        super(control);
        this.control = control;
        digitShape.getStyleClass().add("digit-shape");
        getChildren().add(digitShape);
        updateShape(control.getWidth(), control.getHeight());
        getSkinnable().requestLayout();

        /**
         * 尺寸改变监听
         */
        control.widthProperty().addListener(sizeListener);
        control.heightProperty().addListener(sizeListener);
        /**
         * 颜色改变监听
         */
        control.lightFillProperty().addListener(otherListener);
        control.darkFillProperty().addListener(otherListener);
        /**
         * 数字改变监听
         */
        control.digitProperty().addListener(otherListener);


    }

    private void updateShape(double w, double h) {
        //根据宽高计算图形
        polygons = new Polygon[]{
                new Polygon(w * 0.037037, h * 0, w * 0.962963, h * 0, w * 0.777778, h * 0.092593, w * 0.222222,
                        h * 0.092593),
                new Polygon(w * 0.222222, h * 0.453704, w * 0.777778, h * 0.453704, w * 0.962963, h * 0.5, w * 0.777778,
                        h * 0.546296, w * 0.222222, h * 0.546296, w * 0.037037, h * 0.5),
                new Polygon(w * 0.222222, h * 0.907407, w * 0.777778, h * 0.907407, w * 0.962963, h * 1, w * 0.037037,
                        h * 1),
                new Polygon(w * 0, h * 0.018519, w * 0.185185, h * 0.111111, w * 0.185185, h * 0.435185, w * 0,
                        h * 0.481481),
                new Polygon(w * 0.814815, h * 0.111111, w * 1, h * 0.018519, w * 1, h * 0.481481, w * 0.814815,
                        h * 0.435185),
                new Polygon(w * 0, h * 0.518519, w * 0.185185, h * 0.564815, w * 0.185185, h * 0.888889, w * 0,
                        h * 0.981481),
                new Polygon(w * 0.814815, h * 0.564815, w * 1, h * 0.518519, w * 1, h * 0.981481, w * 0.814815,
                        h * 0.888889)};
        //注意这里是setAll ,不是addAll
        digitShape.getChildren().setAll(polygons);
        for (int i = 0; i < polygons.length; i++) {
            Polygon temp = polygons[i];
            temp.setSmooth(true);
            temp.setFill(DIGIT_BOOLEAN[getSkinnable().getDigit()][i]
                    ? getSkinnable().getLightFill() : getSkinnable().getDarkFill());
        }

    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        if (flag) {
            updateShape(w, h);
            flag = false;
        }
        layoutInArea(digitShape, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return rightInset + leftInset + 50;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
                                       double leftInset) {
        return topInset + bottomInset + 100;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {

        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    public void dispose() {
        getSkinnable().lightFillProperty().removeListener(otherListener);
        getSkinnable().darkFillProperty().removeListener(otherListener);
        getSkinnable().widthProperty().removeListener(sizeListener);
        getSkinnable().heightProperty().removeListener(sizeListener);
        getSkinnable().digitProperty().removeListener(otherListener);
        polygons = null;
        getChildren().clear();//清空
        super.dispose();
    }
}
