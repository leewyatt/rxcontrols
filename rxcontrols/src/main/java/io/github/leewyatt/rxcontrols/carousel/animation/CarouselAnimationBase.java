package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * Base class for carousel animations that provides a {@code finishAction} template.
 *
 * <p>Subclasses set a {@link Runnable} via {@link #setFinishAction(Runnable)} that
 * captures the animation's end state. This action is shared between the animation's
 * {@code onFinished} handler and {@link #jumpToEnd()}, ensuring both paths produce
 * the same visual result.</p>
 */
public abstract class CarouselAnimationBase implements CarouselAnimation {

    private Animation currentAnimation;
    private Runnable finishAction;
    private ChangeListener<Number> resizeListener;
    private StackPane resizeGuardPane;

    /**
     * Sets the action to execute when the animation finishes or is jumped to the end.
     *
     * @param action the finish action
     */
    protected void setFinishAction(Runnable action) {
        this.finishAction = action;
    }

    /**
     * Stores a reference to the currently playing animation so it can be stopped
     * by {@link #jumpToEnd()}.
     *
     * @param animation the current animation
     */
    protected void setCurrentAnimation(Animation animation) {
        this.currentAnimation = animation;
    }

    /**
     * Returns the currently playing animation, or {@code null} if none.
     *
     * @return the current animation
     */
    protected Animation getCurrentAnimation() {
        return currentAnimation;
    }

    @Override
    public void jumpToEnd() {
        if (currentAnimation != null) {
            currentAnimation.stop();
            currentAnimation = null;
        }
        if (finishAction != null) {
            finishAction.run();
            finishAction = null;
        }
    }

    @Override
    public void clearEffects(TransitionContext context) {
        Pane effectPane = context.getEffectPane();
        if (effectPane != null) {
            effectPane.getChildren().clear();
        }
    }

    /**
     * Installs a resize guard on the given content pane. If the pane is resized
     * while the animation is playing, {@link #jumpToEnd()} is called automatically.
     *
     * <p>This is intended for animations that are not resize-safe (e.g. snapshot-based
     * animations). Call {@link #removeResizeGuard()} in the finish action, clearEffects,
     * and dispose methods.</p>
     *
     * @param contentPane the content pane to monitor
     */
    protected void installResizeGuard(StackPane contentPane) {
        removeResizeGuard();
        resizeGuardPane = contentPane;
        resizeListener = (obs, oldVal, newVal) -> jumpToEnd();
        contentPane.widthProperty().addListener(resizeListener);
        contentPane.heightProperty().addListener(resizeListener);
    }

    /**
     * Removes a previously installed resize guard.
     */
    protected void removeResizeGuard() {
        if (resizeListener != null && resizeGuardPane != null) {
            resizeGuardPane.widthProperty().removeListener(resizeListener);
            resizeGuardPane.heightProperty().removeListener(resizeListener);
            resizeListener = null;
            resizeGuardPane = null;
        }
    }

    @Override
    public void dispose() {
        if (currentAnimation != null) {
            currentAnimation.stop();
            currentAnimation = null;
        }
        removeResizeGuard();
        finishAction = null;
    }
}
