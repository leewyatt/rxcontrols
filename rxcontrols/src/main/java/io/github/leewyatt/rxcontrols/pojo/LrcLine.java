package io.github.leewyatt.rxcontrols.pojo;

import io.github.leewyatt.rxcontrols.utils.LrcUtil;

import java.util.Objects;

/**
 *
 * 一条歌词
 */
public class LrcLine implements Comparable<LrcLine>{

    /**
     * Lrc 歌词里的 时间格式
     * 例如:  [00:01.5]
     */
    public String timeTag;

    /**
     * Lrc 歌词出现的时间 (毫秒数)
     * 1005
     */
    public long time;

    /**
     * Lrc 里的一句歌词
     */
    public String words;

    public LrcLine() {
    }

    public LrcLine(String timeTag, String words) {
        this.timeTag = timeTag;
        this.time = LrcUtil.convertToTime(timeTag);
        this.words = words;
    }

    public LrcLine(long time, String words) {
        this.time = time;
        this.timeTag = LrcUtil.convertToTag(time);
        this.words = words;
    }

    public long getTime() {
        return time;
    }

    /**
     * 如果改变了 long time ,那么 timeTag也需要改变
     * @param time
     */
    public void setTime(long time) {
        this.time = time;
        this.timeTag = LrcUtil.convertToTag(time);
    }

    public String getTimeTag() {
        return timeTag;
    }

    /**
     * 如果修改了timeTag 那么 time也会变化
     * @param timeTag
     */
    public void setTimeTag(String timeTag) {
        this.timeTag = timeTag;
        this.time = LrcUtil.convertToTime(timeTag);
    }

    public String getWords() {
        return words;
    }

    public void setWords(String words) {
        this.words = words;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LrcLine lrcLine = (LrcLine) o;
        return time == lrcLine.time &&
                Objects.equals(timeTag, lrcLine.timeTag) &&
                Objects.equals(words, lrcLine.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeTag, time, words);
    }

    @Override
    public String toString() {
        return "LrcLine{" +
                "lrcTime='" + timeTag + '\'' +
                ", time=" + time +
                ", words='" + words + '\'' +
                '}';
    }

    @Override
    public int compareTo(LrcLine lrcLine) {
        return Long.compare(this.time, lrcLine.time);
    }
}
