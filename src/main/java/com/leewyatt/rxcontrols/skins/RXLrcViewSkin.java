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
package com.leewyatt.rxcontrols.skins;

import com.leewyatt.rxcontrols.controls.RXLrcView;
import com.leewyatt.rxcontrols.pojo.LrcDoc;
import com.leewyatt.rxcontrols.pojo.LrcLine;
import com.leewyatt.rxcontrols.utils.UIUtil;
import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 * <p>
 * 歌词组件的皮肤
 */
public class RXLrcViewSkin extends SkinBase<RXLrcView> {

    private RXLrcView control;
    private Pane lrcPane;
    private final Pane root;
    private final Label tipLabel;
    private int currentIndex;
    private Timeline lrcPaneMoveAnim;
    private ParallelTransition moveAndScaleAnim;
    private Timeline reboundUp;
    private Timeline reboundDown;

    /**
     * 回弹用时
     */
    private final static Duration REBOUND_DURATION = Duration.millis(150);
    /**
     * 开始拖动的Y坐标
     */
    private double startDragY;
    /**
     * 上一次移动距离
     */
    private double lastMoveDis;
    /**
     * 歌词行移动 (尝试过整体移动,性能上并没有比单行移动的效果强...)
     */
    private TranslateTransition[] lineMove;
    /**
     * 变小动画
     */
    private ScaleTransition smallST;
    /**
     * 变大动画
     */
    private ScaleTransition bigST;

    private final ChangeListener<LrcDoc> lrcDocChangeListener = (observable, oldValue, newValue) -> paintLrcLines();

    private final InvalidationListener invalidationListener = observable -> paintLrcLines();

    private final ChangeListener<Duration> durationChangeListener = (observable, oldValue, newValue) -> {
        if (emptyLrcDoc()) {
            return;
        }
        int lastIndex = computeLrcLinesIndex();
        if (currentIndex == lastIndex) {
            return;
        }
        moveLrcLines(lastIndex);
    };

    public RXLrcViewSkin(RXLrcView control) {
        super(control);
        this.control = control;
        lrcPaneMoveAnim = new Timeline();
        moveAndScaleAnim = new ParallelTransition();
        reboundUp = new Timeline();
        reboundDown = new Timeline();
        lineMove = new TranslateTransition[0];
        smallST = new ScaleTransition();
        bigST = new ScaleTransition();

        root = new Pane();
        //lrc-view-root
        root.getStyleClass().add("pane");
        tipLabel = new Label();
        //lrc-view-tip
        tipLabel.getStyleClass().add("tip-label");
        tipLabel.textProperty().bind(control.tipStringProperty());
        lrcPane = new Pane();
        //lrc-view-pane
        lrcPane.getStyleClass().add("lrc-pane");
        root.getChildren().add(lrcPane);
        UIUtil.clipRoot(control, root);
        getChildren().setAll(root);
        paintLrcLines();

        // 歌词文件被替换成其他歌词文件的时候, 重绘歌词
        control.lrcDocProperty().addListener(lrcDocChangeListener);

        // 当组件的宽发生改变时,重绘歌词
        control.widthProperty().addListener(invalidationListener);

        //当组件的高发生改变时,重绘歌词
        control.heightProperty().addListener(invalidationListener);

        control.borderProperty().addListener(invalidationListener);

        control.paddingProperty().addListener(invalidationListener);

        //歌词文件的播放进度放生改变时,移动歌词/
        control.currentTimeProperty().addListener(durationChangeListener);

        //-------让LrcPane 可以手动移动,去查看歌词其他部分的内容---------
        // 鼠标按下
        control.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        //鼠标拖动
        control.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        //鼠标释放
        control.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);

        //行高改变时
        control.lineHeightProperty().addListener(invalidationListener);
    }

    private final EventHandler<MouseEvent> mousePressedHandler = event -> startDragY = event.getY() - lrcPane.getLayoutY();
    private final EventHandler<MouseEvent> mouseDraggedHandler = event -> {
        double offsetY = 0;
        if (lrcPane.getChildren().size() != 0) {
            offsetY = lrcPane.getChildren().get(0).getTranslateY();
        }

        lastMoveDis = event.getY() - startDragY;
        double bottomBound = UIUtil.computeInnerH(control) / 2 + control.getLineHeight() / 2 - control.getLineHeight() - offsetY;

        if (lastMoveDis > bottomBound) {
            lastMoveDis = bottomBound;
        }
        double topBound = -lrcPane.getHeight() - offsetY;
        if (lastMoveDis < topBound) {
            lastMoveDis = topBound;
        }
        lrcPane.setLayoutY(lastMoveDis);
    };

    private EventHandler<MouseEvent> mouseReleasedHandler = event -> {
        double offsetY = 0;
        if (lrcPane.getChildren().size() != 0) {
            offsetY = lrcPane.getChildren().get(0).getTranslateY();
        }
        //低于底部后 ,自动往上的动画
        double x = UIUtil.computeBorderSize(control, false, false, true, false) +
                UIUtil.computePaddingSize(control, false, false, true, false);

        if (lrcPane.getLayoutY() >= UIUtil.computeInnerH(control) / 2 - control.getLineHeight() / 2 - control.getLineHeight() - offsetY - x) {
            if (reboundUp.getStatus() == Animation.Status.RUNNING) {
                reboundUp.stop();
            }
            reboundUp.getKeyFrames().setAll(new KeyFrame(REBOUND_DURATION, new KeyValue(
                    lrcPane.layoutYProperty(),
                    UIUtil.computeInnerH(control) / 2 - control.getLineHeight() / 2 - control.getLineHeight() - offsetY,
                    Interpolator.EASE_OUT)));
            reboundUp.play();
        }
        //高于顶部后,自动往下的动画
        if (lrcPane.getLayoutY() <= -lrcPane.getHeight() + control.getLineHeight() - offsetY) {
            if (reboundDown.getStatus() == Animation.Status.RUNNING) {
                reboundDown.stop();
            }
            reboundDown.getKeyFrames().setAll(new KeyFrame(
                    REBOUND_DURATION,
                    new KeyValue(
                            lrcPane.layoutYProperty(),
                            -lrcPane.getHeight() + control.getLineHeight() - offsetY,
                            Interpolator.EASE_OUT)
            ));

            reboundDown.play();
        }
    };

    private void moveLrcLines(int newIndex) {
        if (newIndex < 0) {
            return;
        }
        UIUtil.animeStopAtEnd(moveAndScaleAnim);
        lrcPaneMoveAnim.getKeyFrames().clear();
        Duration duration = control.getAnimationTime();
        // 歌词面板的整体移动
        if (Double.compare(lrcPane.getLayoutY(), 0) != 0) {
            lrcPaneMoveAnim.getKeyFrames().setAll(new KeyFrame(duration,
                    new KeyValue(lrcPane.layoutYProperty(), 0)
            ));
        }
        moveAndScaleAnim.getChildren().clear();
        ArrayList<LrcLine> lines = control.getLrcDoc().getLrcLines();
        double moveDistance = -(newIndex - currentIndex) * control.getLineHeight();
        // 歌词并行移动
        for (int i = 0; i < lines.size(); i++) {
            LrcLineLabel node = (LrcLineLabel) lrcPane.getChildren().get(i);
            lineMove[i].setDuration(duration);
            lineMove[i].setNode(node);
            lineMove[i].setByY(moveDistance);
            if (newIndex == i) {
//                StyleUtil.addClass(node, "lrc-current-line");
                node.setPlaying(true);
            } else {
//                StyleUtil.removeClass(node, "lrc-current-line");
                node.setPlaying(false);
                if (i != currentIndex) {
                    node.setScaleX(1.0);
                    node.setScaleY(1.0);
                }
            }
        }
        moveAndScaleAnim.getChildren().addAll(lineMove);
        // 当前歌词缩小
        if (currentIndex != -1) {
            Label node = (Label) lrcPane.getChildren().get(currentIndex);
            smallST.setNode(node);
            smallST.setDuration(duration);
            smallST.setToX(1);
            smallST.setToY(1);
            moveAndScaleAnim.getChildren().add(smallST);
        }
        // 下一句歌词放大
        Label node = (Label) lrcPane.getChildren().get(newIndex);
        bigST.setNode(node);
        bigST.setDuration(duration);
        bigST.setToX(control.getCurrentLineScaling());
        bigST.setToY(control.getCurrentLineScaling());
        moveAndScaleAnim.getChildren().addAll(bigST, lrcPaneMoveAnim);
        moveAndScaleAnim.play();
        currentIndex = newIndex;
    }

    private void paintLrcLines() {
        lrcPane.getChildren().clear();
        if (emptyLrcDoc()) {
            lrcPane.getChildren().add(tipLabel);
            tipLabel.layoutXProperty().bind(root.widthProperty().subtract(tipLabel.widthProperty()).divide(2.0));
            tipLabel.layoutYProperty().bind(root.heightProperty().subtract(tipLabel.heightProperty()).divide(2.0));
            return;
        }
        //如果歌词不为空, 那么准备绘制歌词
        int index = computeLrcLinesIndex();
        ArrayList<LrcLine> lines = control.getLrcDoc().getLrcLines();
        for (int i = 0; i < lines.size(); i++) {
            LrcLineLabel label = new LrcLineLabel(lines.get(i).getWords());
//            label.getStyleClass().add("lrc-line");
            if (index == i) {
//                StyleUtil.addClass(label, "lrc-current-line");
                label.setPlaying(true);
            } else {
//                StyleUtil.removeClass(label, "lrc-current-line");
                label.setPlaying(false);
            }
            label.setLayoutY(
                    (i + 1) * control.getLineHeight()
                            + (UIUtil.computeInnerH(control)
                            - control.getLineHeight()) / 2);
            label.setPrefHeight(control.getLineHeight());
            label.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> UIUtil.computeInnerW(control),
                    control.layoutBoundsProperty(),
                    control.paddingProperty(),
                    control.borderProperty()));
            lrcPane.getChildren().add(label);
        }

        currentIndex = -1;

        // 改变行数的时候, 根据需要增减动画
        int newSize = lines.size();
        int oldSize = lineMove.length;
        lineMove = Arrays.copyOf(lineMove, newSize);
        for (int i = oldSize; i < newSize; i++) {
            lineMove[i] = new TranslateTransition();
        }
        moveLrcLines(index);

    }

    /**
     * @return 当前应该播放的是第几行的歌词
     */
    private int computeLrcLinesIndex() {
        LrcDoc lrcDoc = control.getLrcDoc();
        if (emptyLrcDoc()) {
            return -1;
        }
        long now = (long) control.getCurrentTime().add(Duration.millis(lrcDoc.getOffset())).add(control.getUserOffset()).toMillis();
        ArrayList<LrcLine> lrcLines = lrcDoc.getLrcLines();
        int size = lrcLines.size();

        if (now < lrcLines.get(0).getTime() - 1) {
            return -1;
        }
        for (int i = 0; i < size - 1; i++) {
            if (now >= lrcLines.get(i).getTime() && now < lrcLines.get(i + 1).getTime()) {
                return i;
            }
        }
        return size - 1;
    }

    private boolean emptyLrcDoc() {
        return control.getLrcDoc() == null ||
                control.getLrcDoc().getLrcLines() == null ||
                control.getLrcDoc().getLrcLines().size() == 0;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(root, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    public void dispose() {
        control.lrcDocProperty().removeListener(lrcDocChangeListener);
        control.widthProperty().removeListener(invalidationListener);
        control.heightProperty().removeListener(invalidationListener);
        control.borderProperty().removeListener(invalidationListener);
        control.paddingProperty().removeListener(invalidationListener);
        control.currentTimeProperty().removeListener(durationChangeListener);
        control.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        UIUtil.animeStop(
                reboundUp, reboundDown, moveAndScaleAnim, lrcPaneMoveAnim, smallST, bigST);
        UIUtil.animeStop(lineMove);
        getChildren().clear();
        super.dispose();
    }

}

class LrcLineLabel extends Label {
    private static final PseudoClass PLAYING_PSEUDO_CLASS = PseudoClass.getPseudoClass("playing");
    private BooleanProperty playing;

    public LrcLineLabel() {
        getStyleClass().setAll("lrc-line");
        playingProperty().addListener(
                (observable, oldValue, newValue) -> pseudoClassStateChanged(PLAYING_PSEUDO_CLASS, newValue));
    }

    public LrcLineLabel(String text) {
        this();
        setText(text);
    }

    public LrcLineLabel(String text, Node graphic) {
        this(text);
        setGraphic(graphic);
    }

    public final void setPlaying(boolean playing) {
        playingProperty().set(playing);
    }

    public final boolean getPlaying() {
        return playing == null ? false : playing.get();
    }

    public final BooleanProperty playingProperty() {
        if (playing == null) {
            playing = new SimpleBooleanProperty(false);
        }
        return playing;
    }
}