package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * No-animation transition. Instantly switches pages while still firing
 * the full lifecycle event sequence (CLOSING, OPENING, CLOSED, OPENED).
 */
public class AnimNone extends PageAnimationBase {

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        PauseTransition pause = new PauseTransition(Duration.ZERO);
        pause.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(pause);
        return pause;
    }
}
