package io.github.leewyatt.rxcontrols.animation.lineButton;

import io.github.leewyatt.rxcontrols.animation.fillbutton.FillAnimationUtil;
import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.skins.RXLineButtonSkin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.shape.Line;

/**
 *
 * 效果: 线条上升下降
 */
public class LineAnimRise implements LineAnimation{
    private RXLineButton control;
    private Timeline animEnter;
    private Timeline animExit;
    private Line line;
    @Override
    public void init(RXLineButtonSkin skin) {
        control = skin.getSkinnable();
        animEnter = skin.getAnimEnter();
        animExit = skin.getAnimExit();
        line = skin.getLine();
        line.setOpacity(0.0);
        line.setScaleX(1.0);
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
            line.setTranslateY(0);
            line.setOpacity(1.0);
        }
        animEnter.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(line.translateYProperty(), 0),
                        new KeyValue(line.opacityProperty(), 1.0))
        );
    }

    @Override
    public void initExitAnim() {
        //如果重置退出动画时,鼠标不在此处, 那么调整文本的颜色与矩形位置
        if (!control.isHover()) {
            if (animExit.getStatus() == Animation.Status.RUNNING) {
                animExit.stop();
            }

            line.setTranslateY(control.getOffsetYPro());
            line.setOpacity(0.0);
        }
        animExit.getKeyFrames().setAll(
                new KeyFrame(control.getAnimationTime(),
                        new KeyValue(line.translateYProperty(), control.getOffsetYPro()),
                        new KeyValue(line.opacityProperty(), 0.0))
        );
    }

    @Override
    public void dispose() {
        FillAnimationUtil.stopAnimation(animEnter, animExit);
        line.setTranslateY(0);
    }


}
