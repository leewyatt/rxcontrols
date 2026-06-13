package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHighlightText;
import io.github.leewyatt.rxcontrols.RXHighlightText.MatchRules;
import io.github.leewyatt.rxcontrols.internal.HighlightSegmenter;
import io.github.leewyatt.rxcontrols.internal.HighlightSegmenter.Segment;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * Skin for {@link RXHighlightText}. Renders the segmented text into a {@link TextFlow},
 * using a {@link Label} for highlighted runs (so {@code -fx-background-color} paints a
 * background, which {@link Text} cannot) and a {@link Text} for plain runs.
 */
public class RXHighlightTextSkin extends RXSkinBase<RXHighlightText> {

    private static final String HIGHLIGHT_STYLE_CLASS = "highlight";
    private static final String PLAIN_STYLE_CLASS = "plain";

    private final TextFlow textFlow;

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXHighlightTextSkin(RXHighlightText control) {
        super(control);
        textFlow = new TextFlow();
        textFlow.getStyleClass().add("text-flow");
        getChildren().add(textFlow);

        disposer.registerBinding(textFlow.lineSpacingProperty(), control.lineSpacingProperty());
        disposer.registerBinding(textFlow.textAlignmentProperty(), control.textAlignmentProperty());

        disposer.registerListener(control.textProperty(), this::rebuild);
        disposer.registerListener(control.getKeywords(), this::rebuild);
        disposer.registerListener(control.matchRulesProperty(), this::rebuild);
        rebuild();
    }

    private void rebuild() {
        RXHighlightText control = getSkinnable();
        MatchRules rules = control.getMatchRules();
        if (rules == null) {
            rules = RXHighlightText.DEFAULT_MATCH_RULES;
        }
        List<Segment> segments = HighlightSegmenter.segment(
                control.getText(), control.getKeywords(), rules.isRegex(), rules.isIgnoreCase());

        textFlow.getChildren().clear();
        for (Segment segment : segments) {
            if (segment.highlight()) {
                Label label = new Label(segment.text());
                label.getStyleClass().add(HIGHLIGHT_STYLE_CLASS);
                textFlow.getChildren().add(label);
            } else {
                Text text = new Text(segment.text());
                text.getStyleClass().add(PLAIN_STYLE_CLASS);
                textFlow.getChildren().add(text);
            }
        }
    }

    // SkinBase's default height computation asks the child for prefHeight(-1)
    // (unbounded width), ignoring that TextFlow is HORIZONTAL content-biased — so
    // wrapped text would overflow. Delegate to the TextFlow at the actual wrap width.

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double contentWidth = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        return topInset + textFlow.prefHeight(contentWidth) + bottomInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(textFlow, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected void disposeSkin() {
        textFlow.getChildren().clear();
    }
}
