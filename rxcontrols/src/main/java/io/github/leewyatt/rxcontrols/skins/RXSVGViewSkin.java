package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSVGView;
import io.github.leewyatt.rxcontrols.utils.SvgUtil;
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import io.github.leewyatt.rxcontrols.pojo.PathInfo;

import java.util.ArrayList;

/**
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
