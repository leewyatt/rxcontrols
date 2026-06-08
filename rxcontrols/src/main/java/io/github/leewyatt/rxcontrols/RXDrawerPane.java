package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDrawerPaneSkin;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
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

    private static final String DEFAULT_STYLE_CLASS = "rx-drawer-pane";

    private static final PseudoClass OPEN_PSEUDO_CLASS = PseudoClass.getPseudoClass("open");
    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");
    private static final PseudoClass TOP_PSEUDO_CLASS = PseudoClass.getPseudoClass("top");
    private static final PseudoClass BOTTOM_PSEUDO_CLASS = PseudoClass.getPseudoClass("bottom");

    // ==================== Constructors ====================

    /**
     * Creates an empty drawer pane with default settings (right side, animated).
     */
    public RXDrawerPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DIALOG);
        updateDirectionPseudoClass();
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
            if (value == null) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new NullPointerException("side cannot be null");
            }
            lastValid = value;
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

    // ==================== Showing ====================

    private final BooleanProperty showing = new SimpleBooleanProperty(this, "showing", DEFAULT_SHOWING);

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

    // ==================== Open / Close / Toggle ====================

    /**
     * Requests the drawer open. Equivalent to {@code setShowing(true)}; a no-op
     * when already open or opening.
     */
    public final void open() {
        setShowing(true);
    }

    /**
     * Requests the drawer closed. Equivalent to {@code setShowing(false)}; a no-op
     * when already closed or closing.
     */
    public final void close() {
        setShowing(false);
    }

    /**
     * Toggles the drawer, derived from the {@link #showingProperty() showing}
     * intent: a mid-slide toggle reverses cleanly because the request — not the
     * transient translate — decides the direction.
     */
    public final void toggle() {
        setShowing(!isShowing());
    }

    // ==================== PseudoClass ====================

    private void updateDirectionPseudoClass() {
        Side current = getSide();
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, current == Side.LEFT);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, current == Side.RIGHT);
        pseudoClassStateChanged(TOP_PSEUDO_CLASS, current == Side.TOP);
        pseudoClassStateChanged(BOTTOM_PSEUDO_CLASS, current == Side.BOTTOM);
    }

    private void updateOpenPseudoClass(boolean showing) {
        pseudoClassStateChanged(OPEN_PSEUDO_CLASS, showing);
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

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(SIDE);
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
}
