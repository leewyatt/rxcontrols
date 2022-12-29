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

import com.leewyatt.rxcontrols.animation.carousel.CarouselAnimation;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.leewyatt.rxcontrols.event.RXCarouselEvent;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * !!!  是否回归前一版本, 用Timeline 替代 Timer? 纠结思考下....
 * Timer 在 阿里java手册里 推荐使用ScheduledExecutorService代替Timer吧
 *
 */
public class RXCarouselSkin extends SkinBase<RXCarousel> {

    private RXCarousel control;
    private ObservableList<RXCarouselPane> paneList;

    /**
     * 场景图上的根节点(包含内容面板,效果面板,遮挡面板,导航面板)
     */
    private final StackPane rootPane;
    /**
     * 主内容面板(存储显示用的节点)
     */
    private StackPane contentPane;
    /**
     * 特效面板( 截图等.. )
     */
    private Pane effectPane;

    /**
     * 导航层 (前进, 后退 以及 每页的按钮)
     */
    private final Pane navigationPane;
    /**
     * 当前面板索引
     */
    private int currentIndex = -1;
    /**
     * 下一面板索引
     */
    private int nextIndex = -1;
    /**
     * 过渡的动画
     */
    private Animation animation;
    /**
     * 自动切换线程
     */
    private Timer autoSwitchTimer;
    //--------导航层的子组件------------
    /**
     * 左方向按键
     */
    private StackPane leftButton;
    /**
     * 左箭图形
     */
    private final Region leftArrow;
    /**
     * 右方向按键
     */
    private StackPane rightButton;
    /**
     * 右箭图形
     */
    private final Region rightArrow;
    /**
     * 导航条
     */
    private FlowPane navBar;

    private final ToggleGroup btnGroup = new ToggleGroup();
    /**
     * 箭头显示动画
     */
    private final Timeline tlArrowShow = new Timeline();
    /**
     * 箭头自动隐藏
     */
    private final Timeline tlArrowHide = new Timeline();
    /**
     * 每页的导航按钮自动显示
     */
    private final Timeline tlNavShow = new Timeline();
    /**
     * 每页的自动隐藏
     */
    private final Timeline tlNavHide = new Timeline();
    /**
     * 显示与隐藏的透明度变化 耗时
     */
    private static final Duration DURATION_ARROW_SHOW = Duration.millis(180);

    public RXCarouselSkin(RXCarousel control) {
        super(control);
        this.control = control;
        rootPane = new StackPane();
        rootPane.getStyleClass().add("root-pane");

        paneList = control.getPaneList();
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");

        effectPane = new Pane();
        effectPane.getStyleClass().add("effect-pane");
        effectPane.setPickOnBounds(false);

        //导航层 包含 前进后退的箭头 以及每页对应的按钮
        navigationPane = new Pane();
        navigationPane.getStyleClass().add("nav-pane");
        navigationPane.setPickOnBounds(false);

        //箭头 前一页按钮
        leftButton = new StackPane();
        leftButton.getStyleClass().add("left-button");
        leftArrow = new Region();
        leftArrow.getStyleClass().add("left-arrow");
        leftButton.getChildren().setAll(leftArrow);
        //点击左键,翻到前一页
        leftButton.setOnMouseClicked(event -> control.showPreviousPane());
        //箭头  后一页按钮
        rightButton = new StackPane();
        rightButton.getStyleClass().add("right-button");
        rightArrow = new Region();
        rightArrow.getStyleClass().add("right-arrow");
        rightButton.getChildren().setAll(rightArrow);
        //点击右键,翻到后一页
        rightButton.setOnMouseClicked(event -> control.showNextPane());

        //导航条: 每页对应的按钮
        navBar = new FlowPane(Orientation.HORIZONTAL);
        navBar.getStyleClass().add("nav-bar");
        navBar.setLayoutX(0);
        //导航条的宽度.
        navBar.prefWidthProperty().bind(control.prefWidthProperty());
        //导航条 layoutY的位置
        navBar.layoutYProperty().bind(navigationPane.heightProperty().subtract(navBar.heightProperty()));

        navigationPane.getChildren().setAll(leftButton, rightButton, navBar);
        // 裁剪容器(避免显示的部分超出容器的范围)
        clipRegion(rootPane);

        // 绑定宽高
        DoubleProperty dbw = control.prefWidthProperty();
        DoubleProperty dbh = control.prefHeightProperty();
        rootPane.minWidthProperty().bind(dbw);
        rootPane.prefWidthProperty().bind(dbw);
        rootPane.minHeightProperty().bind(dbh);
        rootPane.prefHeightProperty().bind(dbh);
    /* 因为需要效果需要setClip ,所以就在不能这里clip裁剪
        所以,每一个RXCarouselPane 应该宽高都和组件一致
       for (RXCarouselPane pane : paneList) {
            clipRegion(pane);
        }*/
        // 添加底层容器到组件里
        rootPane.getChildren().addAll(contentPane, effectPane, navigationPane);
        //rootPane.backgroundProperty().bind(control.backgroundProperty());


        getChildren().setAll(rootPane);
        getSkinnable().requestLayout();

        //添加Listener
        control.selectedIndexProperty().addListener(selectedIndexChangeListener);
        control.carouselAnimationProperty().addListener(animationChangeListener);
        control.getPaneList().addListener(changeListener);
        control.autoSwitchProperty().addListener(autoSwitchListener);
//        registerChangeListener(control.autoSwitchProperty(), ob->{
//            if (control.isAutoSwitch()) {
//                initTimer();
//            } else {
//                stopTimer();
//            }
//        });
        control.showTimeProperty().addListener(timeListener);
        control.animationTimeProperty().addListener(timeListener);
        control.hoverProperty().addListener(hoverPauseListener);
        control.arrowDisplayModeProperty().addListener(arrowDisplayModeListener);
        control.navDisplayModeProperty().addListener(navBarDisplayModelListener);
        init();//初始化显示

    }

    /**
     * 获取该 切换按钮 在ToggleGroup里的位置
     *
     * @param tg
     * @return
     */
    private int getTgBtnIndex(Toggle tg) {
        ObservableList<Toggle> toggles = btnGroup.getToggles();
        for (int i = 0; i < toggles.size(); i++) {
            if (tg == toggles.get(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 箭头的显示模式发生了改变的时调用
     */
    private final InvalidationListener arrowDisplayModeListener = ob -> setArrowButtonShowCon();

    /**
     * 导航条显示模式发生了变化时调用
     */
    private final ChangeListener<Boolean> navAutoShowListener = (ob, ov, nv) -> {
        navBar.setVisible(true);
        if (tlNavShow != null) {
            tlNavShow.stop();
        }
        if (tlNavHide != null) {
            tlNavHide.stop();
        }
        if (control.navDisplayModeProperty().get() == DisplayMode.AUTO) {
            // 移入显示
            if (nv) {
                navBar.setOpacity(0);
                tlNavShow.getKeyFrames()
                        .setAll(new KeyFrame(DURATION_ARROW_SHOW, new KeyValue(navBar.opacityProperty(), 1.0)));
                tlNavShow.play();
                // 移除隐藏
            } else {
                navBar.setOpacity(1);
                tlNavHide.getKeyFrames()
                        .setAll(new KeyFrame(DURATION_ARROW_SHOW, new KeyValue(navBar.opacityProperty(), 0)));
                tlNavHide.setOnFinished(ex -> {
                    navBar.setVisible(false);
                });
                tlNavHide.play();
            }
        }
    };

    /**
     * 导航显示变化
     */
    private final InvalidationListener navBarDisplayModelListener = ob -> setNavBarShowCon();

    private void setNavBarShowCon() {
        navBar.setVisible(true);
        DisplayMode mode = control.getNavDisplayMode();
        if (mode == DisplayMode.SHOW) {
            control.hoverProperty().removeListener(navAutoShowListener);
            navBar.setOpacity(1.0);
            navBar.setVisible(true);
        } else if (mode == DisplayMode.HIDE) {
            control.hoverProperty().removeListener(navAutoShowListener);
            navBar.setVisible(false);
        } else {
            navBar.setVisible(false);
            control.hoverProperty().addListener(navAutoShowListener);
        }
    }

    /**
     * 移入暂停 或者 移除时自动播放
     */
    private final ChangeListener<Boolean> hoverPauseListener = (ob, ov, nv) -> {
        if (control.isAutoSwitch() && control.isHoverPause()) {
            if (nv && autoSwitchTimer != null) {
                stopTimer();
            } else {
                initTimer();
            }
        }
    };

    private void init() {
        paneList = control.getPaneList();
        //初始化导航条
        initNavBar();

        contentPane.getChildren().setAll(paneList);
        //重置界面后,再次清理切换动画造成的Pane的位移,形变 等 影响
        control.getCarouselAnimation().clearEffects(paneList, effectPane, currentIndex, nextIndex);

        //首次运行时,选择好页码
        int size = paneList.size();
        int selectIndex = control.getSelectedIndex();
        nextIndex = selectIndex;
        if (size > 0) {
            int firstPane = -1;
            if (selectIndex > size - 1 || selectIndex < 0 || size == 1) {
                control.setSelectedIndex(0);
                firstPane = 0;
            } else {
                for (int i = 0; i < size; i++) {
                    if (i == selectIndex) {
                        firstPane = i;
                    } else {
                        RXCarouselPane pane = paneList.get(i);
                        pane.setVisible(false);
                    }
                }
            }

            RXCarouselPane pane = paneList.get(firstPane);
            pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENING));
            pane.setVisible(true);
            pane.fireEvent(new RXCarouselEvent(RXCarouselEvent.OPENED));
            control.getCarouselAnimation().initAnimation(contentPane, effectPane, paneList, currentIndex, nextIndex);
        }

        if (control.isAutoSwitch()) {
            stopTimer();
            //条目大于等于2的时候才开始切换
            if (size > 1) {
                initTimer();
            }
        }

        //箭头 位置的初始化
        setArrowButtonLocation();
        //箭头 显示与否
        setArrowButtonShowCon();
        //导航条是否显示
        setNavBarShowCon();

    }

    /**
     * 设置左右按键是否显示
     */
    private void setArrowButtonShowCon() {
        DisplayMode displayMode = control.getArrowDisplayMode();
        if (displayMode == DisplayMode.HIDE) {
            control.hoverProperty().removeListener(arrowShowListener);
            leftButton.setVisible(false);
            rightButton.setVisible(false);
        } else if (displayMode == DisplayMode.SHOW) {
            control.hoverProperty().removeListener(arrowShowListener);
            leftButton.setOpacity(1.0);
            rightButton.setOpacity(1.0);
            leftButton.setVisible(true);
            rightButton.setVisible(true);
        } else if (displayMode == DisplayMode.AUTO) {
            leftButton.setVisible(false);
            rightButton.setVisible(false);
            control.hoverProperty().addListener(arrowShowListener);
        }
    }

    private void setArrowButtonLocation() {
        DoubleBinding y1 = Bindings.createDoubleBinding(new Callable<Double>() {
            @Override
            public Double call() throws Exception {
                return contentPane.heightProperty().subtract(leftButton.heightProperty()).divide(2.0).get();
            }
        }, contentPane.heightProperty(), leftButton.heightProperty());
        leftButton.layoutYProperty().bind(y1);
        leftButton.setLayoutX(10);

        DoubleBinding y2 = Bindings.createDoubleBinding(new Callable<Double>() {
            @Override
            public Double call() throws Exception {
                return contentPane.heightProperty().subtract(rightButton.heightProperty()).divide(2.0).get();
            }
        }, contentPane.heightProperty(), rightButton.heightProperty());
        rightButton.layoutYProperty().bind(y2);

        DoubleBinding w2 = Bindings.createDoubleBinding(() -> contentPane.widthProperty().subtract(rightButton.widthProperty()).subtract(10).get(), contentPane.widthProperty(), rightButton.widthProperty());

        rightButton.layoutXProperty().bind(w2);
    }

    private final ChangeListener<Boolean> arrowShowListener = (ob, ov, nv) -> {
        leftButton.setVisible(true);
        rightButton.setVisible(true);

        if (tlArrowShow != null) {
            tlArrowShow.stop();
        }
        if (tlArrowHide != null) {
            tlArrowHide.stop();
        }
        if (control.arrowDisplayModeProperty().get() == DisplayMode.AUTO) {
            // 移入显示
            if (nv) {
                leftButton.setOpacity(0);
                rightButton.setOpacity(0);
                KeyValue kv2 = new KeyValue(leftButton.opacityProperty(), 1.0);
                KeyValue kv4 = new KeyValue(rightButton.opacityProperty(), 1.0);
                KeyFrame kf2 = new KeyFrame(DURATION_ARROW_SHOW, kv2);
                KeyFrame kf4 = new KeyFrame(DURATION_ARROW_SHOW, kv4);
                tlArrowShow.getKeyFrames().clear();
                tlArrowShow.getKeyFrames().addAll(kf2, kf4);

                tlArrowShow.play();
                // 移除隐藏
            } else {
                leftButton.setOpacity(1);
                rightButton.setOpacity(1);
                KeyValue kv2 = new KeyValue(leftButton.opacityProperty(), 0);
                KeyValue kv4 = new KeyValue(rightButton.opacityProperty(), 0);
                KeyFrame kf2 = new KeyFrame(DURATION_ARROW_SHOW, kv2);
                KeyFrame kf4 = new KeyFrame(DURATION_ARROW_SHOW, kv4);
                tlArrowHide.getKeyFrames().clear();
                tlArrowHide.getKeyFrames().addAll(kf2, kf4);
                tlArrowHide.setOnFinished(ex -> {
                    leftButton.setVisible(false);
                    rightButton.setVisible(false);
                });
                tlArrowHide.play();
            }
        }
    };

    private final ChangeListener<Boolean> autoSwitchListener = (ob, ov, nv) -> {
        if (nv) {
            initTimer();
        } else {
            stopTimer();
        }
    };


    private final InvalidationListener timeListener = observable -> {
        if (control.isAutoSwitch()) {
            initTimer();
        }
    };

    private final ListChangeListener<RXCarouselPane> changeListener = c -> init();

    private final ChangeListener<CarouselAnimation> animationChangeListener = (ob, oldAnimation, newAnimation) -> {
        // 1: 停止之前的动画
        if (animation != null) {
            animation.stop();
        }
        oldAnimation.clearEffects(paneList, effectPane, currentIndex, nextIndex);
        newAnimation.initAnimation(contentPane, effectPane, paneList, currentIndex, nextIndex);
    };

    private final ChangeListener<Number> selectedIndexChangeListener = (ob, ov, nv) -> {
        currentIndex = ov.intValue();
        nextIndex = nv.intValue();
        //首次加载的时候, 设置第一页 只有一个Pane的时候,也进入这个if
        if ((currentIndex < 0 || currentIndex > paneList.size() - 1) && nextIndex == 0) {
            paneList.get(0).setVisible(true);
            btnGroup.getToggles().get(0).setSelected(true);
            for (int i = 1; i < paneList.size(); i++) {
                paneList.get(i).setVisible(false);
            }
            return;
        }
        playAnimation();
    };

    /**
     * 初始化导航条
     */
    private void initNavBar() {
        btnGroup.getToggles().clear();
        navBar.getChildren().clear();
        int size = paneList.size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                RXToggleButton btn = new RXToggleButton();
                btn.textProperty().bind(paneList.get(i).textProperty());
                btn.getStyleClass().add("nav-button");
                btn.setId("nav-button" + i);
                // btn.managedProperty().bind(btn.visibleProperty());// 可见和可管理绑定
                navBar.getChildren().add(btn);
                btnGroup.getToggles().add(btn);
                int finalIndex = i;
                btn.setOnAction(event -> {
                    btnGroup.selectToggle(btn);
                    control.setSelectedIndex(finalIndex);
                });
            }

            int tempIndex = control.getSelectedIndex();
            //如果在合理的区间就选中它
            if (tempIndex < size && tempIndex > -1) {
                btnGroup.getToggles().get(tempIndex).setSelected(true);
                //如果不在合理区间,且数据至少有1条,那么选中第一个
            } else {
                btnGroup.getToggles().get(0).setSelected(true);
            }
        }
        //setNavBarShowCon();
    }

    private void playAnimation() {
        //选中导航按钮
        if (nextIndex < paneList.size() && nextIndex > -1) {
            btnGroup.getToggles().get(nextIndex).setSelected(true);
        }
        // 1: 停止之前的动画
        if (animation != null) {
            animation.stop();
        }
        // 2: 获取动画播放的Animation
        CarouselAnimation carouselAnimation = control.getCarouselAnimation();
        animation = carouselAnimation.getAnimation(control, contentPane, effectPane, paneList, currentIndex, nextIndex, control.foreAndAftJumpProperty().get(), control.getAnimationTime());
        control.foreAndAftJumpProperty().set(false);

        // 3: 播放动画;
        if (animation != null) {
            animation.play();
        }
    }

    /**
     * 裁剪容器为指定的范围
     *
     * @param region
     */
    private void clipRegion(Region region) {
        Rectangle rectClip = new Rectangle();
        region.setClip(rectClip);
        rectClip.widthProperty().bind(region.widthProperty().subtract(control.getBorder().getStrokes().size()));
        rectClip.heightProperty().bind(region.heightProperty().subtract(control.getBorder().getStrokes().size()));
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(rootPane, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    //    @Override
//    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
//                                      double leftInset) {
//        return rightInset + leftInset
//                + (control.prefWidthProperty().get() == -1 ? 300 : control.prefWidthProperty().get());
//    }
//
//    @Override
//    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
//                                       double leftInset) {
//        return topInset + bottomInset
//                + (control.prefHeightProperty().get() == -1 ? 200 : control.prefHeightProperty().get());
//    }
//
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {

        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    private void initTimer() {
        stopTimer();//先取消
        autoSwitchTimer = new Timer(true);
        //计算周期时间 = 显示时间 + 切换(动画)时间
        long period = (long) (control.getShowTime().toMillis() + control.getAnimationTime().toMillis());
        autoSwitchTimer.schedule(new TimerTask() {
                                     @Override
                                     public void run() {
                                         //非UI线程去修改图形界面
                                         Platform.runLater(() -> {
                                             if (animation == null) {
                                                 control.showNextPane();
                                             } else if (animation.getStatus() != Animation.Status.RUNNING) {
                                                 control.showNextPane();
                                             }
                                         });

                                     }
                                 }, period,
                period);
    }

    private void stopTimer() {
        if (autoSwitchTimer != null) {
            //取消
            autoSwitchTimer.cancel();
            //清除
            autoSwitchTimer.purge();
            autoSwitchTimer = null;
        }
    }

    public List<RXToggleButton> getNavButtons() {
        List<RXToggleButton> buttons = new ArrayList<>();
        for (Node child : navBar.getChildren()) {
            buttons.add((RXToggleButton) child);
        }
        return buttons;
    }

    /**
     * 如果有多个皮肤(skin),那么切换皮肤时,会被调用
     */
    @Override
    public void dispose() {
        //结束自动切换线程
        stopTimer();
        // 结束动画
        if (animation != null) {
            animation.stop();
            animation = null;
        }

       //解除绑定
        for (RXToggleButton button : getNavButtons()) {
            button.textProperty().unbind();
        }
        rootPane.minWidthProperty().unbind();
        rootPane.prefWidthProperty().unbind();
        rootPane.minHeightProperty().unbind();
        rootPane.prefHeightProperty().unbind();
        navBar.layoutYProperty().unbind();
        navBar.prefWidthProperty().bind(control.prefWidthProperty());
        rightButton.layoutXProperty().unbind();
        rightButton.layoutYProperty().unbind();
        leftButton.layoutYProperty().unbind();
        // 移除Listener
        control.selectedIndexProperty().removeListener(selectedIndexChangeListener);
        control.carouselAnimationProperty().removeListener(animationChangeListener);
        control.getPaneList().removeListener(changeListener);
        control.autoSwitchProperty().removeListener(autoSwitchListener);
        control.showTimeProperty().removeListener(timeListener);
        control.animationTimeProperty().removeListener(timeListener);
        control.hoverProperty().removeListener(hoverPauseListener);
        control.arrowDisplayModeProperty().removeListener(arrowDisplayModeListener);
        control.navDisplayModeProperty().removeListener(navBarDisplayModelListener);
        control.hoverProperty().removeListener(arrowShowListener);
        control.hoverProperty().removeListener(navAutoShowListener);
        // 清空组件
        getChildren().clear();
        // 调用父类的dispose
        super.dispose();
    }

}
