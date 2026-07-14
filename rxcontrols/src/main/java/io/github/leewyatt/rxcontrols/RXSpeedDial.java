package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.event.RXSpeedDialEvent;
import io.github.leewyatt.rxcontrols.skins.RXSpeedDialSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A speed dial: a persistent main floating action button that fans out related
 * secondary actions in a linear direction.
 *
 * <p>{@code RXSpeedDial} is a plain {@link Control} placed by the caller, often
 * in a {@code StackPane} corner. Its layout footprint tracks the main FAB; the
 * secondary actions live on an unmanaged layer and may overflow the control
 * bounds without changing parent layout. The main FAB size can be styled with
 * {@code -rx-fab-size} by targeting the direct child
 * {@code .rx-speed-dial > .rx-fab}.</p>
 */
@DefaultProperty("actions")
public class RXSpeedDial extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-speed-dial";

    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");
    private static final PseudoClass UP_PSEUDO_CLASS = PseudoClass.getPseudoClass("up");
    private static final PseudoClass DOWN_PSEUDO_CLASS = PseudoClass.getPseudoClass("down");
    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");

    /**
     * Default open and close animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    /**
     * Default delay between adjacent action animations.
     */
    public static final Duration DEFAULT_STAGGER_DELAY = Duration.millis(30.0);

    /**
     * Default space between the main FAB and adjacent action FABs.
     */
    public static final double DEFAULT_ACTION_SPACING = 8.0;

    /**
     * Default space between an action FAB and its label.
     */
    public static final double DEFAULT_LABEL_GAP = 8.0;

    // ==================== Constructors ====================

    /**
     * Creates an empty speed dial.
     */
    public RXSpeedDial() {
        initialize();
    }

    /**
     * Creates a speed dial with the given main icon.
     *
     * @param icon the main FAB icon, or {@code null}
     */
    public RXSpeedDial(@NamedArg("icon") Node icon) {
        initialize();
        setIcon(icon);
    }

    /**
     * Creates a speed dial with the given main icon and actions.
     *
     * @param icon    the main FAB icon, or {@code null}
     * @param actions initial actions to append, or {@code null}
     */
    public RXSpeedDial(@NamedArg("icon") Node icon,
                       @NamedArg("actions") RXSpeedDialAction... actions) {
        initialize();
        setIcon(icon);
        if (actions != null) {
            getActions().addAll(actions);
        }
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        updateDirectionPseudoClasses();
    }

    /**
     * Creates the default speed-dial skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSpeedDialSkin(this);
    }

    /**
     * Returns the user-agent stylesheet for this control.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Actions ====================

    private final ObservableList<RXSpeedDialAction> actions = FXCollections.observableArrayList();

    /**
     * The secondary actions fanned out from the main FAB. Material guidance is
     * two to six actions; the list remains unrestricted for caller flexibility.
     * Null entries are ignored by the default skin.
     *
     * @return the observable, modifiable actions list
     */
    public final ObservableList<RXSpeedDialAction> getActions() {
        return actions;
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing =
            new ReadOnlyBooleanWrapper(this, "showing", false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, get());
                    notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
                }
            };
    private boolean terminalEventPending;
    private CloseReason pendingTerminalCloseReason;

    /**
     * Whether the dial is currently expanded.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the dial is currently expanded.
     *
     * @return whether the dial is currently expanded
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Opens the dial. This is a no-op when already open or disabled.
     */
    public final void open() {
        if (isShowing() || isDisabled()) {
            return;
        }
        fireSpeedDialEvent(RXSpeedDialEvent.SHOWING, null);
        terminalEventPending = true;
        pendingTerminalCloseReason = null;
        showing.set(true);
        if (getSkin() == null) {
            completeShowingTransition(true);
        }
    }

    /**
     * Requests the dial closed with {@link CloseReason#TOGGLE}.
     */
    public final void close() {
        close(CloseReason.TOGGLE);
    }

    /**
     * Requests the dial closed with the given reason. This overload is intended
     * for custom close affordances that can report a precise reason.
     *
     * @param reason the close reason, or {@code null}
     */
    public final void close(CloseReason reason) {
        if (!isShowing()) {
            return;
        }
        CloseReason effectiveReason = reason == null ? CloseReason.TOGGLE : reason;
        if (fireCloseRequest(effectiveReason)) {
            return;
        }
        terminalEventPending = true;
        pendingTerminalCloseReason = effectiveReason;
        fireSpeedDialEvent(RXSpeedDialEvent.HIDING, effectiveReason);
        showing.set(false);
        if (getSkin() == null) {
            completeShowingTransition(false);
        }
    }

    /**
     * Toggles the dial open or closed.
     */
    public final void toggle() {
        if (isShowing()) {
            close(CloseReason.TOGGLE);
        } else {
            open();
        }
    }

    private boolean fireCloseRequest(CloseReason reason) {
        RXSpeedDialEvent event = fireSpeedDialEvent(RXSpeedDialEvent.CLOSE_REQUEST, reason);
        return event.isConsumed();
    }

    private RXSpeedDialEvent fireSpeedDialEvent(EventType<RXSpeedDialEvent> eventType,
                                                CloseReason closeReason) {
        RXSpeedDialEvent event = new RXSpeedDialEvent(this, this, eventType, closeReason);
        fireEvent(event);
        return event;
    }

    private void completeShowingTransition(boolean showingState) {
        if (!terminalEventPending || isShowing() != showingState) {
            return;
        }
        terminalEventPending = false;
        if (showingState) {
            fireSpeedDialEvent(RXSpeedDialEvent.SHOWN, null);
            return;
        }
        CloseReason reason = pendingTerminalCloseReason == null ? CloseReason.TOGGLE : pendingTerminalCloseReason;
        pendingTerminalCloseReason = null;
        fireSpeedDialEvent(RXSpeedDialEvent.HIDDEN, reason);
    }

    // ==================== Direction ====================

    private final ObjectProperty<Direction> direction =
            new SimpleObjectProperty<>(this, "direction", Direction.UP) {
                @Override
                protected void invalidated() {
                    updateDirectionPseudoClasses();
                    requestLayout();
                }
            };

    /**
     * Direction in which actions are laid out. A {@code null} value is stored
     * as-is; the skin renders it as {@link Direction#UP}.
     *
     * @return the direction property
     */
    public final ObjectProperty<Direction> directionProperty() {
        return direction;
    }

    /**
     * Returns the action layout direction.
     *
     * @return the action layout direction, or {@code null}
     */
    public final Direction getDirection() {
        return direction.get();
    }

    /**
     * Sets the action layout direction.
     *
     * @param value the action layout direction, or {@code null}
     */
    public final void setDirection(Direction value) {
        direction.set(value);
    }

    private void updateDirectionPseudoClasses() {
        Direction current = getDirection();
        Direction effective = current == null ? Direction.UP : current;
        pseudoClassStateChanged(UP_PSEUDO_CLASS, effective == Direction.UP);
        pseudoClassStateChanged(DOWN_PSEUDO_CLASS, effective == Direction.DOWN);
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, effective == Direction.LEFT);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, effective == Direction.RIGHT);
    }

    // ==================== Icon ====================

    private final ObjectProperty<Node> icon = new SimpleObjectProperty<>(this, "icon");

    /**
     * Icon shown by the main FAB when the dial is closed.
     *
     * @return the icon property
     */
    public final ObjectProperty<Node> iconProperty() {
        return icon;
    }

    /**
     * Returns the closed-state main icon.
     *
     * @return the closed-state main icon, or {@code null}
     */
    public final Node getIcon() {
        return icon.get();
    }

    /**
     * Sets the closed-state main icon.
     *
     * @param value the closed-state main icon, or {@code null}
     */
    public final void setIcon(Node value) {
        icon.set(value);
    }

    // ==================== Open Icon ====================

    private final ObjectProperty<Node> openIcon = new SimpleObjectProperty<>(this, "openIcon");

    /**
     * Optional alternate icon available for the main FAB's open-state
     * presentation. A {@code null} value lets the skin continue using the closed
     * icon.
     *
     * @return the open icon property
     */
    public final ObjectProperty<Node> openIconProperty() {
        return openIcon;
    }

    /**
     * Returns the open-state main icon.
     *
     * @return the open-state main icon, or {@code null}
     */
    public final Node getOpenIcon() {
        return openIcon.get();
    }

    /**
     * Sets the open-state main icon.
     *
     * @param value the open-state main icon, or {@code null}
     */
    public final void setOpenIcon(Node value) {
        openIcon.set(value);
    }

    // ==================== Open Trigger ====================

    private final ObjectProperty<OpenTrigger> openTrigger =
            new SimpleObjectProperty<>(this, "openTrigger", OpenTrigger.CLICK);

    /**
     * Preferred interaction mode for opening the dial. A {@code null} value is
     * stored as-is and rendered as {@link OpenTrigger#CLICK} by the skin.
     *
     * @return the open-trigger property
     */
    public final ObjectProperty<OpenTrigger> openTriggerProperty() {
        return openTrigger;
    }

    /**
     * Returns the open trigger.
     *
     * @return the open trigger, or {@code null}
     */
    public final OpenTrigger getOpenTrigger() {
        return openTrigger.get();
    }

    /**
     * Sets the open trigger.
     *
     * @param value the open trigger, or {@code null}
     */
    public final void setOpenTrigger(OpenTrigger value) {
        openTrigger.set(value);
    }

    // ==================== Label Mode ====================

    private final ObjectProperty<LabelMode> labelMode =
            new SimpleObjectProperty<>(this, "labelMode", LabelMode.HOVER);

    /**
     * Preferred display mode for action labels. A {@code null} value is stored
     * as-is and rendered as {@link LabelMode#HOVER} by the default skin.
     *
     * @return the label-mode property
     */
    public final ObjectProperty<LabelMode> labelModeProperty() {
        return labelMode;
    }

    /**
     * Returns the label mode.
     *
     * @return the label mode, or {@code null}
     */
    public final LabelMode getLabelMode() {
        return labelMode.get();
    }

    /**
     * Sets the label mode.
     *
     * @param value the label mode, or {@code null}
     */
    public final void setLabelMode(LabelMode value) {
        labelMode.set(value);
    }

    // ==================== Label Placement ====================

    private final ObjectProperty<LabelPlacement> labelPlacement =
            new StyleableObjectProperty<>(LabelPlacement.AUTO) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, LabelPlacement> getCssMetaData() {
                    return StyleableProperties.LABEL_PLACEMENT;
                }

                @Override
                public Object getBean() {
                    return RXSpeedDial.this;
                }

                @Override
                public String getName() {
                    return "labelPlacement";
                }
            };

    /**
     * Preferred placement for action labels, styleable through
     * {@code -rx-label-placement}. A {@code null} value is stored as-is and
     * rendered as {@link LabelPlacement#AUTO} by the default skin.
     *
     * @return the label-placement property
     */
    public final ObjectProperty<LabelPlacement> labelPlacementProperty() {
        return labelPlacement;
    }

    /**
     * Returns the label placement.
     *
     * @return the label placement, or {@code null}
     */
    public final LabelPlacement getLabelPlacement() {
        return labelPlacement.get();
    }

    /**
     * Sets the label placement.
     *
     * @param value the label placement, or {@code null}
     */
    public final void setLabelPlacement(LabelPlacement value) {
        labelPlacement.set(value);
    }

    // ==================== Close On Focus Loss ====================

    private final BooleanProperty closeOnFocusLoss =
            new SimpleBooleanProperty(this, "closeOnFocusLoss", true);

    /**
     * Whether the dial closes when focus leaves it.
     *
     * @return the close-on-focus-loss property
     */
    public final BooleanProperty closeOnFocusLossProperty() {
        return closeOnFocusLoss;
    }

    /**
     * Returns whether the dial closes when focus leaves it.
     *
     * @return whether the dial closes when focus leaves it
     */
    public final boolean isCloseOnFocusLoss() {
        return closeOnFocusLoss.get();
    }

    /**
     * Sets whether the dial closes when focus leaves it.
     *
     * @param value {@code true} to close when focus leaves the dial
     */
    public final void setCloseOnFocusLoss(boolean value) {
        closeOnFocusLoss.set(value);
    }

    // ==================== Close On Click Outside ====================

    private final BooleanProperty closeOnClickOutside =
            new SimpleBooleanProperty(this, "closeOnClickOutside", true);

    /**
     * Whether the dial closes when the scene is clicked outside it.
     *
     * @return the close-on-click-outside property
     */
    public final BooleanProperty closeOnClickOutsideProperty() {
        return closeOnClickOutside;
    }

    /**
     * Returns whether the dial closes on outside clicks.
     *
     * @return whether the dial closes on outside clicks
     */
    public final boolean isCloseOnClickOutside() {
        return closeOnClickOutside.get();
    }

    /**
     * Sets whether the dial closes on outside clicks.
     *
     * @param value {@code true} to close on outside clicks
     */
    public final void setCloseOnClickOutside(boolean value) {
        closeOnClickOutside.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether open and close transitions are animated. This is a Java-only
     * property, not a CSS property.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether transitions are animated.
     *
     * @return whether transitions are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether transitions are animated.
     *
     * @param value {@code true} to animate transitions
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
                    return RXSpeedDial.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration used by open and close transitions, styleable through
     * {@code -rx-animation-duration}. A {@code null}, non-positive, or non-finite
     * value is stored as-is and rendered as an immediate transition by the default
     * skin.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration, or {@code null}
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Stagger Delay ====================

    private final ObjectProperty<Duration> staggerDelay =
            new StyleableObjectProperty<>(DEFAULT_STAGGER_DELAY) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.STAGGER_DELAY;
                }

                @Override
                public Object getBean() {
                    return RXSpeedDial.this;
                }

                @Override
                public String getName() {
                    return "staggerDelay";
                }
            };

    /**
     * Delay between adjacent action animations, styleable through
     * {@code -rx-stagger-delay}. A {@code null}, non-positive, or non-finite value
     * is stored as-is and rendered as no stagger by the default skin.
     *
     * @return the stagger-delay property
     */
    public final ObjectProperty<Duration> staggerDelayProperty() {
        return staggerDelay;
    }

    /**
     * Returns the stagger delay.
     *
     * @return the stagger delay, or {@code null}
     */
    public final Duration getStaggerDelay() {
        return staggerDelay.get();
    }

    /**
     * Sets the stagger delay.
     *
     * @param value the stagger delay, or {@code null}
     */
    public final void setStaggerDelay(Duration value) {
        staggerDelay.set(value);
    }

    // ==================== Action Spacing ====================

    private final DoubleProperty actionSpacing =
            new StyleableDoubleProperty(DEFAULT_ACTION_SPACING) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.ACTION_SPACING;
                }

                @Override
                public Object getBean() {
                    return RXSpeedDial.this;
                }

                @Override
                public String getName() {
                    return "actionSpacing";
                }
            };

    /**
     * Space between the main FAB and adjacent action FABs, styleable through
     * {@code -rx-action-spacing}. Non-finite or negative values are stored
     * as-is and clamped to {@code 0} by the default skin.
     *
     * @return the action-spacing property
     */
    public final DoubleProperty actionSpacingProperty() {
        return actionSpacing;
    }

    /**
     * Returns the space between the main FAB and adjacent action FABs.
     *
     * @return the action spacing
     */
    public final double getActionSpacing() {
        return actionSpacing.get();
    }

    /**
     * Sets the space between the main FAB and adjacent action FABs.
     *
     * @param value the action spacing
     */
    public final void setActionSpacing(double value) {
        actionSpacing.set(value);
    }

    // ==================== Label Gap ====================

    private final DoubleProperty labelGap =
            new StyleableDoubleProperty(DEFAULT_LABEL_GAP) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.LABEL_GAP;
                }

                @Override
                public Object getBean() {
                    return RXSpeedDial.this;
                }

                @Override
                public String getName() {
                    return "labelGap";
                }
            };

    /**
     * Space between each action FAB and its label, styleable through
     * {@code -rx-label-gap}. Non-finite or negative values are stored as-is and
     * clamped to {@code 0} by the default skin.
     *
     * @return the label-gap property
     */
    public final DoubleProperty labelGapProperty() {
        return labelGap;
    }

    /**
     * Returns the space between each action FAB and its label.
     *
     * @return the label gap
     */
    public final double getLabelGap() {
        return labelGap.get();
    }

    /**
     * Sets the space between each action FAB and its label.
     *
     * @param value the label gap
     */
    public final void setLabelGap(double value) {
        labelGap.set(value);
    }

    // ==================== Events ====================

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> onShowing;

    /**
     * Handler called before the dial expands.
     *
     * @return the onShowing property
     */
    public final ObjectProperty<EventHandler<RXSpeedDialEvent>> onShowingProperty() {
        if (onShowing == null) {
            onShowing = newHandlerProperty("onShowing", RXSpeedDialEvent.SHOWING);
        }
        return onShowing;
    }

    /**
     * Returns the onShowing handler.
     *
     * @return the onShowing handler, or {@code null}
     */
    public final EventHandler<RXSpeedDialEvent> getOnShowing() {
        return onShowing == null ? null : onShowing.get();
    }

    /**
     * Sets the onShowing handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnShowing(EventHandler<RXSpeedDialEvent> value) {
        onShowingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> onShown;

    /**
     * Handler called after the dial expands.
     *
     * @return the onShown property
     */
    public final ObjectProperty<EventHandler<RXSpeedDialEvent>> onShownProperty() {
        if (onShown == null) {
            onShown = newHandlerProperty("onShown", RXSpeedDialEvent.SHOWN);
        }
        return onShown;
    }

    /**
     * Returns the onShown handler.
     *
     * @return the onShown handler, or {@code null}
     */
    public final EventHandler<RXSpeedDialEvent> getOnShown() {
        return onShown == null ? null : onShown.get();
    }

    /**
     * Sets the onShown handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnShown(EventHandler<RXSpeedDialEvent> value) {
        onShownProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> onCloseRequest;

    /**
     * Handler called before any close proceeds. Consuming the event keeps the
     * dial open.
     *
     * @return the onCloseRequest property
     */
    public final ObjectProperty<EventHandler<RXSpeedDialEvent>> onCloseRequestProperty() {
        if (onCloseRequest == null) {
            onCloseRequest = newHandlerProperty("onCloseRequest", RXSpeedDialEvent.CLOSE_REQUEST);
        }
        return onCloseRequest;
    }

    /**
     * Returns the onCloseRequest handler.
     *
     * @return the onCloseRequest handler, or {@code null}
     */
    public final EventHandler<RXSpeedDialEvent> getOnCloseRequest() {
        return onCloseRequest == null ? null : onCloseRequest.get();
    }

    /**
     * Sets the onCloseRequest handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnCloseRequest(EventHandler<RXSpeedDialEvent> value) {
        onCloseRequestProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> onHiding;

    /**
     * Handler called before the dial collapses after close was not vetoed.
     *
     * @return the onHiding property
     */
    public final ObjectProperty<EventHandler<RXSpeedDialEvent>> onHidingProperty() {
        if (onHiding == null) {
            onHiding = newHandlerProperty("onHiding", RXSpeedDialEvent.HIDING);
        }
        return onHiding;
    }

    /**
     * Returns the onHiding handler.
     *
     * @return the onHiding handler, or {@code null}
     */
    public final EventHandler<RXSpeedDialEvent> getOnHiding() {
        return onHiding == null ? null : onHiding.get();
    }

    /**
     * Sets the onHiding handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnHiding(EventHandler<RXSpeedDialEvent> value) {
        onHidingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> onHidden;

    /**
     * Handler called after the dial collapses.
     *
     * @return the onHidden property
     */
    public final ObjectProperty<EventHandler<RXSpeedDialEvent>> onHiddenProperty() {
        if (onHidden == null) {
            onHidden = newHandlerProperty("onHidden", RXSpeedDialEvent.HIDDEN);
        }
        return onHidden;
    }

    /**
     * Returns the onHidden handler.
     *
     * @return the onHidden handler, or {@code null}
     */
    public final EventHandler<RXSpeedDialEvent> getOnHidden() {
        return onHidden == null ? null : onHidden.get();
    }

    /**
     * Sets the onHidden handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnHidden(EventHandler<RXSpeedDialEvent> value) {
        onHiddenProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXSpeedDialEvent>> newHandlerProperty(String name,
                                                                              EventType<RXSpeedDialEvent> type) {
        return new ObjectPropertyBase<>() {
            @Override
            protected void invalidated() {
                setEventHandler(type, get());
            }

            @Override
            public Object getBean() {
                return RXSpeedDial.this;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ==================== Accessibility ====================

    /**
     * Answers accessibility queries for speed-dial state.
     *
     * @param attribute  the queried attribute
     * @param parameters optional query parameters
     * @return the queried value, or the superclass value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.EXPANDED) {
            return isShowing();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSpeedDial, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXSpeedDial node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSpeedDial node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXSpeedDial, Duration> STAGGER_DELAY =
                new CssMetaData<>("-rx-stagger-delay",
                        DurationConverter.getInstance(), DEFAULT_STAGGER_DELAY) {
                    @Override
                    public boolean isSettable(RXSpeedDial node) {
                        return !node.staggerDelay.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSpeedDial node) {
                        return (StyleableProperty<Duration>) node.staggerDelayProperty();
                    }
                };

        private static final CssMetaData<RXSpeedDial, Number> ACTION_SPACING =
                new CssMetaData<>("-rx-action-spacing",
                        SizeConverter.getInstance(), DEFAULT_ACTION_SPACING) {
                    @Override
                    public boolean isSettable(RXSpeedDial node) {
                        return !node.actionSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSpeedDial node) {
                        return (StyleableProperty<Number>) node.actionSpacingProperty();
                    }
                };

        private static final CssMetaData<RXSpeedDial, Number> LABEL_GAP =
                new CssMetaData<>("-rx-label-gap",
                        SizeConverter.getInstance(), DEFAULT_LABEL_GAP) {
                    @Override
                    public boolean isSettable(RXSpeedDial node) {
                        return !node.labelGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSpeedDial node) {
                        return (StyleableProperty<Number>) node.labelGapProperty();
                    }
                };

        private static final CssMetaData<RXSpeedDial, LabelPlacement> LABEL_PLACEMENT =
                new CssMetaData<>("-rx-label-placement",
                        new EnumConverter<>(LabelPlacement.class), LabelPlacement.AUTO) {
                    @Override
                    public boolean isSettable(RXSpeedDial node) {
                        return !node.labelPlacement.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<LabelPlacement> getStyleableProperty(RXSpeedDial node) {
                        return (StyleableProperty<LabelPlacement>) node.labelPlacementProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATION_DURATION);
            styleables.add(STAGGER_DELAY);
            styleables.add(ACTION_SPACING);
            styleables.add(LABEL_GAP);
            styleables.add(LABEL_PLACEMENT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * Direction in which actions are laid out from the main FAB.
     */
    public enum Direction {

        /**
         * Actions are laid out above the main FAB.
         */
        UP,

        /**
         * Actions are laid out below the main FAB.
         */
        DOWN,

        /**
         * Actions are laid out left of the main FAB.
         */
        LEFT,

        /**
         * Actions are laid out right of the main FAB.
         */
        RIGHT
    }

    /**
     * Trigger mode available to speed-dial interaction wiring.
     */
    public enum OpenTrigger {

        /**
         * Action-event trigger mode.
         */
        CLICK,

        /**
         * Hover or focus trigger mode.
         */
        HOVER
    }

    /**
     * Requested visibility mode for action labels.
     */
    public enum LabelMode {

        /**
         * Hover or focus label mode.
         */
        HOVER,

        /**
         * Persistent label mode.
         */
        PERSISTENT,

        /**
         * No-label mode.
         */
        NONE
    }

    /**
     * Requested placement for action labels relative to the action FAB.
     */
    public enum LabelPlacement {

        /**
         * Uses the default placement for the current action direction.
         */
        AUTO,

        /**
         * Places labels at the cross-axis start side: left for vertical action
         * directions, top for horizontal action directions.
         */
        START,

        /**
         * Places labels at the cross-axis end side: right for vertical action
         * directions, bottom for horizontal action directions.
         */
        END
    }

    /**
     * Reason a dial close was requested.
     */
    public enum CloseReason {

        /**
         * The main FAB toggled the dial closed.
         */
        TOGGLE,

        /**
         * The Escape key requested close.
         */
        ESCAPE,

        /**
         * Focus left the dial.
         */
        FOCUS_LOST,

        /**
         * The scene was clicked outside the dial.
         */
        CLICK_OUTSIDE,

        /**
         * Pointer hover left the dial.
         */
        MOUSE_EXIT,

        /**
         * A secondary action fired.
         */
        ACTION
    }
}
