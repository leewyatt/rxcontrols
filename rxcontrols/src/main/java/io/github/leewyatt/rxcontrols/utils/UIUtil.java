package io.github.leewyatt.rxcontrols.utils;

import javafx.animation.Animation;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Control;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
/**
 *
 */
public class UIUtil {


    /**
     * 获得组件的真实内部宽, 去掉了边框和内边距
     *
     * @param control 指定的组件
     */
    public static double computeInnerW(Control control) {
        if (control == null) {
            return 0;
        }
        double width = 0;
        Bounds bounds = control.getLayoutBounds();
        if (bounds != null) {
            width = bounds.getMaxX() - bounds.getMinX();
        }
        double paddingWidth = 0;
        Insets padding = control.getPadding();
        if (padding != null) {
            paddingWidth = padding.getLeft() + padding.getRight();
        }
        double borderWidth = 0;
        if (control.getBorder() != null && control.getBorder().getInsets() != null) {
            Insets insets = control.getBorder().getInsets();
            borderWidth = insets.getLeft() + insets.getRight();
        }
        return width - borderWidth - paddingWidth;
    }

    /**
     * 获得组件的真实内部高, 去掉了边框和内边距
     *
     * @param control 指定的组件
     */
    public static double computeInnerH(Control control) {
        if (control == null) {
            return 0;
        }
        double height = 0;
        Bounds bounds = control.getLayoutBounds();
        if (bounds != null) {
            height = bounds.getMaxY() - bounds.getMinY();
        }
        double borderHeight = computeBorderSize(control, true, false, true, false);
        double paddingHeight = computePaddingSize(control, true, false, true, false);
        return height - borderHeight - paddingHeight;
    }

    /**
     * 获得组件的内边距长度
     *
     * @param control
     * @param top     上边距
     * @param right   右边距
     * @param bottom  下边距
     * @param left    左边距
     * @return
     */
    public static double computePaddingSize(Control control, boolean top, boolean right, boolean bottom, boolean left) {
        double paddingSize = 0;
        Insets insets = control.getPadding();
        if (control == null || insets == null) {
            return paddingSize;
        }
        return getSize(top, right, bottom, left, insets);
    }


    /**
     * 获取组件的边框大小
     * @param control
     * @param top 上边框
     * @param right 有边框
     * @param bottom 下边框
     * @param left 左边框
     * @return
     */
    public static double computeBorderSize(Control control, boolean top, boolean right, boolean bottom, boolean left) {
        double borderSize = 0;
        if (control == null || control.getBorder() == null || control.getBorder().getInsets() == null) {
            return borderSize;
        }
        Insets insets = control.getBorder().getInsets();
        return getSize(top, right, bottom, left, insets);
    }


    private static double getSize(boolean top, boolean right, boolean bottom, boolean left, Insets insets) {
        double size = 0;
        if (top) {
            size += insets.getTop();
        }
        if (right) {
            size += insets.getRight();
        }
        if (bottom) {
            size += insets.getBottom();
        }
        if (left) {
            size += insets.getLeft();
        }
        return size;
    }

    /**
     * 根据组件的 位置, 边框, 内边距来计算根节点内容面积的大小
     * @param control 组件
     * @param root 根节点
     */
    public static void clipRoot(Control control, Pane root) {
        Rectangle rect = new Rectangle();
        rect.widthProperty().bind(Bindings.createDoubleBinding(
                () -> UIUtil.computeInnerW(control),
                control.boundsInParentProperty(),
                control.borderProperty(),
                control.paddingProperty(),
                control.widthProperty()));

        rect.heightProperty().bind(Bindings.createDoubleBinding(
                () -> UIUtil.computeInnerH(control),
                control.boundsInParentProperty(),
                control.borderProperty(),
                control.paddingProperty(),
                control.heightProperty()));
        root.setClip(rect);
    }

    /**
     * 动画跳转到最后面然后停止.
     * @param animations 动画
     */
    public static void animeStopAtEnd(Animation... animations){
        if(animations==null||animations.length==0){
            return;
        }
        for (Animation animation : animations) {
            if(animation==null){
                continue;
            }
            if(animation.getStatus()!= Animation.Status.STOPPED){
                animation.jumpTo(animation.getTotalDuration());
                animation.stop();
            }
        }
    }


    public static void animeStop(Animation... animations){
        if(animations==null||animations.length==0){
            return;
        }
        for (Animation animation : animations) {
            if (animation == null) {
                continue;
            }
            animation.stop();
        }
    }
}
