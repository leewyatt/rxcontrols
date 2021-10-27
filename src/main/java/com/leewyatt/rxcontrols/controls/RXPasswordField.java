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
import com.leewyatt.rxcontrols.skins.RXPasswordFieldSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.StringConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.*;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 密码组件:
 * 可以隐藏显示密码
 * 可以修改密码的小圆点
 */
public class RXPasswordField extends PasswordField {
    private StyleableObjectProperty<DisplayMode> buttonDisplayMode;
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();
    private static final String DEFAULT_STYLE_CLASS = "rx-password-field";
    /**
     * 获取皮肤(目的是为了对showPassword进行控制: maskText 方法里需要使用showPasswordProperty,
     * 如果把showPasswordProperty给Control , 那么maskText 会抛出空指针,因为刚开始的时候Control为空
     */
    protected RXPasswordFieldSkin skin;

    public final SimpleBooleanProperty showPasswordProperty() {
        return skin.showPasswordProperty();
    }

    public final boolean isShowPassword() {
        return skin.showPasswordProperty().get();
    }

    public final void setShowPassword(final boolean showPassword) {
        skin.showPasswordProperty().set(showPassword);
    }

    public RXPasswordField() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        skin = new RXPasswordFieldSkin(this);
        setAccessibleRole(AccessibleRole.PASSWORD_FIELD);
        showingProperty().addListener(
                (observable, oldValue, newValue) -> pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, newValue));
        showingProperty().bind(showPasswordProperty());
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    public RXPasswordField(String txt) {
        this();
        setText(txt);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return skin;
    }

    /**
     * 空实现, 不能剪切
     */
    @Override
    public void cut() {
    }

    /**
     * 空实现,不能赋值
     */
    @Override
    public void copy() {
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case TEXT:
                return null;
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }


    public final SimpleStyleableStringProperty echocharProperty() {
        return skin.echocharProperty();
    }

    public final String getEchochar() {
        return skin.echocharProperty().get();
    }

    public final void setEchochar(final String echochar) {
        skin.echocharProperty().set(echochar);
    }

    public final StyleableObjectProperty<DisplayMode> buttonDisplayModeProperty() {
        if (buttonDisplayMode == null) {
            buttonDisplayMode = new StyleableObjectProperty<DisplayMode>(DisplayMode.AUTO) {

                @Override
                public CssMetaData<? extends Styleable, DisplayMode> getCssMetaData() {
                    return StyleableProperties.BUTTON_DISPLAY_MODE;
                }

                @Override
                public Object getBean() {
                    return RXPasswordField.this;
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

    // 样式
    public static class StyleableProperties {
        public static final CssMetaData<RXPasswordField, String> ECHOCHAR = new CssMetaData<RXPasswordField, String>(
                "-rx-echochar", StringConverter.getInstance(), String.valueOf(RXPasswordFieldSkin.BULLET)) {

            @Override
            public StyleableProperty<String> getStyleableProperty(RXPasswordField control) {
                return control.echocharProperty();
            }

            @Override
            public boolean isSettable(RXPasswordField control) {
                return control.echocharProperty() == null || !control.echocharProperty().isBound();
            }
        };
        // 按钮显示
        private static final CssMetaData<RXPasswordField, DisplayMode> BUTTON_DISPLAY_MODE = new CssMetaData<RXPasswordField, DisplayMode>(
                "-rx-button-display", new EnumConverter<DisplayMode>(DisplayMode.class), DisplayMode.AUTO) {
            @Override
            public boolean isSettable(RXPasswordField control) {
                return control.buttonDisplayMode == null || !control.buttonDisplayMode.isBound();
            }

            @Override
            public StyleableProperty<DisplayMode> getStyleableProperty(RXPasswordField control) {
                return control.buttonDisplayModeProperty();
            }
        };

        // 创建一个CSS样式的表
        protected static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(PasswordField.getClassCssMetaData());
            Collections.addAll(styleables, ECHOCHAR, BUTTON_DISPLAY_MODE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    private BooleanProperty showing;

    private final void setShowing(boolean showing) {
        showingProperty().set(showing);
    }

    private final boolean isShowing() {
        return showing == null ? false : showing.get();
    }

    private final BooleanProperty showingProperty() {
        if (showing == null) {
            showing = new SimpleBooleanProperty(false);
        }
        return showing;
    }
}
