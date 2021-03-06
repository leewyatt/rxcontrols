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
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
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
 * QQ???: 518914410
 *
 * !!!  ????????????????????????, ???Timeline ?????? Timer? ???????????????....
 * Timer ??? ??????java????????? ????????????ScheduledExecutorService??????Timer???
 *
 */
public class RXCarouselSkin extends SkinBase<RXCarousel> {

    private RXCarousel control;
    private ObservableList<RXCarouselPane> paneList;
    /**
     * ????????????
     */
    private final SubScene subScene;
    /**
     * ????????????????????????(??????????????????,????????????,????????????,????????????)
     */
    private final StackPane rootPane;
    /**
     * ???????????????(????????????????????????)
     */
    private StackPane contentPane;
    /**
     * ????????????( ?????????.. )
     */
    private Pane effectPane;

    /**
     * ????????? (??????, ?????? ?????? ???????????????)
     */
    private final Pane navigationPane;
    /**
     * ??????????????????
     */
    private int currentIndex = -1;
    /**
     * ??????????????????
     */
    private int nextIndex = -1;
    /**
     * ???????????????
     */
    private Animation animation;
    /**
     * ??????????????????
     */
    private Timer autoSwitchTimer;
    //--------?????????????????????------------
    /**
     * ???????????????
     */
    private StackPane leftButton;
    /**
     * ????????????
     */
    private final Region leftArrow;
    /**
     * ???????????????
     */
    private StackPane rightButton;
    /**
     * ????????????
     */
    private final Region rightArrow;
    /**
     * ?????????
     */
    private FlowPane navBar;

    private final ToggleGroup btnGroup = new ToggleGroup();
    /**
     * ??????????????????
     */
    private final Timeline tlArrowShow = new Timeline();
    /**
     * ??????????????????
     */
    private final Timeline tlArrowHide = new Timeline();
    /**
     * ?????????????????????????????????
     */
    private final Timeline tlNavShow = new Timeline();
    /**
     * ?????????????????????
     */
    private final Timeline tlNavHide = new Timeline();
    /**
     * ????????????????????????????????? ??????
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

        //????????? ?????? ????????????????????? ???????????????????????????
        navigationPane = new Pane();
        navigationPane.getStyleClass().add("nav-pane");
        navigationPane.setPickOnBounds(false);

        //?????? ???????????????
        leftButton = new StackPane();
        leftButton.getStyleClass().add("left-button");
        leftArrow = new Region();
        leftArrow.getStyleClass().add("left-arrow");
        leftButton.getChildren().setAll(leftArrow);
        //????????????,???????????????
        leftButton.setOnMouseClicked(event -> control.showPreviousPane());
        //??????  ???????????????
        rightButton = new StackPane();
        rightButton.getStyleClass().add("right-button");
        rightArrow = new Region();
        rightArrow.getStyleClass().add("right-arrow");
        rightButton.getChildren().setAll(rightArrow);
        //????????????,???????????????
        rightButton.setOnMouseClicked(event -> control.showNextPane());

        //?????????: ?????????????????????
        navBar = new FlowPane(Orientation.HORIZONTAL);
        navBar.getStyleClass().add("nav-bar");
        navBar.setLayoutX(0);
        //??????????????????.
        navBar.prefWidthProperty().bind(control.prefWidthProperty());
        //????????? layoutY?????????
        navBar.layoutYProperty().bind(navigationPane.heightProperty().subtract(navBar.heightProperty()));

        navigationPane.getChildren().setAll(leftButton, rightButton, navBar);
        // ????????????(??????????????????????????????????????????)
        //SubScene ??????????????????????????????
//        clipRegion(control);
//        clipRegion(rootPane);

        // ????????????
        DoubleProperty dbw = control.prefWidthProperty();
        DoubleProperty dbh = control.prefHeightProperty();
        rootPane.minWidthProperty().bind(dbw);
        rootPane.prefWidthProperty().bind(dbw);
        rootPane.minHeightProperty().bind(dbh);
        rootPane.prefHeightProperty().bind(dbh);
    /* ????????????????????????setClip ,????????????????????????clip??????
        ??????,?????????RXCarouselPane ??????????????????????????????
       for (RXCarouselPane pane : paneList) {
            clipRegion(pane);
        }*/
        // ??????????????????????????????
        subScene = new SubScene(contentPane, dbw.get(), dbh.get(), false, SceneAntialiasing.BALANCED);
        subScene.getStyleClass().add("carousel-subscene");
        rootPane.getChildren().addAll(subScene, effectPane, navigationPane);
        contentPane.backgroundProperty().bind(control.backgroundProperty());
        subScene.widthProperty().bind(control.prefWidthProperty());
        subScene.heightProperty().bind(control.prefHeightProperty());
        subScene.setCamera(new PerspectiveCamera());

        getChildren().setAll(rootPane);
        getSkinnable().requestLayout();

        //??????Listener
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
        init();//???????????????

    }

    /**
     * ????????? ???????????? ???ToggleGroup????????????
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
     * ????????????????????????????????????????????????
     */
    private final InvalidationListener arrowDisplayModeListener = ob -> setArrowButtonShowCon();

    /**
     * ?????????????????????????????????????????????
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
            // ????????????
            if (nv) {
                navBar.setOpacity(0);
                tlNavShow.getKeyFrames()
                        .setAll(new KeyFrame(DURATION_ARROW_SHOW, new KeyValue(navBar.opacityProperty(), 1.0)));
                tlNavShow.play();
                // ????????????
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
     * ??????????????????
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
     * ???????????? ?????? ?????????????????????
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
        //??????????????????
        initNavBar();

        contentPane.getChildren().setAll(paneList);
        //???????????????,?????????????????????????????????Pane?????????,?????? ??? ??????
        control.getCarouselAnimation().clearEffects(paneList, effectPane, currentIndex, nextIndex);

        //???????????????,???????????????
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
            //??????????????????2????????????????????????
            if (size > 1) {
                initTimer();
            }
        }

        //?????? ??????????????????
        setArrowButtonLocation();
        //?????? ????????????
        setArrowButtonShowCon();
        //?????????????????????
        setNavBarShowCon();

    }

    /**
     * ??????????????????????????????
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
            // ????????????
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
                // ????????????
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
        // 1: ?????????????????????
        if (animation != null) {
            animation.stop();
        }
        oldAnimation.clearEffects(paneList, effectPane, currentIndex, nextIndex);
        newAnimation.initAnimation(contentPane, effectPane, paneList, currentIndex, nextIndex);
    };

    private final ChangeListener<Number> selectedIndexChangeListener = (ob, ov, nv) -> {
        currentIndex = ov.intValue();
        nextIndex = nv.intValue();
        //?????????????????????, ??????????????? ????????????Pane?????????,???????????????if
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
     * ??????????????????
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
                // btn.managedProperty().bind(btn.visibleProperty());// ????????????????????????
                navBar.getChildren().add(btn);
                btnGroup.getToggles().add(btn);
                int finalIndex = i;
                btn.setOnAction(event -> {
                    btnGroup.selectToggle(btn);
                    control.setSelectedIndex(finalIndex);
                });
            }

            int tempIndex = control.getSelectedIndex();
            //????????????????????????????????????
            if (tempIndex < size && tempIndex > -1) {
                btnGroup.getToggles().get(tempIndex).setSelected(true);
                //????????????????????????,??????????????????1???,?????????????????????
            } else {
                btnGroup.getToggles().get(0).setSelected(true);
            }
        }
        //setNavBarShowCon();
    }

    private void playAnimation() {
        //??????????????????
        if (nextIndex < paneList.size() && nextIndex > -1) {
            btnGroup.getToggles().get(nextIndex).setSelected(true);
        }
        // 1: ?????????????????????
        if (animation != null) {
            animation.stop();
        }
        // 2: ?????????????????????Animation
        CarouselAnimation carouselAnimation = control.getCarouselAnimation();
        animation = carouselAnimation.getAnimation(control, contentPane, effectPane, paneList, currentIndex, nextIndex, control.foreAndAftJumpProperty().get(), control.getAnimationTime());
        control.foreAndAftJumpProperty().set(false);

        // 3: ????????????;
        if (animation != null) {
            animation.play();
        }
    }

    /**
     * ??????????????????????????????
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
        stopTimer();//?????????
        autoSwitchTimer = new Timer(true);
        //?????????????????? = ???????????? + ??????(??????)??????
        long period = (long) (control.getShowTime().toMillis() + control.getAnimationTime().toMillis());
        autoSwitchTimer.schedule(new TimerTask() {
                                     @Override
                                     public void run() {
                                         //???UI???????????????????????????
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
            //??????
            autoSwitchTimer.cancel();
            //??????
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
     * ?????????????????????(skin),?????????????????????,????????????
     */
    @Override
    public void dispose() {
        //????????????????????????
        stopTimer();
        // ????????????
        if (animation != null) {
            animation.stop();
            animation = null;
        }

       //????????????
        for (RXToggleButton button : getNavButtons()) {
            button.textProperty().unbind();
        }
        contentPane.backgroundProperty().unbind();
        subScene.widthProperty().unbind();
        subScene.heightProperty().unbind();
        rootPane.minWidthProperty().unbind();
        rootPane.prefWidthProperty().unbind();
        rootPane.minHeightProperty().unbind();
        rootPane.prefHeightProperty().unbind();
        navBar.layoutYProperty().unbind();
        navBar.prefWidthProperty().bind(control.prefWidthProperty());
        rightButton.layoutXProperty().unbind();
        rightButton.layoutYProperty().unbind();
        leftButton.layoutYProperty().unbind();
        // ??????Listener
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
        // ????????????
        getChildren().clear();
        // ???????????????dispose
        super.dispose();
    }

    public SubScene getSubScene() {
        return subScene;
    }
}
