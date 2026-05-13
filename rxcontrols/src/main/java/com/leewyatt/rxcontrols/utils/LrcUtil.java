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
package com.leewyatt.rxcontrols.utils;

import com.leewyatt.rxcontrols.pojo.LrcDoc;
import com.leewyatt.rxcontrols.pojo.LrcLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class LrcUtil {

    /**
     * 把毫秒数转成 Lrc歌词用的时间标签
     *
     * @param time 1005
     * @return [00:01.005]
     */
    public static String convertToTag(long time) {
        long m = time / 60 / 1000;
        return "[" +
                String.format("%02d", m) + ":" +
                String.format("%02d", (time - m * (1000 * 60)) / 1000) + "." +
                String.format("%03d", time % 1000) + "]";
    }

    /**
     * 把时间标签转成为毫秒
     * 例如[00:01.005] 转成 1005
     * [00:01] 转成 1000
     *
     * @param timeTag [00:01.5]
     * @return 1005
     */
    public static long convertToTime(String timeTag) {
        // 首先去掉 中括号, 然后用 冒号和点去切割字符串
        String[] times = timeTag.replaceAll("\\[|\\]", "").split(":|\\.");
        return Integer.parseInt(times[0]) * 60 * 1000 + Integer.parseInt(times[1]) * 1000 +
                (times.length == 3 ? Integer.parseInt(times[2]) : 0);
    }

    /**
     * Lrc 时间标签的正则表达式 [mm:ss.fff] 或者[mm:ss]
     * d{1,5} 分钟数,最少是0,最多是99999 (满足超长音频的可能)
     * d{1,2} 秒钟数,最少是0,最多是99 (当然了,实际不会超过60)
     * d{1,3} 毫秒数,最少是0,最多是999
     */
    public static String TIME_TAG_REGEX = "(\\[\\d{1,5}:\\d{1,2}\\.\\d{1,3}\\])|(\\[\\d{1,5}:\\d{1,2}\\])";

    /**
     * 解析一行歌词;
     * 因为可能存在一句话有多个时间标签, 需要一个List ,用于存储多条LrcLine
     * [02:50.340] [03:57.942]只怕我自己会爱上你
     */
    public static void parseLrcLines(String line, ArrayList<LrcLine> lines) {
        //歌词, 去掉时间标签就可以了(有时候标签间有空格,所以trim一下)
        String words = line.replaceAll(TIME_TAG_REGEX, "").trim();
        Pattern pattern = Pattern.compile(TIME_TAG_REGEX);
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            LrcLine lrcLine = new LrcLine();
            //设置时间标签
            lrcLine.setTimeTag(matcher.group(0));
            lrcLine.setWords(words);
            lines.add(lrcLine);
        }
    }

    public static ArrayList<LrcLine> parseLrcLines(String line) {
        ArrayList<LrcLine> lines = new ArrayList<>();
        parseLrcLines(line, lines);
        return lines;
    }

    /**
     * 艺术家
     */
    private static String AR_REGEX = "\\[ar:(.*?)\\]";

    /**
     * 标题
     */
    private static String TI_REGEX = "\\[ti:(.*?)\\]";

    /**
     * 专辑
     */
    private static String AL_REGEX = "\\[al:(.*?)\\]";

    /**
     * 编辑Lrc歌词的人
     */
    private static String BY_REGEX = "\\[by:(.*?)\\]";

    /**
     * 补偿时值,可以为负数,单位是毫秒
     */
    private static String OFFSET_REGEX = "\\[offset:(.*?)\\]";

    /**
     * idTags 匹配所有的标签信息
     */
    private static String IDTAGS_REGEX = "\\[ar:(.*?)\\]|\\[ti:(.*?)\\]|\\[al:(.*?)\\]|\\[by:(.*?)\\]|\\[offset:(.*?)\\]";


    /**
     * 把字符串解析成歌词
     */
    public static LrcDoc parseLrcDoc(String lrc) {
        if (StringUtil.isEmpty(lrc)) {
            return null;
        }
        LrcDoc doc = new LrcDoc();
        doc.setArtist(parseIdTag(lrc, AR_REGEX));
        doc.setTitle(parseIdTag(lrc, TI_REGEX));
        doc.setAlbum(parseIdTag(lrc, AL_REGEX));
        doc.setBy(parseIdTag(lrc, BY_REGEX));
        String offsetStr = parseIdTag(lrc, OFFSET_REGEX);
        doc.setOffset(StringUtil.isEmpty(offsetStr) ? 0 : Long.parseLong(offsetStr));
        String temp = lrc.replaceAll(IDTAGS_REGEX, "");
        String[] lines = temp.split("\n");
        ArrayList<LrcLine> lrcLines = new ArrayList<>();
        for (String line : lines) {
            if (!StringUtil.isEmpty(line)) {
                parseLrcLines(line, lrcLines);
            }
        }
        Collections.sort(lrcLines);
        doc.setLrcLines(lrcLines);
        return doc;
    }

    /**
     * 截取id 标签, 获得数据
     * @param str
     * @param reg
     * @return
     */
    private static String parseIdTag(String str, String reg) {
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

}
