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

import com.leewyatt.rxcontrols.animation.fillbutton.FillAnimation;
import com.leewyatt.rxcontrols.controls.RXFillButton;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 填充颜色按钮皮肤
 */
public class RXFillButtonSkin extends SkinBase<RXFillButton> {

    //private static final Background DEFAULT_FILL_BACKGROUND = new Background(new BackgroundFill(Color.rgb(97, 109, 255), CornerRadii.EMPTY, Insets.EMPTY));

    private RXFillButton control;
    /**
     * 文字标签
     */
    private Label label;
    /**
     * 背景填充用区域 (Rectangle 无法单独设置每个角的radius;所以使用Region比较好)
     */
    private Region fillRegion;
    /**
     * 鼠标进入时的动画
     */
    private Timeline animEnter;
    /**
     * 鼠标移除时的动画
     */
    private Timeline animExit;

    public RXFillButtonSkin(RXFillButton control) {
        super(control);
        this.control = control;
        animEnter = new Timeline();
        animExit = new Timeline();

        //------------高亮区域的初始化-------------
        fillRegion = new Region();
        fillRegion.getStyleClass().add("fill-region");
        //fillRegion.setBackground(DEFAULT_FILL_BACKGROUND);
        //因为layoutInArea() 所以fillRegion所以就不用管他的宽高了
        //fillRegion.backgroundProperty().bind(control.fillRegionBackgroundProperty());
        //fillRegion.borderProperty().bind(control.fillRegionBorderProperty());

        //--------------文字标签的初始化以及绑定--------------------------
        label = new Label();
//        label.getStyleClass().add("button-label");
        label.ellipsisStringProperty().bind(control.ellipsisStringProperty());
        //颜色不用绑定了;因为textFill用于存储普通状态时的颜色值, 所以会根据需要的时候显示该颜色label.textFillProperty().bind(control.textFillProperty());
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

        getChildren().addAll(fillRegion, label);

        //初始化准备以及动画
        control.getFillAnimation().init(this);

        control.fillAnimationProperty().addListener(fillAnimationChangeListener);

        //----------初始化/重置动画---------------------
        fillRegion.widthProperty().addListener(animChangeListener);
        fillRegion.heightProperty().addListener(animChangeListener);
        control.hoverTextFillProperty().addListener(animChangeListener);
        control.textFillProperty().addListener(animChangeListener);
        control.animationTimeProperty().addListener(animChangeListener);

        //-------添加鼠标事件---------
        control.addEventFilter(MouseEvent.MOUSE_ENTERED, enterHandler);
        control.addEventFilter(MouseEvent.MOUSE_EXITED, exitHandler);
        control.addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);

    }

    private ChangeListener animChangeListener = (ob, ov, nv) -> restAnim();
    private EventHandler<MouseEvent> enterHandler = event -> playAnimEnter();
    private EventHandler<MouseEvent> exitHandler = event -> playAnimExit();
    private EventHandler<MouseEvent> clickHandler = event -> {
        if (event.getButton() == MouseButton.PRIMARY) {
            control.fireEvent(new ActionEvent());
        }
    };

    private void restAnim() {
        control.getFillAnimation().initEnterAnim();
        control.getFillAnimation().initExitAnim();

    }

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

    private ChangeListener<FillAnimation> fillAnimationChangeListener = (ob, ov, nv) -> {
        //旧的销毁
        ov.dispose();
        //新的初始化
        nv.init(this);
    };

    //    @Override
//    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
//        return label.minWidth(height);
//    }
//
//    @Override
//    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
//        return label.minHeight(width);
//    }
//
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.prefWidth(height) + leftInset + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return label.prefHeight(width) + topInset + bottomInset;
    }
//
//    @Override
//    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
//        return label.prefWidth(height) + leftInset + rightInset;
//    }
//
//    @Override
//    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
//        return label.prefHeight(width) + topInset + bottomInset;
//    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
        layoutInArea(fillRegion, contentX, contentY, contentWidth, contentHeight, 0,
                HPos.CENTER, VPos.CENTER);
        layoutInArea(label, contentX, contentY, contentWidth, contentHeight, 0,
                control.getAlignment().getHpos(), control.getAlignment().getVpos());
    }

    @Override
    public void dispose() {
        label.ellipsisStringProperty().unbind();
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

        fillRegion.widthProperty().removeListener(animChangeListener);
        fillRegion.heightProperty().removeListener(animChangeListener);
        control.fillAnimationProperty().removeListener(fillAnimationChangeListener);
        control.hoverTextFillProperty().removeListener(animChangeListener);
        control.textFillProperty().removeListener(animChangeListener);
        control.animationTimeProperty().removeListener(animChangeListener);
        control.removeEventFilter(MouseEvent.MOUSE_ENTERED, enterHandler);
        control.removeEventFilter(MouseEvent.MOUSE_EXITED, exitHandler);
        control.removeEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
        if (animEnter != null) {
            animEnter.stop();
            animEnter = null;
        }
        if (animExit != null) {
            animExit.stop();
            animExit = null;
        }
        getChildren().clear();
        super.dispose();
    }

    public Region getFillRegion() {
        return fillRegion;
    }

    public Label getLabel() {
        return label;
    }

    public Timeline getAnimEnter() {
        return animEnter;
    }

    public Timeline getAnimExit() {
        return animExit;
    }

}
