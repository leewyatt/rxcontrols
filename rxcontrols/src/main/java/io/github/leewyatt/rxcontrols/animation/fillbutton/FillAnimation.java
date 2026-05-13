package io.github.leewyatt.rxcontrols.animation.fillbutton;

import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;

/**
 */
public interface FillAnimation {

    /**
     * 首次调用时做初始化准备工作
     * @param skin
     */
    public void init(RXFillButtonSkin skin);


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
     * 注意和轮播图的不同点;
     * 轮播图 考虑到很多效果可能要重复使用, 比如新建一个集合 每次随机抽取一个效果,作为轮播图下一次播放的效果;
     * 所以dispose 需要自己手动调用, 来销毁
     *
     * 而Button/Labeled的动画效果,考虑不会变来变去的,一半只会在首次更改按钮效果. 所以在skin代码中进行了调用
     * 如果下次还需要这个效果, 那么还是需要再次创建 new
     */
    public void dispose();
}
