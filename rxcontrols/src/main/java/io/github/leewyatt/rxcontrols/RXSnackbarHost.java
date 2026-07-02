package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSnackbarHostSkin;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * An in-scene host that displays Material-style snackbars — short, non-modal,
 * auto-hiding notices — one at a time. Applications normally never add this
 * control to the scene graph themselves: the {@code RXSnackbars} facade resolves
 * a per-scene host lazily and injects it through an overlay layer, so showing a
 * message is a single call. A host can also be used directly (for example
 * mounted into a specific pane).
 *
 * <p>Requests are immutable {@link RXSnackbarRequest} values. While a snackbar is
 * displayed, new requests wait in a bounded FIFO queue (see
 * {@link #maxQueueSizeProperty() maxQueueSize}); when the displayed bar leaves,
 * the next request is promoted. Every removal path — timeout, action, close icon,
 * programmatic dismissal, replacement, overflow, duplicate rejection — funnels
 * through one dismiss gate that notifies exactly once per request: first the
 * request's own {@code onDismissed} callback, then a {@code DISMISSED}
 * {@link RXSnackbarEvent}, after which the host drops its reference to the
 * request.</p>
 *
 * <p>The read-only {@link #showingProperty() showing} is the display truth: it is
 * {@code true} from the moment a request becomes current until its bar has fully
 * left and no successor followed. The skin observes
 * {@link #currentRequestProperty() currentRequest} and plays transitions; it
 * reports back through {@link #notifyShown()} / {@link #notifyDismissed()} and
 * routes user-driven dismissals through {@link #requestDismiss(DismissReason)}.
 * Without a skin (a host never attached to a scene), transitions are treated as
 * instantaneous and the model settles synchronously.</p>
 */
public class RXSnackbarHost extends Control {

    // ==================== Constants ====================

    /**
     * Default bar position within the host.
     */
    public static final Pos DEFAULT_POSITION = Pos.BOTTOM_LEFT;

    /**
     * Default auto-hide duration a request inherits when it does not set one.
     */
    public static final Duration DEFAULT_DURATION = Duration.seconds(4.0);

    /**
     * Default upper bound of the pending queue, also the use-site fallback when
     * {@link #maxQueueSizeProperty() maxQueueSize} is non-positive.
     */
    public static final int DEFAULT_MAX_QUEUE_SIZE = 5;

    /**
     * Default scheduling strategy, also the use-site fallback when both the host
     * and the request leave the strategy unset.
     */
    public static final RXSnackbarStrategy DEFAULT_STRATEGY = RXSnackbarStrategy.QUEUE;

    /**
     * Default margin between the bar and the host edges.
     */
    public static final Insets DEFAULT_MARGIN = new Insets(24.0);

    /**
     * Default maximum bar width (the Material snackbar upper bound).
     */
    public static final double DEFAULT_SNACKBAR_MAX_WIDTH = 568.0;

    /**
     * Default enter / exit transition duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(250.0);

    /**
     * Default enter / exit interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final boolean DEFAULT_ANIMATED = true;
    private static final boolean DEFAULT_PREVENT_DUPLICATE = false;

    private static final String DEFAULT_STYLE_CLASS = "rx-snackbar-host";

    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    // ==================== Dismiss gate state ====================

    // Pending requests, FIFO; always bounded at the enqueue use site. A LinkedList
    // so a same-key in-place update can swap a queued request where it stands.
    private final LinkedList<RXSnackbarRequest> queue = new LinkedList<>();

    // The request whose exit is in flight (it already left currentRequest), with the
    // reason stashed for settlement, until the skin reports notifyDismissed().
    private RXSnackbarRequest closingRequest;
    private DismissReason pendingDismissReason;

    // True while the settlement pass of notifyDismissed() runs, so re-entrant
    // show()/dismiss() calls from onDismissed callbacks queue up instead of
    // colliding with the in-progress promotion.
    private boolean closeGateActive;

    // True between a request becoming current and its notifyShown(), so a stray
    // notifyShown() never fires SHOWN out of band and a re-entrant show() from a
    // SHOWING handler queues up instead of stealing the current slot.
    private boolean openInProgress;

    // ==================== Constructors ====================

    /**
     * Creates an empty host with default settings (bottom-left, 4s auto-hide,
     * FIFO queue bounded at 5).
     */
    public RXSnackbarHost() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.NODE);
        // A transparent overlay is not a Tab stop (Control defaults to true).
        setFocusTraversable(false);
        // Region's constructor flips pickOnBounds to TRUE (unlike Node's false),
        // so a scene-filling host would swallow every mouse event. With it off,
        // picking falls back to geometry — this host paints no background, so
        // empty space clicks through and only the bar (which has one) is hit.
        setPickOnBounds(false);
        // Leaving the scene settles everything: pending requests are DISCARDED and a
        // displayed bar closes as PROGRAMMATIC, so no callback is ever left hanging.
        // Registered before any skin exists, so this runs ahead of the skin's own
        // scene listener and the skin finds an already-settled model.
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                handleSceneDetached();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSnackbarHostSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, get());
        }
    };

    /**
     * Whether a snackbar is displayed — {@code true} from the moment a request
     * becomes current (its enter transition starts) until a bar has fully left
     * with no successor promoted; it stays {@code true} across queue advancement
     * and replacement. The read-only source of truth, tracked by the
     * {@code :showing} pseudo-class.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether a snackbar is displayed.
     *
     * @return {@code true} while a snackbar is displayed
     */
    public final boolean isShowing() {
        return showing.get();
    }

    // ==================== Current Request ====================

    private final ReadOnlyObjectWrapper<RXSnackbarRequest> currentRequest =
            new ReadOnlyObjectWrapper<>(this, "currentRequest");

    /**
     * The request the host intends to display right now — the skin's single
     * observation point. {@code null → R} starts an enter transition; {@code R →
     * null} starts an exit (every dismissal and replacement passes through
     * {@code null}); a direct non-null {@code A → B} switch happens only for a
     * same-key in-place update, which swaps content without transitions. While an
     * exit is animating this is already {@code null}; the leaving request is
     * carried by the {@code DISMISSED} event.
     *
     * @return the read-only current-request property
     */
    public final ReadOnlyObjectProperty<RXSnackbarRequest> currentRequestProperty() {
        return currentRequest.getReadOnlyProperty();
    }

    /**
     * Returns the request the host intends to display right now.
     *
     * @return the current request, or {@code null}
     */
    public final RXSnackbarRequest getCurrentRequest() {
        return currentRequest.get();
    }

    // ==================== Strategy ====================

    private final ObjectProperty<RXSnackbarStrategy> strategy =
            new SimpleObjectProperty<>(this, "strategy", DEFAULT_STRATEGY);

    /**
     * The default scheduling strategy for requests that do not set their own.
     * {@code null} falls back to {@link #DEFAULT_STRATEGY} at the use site.
     *
     * @return the strategy property
     */
    public final ObjectProperty<RXSnackbarStrategy> strategyProperty() {
        return strategy;
    }

    /**
     * Returns the default scheduling strategy.
     *
     * @return the strategy, or {@code null}
     */
    public final RXSnackbarStrategy getStrategy() {
        return strategy.get();
    }

    /**
     * Sets the default scheduling strategy.
     *
     * @param value the strategy, or {@code null} for {@link #DEFAULT_STRATEGY}
     */
    public final void setStrategy(RXSnackbarStrategy value) {
        strategy.set(value);
    }

    // ==================== Position ====================

    private final ObjectProperty<Pos> position = new SimpleObjectProperty<>(this, "position", DEFAULT_POSITION) {
        @Override
        protected void invalidated() {
            requestLayout();
        }
    };

    /**
     * Where the bar sits within the host. All nine {@link Pos} values are
     * accepted and interpreted as physical positions (no RTL flipping); a single
     * bar is shown regardless of position — corner positions do not stack.
     * {@code null} falls back to {@link #DEFAULT_POSITION} at the use site.
     *
     * @return the position property
     */
    public final ObjectProperty<Pos> positionProperty() {
        return position;
    }

    /**
     * Returns the bar position.
     *
     * @return the position, or {@code null}
     */
    public final Pos getPosition() {
        return position.get();
    }

    /**
     * Sets the bar position.
     *
     * @param value the position, or {@code null} for {@link #DEFAULT_POSITION}
     */
    public final void setPosition(Pos value) {
        position.set(value);
    }

    // ==================== Margin ====================

    private final ObjectProperty<Insets> margin = new StyleableObjectProperty<>(DEFAULT_MARGIN) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Insets> getCssMetaData() {
            return StyleableProperties.MARGIN;
        }

        @Override
        public Object getBean() {
            return RXSnackbarHost.this;
        }

        @Override
        public String getName() {
            return "margin";
        }
    };

    /**
     * The gap between the bar and the host's edges, resolved at layout time:
     * {@code null} falls back to {@link #DEFAULT_MARGIN}, and a negative,
     * {@code NaN}, or infinite component is treated as {@code 0}. Styleable via
     * {@code -rx-snackbar-margin}.
     *
     * @return the margin property
     */
    public final ObjectProperty<Insets> marginProperty() {
        return margin;
    }

    /**
     * Returns the bar margin.
     *
     * @return the margin, or {@code null}
     */
    public final Insets getMargin() {
        return margin.get();
    }

    /**
     * Sets the bar margin.
     *
     * @param value the margin, or {@code null} for {@link #DEFAULT_MARGIN}
     */
    public final void setMargin(Insets value) {
        margin.set(value);
    }

    // ==================== Snackbar Max Width ====================

    private final DoubleProperty snackbarMaxWidth = new StyleableDoubleProperty(DEFAULT_SNACKBAR_MAX_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.SNACKBAR_MAX_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXSnackbarHost.this;
        }

        @Override
        public String getName() {
            return "snackbarMaxWidth";
        }
    };

    /**
     * The bar's maximum width, clamped by the skin at layout time: a
     * non-positive or {@code NaN} value falls back to
     * {@link #DEFAULT_SNACKBAR_MAX_WIDTH}, and {@code POSITIVE_INFINITY} removes
     * the cap (the bar is still bounded by the available width). Styleable via
     * {@code -rx-snackbar-max-width}. Named {@code snackbarMaxWidth} because
     * {@code maxWidth} is the control's own {@code Region} sizing property.
     *
     * @return the snackbar max-width property
     */
    public final DoubleProperty snackbarMaxWidthProperty() {
        return snackbarMaxWidth;
    }

    /**
     * Returns the bar's maximum width.
     *
     * @return the bar's maximum width
     */
    public final double getSnackbarMaxWidth() {
        return snackbarMaxWidth.get();
    }

    /**
     * Sets the bar's maximum width.
     *
     * @param value the maximum width; non-positive or {@code NaN} falls back to the default
     */
    public final void setSnackbarMaxWidth(double value) {
        snackbarMaxWidth.set(value);
    }

    // ==================== Default Duration ====================

    private final ObjectProperty<Duration> defaultDuration =
            new SimpleObjectProperty<>(this, "defaultDuration", DEFAULT_DURATION);

    /**
     * The auto-hide duration a request inherits when its own duration is
     * {@code null}. A {@code null}, non-positive, indefinite, or unknown value
     * means inherited requests are persistent (no auto-hide). Note the asymmetry
     * with {@link #animationDurationProperty() animationDuration}, whose edge
     * values mean "snap".
     *
     * @return the default-duration property
     */
    public final ObjectProperty<Duration> defaultDurationProperty() {
        return defaultDuration;
    }

    /**
     * Returns the default auto-hide duration.
     *
     * @return the default duration, or {@code null} for persistent
     */
    public final Duration getDefaultDuration() {
        return defaultDuration.get();
    }

    /**
     * Sets the default auto-hide duration.
     *
     * @param value the duration; {@code null} or non-positive makes inherited requests persistent
     */
    public final void setDefaultDuration(Duration value) {
        defaultDuration.set(value);
    }

    // ==================== Max Queue Size ====================

    private final IntegerProperty maxQueueSize = new SimpleIntegerProperty(this, "maxQueueSize", DEFAULT_MAX_QUEUE_SIZE);

    /**
     * Upper bound of the pending queue — the queue is always bounded. When it
     * overflows, the oldest pending request is dropped and settled with
     * {@link DismissReason#DISCARDED}. A non-positive value is interpreted as
     * {@link #DEFAULT_MAX_QUEUE_SIZE} at the enqueue use site (the property is
     * never rewritten, so binding it stays safe).
     *
     * @return the max-queue-size property
     */
    public final IntegerProperty maxQueueSizeProperty() {
        return maxQueueSize;
    }

    /**
     * Returns the queue bound.
     *
     * @return the queue bound
     */
    public final int getMaxQueueSize() {
        return maxQueueSize.get();
    }

    /**
     * Sets the queue bound.
     *
     * @param value the queue bound; non-positive is interpreted as the default at the use site
     */
    public final void setMaxQueueSize(int value) {
        maxQueueSize.set(value);
    }

    // ==================== Prevent Duplicate ====================

    private final BooleanProperty preventDuplicate =
            new SimpleBooleanProperty(this, "preventDuplicate", DEFAULT_PREVENT_DUPLICATE);

    /**
     * Whether a request that matches the displayed or a pending request is
     * rejected with {@link DismissReason#DUPLICATE}. Only unkeyed requests match,
     * by message text; a keyed request either hits its key (and updates in place —
     * that always wins over rejection) or carries a distinct identity and is never
     * a duplicate. Default {@code false}.
     *
     * @return the prevent-duplicate property
     */
    public final BooleanProperty preventDuplicateProperty() {
        return preventDuplicate;
    }

    /**
     * Returns whether duplicate prevention is on.
     *
     * @return whether duplicate prevention is on
     */
    public final boolean isPreventDuplicate() {
        return preventDuplicate.get();
    }

    /**
     * Sets whether duplicate prevention is on.
     *
     * @param value whether duplicate prevention is on
     */
    public final void setPreventDuplicate(boolean value) {
        preventDuplicate.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXSnackbarHost.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether enter / exit transitions animate. When {@code false}, transitions
     * snap to their final state.
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
                    return RXSnackbarHost.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single enter / exit transition. A {@code null}, non-positive,
     * unknown, or indefinite value is not rejected; it disables animation (the
     * transition snaps), like {@code animated=false}.
     *
     * @return the animation-duration property
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
     * @param value the animation duration; {@code null} or non-positive disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for enter / exit transitions. Accepts {@code null}, which the
     * skin treats as {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable: there
     * is no stable public CSS converter for an arbitrary {@link Interpolator}.
     *
     * @return the animation-interpolator property
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

    // ==================== Events ====================

    private ObjectProperty<EventHandler<RXSnackbarEvent>> onShowing;

    /**
     * Handler called when a request is about to become the displayed snackbar
     * (its enter transition starts). A pre-current notification: read the request
     * from {@link RXSnackbarEvent#getRequest() the event} —
     * {@link #getCurrentRequest()} still holds the previous value while it fires.
     *
     * @return the onShowing property
     */
    public final ObjectProperty<EventHandler<RXSnackbarEvent>> onShowingProperty() {
        if (onShowing == null) {
            onShowing = newHandlerProperty("onShowing", RXSnackbarEvent.SHOWING);
        }
        return onShowing;
    }

    /**
     * Returns the onShowing handler.
     *
     * @return the onShowing handler, or {@code null}
     */
    public final EventHandler<RXSnackbarEvent> getOnShowing() {
        return onShowing == null ? null : onShowing.get();
    }

    /**
     * Sets the onShowing handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnShowing(EventHandler<RXSnackbarEvent> value) {
        onShowingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSnackbarEvent>> onShown;

    /**
     * Handler called when the enter transition has fully completed.
     *
     * @return the onShown property
     */
    public final ObjectProperty<EventHandler<RXSnackbarEvent>> onShownProperty() {
        if (onShown == null) {
            onShown = newHandlerProperty("onShown", RXSnackbarEvent.SHOWN);
        }
        return onShown;
    }

    /**
     * Returns the onShown handler.
     *
     * @return the onShown handler, or {@code null}
     */
    public final EventHandler<RXSnackbarEvent> getOnShown() {
        return onShown == null ? null : onShown.get();
    }

    /**
     * Sets the onShown handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnShown(EventHandler<RXSnackbarEvent> value) {
        onShownProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSnackbarEvent>> onDismissed;

    /**
     * Handler called exactly once per removed request, after the request's own
     * {@code onDismissed} callback.
     *
     * @return the onDismissed property
     */
    public final ObjectProperty<EventHandler<RXSnackbarEvent>> onDismissedProperty() {
        if (onDismissed == null) {
            onDismissed = newHandlerProperty("onDismissed", RXSnackbarEvent.DISMISSED);
        }
        return onDismissed;
    }

    /**
     * Returns the onDismissed handler.
     *
     * @return the onDismissed handler, or {@code null}
     */
    public final EventHandler<RXSnackbarEvent> getOnDismissed() {
        return onDismissed == null ? null : onDismissed.get();
    }

    /**
     * Sets the onDismissed handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnDismissed(EventHandler<RXSnackbarEvent> value) {
        onDismissedProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSnackbarEvent>> newHandlerProperty(String name,
                                                                             EventType<RXSnackbarEvent> type) {
        return new ObjectPropertyBase<>() {
            @Override
            protected void invalidated() {
                setEventHandler(type, get());
            }

            @Override
            public Object getBean() {
                return RXSnackbarHost.this;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ==================== Show / Dismiss API ====================

    /**
     * Shows the request, or schedules it while another snackbar is displayed.
     *
     * <p>Scheduling resolves in this order: a request whose key matches the
     * displayed or a pending request always updates that request in place (the old
     * one settles with {@link DismissReason#REPLACED}); otherwise, with
     * {@link #preventDuplicateProperty() preventDuplicate} on, a matching request
     * is rejected with {@link DismissReason#DUPLICATE}; otherwise the effective
     * strategy applies — {@code REPLACE} preempts a displayed bar (unless it has
     * an action and is not persistent), {@code QUEUE} appends to the bounded FIFO
     * queue (see {@link #maxQueueSizeProperty() maxQueueSize}; on overflow the
     * oldest pending request settles with {@link DismissReason#DISCARDED}).</p>
     *
     * @param request the request to show
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public void show(RXSnackbarRequest request) {
        Objects.requireNonNull(request, "request");
        // A settlement pass is running (an onDismissed / SHOWING callback
        // re-entered show): queue up unconditionally. No key update or REPLACE
        // preemption may touch the request being settled, or it would be settled
        // twice — the same guard philosophy as requestDismiss.
        if (closeGateActive) {
            enqueue(request);
            return;
        }
        if (request.getKey() != null && updateByKey(request)) {
            return;
        }
        if (isPreventDuplicate() && isDuplicate(request)) {
            settle(request, DismissReason.DUPLICATE);
            return;
        }
        if (getCurrentRequest() == null && closingRequest == null && !openInProgress) {
            makeCurrent(request);
            return;
        }
        if (effectiveStrategy(request) == RXSnackbarStrategy.REPLACE) {
            RXSnackbarRequest current = getCurrentRequest();
            if (current != null && !isProtectedFromReplace(current)) {
                // Preempt: the new request jumps the queue and displays right after
                // the displayed bar's exit; enqueue first so a synchronous (snap)
                // close promotes it immediately.
                enqueueFirst(request);
                initiateClose(current, DismissReason.REPLACED);
                return;
            }
        }
        enqueue(request);
    }

    /**
     * Shows a plain text snackbar with all defaults (inherits the host's
     * duration, no action, no key).
     *
     * @param message the message text
     */
    public void show(String message) {
        show(RXSnackbarRequest.builder(message).build());
    }

    /**
     * Dismisses the displayed snackbar with {@link DismissReason#PROGRAMMATIC}.
     * A no-op when nothing is displayed or an exit is already in flight; pending
     * requests are not affected.
     */
    public void dismiss() {
        requestDismiss(DismissReason.PROGRAMMATIC);
    }

    /**
     * Dismisses the request carrying the given key: a displayed match dismisses
     * with {@link DismissReason#PROGRAMMATIC}, a pending match is removed and
     * settles with {@link DismissReason#DISCARDED}.
     *
     * @param key the request key to match
     * @return {@code true} if a displayed or pending request matched; matching a
     *         displayed request that is already leaving does not dismiss it twice
     */
    public boolean dismiss(String key) {
        if (key == null) {
            return false;
        }
        RXSnackbarRequest current = getCurrentRequest();
        if (current != null && key.equals(current.getKey())) {
            requestDismiss(DismissReason.PROGRAMMATIC);
            return true;
        }
        for (Iterator<RXSnackbarRequest> iterator = queue.iterator(); iterator.hasNext(); ) {
            RXSnackbarRequest queued = iterator.next();
            if (key.equals(queued.getKey())) {
                iterator.remove();
                settle(queued, DismissReason.DISCARDED);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every request: pending requests settle with
     * {@link DismissReason#DISCARDED}, then the displayed snackbar (if any)
     * dismisses with {@link DismissReason#PROGRAMMATIC}. All request references
     * are dropped.
     */
    public void clear() {
        drainQueue();
        requestDismiss(DismissReason.PROGRAMMATIC);
    }

    // ==================== Host-skin hooks ====================

    /**
     * Requests the displayed snackbar to leave for the given reason — the single
     * dismiss gate every exit path funnels into. Used by the skin for
     * user-driven exits (timeout, action, close icon, ESC); applications normally
     * call {@link #dismiss()} instead. A no-op when nothing is displayed, an exit
     * is already in flight, or a settlement pass is running — so a same-frame
     * double trigger (say, timeout racing an action click) dismisses exactly once.
     *
     * @param reason why the snackbar should leave; {@code null} is treated as
     *               {@link DismissReason#PROGRAMMATIC}
     */
    public final void requestDismiss(DismissReason reason) {
        if (closeGateActive || closingRequest != null) {
            return;
        }
        RXSnackbarRequest current = getCurrentRequest();
        if (current == null) {
            return;
        }
        initiateClose(current, reason == null ? DismissReason.PROGRAMMATIC : reason);
    }

    /**
     * Invoked by the skin when the enter transition has fully completed: fires
     * {@code SHOWN}. Not intended to be called by applications — a stray call
     * while no enter is in flight is a no-op.
     */
    public final void notifyShown() {
        if (!openInProgress) {
            return;
        }
        openInProgress = false;
        RXSnackbarRequest request = getCurrentRequest();
        if (request == null) {
            return;
        }
        fireEvent(new RXSnackbarEvent(RXSnackbarEvent.SHOWN, this, request, null));
    }

    /**
     * Invoked by the skin when the exit transition has fully completed: settles
     * the leaving request (its {@code onDismissed} callback, then the
     * {@code DISMISSED} event), drops its reference, and promotes the next pending
     * request. Not intended to be called by applications — a stray call while no
     * exit is in flight is a no-op and never fabricates a notification.
     */
    public final void notifyDismissed() {
        if (closingRequest == null) {
            return;
        }
        RXSnackbarRequest settled = closingRequest;
        DismissReason reason = pendingDismissReason == null ? DismissReason.PROGRAMMATIC : pendingDismissReason;
        closingRequest = null;
        pendingDismissReason = null;
        closeGateActive = true;
        try {
            settle(settled, reason);
        } finally {
            closeGateActive = false;
            promoteNext();
        }
    }

    // ==================== Effective-value helpers ====================

    /**
     * Resolves the auto-hide duration that applies to the request on this host:
     * the request's own duration, or this host's
     * {@link #defaultDurationProperty() defaultDuration} when the request leaves
     * it {@code null}. The result is persistent (no auto-hide) when it is
     * {@code null}, non-positive, indefinite, or unknown.
     *
     * @param request the request to resolve
     * @return the effective duration, possibly {@code null} (persistent)
     */
    public final Duration effectiveDuration(RXSnackbarRequest request) {
        Objects.requireNonNull(request, "request");
        Duration duration = request.getDuration();
        return duration != null ? duration : getDefaultDuration();
    }

    /**
     * Resolves whether the bar rendered for the request shows a close icon: the
     * request's own {@link RXSnackbarRequest#isShowCloseIcon() showCloseIcon}, or
     * forced {@code true} when the request is effectively persistent and carries
     * no action — so every bar keeps a non-programmatic way to leave. A rendering
     * decision only; the request value is never rewritten.
     *
     * @param request the request to resolve
     * @return whether the rendered bar shows a close icon
     */
    public final boolean effectiveShowCloseIcon(RXSnackbarRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.isShowCloseIcon()) {
            return true;
        }
        return isPersistent(effectiveDuration(request)) && !request.hasAction();
    }

    // Kept the exact complement of RXSnackbarHostSkin#isPositiveFinite: a duration
    // this method calls persistent is exactly one the skin starts no timer for, so
    // the forced close icon and the missing timer can never disagree.
    private static boolean isPersistent(Duration duration) {
        return duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThanOrEqualTo(Duration.ZERO);
    }

    // ==================== Queue state machine ====================

    // Same-key resolution: a key hit always wins (an update, never a duplicate
    // rejection). Returns true when the request was consumed as an update.
    private boolean updateByKey(RXSnackbarRequest request) {
        RXSnackbarRequest current = getCurrentRequest();
        if (current != null && request.getKey().equals(current.getKey())) {
            updateInPlace(current, request);
            return true;
        }
        for (ListIterator<RXSnackbarRequest> iterator = queue.listIterator(); iterator.hasNext(); ) {
            RXSnackbarRequest queued = iterator.next();
            if (request.getKey().equals(queued.getKey())) {
                iterator.set(request);
                settle(queued, DismissReason.REPLACED);
                return true;
            }
        }
        return false;
    }

    // In-place update of the displayed bar — the only non-null -> non-null
    // currentRequest transition. Event order is fixed: old DISMISSED(REPLACED),
    // new SHOWING, new SHOWN (no transitions; the skin swaps content and restarts
    // the timer on the A -> B change). The gate stays active until the
    // replacement is committed: while the settled request is still current (the
    // settle callback and the SHOWING handler both run in that window), a
    // re-entrant show() must queue up rather than match it again and settle it
    // twice, or preempt it into a stuck closingRequest.
    private void updateInPlace(RXSnackbarRequest previous, RXSnackbarRequest replacement) {
        closeGateActive = true;
        try {
            settle(previous, DismissReason.REPLACED);
            openInProgress = true;
            fireEvent(new RXSnackbarEvent(RXSnackbarEvent.SHOWING, this, replacement, null));
            currentRequest.set(replacement);
        } finally {
            closeGateActive = false;
        }
        notifyShown();
    }

    // Duplicate matching applies only when no key update hit: a keyed request that
    // matched nothing carries a distinct identity (never a duplicate); an unkeyed
    // request matches by message text against the displayed and pending requests.
    private boolean isDuplicate(RXSnackbarRequest request) {
        if (request.getKey() != null) {
            return false;
        }
        String message = request.getMessage();
        if (message == null) {
            return false;
        }
        RXSnackbarRequest current = getCurrentRequest();
        if (current != null && message.equals(current.getMessage())) {
            return true;
        }
        for (RXSnackbarRequest queued : queue) {
            if (message.equals(queued.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private RXSnackbarStrategy effectiveStrategy(RXSnackbarRequest request) {
        if (request.getStrategy() != null) {
            return request.getStrategy();
        }
        RXSnackbarStrategy hostStrategy = getStrategy();
        return hostStrategy != null ? hostStrategy : DEFAULT_STRATEGY;
    }

    // A displayed bar with an action that will auto-hide anyway gets its reading /
    // acting time; a persistent one may be preempted, or it would block the queue
    // forever.
    private boolean isProtectedFromReplace(RXSnackbarRequest current) {
        return current.hasAction() && !isPersistent(effectiveDuration(current));
    }

    private void makeCurrent(RXSnackbarRequest request) {
        openInProgress = true;
        showing.set(true);
        fireEvent(new RXSnackbarEvent(RXSnackbarEvent.SHOWING, this, request, null));
        // Setting currentRequest triggers the skin's enter transition; without
        // animation the skin finalizes synchronously and notifyShown() has already
        // run by the time this returns. Without a skin, complete the enter here.
        currentRequest.set(request);
        if (getSkin() == null) {
            notifyShown();
        }
    }

    private void initiateClose(RXSnackbarRequest request, DismissReason reason) {
        openInProgress = false;
        closingRequest = request;
        pendingDismissReason = reason;
        // The skin observes R -> null and plays the exit transition, calling
        // notifyDismissed() when done (synchronously when not animating). Without
        // a skin, complete the exit here.
        currentRequest.set(null);
        if (getSkin() == null) {
            notifyDismissed();
        }
    }

    private void promoteNext() {
        if (getCurrentRequest() != null || closingRequest != null) {
            return;
        }
        RXSnackbarRequest next = queue.pollFirst();
        if (next != null) {
            makeCurrent(next);
        } else {
            showing.set(false);
        }
    }

    private void enqueue(RXSnackbarRequest request) {
        trimQueueForOneMore();
        queue.addLast(request);
    }

    private void enqueueFirst(RXSnackbarRequest request) {
        trimQueueForOneMore();
        queue.addFirst(request);
    }

    private void trimQueueForOneMore() {
        int bound = getMaxQueueSize();
        int effectiveBound = bound <= 0 ? DEFAULT_MAX_QUEUE_SIZE : bound;
        while (queue.size() >= effectiveBound) {
            settle(queue.pollFirst(), DismissReason.DISCARDED);
        }
    }

    // Exactly-once settlement of a removed request: the per-request callback first,
    // then the host DISMISSED event. The event still fires (and state stays
    // consistent) when the callback throws; the exception propagates to the caller.
    private void settle(RXSnackbarRequest request, DismissReason reason) {
        try {
            BiConsumer<RXSnackbarRequest, DismissReason> callback = request.getOnDismissed();
            if (callback != null) {
                callback.accept(request, reason);
            }
        } finally {
            fireEvent(new RXSnackbarEvent(RXSnackbarEvent.DISMISSED, this, request, reason));
        }
    }

    private void drainQueue() {
        while (!queue.isEmpty()) {
            settle(queue.pollFirst(), DismissReason.DISCARDED);
        }
    }

    private void handleSceneDetached() {
        drainQueue();
        if (closingRequest != null) {
            // The skin may already be gone; settle the in-flight exit now (a later
            // skin callback is a guarded no-op).
            notifyDismissed();
        } else if (getCurrentRequest() != null) {
            initiateClose(getCurrentRequest(), DismissReason.PROGRAMMATIC);
        }
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSnackbarHost, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXSnackbarHost node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSnackbarHost node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXSnackbarHost, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXSnackbarHost node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSnackbarHost node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXSnackbarHost, Insets> MARGIN =
                new CssMetaData<>("-rx-snackbar-margin", InsetsConverter.getInstance(), DEFAULT_MARGIN) {
                    @Override
                    public boolean isSettable(RXSnackbarHost node) {
                        return !node.margin.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXSnackbarHost node) {
                        return (StyleableProperty<Insets>) node.marginProperty();
                    }
                };

        private static final CssMetaData<RXSnackbarHost, Number> SNACKBAR_MAX_WIDTH =
                new CssMetaData<>("-rx-snackbar-max-width",
                        SizeConverter.getInstance(), DEFAULT_SNACKBAR_MAX_WIDTH) {
                    @Override
                    public boolean isSettable(RXSnackbarHost node) {
                        return !node.snackbarMaxWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSnackbarHost node) {
                        return (StyleableProperty<Number>) node.snackbarMaxWidthProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            styleables.add(MARGIN);
            styleables.add(SNACKBAR_MAX_WIDTH);
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
}
