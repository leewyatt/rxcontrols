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

import com.leewyatt.rxcontrols.animation.fillbutton.*;
import com.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.PaintConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
 * 颜色填充按钮
 */
public class RXFillButton extends RXButtonBase {
    private StyleableObjectProperty<FillType> fillType;
    private StyleableObjectProperty<Paint> hoverTextFill;
    private ObjectProperty<FillAnimation> fillAnimation;
    private static final Color DEFAULT_FILL_TEXT_PAINT = Color.WHITE;

    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-fill-button";
    /**
     * 默认外观css
     */
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();

    public RXFillButton() {
        this(null);
    }

    public RXFillButton(String text) {
        super(text == null ? "RXFillButton" : text);
        init();
    }

    public RXFillButton(String text, Node graphic) {
        super(text == null ? "RXFillButton" : text, graphic);
        init();
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    private void init() {
        setPrefSize(150, 60);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAlignment(Pos.CENTER);
        fillTypeProperty().addListener(fillTypeChangeListener);
        fillAnimation = new SimpleObjectProperty<>(this, "fillAnimation", new FillAnimLeftToRight());
    }

    public FillAnimation getFillAnimation() {
        return fillAnimation.get();
    }

    public ObjectProperty<FillAnimation> fillAnimationProperty() {
        return fillAnimation;
    }

    public void setFillAnimation(FillAnimation fillAnimation) {
        fillAnimationProperty().set(fillAnimation);
    }

    private ChangeListener<FillType> fillTypeChangeListener = (ob, oldType, newType) -> {
        switch (newType) {
            case BOTTOM_TO_TOP:
                setFillAnimation(new FillAnimBottomToTop());
                break;
            case CIRCLE_TO_SIDE:
                setFillAnimation(new FillAnimCircleToSide());
                break;
            case CORNER_TO_CENTER:
                setFillAnimation(new FillAnimCornerToCenter());
                break;
            case HOR_TO_MIDDLE:
                setFillAnimation(new FillAnimHorToMiddle());
                break;
            case HOR_TO_SIDE:
                setFillAnimation(new FillAnimHorToSide());
                break;
            case HOR_ZIGZAG:
                setFillAnimation(new FillAnimHorZigzag());
                break;
            case RIGHT_TO_LEFT:
                setFillAnimation(new FillAnimRightToLeft());
                break;
            case TOP_TO_BOTTOM:
                setFillAnimation(new FillAnimTopToBottom());
                break;
            case VER_TO_MIDDLE:
                setFillAnimation(new FillAnimVerToMiddle());
                break;
            case VER_TO_SIDE:
                setFillAnimation(new FillAnimVerToSide());
                break;
            case VER_ZIGZAG:
                setFillAnimation(new FillAnimVerZigzag());
                break;
            case LEFT_TO_RIGHT:
            default:
                setFillAnimation(new FillAnimLeftToRight());
                break;
        }
    };

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXFillButtonSkin(this);
    }

    /**
     * @return 填充类型
     */
    public StyleableObjectProperty<FillType> fillTypeProperty() {
        if (fillType == null) {
            fillType = new SimpleStyleableObjectProperty<FillType>(StyleableProperties.FILL_TYPE, this, "fillType", FillType.LEFT_TO_RIGHT);
        }
        return this.fillType;
    }

    public void setFillType(FillType type) {
        this.fillTypeProperty().set(type);
    }

    public FillType getFillType() {
        return this.fillType == null ? FillType.LEFT_TO_RIGHT : this.fillType.get();
    }

    /**
     * @return 文本填充色
     */
    public Paint getHoverTextFill() {
        return hoverTextFill == null ? DEFAULT_FILL_TEXT_PAINT : hoverTextFillProperty().get();
    }

    public StyleableObjectProperty<Paint> hoverTextFillProperty() {
        if (hoverTextFill == null) {
            hoverTextFill = new SimpleStyleableObjectProperty<Paint>(StyleableProperties.HOVER_TEXT_FILL, this, "hoverTextFill", DEFAULT_FILL_TEXT_PAINT);
        }
        return hoverTextFill;
    }

    public void setHoverTextFill(Paint hoverTextFill) {
        this.hoverTextFillProperty().set(hoverTextFill);
    }

    //-------------------CSS Styles-----------------------

    private static class StyleableProperties {

        private static final CssMetaData<RXFillButton, FillType> FILL_TYPE =
                new CssMetaData<RXFillButton, FillType>("-rx-fill-type", new EnumConverter<FillType>(FillType.class), FillType.LEFT_TO_RIGHT) {

                    @Override
                    public boolean isSettable(RXFillButton control) {
                        return control.fillType == null || !control.fillType.isBound();
                    }

                    @Override
                    public StyleableProperty<FillType> getStyleableProperty(RXFillButton control) {
                        return control.fillTypeProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Paint> HOVER_TEXT_FILL =
                new CssMetaData<RXFillButton, Paint>("-rx-hover-text-fill", PaintConverter.getInstance(), DEFAULT_FILL_TEXT_PAINT) {
                    @Override
                    public boolean isSettable(RXFillButton styleable) {
                        return styleable.hoverTextFill == null || !styleable.hoverTextFill.isBound();
                    }

                    @Override
                    public StyleableProperty<Paint> getStyleableProperty(RXFillButton styleable) {
                        return styleable.hoverTextFillProperty();
                    }
                };
        // 创建一个CSS样式的表
        private static List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(RXButtonBase.getClassCssMetaData());
            Collections.addAll(styleables, FILL_TYPE, HOVER_TEXT_FILL);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }
    /**
     * 填充方式
     */

    public enum FillType {
        /**
         * fill的动画方向描述
         * HOR_代表水平方向
         * VER_代表垂直方向
         */
        BOTTOM_TO_TOP,
        CIRCLE_TO_SIDE,
        CORNER_TO_CENTER,
        HOR_TO_MIDDLE,
        HOR_TO_SIDE,
        HOR_ZIGZAG,
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        TOP_TO_BOTTOM,
        VER_TO_MIDDLE,
        VER_TO_SIDE,
        VER_ZIGZAG
    }

}
