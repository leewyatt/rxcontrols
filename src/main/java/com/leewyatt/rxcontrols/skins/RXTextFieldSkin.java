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

import com.leewyatt.rxcontrols.controls.RXTextField;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.leewyatt.rxcontrols.event.RXActionEvent;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import javafx.beans.value.ChangeListener;
import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class RXTextFieldSkin extends TextFieldSkin {
    private Region region;
    private RXTextField textField;
    private StackPane btn;
    private ChangeListener<DisplayMode> displayModeChangeLi = (ob, ov, nv) -> {
        setButtonStatus(nv);
    };

    public RXTextFieldSkin(RXTextField textField) {
        super(textField);
        this.textField = textField;
        Pane topPane = new Pane();
        topPane.getStyleClass().add("tf-top-pane");
        btn = new StackPane();
        btn.getStyleClass().add("tf-button");
        region = new Region();
        region.getStyleClass().add("tf-button-shape");
        btn.getChildren().addAll(region);
        topPane.getChildren().addAll(btn);
        getChildren().addAll(topPane);
        btn.layoutXProperty().bind(topPane.widthProperty().subtract(btn.widthProperty()));
        btn.layoutYProperty().bind(topPane.heightProperty().subtract(btn.heightProperty()).divide(2));
        DisplayMode displayMode = textField.buttonDisplayModeProperty().get();

        setButtonStatus(displayMode);
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseClicked(e -> {
           getSkinnable().fireEvent(new RXActionEvent());
        });

        textField.buttonDisplayModeProperty().addListener(displayModeChangeLi);
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
            btn.visibleProperty().bind(textField.focusedProperty());
        }
    }

    @Override
    public void dispose() {
        btn.visibleProperty().unbind();
        btn.layoutXProperty().unbind();
        btn.layoutYProperty().unbind();
        textField.buttonDisplayModeProperty().removeListener(displayModeChangeLi);
        getChildren().clear();
        super.dispose();
    }
}
