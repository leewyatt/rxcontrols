package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDrawerMode;
import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDrawerPaneSkin;

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
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
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
 * of truth {@link #showingProperty() showing}, exposed for binding and driven
 * imperatively by {@link #open()} / {@link #close()} / {@link #toggle()}.
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
 * <p>This is the first increment of the drawer: overlay sliding only. Scrim,
 * the {@code CLOSE_REQUEST} veto event, {@code PUSH} mode, header/footer chrome,
 * focus trapping and ESC handling are layered on in later increments.</p>
 */
public class RXDrawerPane extends Control {

    // ==================== Constants ====================

    /**
     * Default edge the drawer attaches to.
     */
    public static final Side DEFAULT_SIDE = Side.RIGHT;

    /**
     * Default drawer mode (overlay).
     */
    public static final RXDrawerMode DEFAULT_DRAWER_MODE = RXDrawerMode.OVERLAY;

    /**
     * Default open/close animation enabled state.
     */
    public static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default open/close animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(250.0);

    /**
     * Default open/close animation interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    /**
     * Default showing state.
     */
    public static final boolean DEFAULT_SHOWING = false;

    /**
     * Default preferred drawer width. {@link Region#USE_COMPUTED_SIZE} lets the
     * skin supply a default thickness for {@link Side#LEFT} / {@link Side#RIGHT}.
     */
    public static final double DEFAULT_PREF_DRAWER_WIDTH = Region.USE_COMPUTED_SIZE;

    /**
     * Default preferred drawer height. {@link Region#USE_COMPUTED_SIZE} lets the
     * skin supply a default thickness for {@link Side#TOP} / {@link Side#BOTTOM}.
     */
    public static final double DEFAULT_PREF_DRAWER_HEIGHT = Region.USE_COMPUTED_SIZE;

    /**
     * Default header title (empty). This is the constructed initial value; a later
     * {@code setTitle(null)} is a legal pass-through and is normalized to empty only
     * at render time.
     */
    public static final String DEFAULT_TITLE = "";

    /**
     * Default for whether the body wraps its content in a {@code ScrollPane}.
     */
    public static final boolean DEFAULT_SCROLLABLE = true;

    /**
     * Default for whether the header shows a close button.
     */
    public static final boolean DEFAULT_SHOW_CLOSE_BUTTON = true;

    /**
     * Default for whether the scrim (dimmed backdrop) is shown.
     */
    public static final boolean DEFAULT_SCRIM = true;

    /**
     * Default scrim opacity when open. The Material scrim convention.
     */
    public static final double DEFAULT_SCRIM_OPACITY = 0.32;

    /**
     * Default for whether clicking the scrim requests a close.
     */
    public static final boolean DEFAULT_DISMISS_ON_SCRIM_CLICK = true;

    /**
     * Default for whether pressing ESC requests a close.
     */
    public static final boolean DEFAULT_CLOSE_ON_ESC = true;

    private static final String DEFAULT_STYLE_CLASS = "rx-drawer-pane";

    private static final PseudoClass OPEN_PSEUDO_CLASS = PseudoClass.getPseudoClass("open");
    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");
    private static final PseudoClass TOP_PSEUDO_CLASS = PseudoClass.getPseudoClass("top");
    private static final PseudoClass BOTTOM_PSEUDO_CLASS = PseudoClass.getPseudoClass("bottom");
    private static final PseudoClass PUSH_PSEUDO_CLASS = PseudoClass.getPseudoClass("push");

    // ==================== Constructors ====================

    /**
     * Creates an empty drawer pane with default settings (right side, animated).
     */
    public RXDrawerPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DIALOG);
        updateDirectionPseudoClass();
        updatePushPseudoClass();
        showing.addListener((obs, wasShowing, isShowing) -> updateOpenPseudoClass(isShowing));
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
     * pane. May be {@code null} for an empty backdrop.
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
        private Side lastValid = DEFAULT_SIDE;

        @Override
        protected void invalidated() {
            Side value = get();
            if (value == null && !isBound()) {
                // A direct set(null) is a recoverable programmer error: revert and signal it.
                set(lastValid);
                throw new NullPointerException("side cannot be null");
            }
            if (value != null) {
                lastValid = value;
            }
            // A bound source may yield null, which cannot be reverted; the effective
            // side then falls back to the default for both the pseudo-class and the
            // skin's geometry (see RXDrawerPaneSkin.sideOrDefault), rather than
            // throwing back into the binding's own evaluation.
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
     * The edge the drawer attaches to and slides from. Cannot be set to
     * {@code null}.
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
     * @param value the side
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setSide(Side value) {
        side.set(value);
    }

    // ==================== Drawer Mode ====================

    private final ObjectProperty<RXDrawerMode> drawerMode =
            new StyleableObjectProperty<>(DEFAULT_DRAWER_MODE) {
                private RXDrawerMode lastValid = DEFAULT_DRAWER_MODE;

                @Override
                protected void invalidated() {
                    RXDrawerMode value = get();
                    if (value == null && !isBound()) {
                        set(lastValid);
                        throw new NullPointerException("drawerMode cannot be null");
                    }
                    if (value != null) {
                        lastValid = value;
                    }
                    updatePushPseudoClass();
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXDrawerMode> getCssMetaData() {
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
     * Whether the drawer overlays the content ({@link RXDrawerMode#OVERLAY}, the
     * default) or pushes it aside ({@link RXDrawerMode#PUSH}). Cannot be set to
     * {@code null}.
     *
     * @return the drawer mode property
     */
    public final ObjectProperty<RXDrawerMode> drawerModeProperty() {
        return drawerMode;
    }

    /**
     * Returns the drawer mode.
     *
     * @return the drawer mode
     */
    public final RXDrawerMode getDrawerMode() {
        return drawerMode.get();
    }

    /**
     * Sets the drawer mode.
     *
     * @param value the drawer mode
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setDrawerMode(RXDrawerMode value) {
        drawerMode.set(value);
    }

    // ==================== Showing ====================

    // Set while close() has already arbitrated the veto, so the showing
    // invalidated path does not fire a second CLOSE_REQUEST.
    private boolean vetoBypass;
    // The reason for the close currently being processed; read by the skin when it
    // fires the CLOSING / CLOSED events.
    private CloseReason activeCloseReason = CloseReason.PROGRAMMATIC;

    private final BooleanProperty showing = new SimpleBooleanProperty(this, "showing", DEFAULT_SHOWING) {
        @Override
        protected void invalidated() {
            if (get() || vetoBypass) {
                return;
            }
            // A direct setShowing(false) (or a binding pushing false) still passes
            // through the CLOSE_REQUEST veto; close() handles its own arbitration.
            activeCloseReason = CloseReason.PROGRAMMATIC;
            if (fireCloseRequest(CloseReason.PROGRAMMATIC) && !isBound()) {
                // Vetoed and revertible: stay open. A bound showing cannot be
                // reverted, so a vetoed bound-close proceeds (documented contract).
                set(true);
            }
        }
    };

    /**
     * The single source of truth for whether the drawer is requested open
     * ({@code true}) or closed ({@code false}). Bindable; equivalent to the web
     * {@code open} / {@code v-model:visible}. The skin observes this property and
     * drives the slide animation; the {@code :open} pseudo-class tracks it.
     *
     * @return the showing property
     */
    public final BooleanProperty showingProperty() {
        return showing;
    }

    /**
     * Returns whether the drawer is requested open.
     *
     * @return {@code true} if the drawer is requested open
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Requests the drawer open ({@code true}) or closed ({@code false}).
     *
     * @param value the requested showing state
     */
    public final void setShowing(boolean value) {
        showing.set(value);
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
                private Duration lastValid = DEFAULT_ANIMATION_DURATION;

                @Override
                protected void invalidated() {
                    Duration value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("animationDuration cannot be null");
                    }
                    if (value.isUnknown() || value.isIndefinite() || value.lessThan(Duration.ZERO)) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new IllegalArgumentException(
                                "animationDuration must be a finite non-negative duration");
                    }
                    lastValid = value;
                }

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
     * Duration of a single open/close transition. Must be a finite non-negative
     * duration; {@link Duration#ZERO} disables animation like {@code animated=false}.
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
     * @param value the animation duration
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is indefinite, unknown, or negative
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
     * value of {@code 0} or less (including the default
     * {@link Region#USE_COMPUTED_SIZE}) defers to the skin's default thickness.
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
     * @param value the preferred drawer width, or {@code <= 0} for the default
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
     * value of {@code 0} or less (including the default
     * {@link Region#USE_COMPUTED_SIZE}) defers to the skin's default thickness.
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
     * @param value the preferred drawer height, or {@code <= 0} for the default
     */
    public final void setPrefDrawerHeight(double value) {
        prefDrawerHeight.set(value);
    }

    // ==================== Title ====================

    private final StringProperty title = new SimpleStringProperty(this, "title", DEFAULT_TITLE);

    /**
     * The header title text. Accepts {@code null} (pure pass-through; the skin
     * renders {@code null} as an empty title).
     *
     * @return the title property
     */
    public final StringProperty titleProperty() {
        return title;
    }

    /**
     * Returns the header title.
     *
     * @return the title, possibly {@code null}
     */
    public final String getTitle() {
        return title.get();
    }

    /**
     * Sets the header title.
     *
     * @param value the title, or {@code null}
     */
    public final void setTitle(String value) {
        title.set(value);
    }

    // ==================== Footer ====================

    private final ObjectProperty<Node> footer = new SimpleObjectProperty<>(this, "footer");

    /**
     * The node hosted in the footer area, typically an action bar. {@code null}
     * (the default) renders no footer.
     *
     * @return the footer property
     */
    public final ObjectProperty<Node> footerProperty() {
        return footer;
    }

    /**
     * Returns the footer node.
     *
     * @return the footer node, or {@code null}
     */
    public final Node getFooter() {
        return footer.get();
    }

    /**
     * Sets the footer node.
     *
     * @param value the footer node, or {@code null}
     */
    public final void setFooter(Node value) {
        footer.set(value);
    }

    // ==================== Scrollable ====================

    private final BooleanProperty scrollable = new SimpleBooleanProperty(this, "scrollable", DEFAULT_SCROLLABLE);

    /**
     * Whether the body wraps its content in a {@code ScrollPane}. When {@code true}
     * (the default) long content scrolls; when {@code false} the body is a bare
     * container and the content decides its own scrolling.
     *
     * @return the scrollable property
     */
    public final BooleanProperty scrollableProperty() {
        return scrollable;
    }

    /**
     * Returns whether the body is scrollable.
     *
     * @return whether the body wraps a {@code ScrollPane}
     */
    public final boolean isScrollable() {
        return scrollable.get();
    }

    /**
     * Sets whether the body is scrollable.
     *
     * @param value whether the body wraps a {@code ScrollPane}
     */
    public final void setScrollable(boolean value) {
        scrollable.set(value);
    }

    // ==================== Show Close Button ====================

    private final BooleanProperty showCloseButton = new StyleableBooleanProperty(DEFAULT_SHOW_CLOSE_BUTTON) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_CLOSE_BUTTON;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "showCloseButton";
        }
    };

    /**
     * Whether the header shows a close button that requests a
     * {@link io.github.leewyatt.rxcontrols.enums.CloseReason#CLOSE_BUTTON CLOSE_BUTTON}
     * close when clicked.
     *
     * @return the show close button property
     */
    public final BooleanProperty showCloseButtonProperty() {
        return showCloseButton;
    }

    /**
     * Returns whether the header shows a close button.
     *
     * @return whether the close button is shown
     */
    public final boolean isShowCloseButton() {
        return showCloseButton.get();
    }

    /**
     * Sets whether the header shows a close button.
     *
     * @param value whether the close button is shown
     */
    public final void setShowCloseButton(boolean value) {
        showCloseButton.set(value);
    }

    // ==================== Scrim ====================

    private final BooleanProperty scrim = new StyleableBooleanProperty(DEFAULT_SCRIM) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SCRIM;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "scrim";
        }
    };

    /**
     * Whether a scrim (dimmed, click-catching backdrop) is shown behind the open
     * drawer. The scrim makes the drawer modal; turning it off yields a
     * non-modal overlay.
     *
     * @return the scrim property
     */
    public final BooleanProperty scrimProperty() {
        return scrim;
    }

    /**
     * Returns whether the scrim is shown.
     *
     * @return whether the scrim is shown
     */
    public final boolean isScrim() {
        return scrim.get();
    }

    /**
     * Sets whether the scrim is shown.
     *
     * @param value whether the scrim is shown
     */
    public final void setScrim(boolean value) {
        scrim.set(value);
    }

    // ==================== Scrim Opacity ====================

    private final DoubleProperty scrimOpacity = new StyleableDoubleProperty(DEFAULT_SCRIM_OPACITY) {
        @Override
        protected void invalidated() {
            if (isBound()) {
                return;
            }
            double value = get();
            double clamped = clampOpacity(value);
            if (clamped != value) {
                set(clamped);
            }
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.SCRIM_OPACITY;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "scrimOpacity";
        }
    };

    /**
     * The scrim opacity when the drawer is open, clamped to {@code [0, 1]} (a
     * non-finite value falls back to {@value #DEFAULT_SCRIM_OPACITY}).
     *
     * @return the scrim opacity property
     */
    public final DoubleProperty scrimOpacityProperty() {
        return scrimOpacity;
    }

    /**
     * Returns the scrim opacity.
     *
     * @return the scrim opacity
     */
    public final double getScrimOpacity() {
        return scrimOpacity.get();
    }

    /**
     * Sets the scrim opacity. Out-of-range values are clamped to {@code [0, 1]}.
     *
     * @param value the scrim opacity
     */
    public final void setScrimOpacity(double value) {
        scrimOpacity.set(value);
    }

    private static double clampOpacity(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_SCRIM_OPACITY;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ==================== Dismiss On Scrim Click ====================

    private final BooleanProperty dismissOnScrimClick =
            new StyleableBooleanProperty(DEFAULT_DISMISS_ON_SCRIM_CLICK) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.DISMISS_ON_SCRIM_CLICK;
                }

                @Override
                public Object getBean() {
                    return RXDrawerPane.this;
                }

                @Override
                public String getName() {
                    return "dismissOnScrimClick";
                }
            };

    /**
     * Whether clicking the scrim requests a close (with reason
     * {@link io.github.leewyatt.rxcontrols.enums.CloseReason#SCRIM_CLICK SCRIM_CLICK}).
     *
     * @return the dismiss-on-scrim-click property
     */
    public final BooleanProperty dismissOnScrimClickProperty() {
        return dismissOnScrimClick;
    }

    /**
     * Returns whether clicking the scrim requests a close.
     *
     * @return whether a scrim click dismisses the drawer
     */
    public final boolean isDismissOnScrimClick() {
        return dismissOnScrimClick.get();
    }

    /**
     * Sets whether clicking the scrim requests a close.
     *
     * @param value whether a scrim click dismisses the drawer
     */
    public final void setDismissOnScrimClick(boolean value) {
        dismissOnScrimClick.set(value);
    }

    // ==================== Close On Esc ====================

    private final BooleanProperty closeOnEsc = new StyleableBooleanProperty(DEFAULT_CLOSE_ON_ESC) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.CLOSE_ON_ESC;
        }

        @Override
        public Object getBean() {
            return RXDrawerPane.this;
        }

        @Override
        public String getName() {
            return "closeOnEsc";
        }
    };

    /**
     * Whether pressing ESC while the drawer is open requests a close (with reason
     * {@link io.github.leewyatt.rxcontrols.enums.CloseReason#ESC ESC}).
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
     * Requests the drawer open. Equivalent to {@code setShowing(true)}; a no-op
     * when already open or opening.
     */
    public final void open() {
        setShowing(true);
    }

    /**
     * Requests the drawer closed through the {@code CLOSE_REQUEST} veto: a handler
     * may {@link javafx.event.Event#consume() consume} the event to keep it open. A
     * no-op when already closed or closing.
     */
    public final void close() {
        requestClose(CloseReason.PROGRAMMATIC);
    }

    /**
     * Requests a close with a specific {@link CloseReason} through the
     * {@code CLOSE_REQUEST} veto. This method is intended to be used by experts,
     * primarily by those implementing Skins or Behaviors, to wire close triggers
     * (close button, ESC, scrim); ordinary code calls {@link #close()}. A no-op
     * when already closed or closing.
     *
     * @param reason why the close was requested
     */
    public final void requestClose(CloseReason reason) {
        if (!isShowing()) {
            return;
        }
        activeCloseReason = reason;
        if (fireCloseRequest(reason)) {
            return;
        }
        vetoBypass = true;
        try {
            setShowing(false);
        } finally {
            vetoBypass = false;
        }
    }

    /**
     * Toggles the drawer, derived from the {@link #showingProperty() showing}
     * intent: a mid-slide toggle reverses cleanly because the request — not the
     * transient translate — decides the direction. Toggling a shown drawer closed
     * goes through the {@code CLOSE_REQUEST} veto.
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
     * @param reason why the close was requested
     * @return {@code true} if a handler consumed the event (close vetoed)
     */
    private boolean fireCloseRequest(CloseReason reason) {
        RXDrawerEvent event = new RXDrawerEvent(RXDrawerEvent.CLOSE_REQUEST, this, reason);
        fireEvent(event);
        return event.isConsumed();
    }

    /**
     * Returns the reason for the close currently being processed. This method is
     * intended to be used by experts, primarily by those implementing new Skins or
     * Behaviors, to tag the {@code CLOSING} / {@code CLOSED} events; ordinary code
     * reads {@link RXDrawerEvent#getReason()} from a handler instead.
     *
     * @return the active close reason
     */
    public final CloseReason getActiveCloseReason() {
        return activeCloseReason;
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
        // Mirror the skin's sideOrDefault: a null (bound) side resolves to the
        // default so the pseudo-class never disagrees with the rendered geometry.
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
        pseudoClassStateChanged(PUSH_PSEUDO_CLASS, getDrawerMode() == RXDrawerMode.PUSH);
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

        private static final CssMetaData<RXDrawerPane, RXDrawerMode> DRAWER_MODE =
                new CssMetaData<>("-rx-drawer-mode",
                        new EnumConverter<>(RXDrawerMode.class), DEFAULT_DRAWER_MODE) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.drawerMode.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXDrawerMode> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<RXDrawerMode>) node.drawerModeProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> SHOW_CLOSE_BUTTON =
                new CssMetaData<>("-rx-show-close-button",
                        BooleanConverter.getInstance(), DEFAULT_SHOW_CLOSE_BUTTON) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.showCloseButton.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.showCloseButtonProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> SCRIM =
                new CssMetaData<>("-rx-scrim", BooleanConverter.getInstance(), DEFAULT_SCRIM) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.scrim.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.scrimProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Number> SCRIM_OPACITY =
                new CssMetaData<>("-rx-scrim-opacity", SizeConverter.getInstance(), DEFAULT_SCRIM_OPACITY) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.scrimOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Number>) node.scrimOpacityProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> DISMISS_ON_SCRIM_CLICK =
                new CssMetaData<>("-rx-dismiss-on-scrim-click",
                        BooleanConverter.getInstance(), DEFAULT_DISMISS_ON_SCRIM_CLICK) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.dismissOnScrimClick.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.dismissOnScrimClickProperty();
                    }
                };

        private static final CssMetaData<RXDrawerPane, Boolean> CLOSE_ON_ESC =
                new CssMetaData<>("-rx-close-on-esc", BooleanConverter.getInstance(), DEFAULT_CLOSE_ON_ESC) {
                    @Override
                    public boolean isSettable(RXDrawerPane node) {
                        return !node.closeOnEsc.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDrawerPane node) {
                        return (StyleableProperty<Boolean>) node.closeOnEscProperty();
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
            styleables.add(SHOW_CLOSE_BUTTON);
            styleables.add(SCRIM);
            styleables.add(SCRIM_OPACITY);
            styleables.add(DISMISS_ON_SCRIM_CLICK);
            styleables.add(CLOSE_ON_ESC);
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
