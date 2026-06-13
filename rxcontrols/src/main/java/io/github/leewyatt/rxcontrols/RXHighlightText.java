package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.HighlightSegmenter;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXHighlightTextSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A non-editable text control that highlights one or more keywords inside its text.
 *
 * <p>Keywords are supplied via {@link #getKeywords()} and matched either literally or
 * as regular expressions, case-sensitively or not, according to {@link #getMatchRules()}.
 * The read-only {@link #matchedProperty() matched} reports whether any keyword matched,
 * so callers can drive search / filter UIs (see {@link #isMatched()}).
 */
public class RXHighlightText extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-highlight-text";

    /**
     * Default matching rule: literal substring, case-insensitive.
     */
    public static final MatchRules DEFAULT_MATCH_RULES = MatchRules.LITERAL_IGNORE_CASE;

    // ==================== Constructors ====================

    /**
     * Creates an empty highlight-text control.
     */
    public RXHighlightText() {
        this("");
    }

    /**
     * Creates a highlight-text control with the given text and no keywords.
     *
     * @param text the text to display; {@code null} is treated as empty
     */
    public RXHighlightText(String text) {
        setText(text == null ? "" : text);
        init();
    }

    /**
     * Creates a highlight-text control with the given text and keywords.
     *
     * @param text     the text to display; {@code null} is treated as empty
     * @param keywords the keywords to highlight; {@code null} adds none
     */
    public RXHighlightText(String text, String... keywords) {
        setText(text == null ? "" : text);
        if (keywords != null) {
            Collections.addAll(this.keywords, keywords);
        }
        init();
    }

    private void init() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        InvalidationListener recompute = obs -> recompute();
        text.addListener(recompute);
        keywords.addListener(recompute);
        matchRules.addListener(recompute);
        recompute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXHighlightTextSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link Orientation#HORIZONTAL} because the text wraps, so the
     * control's height depends on the width allotted to it.
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    private void recompute() {
        MatchRules rules = getMatchRules();
        if (rules == null) {
            rules = DEFAULT_MATCH_RULES;
        }
        matched.set(HighlightSegmenter.matches(getText(), keywords, rules.isRegex(), rules.isIgnoreCase()));
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    /**
     * The text to display and highlight.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the displayed text.
     *
     * @return the text
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the displayed text.
     *
     * @param value the text to display
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Keywords ====================

    private final ObservableList<String> keywords = FXCollections.observableArrayList();

    /**
     * The list of keywords to highlight. Empty or blank entries are ignored; each
     * remaining entry is matched literally or as a regular expression depending on
     * {@link #matchRulesProperty()}.
     *
     * @return the live, mutable keyword list
     */
    public final ObservableList<String> getKeywords() {
        return keywords;
    }

    // ==================== Match Rules ====================

    private final ObjectProperty<MatchRules> matchRules =
            new SimpleObjectProperty<>(this, "matchRules", DEFAULT_MATCH_RULES);

    /**
     * How keywords are matched against the text (literal / regex, case sensitivity).
     *
     * @return the match-rules property
     */
    public final ObjectProperty<MatchRules> matchRulesProperty() {
        return matchRules;
    }

    /**
     * Returns the matching rule.
     *
     * @return the match rule
     */
    public final MatchRules getMatchRules() {
        return matchRules.get();
    }

    /**
     * Sets the matching rule.
     *
     * @param value the match rule
     */
    public final void setMatchRules(MatchRules value) {
        matchRules.set(value);
    }

    // ==================== Matched (read-only) ====================

    private final ReadOnlyBooleanWrapper matched =
            new ReadOnlyBooleanWrapper(this, "matched", false);

    /**
     * Whether any keyword currently matches the text.
     *
     * @return the read-only matched property
     */
    public final ReadOnlyBooleanProperty matchedProperty() {
        return matched.getReadOnlyProperty();
    }

    /**
     * Returns whether any keyword currently matches the text.
     *
     * @return {@code true} if at least one keyword matches
     */
    public final boolean isMatched() {
        return matched.get();
    }

    // ==================== Text Alignment ====================

    private final ObjectProperty<TextAlignment> textAlignment =
            new StyleableObjectProperty<>(TextAlignment.LEFT) {
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
                    return StyleableProperties.TEXT_ALIGNMENT;
                }
            };

    /**
     * The horizontal alignment of each line of text. Styleable via
     * {@code -fx-text-alignment}.
     *
     * @return the text-alignment property
     */
    public final ObjectProperty<TextAlignment> textAlignmentProperty() {
        return textAlignment;
    }

    /**
     * Returns the horizontal text alignment.
     *
     * @return the text alignment
     */
    public final TextAlignment getTextAlignment() {
        return textAlignment.get();
    }

    /**
     * Sets the horizontal text alignment.
     *
     * @param value the text alignment
     */
    public final void setTextAlignment(TextAlignment value) {
        textAlignment.set(value);
    }

    // ==================== Line Spacing ====================

    private final DoubleProperty lineSpacing =
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
                    return StyleableProperties.LINE_SPACING;
                }
            };

    /**
     * The vertical spacing between text lines, in pixels. Styleable via
     * {@code -fx-line-spacing}.
     *
     * @return the line-spacing property
     */
    public final DoubleProperty lineSpacingProperty() {
        return lineSpacing;
    }

    /**
     * Returns the vertical spacing between text lines.
     *
     * @return the line spacing, in pixels
     */
    public final double getLineSpacing() {
        return lineSpacing.get();
    }

    /**
     * Sets the vertical spacing between text lines.
     *
     * @param value the line spacing, in pixels
     */
    public final void setLineSpacing(double value) {
        lineSpacing.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXHighlightText, TextAlignment> TEXT_ALIGNMENT =
                new CssMetaData<>("-fx-text-alignment",
                        new EnumConverter<>(TextAlignment.class), TextAlignment.LEFT) {

                    @Override
                    public boolean isSettable(RXHighlightText node) {
                        return !node.textAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<TextAlignment> getStyleableProperty(RXHighlightText node) {
                        return (StyleableProperty<TextAlignment>) node.textAlignmentProperty();
                    }
                };

        private static final CssMetaData<RXHighlightText, Number> LINE_SPACING =
                new CssMetaData<>("-fx-line-spacing", SizeConverter.getInstance(), 0) {

                    @Override
                    public boolean isSettable(RXHighlightText node) {
                        return !node.lineSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXHighlightText node) {
                        return (StyleableProperty<Number>) node.lineSpacingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(TEXT_ALIGNMENT);
            styleables.add(LINE_SPACING);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CssMetaData associated with this class, including that of its
     * superclasses.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Match Rules enum ====================

    /**
     * How keywords are matched against the text.
     */
    public enum MatchRules {
        /**
         * Literal substring match, case-sensitive.
         */
        LITERAL_CASE_SENSITIVE(false, false),
        /**
         * Literal substring match, case-insensitive.
         */
        LITERAL_IGNORE_CASE(false, true),
        /**
         * Regular-expression match, case-sensitive.
         */
        REGEX(true, false),
        /**
         * Regular-expression match, case-insensitive.
         */
        REGEX_IGNORE_CASE(true, true);

        private final boolean regex;
        private final boolean ignoreCase;

        MatchRules(boolean regex, boolean ignoreCase) {
            this.regex = regex;
            this.ignoreCase = ignoreCase;
        }

        /**
         * @return {@code true} if keywords are treated as regular expressions
         */
        public boolean isRegex() {
            return regex;
        }

        /**
         * @return {@code true} if matching ignores case
         */
        public boolean isIgnoreCase() {
            return ignoreCase;
        }
    }
}
