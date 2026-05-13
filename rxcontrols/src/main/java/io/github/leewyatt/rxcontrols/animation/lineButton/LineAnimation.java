package io.github.leewyatt.rxcontrols.animation.lineButton;

import io.github.leewyatt.rxcontrols.skins.RXLineButtonSkin;

/**
 */
public interface LineAnimation {
    /**
     * 首次调用时做初始化准备工作
     * @param skin
     */
    public void init(RXLineButtonSkin skin);


    /**
     * 初始化鼠标移入动画:
     */
    public void initEnterAnim();

    /**
     * 初始化鼠标移除动画:
     */
    public void initExitAnim();

    /**
     * 切换其他动画效果时的清理工作
     */
    public void dispose();
}
