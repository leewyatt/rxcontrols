package io.github.leewyatt.rxcontrols.animation.fillbutton;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 */
public class FillAnimVerToSide implements FillAnimation {
    private Rectangle rectClip;

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

        rectClip = new Rectangle();
        rectClip.widthProperty().bind(region.widthProperty());
        rectClip.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> (region.getHeight() - rectClip.getHeight()) / 2.0,
                rectClip.heightProperty(), region.heightProperty()));
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
            rectClip.setHeight(region.getHeight());
            label.setTextFill(control.getHoverTextFill());
        }

        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.heightProperty(), region.getHeight()),
                        new KeyValue(label.textFillProperty(), control.getHoverTextFill()))

        );
    }

    @Override
    public void initExitAnim() {
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }
            rectClip.setHeight(0);
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.heightProperty(), 0),
                        new KeyValue(label.textFillProperty(), control.getTextFill()))
        );
    }

    @Override
    public void dispose() {
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        region.setClip(null);
        FillAnimationUtil.disposeRectangle(rectClip);
        System.gc();
    }
}
