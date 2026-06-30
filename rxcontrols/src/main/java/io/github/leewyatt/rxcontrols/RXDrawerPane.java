package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDrawerPaneSkin;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An in-scene, single-container, transient overlay drawer. {@code RXDrawerPane}
 * lays a main {@link #contentProperty() content} node over the whole area and
 * slides a {@link #drawerContentProperty() drawerContent} panel in from one edge
 * on top of it. The panel is shown and hidden through the single boolean source
 * of truth {@link #showingProperty() showing}, a read-only state driven by
 * {@link #open()} / {@link #close()} / {@link #toggle()}.
 *
 * <p>The slide is a pure {@code translate} animation: layout always places the
 * panel at its open (edge-attached) position, and the closed state is expressed
 * by translating the panel off its edge. {@link #showingProperty() showing} is the
 * single source of truth (as in {@code ComboBoxBase}); queries never inspect the
 * transient translate value.</p>
 *
 * <pre>{@code
 * RXDrawerPane drawer = new RXDrawerPane();
 * drawer.setContent(mainPage);
 * drawer.setSide(Side.RIGHT);
 * drawer.setDrawerContent(detailForm);
 * openButton.setOnAction(e -> drawer.open());
 * }</pre>
 *
 * <p>The drawer can {@link DrawerMode#OVERLAY overlay} the content (optionally
 * over a dimming, click-catching backdrop that makes it modal) or
 * {@link DrawerMode#PUSH push} it aside. Closing flows through a vetoable
 * {@code CLOSE_REQUEST} {@link io.github.leewyatt.rxcontrols.event.RXDrawerEvent}
 * before any close proceeds. Drawer content
 * is rendered directly; applications own any header, body, footer, scrolling, and
 * close-button layout they need.</p>
 */
public class RXDrawerPane extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-drawer-pane";

    private static final PseudoClass OPEN_PSEUDO_CLASS = PseudoClass.getPseudoClass("open");
    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");
    private static final PseudoClass TOP_PSEUDO_CLASS = PseudoClass.getPseudoClass("top");
    private static final PseudoClass BOTTOM_PSEUDO_CLASS = PseudoClass.getPseudoClass("bottom");
    private static final PseudoClass PUSH_PSEUDO_CLASS = PseudoClass.getPseudoClass("push");

    // ==================== Constants ====================

    /**
     * Default edge the drawer attaches to.
     */
    public static final Side DEFAULT_SIDE = Side.RIGHT;

    /**
     * Default drawer mode (overlay).
     */
    public static final DrawerMode DEFAULT_DRAWER_MODE = DrawerMode.OVERLAY;

    /**
     * Default open/close animation enabled state.
     */
    private static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default open/close animation duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(220.0);

    /**
     * Default open/close animation interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    /**
     * Default showing state.
     */
    private static final boolean DEFAULT_SHOWING = false;

    /**
     * Default preferred drawer width. {@link Region#USE_COMPUTED_SIZE} lets the skin
     * size the {@link Side#LEFT} / {@link Side#RIGHT} panel to fit its content.
     */
    private static final double DEFAULT_PREF_DRAWER_WIDTH = Region.USE_COMPUTED_SIZE;

    /**
     * Default preferred drawer height. {@link Region#USE_COMPUTED_SIZE} lets the skin
     * size the {@link Side#TOP} / {@link Side#BOTTOM} panel to fit its content.
     */
    private static final double DEFAULT_PREF_DRAWER_HEIGHT = Region.USE_COMPUTED_SIZE;

    /**
     * Default for whether the backdrop is shown.
     */
    private static final boolean DEFAULT_BACKDROP_VISIBLE = true;

    /**
     * Default for whether clicking the backdrop requests a close.
     */
    private static final boolean DEFAULT_CLOSE_ON_BACKDROP_CLICK = true;

    /**
     * Default for whether pressing ESC requests a close.
     */
    private static final boolean DEFAULT_CLOSE_ON_ESC = true;

    // ==================== Constructors ====================

    /**
     * Creates an empty drawer pane with default settings (right side, animated).
     */
    public RXDrawerPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DIALOG);
        updateDirectionPseudoClass();
        updatePushPseudoClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDrawerPaneSkin(this);
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
     * <p>The drawer forwards the content node's bias so a width-fitting parent
     * sizes the pane correctly; the floating drawer panel never contributes.</p>
     */
    @Override
    public Orientation getContentBias() {
        Node node = getContent();
        return node == null ? null : node.getContentBias();
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * The main content node laid out behind the drawer panel, filling the whole
     * pane. May be {@code null} for an empty pane.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the main content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the main content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Drawer Content ====================

    private final ObjectProperty<Node> drawerContent = new SimpleObjectProperty<>(this, "drawerContent");

    /**
     * The node hosted inside the sliding drawer panel. May be {@code null} for an
     * empty panel.
     *
     * @return the drawer content property
     */
    public final ObjectProperty<Node> drawerContentProperty() {
        return drawerContent;
    }

    /**
     * Returns the drawer content node.
     *
     * @return the drawer content node, or {@code null}
     */
    public final Node getDrawerContent() {
        return drawerContent.get();
    }

    /**
     * Sets the drawer content node.
     *
     * @param value the drawer content node, or {@code null}
     */
    public final void setDrawerContent(Node value) {
        drawerContent.set(value);
    }

    // ==================== Side ====================

    private final ObjectProperty<Side> side = new StyleableObjectProperty<>(DEFAULT_SIDE) {
        @Override
        protected void invalidated() {
            updateDirectionPseudoClass();
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Side> getCssMetaData() {
            return StyleableProperties.SIDE;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "side";
        }
    };

    /**
     * The edge the drawer attaches to and slides from. A {@code null} value is not
     * rejected; it resolves to the default {@link #DEFAULT_SIDE} at the use site.
     *
     * @return the side property
     */
    public final ObjectProperty<Side> sideProperty() {
        return side;
    }

    /**
     * Returns the side.
     *
     * @return the side
     */
    public final Side getSide() {
        return side.get();
    }

    /**
     * Sets the side.
     *
     * @param value the side, or {@code null} to fall back to the default
     */
    public final void setSide(Side value) {
        side.set(value);
    }

    // ==================== Drawer Mode ====================

    private final ObjectProperty<DrawerMode> drawerMode =
            new StyleableObjectProperty<>(DEFAULT_DRAWER_MODE) {
                @Override
                protected void invalidated() {
                    updatePushPseudoClass();
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, DrawerMode> getCssMetaData() {
                    return StyleableProperties.DRAWER_MODE;
                }

                @Override
                public Object getBean() {
                    return RXDrawerPane.this;
                }

                @Override
                public String getName() {
                    return "drawerMode";
                }
            };

    /**
     * Whether the drawer overlays the content ({@link DrawerMode#OVERLAY}, the
     * default) or pushes it aside ({@link DrawerMode#PUSH}). A {@code null} value
     * is not rejected; it resolves to the default {@link #DEFAULT_DRAWER_MODE} at the
     * use site.
     *
     * @return the drawer mode property
     */
    public final ObjectProperty<DrawerMode> drawerModeProperty() {
        return drawerMode;
    }

    /**
     * Returns the drawer mode.
     *
     * @return the drawer mode
     */
    public final DrawerMode getDrawerMode() {
        return drawerMode.get();
    }

    /**
     * Sets the drawer mode.
     *
     * @param value the drawer mode, or {@code null} to fall back to the default
     */
    public final void setDrawerMode(DrawerMode value) {
        drawerMode.set(value);
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing =
            new ReadOnlyBooleanWrapper(this, "showing", DEFAULT_SHOWING) {
                @Override
                protected void invalidated() {
                    updateOpenPseudoClass(get());
                }
            };

    /**
     * Whether the drawer is open ({@code true}) or closed ({@code false}). This is
     * the read-only, committed source of truth, mirroring {@code ComboBoxBase.showing}:
     * it is driven through {@link #open()} / {@link #close()} / {@link #toggle()}, not
     * written directly. The skin observes it to run the slide and the {@code :open}
     * pseudo-class tracks it.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the drawer is open.
     *
     * @return {@code true} if the drawer is open
     */
    public final boolean isShowing() {
        return showing.get();
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether open/close transitions animate. When {@code false}, transitions snap
     * to their final state; turning it off mid-flight snaps the running transition.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether open/close transitions animate.
     *
     * @return whether transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether open/close transitions animate.
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
                    return RXDrawerPane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single open/close transition. A {@code null}, non-positive,
     * unknown, or indefinite value is not rejected; it disables animation (the
     * transition snaps), like {@code animated=false} or {@link Duration#ZERO}.
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
     * Interpolator used for open/close transitions. Accepts {@code null}, which the
     * skin treats as {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable: there
     * is no stable public CSS converter for an arbitrary {@link Interpolator}.
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

    // ==================== Pref Drawer Width ====================

    private final DoubleProperty prefDrawerWidth =
            new SimpleDoubleProperty(this, "prefDrawerWidth", DEFAULT_PREF_DRAWER_WIDTH) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * The drawer panel thickness for {@link Side#LEFT} / {@link Side#RIGHT}. A
     * positive value is the preferred width; the default {@link Region#USE_COMPUTED_SIZE}
     * (or any value {@code <= 0}) instead takes the {@link #drawerContentProperty()
     * drawerContent}'s preferred width, falling back to the skin's default thickness only
     * when the content has none. Either way the result is bounded by the content's
     * min/max, so the panel is never narrower than the content's minimum nor wider than
     * its maximum.
     *
     * @return the preferred drawer width property
     */
    public final DoubleProperty prefDrawerWidthProperty() {
        return prefDrawerWidth;
    }

    /**
     * Returns the preferred drawer width.
     *
     * @return the preferred drawer width
     */
    public final double getPrefDrawerWidth() {
        return prefDrawerWidth.get();
    }

    /**
     * Sets the preferred drawer width.
     *
     * @param value the preferred drawer width, or {@code <= 0} to fit the drawer content
     */
    public final void setPrefDrawerWidth(double value) {
        prefDrawerWidth.set(value);
    }

    // ==================== Pref Drawer Height ====================

    private final DoubleProperty prefDrawerHeight =
            new SimpleDoubleProperty(this, "prefDrawerHeight", DEFAULT_PREF_DRAWER_HEIGHT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * The drawer panel thickness for {@link Side#TOP} / {@link Side#BOTTOM}. A
     * positive value is the preferred height; the default {@link Region#USE_COMPUTED_SIZE}
     * (or any value {@code <= 0}) instead takes the {@link #drawerContentProperty()
     * drawerContent}'s preferred height, falling back to the skin's default thickness only
     * when the content has none. Either way the result is bounded by the content's
     * min/max, so the panel is never shorter than the content's minimum nor taller than
     * its maximum.
     *
     * @return the preferred drawer height property
     */
    public final DoubleProperty prefDrawerHeightProperty() {
        return prefDrawerHeight;
    }

    /**
     * Returns the preferred drawer height.
     *
     * @return the preferred drawer height
     */
    public final double getPrefDrawerHeight() {
        return prefDrawerHeight.get();
    }

    /**
     * Sets the preferred drawer height.
     *
     * @param value the preferred drawer height, or {@code <= 0} to fit the drawer content
     */
    public final void setPrefDrawerHeight(double value) {
        prefDrawerHeight.set(value);
    }

    // ==================== Backdrop ====================

    private final BooleanProperty backdropVisible =
            new StyleableBooleanProperty(DEFAULT_BACKDROP_VISIBLE) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.BACKDROP_VISIBLE;
                }

                @Override
                public Object getBean() {
                    return RXDrawerPane.this;
                }

                @Override
                public String getName() {
                    return "backdropVisible";
                }
            };

    /**
     * Whether the dimmed, click-catching backdrop is shown
     * behind the open drawer. Only effective in {@link DrawerMode#OVERLAY} mode:
     * showing it makes the drawer modal; hiding it yields a non-modal overlay. The
     * dim level is styled on {@code .backdrop} via {@code -fx-background-color}
     * (e.g. {@code rgba(0,0,0,0.32)}), so there is no opacity property.
     *
     * @return the backdrop visible property
     */
    public final BooleanProperty backdropVisibleProperty() {
        return backdropVisible;
    }

    /**
     * Returns whether the backdrop is shown.
     *
     * @return whether the backdrop is shown
     */
    public final boolean isBackdropVisible() {
        return backdropVisible.get();
    }

    /**
     * Sets whether the backdrop is shown.
     *
     * @param value whether the backdrop is shown
     */
    public final void setBackdropVisible(boolean value) {
        backdropVisible.set(value);
    }

    // ==================== Close On Backdrop Click ====================

    private final BooleanProperty closeOnBackdropClick =
            new SimpleBooleanProperty(this, "closeOnBackdropClick", DEFAULT_CLOSE_ON_BACKDROP_CLICK);

    /**
     * Whether clicking the backdrop requests a close.
     *
     * @return the close-on-backdrop-click property
     */
    public final BooleanProperty closeOnBackdropClickProperty() {
        return closeOnBackdropClick;
    }

    /**
     * Returns whether clicking the backdrop requests a close.
     *
     * @return whether a backdrop click closes the drawer
     */
    public final boolean isCloseOnBackdropClick() {
        return closeOnBackdropClick.get();
    }

    /**
     * Sets whether clicking the backdrop requests a close.
     *
     * @param value whether a backdrop click closes the drawer
     */
    public final void setCloseOnBackdropClick(boolean value) {
        closeOnBackdropClick.set(value);
    }

    // ==================== Close On Esc ====================

    private final BooleanProperty closeOnEsc =
            new SimpleBooleanProperty(this, "closeOnEsc", DEFAULT_CLOSE_ON_ESC);

    /**
     * Whether pressing ESC while the drawer is open requests a close.
     *
     * @return the close-on-esc property
     */
    public final BooleanProperty closeOnEscProperty() {
        return closeOnEsc;
    }

    /**
     * Returns whether ESC requests a close.
     *
     * @return whether ESC closes the drawer
     */
    public final boolean isCloseOnEsc() {
        return closeOnEsc.get();
    }

    /**
     * Sets whether ESC requests a close.
     *
     * @param value whether ESC closes the drawer
     */
    public final void setCloseOnEsc(boolean value) {
        closeOnEsc.set(value);
    }

    // ==================== Open / Close / Toggle ====================

    /**
     * Opens the drawer. A no-op when already open.
     */
    public final void open() {
        if (!isShowing()) {
            showing.set(true);
        }
    }

    /**
     * Requests the drawer closed through the {@code CLOSE_REQUEST} veto: a handler
     * may {@link javafx.event.Event#consume() consume} the event to keep it open. A
     * no-op when already closed.
     */
    public final void close() {
        if (!isShowing()) {
            return;
        }
        if (fireCloseRequest()) {
            return;
        }
        showing.set(false);
    }

    /**
     * Toggles the drawer: opens it when closed, or requests a close — through the
     * {@code CLOSE_REQUEST} veto — when open. A mid-slide toggle reverses cleanly
     * because the committed {@link #showingProperty() showing} state, not the
     * transient translate, decides the direction.
     */
    public final void toggle() {
        if (isShowing()) {
            close();
        } else {
            open();
        }
    }

    /**
     * Fires a {@code CLOSE_REQUEST} and reports whether a handler vetoed it.
     *
     * @return {@code true} if a handler consumed the event (close vetoed)
     */
    private boolean fireCloseRequest() {
        RXDrawerEvent event = new RXDrawerEvent(RXDrawerEvent.CLOSE_REQUEST, this);
        fireEvent(event);
        return event.isConsumed();
    }

    // ==================== Events ====================

    private ObjectProperty<EventHandler<RXDrawerEvent>> onOpening;

    /**
     * Handler called when an open slide starts.
     *
     * @return the onOpening property
     */
    public final ObjectProperty<EventHandler<RXDrawerEvent>> onOpeningProperty() {
        if (onOpening == null) {
            onOpening = newHandlerProperty("onOpening", RXDrawerEvent.OPENING);
        }
        return onOpening;
    }

    /**
     * Returns the onOpening handler.
     *
     * @return the onOpening handler, or {@code null}
     */
    public final EventHandler<RXDrawerEvent> getOnOpening() {
        return onOpening == null ? null : onOpening.get();
    }

    /**
     * Sets the onOpening handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnOpening(EventHandler<RXDrawerEvent> value) {
        onOpeningProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDrawerEvent>> onOpened;

    /**
     * Handler called when an open slide has fully completed.
     *
     * @return the onOpened property
     */
    public final ObjectProperty<EventHandler<RXDrawerEvent>> onOpenedProperty() {
        if (onOpened == null) {
            onOpened = newHandlerProperty("onOpened", RXDrawerEvent.OPENED);
        }
        return onOpened;
    }

    /**
     * Returns the onOpened handler.
     *
     * @return the onOpened handler, or {@code null}
     */
    public final EventHandler<RXDrawerEvent> getOnOpened() {
        return onOpened == null ? null : onOpened.get();
    }

    /**
     * Sets the onOpened handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnOpened(EventHandler<RXDrawerEvent> value) {
        onOpenedProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDrawerEvent>> onCloseRequest;

    /**
     * Handler called before any close proceeds; {@link javafx.event.Event#consume()
     * consuming} the event keeps the drawer open.
     *
     * @return the onCloseRequest property
     */
    public final ObjectProperty<EventHandler<RXDrawerEvent>> onCloseRequestProperty() {
        if (onCloseRequest == null) {
            onCloseRequest = newHandlerProperty("onCloseRequest", RXDrawerEvent.CLOSE_REQUEST);
        }
        return onCloseRequest;
    }

    /**
     * Returns the onCloseRequest handler.
     *
     * @return the onCloseRequest handler, or {@code null}
     */
    public final EventHandler<RXDrawerEvent> getOnCloseRequest() {
        return onCloseRequest == null ? null : onCloseRequest.get();
    }

    /**
     * Sets the onCloseRequest handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnCloseRequest(EventHandler<RXDrawerEvent> value) {
        onCloseRequestProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDrawerEvent>> onClosing;

    /**
     * Handler called when a close slide starts (the close was not vetoed).
     *
     * @return the onClosing property
     */
    public final ObjectProperty<EventHandler<RXDrawerEvent>> onClosingProperty() {
        if (onClosing == null) {
            onClosing = newHandlerProperty("onClosing", RXDrawerEvent.CLOSING);
        }
        return onClosing;
    }

    /**
     * Returns the onClosing handler.
     *
     * @return the onClosing handler, or {@code null}
     */
    public final EventHandler<RXDrawerEvent> getOnClosing() {
        return onClosing == null ? null : onClosing.get();
    }

    /**
     * Sets the onClosing handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnClosing(EventHandler<RXDrawerEvent> value) {
        onClosingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDrawerEvent>> onClosed;

    /**
     * Handler called when a close slide has fully completed.
     *
     * @return the onClosed property
     */
    public final ObjectProperty<EventHandler<RXDrawerEvent>> onClosedProperty() {
        if (onClosed == null) {
            onClosed = newHandlerProperty("onClosed", RXDrawerEvent.CLOSED);
        }
        return onClosed;
    }

    /**
     * Returns the onClosed handler.
     *
     * @return the onClosed handler, or {@code null}
     */
    public final EventHandler<RXDrawerEvent> getOnClosed() {
        return onClosed == null ? null : onClosed.get();
    }

    /**
     * Sets the onClosed handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnClosed(EventHandler<RXDrawerEvent> value) {
        onClosedProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDrawerEvent>> newHandlerProperty(String name,
                                                                           EventType<RXDrawerEvent> type) {
        return new ObjectPropertyBase<>() {
            @Override
            protected void invalidated() {
                setEventHandler(type, get());
            }

            @Override
            public Object getBean() {
                return RXDrawerPane.this;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ==================== PseudoClass ====================

    private void updateDirectionPseudoClass() {
        Side current = getSide();
        if (current == null) {
            current = DEFAULT_SIDE;
        }
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, current == Side.LEFT);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, current == Side.RIGHT);
        pseudoClassStateChanged(TOP_PSEUDO_CLASS, current == Side.TOP);
        pseudoClassStateChanged(BOTTOM_PSEUDO_CLASS, current == Side.BOTTOM);
    }

    private void updateOpenPseudoClass(boolean showing) {
        pseudoClassStateChanged(OPEN_PSEUDO_CLASS, showing);
    }

    private void updatePushPseudoClass() {
        pseudoClassStateChanged(PUSH_PSEUDO_CLASS, getDrawerMode() == DrawerMode.PUSH);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXDrawerPane, Side> SIDE =
                new CssMetaData<>("-rx-side", new EnumConverter<>(Side.class), DEFAULT_SIDE) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.side.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Side> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Side>) node.sideProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, DrawerMode> DRAWER_MODE =
                new CssMetaData<>("-rx-drawer-mode",
                        new EnumConverter<>(DrawerMode.class), DEFAULT_DRAWER_MODE) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.drawerMode.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<DrawerMode> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<DrawerMode>) node.drawerModeProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> BACKDROP_VISIBLE =
                new CssMetaData<>("-rx-backdrop-visible",
                        BooleanConverter.getInstance(), DEFAULT_BACKDROP_VISIBLE) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.backdropVisible.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.backdropVisibleProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(SIDE);
            styleables.add(DRAWER_MODE);
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            styleables.add(BACKDROP_VISIBLE);
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
     * How an {@link RXDrawerPane} positions its drawer panel relative to the main
     * content.
     */
    public enum DrawerMode {

        /**
         * The panel floats over the content, which stays in place.
         */
        OVERLAY,

        /**
         * The panel pushes the content aside and relayouts the content area as it
         * opens.
         */
        PUSH
    }
}
