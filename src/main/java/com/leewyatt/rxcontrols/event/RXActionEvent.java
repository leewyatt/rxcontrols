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
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 主要是为了和 ActionEvent 进行区分!
 * 比如文本框按回车键就会触发ActionEvent;
 * 我又写了一个按钮在文本框里, 点击按钮并不是想触发自带的ActionEvent事件.而是比如复制删除等自定义事件
 *
 * 注意, onClickButton触发的事件,用SceneBuilder自动生成的代码依然写的是 ActionEvent;
 * 需要手动修改成RXActionEvent
 */
public class RXActionEvent extends Event {

    private static final long serialVersionUID = 1L;

    public static final EventType<RXActionEvent> RXACTION =
            new EventType<RXActionEvent>(Event.ANY, "RXACTION");

    public static final EventType<RXActionEvent> ANY = RXACTION;


    public RXActionEvent() {
        super(RXACTION);
    }


    public RXActionEvent(Object source, EventTarget target) {
        super(source, target, RXACTION);
    }

    @Override
    public RXActionEvent copyFor(Object newSource, EventTarget newTarget) {
        return (RXActionEvent) super.copyFor(newSource, newTarget);
    }

    @Override
    public EventType<? extends RXActionEvent> getEventType() {
        return (EventType<? extends RXActionEvent>) super.getEventType();
    }
}
