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
package app.ui;

import com.leewyatt.rxcontrols.animation.carousel.AnimVerBlinds;
import com.leewyatt.rxcontrols.animation.carousel.CarouselAnimation;
import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.enums.DisplayMode;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class TestCarousel extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white");
        RXCarousel carousel = new RXCarousel();
        carousel.setPrefSize(500, 300);
        String[] colors = {"#ebe8ff", "#fffaad", " #d5ffd1"};
        for (int i = 0; i < colors.length; i++) {
            // 添加RXCarouselPane 到 RXCarousel里
            carousel.getPaneList().add(initPane(i, colors[i]));
        }
        //给轮播图设置动画类型;
        //CarouselAnimation 是接口. AnimVerBlinds是实现类
        //所有的实现类都在 com.leewyatt.rxcontrols.animation.carousel.*;
        CarouselAnimation anim = new AnimVerBlinds(20);
        carousel.setCarouselAnimation(anim);
        //设置页面切换的动画时间
        //carousel.setAnimationTime(Duration.millis(600));
        //设置导航条为显示
        //carousel.setNavDisplayMode(DisplayMode.SHOW);
        //设置前进后退按钮为 鼠标移入显示. 鼠标移除隐藏
        carousel.setArrowDisplayMode(DisplayMode.AUTO);
        //设置当鼠标移入轮播图时停止自动播放
        //carousel.setHoverPause(true);
        //设置每一页的显示时间
        //carousel.setShowTime(Duration.millis(1500));
        //设置初始选中下标为2的页面
        //carousel.setSelectedIndex(2);
        //设置自动播放/切换 轮播图
        carousel.setAutoSwitch(true);

        root.setCenter(carousel);
        Scene scene = new Scene(root, 530, 380);
        primaryStage.setScene(scene);
        primaryStage.setTitle("轮播图组件");
        primaryStage.show();

        //如果很在乎内存,那么在一个动画效果不用的时候,可以调用dispose,销毁
        //anim.dispose(); //动画的 dispose方法里一般都是清空动画,解除绑定等操作
        //anim = null;//然后置空
        //System.gc();

    }

    public static void main(String[] args) {
        launch(args);
    }

    private RXCarouselPane initPane(int index, String webColor) {
        //把需要的图片或者组件,放到RXCarouselPane 里;
        //为了保持更佳的切换效果,建议所有的RXCarouselPane和RXCarousel大小保持一致
        Label label = new Label(" Pane " + (index + 1));
        label.setStyle("-fx-font-size: 20px;-fx-font-weight: 900");
        RXCarouselPane pane1 = new RXCarouselPane(label);
        pane1.setStyle("-fx-background-color: " + webColor + ";");
        //如果不怕麻烦. 为了更好的动画效果 ,可以实现下面四个方法;轮播图组件会在适当的时候被调用;
        pane1.setOnOpening(event -> {
            //System.out.println("正在打开 Pane" + index + " is on opening..");
        });
        pane1.setOnOpened(event -> {
            //System.out.println("已经打开 Pane" + index + " is on opened..");
        });

        pane1.setOnClosing(event -> {
            // System.out.println("正在关闭 Pane" + index + " is on closing..");
        });
        pane1.setOnClosed(event -> {
            //System.out.println("已经关闭 Pane" + index + " is on closed..");
        });
        return pane1;
    }
}
