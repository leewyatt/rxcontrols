package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.event.RXActionEvent;
import io.github.leewyatt.rxcontrols.skins.RXTextFieldSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.event.EventHandler;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * 带按钮的文本框
 */
public class RXTextField extends TextField {

    private static final String DEFAULT_STYLE_CLASS = "rx-text-field";
    private static final String USER_AGENT_STYLESHEET = RXTextField.class.getResource("/rx-controls.css")
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
