package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.event.RXTransitionPaneEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.transition.ContentBias;
import io.github.leewyatt.rxcontrols.skins.RXTransitionPaneSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A container that plays a {@link PageAnimation} transition whenever its
 * {@link #contentProperty() content} changes.
 *
 * <p>The pane hosts a single content node. Setting a new content while one is
 * displayed plays the configured animation from the old node to the new one;
 * the first content, a {@code null} content, and a change from {@code null}
 * switch with a direct cut. When a new content arrives while a transition is
 * still playing, the running transition jumps to its end state and the new
 * one starts (latest wins).</p>
 *
 * <p>The transition direction is taken from the {@link #directionProperty()
 * direction} property at the moment the content changes;
 * {@link #transitionTo(Node, TransitionDirection)} sets both in one call.
 * Multi-page display animations and animations requiring more than two pages
 * fall back to a direct cut.</p>
 *
 * <p>The pane sizes to its current content, so contents of differing sizes
 * resize it at the start of the transition (a shrinking change clips the
 * outgoing content while it leaves). Set a fixed preferred size for a stable
 * layout across transitions.</p>
 *
 * <h2>Content lifecycle</h2>
 *
 * <p>The pane fires {@link RXTransitionPaneEvent}s at the content nodes as
 * they become and stop being its content. A page can listen on itself, and
 * the pane offers {@link #onContentOpeningProperty() onContentOpening},
 * {@link #onContentOpenedProperty() onContentOpened},
 * {@link #onContentClosingProperty() onContentClosing} and
 * {@link #onContentClosedProperty() onContentClosed} for all of its
 * contents. The events stop at the owning pane, so a nested pane's contents
 * never reach the outer pane or the outer page.</p>
 *
 * <p>An animated change from {@code A} to {@code B}:</p>
 * <pre>
 * CLOSING(A)  OPENING(B)   transitioning = true, animation plays
 * ...
 * transitioning = false    CLOSED(A)  OPENED(B)   A is removed
 * </pre>
 *
 * <p>A direct cut fires {@code CLOSING(A) CLOSED(A)}, removes {@code A}, then
 * fires {@code OPENING(B) OPENED(B)}; a change from or to {@code null} fires
 * only the half that applies, and setting the same node again fires nothing.
 * The initial content receives {@code OPENING} / {@code OPENED} when the skin
 * is installed, normally during the pane's first CSS pass, so register
 * handlers before the pane is first shown. Replacing the skin does not
 * repeat the events; removing it closes the content, and the next skin opens
 * the content current at that time.</p>
 *
 * <p>Every node that receives {@code OPENING} receives {@code OPENED}, and
 * every node that receives {@code CLOSING} receives {@code CLOSED}: an
 * interrupted transition jumps to its end and completes its events before
 * the next change starts. {@code isTransitioning()} is {@code false} during
 * every event. At {@code OPENING} and {@code CLOSED} the node is in the pane
 * but hidden; at {@code CLOSING} and {@code OPENED} it is shown. These states
 * hold when an event starts its dispatch: a handler that replaces or removes
 * the skin changes them for the handlers that run after it. The pane
 * hides and shows internal wrappers, never the content node itself. When the
 * animation is stopped from outside, for example by its own resize guard,
 * the completing events follow on the next turn of the event loop.</p>
 *
 * <p>Handlers may change the content. The change is applied once the current
 * step has completed its events, and only the latest content is opened: a
 * node replaced before its {@code OPENING} receives no events, and a node
 * replaced after its {@code OPENING} but before it shows receives
 * {@code OPENED}, {@code CLOSING} and {@code CLOSED} without ever being
 * shown. A change made in an {@code OPENING} or {@code CLOSING} handler skips
 * the now outdated animation. The events are notifications and cannot veto a
 * change. An exception thrown by a handler ends that event's dispatch, as for
 * any JavaFX event; the pane reports it to the thread's uncaught exception
 * handler and still completes the sequence.</p>
 *
 * <p>The lifecycle tracks whether a node is the pane's content, not whether
 * it is on screen: hiding the pane or an ancestor, switching tabs, or closing
 * the window fires no content events. Work that should pause while hidden
 * needs both signals: running while opened and actually showing, as reported
 * by {@link io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty}.</p>
 */
@DefaultProperty("content")
public class RXTransitionPane extends Control {

    /**
     * The default style class of this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-transition-pane";

    /**
     * The default transition duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(500.0);

    // ==================== Constructors ====================

    /**
     * Creates an empty transition pane.
     */
    public RXTransitionPane() {
        this(null);
    }

    /**
     * Creates a transition pane showing the given content.
     *
     * @param content the initial content, may be {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXTransitionPane(Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Display-only container: initialize focusTraversable to false with a
        // null StyleOrigin so CSS can still override it, mirroring Label and
        // ProgressIndicator.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        // Content events stop at their own pane, so an outer pane or page
        // never receives a nested pane's lifecycle. Handlers on this pane
        // still run: consuming only stops the event leaving this node.
        addEventHandler(RXTransitionPaneEvent.ANY, event -> {
            if (event.getPane() == this) {
                event.consume();
            }
        });
        setContent(content);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content =
            new SimpleObjectProperty<>(this, "content");

    /**
     * The content displayed by this pane. Changing it plays the configured
     * transition; {@code null} shows an empty pane.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content displayed by this pane.
     *
     * @return the content, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content displayed by this pane.
     *
     * @param value the content, may be {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether content changes should animate. When {@code false}, contents
     * switch with a direct cut.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether content changes should animate.
     *
     * @return {@code true} if content transitions are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether content changes should animate.
     *
     * @param value {@code true} to animate content transitions
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimFade());

    /**
     * The animation used for content transitions. Any {@link PageAnimation}
     * implementation can be used; a {@code null} animation, multi-page
     * display animations, and animations requiring more than two pages fall
     * back to a direct cut.
     * A {@code PageAnimation} instance may be reused sequentially, but must
     * not drive concurrent transitions or be shared by multiple live hosts.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for content transitions.
     *
     * @return the animation
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for content transitions.
     *
     * @param value the animation
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the content transition animation. Non-positive, unknown,
     * indefinite, or {@code null} values fall back to a direct cut. Also
     * settable from CSS via {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of the content transition animation.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of the content transition animation.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Direction ====================

    private final ObjectProperty<TransitionDirection> direction =
            new SimpleObjectProperty<>(this, "direction", TransitionDirection.FORWARD);

    /**
     * The logical direction handed to the animation when the content changes.
     * A {@code null} value is treated as {@link TransitionDirection#FORWARD}.
     *
     * @return the direction property
     */
    public final ObjectProperty<TransitionDirection> directionProperty() {
        return direction;
    }

    /**
     * Returns the transition direction.
     *
     * @return the direction
     */
    public final TransitionDirection getDirection() {
        return direction.get();
    }

    /**
     * Sets the transition direction.
     *
     * @param value the direction
     */
    public final void setDirection(TransitionDirection value) {
        direction.set(value);
    }

    /**
     * Sets the direction and the content in one call, so the change plays in
     * the given direction.
     *
     * @param value              the new content, may be {@code null}
     * @param transitionDirection the direction for this change
     */
    public final void transitionTo(Node value, TransitionDirection transitionDirection) {
        setDirection(transitionDirection);
        setContent(value);
    }

    // ==================== Transitioning ====================

    private final ReadOnlyBooleanWrapper transitioning =
            new ReadOnlyBooleanWrapper(this, "transitioning", false);

    /**
     * Whether a content transition is currently playing (read-only).
     *
     * @return the transitioning property
     */
    public final ReadOnlyBooleanProperty transitioningProperty() {
        return transitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a content transition is currently playing.
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
     * @param value true if a content transition is in progress
     */
    public final void setTransitioning(boolean value) {
        transitioning.set(value);
    }

    // ==================== Content Lifecycle Handlers ====================

    private ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentOpening;

    /**
     * Called when a node is about to become this pane's content. Equivalent
     * to {@code addEventHandler(RXTransitionPaneEvent.OPENING, handler)},
     * except that it holds a single handler.
     *
     * @return the onContentOpening handler property
     * @see RXTransitionPaneEvent#OPENING
     */
    public final ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentOpeningProperty() {
        if (onContentOpening == null) {
            onContentOpening = new HandlerProperty("onContentOpening", RXTransitionPaneEvent.OPENING);
        }
        return onContentOpening;
    }

    /**
     * Returns the handler called when a node is about to become the content.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTransitionPaneEvent> getOnContentOpening() {
        return onContentOpening == null ? null : onContentOpening.get();
    }

    /**
     * Sets the handler called when a node is about to become the content.
     *
     * @param value the handler, may be {@code null}
     */
    public final void setOnContentOpening(EventHandler<RXTransitionPaneEvent> value) {
        onContentOpeningProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentOpened;

    /**
     * Called when a node has become this pane's content and is fully shown.
     * Equivalent to {@code addEventHandler(RXTransitionPaneEvent.OPENED, handler)},
     * except that it holds a single handler.
     *
     * @return the onContentOpened handler property
     * @see RXTransitionPaneEvent#OPENED
     */
    public final ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentOpenedProperty() {
        if (onContentOpened == null) {
            onContentOpened = new HandlerProperty("onContentOpened", RXTransitionPaneEvent.OPENED);
        }
        return onContentOpened;
    }

    /**
     * Returns the handler called when a node has become the content.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTransitionPaneEvent> getOnContentOpened() {
        return onContentOpened == null ? null : onContentOpened.get();
    }

    /**
     * Sets the handler called when a node has become the content.
     *
     * @param value the handler, may be {@code null}
     */
    public final void setOnContentOpened(EventHandler<RXTransitionPaneEvent> value) {
        onContentOpenedProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentClosing;

    /**
     * Called when this pane's content is about to be replaced. Equivalent to
     * {@code addEventHandler(RXTransitionPaneEvent.CLOSING, handler)}, except
     * that it holds a single handler.
     *
     * @return the onContentClosing handler property
     * @see RXTransitionPaneEvent#CLOSING
     */
    public final ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentClosingProperty() {
        if (onContentClosing == null) {
            onContentClosing = new HandlerProperty("onContentClosing", RXTransitionPaneEvent.CLOSING);
        }
        return onContentClosing;
    }

    /**
     * Returns the handler called when the content is about to be replaced.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTransitionPaneEvent> getOnContentClosing() {
        return onContentClosing == null ? null : onContentClosing.get();
    }

    /**
     * Sets the handler called when the content is about to be replaced.
     *
     * @param value the handler, may be {@code null}
     */
    public final void setOnContentClosing(EventHandler<RXTransitionPaneEvent> value) {
        onContentClosingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentClosed;

    /**
     * Called when a replaced content is fully hidden, right before it is
     * removed from this pane. Equivalent to
     * {@code addEventHandler(RXTransitionPaneEvent.CLOSED, handler)}, except
     * that it holds a single handler.
     *
     * @return the onContentClosed handler property
     * @see RXTransitionPaneEvent#CLOSED
     */
    public final ObjectProperty<EventHandler<RXTransitionPaneEvent>> onContentClosedProperty() {
        if (onContentClosed == null) {
            onContentClosed = new HandlerProperty("onContentClosed", RXTransitionPaneEvent.CLOSED);
        }
        return onContentClosed;
    }

    /**
     * Returns the handler called when a replaced content is fully hidden.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTransitionPaneEvent> getOnContentClosed() {
        return onContentClosed == null ? null : onContentClosed.get();
    }

    /**
     * Sets the handler called when a replaced content is fully hidden.
     *
     * @param value the handler, may be {@code null}
     */
    public final void setOnContentClosed(EventHandler<RXTransitionPaneEvent> value) {
        onContentClosedProperty().set(value);
    }

    private final class HandlerProperty extends ObjectPropertyBase<EventHandler<RXTransitionPaneEvent>> {

        private final String name;
        private final EventType<RXTransitionPaneEvent> eventType;

        HandlerProperty(String name, EventType<RXTransitionPaneEvent> eventType) {
            this.name = name;
            this.eventType = eventType;
        }

        @Override
        protected void invalidated() {
            setEventHandler(eventType, get());
        }

        @Override
        public Object getBean() {
            return RXTransitionPane.this;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    // ==================== Layout ====================

    /**
     * Advertises the current content's content bias, so a parent layout
     * measures the pane against the correct axis when the content has a
     * content bias (e.g. a {@code wrapText} label whose height depends on its
     * width). The single content slot contributes its node's bias directly; a
     * {@code null} content has no bias.
     *
     * @return the current content's content bias, or {@code null} if it has
     * none or the content is {@code null}
     */
    @Override
    public Orientation getContentBias() {
        return ContentBias.of(getContent());
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTransitionPaneSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * Returns the CSS metadata of this control class.
     *
     * @return the class CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    // ==================== Styleable Properties ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTransitionPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXTransitionPane control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTransitionPane control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
