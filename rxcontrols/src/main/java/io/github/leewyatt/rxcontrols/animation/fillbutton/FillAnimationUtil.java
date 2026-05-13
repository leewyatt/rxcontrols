package io.github.leewyatt.rxcontrols.animation.fillbutton;

import javafx.animation.Animation;
import javafx.scene.shape.Rectangle;

/**
 */
public class FillAnimationUtil {
    /**
     * 设置矩形大小
     */
    public static void setSize(Double width, Double height, Rectangle... rects) {
        for (Rectangle rect : rects) {
            if (width != null) {
                rect.setWidth(width);
            }
            if (height != null) {
                rect.setHeight(height);
            }
        }
    }

    /**
     * 停止动画
     *
     * @param animations 需要停止的动画
     */
    public static void stopAnimation(Animation... animations) {
        for (Animation animation : animations) {
            if (animation != null && animation.getStatus() != Animation.Status.STOPPED) {
                animation.stop();
            }
        }
    }


    /**
     * 解除宽高位移绑定,并且设置为null,
     */
    public static void disposeRectangle(Rectangle... rects) {
        for (Rectangle rect : rects) {
            if (rect != null) {
                if(rect.translateYProperty().isBound()){
                    rect.translateYProperty().unbind();
                }
                if (rect.translateXProperty().isBound()) {
                    rect.translateXProperty().unbind();
                }
                if (rect.heightProperty().isBound()) {
                    rect.heightProperty().unbind();
                }
                if (rect.widthProperty().isBound()) {
                    rect.widthProperty().unbind();
                }
                rect = null;
            }
        }

    }

}
