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

import com.leewyatt.rxcontrols.skins.RXSVGViewSkin;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * SVG组件. 可以显示多条路径, 已经分别填充颜色
 *
 */
public class RXSVGView extends Control {
    private static final String DEFAULT_STYLE_CLASS = "rx-svg-view";

    private StringProperty content;
    public RXSVGView(){
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }
    public RXSVGView(String content){
        this();
        setContent(content);
    }

    public final void setContent(String value) {
        contentProperty().set(value);
    }

    public final String getContent() {
        return content == null ? "" : content.get();
    }


    public final StringProperty contentProperty() {
        if (content == null) {
            content = new StringPropertyBase("") {
                @Override
                public Object getBean() {
                    return RXSVGView.this;
                }
                @Override
                public String getName() {
                    return "content";
                }
            };
        }
        return content;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSVGViewSkin(this);
    }
}
