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

import com.leewyatt.rxcontrols.pojo.LrcDoc;
import com.leewyatt.rxcontrols.skins.RXLrcViewSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.DurationConverter;
import com.sun.javafx.css.converters.SizeConverter;
import com.sun.javafx.css.converters.StringConverter;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.WritableValue;
import javafx.css.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 显示歌词的组件
 */
public class RXLrcView extends Control {
    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-lrc-view";
    /**
     * 默认外观css
     */
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();
    /**
     * 默认的翻页动画时间
     */
    private static final Duration DEFAULT_ANIMATION_TIME = Duration.millis(300);
    /**
     * 默认的行高
     */
    private static final double DEFAULT_LINE_HEIGHT = 25;
    /**
     * 默认的提示文字:
     */
    private final static String DEFAULT_TIP_STRING = "暂无歌词...";

    /**
     * 显示歌词的一行的缩放比例:
     */
    private final static double DEFAULT_SCALING = 1.3;
    /**
     * 当前歌词的放大比例
     */
    private DoubleProperty currentLineScaling;

    public final DoubleProperty currentLineScalingProperty() {
        if (currentLineScaling == null) {
            currentLineScaling = new StyleableDoubleProperty(DEFAULT_SCALING) {
                @Override
                public Object getBean() {
                    return this;
                }

                @Override
                public String getName() {
                    return "currentLineScaling";
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.CURRENT_LINE_SCALING;
                }
            };
        }
        return currentLineScaling;
    }

    public double getCurrentLineScaling() {
        return currentLineScaling == null ? DEFAULT_SCALING : currentLineScalingProperty().get();
    }

    public void setCurrentLineScaling(double value) {
        currentLineScalingProperty().set(value);
    }

    /**
     * 用户对歌词的时间调整
     * 歌词的最终显示时间 = 时间标签的时间 + 歌词里的offset偏移量 + 用户调整的歌词时间偏移量
     */
    private ObjectProperty<Duration> userOffset;

    public final ObjectProperty<Duration> userOffsetProperty() {
        if (userOffset == null) {
            userOffset = new SimpleObjectProperty<Duration>(this, "userOffset", Duration.ZERO);
        }
        return userOffset;
    }

    public final Duration getUserOffset() {
        if (userOffset == null) {
            return Duration.ZERO;
        }
        return userOffsetProperty().get();
    }

    public final void setUserOffset(Duration value) {
        userOffsetProperty().set(value);
    }

    /**
     * 歌词文档 (解析后的歌词文档对象)
     * 可以通过 LrcUtil.parseLrc(String lrc) 获得该对象
     */
    private ObjectProperty<LrcDoc> lrcDoc;

    public final ObjectProperty<LrcDoc> lrcDocProperty() {
        if (lrcDoc == null) {
            lrcDoc = new SimpleObjectProperty<LrcDoc>(this, "lrcDoc");
        }
        return lrcDoc;
    }

    public final LrcDoc getLrcDoc() {
        if (lrcDoc == null) {
            return null;
        }
        return lrcDocProperty().get();
    }

    public final void setLrcDoc(LrcDoc value) {
        lrcDocProperty().set(value);
    }

    /**
     * 播放时间(进度)
     */
    private ObjectProperty<Duration> currentTime;

    public final ObjectProperty<Duration> currentTimeProperty() {
        if (currentTime == null) {
            currentTime = new SimpleObjectProperty<Duration>(this, "currentTime", Duration.ZERO);
        }
        return currentTime;
    }

    public final Duration getCurrentTime() {
        if (currentTime == null) {
            return Duration.ZERO;
        }
        return currentTimeProperty().get();
    }

    public final void setCurrentTime(Duration value) {
        currentTimeProperty().set(value);
    }

    /**
     * 歌词(上移等.)动画的时间
     * 为了避免歌词错乱,最大动画时间,不能超过下一句歌词与这句歌词的空隙
     * 最后一句歌词不存在下一句, 注意分开判断
     */
    private StyleableObjectProperty<Duration> animationTime;

    public final StyleableObjectProperty<Duration> animationTimeProperty() {
        if (animationTime == null) {
            animationTime = new SimpleStyleableObjectProperty<>(RXLrcView.StyleableProperties.ANIMATION_TIME, this,
                    "animationTime", DEFAULT_ANIMATION_TIME);
        }
        return this.animationTime;
    }

    public final Duration getAnimationTime() {
        return animationTime == null ? DEFAULT_ANIMATION_TIME : animationTime.get();
    }

    public final void setAnimationTime(final Duration animationTime) {
        this.animationTimeProperty().set(animationTime);
    }

    /**
     * 设置行高;
     * 每行歌词用行高比用间距好计算点...
     */

    private StyleableDoubleProperty lineHeight;

    public final DoubleProperty lineHeightProperty() {
        if (lineHeight == null) {
            lineHeight = new StyleableDoubleProperty(DEFAULT_LINE_HEIGHT) {
                @Override
                public Object getBean() {
                    return this;
                }

                @Override
                public String getName() {
                    return "lineHeight";
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.LINE_HEIGHT;
                }
            };
        }
        return lineHeight;
    }

    public final double getLineHeight() {
        return lineHeight == null ? DEFAULT_LINE_HEIGHT : lineHeightProperty().get();
    }

    public final void setLineHeight(double value) {
        lineHeightProperty().set(value);
    }

    /**
     * 当歌词文件为空时候的文字,
     * 当加载的时候,可以提示: 歌词正在加载, 请稍等...
     * 或者当歌词为空的时候,可以提示 :纯音乐,无歌词...
     */

    private StyleableStringProperty tipString;

    public final StringProperty tipStringProperty() {
        if (tipString == null) {
            tipString = new StyleableStringProperty(DEFAULT_TIP_STRING) {
                @Override
                public Object getBean() {
                    return RXLrcView.this;
                }

                @Override
                public String getName() {
                    return "tipString";
                }

                @Override
                public CssMetaData<RXLrcView, String> getCssMetaData() {
                    return StyleableProperties.TIP_STRING;
                }
            };
        }
        return tipString;
    }

    public final void setTipString(String value) {
        tipStringProperty().set((value == null) ? "" : value);
    }

    public final String getTipString() {
        return tipString == null ? DEFAULT_TIP_STRING : tipStringProperty().get();
    }

    /**
     * 构造器
     */
    public RXLrcView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPrefSize(280, 360);
    }

    /**
     * 默认的外观
     *
     * @return
     */
    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXLrcViewSkin(this);
    }

    /**
     * 自定义css的支持
     */
    private static class StyleableProperties {
        private static final CssMetaData<RXLrcView, Duration> ANIMATION_TIME =
                new CssMetaData<RXLrcView, Duration>(
                        "-rx-animation-time", DurationConverter.getInstance(), DEFAULT_ANIMATION_TIME) {

                    @Override
                    public boolean isSettable(RXLrcView control) {
                        return control.animationTime == null || !control.animationTime.isBound();
                    }

                    @Override
                    public StyleableProperty<Duration> getStyleableProperty(RXLrcView control) {
                        return control.animationTimeProperty();
                    }
                };

        private static final CssMetaData<RXLrcView, Number> LINE_HEIGHT =
                new CssMetaData<RXLrcView, Number>("-rx-line-height",
                        SizeConverter.getInstance(), DEFAULT_LINE_HEIGHT) {

                    @Override
                    public boolean isSettable(RXLrcView node) {
                        return node.lineHeight == null || !node.lineHeight.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(RXLrcView node) {
                        return (StyleableProperty<Number>) node.lineHeightProperty();
                    }

                };
        private static final CssMetaData<RXLrcView, String> TIP_STRING =
                new CssMetaData<RXLrcView, String>("-rx-tip-string",
                        StringConverter.getInstance(), DEFAULT_TIP_STRING) {

                    @Override
                    public boolean isSettable(RXLrcView n) {
                        return n.tipString == null || !n.tipString.isBound();
                    }

                    @Override
                    public StyleableProperty<String> getStyleableProperty(RXLrcView n) {
                        return (StyleableProperty<String>) (WritableValue<String>) n.tipStringProperty();
                    }
                };

        private static final CssMetaData<RXLrcView, Number> CURRENT_LINE_SCALING =
                new CssMetaData<RXLrcView, Number>("-rx-line-scaling",
                        SizeConverter.getInstance(),DEFAULT_SCALING) {
                    @Override
                    public boolean isSettable(RXLrcView n) {
                        return n.currentLineScaling == null || !n.currentLineScaling.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(RXLrcView n) {
                        return (StyleableProperty<Number>)n.currentLineScalingProperty();
                    }
                };
        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, ANIMATION_TIME, LINE_HEIGHT, TIP_STRING,CURRENT_LINE_SCALING);
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
        return RXLrcView.StyleableProperties.STYLEABLES;
    }

}
