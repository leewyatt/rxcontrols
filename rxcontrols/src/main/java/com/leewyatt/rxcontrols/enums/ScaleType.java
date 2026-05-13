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
package com.leewyatt.rxcontrols.enums;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 早期版本轮播图允许不同尺寸的图片放入同一个轮播组件里; 按照指定的缩放方式进行缩放
 * 节点缩放类型: (参考安卓的图片缩放模式)
 *
 * 目前版本已经弃用; 也比较纠结, 可能以后会再次使用...
 *
 */
public enum ScaleType {
    /**
     * 非等比例缩放,填满 (默认状态)
     */
    FIT_XY,
    /**
     * 等比例缩放: 太大-->等比例缩放至能显示(高或宽比较长的一边可以完全放下); 太小直接显示
     */
    CENTER_INSIDE,
    /**
     * 等比例缩放: 太大-->等比例缩放至能显示(高或宽比较长的一边可以完全放下); 太小放大后显示
     */
    FIT_CENTER
}