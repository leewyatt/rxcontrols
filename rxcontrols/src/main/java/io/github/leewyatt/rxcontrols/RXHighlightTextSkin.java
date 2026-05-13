package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXHighlightText.MatchRules;
import io.github.leewyatt.rxcontrols.utils.StringUtil;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Pair;

import java.util.ArrayList;

/**
 *
 */
public class RXHighlightTextSkin extends SkinBase<RXHighlightText> {
    /**
     * 避免用户直接访问matchWrapper属性. 但是skin可以访问, 所以把属性的访问限制设置为protected
     */
    protected ReadOnlyBooleanWrapper matchWrapper=new ReadOnlyBooleanWrapper(false);
    private RXHighlightText control;
    private TextFlow textFlow;
    private InvalidationListener invalidListener = ob -> {
        fillPane();
    };

    public RXHighlightTextSkin(RXHighlightText control) {
        super(control);
        this.control = control;
        textFlow = new TextFlow();
        textFlow.getStyleClass().add("text-flow");
        textFlow.lineSpacingProperty().bind(control.lineSpacingProperty());
        textFlow.textAlignmentProperty().bind(control.textAlignmentProperty());
        getChildren().add(textFlow);

        control.textProperty().addListener(invalidListener);
        control.keywordsProperty().addListener(invalidListener);
        control.matchRulesProperty().addListener(invalidListener);
        fillPane();
    }

    private boolean flag;

    private void fillPane() {
        textFlow.getChildren().clear();//首先清空
        ArrayList<Pair<String, Boolean>> list;
        if (control.getMatchRules() == MatchRules.MATCH_CASE) {
            list = StringUtil.parseText(control.getText(), control.getKeywords(), false);
        } else if (control.getMatchRules() == MatchRules.IGNORE_CASE) {
            list = StringUtil.parseText(control.getText(), control.getKeywords(), true);
        } else {
            list = StringUtil.matchText(control.getText(), control.getKeywords());
        }
        flag =false;
        list.forEach(pair -> {
            if (pair.getValue()) {
                flag = true;
                Label node = new Label(pair.getKey());
                node.getStyleClass().add("highlight-label");//高亮的文本
                textFlow.getChildren().add(node);
            } else {
                Text node = new Text(pair.getKey());
                node.getStyleClass().add("plain-text");//普通文本
                textFlow.getChildren().add(node);
            }
        });
        matchWrapper.set(flag);
        list.clear();
        list = null;
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        layoutInArea(textFlow, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    @Override
    public void dispose() {
        control.textProperty().removeListener(invalidListener);
        control.keywordsProperty().removeListener(invalidListener);
        control.matchRulesProperty().removeListener(invalidListener);
        textFlow.lineSpacingProperty().unbind();
        textFlow.textAlignmentProperty().unbind();

        if (textFlow != null) {
            textFlow.getChildren().clear();
        }
        getChildren().clear();
        super.dispose();
    }
}
