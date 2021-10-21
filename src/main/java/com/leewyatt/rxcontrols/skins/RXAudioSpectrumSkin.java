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

import com.leewyatt.rxcontrols.controls.RXAudioSpectrum;
import javafx.beans.value.ChangeListener;
import javafx.collections.ArrayChangeListener;
import javafx.collections.ObservableFloatArray;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.SkinBase;
import javafx.scene.effect.Effect;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Shape;

import java.util.Arrays;

/**
 * @author LeeWyatt
 *
 * QQ: 9670453
 * QQ群: 518914410
 * 音频组件的皮肤
 */
public class RXAudioSpectrumSkin extends SkinBase<RXAudioSpectrum> {
    private  RXAudioSpectrum control ;
    private Region[] rects;
    private  HBox box;

    private final ChangeListener<Shape> shapeChangeListener = (ob, ov, nv) -> {
        int len = rects.length;
        Shape barShape = control.getBarShape();
        for (Region rect : rects) {
            rect.setShape(barShape);
        }
    };

    private final ChangeListener<Effect> effectChangeListener = (ob, ov, nv) -> {
        int len = rects.length;
        Effect barEffect = control.getBarEffect();
        for (Region rect : rects) {
            rect.setEffect(barEffect);
        }
    };


    private final ChangeListener<Number> numberChangeListener = (ob, ov, nv) -> {
        int oldNumBands = ov.intValue();
        int newNumBands = nv.intValue();
        rects = Arrays.copyOf(rects, newNumBands);
        initBars(oldNumBands, newNumBands);
    };

    private final ChangeListener<RXAudioSpectrum.CrestPos> crestPosChangeListener = (ob, ov, nv) -> {
        drawBars(control.getMagnitudes());
    };

    private final ArrayChangeListener<ObservableFloatArray> dataChangeListener = (observableArray, sizeChanged, from, to) -> {
        drawBars(observableArray.toArray(new float[observableArray.size()]));
    };
    public RXAudioSpectrumSkin(RXAudioSpectrum control) {
        super(control);
        this.control = control;
        box = new HBox();
        box.getStyleClass().add("bar-box");
        int numBands = getNumBands();
        rects = new Region[numBands];
        initBars(0, numBands);
        getChildren().add(box);

        // 形状发生变化时
        control.barShapeProperty().addListener(shapeChangeListener);
        // 效果发生变化时
        control.barEffectProperty().addListener(effectChangeListener);
        // 频谱数量发生变化时
        control.audioSpectrumNumBandsProperty().addListener(numberChangeListener);
        // 波峰发生改变时
        control.crestPosProperty().addListener(crestPosChangeListener);
        // 频谱数据发生变化时
        control.magnitudesProperty().addListener(dataChangeListener);
    }

    private void drawBars(float[] magnitudes) {
        if (magnitudes == null||control.getAudioSpectrumNumBands()<RXAudioSpectrum.MIN_NUM_BANDS) {
            return;
        }
        int len = Math.min(magnitudes.length, getNumBands());
        RXAudioSpectrum.CrestPos crestPos = control.getCrestPos();
        switch (crestPos) {
            case LEFT:
                crestToLeft(magnitudes, len);
                break;
            case RIGHT:
                crestToRight(magnitudes, len);
                break;
            case SIDE:
                crestToSide(magnitudes, len);
                break;
            case DOUBLE:
                crestToDoubleMiddle(magnitudes, len);
                break;
            default:
                crestToMiddle(magnitudes, len);
                break;
        }
    }

    private void initBars(int start, int end) {
        Shape barShape = control.getBarShape();
        Effect barEffect = control.getBarEffect();
        for (int i = start; i < end; i++) {
            rects[i] = new Region();
            rects[i].getStyleClass().setAll("data" + i, "bar");
            rects[i].setShape(barShape);
            rects[i].setEffect(barEffect);
        }
        box.getChildren().setAll(rects);
    }

    /**
     * 波峰在中: 采样前面的数据, 后面的舍弃
     */
    private void crestToMiddle(float[] magnitudes, int len) {
        int offset = len % 2 == 0 ? -1 : 0;
        int j = len / 2 + offset;
        for (int i = 0; i <= len / 2 + offset; i++) {
            float value = magnitudes[i] + getThreshold();
            rects[j].setMaxHeight(value);
            rects[len - j - 1].setMaxHeight(value);
            j--;
        }
    }

    /**
     * 波峰在两侧, 取前面部分
     */
    private void crestToSide(float[] magnitudes, int len) {
        for (int i = 0; i <= len / 2; i++) {
            float value = magnitudes[i] + getThreshold();
            rects[i].setMaxHeight(value);
            rects[len - i - 1].setMaxHeight(value);
        }
    }


    /**
     * 波峰在两侧中间double波峰;取样前面部分
     */
    private void crestToDoubleMiddle(float[] magnitudes, int len) {
        int halfLen = len / 2;
        int offset = halfLen % 2 == 0 ? 0 : -1;
        for (int i = 0; i < halfLen + offset; i += 2) {
            rects[i / 2 + halfLen / 2].setMaxHeight(magnitudes[i] + getThreshold());
            rects[halfLen / 2 - i / 2 - 1].setMaxHeight(magnitudes[i + 1] + getThreshold());

        }
        if (offset == -1) {
            rects[halfLen-1].setMaxHeight( magnitudes[halfLen - 1] + getThreshold());
        }

        for (int i = 0; i < len / 2; i++) {
            rects[len - i - 1].setMaxHeight(rects[i].getMaxHeight());

        }
        if (len % 2 != 0) {
            rects[len / 2].setMaxHeight(magnitudes[len / 2]+getThreshold()) ;
        }

    }

    /**
     * 播放在左侧
     */
    private void crestToLeft(float[] magnitudes, int len) {
        for (int i = 0; i < len; i++) {
            rects[i].setMaxHeight(magnitudes[i] + getThreshold());
        }
    }

    /**
     * 波峰在右侧
     */
    private void crestToRight(float[] magnitudes, int len) {
        for (int i = 0; i < len; i++) {
            rects[i].setMaxHeight(magnitudes[len - i - 1] + getThreshold());
        }
    }

    private int getThreshold() {
        return Math.abs(control.getAudioSpectrumThreshold());
    }

    private int getNumBands() {
        int numBands = control.getAudioSpectrumNumBands();
        if (numBands < RXAudioSpectrum.MIN_NUM_BANDS) {
            return RXAudioSpectrum.MIN_NUM_BANDS;
        }
        return numBands;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(box, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return box.prefWidth(height) + leftInset + rightInset;
//        return leftInset + rightInset + box.minWidth(height);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + bottomInset + getThreshold();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return box.prefWidth(height) + leftInset + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return box.prefHeight(width) + topInset + bottomInset;
    }

    @Override
    public void dispose() {
        control.barShapeProperty().removeListener(shapeChangeListener);
        control.barEffectProperty().removeListener(effectChangeListener);
        control.audioSpectrumNumBandsProperty().removeListener(numberChangeListener);
        control.crestPosProperty().removeListener(crestPosChangeListener);
        control.magnitudesProperty().removeListener(dataChangeListener);
        box.getChildren().clear();
        rects = null;
        getChildren().clear();
        super.dispose();
    }
}

