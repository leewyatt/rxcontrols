package io.github.leewyatt.rxcontrols.animation.fillbutton;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 */
public class FillAnimTopToBottom implements FillAnimation {
    private Rectangle rectClip;
    private SimpleDoubleProperty rectHeightPro;

    private RXFillButton control;
    private Region region;
    private Label label;
    private Timeline animEnter;
    private Timeline animExit;
    @Override
    public void init(RXFillButtonSkin skin) {
        control = skin.getSkinnable();
        region = skin.getFillRegion();
        label = skin.getLabel();
        animEnter = skin.getAnimEnter();
        animExit = skin.getAnimExit();

        rectHeightPro = new SimpleDoubleProperty(0);
        rectClip = new Rectangle();
        rectClip.widthProperty().bind(region.widthProperty());
        rectClip.heightProperty().bind(rectHeightPro);
        region.setClip(rectClip);

        initEnterAnim();
        initExitAnim();
    }

    @Override
    public void initEnterAnim() {
        if (control.isHover()) {
            if (animEnter.getStatus() == Animation.Status.RUNNING) {
                animEnter.stop();
            }
            rectHeightPro.set(region.getHeight());
            label.setTextFill(control.getHoverTextFill());
        }
        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectHeightPro, region.getHeight()),
                        new KeyValue(label.textFillProperty(), control.getHoverTextFill()))
        );

    }

    @Override
    public void initExitAnim() {
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }
            rectHeightPro.set(0);
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectHeightPro, 0),
                        new KeyValue(label.textFillProperty(), control.getTextFill()))
        );

    }

    @Override
    public void dispose() {
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        region.setClip(null);
        FillAnimationUtil.disposeRectangle(rectClip);
        rectHeightPro = null;
        System.gc();

    }
}
