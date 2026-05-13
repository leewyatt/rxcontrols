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

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class StringUtil {
    /**
     * 分解字符串的方法
     * abcdABcd 查找"aB" ,忽略大小写,那么可以分解成 [ab] cd [AB] cd
     *
     * @param text       内容
     * @param keywords   关键字
     * @param ignoreCase 是否忽略大小写 true 忽略, false 不忽略
     * @return
     */
    public static ArrayList<Pair<String, Boolean>> parseText(String text, String keywords, boolean ignoreCase) {
        ArrayList<Pair<String, Boolean>> list = new ArrayList<>();
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty() || (!ignoreCase && !text.contains(keywords)) || (ignoreCase && !text.toUpperCase().contains(keywords.toUpperCase()))) {
            list.add(new Pair<String, Boolean>(text, false));
            return list;
        }
        String textTemp = text;
        String kwTemp = keywords;
        if (ignoreCase) {
            textTemp = text.toUpperCase();
            kwTemp = keywords.toUpperCase();
        }
        int start = 0;
        int kwLen = keywords.length();
        int textLen = text.length();
        while (start != -1 && start < textLen) {
            int startIndex = textTemp.indexOf(kwTemp, start);
            int endIndex = (startIndex == -1) ? -1 : startIndex + kwLen;
            // 如果没有查找到关键字
            if (startIndex == -1 || endIndex == -1) {
                if (textLen > start) {
                    // 没有找到,但是还有剩下的文字,还是加入到list
                    list.add(new Pair<>(text.substring(start), false));
                    break;
                }
            }
            // 第一个字符串,如果不为空,就添加到list.
            String subStr = text.substring(start, startIndex);
            if (!subStr.isEmpty()) {
                list.add(new Pair<>(subStr, false));
            }
            // 如果找到了关键字.那么添加到list
            list.add(new Pair<>(text.substring(startIndex, endIndex), true));
            start = endIndex;
        }
        return list;
    }

    /**
     * 按正则表达式进行匹配
     * 字符串 "123ABC456EDF" 如果按照正在表达式[0-9]+匹配
     * 那么可以分成4个部分: [123] ABC [456] EDF
     * @param text 文本
     * @param reg 正则表达式
     * @return
     */
    public static ArrayList<Pair<String, Boolean>> matchText(String text, String reg) {
        ArrayList<Pair<String, Boolean>> res = new ArrayList<>();
        if(text==null||text.isEmpty()||reg==null||reg.isEmpty()){
            res.add(new Pair<>(text, false));
            return res;
        }

        Matcher m = null;
        try {
            m = Pattern.compile(reg).matcher(text);
        } catch (Exception e) {
            res.add(new Pair<>(text, false));
            return res;
        }
        ArrayList<Integer> list = new ArrayList<>();
        while (m.find()) {
            list.add(m.start());
            list.add(m.end());
        }
        if(list.size()==0){
            res.add(new Pair<>(text, false));
            return res;
        }

        if(list.get(list.size()-1)!=text.length()){
            list.add(text.length());
        }
        boolean flag = true;
        if (list.get(0) != 0) {
            flag = false;
            list.add(0, 0);
        }
        for (int i = 0; i < list.size() - 1; i++) {
            if(list.get(i).equals(list.get(i + 1))){
                flag = !flag;
                continue;
            }
            String sub = text.substring(list.get(i), list.get(i + 1));
            res.add(new Pair<>(sub, flag));
            flag = !flag;
        }
        return res;
    }

    /**
     * 分解字符串的方法2: [private 私有方法],仅仅用于和前面的方法比较速度用的;
     *
     * @param text       内容
     * @param keywords   关键字
     * @param ignoreCase 是否忽略大小写
     * @return
     */
    @Deprecated
    private static ArrayList<Pair<String, Boolean>> parseText2(String text, String keywords, boolean ignoreCase) {
        ArrayList<Pair<String, Boolean>> list = new ArrayList<>();
        if (text == null || keywords == null || text.isEmpty() || keywords.isEmpty() || (!ignoreCase && !text.contains(keywords)) || (ignoreCase && !text.toUpperCase().contains(keywords.toUpperCase()))) {
            list.add(new Pair<String, Boolean>(text, false));
            return list;
        }
        String textTemp = text;
        String keywordsTemp = keywords;
        if (ignoreCase) {
            textTemp = text.toUpperCase();
            keywordsTemp = keywords.toUpperCase();
        }
        //搜索的初始下标
        int fromIndex = 0;
        //关键字长度
        int len = keywordsTemp.length();
        ArrayList<Integer> kwList = new ArrayList<>();
        kwList.add(0);
        boolean flag = false;
        while ((fromIndex = textTemp.indexOf(keywordsTemp, fromIndex)) != -1) {
            if (fromIndex != 0) {
                kwList.add(fromIndex);
            } else {
                flag = true;
            }
            kwList.add(fromIndex + len);
            //如果查找到了,那么把搜索位置往后挪动
            fromIndex += len;
        }
        kwList.add(textTemp.length());
        for (int i = 0; i < kwList.size() - 1; i++) {
            String t = text.substring(kwList.get(i), kwList.get(i + 1));
            if (!t.isEmpty()) {
                list.add(new Pair<>(t, flag));
            }
            flag = !flag;
        }
        return list;
    }

    public static boolean isEmpty(String str) {
        return str==null?true:(str.trim().length()==0?true:false);
    }
}
