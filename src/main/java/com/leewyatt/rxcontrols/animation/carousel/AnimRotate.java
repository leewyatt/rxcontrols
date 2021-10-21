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

import com.leewyatt.rxcontrols.controls.RXCarousel;
import com.leewyatt.rxcontrols.pane.RXCarouselPane;
import javafx.animation.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.List;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 */
public class AnimRotate extends CarouselAnimationBase {
    private final Timeline animation;
    private final Rotate rotate;

    public AnimRotate() {
        rotate= new Rotate();
        rotate.setPivotX(0);
        rotate.setAxis(Rotate.Z_AXIS);
        animation = new Timeline();
    }


    @Override
    public Animation getAnimation(RXCarousel rxCarousel, StackPane contentPane, Pane effectPane, List<RXCarouselPane> panes, int currentIndex, int nextIndex, boolean foreAndAftJump, Duration animationTime) {
        reset(panes, nextIndex, currentIndex);
        RXCarouselPane currentPane = panes.get(currentIndex);
        RXCarouselPane nextPane = panes.get(nextIndex);

        boolean direction = CarouselAnimUtil.computeDirection(panes, currentIndex, nextIndex, foreAndAftJump);
        rotate.setPivotY(CarouselAnimUtil.getPaneHeight(contentPane));
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        event -> {
                            if (direction) {
                                currentPane.toFront();
                                currentPane.getTransforms().add(rotate);
                            } else {
                                nextPane.toFront();
                                nextPane.getTransforms().add(rotate);
                            }
                           nextPane.fireOpening();
                            currentPane.fireClosing();
                        },
                        new KeyValue(rotate.angleProperty(), direction ? 0 :90)),
                new KeyFrame(animationTime, new KeyValue(rotate.angleProperty(), direction ?  90:0,Interpolator.EASE_IN)));

        animation.setOnFinished(event -> {
            nextPane.getTransforms().clear();
            nextPane.fireOpened();
            currentPane.setVisible(false);
            currentPane.getTransforms().clear();
            currentPane.fireClosed();

        });
        return animation;
    }

    @Override
    public void clearEffects(List<RXCarouselPane> panes, Pane effectPane, int currentIndex, int nextIndex) {
        reset(panes, nextIndex);
    }

    private void reset(List<RXCarouselPane> panes, int... showArrayIndex) {
        for (int i = 0; i < panes.size(); i++) {
            RXCarouselPane pane = panes.get(i);
            CarouselAnimUtil.showPanes(i, pane, showArrayIndex);
            pane.getTransforms().clear();
        }
    }

    @Override
    public void dispose() {
        CarouselAnimUtil.disposeTimeline(animation);
    }
}
