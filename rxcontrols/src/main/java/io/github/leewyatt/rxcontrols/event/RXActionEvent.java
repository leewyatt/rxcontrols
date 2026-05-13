package io.github.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
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
