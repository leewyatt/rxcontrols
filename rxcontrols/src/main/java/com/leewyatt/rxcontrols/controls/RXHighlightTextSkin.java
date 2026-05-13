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

import com.leewyatt.rxcontrols.controls.RXHighlightText.MatchRules;
import com.leewyatt.rxcontrols.utils.StringUtil;
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
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
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
