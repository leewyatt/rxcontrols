package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTranslationButtonSkin;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 *
 * 有用两个Label的按钮,鼠标移动上去会显示hoverLabel,移除会显示nonHoverLabel
 *
 */
public class RXTranslationButton extends RXButtonBase {

    private static final String DEFAULT_STYLE_CLASS = "rx-translation-button";

    private RXTranslationButtonSkin skin;

    public RXTranslationButton() {
        this(null);
    }

    public RXTranslationButton(String text) {
        super(text == null ? "Translation" : text);
        init();
    }



    private void  init(){
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPrefSize(160, 60);
    }
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        skin = new RXTranslationButtonSkin(this);
        return skin;
    }

    public Label getHoverLabel() {
        return skin.getHoverLabel();
    }

    public Label getNonHoverLabel() {
        return skin.getNonHoverLabel();
    }



    /**
     * 移动方向
     */
    private StyleableObjectProperty<TranslationDir> translationDir;

    /**
     * 样式
     */
    private static class StyleableProperties {
        // 移动方向
        private static final CssMetaData<RXTranslationButton, TranslationDir> TRANSLATION_DIR = new CssMetaData<RXTranslationButton, TranslationDir>(
                "-rx-translation-dir", new EnumConverter<TranslationDir>(TranslationDir.class), TranslationDir.BOTTOM_TO_TOP) {
            @Override
            public boolean isSettable(RXTranslationButton control) {
                return control.translationDir == null || !control.translationDir.isBound();
            }

            @Override
            public StyleableProperty<TranslationDir> getStyleableProperty(RXTranslationButton control) {
                return control.translationDirProperty();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(RXButtonBase.getClassCssMetaData());
            Collections.addAll(styleables, TRANSLATION_DIR);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return RXTranslationButton.StyleableProperties.STYLEABLES;
    }

    public TranslationDir getTranslationDir() {
        return translationDirProperty().get();
    }

    public StyleableObjectProperty<TranslationDir> translationDirProperty() {
        if (translationDir == null) {
            translationDir = new StyleableObjectProperty<TranslationDir>(TranslationDir.BOTTOM_TO_TOP) {
                @Override
                public Object getBean() {
                    return RXTranslationButton.this;
                }

                @Override
                public String getName() {
                    return "translationDir";
                }

                @Override
                public CssMetaData<? extends Styleable, TranslationDir> getCssMetaData() {
                    return RXTranslationButton.StyleableProperties.TRANSLATION_DIR;
                }
            };
        }
        return translationDir;
    }

    public void setTranslationDir(TranslationDir translationDirection) {
        this.translationDirProperty().set(translationDirection);
    }

    public enum TranslationDir {
        /**
         * 运动方向
         */
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM
    }

}
