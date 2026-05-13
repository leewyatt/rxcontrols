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
package com.leewyatt.rxcontrols.skins;

import com.leewyatt.rxcontrols.controls.RXSVGView;
import com.leewyatt.rxcontrols.utils.SvgUtil;
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import com.leewyatt.rxcontrols.pojo.PathInfo;

import java.util.ArrayList;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public class RXSVGViewSkin extends SkinBase<RXSVGView> {
    private Pane pane;
    private InvalidationListener updateListener = ob -> updateSVG();

    public RXSVGViewSkin(RXSVGView control) {
        super(control);
        pane = new Pane();
        pane.getStyleClass().add("svg-pane");
        updateSVG();
        getChildren().setAll(pane);
        control.contentProperty().addListener(updateListener);
    }

    private void updateSVG() {
        pane.getChildren().clear();//首先清空
        String content = getSkinnable().getContent();
        if (content.trim().isEmpty()) {
            return;
        }
        ArrayList<PathInfo> pathInfos = SvgUtil.parseSvg(content);
        for (PathInfo info : pathInfos) {
            SVGPath path = new SVGPath();
            path.setContent(info.getPathD());
            path.setFill(Color.valueOf(info.getPathFill()));
            path.getStyleClass().add(info.getPathId());
            pane.getChildren().add(path);
        }
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        layoutInArea(pane, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    public void dispose() {
        getSkinnable().contentProperty().removeListener(updateListener);
        getChildren().clear();
        super.dispose();
    }
}
