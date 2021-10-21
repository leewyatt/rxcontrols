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
package com.leewyatt.rxcontrols.controls;

import com.leewyatt.rxcontrols.skins.RXDigitSkin;
import com.sun.javafx.css.converters.PaintConverter;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 仿电子数字组件
 */
public class RXDigit extends Control {
    private static final String DEFAULT_STYLE_CLASS = "rx-digit";
    private static final Color DEFAULT_DARK_FILL = Color.web("#dddddd");
    private static final Color DEFAULT_LIGHT_FILL = Color.BLACK;
    /**
     * 填充色 (明亮部分)
     */
    private StyleableObjectProperty<Paint> lightFill;
    /**
     * 填充色(灰暗部分)
     */
    private StyleableObjectProperty<Paint> darkFill;
    /**
     * 数字
     */
    private SimpleIntegerProperty digit;

    public RXDigit() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        digit = new SimpleIntegerProperty(0);
    }

    public RXDigit(int digitNum) {
        this();
        setDigit(digitNum);
    }

    /**
     * @return 默认皮肤
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDigitSkin(this);
    }

    public final StyleableObjectProperty<Paint> lightFillProperty() {
        if (lightFill == null) {
            lightFill = new SimpleStyleableObjectProperty<Paint>(StyleableProperties.LIGHT_FILL, RXDigit.this,
                    "lightFill", DEFAULT_LIGHT_FILL);
        }
        return this.lightFill;
    }

    public final Paint getLightFill() {
        return lightFill == null ? DEFAULT_LIGHT_FILL : lightFill.get();
    }

    public final void setLightFill(final Paint lightFill) {
        this.lightFillProperty().set(lightFill);
    }

    public final StyleableObjectProperty<Paint> darkFillProperty() {
        if (darkFill == null) {
            darkFill = new SimpleStyleableObjectProperty<Paint>(StyleableProperties.DARK_FILL, RXDigit.this,
                    "darkFill", DEFAULT_DARK_FILL);
        }
        return this.darkFill;
    }

    public final Paint getDarkFill() {
        return darkFill == null ? DEFAULT_DARK_FILL : darkFill.get();
    }

    public final void setDarkFill(final Paint darkFill) {
        this.darkFillProperty().set(darkFill);
    }

    /**
     * 自定义样式
     */
    private static class StyleableProperties {
        private static final CssMetaData<RXDigit, Paint> LIGHT_FILL = new CssMetaData<RXDigit, Paint>(
                "-rx-light-fill", PaintConverter.getInstance(), Color.BLACK) {

            @Override
            public boolean isSettable(RXDigit control) {
                return control.lightFill == null || !control.lightFill.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(RXDigit control) {
                return control.lightFillProperty();
            }
        };

        private static final CssMetaData<RXDigit, Paint> DARK_FILL = new CssMetaData<RXDigit, Paint>(
                "-rx-dark-fill", PaintConverter.getInstance(), Color.web("#dddddd")) {

            @Override
            public boolean isSettable(RXDigit control) {
                return control.darkFill == null || !control.darkFill.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(RXDigit control) {
                return control.darkFillProperty();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, LIGHT_FILL, DARK_FILL);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    public final SimpleIntegerProperty digitProperty() {
        return this.digit;
    }

    public final int getDigit() {
        int value = this.digitProperty().getValue();
        if (value < 0) {
            return 0;
        } else if (value > 9) {
            return 9;
        } else {
            return value;
        }
    }

    public final void setDigit(final int digit) {
        if (digit < 0) {
            this.digitProperty().setValue(0);
        } else if (digit > 9) {
            this.digitProperty().setValue(9);
        } else {
            this.digitProperty().setValue(digit);
        }
    }

}
