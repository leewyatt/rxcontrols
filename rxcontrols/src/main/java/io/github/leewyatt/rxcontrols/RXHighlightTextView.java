package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.HighlightSegmenter;
import io.github.leewyatt.rxcontrols.skins.RXHighlightTextViewSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Skin;

import java.util.Collections;
import java.util.List;

/**
 * A selectable text control that also highlights one or more keywords inside its text.
 *
 * <p>Extends {@link RXTextView} with keyword matching: keywords are supplied via
 * {@link #getKeywords()} and matched either literally or as regular expressions,
 * case-sensitively or not, according to {@link #getMatchRules()}. The read-only
 * {@link #matchedProperty() matched} reports whether any keyword matched, so callers can
 * drive search / filter UIs (see {@link #isMatched()}); the selection, caret and copy
 * behaviour are inherited unchanged from {@link RXTextView}.
 *
 * <p>Keyword matching runs on the JavaFX application thread; very large text combined
 * with a catastrophically backtracking regular expression can stall it, so callers
 * should keep keyword patterns simple.
 */
public class RXHighlightTextView extends RXTextView {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-highlight-text-view";

    /**
     * Default matching rule: literal substring, case-insensitive.
     */
    public static final MatchRules DEFAULT_MATCH_RULES = MatchRules.LITERAL_IGNORE_CASE;

    // ==================== Constructors ====================

    /**
     * Creates an empty highlight-text control.
     */
    public RXHighlightTextView() {
        this("");
    }

    /**
     * Creates a highlight-text control with the given text and no keywords.
     *
     * @param text the text to display; {@code null} is treated as empty
     */
    public RXHighlightTextView(String text) {
        super(text);
        init();
    }

    /**
     * Creates a highlight-text control with the given text and keywords.
     *
     * @param text     the text to display; {@code null} is treated as empty
     * @param keywords the keywords to highlight; {@code null} adds none
     */
    public RXHighlightTextView(String text, String... keywords) {
        super(text);
        if (keywords != null) {
            Collections.addAll(this.keywords, keywords);
        }
        init();
    }

    private void init() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setSelectable(false);
        InvalidationListener recompute = obs -> recompute();
        textProperty().addListener(recompute);
        keywords.addListener(recompute);
        matchRules.addListener(recompute);
        recompute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXHighlightTextViewSkin(this);
    }

    private void recompute() {
        MatchRules rules = getMatchRules();
        if (rules == null) {
            rules = DEFAULT_MATCH_RULES;
        }
        List<IndexRange> ranges = HighlightSegmenter.highlightRanges(
                getText(), keywords, rules.isRegex(), rules.isIgnoreCase());
        matched.set(!ranges.isEmpty());
        highlightRanges.set(ranges);
    }

    // ==================== Keywords ====================

    private final ObservableList<String> keywords = FXCollections.observableArrayList();

    /**
     * The list of keywords to highlight. Empty or blank entries are ignored; each
     * remaining entry is matched literally or as a regular expression depending on
     * {@link #matchRulesProperty()}. Occurrences of one keyword are matched
     * non-overlapping (via {@code Matcher.find()}); ranges from different keywords that
     * overlap or touch are merged into a single contiguous highlight.
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

    // ==================== Highlight Ranges (read-only, skin-facing) ====================

    private final ReadOnlyObjectWrapper<List<IndexRange>> highlightRanges =
            new ReadOnlyObjectWrapper<>(this, "highlightRanges", List.of());

    /**
     * The merged, ordered, non-overlapping character ranges that currently match a
     * keyword — the single source of truth the skin consumes to paint highlight
     * backgrounds and to split highlighted text runs. Recomputed once per text /
     * keyword / rule change (the same pass that updates {@link #matchedProperty()
     * matched}), so the matched flag and the rendered highlights can never disagree.
     *
     * @return the read-only highlight-ranges property
     */
    public final ReadOnlyObjectProperty<List<IndexRange>> highlightRangesProperty() {
        return highlightRanges.getReadOnlyProperty();
    }

    /**
     * Returns the current highlight ranges (unmodifiable).
     *
     * @return the merged, ordered, non-overlapping highlight ranges
     */
    public final List<IndexRange> getHighlightRanges() {
        return highlightRanges.get();
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
