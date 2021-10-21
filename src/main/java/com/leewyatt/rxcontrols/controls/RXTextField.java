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

import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.leewyatt.rxcontrols.event.RXActionEvent;
import com.leewyatt.rxcontrols.skins.RXTextFieldSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.event.EventHandler;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 带按钮的文本框
 */
public class RXTextField extends TextField {

    private static final String DEFAULT_STYLE_CLASS = "rx-text-field";
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();



    public RXTextField() {
        super();
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public RXTextField(String text) {
        super(text);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }


    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    /**
     * 当点击了自带的按钮后的事件处理
     * 这里使用的是RXActionEvent ,因为如果使用ActionEvent ,那么会和TextField自带的事件重合
     */
    private final ObjectProperty<EventHandler<RXActionEvent>> onClickButtonProperty =
            new ObjectPropertyBase<EventHandler<RXActionEvent>>() {

                @Override
                protected void invalidated() {
                    setEventHandler(RXActionEvent.RXACTION, get());
                }

                @Override
                public Object getBean() {
                    return RXTextField.this;
                }

                @Override
                public String getName() {
                    return "onAction";
                }
            };

    public ObjectProperty<EventHandler<RXActionEvent>> onClickButtonProperty() {
        return onClickButtonProperty;
    }

    public EventHandler<RXActionEvent> getOnClickButton() {
        return onClickButtonProperty.get();
    }

    public void setOnClickButton(EventHandler<RXActionEvent> value) {
        onClickButtonProperty.set(value);
    }



    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTextFieldSkin(this);
    }

    private StyleableObjectProperty<DisplayMode> buttonDisplayMode;

    private static class StyleableProperties {
        // 按钮显示
        private static final CssMetaData<RXTextField, DisplayMode> BUTTON_DISPLAY_MODE = new CssMetaData<RXTextField, DisplayMode>(
                "-rx-button-display", new EnumConverter<DisplayMode>(DisplayMode.class), DisplayMode.AUTO) {
            @Override
            public boolean isSettable(RXTextField control) {
                return control.buttonDisplayMode == null || !control.buttonDisplayMode.isBound();
            }

            @Override
            public StyleableProperty<DisplayMode> getStyleableProperty(RXTextField control) {
                return control.buttonDisplayModeProperty();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(TextField.getClassCssMetaData());
            Collections.addAll(styleables, BUTTON_DISPLAY_MODE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }

    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return RXTextField.StyleableProperties.STYLEABLES;
    }

    public final StyleableObjectProperty<DisplayMode> buttonDisplayModeProperty() {
        if (buttonDisplayMode == null) {
            buttonDisplayMode = new StyleableObjectProperty<DisplayMode>(DisplayMode.AUTO) {

                @Override
                public CssMetaData<? extends Styleable, DisplayMode> getCssMetaData() {
                    return RXTextField.StyleableProperties.BUTTON_DISPLAY_MODE;
                }

                @Override
                public Object getBean() {
                    return RXTextField.this;
                }

                @Override
                public String getName() {
                    return "buttonDisplayMode";
                }
            };
        }
        return this.buttonDisplayMode;
    }

    public final DisplayMode getButtonDisplayMode() {
        return buttonDisplayMode==null?DisplayMode.AUTO :buttonDisplayMode.get();
    }

    public final void setButtonDisplayMode(final DisplayMode buttonDisplayMode) {
        this.buttonDisplayModeProperty().set(buttonDisplayMode);
    }
}
