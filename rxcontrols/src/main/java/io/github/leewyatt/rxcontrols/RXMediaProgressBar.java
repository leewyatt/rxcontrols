package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXMediaProgressBarSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

/**
 *
 * 多媒体(音频,视频)进度条
 * 有底层轨道, 缓冲轨道, 播放进度轨道
 */
public class RXMediaProgressBar extends Control {
    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-media-progress-bar";

    public RXMediaProgressBar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
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
