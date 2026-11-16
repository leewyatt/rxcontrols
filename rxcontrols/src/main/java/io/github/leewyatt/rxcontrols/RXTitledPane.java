package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTitledPaneSkin;
import javafx.animation.Interpolator;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
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
import javafx.css.converter.EnumConverter;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A collapsible panel made of a header and a body. It works on its own or as a
 * member of an {@link RXAccordion}.
 *
 * <p>The header shows either a default title built from {@link #textProperty() text}
 * and {@link #graphicProperty() graphic}, or a caller-supplied
 * {@link #headerProperty() header} node, which then replaces the default title. A
 * {@link #headerTrailingProperty() headerTrailing} node at the header's trailing edge
 * holds header controls (buttons, menus, switches, fields); interacting with it never
 * toggles the pane.</p>
 *
 * <pre>{@code
 * RXTitledPane pane = new RXTitledPane("Report", reportView);
 * pane.setHeaderTrailing(new HBox(8, refreshButton, moreMenu));
 * pane.setExpanded(true);
 * }</pre>
 *
 * <p>{@link #expandedProperty() expanded} is the single logical state: it changes at
 * once, while the skin animates the body toward it. A click anywhere on the header
 * outside the trailing node toggles the pane unless a node inside the title consumed
 * the press (every JavaFX control does);
 * {@link #toggleTriggerProperty() toggleTrigger} can restrict pointer toggling to the
 * expand indicator. The whole header row is the hover / ripple surface and the
 * keyboard focus stop: Space or Enter toggles it (Enter is taken from a default
 * button while the header has focus).</p>
 *
 * <p>The body can be given directly through {@link #contentProperty() content}, or
 * created lazily: while {@code content} is {@code null}, expanding the pane calls the
 * {@link #contentFactoryProperty() contentFactory} once and stores the result in
 * {@code content}. Collapsing never discards content; a collapsed body stays in the
 * scene graph but is not rendered, picked or focus-traversed. Resources owned by the
 * content (animations, media, threads) are not stopped on collapse.</p>
 *
 * <p>The pane is sized to its content: the height follows the header plus the
 * revealed part of the body, and the maximum height equals the preferred height, so
 * a collapsed pane never stretches. Long bodies are scrolled by wrapping the pane (or
 * the accordion) in a {@code ScrollPane}.</p>
 */
@DefaultProperty("content")
public class RXTitledPane extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-titled-pane";

    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass COLLAPSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("collapsed");
    private static final PseudoClass INDICATOR_TRIGGER_PSEUDO_CLASS = PseudoClass.getPseudoClass("indicator-trigger");
    private static final PseudoClass FIRST_PSEUDO_CLASS = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST_PSEUDO_CLASS = PseudoClass.getPseudoClass("last");

    // ==================== Constants ====================

    /**
     * Default pointer trigger: the whole header, except the trailing node, toggles the pane.
     */
    public static final ToggleTrigger DEFAULT_TOGGLE_TRIGGER = ToggleTrigger.HEADER;

    /**
     * Default position of the expand indicator.
     */
    public static final IndicatorPosition DEFAULT_INDICATOR_POSITION = IndicatorPosition.START;

    /**
     * Default expand / collapse interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final boolean DEFAULT_ANIMATED = true;

    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    // ==================== Internal State ====================

    private RXAccordion accordion;

    private boolean creatingContent;

    // ==================== Constructors ====================

    /**
     * Creates a collapsed pane with no title and no content.
     */
    public RXTitledPane() {
        this(null, null);
    }

    /**
     * Creates a collapsed pane with the given title text.
     *
     * @param text the title text, or {@code null}
     */
    public RXTitledPane(String text) {
        this(text, null);
    }

    /**
     * Creates a collapsed pane with the given title text and content.
     *
     * @param text    the title text, or {@code null}
     * @param content the body content, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXTitledPane(@NamedArg("text") String text, @NamedArg("content") Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // The title inside the skin is the focus stop; the pane itself only forwards
        // requestFocus() to it, so it must not be a traversal stop of its own.
        ((StyleableProperty<Boolean>) (StyleableProperty<?>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        setText(text);
        setContent(content);
        updateExpandedPseudoClass();
        updateTriggerPseudoClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTitledPaneSkin(this);
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
     * <p>Always {@link Orientation#HORIZONTAL}: a wrapping title or a width-dependent
     * body makes the height depend on the width.</p>
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * Text of the default title. It is also the accessible name of the title when
     * {@link #accessibleTextProperty() accessibleText} is not set, including while a
     * custom {@link #headerProperty() header} is shown.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the title text.
     *
     * @return the title text, possibly {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the title text.
     *
     * @param value the title text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * Graphic of the default title, shown before the text. Not displayed while a
     * custom {@link #headerProperty() header} is set; the skin then releases the node,
     * so it may be reused elsewhere.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the title graphic.
     *
     * @return the title graphic, possibly {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the title graphic.
     *
     * @param value the title graphic, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Header ====================

    private final ObjectProperty<Node> header = new SimpleObjectProperty<>(this, "header");

    /**
     * Custom title node. When non-null it replaces the default title built from
     * {@link #textProperty() text} and {@link #graphicProperty() graphic}, which are
     * then not displayed; setting it back to {@code null} restores the default title.
     *
     * <p>The node lives inside the toggle area: a press it consumes (every JavaFX
     * control does) does not toggle the pane, any other press does. Interactive nodes
     * belong in {@link #headerTrailingProperty() headerTrailing}, where a press never
     * starts the header ripple. Give a graphic-only header an accessible name through {@link #accessibleTextProperty() accessibleText} or
     * {@link #textProperty() text}.</p>
     *
     * @return the header property
     */
    public final ObjectProperty<Node> headerProperty() {
        return header;
    }

    /**
     * Returns the custom title node.
     *
     * @return the custom title node, or {@code null} for the default title
     */
    public final Node getHeader() {
        return header.get();
    }

    /**
     * Sets the custom title node.
     *
     * @param value the custom title node, or {@code null} for the default title
     */
    public final void setHeader(Node value) {
        header.set(value);
    }

    // ==================== Header Trailing ====================

    private final ObjectProperty<Node> headerTrailing = new SimpleObjectProperty<>(this, "headerTrailing");

    /**
     * A node at the header's trailing edge (the right in left-to-right, mirrored under
     * RTL), for header controls such as buttons, menus, switches, fields or badges;
     * wrap several in a container such as an {@code HBox}. The trailing slot is inert:
     * no pointer or keyboard interaction inside it toggles the pane or starts the
     * header ripple. It keeps its preferred width and shrinks toward its minimum width
     * only after the title has been truncated to its minimum. An
     * {@link IndicatorPosition#END END} indicator sits after it. May be {@code null}.
     *
     * @return the header-trailing property
     */
    public final ObjectProperty<Node> headerTrailingProperty() {
        return headerTrailing;
    }

    /**
     * Returns the header-trailing node.
     *
     * @return the header-trailing node, or {@code null}
     */
    public final Node getHeaderTrailing() {
        return headerTrailing.get();
    }

    /**
     * Sets the header-trailing node.
     *
     * @param value the header-trailing node, or {@code null}
     */
    public final void setHeaderTrailing(Node value) {
        headerTrailing.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            createContentIfNeeded();
        }

        @Override
        public Object getBean() {
            return RXTitledPane.this;
        }

        @Override
        public String getName() {
            return "content";
        }
    };

    /**
     * Body content. Whenever it is {@code null} while the pane is expanded, the
     * {@link #contentFactoryProperty() contentFactory} (if any) is asked for it, so
     * clearing the content of an expanded pane rebuilds it from the factory.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the body content.
     *
     * @return the body content, possibly {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the body content.
     *
     * @param value the body content, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Content Factory ====================

    private final ObjectProperty<Supplier<Node>> contentFactory = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            createContentIfNeeded();
        }

        @Override
        public Object getBean() {
            return RXTitledPane.this;
        }

        @Override
        public String getName() {
            return "contentFactory";
        }
    };

    /**
     * Lazy body factory. It runs on the JavaFX Application Thread whenever the pane is
     * expanded while {@link #contentProperty() content} is {@code null} (when it
     * expands, when the factory is set on an expanded pane, or when the content of an
     * expanded pane is cleared), and its result is stored in {@code content}; measuring
     * a collapsed pane never calls it. Direct content always wins, replacing the
     * factory keeps the current content, and a bound {@code content} is never written.
     *
     * <p>If the factory throws, the exception goes to the current thread's uncaught
     * exception handler and {@code content} stays {@code null}, as it does when the
     * factory returns {@code null}; the factory is asked again the next time the pane
     * expands. Long-running work belongs in the application, not in the factory.</p>
     *
     * @return the content factory property
     */
    public final ObjectProperty<Supplier<Node>> contentFactoryProperty() {
        return contentFactory;
    }

    /**
     * Returns the lazy body factory.
     *
     * @return the content factory, possibly {@code null}
     */
    public final Supplier<Node> getContentFactory() {
        return contentFactory.get();
    }

    /**
     * Sets the lazy body factory.
     *
     * @param value the content factory, or {@code null}
     */
    public final void setContentFactory(Supplier<Node> value) {
        contentFactory.set(value);
    }

    // ==================== Expanded ====================

    private final BooleanProperty expanded = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            updateExpandedPseudoClass();
            createContentIfNeeded();
        }

        @Override
        public Object getBean() {
            return RXTitledPane.this;
        }

        @Override
        public String getName() {
            return "expanded";
        }
    };

    /**
     * Whether the pane is expanded. This is the logical target; the body animates
     * toward it. Defaults to {@code false}. Inside an {@link RXAccordion} in
     * {@link RXAccordion.ExpandMode#SINGLE SINGLE} mode, expanding a pane collapses the
     * others. Bidirectional binding is supported; while the property is bound
     * one-way, user toggling and accordion coordination leave it untouched (the
     * accordion's single-expansion rule then holds only for the unbound panes).
     * {@code :expanded} / {@code :collapsed} reflect this logical value, not the
     * animation.
     *
     * @return the expanded property
     */
    public final BooleanProperty expandedProperty() {
        return expanded;
    }

    /**
     * Returns whether the pane is expanded.
     *
     * @return {@code true} if the pane is expanded
     */
    public final boolean isExpanded() {
        return expanded.get();
    }

    /**
     * Sets whether the pane is expanded.
     *
     * @param value {@code true} to expand the pane
     */
    public final void setExpanded(boolean value) {
        expanded.set(value);
    }

    // ==================== Toggle Trigger ====================

    private final ObjectProperty<ToggleTrigger> toggleTrigger =
            new StyleableObjectProperty<>(DEFAULT_TOGGLE_TRIGGER) {
                @Override
                protected void invalidated() {
                    updateTriggerPseudoClass();
                }

                @Override
                public CssMetaData<? extends Styleable, ToggleTrigger> getCssMetaData() {
                    return StyleableProperties.TOGGLE_TRIGGER;
                }

                @Override
                public Object getBean() {
                    return RXTitledPane.this;
                }

                @Override
                public String getName() {
                    return "toggleTrigger";
                }
            };

    /**
     * Which part of the header toggles the pane on a pointer click. With
     * {@link ToggleTrigger#INDICATOR INDICATOR} only the expand indicator does; when
     * the indicator is hidden ({@link IndicatorPosition#NONE NONE}) the whole header
     * toggles instead, so there is always a pointer entry. The keyboard toggles in
     * both modes. {@code null} is treated as {@link #DEFAULT_TOGGLE_TRIGGER}.
     *
     * @return the toggle trigger property
     */
    public final ObjectProperty<ToggleTrigger> toggleTriggerProperty() {
        return toggleTrigger;
    }

    /**
     * Returns the pointer toggle trigger.
     *
     * @return the toggle trigger, possibly {@code null}
     */
    public final ToggleTrigger getToggleTrigger() {
        return toggleTrigger.get();
    }

    /**
     * Sets the pointer toggle trigger.
     *
     * @param value the toggle trigger, or {@code null} for the default
     */
    public final void setToggleTrigger(ToggleTrigger value) {
        toggleTrigger.set(value);
    }

    // ==================== Indicator Position ====================

    private final ObjectProperty<IndicatorPosition> indicatorPosition =
            new StyleableObjectProperty<>(DEFAULT_INDICATOR_POSITION) {
                @Override
                protected void invalidated() {
                    updateTriggerPseudoClass();
                }

                @Override
                public CssMetaData<? extends Styleable, IndicatorPosition> getCssMetaData() {
                    return StyleableProperties.INDICATOR_POSITION;
                }

                @Override
                public Object getBean() {
                    return RXTitledPane.this;
                }

                @Override
                public String getName() {
                    return "indicatorPosition";
                }
            };

    /**
     * Where the expand indicator sits in the header: at the logical start, at the
     * logical end (after the {@link #headerTrailingProperty() headerTrailing} node), or
     * not shown. Start and end follow the node orientation. {@code null} is treated as {@link #DEFAULT_INDICATOR_POSITION}.
     *
     * @return the indicator position property
     */
    public final ObjectProperty<IndicatorPosition> indicatorPositionProperty() {
        return indicatorPosition;
    }

    /**
     * Returns the expand indicator position.
     *
     * @return the indicator position, possibly {@code null}
     */
    public final IndicatorPosition getIndicatorPosition() {
        return indicatorPosition.get();
    }

    /**
     * Sets the expand indicator position.
     *
     * @param value the indicator position, or {@code null} for the default
     */
    public final void setIndicatorPosition(IndicatorPosition value) {
        indicatorPosition.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXTitledPane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether expanding and collapsing animate. Turning it off while a transition runs
     * snaps the pane to its target state.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether expanding and collapsing animate.
     *
     * @return whether transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether expanding and collapsing animate.
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
                    return RXTitledPane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a full expand or collapse; a transition reversed midway takes the
     * proportional remainder. A {@code null}, non-positive, unknown or indefinite
     * value is not rejected; it disables animation like {@code animated=false}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration, possibly {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration; {@code null} or a non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator of expand and collapse transitions. {@code null} is treated as
     * {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable: there is no stable public
     * CSS converter for an arbitrary {@link Interpolator}.
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

    // ==================== Lazy Content ====================

    private void createContentIfNeeded() {
        Supplier<Node> factory = getContentFactory();
        if (creatingContent || factory == null || !isExpanded() || getContent() != null) {
            return;
        }
        creatingContent = true;
        try {
            Node created = factory.get();
            if (created != null && getContent() == null && !content.isBound()) {
                setContent(created);
            }
        } catch (RuntimeException ex) {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, ex);
        } finally {
            creatingContent = false;
        }
    }

    // ==================== Accordion Membership ====================

    RXAccordion getAccordion() {
        return accordion;
    }

    void setAccordion(RXAccordion value) {
        accordion = value;
        if (value == null) {
            setPositionInAccordion(false, false);
        }
    }

    void setPositionInAccordion(boolean first, boolean last) {
        pseudoClassStateChanged(FIRST_PSEUDO_CLASS, first);
        pseudoClassStateChanged(LAST_PSEUDO_CLASS, last);
    }

    // ==================== PseudoClass ====================

    private void updateExpandedPseudoClass() {
        boolean value = isExpanded();
        pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, value);
        pseudoClassStateChanged(COLLAPSED_PSEUDO_CLASS, !value);
    }

    private void updateTriggerPseudoClass() {
        pseudoClassStateChanged(INDICATOR_TRIGGER_PSEUDO_CLASS,
                getToggleTrigger() == ToggleTrigger.INDICATOR
                        && getIndicatorPosition() != IndicatorPosition.NONE);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXTitledPane, ToggleTrigger> TOGGLE_TRIGGER =
                new CssMetaData<>("-rx-toggle-trigger",
                        new EnumConverter<>(ToggleTrigger.class), DEFAULT_TOGGLE_TRIGGER) {
                    @Override
                    public boolean isSettable(RXTitledPane node) {
                        return !node.toggleTrigger.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ToggleTrigger> getStyleableProperty(RXTitledPane node) {
                        return (StyleableProperty<ToggleTrigger>) node.toggleTriggerProperty();
                    }
                };

        private static final CssMetaData<RXTitledPane, IndicatorPosition> INDICATOR_POSITION =
                new CssMetaData<>("-rx-indicator-position",
                        new EnumConverter<>(IndicatorPosition.class), DEFAULT_INDICATOR_POSITION) {
                    @Override
                    public boolean isSettable(RXTitledPane node) {
                        return !node.indicatorPosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<IndicatorPosition> getStyleableProperty(RXTitledPane node) {
                        return (StyleableProperty<IndicatorPosition>) node.indicatorPositionProperty();
                    }
                };

        private static final CssMetaData<RXTitledPane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXTitledPane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXTitledPane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXTitledPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXTitledPane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTitledPane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(TOGGLE_TRIGGER);
            styleables.add(INDICATOR_POSITION);
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
     * {@inheritDoc}
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * Which part of an {@link RXTitledPane} header toggles the pane on a pointer click.
     */
    public enum ToggleTrigger {

        /**
         * The whole header except the trailing node, including the indicator, toggles the pane.
         */
        HEADER,

        /**
         * Only the expand indicator toggles the pane; the title text is inert.
         */
        INDICATOR
    }

    /**
     * Position of the expand indicator in an {@link RXTitledPane} header.
     */
    public enum IndicatorPosition {

        /**
         * At the logical start of the title, before the text.
         */
        START,

        /**
         * At the logical end of the header, after the header-trailing node.
         */
        END,

        /**
         * Not shown.
         */
        NONE
    }
}
