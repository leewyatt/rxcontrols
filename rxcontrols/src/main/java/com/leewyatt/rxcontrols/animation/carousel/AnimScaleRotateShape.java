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
package com.leewyatt.rxcontrols.animation.carousel;

import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class AnimScaleRotateShape extends CarouselAnimationBase {
    private Shape shape;
    private Timeline animation;
    private double scaleXFrom;
    private double scaleXTo;
    private double scaleYFrom;
    private double scaleYTo;
    private double rotateFrom;
    private double rotateTo;
    private Point3D rotationAxis;
    private SVGPath path;


    public AnimScaleRotateShape() {
        this(null, 1, 100, 1, 100, 0, 0, Rotate.Z_AXIS);
    }


    public AnimScaleRotateShape(Shape shape, double scaleFrom, double scaleTo){
        this(shape, scaleFrom, scaleTo,scaleFrom, scaleTo,0, 0, Rotate.Z_AXIS);
    }


    public AnimScaleRotateShape(Shape shape, double scaleFrom, double scaleTo, double rotateFrom, double rotateTo, Point3D rotationAxis){
        this(shape, scaleFrom, scaleTo,scaleFrom, scaleTo,rotateFrom, rotateTo, Rotate.Z_AXIS);
    }

    public AnimScaleRotateShape(Shape shape, double scaleXFrom, double scaleXTo, double scaleYFrom, double scaleYTo, double rotateFrom, double rotateTo, Point3D rotationAxis) {
        setShape(shape);
        setScaleXFrom(scaleXFrom);
        setScaleXTo(scaleXTo);
        setScaleYFrom(scaleYFrom);
        setScaleYTo(scaleYTo);
        setRotateFrom(rotateFrom);
        setRotateTo(rotateTo);
        this.rotationAxis = rotationAxis;
        animation = new Timeline();
    }

    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);
        double x = CarouselAnimUtil.getPaneWidth(contentPane)/2;
        double y=CarouselAnimUtil.getPaneHeight(contentPane)/2;
        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        shape.setRotationAxis(rotationAxis);
        if(shape==path&&shape.getTranslateX()==0.0&&shape.getTranslateY()==0.0){
            shape.setTranslateX(x);
            shape.setTranslateY(y);
        }
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                            if (direction) {
                                nextPane.setClip(shape);
                                nextPane.toFront();
                            } else {
                                currentPane.setClip(shape);
                                currentPane.toFront();
                            }
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(shape.scaleXProperty(), direction ? scaleXFrom : scaleXTo),
                        new KeyValue(shape.scaleYProperty(), direction ? scaleYFrom : scaleYTo),
                        new KeyValue(shape.rotateProperty(), direction ? rotateFrom : rotateTo)),
                new KeyFrame(animationTime,
                        new KeyValue(shape.scaleXProperty(), direction ? scaleXTo : scaleXFrom),
                        new KeyValue(shape.scaleYProperty(), direction ? scaleYTo : scaleYFrom),
                        new KeyValue(shape.rotateProperty(), direction ? rotateTo : rotateFrom)
                ));
        animation.setOnFinished(e -> {
            nextPane.setClip(null);
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.setClip(null);
            currentPane.setRotationAxis(Rotate.Z_AXIS);
            currentPane.fireClosed();
            resetNode();
        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    private void reset(List<RXCarouselPane> panes, int... showIndexAry) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showIndexAry);
            pane.setClip(null);
        }
        resetNode();
    }

    private void resetNode(){
        shape.setScaleX(scaleXFrom);
        shape.setScaleY(scaleYFrom);
        shape.setRotate(rotateFrom);
        shape.setRotationAxis(rotationAxis);
    }

    private Shape getDefaultShape() {
        if (path == null) {
            path = new SVGPath();
            path.setContent("M14,6L9.61,5.2L7.5,1.3L5.4,5.2L1,6l3,3.3l-0.6,4.4l4-1.9l4,1.9L11,9.29L14,6z");
        }
        return path;
    }

    public double getScaleYFrom() {
        return scaleYFrom;
    }

    public void setScaleYFrom(double scaleYFrom) {
        this.scaleYFrom = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, scaleYFrom);
    }

    public double getScaleYTo() {
        return scaleYTo;
    }

    public void setScaleYTo(double scaleYTo) {
        this.scaleYTo = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, scaleYTo);
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        if (shape == null) {
            this.shape = getDefaultShape();
        } else {
            this.shape = shape;
        }
    }

    public double getScaleXFrom() {
        return scaleXFrom;
    }

    public void setScaleXFrom(double scaleXFrom) {
        this.scaleXFrom = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, scaleXFrom);
    }

    public double getScaleXTo() {
        return scaleXTo;
    }

    public void setScaleXTo(double scaleXTo) {
        this.scaleXTo = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, scaleXTo);
    }

    public double getRotateFrom() {
        return rotateFrom;
    }

    public void setRotateFrom(double rotateFrom) {
        this.rotateFrom = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, rotateFrom);
    }

    public double getRotateTo() {
        return rotateTo;
    }

    public void setRotateTo(double rotateTo) {
        this.rotateTo = CarouselAnimUtil.clamp(0, Double.MAX_VALUE, rotateTo);
        ;
    }

    public Point3D getRotationAxis() {
        return rotationAxis;
    }

    public void setRotationAxis(Point3D rotationAxis) {
        this.rotationAxis = rotationAxis;
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
