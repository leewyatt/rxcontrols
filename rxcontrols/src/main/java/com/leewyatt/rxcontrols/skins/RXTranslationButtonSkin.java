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

import com.leewyatt.rxcontrols.controls.RXTranslationButton;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 *
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class RXTranslationButtonSkin extends SkinBase<RXTranslationButton> {
    private RXTranslationButton control;
    private Timeline animEnter;
    private Timeline animExit;
    private StackPane rootPane;
    private Label hoverLabel;
    private Label nonHoverLabel;
    /**
     * 宽高发生改变,或者动画方向发生改变时 更新动画
     */
    private ChangeListener changeListener = (ob, ov, nv) -> updateAnim();
    private final EventHandler<MouseEvent> enterEventHandler;
    private final EventHandler<MouseEvent> mouseExitHandler;
    private final EventHandler<MouseEvent> mouseClickHandler;

    public RXTranslationButtonSkin(RXTranslationButton control) {
        super(control);
        this.control = control;
        hoverLabel = new Label();
        hoverLabel.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        hoverLabel.setAlignment(Pos.CENTER);
        hoverLabel.getStyleClass().add("hover-label");
        nonHoverLabel = new Label();
        nonHoverLabel.setAlignment(Pos.CENTER);
        nonHoverLabel.getStyleClass().add("non-hover-label");
        animEnter = new Timeline();
        animExit = new Timeline();
        rootPane = new StackPane();
        rootPane.getStyleClass().add("translation-pane");
        rootPane.getChildren().addAll(hoverLabel, nonHoverLabel);
        getChildren().addAll(rootPane);
        clipRegion(rootPane);
        hoverLabel.prefWidthProperty().bind(rootPane.widthProperty());
        hoverLabel.prefHeightProperty().bind(rootPane.heightProperty());
        nonHoverLabel.prefWidthProperty().bind(rootPane.widthProperty());
        nonHoverLabel.prefHeightProperty().bind(rootPane.heightProperty());
        //--------------non hover Label文字标签属性绑定--------------------------
        nonHoverLabel.ellipsisStringProperty().bind(control.ellipsisStringProperty());
        nonHoverLabel.textFillProperty().bind(control.textFillProperty());
        nonHoverLabel.fontProperty().bind(control.fontProperty());
        //注意这里的文字绑定
        nonHoverLabel.textProperty().bind(control.textProperty());
        nonHoverLabel.contentDisplayProperty().bind(control.contentDisplayProperty());
        nonHoverLabel.graphicTextGapProperty().bind(control.graphicTextGapProperty());
        nonHoverLabel.alignmentProperty().bind(control.alignmentProperty());
        nonHoverLabel.mnemonicParsingProperty().bind(control.mnemonicParsingProperty());
        nonHoverLabel.textAlignmentProperty().bind(control.textAlignmentProperty());
        nonHoverLabel.textOverrunProperty().bind(control.textOverrunProperty());
        nonHoverLabel.wrapTextProperty().bind(control.wrapTextProperty());
        nonHoverLabel.underlineProperty().bind(control.underlineProperty());
        nonHoverLabel.lineSpacingProperty().bind(control.lineSpacingProperty());

        //-------------HoverLabel-----------------
        hoverLabel.graphicProperty().bind(control.graphicProperty());

        //更新动画
        control.translationDirProperty().addListener(changeListener);
        rootPane.widthProperty().addListener(changeListener);
        rootPane.heightProperty().addListener(changeListener);

        //-------添加鼠标事件---------
        enterEventHandler = event -> playAnimEnter();
        control.addEventFilter(MouseEvent.MOUSE_ENTERED, enterEventHandler);
        mouseExitHandler = event -> playAnimExit();
        control.addEventFilter(MouseEvent.MOUSE_EXITED, mouseExitHandler);
        mouseClickHandler = event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                control.fireEvent(new ActionEvent());
            }
        };
        control.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseClickHandler);

    }

    private void playAnimEnter() {
        if (animExit.getStatus() == Animation.Status.RUNNING) {
            animExit.stop();
        }
        animEnter.play();
    }

    private void playAnimExit() {
        if (animEnter.getStatus() == Animation.Status.RUNNING) {
            animEnter.stop();
        }
        animExit.play();
    }

    private void updateAnim() {
        if (animEnter.getStatus() != Animation.Status.STOPPED) {
            animEnter.stop();
        }
        if (animExit.getStatus() != Animation.Status.STOPPED) {
            animExit.stop();
        }
        nonHoverLabel.setTranslateX(0);
        nonHoverLabel.setTranslateY(0);
        RXTranslationButton.TranslationDir dir = control.getTranslationDir();
        if (dir == RXTranslationButton.TranslationDir.BOTTOM_TO_TOP || dir == RXTranslationButton.TranslationDir.TOP_TO_BOTTOM) {
            boolean direction = dir == RXTranslationButton.TranslationDir.BOTTOM_TO_TOP ? true : false;
            double height = rootPane.getHeight();
            hoverLabel.setTranslateY(direction ? height : -height);
            hoverLabel.setTranslateX(0);
            animExit.getKeyFrames().setAll(
                    new KeyFrame(control.getAnimationTime(),
                            new KeyValue(nonHoverLabel.translateYProperty(), 0),
                            new KeyValue(hoverLabel.translateYProperty(), direction ? height : -height))
            );
            animEnter.getKeyFrames().setAll(
                    new KeyFrame(control.getAnimationTime(),
                            new KeyValue(nonHoverLabel.translateYProperty(), direction ? -height : height),
                            new KeyValue(hoverLabel.translateYProperty(), 0))
            );
            if (control.isHover()) {
                nonHoverLabel.setTranslateY(direction ? -height : height);
                hoverLabel.setTranslateY(0);
            } else {
                nonHoverLabel.setTranslateY(0);
                hoverLabel.setTranslateY(direction ? height : -height);
            }
        } else {
            boolean direction = dir == RXTranslationButton.TranslationDir.RIGHT_TO_LEFT ? true : false;
            double width = rootPane.getWidth();
            hoverLabel.setTranslateX(direction ? width : -width);
            hoverLabel.setTranslateY(0);

            animExit.getKeyFrames().setAll(
                    new KeyFrame(control.getAnimationTime(),
                            new KeyValue(nonHoverLabel.translateXProperty(), 0),
                            new KeyValue(hoverLabel.translateXProperty(), direction ? width : -width))
            );
            animEnter.getKeyFrames().setAll(
                    new KeyFrame(control.getAnimationTime(),
                            new KeyValue(nonHoverLabel.translateXProperty(), direction ? -width : width),
                            new KeyValue(hoverLabel.translateXProperty(), 0))
            );
            if (control.isHover()) {
                nonHoverLabel.setTranslateX(direction ? -width : width);
                hoverLabel.setTranslateX(0);
            } else {
                nonHoverLabel.setTranslateX(0);
                hoverLabel.setTranslateX(direction ? width : -width);
            }
        }
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
        layoutInArea(rootPane, contentX, contentY, contentWidth, contentHeight, 0, HPos.CENTER, VPos.CENTER);
//        layoutInArea(hoverLabel, contentX, contentY, contentWidth, contentHeight, 0,
//                control.getAlignment().getHpos(), control.getAlignment().getVpos());
//        layoutInArea(nonHoverLabel, contentX, contentY, contentWidth, contentHeight, 0,
//                nonHoverLabel.getAlignment().getHpos(),  nonHoverLabel.getAlignment().getVpos());
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

        //解绑
        rectClip.widthProperty().unbind();
        rectClip.heightProperty().unbind();
        hoverLabel.prefWidthProperty().unbind();
        hoverLabel.prefHeightProperty().unbind();
        nonHoverLabel.prefWidthProperty().unbind();
        nonHoverLabel.prefHeightProperty().unbind();
        nonHoverLabel.ellipsisStringProperty().unbind();
        nonHoverLabel.textFillProperty().unbind();
        nonHoverLabel.fontProperty().unbind();
        nonHoverLabel.textProperty().unbind();
        nonHoverLabel.contentDisplayProperty().unbind();
        nonHoverLabel.graphicTextGapProperty().unbind();
        nonHoverLabel.alignmentProperty().unbind();
        nonHoverLabel.mnemonicParsingProperty().unbind();
        nonHoverLabel.textAlignmentProperty().unbind();
        nonHoverLabel.textOverrunProperty().unbind();
        nonHoverLabel.wrapTextProperty().unbind();
        nonHoverLabel.underlineProperty().unbind();
        nonHoverLabel.lineSpacingProperty().unbind();
        hoverLabel.graphicProperty().unbind();

        //移除监听器
        control.translationDirProperty().removeListener(changeListener);
        rootPane.widthProperty().removeListener(changeListener);
        rootPane.heightProperty().removeListener(changeListener);
        //移除事件过滤
        control.removeEventFilter(MouseEvent.MOUSE_ENTERED, enterEventHandler);
        control.removeEventFilter(MouseEvent.MOUSE_EXITED, mouseExitHandler);
        control.removeEventFilter(MouseEvent.MOUSE_CLICKED, mouseClickHandler);
        rectClip=null;
        getChildren().clear();
        super.dispose();
    }

    public Label getHoverLabel() {
        return hoverLabel;
    }

    public Label getNonHoverLabel() {
        return nonHoverLabel;
    }

    private Rectangle rectClip = new Rectangle();

    /**
     * 裁切,隐藏掉超过组件内容大小的部分
     *
     * @param node
     */
    private void clipRegion(Node node) {
        rectClip.widthProperty().bind(rootPane.widthProperty());
        rectClip.heightProperty().bind(rootPane.heightProperty());
        node.setClip(rectClip);
    }
}
