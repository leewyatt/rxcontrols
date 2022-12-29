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

import com.leewyatt.rxcontrols.controls.RXPasswordField;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.SimpleStyleableStringProperty;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;


/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class RXPasswordFieldSkin extends TextFieldSkin {
    /**
     * 默认的小圆点
     */
    public static final char BULLET = '\u25cf';
    /**
     * 加密用的字符(取字符串第一个字符); 用于替代默认的小圆点
     */
    private SimpleStyleableStringProperty echochar;
    /**
     * 是否显示密码为明文
     */
    private SimpleBooleanProperty showPassword;
    private RXPasswordField control;
    private StackPane btn;
    private ChangeListener<DisplayMode> displayModeChangeListener = (ob, ov, nv) -> {
        setButtonStatus(nv);
    };

    public final SimpleBooleanProperty showPasswordProperty() {
        if (showPassword == null) {
            showPassword = new SimpleBooleanProperty(false);
        }
        return this.showPassword;
    }

    public final boolean isShowPassword() {
        return this.showPasswordProperty().get();
    }

    public final void setShowPassword(final boolean showPassword) {
        this.showPasswordProperty().set(showPassword);
    }

    InvalidationListener updateListener = ob -> {
        control.setText(control.getText());
        //control.setShowing(showPasswordProperty().get());
    };

    public RXPasswordFieldSkin(RXPasswordField control) {
        super(control);
        this.control = control;
        showPasswordProperty().addListener(updateListener);
        echocharProperty().addListener(updateListener);

        Pane topPane = new Pane();
        topPane.getStyleClass().add("tf-top-pane");
        btn = new StackPane();
        btn.getStyleClass().add("tf-button");
        btn.setMinSize(16, 16);
        //btn.setMaxSize(16, 16);
        Region region = new Region();
        region.getStyleClass().add("tf-button-shape");
        btn.getChildren().addAll(region);
        topPane.getChildren().addAll(btn);
        getChildren().addAll(topPane);

        btn.layoutXProperty().bind(topPane.widthProperty().subtract(btn.widthProperty()));
        btn.layoutYProperty().bind(topPane.heightProperty().subtract(btn.heightProperty()).divide(2));
        DisplayMode displayMode = control.getButtonDisplayMode();

        setButtonStatus(displayMode);
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                showPasswordProperty().set(!showPasswordProperty().get());
                control.end();
            }
        });
        control.buttonDisplayModeProperty().addListener(displayModeChangeListener);

    }

    private void setButtonStatus(DisplayMode displayMode) {
        if (displayMode == DisplayMode.SHOW) {
            btn.visibleProperty().unbind();
            btn.setVisible(true);
        } else if (displayMode == DisplayMode.HIDE) {
            btn.visibleProperty().unbind();
            btn.setVisible(false);
        } else if (displayMode == DisplayMode.AUTO) {
            // btn.setVisible(false);
            btn.visibleProperty().bind(control.focusedProperty());
        }
    }

    @Override
    protected String maskText(String txt) {
        TextField textField = getSkinnable();
        if (showPasswordProperty().get()) {
            return textField.getText();
        } else {
            int n = textField.getLength();
            StringBuilder passwordBuilder = new StringBuilder(n);
            for (int i = 0; i < n; i++) {
                String str = this.echocharProperty().get();
                passwordBuilder.append((str == null || str.length() == 0) ? BULLET : str.charAt(0));
            }
            return passwordBuilder.toString();
        }
    }

    public final SimpleStyleableStringProperty echocharProperty() {
        if (echochar == null) {
            echochar = new SimpleStyleableStringProperty(RXPasswordField.StyleableProperties.ECHOCHAR, this,"enchochar",
                    String.valueOf(BULLET));
        }
        return this.echochar;
    }

    public final String getEchochar() {
        return this.echocharProperty().get();
    }

    public final void setEchochar(final String echochar) {
        this.echocharProperty().set(echochar);
    }

    @Override
    public void dispose() {
        control.buttonDisplayModeProperty().removeListener(displayModeChangeListener);
        showPasswordProperty().removeListener(updateListener);
        echocharProperty().removeListener(updateListener);
        
        btn.visibleProperty().unbind();
        btn.layoutXProperty().unbind();
        btn.layoutYProperty().unbind();
        
        getChildren().clear();
        super.dispose();
    }

}
