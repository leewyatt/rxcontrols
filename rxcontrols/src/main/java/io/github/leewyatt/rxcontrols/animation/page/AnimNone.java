package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * No-animation transition. Switches pages at once as a transition of zero
 * length, so a host still goes through its transition: its transitioning
 * state and its lifecycle events behave as with any other animation.
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

        PauseTransition pause = new PauseTransition(Duration.ZERO);
        pause.setOnFinished(e -> finish.run());

        setCurrentAnimation(pause);
        return pause;
    }
}
