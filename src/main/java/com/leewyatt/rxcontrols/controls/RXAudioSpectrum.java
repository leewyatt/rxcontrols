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

import com.leewyatt.rxcontrols.skins.RXAudioSpectrumSkin;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EffectConverter;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.ShapeConverter;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableFloatArray;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.effect.Effect;
import javafx.scene.shape.Shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 音频组件
 */
public class RXAudioSpectrum extends Control {
    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-audio-spectrum";
    /**
     * 默认外观css
     */
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();

    /**
     * 默认的波峰位置
     */
    private static final CrestPos DEFAULT_CREST_POS = CrestPos.MIDDLE;

    /**
     * 默认的柱状图特效
     */
    private static final Effect DEFAULT_BAR_EFFECT = null;

    /**
     * 默认的柱状图形状
     */
    private static final Shape DEFAULT_BAR_SHAPE = null;

    public RXAudioSpectrum() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }


    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAudioSpectrumSkin(this);
    }



    /**
     * 默认值 128
     * 最小值 2
     */
    public final static int DEFAULT_NUM_BANDS = 128;
    public final static int MIN_NUM_BANDS = 2;

    /**
     * 频谱采集数量
     */
    private IntegerProperty audioSpectrumNumBands;

    public final IntegerProperty audioSpectrumNumBandsProperty() {
        if (audioSpectrumNumBands == null) {
            audioSpectrumNumBands = new SimpleIntegerProperty(this, "audioSpectrumNumBands", DEFAULT_NUM_BANDS);
        }
        return audioSpectrumNumBands;
    }

    public final int getAudioSpectrumNumBands() {
        return audioSpectrumNumBands == null ? DEFAULT_NUM_BANDS : audioSpectrumNumBands.get();
    }

    public final void setAudioSpectrumNumBands(int value) {
        audioSpectrumNumBandsProperty().set(value);
    }

    /**
     * 频带
     */
    private ObservableFloatArray magnitudes;

    public ObservableFloatArray magnitudesProperty() {
        if (magnitudes == null) {
            magnitudes = FXCollections.observableFloatArray();
        }
        return magnitudes;
    }

    public final float[] getMagnitudes() {
        return magnitudes == null ? null : magnitudes.toArray(new float[magnitudes.size()]);
    }

    public final void setMagnitudes(float[] value) {
        magnitudesProperty().setAll(value);
    }

    /**
     * 频段 (暂时没有使用该数据;)
     */
    private ObservableFloatArray phases;

    public ObservableFloatArray phasesProperty() {
        if (phases == null) {
            phases = FXCollections.observableFloatArray();
        }
        return phases;
    }

    public final float[] getPhases() {
        return phases == null ? null : phases.toArray(new float[phases.size()]);
    }

    public final void setPhases(float[] value) {
        phasesProperty().setAll(value);
    }

    /**
     * audioSpectrumThreshold
     * 灵敏度阈值，以分贝为单位；必须为非正数。对于给定频谱带中的峰值频率，低于此阈值的值将被设置为阈值。
     * 默认值为理应-60 dB。
     * 但由于 Scene Builder 版本8或者版本11 输入负数会出现参数错误 (怀疑是bug)
     * 所以默认值调整为 正60
     */
    public final static int DEFAULT_SPECTRUM_THRESHOLD = 60;

    private SimpleIntegerProperty audioSpectrumThreshold;

    public final void setAudioSpectrumThreshold(int value) {
        //取绝对值
        audioSpectrumThresholdProperty().set(Math.abs(value));
    }

    public final int getAudioSpectrumThreshold() {
        return audioSpectrumThreshold == null ? DEFAULT_SPECTRUM_THRESHOLD : audioSpectrumThreshold.get();
    }

    public IntegerProperty audioSpectrumThresholdProperty() {
        if (audioSpectrumThreshold == null) {
            audioSpectrumThreshold = new SimpleIntegerProperty(this, "audioSpectrumThreshold", DEFAULT_SPECTRUM_THRESHOLD);
        }
        return audioSpectrumThreshold;
    }

    /**
     * barEffect 矩形(柱状) 的特效: 由于javafx中css的局限性,目前支持的特效就内外阴影两种特效 ;
     */
    private ObjectProperty<Effect> barEffect;

    public final ObjectProperty<Effect> barEffectProperty() {
        if (barEffect == null) {
            barEffect = new StyleableObjectProperty<Effect>(DEFAULT_BAR_EFFECT){

                @Override
                public CssMetaData<? extends Styleable, Effect> getCssMetaData() {
                    return StyleableProperties.BAR_EFFECT;
                }

                @Override
                public Object getBean() {
                    return RXAudioSpectrum.this;
                }

                @Override
                public String getName() {
                    return "barEffect";
                }
            };
        }
        return barEffect;
    }

    public final Effect getBarEffect() {
        return barEffect == null ? DEFAULT_BAR_EFFECT : barEffect.get();
    }

    public final void setBarEffect(Effect value) {
        barEffectProperty().set(value);
    }

    /**
     * 波峰的位置: 该属性支持css
     */
    private StyleableObjectProperty<CrestPos> crestPos;

    public final StyleableObjectProperty<CrestPos> crestPosProperty() {
        if (crestPos == null) {
            crestPos = new StyleableObjectProperty<CrestPos>(DEFAULT_CREST_POS) {
                @Override
                public CssMetaData<? extends Styleable, CrestPos> getCssMetaData() {
                    return RXAudioSpectrum.StyleableProperties.CREST_POS;
                }

                @Override
                public Object getBean() {
                    return RXAudioSpectrum.this;
                }

                @Override
                public String getName() {
                    return "crestPos";
                }

            };
        }

        return this.crestPos;
    }

    public final CrestPos getCrestPos() {
        return crestPos == null ? DEFAULT_CREST_POS : crestPos.get();
    }

    public final void setCrestPos(final CrestPos avatarShapeType) {
        crestPosProperty().set(avatarShapeType);

    }

    /**
     * 形状支持css
     */
    private ObjectProperty<Shape> barShape;

    public final Shape getBarShape() {
        return barShape == null ? DEFAULT_BAR_SHAPE : barShape.get();
    }

    public final void setBarShape(Shape value) {
        barShapeProperty().set(value);
    }

    public final ObjectProperty<Shape> barShapeProperty() {
        if (barShape == null) {
            barShape = new StyleableObjectProperty<Shape>() {
                @Override
                public Object getBean() {
                    return RXAudioSpectrum.this;
                }

                @Override
                public String getName() {
                    return "barShape";
                }

                @Override
                public CssMetaData<? extends Styleable, Shape> getCssMetaData() {
                    return RXAudioSpectrum.StyleableProperties.BAR_SHAPE;
                }
            };
        }
        return this.barShape;
    }

    // 样式
    private static class StyleableProperties {

        private static final CssMetaData<RXAudioSpectrum,Effect> BAR_EFFECT =
                new CssMetaData<RXAudioSpectrum,Effect>("-rx-bar-effect", EffectConverter.getInstance(),DEFAULT_BAR_EFFECT) {

                    @Override
                    public boolean isSettable(RXAudioSpectrum control) {
                        return control.barEffect == null || control.barEffect.isBound();
                    }

                    @Override
                    public StyleableProperty<Effect> getStyleableProperty(RXAudioSpectrum control) {
                        return (StyleableProperty<Effect>)control.effectProperty();
                    }
                };



        private static final CssMetaData<RXAudioSpectrum, Shape> BAR_SHAPE = new CssMetaData<RXAudioSpectrum, Shape>(
                "-rx-bar-shape", ShapeConverter.getInstance(),DEFAULT_BAR_SHAPE) {

            @Override
            public boolean isSettable(RXAudioSpectrum control) {
                return control.barShape == null || !control.barShape.isBound();
            }

            @Override
            public StyleableProperty<Shape> getStyleableProperty(RXAudioSpectrum control) {
                return (StyleableProperty<Shape>) control.barShapeProperty();
            }
        };
        private static final CssMetaData<RXAudioSpectrum, CrestPos> CREST_POS = new CssMetaData<RXAudioSpectrum, CrestPos>(
                "-rx-crest-pos", new EnumConverter<CrestPos>(CrestPos.class),DEFAULT_CREST_POS) {
            @Override
            public boolean isSettable(RXAudioSpectrum control) {
                return control.crestPos == null || !control.crestPos.isBound();
            }

            @Override
            public StyleableProperty<CrestPos> getStyleableProperty(RXAudioSpectrum control) {
                return control.crestPosProperty();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, CREST_POS, BAR_SHAPE,BAR_EFFECT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }

    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return RXAudioSpectrum.StyleableProperties.STYLEABLES;
    }

    /**
     * 波峰的位置
     */
    public enum CrestPos {
        /**
         *
         * 居中----^^----
         */
        MIDDLE,
        /**
         *
         * 靠左^^--------
         */
        LEFT,
        /**
         *
         * 靠右--------^^
         */
        RIGHT,
        /**
         *
         * 两侧^--------^
         */
        SIDE,
        /**
         * ---^---^---
         */
        DOUBLE

    }



}
