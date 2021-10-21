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

import com.sun.javafx.css.converters.DurationConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.css.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * FillButton, LineButton等的基类
 *
 */
public abstract class RXButtonBase extends Labeled {
    /**
     * 动画默认时间
     */
    private static final Duration DEFAULT_ANIMATION_TIME = Duration.millis(130);
    /**
     * 动画时间
     */
    private StyleableObjectProperty<Duration> animationTime;
    public static final String DEFAULT_TEXT="Button";
    /**
     * 点击事件
     */
    private ObjectProperty<EventHandler<ActionEvent>> onAction;

    /**
     *
     * @return 事件
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<EventHandler<ActionEvent>>() {
                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get());
                }

                @Override
                public Object getBean() {
                    return RXButtonBase.this;
                }

                @Override
                public String getName() {
                    return "onAction";
                }
            };
        }
        return onAction;
    }

    public final EventHandler<ActionEvent> getOnAction() {
        return onAction == null ? null : onActionProperty().get();
    }

    public final void setOnAction(EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    public RXButtonBase() {
        this(null);
    }

    public RXButtonBase(String text) {
        super(text==null?DEFAULT_TEXT:text);
    }

    public RXButtonBase(String text, Node graphic) {
        super(text==null?DEFAULT_TEXT:text, graphic);
    }

    /**
     * 样式
     */
    private static class StyleableProperties {
        private static final CssMetaData<RXButtonBase, Duration> ANIMATION_TIME = new CssMetaData<RXButtonBase, Duration>(
                "-rx-animation-time", DurationConverter.getInstance(),DEFAULT_ANIMATION_TIME) {

            @Override
            public boolean isSettable(RXButtonBase control) {
                return control.animationTime == null || !control.animationTime.isBound();
            }

            @Override
            public StyleableProperty<Duration> getStyleableProperty(RXButtonBase control) {
                return control.animationTimeProperty();
            }
        };


        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Labeled.getClassCssMetaData());
            styleables.add(ANIMATION_TIME);
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

    public final StyleableObjectProperty<Duration> animationTimeProperty() {
        if (animationTime == null) {
            animationTime = new SimpleStyleableObjectProperty<>(RXButtonBase.StyleableProperties.ANIMATION_TIME, this,
                    "animationTime", DEFAULT_ANIMATION_TIME);
        }
        return this.animationTime;
    }

    public final Duration getAnimationTime() {
        return animationTime==null? DEFAULT_ANIMATION_TIME :animationTime.get();
    }

    public final void setAnimationTime(final Duration animationTime) {
        this.animationTimeProperty().set(animationTime);
    }
}
