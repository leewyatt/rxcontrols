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
package com.leewyatt.rxcontrols.animation.fillbutton;

import com.leewyatt.rxcontrols.skins.RXFillButtonSkin;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
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
