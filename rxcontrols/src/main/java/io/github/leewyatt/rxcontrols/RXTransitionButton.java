package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.transition.ContentBias;
import io.github.leewyatt.rxcontrols.skins.RXTransitionButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Skin;

/**
 * A button with two content faces: the normal face is the button's own text
 * and graphic, the alternate face is the {@link #alternateContentProperty()
 * alternateContent} node. While the {@link #animationTriggerProperty()
 * trigger} state is active (hover by default), the button plays a
 * {@link PageAnimation} transition to the alternate face and plays back when
 * it turns inactive.
 *
 * <p>Any {@link PageAnimation} preset can drive the swap; the default is a
 * vertical slide. A {@code null} alternate content, a {@code Duration.ZERO}
 * duration, multi-page display animations, and animations requiring more
 * than two pages switch with a direct cut. Interrupting a running transition
 * jumps it to its end state before the new one starts (latest wins).</p>
 *
 * <p>Keyboard activation (SPACE, and ENTER on non-Mac platforms), default
 * and cancel button accelerators, ripple feedback and accessibility follow
 * standard button semantics. Mnemonic parsing and arrow-key focus traversal
 * are not supported by this control's skin.</p>
 */
public class RXTransitionButton extends RXAnimatedButton {

    /**
     * The default style class of this control.
     */
    public static final String DEFAULT_STYLE_CLASS = "rx-transition-button";

    // ==================== Constructors ====================

    /**
     * Creates a transition button with an empty text caption.
     */
    public RXTransitionButton() {
        init();
    }

    /**
     * Creates a transition button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXTransitionButton(@NamedArg("text") String text) {
        super(text);
        init();
    }

    /**
     * Creates a transition button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXTransitionButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        init();
    }

    private void init() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    // ==================== Alternate Content ====================

    private final ObjectProperty<Node> alternateContent =
            new SimpleObjectProperty<>(this, "alternateContent");

    /**
     * The content shown while the trigger state is active. {@code null}
     * shows an empty face with a direct cut.
     *
     * @return the alternate content property
     */
    public final ObjectProperty<Node> alternateContentProperty() {
        return alternateContent;
    }

    /**
     * Returns the alternate face content.
     *
     * @return the alternate content, or {@code null}
     */
    public final Node getAlternateContent() {
        return alternateContent.get();
    }

    /**
     * Sets the alternate face content.
     *
     * @param value the alternate content, may be {@code null}
     */
    public final void setAlternateContent(Node value) {
        alternateContent.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation",
                    new AnimSlide(Orientation.VERTICAL));

    /**
     * The animation used for face transitions. Any {@link PageAnimation}
     * implementation can be used; a {@code null} animation, multi-page
     * display animations, and animations requiring more than two pages fall
     * back to a direct cut.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for face transitions.
     *
     * @return the animation
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for face transitions.
     *
     * @param value the animation
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Transitioning ====================

    private final ReadOnlyBooleanWrapper transitioning =
            new ReadOnlyBooleanWrapper(this, "transitioning", false);

    /**
     * Whether a face transition is currently playing (read-only).
     *
     * @return the transitioning property
     */
    public final ReadOnlyBooleanProperty transitioningProperty() {
        return transitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a face transition is currently playing.
     *
     * @return {@code true} while a transition is playing
     */
    public final boolean isTransitioning() {
        return transitioning.get();
    }

    /**
     * Updates the transitioning flag. This method is intended to be used by
     * experts, primarily by those implementing new Skins. It is not common
     * for developers to access this method directly.
     *
     * @param value true if a face transition is in progress
     */
    public final void setTransitioning(boolean value) {
        transitioning.set(value);
    }

    // ==================== Layout ====================

    /**
     * Advertises the content bias merged from both faces, so a parent layout
     * measures the button against the correct axis when either face has a
     * content bias. The front face contributes {@code super.getContentBias()}
     * (the button's own {@code wrapText}); the alternate face contributes its
     * node's bias. The merge gives HORIZONTAL priority, matching the fixed-face
     * sizing of {@code RXBox} and JavaFX {@code StackPane}.
     *
     * @return the merged content bias, or {@code null} if neither face has one
     */
    @Override
    public Orientation getContentBias() {
        return ContentBias.merge(super.getContentBias(), ContentBias.of(getAlternateContent()));
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTransitionButtonSkin(this);
    }
}
