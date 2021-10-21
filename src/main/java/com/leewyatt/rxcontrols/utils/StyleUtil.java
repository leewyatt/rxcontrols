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

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.stream.Collectors;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class StyleUtil {
    private StyleUtil() {
    }

    /**
     * Stylesheets的去重
     */
    public static void distinctSheets(Parent parent) {
        distinct(parent.getStylesheets());
    }

    /**
     * 切换Stylesheets: 如果存在就删除,如果不存在就添加
     */
    public static void toggleSheets(Parent parent, String... sheets) {
        toggle(parent.getStylesheets(), sheets);
    }

    /**
     * 切换Stylesheets: 删除指定样式表, 添加指定的样式表
     * 比如:  参数是 parent,{sheet1,sheet2,sheet3},{sheet2,sheet3}
     * 那么 parent 就会删除 sheet1 保留 sheet2,sheet3
     */
    public static void toggleSheets(Parent parent, String[] removeSheets, String... addSheets) {
        if (removeSheets.length == 0) {
            addSheets(parent, addSheets);
            return;
        }
        if (addSheets.length == 0) {
            removeSheets(parent, removeSheets);
            return;
        }
        for (String sheet : removeSheets) {
            boolean exist = false;
            for (String addSheet : addSheets) {
                if (sheet.equals(addSheet)) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                removeSheets(parent, sheet);
            }
        }
        addSheets(parent,addSheets);
    }

    /**
     * 添加Stylesheets ;如果不存在就添加
     */
    public static void addSheets(Parent parent, String... sheets) {
        add(parent.getStylesheets(), sheets);
    }

    /**
     * 删除Stylesheets ;如果存在就删除
     */
    public static void removeSheets(Parent parent, String... sheets) {
        remove(parent.getStylesheets(), sheets);
    }

    /**
     * 删除所有StyleSheets
     */
    public static void clearSheets(Parent parent) {
        clear(parent.getStylesheets());
    }

    /**
     * 和上面的方法相同, 只是下面几个方法的参数是Scene,上面的参数是Parent
     */

    public static void distinctSheets(Scene scene) {
        distinct(scene.getStylesheets());
    }

    public static void toggleSheets(Scene scene, String... sheets) {
        toggle(scene.getStylesheets(), sheets);
    }


    public static void toggleSheets(Scene scene, String[] removeSheets, String... addSheets) {
        if (removeSheets.length == 0) {
            addSheets(scene, addSheets);
            return;
        }
        if (addSheets.length == 0) {
            removeSheets(scene, removeSheets);
            return;
        }
        for (String sheet : removeSheets) {
            boolean exist = false;
            for (String addSheet : addSheets) {
                if (sheet.equals(addSheet)) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                removeSheets(scene, sheet);
            }
        }
        addSheets(scene,addSheets);
    }

    public static void addSheets(Scene scene, String... sheets) {
        add(scene.getStylesheets(), sheets);
    }

    public static void removeSheets(Scene scene, String... sheets) {
        remove(scene.getStylesheets(), sheets);
    }

    public static void clearSheets(Scene scene) {
        clear(scene.getStylesheets());
    }

    /**
     * 去掉重复的class类名
     */
    public static void distinctClass(Node node) {
        distinct(node.getStyleClass());
    }

    /**
     * 切换CSS类名:  检查节点的类名. 如果存在就删除该类名, 如果不存在就添加该类名
     */
    public static void toggleClass(Node node, String... classNames) {
        toggle(node.getStyleClass(), classNames);
    }

    /**
     * 切换类名. 删除指定类名, 添加指定类名
     */
    public static void toggleClass(Node node, String[] removeClassNames, String... addClassNames) {
        if (removeClassNames.length == 0) {
            addClass(node, addClassNames);
            return;
        }
        if (addClassNames.length == 0) {
            removeClass(node, removeClassNames);
            return;
        }
        for (String clazzName : removeClassNames) {
            boolean exist = false;
            for (String addClass : addClassNames) {
                if (clazzName.equals(addClass)) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                removeClass(node, clazzName);
            }
        }
        addClass(node,addClassNames);
    }


    /**
     * 添加CSS类名 : 如果不存在该类名, 那么就添加
     */
    public static void addClass(Node node, String... classNames) {
        add(node.getStyleClass(), classNames);
    }

    /**
     * 删除CSS类名 : 如果存在该类名, 那么就删除, 如果存在多个,就全部删除
     */
    public static void removeClass(Node node, String... classNames) {
        remove(node.getStyleClass(), classNames);
    }

    public static void clearClass(Node node) {
        clear(node.getStyleClass());
    }

    private static void distinct(ObservableList<String> list) {
        list.setAll(list.stream().distinct().collect(Collectors.toList()));
    }

    private static void toggle(ObservableList<String> list, String... es) {
        for (String e : es) {
            if (!list.contains(e)) {
                list.add(e);
            } else {
                //只要相同的就都删
                list.removeAll(e);
            }
        }
    }

    private static void add(ObservableList<String> list, String... es) {
        for (String e : es) {
            if (!list.contains(e)) {
                list.add(e);
            }
        }
    }

    private static void remove(ObservableList<String> list, String... es) {
        for (String e : es) {
            if (list.contains(e)) {
                //只要相同元素就都删除
                list.removeAll(e);
            }
        }
    }

    private static void clear(ObservableList<String> list) {
        list.clear();
    }
}
