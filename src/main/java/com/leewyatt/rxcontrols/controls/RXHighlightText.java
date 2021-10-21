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

import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.SizeConverter;
import javafx.beans.property.*;
import javafx.css.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 关键字高亮文本: 支持正则表达式匹配,或者 普通文本进行匹配
 */
public class RXHighlightText extends Control {
    private RXHighlightTextSkin skin;
    private ObjectProperty<MatchRules> matchRules ;
    private static final String DEFAULT_STYLE_CLASS = "rx-highlight-text";
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css").toExternalForm();

    public MatchRules getMatchRules() {
        return matchRules==null?MatchRules.MATCH_CASE:matchRules.get();
    }

    public ObjectProperty<MatchRules> matchRulesProperty() {
        if (matchRules == null) {
            matchRules = new SimpleObjectProperty<>(MatchRules.MATCH_CASE);
        }
        return matchRules;
    }

    public void setMatchRules(MatchRules matchRules) {
        this.matchRulesProperty().set(matchRules);
    }

    public ReadOnlyBooleanProperty matchProperty() {
        return skin.matchWrapper.getReadOnlyProperty();
    }

    public boolean isMatch() {
        return  matchProperty().get();
    }

    public RXHighlightText() {
        this(null);
    }

    public RXHighlightText(String text) {
        setText(text == null ? "RXHighlightText" : text);
        init();
    }

    public RXHighlightText(String text, String keywords) {
        setText(text == null ? "RXHighlightText" : text);
        setKeywords(keywords);
        init();
    }

    private void init(){
        skin = new RXHighlightTextSkin(this);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMouseTransparent(true);
    }
    @Override
    protected Skin<?> createDefaultSkin() {
        return skin;
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }



    /**
     * textProperty文本内容
     */
    private  StringProperty text;

    public final StringProperty textProperty() {
        if (text == null) {
            text = new SimpleStringProperty(this, "");
        }
        return text;
    }

    public final String getText() {
        return text==null?"":text.get();
    }

    public final void setText(String value) {
        textProperty().set(value);
    }

    /**
     * keywordsProperty 关键字
     */
    private  SimpleStringProperty keywords;

    public final StringProperty keywordsProperty() {
        if(keywords==null){
            keywords = new SimpleStringProperty(this, "");
        }
        return keywords;
    }

    public final String getKeywords() {
        return keywords == null?"": keywords.get();
    }

    public final void setKeywords(String value) {
        keywordsProperty().set(value);
    }


    private ObjectProperty<TextAlignment> textAlignment;

    public final void setTextAlignment(TextAlignment value) {
        textAlignmentProperty().set(value);
    }

    public final TextAlignment getTextAlignment() {
        return textAlignment == null ? TextAlignment.LEFT : textAlignment.get();
    }

    public final ObjectProperty<TextAlignment> textAlignmentProperty() {
        if (textAlignment == null) {
            textAlignment =
                    new StyleableObjectProperty<TextAlignment>(TextAlignment.LEFT) {
                        @Override
                        public Object getBean() {
                            return RXHighlightText.this;
                        }

                        @Override
                        public String getName() {
                            return "textAlignment";
                        }

                        @Override
                        public CssMetaData<RXHighlightText, TextAlignment> getCssMetaData() {
                            return RXHighlightText.StyleableProperties.TEXT_ALIGNMENT;
                        }

                    };
        }
        return textAlignment;
    }

    private DoubleProperty lineSpacing;

    public final void setLineSpacing(double spacing) {
        lineSpacingProperty().set(spacing);
    }

    public final double getLineSpacing() {
        return lineSpacing == null ? 0 : lineSpacing.get();
    }

    public final DoubleProperty lineSpacingProperty() {
        if (lineSpacing == null) {
            lineSpacing =
                    new StyleableDoubleProperty(0) {
                        @Override
                        public Object getBean() {
                            return RXHighlightText.this;
                        }

                        @Override
                        public String getName() {
                            return "lineSpacing";
                        }

                        @Override
                        public CssMetaData<RXHighlightText, Number> getCssMetaData() {
                            return RXHighlightText.StyleableProperties.LINE_SPACING;
                        }
                    };
        }
        return lineSpacing;
    }

    private static class StyleableProperties {

        private static final
        CssMetaData<RXHighlightText, TextAlignment> TEXT_ALIGNMENT =
                new CssMetaData<RXHighlightText, TextAlignment>("-fx-text-alignment",
                        new EnumConverter<TextAlignment>(TextAlignment.class),
                        TextAlignment.LEFT) {

                    @Override
                    public boolean isSettable(RXHighlightText node) {
                        return node.textAlignment == null || !node.textAlignment.isBound();
                    }

                    @Override
                    public StyleableProperty<TextAlignment> getStyleableProperty(RXHighlightText node) {
                        return (StyleableProperty<TextAlignment>) node.textAlignmentProperty();
                    }
                };

        private static final
        CssMetaData<RXHighlightText, Number> LINE_SPACING =
                new CssMetaData<RXHighlightText, Number>("-fx-line-spacing",
                        SizeConverter.getInstance(), 0) {

                    @Override
                    public boolean isSettable(RXHighlightText node) {
                        return node.lineSpacing == null || !node.lineSpacing.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(RXHighlightText node) {
                        return (StyleableProperty<Number>) node.lineSpacingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<CssMetaData<? extends Styleable, ?>>(Control.getClassCssMetaData());
            styleables.add(TEXT_ALIGNMENT);
            styleables.add(LINE_SPACING);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return RXHighlightText.StyleableProperties.STYLEABLES;

    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * 匹配方式
     */

    public enum MatchRules {
        /**
         * 把字符串当正则表达式去匹配
         */
        REGEX,
        /**
         * 把字符串当普通字符串去匹配; 区分大小写
         */
        MATCH_CASE,
        /**
         *把字符串当成普通字符串匹配,不区分大小写
         */
        IGNORE_CASE
    }

}
