package io.github.leewyatt.rxcontrols.animation.fillbutton;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 */
public class FillAnimHorToMiddle implements FillAnimation {

    private Rectangle rectClip1;
    private Rectangle rectClip2;
    private SimpleDoubleProperty rectWidthPro;

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

        rectClip1 = new Rectangle();
        rectClip2 = new Rectangle();
        rectWidthPro = new SimpleDoubleProperty(0);

        region.clipProperty().bind(Bindings.createObjectBinding(
                () -> {
                    double rectW = rectWidthPro.get();
                    double regionH = region.getHeight();
                    double regionW = region.getWidth();
                    FillAnimationUtil.setSize(rectW, regionH, rectClip1, rectClip2);
                    rectClip2.setTranslateX(regionW - rectW);
                    return Shape.union(rectClip1, rectClip2);
                }, rectWidthPro, region.widthProperty(), region.heightProperty()));

        initEnterAnim();
        initExitAnim();
    }

    @Override
    public void initEnterAnim() {
        //如果重置动画时,鼠标处于组件上方,那么先停止动画, 并且调整此矩形,以及文字颜色
        if (control.isHover()) {
            if (animEnter.getStatus() == Animation.Status.RUNNING) {
                animEnter.stop();
            }
            rectWidthPro.set(region.getWidth() / 2.0);
            label.setTextFill(control.getHoverTextFill());
        }
        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectWidthPro, region.getWidth() / 2.0),
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
            rectWidthPro.set(0);
            label.setTextFill(control.getTextFill());
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(rectWidthPro, 0),
                        new KeyValue(label.textFillProperty(), control.getTextFill()))
        );
    }

    @Override
    public void dispose() {
        //动画停止
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        //------解除clip绑定-----
        region.clipProperty().unbind();
        region.setClip(null);
        //--------解除长宽与位移的绑定并且销毁---------
        FillAnimationUtil.disposeRectangle(rectClip1, rectClip2);
        rectWidthPro = null;
        System.gc();

    }
}
