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

import com.leewyatt.rxcontrols.animation.carousel.AnimHorMove;
import com.leewyatt.rxcontrols.animation.carousel.CarouselAnimation;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import com.leewyatt.rxcontrols.utils.RXResources;
import com.sun.javafx.css.converters.BooleanConverter;
import com.sun.javafx.css.converters.DurationConverter;
import com.sun.javafx.css.converters.EnumConverter;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 * 轮播图
 *
 */

public class RXCarousel extends Control {
    /**
     * 如果开启了自动翻页,那么默认每页显示时间
     */
    private static final Duration DEFAULT_SHOW_TIME = Duration.millis(350);
    /**
     * 默认的翻页动画时间
     */
    private static final Duration DEFAULT_ANIMATION_TIME = Duration.millis(600);
    /**
     * 添加css的类名
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-carousel";
    /**
     * 默认外观css
     */
    private static final String USER_AGENT_STYLESHEET = RXResources.load("/rx-controls.css")
            .toExternalForm();
    /**
     * 需要显示的项目
     */
    private final ObservableList<RXCarouselPane> paneList;
    /**
     * 过渡动画
     */
    private final SimpleObjectProperty<CarouselAnimation> carouselAnimation;
    /**
     * 当前选择的页索引
     */
    private final SimpleIntegerProperty selectedIndex;
    /**
     * 自动播放时的跳转.判断是否是首尾间的跳转;
     * 比如有的动画切换效果和运动顺序有关;比如水平移动图片动画
     * 假设有5个图片的切换,那么正在显示第5张图片时,点击导航显示第1张图片, 那么算逆序;呈现逆序的动画
     * 如果是自动播放,从第5张图片,切换到第1张图片, 那么算正序; 呈现正序的动画
     */
    private final SimpleBooleanProperty foreAndAftJump;

    //----css可以修饰的属性------------
    /**
     * 翻页动画的时间
     */
    private StyleableObjectProperty<Duration> animationTime;
    /**
     * 每页显示的时间
     */
    private StyleableObjectProperty<Duration> showTime;
    /**
     * 是否自动切换页面
     */
    private StyleableBooleanProperty autoSwitch;
    /**
     * 鼠标移入是否暂停
     */
    private StyleableBooleanProperty hoverPause;
    /**
     * 箭头显示方式(1 可见,2 隐藏,3 自动:鼠标移入显示,移除隐藏)
     */
    private StyleableObjectProperty<DisplayMode> arrowDisplayMode;
    /**
     * 导航按钮显示方式(1 可见,2 隐藏,3 自动:鼠标移入显示,移除隐藏)
     */
    private StyleableObjectProperty<DisplayMode> navDisplayMode;
    /**
     * 皮肤
     */
    private RXCarouselSkin skin;

    public final StyleableObjectProperty<Duration> animationTimeProperty() {
        if (animationTime == null) {
            animationTime = new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_TIME, this,
                    "animationTime", DEFAULT_ANIMATION_TIME);
        }
        return this.animationTime;
    }

    public final Duration getAnimationTime() {
        return animationTime == null ? DEFAULT_ANIMATION_TIME : animationTime.get();
    }

    public final void setAnimationTime(final Duration animationTime) {
        this.animationTimeProperty().set(animationTime);
    }

    public final StyleableObjectProperty<Duration> showTimeProperty() {
        if (showTime == null) {
            showTime = new SimpleStyleableObjectProperty<>(StyleableProperties.SHOW_TIME, this, "showTime",
                    DEFAULT_SHOW_TIME);
        }
        return this.showTime;
    }

    public final Duration getShowTime() {
        return showTime == null ? DEFAULT_SHOW_TIME : this.showTimeProperty().get();
    }

    public final void setShowTime(final Duration showTime) {
        this.showTimeProperty().set(showTime);
    }

    public final StyleableObjectProperty<DisplayMode> arrowDisplayModeProperty() {
        if (arrowDisplayMode == null) {
            arrowDisplayMode = new StyleableObjectProperty<DisplayMode>(DisplayMode.AUTO) {

                @Override
                public CssMetaData<? extends Styleable, DisplayMode> getCssMetaData() {
                    return StyleableProperties.ARROW_DISPLAY_MODE;
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "arrowDisplayMode";
                }
            };
        }

        return this.arrowDisplayMode;
    }

    public final DisplayMode getArrowDisplayMode() {
        return arrowDisplayMode == null ? DisplayMode.AUTO : arrowDisplayModeProperty().get();
    }

    public final void setArrowDisplayMode(final DisplayMode arrowDisplayMode) {
        this.arrowDisplayModeProperty().set(arrowDisplayMode);
    }

    public final StyleableObjectProperty<DisplayMode> navDisplayModeProperty() {
        if (navDisplayMode == null) {
            navDisplayMode = new StyleableObjectProperty<DisplayMode>(DisplayMode.SHOW) {
                @Override
                public CssMetaData<? extends Styleable, DisplayMode> getCssMetaData() {
                    return StyleableProperties.NAV_DISPLAY_MODE;
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "navDisplayMode";
                }
            };
        }
        return this.navDisplayMode;
    }

    public final DisplayMode getNavDisplayMode() {
        return navDisplayMode == null ? DisplayMode.SHOW : navDisplayMode.get();
    }

    public final void setNavDisplayMode(final DisplayMode navDisplayMode) {
        this.navDisplayModeProperty().set(navDisplayMode);
    }

    public final StyleableBooleanProperty hoverPauseProperty() {
        if (hoverPause == null) {
            hoverPause = new SimpleStyleableBooleanProperty(StyleableProperties.HOVER_PAUSE, RXCarousel.this,
                    "hoverPause", true);
        }
        return this.hoverPause;
    }

    public final boolean isHoverPause() {
        return hoverPause == null || hoverPause.get();
    }

    public final void setHoverPause(final boolean hoverPause) {
        this.hoverPauseProperty().set(hoverPause);
    }

    public final StyleableBooleanProperty autoSwitchProperty() {
        if (autoSwitch == null) {
            autoSwitch = new SimpleStyleableBooleanProperty(StyleableProperties.AUTO_SWITCH, RXCarousel.this,
                    "autoSwitch", true);
        }
        return this.autoSwitch;
    }

    public final boolean isAutoSwitch() {
        return autoSwitch == null || autoSwitch.get();
    }

    public final void setAutoSwitch(final boolean autoSwitch) {
        this.autoSwitchProperty().set(autoSwitch);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        skin = new RXCarouselSkin(this);
        return skin;
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    /**
     * 构造器
     */
    public RXCarousel() {
        setPrefSize(300, 200);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        selectedIndex = new SimpleIntegerProperty(this, "selectedIndex", 0);
        paneList = FXCollections.observableArrayList();
        carouselAnimation = new SimpleObjectProperty<CarouselAnimation>(this, "carouselAnimation", new AnimHorMove());
        foreAndAftJump = new SimpleBooleanProperty(this, "foreAndAftJump", false);
    }

    public RXCarousel(ObservableList<RXCarouselPane> paneList) {
        this();
        this.paneList.setAll(paneList);
    }

    public RXCarousel(RXCarouselPane... panes) {
        this(FXCollections.observableArrayList(panes));
    }

    protected final BooleanProperty foreAndAftJumpProperty() {
        return foreAndAftJump;
    }

    protected final boolean getForeAndAftJump() {
        return foreAndAftJump.get();
    }

    protected final void setForeAndAftJump(boolean value) {
        foreAndAftJump.set(value);
    }

    public CarouselAnimation getCarouselAnimation() {
        return carouselAnimation.get();
    }

    public SimpleObjectProperty<CarouselAnimation> carouselAnimationProperty() {
        return carouselAnimation;
    }

    public void setCarouselAnimation(CarouselAnimation carouselAnimation) {
        this.carouselAnimation.set(carouselAnimation);
    }

    public ObservableList<RXCarouselPane> getPaneList() {
        return paneList;
    }

    public void setPaneList(Collection<? extends RXCarouselPane> paneList) {
        this.paneList.setAll(paneList);
    }

    public void setPaneList(RXCarouselPane... panes) {
        this.paneList.setAll(panes);
    }

    public final IntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }

    public final int getSelectedIndex() {
        return selectedIndexProperty().get();
    }

    /**
     * 自定义css样式
     */
    private static class StyleableProperties {

        private static final CssMetaData<RXCarousel, Boolean> HOVER_PAUSE = new CssMetaData<RXCarousel, Boolean>(
                "-rx-hover-pause", BooleanConverter.getInstance(), true) {

            @Override
            public boolean isSettable(RXCarousel control) {
                return control.hoverPause == null || !control.hoverPause.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(RXCarousel control) {
                return control.hoverPauseProperty();
            }
        };

        // 导航栏显示css
        private static final CssMetaData<RXCarousel, DisplayMode> NAV_DISPLAY_MODE = new CssMetaData<RXCarousel, DisplayMode>(
                "-rx-nav-display", new EnumConverter<DisplayMode>(DisplayMode.class), DisplayMode.SHOW) {
            @Override
            public boolean isSettable(RXCarousel control) {
                return control.navDisplayMode == null || !control.navDisplayMode.isBound();
            }

            @Override
            public StyleableProperty<DisplayMode> getStyleableProperty(RXCarousel control) {
                return control.navDisplayModeProperty();
            }
        };

        // 箭头显示css
        private static final CssMetaData<RXCarousel, DisplayMode> ARROW_DISPLAY_MODE = new CssMetaData<RXCarousel, DisplayMode>(
                "-rx-arrow-display", new EnumConverter<DisplayMode>(DisplayMode.class), DisplayMode.AUTO) {
            @Override
            public boolean isSettable(RXCarousel control) {
                return control.arrowDisplayMode == null || !control.arrowDisplayMode.isBound();
            }

            @Override
            public StyleableProperty<DisplayMode> getStyleableProperty(RXCarousel control) {
                return control.arrowDisplayModeProperty();
            }
        };

        private static final CssMetaData<RXCarousel, Duration> ANIMATION_TIME = new CssMetaData<RXCarousel, Duration>(
                "-rx-animation-time", DurationConverter.getInstance(), DEFAULT_ANIMATION_TIME) {

            @Override
            public boolean isSettable(RXCarousel control) {
                return control.animationTime == null || !control.animationTime.isBound();
            }

            @Override
            public StyleableProperty<Duration> getStyleableProperty(RXCarousel control) {
                return control.animationTimeProperty();
            }
        };
        private static final CssMetaData<RXCarousel, Duration> SHOW_TIME = new CssMetaData<RXCarousel, Duration>(
                "-rx-show-time", DurationConverter.getInstance(), DEFAULT_SHOW_TIME) {

            @Override
            public boolean isSettable(RXCarousel control) {
                return control.showTime == null || !control.showTime.isBound();
            }

            @Override
            public StyleableProperty<Duration> getStyleableProperty(RXCarousel control) {
                return control.showTimeProperty();
            }
        };
        private static final CssMetaData<RXCarousel, Boolean> AUTO_SWITCH = new CssMetaData<RXCarousel, Boolean>(
                "-rx-auto-switch", BooleanConverter.getInstance(), true) {

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(RXCarousel control) {
                return control.autoSwitchProperty();
            }

            @Override
            public boolean isSettable(RXCarousel control) {
                return control.autoSwitch == null || !control.autoSwitch.isBound();
            }
        };

        // 创建一个CSS样式的表
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, HOVER_PAUSE, ANIMATION_TIME, SHOW_TIME, AUTO_SWITCH, ARROW_DISPLAY_MODE,
                    NAV_DISPLAY_MODE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * 这个方法写了,可以在SceneBuilder的Style里出现等提示
     *
     * @return css样式
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * 传入索引,跳转到指定页面: 不判断是否是尾->首 或者 首->尾的模式
     *
     * @param index
     */
    public final void setSelectedIndex(int index) {
        if (paneList == null || paneList.size() == 0 || index < 0 || index > paneList.size() - 1) {
            return;
        }
        selectedIndexProperty().set(index);
    }

    //----------下一页/上一页 支持判断首->尾 方向;可以调用循环动画------

    /**
     * 翻到下一页:
     */
    public void showNextPane() {
        if (paneList.size() == 0) {
            return;
        }
        int value = getSelectedIndex() + 1;
        if (value > paneList.size() - 1) {
            value = 0;
        }
        foreAndAftJumpProperty().set(true);
        selectedIndexProperty().set(value);

    }

    /**
     * 翻到上一页
     */
    public void showPreviousPane() {
        if (paneList.size() == 0) {
            return;
        }
        int value = getSelectedIndex() - 1;
        if (value < 0) {
            value = paneList.size() - 1;
        }
        foreAndAftJumpProperty().set(true);
        selectedIndexProperty().set(value);
    }

    public List<RXToggleButton> getNavButtons() {
        return skin.getNavButtons();
    }


    /**
     * 翻转方向/运动方向
     */
    public enum RXDirection {
        /**
         * 垂直
         */
        VER,
        /**
         * 水平
         */
        HOR
    }
}
