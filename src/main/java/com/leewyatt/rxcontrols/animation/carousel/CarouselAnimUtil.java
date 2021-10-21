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
package com.leewyatt.rxcontrols.animation.carousel;

import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import com.leewyatt.rxcontrols.event.RXCarouselEvent;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 工具方法
 */
public class CarouselAnimUtil {

    public static void fireClosing(RXCarouselPane pane){
        pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.CLOSING));
    }

    public static void fireOpening(RXCarouselPane pane){
        pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENING));
    }

    public static void fireClosed(RXCarouselPane pane){
        pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.CLOSED));
    }

    public static void fireOpened(RXCarouselPane pane){
        pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENED));
    }

    /**
     * 返回一个在合理范围的值
     * @param min
     * @param max
     * @param value
     * @return
     */
    public static double clamp(double min, double max, double value) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 显示与隐藏页面
     *
     * @param pane
     * @param showIndexAry
     */
    public static void showPanes(int i, Pane pane, int... showIndexAry) {
        boolean flag = true;
        for (int index : showIndexAry) {
            if (i == index) {
                flag = false;
                pane.setVisible(true);
                break;
            }
        }
        if (flag) {
            pane.setVisible(false);
        }
    }

    /**
     * 清理timeline
     *
     * @param timelines
     */
    public static void disposeTimeline(Timeline... timelines) {
        if (timelines == null) {
            return;
        }
        for (int i = 0; i < timelines.length; i++) {
            if (timelines[i] != null) {
               if(timelines[i].getStatus()== Animation.Status.RUNNING){
                   timelines[i].stop();
               }
                timelines[i].getKeyFrames().clear();
                timelines[i] = null;
            }
        }
    }

    /**
     *工具: 获得组件的宽
     * @param pane
     * @return
     */
    public static double getPaneWidth(Pane pane) {
        Bounds paneBounds = pane.getLayoutBounds();
        return paneBounds.getWidth();
    }

    /**
     * 工具: 获得组件的高
     * @param pane
     * @return
     */
    public static double getPaneHeight(Pane pane) {
        Bounds paneBounds = pane.getLayoutBounds();
        return paneBounds.getHeight();
    }

    private static final int MIN_SIZE = 2;

    /**
     * 工具: 计算需要的节点和方向
     * @param panes
     * @param currentIndex
     * @param nextIndex
     * @param isLoop
     * @param skipMiddlePane
     * @return
     */
    public static Pair<List<RXCarouselPane>, Boolean> computeSubListAndDirection(List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean isLoop, boolean skipMiddlePane) {
        List<RXCarouselPane> subList;
        boolean direction = currentIndex < nextIndex;
        if (!isLoop) {
            if (direction) {
                subList = panes.subList(currentIndex, nextIndex + 1);
            } else {
                subList = panes.subList(nextIndex, currentIndex + 1);
            }
        } else {
            RXCarouselPane currentPane = panes.get(currentIndex);
            RXCarouselPane nextPane = panes.get(nextIndex);
            if (panes.size() == MIN_SIZE) {
                direction = true;
                subList = new ArrayList<>();
                subList.add(currentPane);
                subList.add(nextPane);
            } else if (currentIndex == panes.size() - 1 && nextIndex == 0) {
                direction = true;
                subList = new ArrayList<>();
                subList.add(currentPane);
                subList.add(nextPane);
            } else if (currentIndex == 0 && nextIndex == panes.size() - 1) {
                direction = false;
                subList = new ArrayList<>();
                subList.add(nextPane);
                subList.add(currentPane);
            } else if (currentIndex < nextIndex) {
                direction = true;
                subList = panes.subList(currentIndex, nextIndex + 1);
            } else {
                direction = false;
                subList = panes.subList(nextIndex, currentIndex + 1);
            }
        }

        if (skipMiddlePane) {
            List<RXCarouselPane> list = new ArrayList<>();
            RXCarouselPane p1 = subList.get(0);
            RXCarouselPane p2 = subList.get(subList.size() - 1);
            list.add(p1);
            list.add(p2);
            return new Pair<>(list, direction);
        }
        return new Pair<>(subList, direction);
    }

    /**
     * 计算动画的方向
     * @param panes
     * @param currentIndex
     * @param nextIndex
     * @param foreAndAftJump
     * @return
     */
    public static boolean computeDirection(List<RXCarouselPane> panes, int currentIndex, int nextIndex,
                                           boolean foreAndAftJump) {
        boolean direction = currentIndex < nextIndex;
        if (foreAndAftJump) {
            if (panes.size() == MIN_SIZE) {
                direction = true;
            } else if (currentIndex == panes.size() - 1 && nextIndex == 0) {
                direction = true;
            } else if (currentIndex == 0 && nextIndex == panes.size() - 1) {
                direction = false;
            } else if (currentIndex < nextIndex) {
                direction = true;
            } else {
                direction = false;
            }

        }
        return direction;
    }

    /**
     * 快照参数
     */
    private static SnapshotParameters PARAMETERS = new SnapshotParameters();

    static {
        //注意填充色设置为透明
        PARAMETERS.setFill(Color.TRANSPARENT);
    }

    public static WritableImage nodeSnapshot(Node node) {
        // 快照截图
        return node.snapshot(PARAMETERS, null);
    }

//    public static void nodeSnapshot(Node node,WritableImage wi){
//        node.snapshot(PARAMETERS,wi);
//    }
}
