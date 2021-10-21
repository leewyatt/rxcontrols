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

import com.leewyatt.rxcontrols.skins.RXAvatarSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 头像组件
 */
public class RXAvatar extends Control {
    private static final String DEFAULT_STYLE_CLASS = "rx-avatar";
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css").toExternalForm();
    private final int DEFAULT_SIZE = 100;

    private ObjectProperty<Image> image;
    private StyleableObjectProperty<Type> shapeType;
    private SimpleDoubleProperty arcWidth;
    private SimpleDoubleProperty arcHeight;

    public RXAvatar() {
        setPrefSize(100, 100);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public RXAvatar(String imageUrl) {
        this(new Image(imageUrl, true));
    }

    public RXAvatar(String imageUrl, boolean backgroundLoading) {
        this(new Image(imageUrl, backgroundLoading));
    }

    public RXAvatar(Image image) {
        this();
        setImage(image);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAvatarSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    public final ObjectProperty<Image> imageProperty() {
        if (image == null) {
            image = new SimpleObjectProperty<Image>(this, "image");
        }
        return this.image;
    }

    public final Image getImage() {
        return this.imageProperty().get();
    }

    public final void setImage(final Image image) {
        this.imageProperty().set(image);
    }

    public final StyleableObjectProperty<Type> shapeTypeProperty() {
        if (shapeType == null) {
            shapeType = new StyleableObjectProperty<Type>(Type.CIRCLE) {
                @Override
                public CssMetaData<? extends Styleable, Type> getCssMetaData() {
                    return StyleableProperties.SHAPE_TYPE;
                }

                @Override
                public Object getBean() {
                    return RXAvatar.this;
                }

                @Override
                public String getName() {
                    return "shapeType";
                }

            };
        }

        return this.shapeType;
    }

    public final Type getShapeType() {
        return this.shapeTypeProperty().get();
    }

    public final void setShapeType(final Type type) {
        this.shapeTypeProperty().set(type);
    }

    // 样式
    private static class StyleableProperties {
        private static final CssMetaData<RXAvatar, Type> SHAPE_TYPE = new CssMetaData<RXAvatar, Type>(
                "-rx-shape-type", new EnumConverter<Type>(Type.class), Type.CIRCLE) {
            @Override
            public boolean isSettable(RXAvatar control) {
                return control.shapeType == null || !control.shapeType.isBound();
            }

            @Override
            public StyleableProperty<Type> getStyleableProperty(RXAvatar control) {
                return control.shapeTypeProperty();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, SHAPE_TYPE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * 确保在sceneBuilder里可见css样式
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    public final SimpleDoubleProperty arcWidthProperty() {
        if (arcWidth == null) {
            arcWidth = new SimpleDoubleProperty(0);
        }
        return this.arcWidth;
    }

    public final double getArcWidth() {
        return this.arcWidthProperty().get();
    }

    public final void setArcWidth(final double arcWidth) {
        this.arcWidthProperty().set(arcWidth);
    }

    public final SimpleDoubleProperty arcHeightProperty() {
        if (arcHeight == null) {
            arcHeight = new SimpleDoubleProperty(0);
        }
        return this.arcHeight;
    }

    public final double getArcHeight() {
        return this.arcHeightProperty().get();
    }

    public final void setArcHeight(final double arcHeight) {
        this.arcHeightProperty().set(arcHeight);
    }

    public enum Type {
        /**
         * 圆形头像
         */
        CIRCLE,
        /**
         * 正方形头像
         */
        SQUARE,
        /**
         * 六边形 水平
         */
        HEXAGON_H,
        /**
         * 六边形 垂直
         */
        HEXAGON_V;
    }

}
