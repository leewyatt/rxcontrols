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
package com.leewyatt.rxcontrols.animation.carousel;

import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.Animation;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public interface CarouselAnimation {
    /**
     * 初始化(当切换到这种动画效果时的准备工作);
     * 主要准备工作都在RXCarouselSkin里完成了,所以这里空实现, 在需要的时候才重写
     *
     * @param contentPane
     * @param effectPane
     * @param panes
     * @param nextIndex
     * @param currentIndex
     */
    public void initAnimation(StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex) ;

    /**
     * 获取动画
     *
     * @param rxCarousel     :轮播组件本身
     * @param contentPane    :内容层
     * @param effectPane     :辅助层
     * @param panes          :所有页面
     * @param currentIndex   :当前下标
     * @param nextIndex      :下一页下标
     * @param foreAndAftJump :是否是自动播放时的尾首跳转
     * @param animationTime  :动画需要的时间
     * @return
     */
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime);

    /**
     * 切换动画特效时前会调用, 清理之前动画造成的位移,变形等;
     * @param panes        :所有的页面
     * @param effectPane   : 辅助层,主要是有的特效需要截图快照等,添加到了该层
     * @param currentIndex :当前的下标
     * @param nextIndex    :下一页的下标
     */
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex);

    /**
     * 如果一个动画效果,不打算再使用, 那么手动调用dispose进行最后的收尾,清理工作,
     * 如解除绑定, 置空对象,加速内存释放;
     * 避免因为属性绑定等, 造成内存泄露
     *
     * 注意FillButton 的 FillAnimation的dispose方法, 是在skin皮肤里自动调用的!
     * 因为考虑到按钮的效果不会经常改变; dispose后,就不能再使用了,如果要使用, 还需要new
     */
    public  void dispose();
}
