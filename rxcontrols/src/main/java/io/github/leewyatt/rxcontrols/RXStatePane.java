package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXStatePaneSkin;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A region-level state container driven by two orthogonal axes: the
 * {@link #stateProperty() state} replacement axis decides which base view is in
 * the scene graph — {@link #contentProperty() content},
 * {@link #emptyContentProperty() emptyContent}, or
 * {@link #errorContentProperty() errorContent}, exactly one at a time — while
 * the loading overlay axis stacks a progress presentation on top of whichever
 * base view is current. The empty and error slots fall back to a default
 * {@link RXPlaceholder} when {@code null}, so the pane is complete out of the
 * box; every slot remains an escape hatch for arbitrary nodes.
 *
 * <pre>{@code
 * RXStatePane pane = new RXStatePane();
 * pane.setContent(tableView);
 * pane.showEmpty();      // replaces the table with the default "No data" view
 * pane.showContent();
 * }</pre>
 *
 * <p><b>Clip.</b> The skin owns this control's {@link Node#clipProperty() clip}:
 * while a skin is installed it keeps a rectangle clip sized to the pane (so the
 * overlay layers never paint outside the bounds) and resets the clip to
 * {@code null} on dispose. A clip set by the application is overwritten.</p>
 */
public class RXStatePane extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-state-pane";

    // ==================== Constants ====================

    /**
     * Default replacement-axis state, also the {@code null} fallback.
     */
    public static final State DEFAULT_STATE = State.CONTENT;

    /**
     * Default animation interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    /**
     * Default animation enabled state.
     */
    private static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default animation duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(220.0);

    /**
     * Default for whether the dim scrim shows while loading.
     */
    private static final boolean DEFAULT_DIMMED = true;

    /**
     * Default for whether the loading presentation blocks input.
     */
    private static final boolean DEFAULT_BLOCKING = true;

    /**
     * Default anti-flicker delay before the loading presentation shows.
     */
    private static final Duration DEFAULT_LOADING_DELAY = Duration.ZERO;

    /**
     * Default progress (indeterminate).
     */
    private static final double DEFAULT_PROGRESS = -1.0;

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    // ==================== Events ====================

    /**
     * Fired on this pane when its default retry button is actioned. Parented at
     * {@link Event#ANY} and delivered as a plain {@link Event} (the
     * {@code MenuItem.MENU_VALIDATION_EVENT} pattern — {@code ActionEvent}
     * instances cannot carry a custom event type). The
     * {@link #onRetryProperty() onRetry} handler receives it; so does any
     * handler registered through {@code addEventHandler(RETRY, ...)}.
     */
    public static final EventType<Event> RETRY = new EventType<>(Event.ANY, "RX_STATE_PANE_RETRY");

    // ==================== Constructors ====================

    /**
     * Creates an empty state pane in the {@link State#CONTENT} state.
     */
    public RXStatePane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.NODE);
        // A pure container is not a Tab stop (Control defaults to true).
        setFocusTraversable(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXStatePaneSkin(this);
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
     *
     * <p>Forwards the bias of the user-set base view slot selected by the
     * current state; a {@code null} slot (showing a default placeholder or an
     * empty base) has no bias.</p>
     */
    @Override
    public Orientation getContentBias() {
        State current = (getState() == null) ? DEFAULT_STATE : getState();
        Node base = switch (current) {
            case EMPTY -> getEmptyContent();
            case ERROR -> getErrorContent();
            default -> getContent();
        };
        return base == null ? null : base.getContentBias();
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * The base view shown in the {@link State#CONTENT} state. {@code null} is a
     * legitimate empty base — no default is substituted.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content node, or {@code null} for an empty base
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== State ====================

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", DEFAULT_STATE) {
        @Override
        protected void invalidated() {
            State current = (get() == null) ? DEFAULT_STATE : get();
            pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, current == State.EMPTY);
            pseudoClassStateChanged(ERROR_PSEUDO_CLASS, current == State.ERROR);
        }
    };

    /**
     * The replacement axis: which base view is in the scene graph. The
     * {@code :empty} and {@code :error} pseudo-classes track it immediately. A
     * {@code null} value is not rejected; it resolves to {@link #DEFAULT_STATE}
     * at the use site.
     *
     * @return the state property
     */
    public final ObjectProperty<State> stateProperty() {
        return state;
    }

    /**
     * Returns the replacement-axis state.
     *
     * @return the state, possibly {@code null}
     */
    public final State getState() {
        return state.get();
    }

    /**
     * Sets the replacement-axis state.
     *
     * @param value the state, or {@code null} to fall back to the default
     */
    public final void setState(State value) {
        state.set(value);
    }

    // ==================== Empty Content ====================

    private final ObjectProperty<Node> emptyContent = new SimpleObjectProperty<>(this, "emptyContent");

    /**
     * The base view shown in the {@link State#EMPTY} state. When {@code null}
     * (the default), the skin shows a default {@link RXPlaceholder} with the
     * {@link RXPlaceholder.Status#EMPTY EMPTY} preset and a "No data" title;
     * the getter still returns {@code null}.
     *
     * @return the empty content property
     */
    public final ObjectProperty<Node> emptyContentProperty() {
        return emptyContent;
    }

    /**
     * Returns the empty content node.
     *
     * @return the empty content node, or {@code null}
     */
    public final Node getEmptyContent() {
        return emptyContent.get();
    }

    /**
     * Sets the empty content node.
     *
     * @param value the empty content node, or {@code null} for the default placeholder
     */
    public final void setEmptyContent(Node value) {
        emptyContent.set(value);
    }

    // ==================== Error Content ====================

    private final ObjectProperty<Node> errorContent = new SimpleObjectProperty<>(this, "errorContent");

    /**
     * The base view shown in the {@link State#ERROR} state. When {@code null}
     * (the default), the skin shows a default {@link RXPlaceholder} with the
     * {@link RXPlaceholder.Status#ERROR ERROR} preset and a "Something went
     * wrong" title; the getter still returns {@code null}.
     *
     * @return the error content property
     */
    public final ObjectProperty<Node> errorContentProperty() {
        return errorContent;
    }

    /**
     * Returns the error content node.
     *
     * @return the error content node, or {@code null}
     */
    public final Node getErrorContent() {
        return errorContent.get();
    }

    /**
     * Sets the error content node.
     *
     * @param value the error content node, or {@code null} for the default placeholder
     */
    public final void setErrorContent(Node value) {
        errorContent.set(value);
    }

    // ==================== Loading ====================

    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", false);

    /**
     * The overlay axis: whether the loading presentation (indicator box, dim
     * scrim, input interception) is stacked on the current base view. This is a
     * plain value — the whole presentation, including the {@code :loading}
     * pseudo-class, is activated by the skin only once the loading delay has
     * elapsed, and is withdrawn on {@code false}. Orthogonal to
     * {@link #stateProperty() state}: refreshing existing content is
     * {@code state == CONTENT && loading == true}.
     *
     * <p>On withdrawal the keyboard is released immediately, but while a
     * visible dim scrim fades out it keeps intercepting mouse events until it
     * settles (roughly the fade-out duration).</p>
     *
     * @return the loading property
     */
    public final BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Returns whether the loading overlay axis is on.
     *
     * @return {@code true} if loading
     */
    public final boolean isLoading() {
        return loading.get();
    }

    /**
     * Sets whether the loading overlay axis is on.
     *
     * @param value whether loading
     */
    public final void setLoading(boolean value) {
        loading.set(value);
    }

    // ==================== Convenience ====================

    /**
     * Turns the loading overlay axis on ({@code setLoading(true)}). Only this
     * axis is touched: the {@link #stateProperty() state} keeps whichever base
     * view it is showing.
     */
    public final void showLoading() {
        setLoading(true);
    }

    /**
     * Turns the loading overlay axis off ({@code setLoading(false)}). Only
     * this axis is touched: the {@link #stateProperty() state} is unchanged.
     */
    public final void hideLoading() {
        setLoading(false);
    }

    /**
     * Switches the replacement axis to {@link State#CONTENT}. Only this axis
     * is touched: an active loading overlay stays on top until
     * {@link #hideLoading()}.
     */
    public final void showContent() {
        setState(State.CONTENT);
    }

    /**
     * Switches the replacement axis to {@link State#EMPTY}. Only this axis is
     * touched: an active loading overlay stays on top until
     * {@link #hideLoading()}.
     */
    public final void showEmpty() {
        setState(State.EMPTY);
    }

    /**
     * Switches the replacement axis to {@link State#ERROR}. Only this axis is
     * touched: an active loading overlay stays on top until
     * {@link #hideLoading()}.
     */
    public final void showError() {
        setState(State.ERROR);
    }

    // ==================== Loading Graphic ====================

    private final ObjectProperty<Node> loadingGraphic = new SimpleObjectProperty<>(this, "loadingGraphic");

    /**
     * The loading indicator slot. When {@code null} (the default), the skin
     * shows a default {@link RXCircularProgressIndicator}; the getter still
     * returns {@code null}. The skin always stacks the loading text label
     * below whatever indicator is in effect, so replacing the indicator keeps
     * the text working. Note that with {@code blocking} off the overlay is
     * mouse-transparent, so interactive nodes inside a custom indicator are
     * not clickable — keep {@code blocking} on for cancellable indicators.
     *
     * @return the loading graphic property
     */
    public final ObjectProperty<Node> loadingGraphicProperty() {
        return loadingGraphic;
    }

    /**
     * Returns the loading graphic node.
     *
     * @return the loading graphic node, or {@code null}
     */
    public final Node getLoadingGraphic() {
        return loadingGraphic.get();
    }

    /**
     * Sets the loading graphic node.
     *
     * @param value the loading graphic node, or {@code null} for the default indicator
     */
    public final void setLoadingGraphic(Node value) {
        loadingGraphic.set(value);
    }

    // ==================== Loading Text ====================

    private final StringProperty loadingText = new SimpleStringProperty(this, "loadingText");

    /**
     * The text shown below the loading indicator. Always effective — the skin
     * keeps the label independent of the {@link #loadingGraphicProperty()
     * loadingGraphic} slot, so it works with any indicator. A {@code null} or
     * empty value hides the label.
     *
     * @return the loading text property
     */
    public final StringProperty loadingTextProperty() {
        return loadingText;
    }

    /**
     * Returns the loading text.
     *
     * @return the loading text, possibly {@code null}
     */
    public final String getLoadingText() {
        return loadingText.get();
    }

    /**
     * Sets the loading text.
     *
     * @param value the loading text, or {@code null} for none
     */
    public final void setLoadingText(String value) {
        loadingText.set(value);
    }

    // ==================== Progress ====================

    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", DEFAULT_PROGRESS);

    /**
     * Determinate progress in {@code [0, 1]}, or {@code -1} for indeterminate.
     * The automatic binding is slot-conditional: it drives the skin's built-in
     * default indicator exactly while {@link #loadingGraphicProperty()
     * loadingGraphic} is {@code null}. A custom indicator node — even a
     * {@code ProgressIndicator} — is never touched (bind it yourself); clearing
     * the slot back to {@code null} restores the automatic drive.
     *
     * @return the progress property
     */
    public final DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Returns the progress.
     *
     * @return the progress, {@code -1} for indeterminate
     */
    public final double getProgress() {
        return progress.get();
    }

    /**
     * Sets the progress.
     *
     * @param value progress in {@code [0, 1]}, or {@code -1} for indeterminate
     */
    public final void setProgress(double value) {
        progress.set(value);
    }

    // ==================== Loading Delay ====================

    private final ObjectProperty<Duration> loadingDelay =
            new SimpleObjectProperty<>(this, "loadingDelay", DEFAULT_LOADING_DELAY);

    /**
     * Anti-flicker delay-in: the whole loading presentation — scrim, indicator,
     * input blocking, focus handling, and the {@code :loading} /
     * {@code :blocking} pseudo-classes — activates atomically only once this
     * delay has elapsed. During the window the base view stays fully
     * interactive, and turning loading off within it means nothing ever shows.
     * A {@code null}, non-positive, unknown, or indefinite value is not
     * rejected; it activates immediately.
     *
     * @return the loading delay property
     */
    public final ObjectProperty<Duration> loadingDelayProperty() {
        return loadingDelay;
    }

    /**
     * Returns the loading delay.
     *
     * @return the loading delay, possibly {@code null}
     */
    public final Duration getLoadingDelay() {
        return loadingDelay.get();
    }

    /**
     * Sets the loading delay.
     *
     * @param value the loading delay; {@code null} or any non-positive value activates immediately
     */
    public final void setLoadingDelay(Duration value) {
        loadingDelay.set(value);
    }

    // ==================== Blocking ====================

    private final BooleanProperty blocking = new SimpleBooleanProperty(this, "blocking", DEFAULT_BLOCKING);

    /**
     * Whether the active loading presentation blocks input: the overlay layer
     * intercepts the mouse and the current base view is disabled, which also
     * removes it from focus and Tab traversal. The {@code :blocking}
     * pseudo-class reflects this only while the presentation is actually
     * active. Toggling the value while the presentation is active takes effect
     * immediately. Note that with {@code blocking} off a visible dim scrim
     * still intercepts mouse events (see {@link #dimmedProperty() dimmed}),
     * and the mouse-transparent overlay makes interactive nodes inside a
     * custom {@link #loadingGraphicProperty() loadingGraphic} unclickable.
     *
     * @return the blocking property
     */
    public final BooleanProperty blockingProperty() {
        return blocking;
    }

    /**
     * Returns whether the loading presentation blocks input.
     *
     * @return whether input is blocked while loading
     */
    public final boolean isBlocking() {
        return blocking.get();
    }

    /**
     * Sets whether the loading presentation blocks input.
     *
     * @param value whether input is blocked while loading
     */
    public final void setBlocking(boolean value) {
        blocking.set(value);
    }

    // ==================== Dimmed ====================

    private final BooleanProperty dimmed = new StyleableBooleanProperty(DEFAULT_DIMMED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.DIMMED;
        }

        @Override
        public Object getBean() {
            return RXStatePane.this;
        }

        @Override
        public String getName() {
            return "dimmed";
        }
    };

    /**
     * Whether the dim scrim shows while the loading presentation is active.
     * Note that a visible scrim intercepts mouse events by the backdrop
     * contract, so dimming is never purely visual; with {@code dimmed} off,
     * mouse interception is decided by {@code blocking} alone. Toggling this
     * while the presentation is active takes effect immediately.
     *
     * @return the dimmed property
     */
    public final BooleanProperty dimmedProperty() {
        return dimmed;
    }

    /**
     * Returns whether the dim scrim shows while loading.
     *
     * @return whether the scrim shows
     */
    public final boolean isDimmed() {
        return dimmed.get();
    }

    /**
     * Sets whether the dim scrim shows while loading.
     *
     * @param value whether the scrim shows
     */
    public final void setDimmed(boolean value) {
        dimmed.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXStatePane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether transitions animate: the overlay fade, the state fade-through,
     * and the backdrop fade. When {@code false} everything snaps.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether transitions animate.
     *
     * @return whether transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether transitions animate.
     *
     * @param value whether transitions animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXStatePane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single fade transition, forwarded to the backdrop. A
     * {@code null}, non-positive, unknown, or indefinite value is not
     * rejected; it disables animation (transitions snap), like
     * {@code animated=false}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator used for fade transitions, forwarded to the backdrop.
     * Accepts {@code null}, which the skin treats as
     * {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable: there is no
     * stable public CSS converter for an arbitrary {@link Interpolator}.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator, possibly {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the animation interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== On Retry ====================

    private ObjectProperty<EventHandler<Event>> onRetry;

    /**
     * The retry hook, backed by the {@link #RETRY} event. Setting a non-null
     * handler is also the single switch that makes the retry button appear in
     * the <em>default</em> error placeholder (clearing it removes the button)
     * — this asymmetry is deliberate: {@code addEventHandler(RETRY, ...)} is a
     * pure listener and never affects the default UI, so the button's
     * visibility has exactly one source of truth. A user-supplied
     * {@link #errorContentProperty() errorContent} is never touched.
     *
     * @return the onRetry property
     */
    public final ObjectProperty<EventHandler<Event>> onRetryProperty() {
        if (onRetry == null) {
            onRetry = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RETRY, get());
                }

                @Override
                public Object getBean() {
                    return RXStatePane.this;
                }

                @Override
                public String getName() {
                    return "onRetry";
                }
            };
        }
        return onRetry;
    }

    /**
     * Returns the retry handler.
     *
     * @return the retry handler, or {@code null}
     */
    public final EventHandler<Event> getOnRetry() {
        return onRetry == null ? null : onRetry.get();
    }

    /**
     * Sets the retry handler.
     *
     * @param value the retry handler, or {@code null} to remove the default retry button
     */
    public final void setOnRetry(EventHandler<Event> value) {
        onRetryProperty().set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXStatePane, Boolean> DIMMED =
                new CssMetaData<>("-rx-dimmed", BooleanConverter.getInstance(), DEFAULT_DIMMED) {
                    @Override
                    public boolean isSettable(RXStatePane node) {
                        return !node.dimmed.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXStatePane node) {
                        return (StyleableProperty<Boolean>) node.dimmedProperty();
                    }
                };

        private static final CssMetaData<RXStatePane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXStatePane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXStatePane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXStatePane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXStatePane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXStatePane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(DIMMED);
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * The replacement-axis states of an {@link RXStatePane}: which base view
     * occupies the scene graph. Loading is intentionally not a state — it is
     * the orthogonal overlay axis stacked on any of these.
     */
    public enum State {

        /**
         * The regular {@code content} base view.
         */
        CONTENT,

        /**
         * The "no data" base view ({@code emptyContent} or the default placeholder).
         */
        EMPTY,

        /**
         * The failure base view ({@code errorContent} or the default placeholder).
         */
        ERROR
    }
}
