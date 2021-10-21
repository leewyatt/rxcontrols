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
package com.leewyatt.rxcontrols.controls;

import com.leewyatt.rxcontrols.utils.RXResources;
import com.leewyatt.rxcontrols.skins.RXMediaProgressBarSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 多媒体(音频,视频)进度条
 * 有底层轨道, 缓冲轨道, 播放进度轨道
 */
public class RXMediaProgressBar extends Control {
    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-media-progress-bar";
    /**
     * 默认外观css
     */
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();


    public RXMediaProgressBar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMediaProgressBarSkin(this);
    }


    /**
     * 当前播放到的时间
     */
    private  ObjectProperty<Duration> currentTime;
    public final ObjectProperty<Duration> currentTimeProperty() {
        if (currentTime == null) {
            currentTime = new SimpleObjectProperty<>(this, "currentTime");
        }
        return currentTime;
    }
    public final Duration getCurrentTime() {
        return currentTime==null? Duration.ZERO:currentTime.get();
    }
    public final void setCurrentTime(Duration value) {
        currentTimeProperty().set(value);
    }



    /**
     * 缓冲的进度
     */
    private  ObjectProperty<Duration> bufferProgressTime;
    public final ObjectProperty<Duration> bufferProgressTimeProperty() {
        if (bufferProgressTime == null) {
            bufferProgressTime = new SimpleObjectProperty<>(this, "bufferProgressTime");
        }
        return bufferProgressTime;
    }
    public final Duration getBufferProgressTime() {
        return bufferProgressTime == null? Duration.ZERO:bufferProgressTime.get();
    }
    public final void setBufferProgressTime(Duration value) {
        bufferProgressTimeProperty().set(value);
    }



    /**
     * 媒体文件的总时长 (不计算重复次数)
     */
    private ObjectProperty<Duration> duration;
    public final ObjectProperty<Duration> durationProperty() {
        if (duration == null) {
            duration = new SimpleObjectProperty<>(this, "duration");
        }
        return duration;
    }
    public final Duration getDuration() {
        return duration == null? Duration.ZERO: duration.get();
    }

    public final void setDuration(Duration value) {
        durationProperty().set(value);
    }

}
