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

import com.leewyatt.rxcontrols.animation.lineButton.LineAnimation;
import com.leewyatt.rxcontrols.controls.RXLineButton;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 线条按钮皮肤
 */
public class RXLineButtonSkin extends SkinBase<RXLineButton> {
    private Line line= new Line();
    private Label label;
    private RXLineButton control;
    private Pane pane;
    private Timeline animEnter = new Timeline();
    private Timeline animExit = new Timeline();

    public RXLineButtonSkin(RXLineButton control) {
        super(control);
        this.control = control;
        pane = new Pane();
        line.getStyleClass().add("line");
        line.setStartX(0);
        label = new Label();
        pane.getChildren().add(line);
        getChildren().addAll(label, pane);
        //属性的绑定
        label.ellipsisStringProperty().bind(control.ellipsisStringProperty());
        label.textFillProperty().bind(control.textFillProperty());
        label.fontProperty().bind(control.fontProperty());
        label.graphicProperty().bind(control.graphicProperty());
        label.contentDisplayProperty().bind(control.contentDisplayProperty());
        label.graphicTextGapProperty().bind(control.graphicTextGapProperty());
        label.alignmentProperty().bind(control.alignmentProperty());
        label.mnemonicParsingProperty().bind(control.mnemonicParsingProperty());
        label.textProperty().bind(control.textProperty());
        label.textAlignmentProperty().bind(control.textAlignmentProperty());
        label.textOverrunProperty().bind(control.textOverrunProperty());
        label.wrapTextProperty().bind(control.wrapTextProperty());
        label.underlineProperty().bind(control.underlineProperty());
        label.lineSpacingProperty().bind(control.lineSpacingProperty());
        //线条长度和文本长度绑定; 如果要改变线条长度, 那么用缩放scaleX
        //line.endXProperty().bind(label.widthProperty());
        //-------监听事件---
        //label.heightProperty().addListener(changeListener);
        //line.setOpacity(0.0);

        clipRegion(pane);


        control.getLineAnimation().init(this);

        control.lineAnimationProperty().addListener(animationChangeListener);
        control.offsetYProProperty().addListener(offsetYChangeListener);
        control.spacingProperty().addListener(spacingChangeListener);
        label.boundsInParentProperty().addListener(boundsChangeListener);

        //-------添加鼠标事件---------
        control.addEventFilter(MouseEvent.MOUSE_ENTERED, enterHandler);
        control.addEventFilter(MouseEvent.MOUSE_EXITED, exitHandler);
        control.addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
    }
    private  Rectangle rect = new Rectangle();
    private void clipRegion(Pane pane){
        rect.widthProperty().bind(pane.widthProperty());
        rect.heightProperty().bind(pane.heightProperty());
        pane.setClip(rect);
    }
    private ChangeListener<LineAnimation> animationChangeListener = (ob, ov, nv) -> {
        //旧的销毁
        ov.dispose();
        //新的初始化
        nv.init(this);
    };

    private  ChangeListener<Number> offsetYChangeListener = (ob, ov, nv) -> {
        control.getLineAnimation().initEnterAnim();
        control.getLineAnimation().initExitAnim();
    };

    private ChangeListener<Number> spacingChangeListener = (ob, ov, nv) -> {
        Bounds bounds = label.getBoundsInParent();
        double maxY = bounds.getMaxY() + nv.doubleValue();
        line.setStartY(maxY);
        line.setEndY(maxY);
    };

    private   ChangeListener<Bounds> boundsChangeListener = (ob, ov, nv) -> {
        double minX = nv.getMinX();
        double maxX = nv.getMaxX();
        double maxY = nv.getMaxY() + control.getSpacing();
        line.setStartY(maxY);
        line.setEndY(maxY);
        line.setStartX(minX);
        line.setEndX(maxX);
//            control.getLineAnimation().initEnterAnim();
//            control.getLineAnimation().initExitAnim();
    };

    private EventHandler<MouseEvent> enterHandler = event -> playAnimEnter();
    private EventHandler<MouseEvent> exitHandler = event -> playAnimExit();
    private EventHandler<MouseEvent> clickHandler = event -> {
        if (event.getButton() == MouseButton.PRIMARY) {
            control.fireEvent(new ActionEvent());
        }
    };

    /**
     * 播放鼠标移入的动画
     */
    public void playAnimEnter() {
        if (animExit.getStatus() == Animation.Status.RUNNING) {
            animExit.stop();
        }
        animEnter.play();
    }

    /**
     * 播放鼠标移除的动画
     */
    public void playAnimExit() {
        if (animEnter.getStatus() == Animation.Status.RUNNING) {
            animEnter.stop();
        }
        animExit.play();
    }


    public Line getLine() {
        return line;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.minWidth(height);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.minHeight(width);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.prefWidth(height) + leftInset + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.prefHeight(width) + topInset + bottomInset;
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
        layoutInArea(label, contentX, contentY, contentWidth, contentHeight, 0,
                control.getAlignment().getHpos(), control.getAlignment().getVpos());
        layoutInArea(pane, contentX, contentY, contentWidth, contentHeight, 0,
                control.getAlignment().getHpos(), control.getAlignment().getVpos());
    }

    @Override
    public void dispose() {
        if (animEnter != null) {
            animEnter.stop();
            animEnter = null;
        }
        if (animExit != null) {
            animExit.stop();
            animExit = null;
        }
        rect.widthProperty().unbind();
        rect.heightProperty().unbind();
        label.ellipsisStringProperty().unbind();
        label.textFillProperty().unbind();
        label.fontProperty().unbind();
        label.graphicProperty().unbind();
        label.contentDisplayProperty().unbind();
        label.graphicTextGapProperty().unbind();
        label.alignmentProperty().unbind();
        label.mnemonicParsingProperty().unbind();
        label.textProperty().unbind();
        label.textAlignmentProperty().unbind();
        label.textOverrunProperty().unbind();
        label.wrapTextProperty().unbind();
        label.underlineProperty().unbind();
        label.lineSpacingProperty().unbind();

        control.lineAnimationProperty().removeListener(animationChangeListener);
        control.offsetYProProperty().removeListener(offsetYChangeListener);
        control.spacingProperty().removeListener(spacingChangeListener);
        label.boundsInParentProperty().removeListener(boundsChangeListener);
        control.removeEventFilter(MouseEvent.MOUSE_ENTERED, enterHandler);
        control.removeEventFilter(MouseEvent.MOUSE_EXITED, exitHandler);
        control.removeEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
        
        getChildren().clear();
        super.dispose();
    }

    public Timeline getAnimEnter() {
        return animEnter;
    }


    public Timeline getAnimExit() {
        return animExit;
    }

    public Label getLabel() {
        return label;
    }
}
