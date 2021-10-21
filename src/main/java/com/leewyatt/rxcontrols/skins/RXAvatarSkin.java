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

import com.leewyatt.rxcontrols.controls.RXAvatar;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * <p>
 * 下次更新思考: 支持更多的形状
 */
public class RXAvatarSkin extends SkinBase<RXAvatar> {
    private Group rootPane;

    private ImageView imageView;
    private RXAvatar avatar;

    private boolean emptyImage() {
        return avatar.getImage() == null;
    }

    private ChangeListener updateUiListener = (ob, ov, nv) -> {
        if (emptyImage()) {
            return;
        }
        double progress = avatar.getImage().getProgress();
        if (Double.compare(progress, 1.0) == 0) {
            clipImageView();
        }
    };

    private ChangeListener arcSizeListener = (ob, ov, nv) -> {
        if (emptyImage()) {
            return;
        }
        double progress = avatar.getImage().getProgress();
        if (Double.compare(progress, 1.0) == 0 && avatar.getShapeType() == RXAvatar.Type.SQUARE) {
            clipImageView();
        }
    };
    private ChangeListener<Number> progressChangeListener = (ob, ov, nv) -> {
        if (nv.intValue() == 1) {
            clipImageView();
        }
    };
    /**
     * 进度条的改变, 需要监听Image上;
     * 旧的Image上监听要移除,新Image的监听要添加
     */
    private ChangeListener<Image> imageChangeListener = (ob, ov, nv) -> {
        if (ov != null) {
            ov.progressProperty().removeListener(progressChangeListener);
        }
        if (nv != null) {
            nv.progressProperty().addListener(progressChangeListener);
        }
    };

    public RXAvatarSkin(RXAvatar avatar) {
        super(avatar);
        this.avatar = avatar;
        rootPane = new Group();
        rootPane.getStyleClass().add("group");
        imageView = new ImageView();
        imageView.getStyleClass().add("image-view");
        imageView.imageProperty().bind(avatar.imageProperty());
        imageView.setSmooth(true);
        imageView.setPreserveRatio(true);
        rootPane.getChildren().setAll(imageView);
        getChildren().setAll(rootPane);

        avatar.imageProperty().addListener(imageChangeListener);
        avatar.arcWidthProperty().addListener(arcSizeListener);
        avatar.arcHeightProperty().addListener(arcSizeListener);
        avatar.shapeTypeProperty().addListener(updateUiListener);
        avatar.prefWidthProperty().addListener(updateUiListener);
        avatar.prefHeightProperty().addListener(updateUiListener);
        if (avatar.getImage() != null) {
            avatar.getImage().progressProperty().addListener(progressChangeListener);
        }
        clipImageView();
    }

    private void clipImageView() {
        Image img = avatar.getImage();
        if (img == null || Double.compare(img.getProgress(), 1.0) != 0) {
            return;
        }
        // !!恢复图形的fitHeight和fitWidth
        imageView.setFitHeight(0);
        imageView.setFitWidth(0);

        double w = img.getWidth();
        double h = img.getHeight();

        double cx = 0, cy = 0;
        // 有的图片是竖图 , 有的图片是横图
        // 为了找到圆心, 那么根据宽和高比较小的一个来计算缩放比例
        double conW = avatar.getPrefWidth();
        double conH = avatar.getPrefHeight();
        double r = Math.min(conW, conH) / 2;
        if (Double.compare(w, h) > 0) {
            // 设置合适的高度 ,那么宽度也自动修改了
            imageView.setFitHeight(r * 2);
            // 计算比例
            double scale = (r * 2) / img.getHeight();
            imageView.setFitWidth(w * scale);
            // 计算出圆心
            cx = w * scale / 2 + imageView.getLayoutX();
            cy = imageView.getFitHeight() / 2 + imageView.getLayoutY();
        } else {
            imageView.setFitWidth(r * 2);
            double scale = (r * 2) / img.getWidth();
            imageView.setFitHeight(h * scale);
            cx = imageView.getFitWidth() / 2 + imageView.getLayoutX();
            cy = h * scale / 2 + imageView.getLayoutY();
        }

        RXAvatar.Type type = avatar.getShapeType();
        Shape clipShape = null;
        if (type == RXAvatar.Type.CIRCLE) {
            clipShape = new Circle(cx, cy, r);
            //如果是正方形头像
        } else if (type == RXAvatar.Type.SQUARE) {
            clipShape = new Rectangle(cx - r, cy - r, r * 2, r * 2);
            ((Rectangle) clipShape).setArcWidth(avatar.getArcWidth());
            ((Rectangle) clipShape).setArcHeight(avatar.getArcHeight());
            //如果是六边形头像HEXAGON_H
        } else if (type == RXAvatar.Type.HEXAGON_H) {
            double rw = 0;
            double rh = 0;
            double rate = 1.16;
            //isWideImage 用于判断图片是否是宽图
            boolean isWideImage = Double.compare(w / h, rate) == 1;
            // 组件太宽
            if (Double.compare(conW / conH, rate) > 0) {
                rh = conH / 2;
                rw = rh * rate;
                // 宽图
                if (isWideImage) {
                    imageView.setFitHeight(conH);
                    // 窄图.
                } else {
                    imageView.setFitWidth(conH * rate);
                }
                // 组件太窄
            } else {
                rw = conW / 2;
                rh = rw / rate;
                // 宽图
                if (isWideImage) {
                    imageView.setFitHeight(conW * rate);
                    // 窄图.
                } else {
                    imageView.setFitWidth(conW);
                }
            }
            // 计算出圆心
            cx = (imageView.prefWidth(-1)) / 2;
            cy = (imageView.prefHeight(-1)) / 2;

            clipShape = new Polygon(cx - rw / 2, cy - rh, cx + rw / 2, cy - rh, cx + rw, cy, cx + rw / 2, cy + rh,
                    cx - rw / 2, cy + rh, cx - rw, cy);
            //如果是六边形头像HEXAGON_V
        } else {
            double rw = 0;
            double rh = 0;
            double rate = 0.871;
            boolean isWideImage = Double.compare(w / h, rate) == 1;
            // 组件太宽
            if (Double.compare(conW / conH, rate) > 0) {
                rh = conH / 2;
                rw = rh * rate;
                // 宽图
                if (isWideImage) {
                    imageView.setFitHeight(rh * 2);
                    // 窄图.
                } else {
                    imageView.setFitWidth(rw * 2);
                }
                // 组件太窄
            } else {
                rw = conW / 2;
                rh = rw / rate;
                // 宽图
                if (isWideImage) {
                    imageView.setFitHeight(rh * 2);
                    // 窄图.
                } else {
                    imageView.setFitWidth(rw * 2);
                }
            }
            // 计算出圆心
            cx = (imageView.prefWidth(-1)) / 2;
            cy = (imageView.prefHeight(-1)) / 2;

            clipShape = new Polygon(cx, cy - rh, cx + rw, cy - rh / 2, cx + rw, cy + rh / 2,
                    cx, cy + rh, cx - rw, cy + rh / 2, cx - rw, cy - rh / 2);
        }
        imageView.setClip(clipShape);
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
        layoutInArea(rootPane, contentX, contentY, contentWidth, contentHeight, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    public void dispose() {
        if (avatar.getImage() != null) {
            avatar.getImage().progressProperty().removeListener(progressChangeListener);
        }
        avatar.imageProperty().addListener(imageChangeListener);
        avatar.arcWidthProperty().addListener(arcSizeListener);
        avatar.arcHeightProperty().addListener(arcSizeListener);
        avatar.shapeTypeProperty().addListener(updateUiListener);
        avatar.prefWidthProperty().addListener(updateUiListener);
        avatar.prefHeightProperty().addListener(updateUiListener);

        getChildren().clear();
        super.dispose();
    }

}
