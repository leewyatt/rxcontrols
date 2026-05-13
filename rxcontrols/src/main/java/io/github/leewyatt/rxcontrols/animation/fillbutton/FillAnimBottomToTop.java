package io.github.leewyatt.rxcontrols.animation.fillbutton;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 */
public class FillAnimBottomToTop implements FillAnimation {
    private Rectangle rectClip;
    private RXFillButton control;
    private Region region;
    private Label label;
    private Timeline animEnter;
    private Timeline animExit;

    public FillAnimBottomToTop() {
    }

    @Override
    public void init(RXFillButtonSkin skin) {
        control = skin.getSkinnable();
        region = skin.getFillRegion();
        label = skin.getLabel();
        animEnter = skin.getAnimEnter();
        animExit = skin.getAnimExit();

        rectClip = new Rectangle();
        rectClip.widthProperty().bind(region.widthProperty());
        rectClip.heightProperty().bind(region.heightProperty());
        rectClip.setTranslateY(region.getHeight());
        region.setClip(rectClip);

        //初始化动画
        initEnterAnim();
        initExitAnim();
    }

    @Override
    public void initEnterAnim() {
        //如果重置动画时,鼠标处于组件上方,那么先停止动画, 并且调整此矩形位置,以及文字颜色
        if (control.isHover()) {
            if (animEnter.getStatus() == Animation.Status.RUNNING) {
                animEnter.stop();
            }
            rectClip.setTranslateY(0);
            label.setTextFill(control.getHoverTextFill());
        }
        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.translateYProperty(), 0),
                        new KeyValue(label.textFillProperty(), control.getHoverTextFill()))
        );
    }

    @Override
    public void initExitAnim() {
        //如果重置退出动画时,鼠标不在此处, 那么调整文本的颜色与矩形位置
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }
            rectClip.setTranslateY(region.getHeight());
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectClip.translateYProperty(), region.getHeight()),
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
