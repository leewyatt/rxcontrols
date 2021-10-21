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
package com.leewyatt.rxcontrols.pojo;

import com.leewyatt.rxcontrols.utils.LrcUtil;

import java.util.Objects;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
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
