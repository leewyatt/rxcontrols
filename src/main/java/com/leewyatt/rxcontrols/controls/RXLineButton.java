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

import com.leewyatt.rxcontrols.animation.lineButton.LineAnimExtend;
import com.leewyatt.rxcontrols.animation.lineButton.LineAnimRise;
import com.leewyatt.rxcontrols.animation.lineButton.LineAnimation;
import com.leewyatt.rxcontrols.skins.RXLineButtonSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.shape.Line;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * 线条按钮
 */
public class RXLineButton extends RXButtonBase {
    RXLineButtonSkin skin ;
    private static final String DEFAULT_STYLE_CLASS = "rx-line-button";
    private StyleableObjectProperty<LineType> lineType;
    private ObjectProperty<LineAnimation> lineAnimation;
    private SimpleDoubleProperty spacing;
    private SimpleDoubleProperty offsetYPro;

    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();

    public RXLineButton() {
        this(null);
    }

    public RXLineButton(String text) {
        super(text==null?"RXLineButton":text);
        init();
    }

    public RXLineButton(String text, Node graphic) {
        super(text==null?"RXLineButton":text, graphic);
        init();
    }

    private void init(){
        skin = new RXLineButtonSkin(this);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPrefSize(150, 60);
        setAlignment(Pos.CENTER);
        lineTypeProperty().addListener(lineTypeChangeListener);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    private ChangeListener<LineType> lineTypeChangeListener = (ob,ov,nv)->{
        switch (nv){
            case RISE:
                setLineAnimation(new LineAnimRise());
                break;
            case EXTEND:
            default:
                setLineAnimation(new LineAnimExtend());
        }
    };
    @Override
    protected Skin<?> createDefaultSkin() {
        return skin;
    }
    public double getOffsetYPro() {
        return offsetYPro == null?15:offsetYPro.get();
    }

    public SimpleDoubleProperty offsetYProProperty() {
        if(offsetYPro==null){
            offsetYPro=new SimpleDoubleProperty(15);
        }
        return offsetYPro;
    }

    public void setOffsetYPro(double offsetYPro) {
        this.offsetYPro.set(offsetYPro);
    }

    public Line getLine(){
        return skin.getLine();
    }

    public LineAnimation getLineAnimation() {
        return lineAnimation == null?new LineAnimExtend():lineAnimation.get();
    }

    public ObjectProperty<LineAnimation> lineAnimationProperty() {
        if(lineAnimation == null){
            lineAnimation = new SimpleObjectProperty<LineAnimation>(RXLineButton.this, "lineAnimation", new LineAnimExtend());
        }
        return lineAnimation;
    }

    public void setLineAnimation(LineAnimation lineAnimation) {
        lineAnimationProperty().set(lineAnimation);
    }

    public LineType getLineType() {
        return lineType==null?LineType.EXTEND:lineType.get();
    }

    public StyleableObjectProperty<LineType> lineTypeProperty() {
        if (lineType==null){
            lineType = new SimpleStyleableObjectProperty<LineType>(RXLineButton.StyleableProperties.LINE_TYPE, this, "lineType", LineType.EXTEND);
        }
        return lineType;
    }

    public void setLineType(LineType lineType) {
        lineTypeProperty().set(lineType);
    }

    public double getSpacing() {
        return spacing == null?0:spacing.get();
    }

    public SimpleDoubleProperty spacingProperty() {
        if (spacing == null) {
            spacing = new SimpleDoubleProperty(0);
        }
        return spacing;
    }

    public void setSpacing(double spacing) {
        spacingProperty().set(spacing);
    }
    //-------------------CSS Styles-----------------------

    private static class StyleableProperties {

        private static final CssMetaData<RXLineButton, LineType> LINE_TYPE =
                new CssMetaData<RXLineButton, LineType>("-rx-line-type", new EnumConverter<LineType>(LineType.class), LineType.EXTEND) {

                    @Override
                    public boolean isSettable(RXLineButton control) {
                        return control.lineType == null || !control.lineType.isBound();
                    }

                    @Override
                    public StyleableProperty<LineType> getStyleableProperty(RXLineButton control) {
                        return control.lineTypeProperty();
                    }
                };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(RXButtonBase.getClassCssMetaData());
            Collections.addAll(styleables, LINE_TYPE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return RXLineButton.StyleableProperties.STYLEABLES;
    }

    /**
     * 线条的显示方式
     */

    public enum LineType {
        /**
         * 线条从中心向两边延伸
         */
        EXTEND,
        /**
         * 线条上升
         */
        RISE
    }

}
