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
package com.leewyatt.rxcontrols.pane;

import com.leewyatt.rxcontrols.event.RXCarouselEvent;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

/**
 *
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 *
 * 继承自BorderPane
 * 1: 主要作用是添加事件属性ObjectProperty<EventHandler<RXCarouselEvent>>
 *      动画中就可以控制 比如正在打开事件,已经打开事件,正在关闭事件和已经关闭事件
 *      方便在打开和关闭的过程中,呈现不同的处理方案(动画)
 * 2: 其他作用,记录描述文字或者节点,方便在导航条里进行显示
 */
public class RXCarouselPane extends BorderPane {
    /**
     * 描述文字
     */
    private SimpleStringProperty text;

    public String getText() {
        return text==null?"":textProperty().get();
    }

    public SimpleStringProperty textProperty() {
        if(text==null){
            text = new SimpleStringProperty();
        }
        return text;
    }

    public RXCarouselPane() {
        super();
        setPrefSize(300, 200);
    }

    public RXCarouselPane(Node center) {
        super(center);
        setPrefSize(300, 200);
    }

    public RXCarouselPane(Node center, Node top, Node right, Node bottom, Node left) {
        super(center, top, right, bottom, left);
        setPrefSize(300, 200);
    }

    public void setContent(Node content){
        getChildren().setAll(content);
    }

    public EventHandler<RXCarouselEvent> getOnClosed() {
        return onClosedProperty().get();
    }

    public ObjectProperty<EventHandler<RXCarouselEvent>> onClosedProperty() {
        return onClosed;
    }

    public void setOnClosed(EventHandler<RXCarouselEvent> onClosed) {
        onClosedProperty().set(onClosed);
    }

    private ObjectProperty<EventHandler<RXCarouselEvent>> onClosed = new ObjectPropertyBase<EventHandler<RXCarouselEvent>>() {
        @Override
        protected void invalidated() {
            setEventHandler(RXCarouselEvent.CLOSED, get());
        }

        @Override
        public Object getBean() {
            return RXCarouselPane.this;
        }

        @Override
        public String getName() {
            return "onClosed";
        }
    };


    public EventHandler<RXCarouselEvent> getOnClosing() {
        return onClosing.get();
    }

    public ObjectProperty<EventHandler<RXCarouselEvent>> onClosingProperty() {
        return onClosing;
    }

    public void setOnClosing(EventHandler<RXCarouselEvent> onClosing) {
        this.onClosing.set(onClosing);
    }

    private ObjectProperty<EventHandler<RXCarouselEvent>> onClosing = new ObjectPropertyBase<EventHandler<RXCarouselEvent>>() {
        @Override
        protected void invalidated() {
            setEventHandler(RXCarouselEvent.CLOSING, get());
        }

        @Override
        public Object getBean() {
            return RXCarouselPane.this;
        }

        @Override
        public String getName() {
            return "onClosing";
        }
    };


    public EventHandler<RXCarouselEvent> getOnOpened() {
        return onOpened.get();
    }

    public ObjectProperty<EventHandler<RXCarouselEvent>> onOpenedProperty() {
        return onOpened;
    }

    public void setOnOpened(EventHandler<RXCarouselEvent> onOpened) {
        this.onOpened.set(onOpened);
    }

    private ObjectProperty<EventHandler<RXCarouselEvent>> onOpened = new ObjectPropertyBase<EventHandler<RXCarouselEvent>>() {
        @Override
        protected void invalidated() {
            setEventHandler(RXCarouselEvent.OPENED, get());
        }

        @Override
        public Object getBean() {
            return RXCarouselPane.this;
        }

        @Override
        public String getName() {
            return "onOpened";
        }
    };


    public EventHandler<RXCarouselEvent> getOnOpening() {
        return onOpening.get();
    }

    public ObjectProperty<EventHandler<RXCarouselEvent>> onOpeningProperty() {
        return onOpening;
    }

    public void setOnOpening(EventHandler<RXCarouselEvent> onOpening) {
        this.onOpening.set(onOpening);
    }

    private ObjectProperty<EventHandler<RXCarouselEvent>> onOpening = new ObjectPropertyBase<EventHandler<RXCarouselEvent>>() {
        @Override
        protected void invalidated() {
            setEventHandler(RXCarouselEvent.OPENING, get());
        }

        @Override
        public Object getBean() {
            return RXCarouselPane.this;
        }

        @Override
        public String getName() {
            return "onOpening";
        }
    };

    //    public static final RXCarouselEvent CLOSING_EVENT = new RXCarouselEvent(RXCarouselEvent.CLOSING);
    public  void fireClosing(){
        fireEvent(new RXCarouselEvent(RXCarouselEvent.CLOSING));
    }

    //    private static final RXCarouselEvent OPENING_EVENT = new RXCarouselEvent(RXCarouselEvent.OPENING);
    public void fireOpening(){
        fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENING));
    }

    //    private static final RXCarouselEvent CLOSED_EVENT = new RXCarouselEvent(RXCarouselEvent.CLOSED);
    public  void fireClosed(){
       fireEvent(new RXCarouselEvent(RXCarouselEvent.CLOSED));
    }

    //    private static final RXCarouselEvent OPENED_EVENT = new RXCarouselEvent(RXCarouselEvent.OPENED);
    public void fireOpened(){
        fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENED));
    }
}
