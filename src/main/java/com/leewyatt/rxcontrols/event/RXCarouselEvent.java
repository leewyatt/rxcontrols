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
package com.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 自定义事件,主要在进行轮播图动画的时候,在需要的地方 fireEvent 即可调用
 * 比如正在播放视频的页面,当CLOSED 事件发生时, 那么可以监听关闭事件,然后对MediaPlayer进行一个关闭操作
 *
 */
public class RXCarouselEvent extends Event {
    private static final long serialVersionUID = 1L;

    public RXCarouselEvent(EventType<? extends Event> eventType) {
        super(eventType);
    }

    /**
     * 已经关闭
     */
    public static final EventType<RXCarouselEvent> CLOSED =
            new EventType<>(Event.ANY, "RX_CLOSED");

    /**
     *已经打开
     */
    public static final EventType<RXCarouselEvent> OPENED =
            new EventType<>(Event.ANY, "RX_OPENED");

    /**
     * 即将打开
     */
    public static final EventType<RXCarouselEvent> OPENING =
            new EventType<>(Event.ANY, "RX_OPENING");

    /**
     * 即将关闭
     */
    public static final EventType<RXCarouselEvent> CLOSING =
            new EventType<>(Event.ANY, "RX_CLOSING");

}
