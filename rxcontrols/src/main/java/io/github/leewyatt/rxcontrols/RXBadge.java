package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXBadgeSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small indicator, showing a count, a short text or a plain dot, attached to a
 * corner of a content node.
 *
 * <p><b>What is shown.</b> The first matching rule wins:</p>
 * <ol>
 *   <li>{@link #indicatorVisibleProperty() indicatorVisible} is {@code false}:
 *       nothing;</li>
 *   <li>the {@link #variantProperty() variant} is {@link Variant#DOT}: a dot,
 *       whatever the count and text are;</li>
 *   <li>the {@link #textProperty() text} is not empty: that text;</li>
 *   <li>the {@link #countProperty() count} exceeds
 *       {@link #maxCountProperty() maxCount}: the maximum followed by {@code +},
 *       such as {@code 99+};</li>
 *   <li>the count is positive, or is zero while
 *       {@link #showZeroProperty() showZero} is on: the count;</li>
 *   <li>otherwise (zero, or a negative count): nothing.</li>
 * </ol>
 * <p>The outcome is published by {@link #indicatorShowingProperty()} and
 * {@link #indicatorTextProperty()}.</p>
 *
 * <p><b>Layout.</b> The badge is a transparent wrapper: its minimum, preferred
 * and maximum sizes, content bias and baseline are those of the content plus the
 * badge's own padding. The indicator takes no layout space and is centered on a
 * corner of the content, so half of it is drawn outside the badge's bounds; a
 * clipping ancestor such as a scroll pane cuts that part off. Padding on the
 * badge reserves room for it, since the indicator follows the content and not the
 * badge's outer edge.</p>
 *
 * <p>Without content the badge is the indicator alone, sized to it and empty
 * when nothing is shown; position, overlap and offsets do not apply.</p>
 *
 * <p>The indicator never receives mouse events, and the badge takes no focus;
 * events on the content reach it and its ancestors as if it were not wrapped.</p>
 *
 * <pre>{@code
 * RXBadge badge = new RXBadge(inboxButton);
 * badge.countProperty().bind(unreadCount);
 *
 * // A dot on the edge of a round avatar
 * RXBadge online = new RXBadge(avatar);
 * online.setVariant(RXBadge.Variant.DOT);
 * online.setOverlap(RXBadge.Overlap.CIRCULAR);
 * online.setPosition(RXBadge.Position.BOTTOM_END);
 * online.getStyleClass().add("success");
 *
 * // A status dot beside a text
 * RXBadge dot = new RXBadge();
 * dot.setVariant(RXBadge.Variant.DOT);
 * Label status = new Label("Online", dot);
 * }</pre>
 *
 * <p><b>CSS.</b> The root style class is {@code rx-badge}; the indicator is
 * {@code .rx-badge > .indicator}, the pulse ring {@code .rx-badge > .pulse} and
 * the pane holding the content {@code .rx-badge > .content-slot}. The colors
 * come from {@code -rx-badge-fill}, {@code -rx-badge-on-fill} and
 * {@code -rx-badge-ring} (an outline around the indicator, transparent unless
 * set). The style classes {@code primary}, {@code success}, {@code warning},
 * {@code danger} and {@code info} pick the fill; danger is the default. The
 * pseudo-classes are {@code :dot} and {@code :standalone}; the indicator has a
 * {@code :dot} of its own, which it keeps while a dot scales out. The indicator
 * is sized in {@code em}, so {@code -fx-font-size} scales it.</p>
 *
 * <p><b>Accessibility.</b> A bare number does not say what is counted. Describe
 * it on the content, for example
 * {@code button.setAccessibleText("Inbox, 12 unread")}.</p>
 */
@DefaultProperty("content")
public class RXBadge extends Control {

    // ==================== Constants ====================

    /**
     * Default maximum count, also used while {@code maxCount} is below one.
     */
    public static final int DEFAULT_MAX_COUNT = 99;

    /**
     * Default variant, also the {@code null} fallback.
     */
    public static final Variant DEFAULT_VARIANT = Variant.STANDARD;

    /**
     * Default position, also the {@code null} fallback.
     */
    public static final Position DEFAULT_POSITION = Position.TOP_END;

    /**
     * Default overlap, also the {@code null} fallback.
     */
    public static final Overlap DEFAULT_OVERLAP = Overlap.RECTANGULAR;

    private static final String DEFAULT_STYLE_CLASS = "rx-badge";
    private static final boolean DEFAULT_ANIMATED = true;

    private static final PseudoClass DOT_PSEUDO_CLASS = PseudoClass.getPseudoClass("dot");
    private static final PseudoClass STANDALONE_PSEUDO_CLASS = PseudoClass.getPseudoClass("standalone");

    // ==================== Constructors ====================

    /**
     * Creates a badge without content.
     */
    public RXBadge() {
        this(null);
    }

    /**
     * Creates a badge attached to the given content.
     *
     * @param content the content node, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXBadge(@NamedArg("content") Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // A null style origin leaves -fx-focus-traversable settable from CSS.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        // Only the content is picked: the empty box around it, and the area the
        // indicator overflows into, stay transparent to the mouse.
        setPickOnBounds(false);
        setContent(content);
        updateIndicator();
        pseudoClassStateChanged(STANDALONE_PSEUDO_CLASS, content == null);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXBadgeSkin(this);
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
    public Orientation getContentBias() {
        Node node = getContent();
        return node == null || !node.isManaged() ? null : node.getContentBias();
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content") {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(STANDALONE_PSEUDO_CLASS, get() == null);
        }
    };

    /**
     * The node the indicator is attached to. With {@code null} the badge shows
     * the indicator alone.
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
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Count ====================

    private final IntegerProperty count = new SimpleIntegerProperty(this, "count") {
        @Override
        protected void invalidated() {
            updateIndicator();
        }
    };

    /**
     * The number to show. Zero is hidden unless
     * {@link #showZeroProperty() showZero} is on; a negative count is always
     * hidden. Initial value is {@code 0}.
     *
     * @return the count property
     */
    public final IntegerProperty countProperty() {
        return count;
    }

    /**
     * Returns the count.
     *
     * @return the count
     */
    public final int getCount() {
        return count.get();
    }

    /**
     * Sets the count.
     *
     * @param value the count
     */
    public final void setCount(int value) {
        count.set(value);
    }

    // ==================== Max Count ====================

    private final IntegerProperty maxCount = new SimpleIntegerProperty(this, "maxCount", DEFAULT_MAX_COUNT) {
        @Override
        protected void invalidated() {
            updateIndicator();
        }
    };

    /**
     * The largest count shown as a number; a larger count shows this value
     * followed by {@code +}. It caps the display only, never the count itself.
     * Initial value is {@link #DEFAULT_MAX_COUNT}, which also applies while the
     * value is below one.
     *
     * @return the maximum count property
     */
    public final IntegerProperty maxCountProperty() {
        return maxCount;
    }

    /**
     * Returns the maximum count.
     *
     * @return the maximum count
     */
    public final int getMaxCount() {
        return maxCount.get();
    }

    /**
     * Sets the maximum count.
     *
     * @param value the maximum count
     */
    public final void setMaxCount(int value) {
        maxCount.set(value);
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text") {
        @Override
        protected void invalidated() {
            updateIndicator();
        }
    };

    /**
     * A short text such as {@code "NEW"}, shown in place of the count while it
     * is not empty.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the text.
     *
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the text.
     *
     * @param value the text, or {@code null} to show the count
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Show Zero ====================

    private final BooleanProperty showZero = new SimpleBooleanProperty(this, "showZero") {
        @Override
        protected void invalidated() {
            updateIndicator();
        }
    };

    /**
     * Whether a count of zero is shown. Initial value is {@code false}.
     *
     * @return the show-zero property
     */
    public final BooleanProperty showZeroProperty() {
        return showZero;
    }

    /**
     * Returns whether a count of zero is shown.
     *
     * @return whether a count of zero is shown
     */
    public final boolean isShowZero() {
        return showZero.get();
    }

    /**
     * Sets whether a count of zero is shown.
     *
     * @param value whether a count of zero is shown
     */
    public final void setShowZero(boolean value) {
        showZero.set(value);
    }

    // ==================== Indicator Visible ====================

    private final BooleanProperty indicatorVisible = new SimpleBooleanProperty(this, "indicatorVisible", true) {
        @Override
        protected void invalidated() {
            updateIndicator();
        }
    };

    /**
     * Whether the indicator may be shown. {@code false} hides it whatever the
     * count, text and variant are; the content stays visible. Initial value is
     * {@code true}.
     *
     * @return the indicator-visible property
     */
    public final BooleanProperty indicatorVisibleProperty() {
        return indicatorVisible;
    }

    /**
     * Returns whether the indicator may be shown.
     *
     * @return whether the indicator may be shown
     */
    public final boolean isIndicatorVisible() {
        return indicatorVisible.get();
    }

    /**
     * Sets whether the indicator may be shown.
     *
     * @param value whether the indicator may be shown
     */
    public final void setIndicatorVisible(boolean value) {
        indicatorVisible.set(value);
    }

    // ==================== Variant ====================

    private final ObjectProperty<Variant> variant = new StyleableObjectProperty<>(DEFAULT_VARIANT) {
        @Override
        protected void invalidated() {
            updateIndicator();
        }

        @Override
        public CssMetaData<? extends Styleable, Variant> getCssMetaData() {
            return StyleableProperties.VARIANT;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "variant";
        }
    };

    /**
     * Whether the indicator shows the count or text, or is a plain dot. Initial
     * value is {@link #DEFAULT_VARIANT}; {@code null} is treated as that default.
     *
     * @return the variant property
     */
    public final ObjectProperty<Variant> variantProperty() {
        return variant;
    }

    /**
     * Returns the variant.
     *
     * @return the variant, possibly {@code null}
     */
    public final Variant getVariant() {
        return variant.get();
    }

    /**
     * Sets the variant.
     *
     * @param value the variant, or {@code null} for the default
     */
    public final void setVariant(Variant value) {
        variant.set(value);
    }

    // ==================== Position ====================

    private final ObjectProperty<Position> position = new StyleableObjectProperty<>(DEFAULT_POSITION) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Position> getCssMetaData() {
            return StyleableProperties.POSITION;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "position";
        }
    };

    /**
     * The corner of the content the indicator is centered on. Initial value is
     * {@link #DEFAULT_POSITION}; {@code null} is treated as that default.
     *
     * @return the position property
     */
    public final ObjectProperty<Position> positionProperty() {
        return position;
    }

    /**
     * Returns the position.
     *
     * @return the position, possibly {@code null}
     */
    public final Position getPosition() {
        return position.get();
    }

    /**
     * Sets the position.
     *
     * @param value the position, or {@code null} for the default
     */
    public final void setPosition(Position value) {
        position.set(value);
    }

    // ==================== Overlap ====================

    private final ObjectProperty<Overlap> overlap = new StyleableObjectProperty<>(DEFAULT_OVERLAP) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Overlap> getCssMetaData() {
            return StyleableProperties.OVERLAP;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "overlap";
        }
    };

    /**
     * The outline of the content the indicator sits on: the corner of its
     * bounding box, or the edge of the circle or ellipse inscribed in it.
     * Initial value is {@link #DEFAULT_OVERLAP}; {@code null} is treated as that
     * default.
     *
     * @return the overlap property
     */
    public final ObjectProperty<Overlap> overlapProperty() {
        return overlap;
    }

    /**
     * Returns the overlap.
     *
     * @return the overlap, possibly {@code null}
     */
    public final Overlap getOverlap() {
        return overlap.get();
    }

    /**
     * Sets the overlap.
     *
     * @param value the overlap, or {@code null} for the default
     */
    public final void setOverlap(Overlap value) {
        overlap.set(value);
    }

    // ==================== Offset X ====================

    private final DoubleProperty offsetX = new StyleableDoubleProperty() {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OFFSET_X;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "offsetX";
        }
    };

    /**
     * Horizontal shift of the indicator from its corner. A positive value moves
     * it toward the center of the content, a negative one away from it, whatever
     * the position is. Initial value is {@code 0}; a non-finite value counts as
     * zero.
     *
     * @return the horizontal offset property
     */
    public final DoubleProperty offsetXProperty() {
        return offsetX;
    }

    /**
     * Returns the horizontal offset.
     *
     * @return the horizontal offset
     */
    public final double getOffsetX() {
        return offsetX.get();
    }

    /**
     * Sets the horizontal offset.
     *
     * @param value the horizontal offset
     */
    public final void setOffsetX(double value) {
        offsetX.set(value);
    }

    // ==================== Offset Y ====================

    private final DoubleProperty offsetY = new StyleableDoubleProperty() {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OFFSET_Y;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "offsetY";
        }
    };

    /**
     * Vertical shift of the indicator from its corner. A positive value moves it
     * toward the center of the content, a negative one away from it, whatever
     * the position is. Initial value is {@code 0}; a non-finite value counts as
     * zero.
     *
     * @return the vertical offset property
     */
    public final DoubleProperty offsetYProperty() {
        return offsetY;
    }

    /**
     * Returns the vertical offset.
     *
     * @return the vertical offset
     */
    public final double getOffsetY() {
        return offsetY.get();
    }

    /**
     * Sets the vertical offset.
     *
     * @param value the vertical offset
     */
    public final void setOffsetY(double value) {
        offsetY.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXBadge.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether the indicator scales in and out as it appears and disappears.
     * When {@code false} it shows and hides at once, which suits a badge in a
     * reused list cell, where every new item would otherwise replay the
     * animation. Initial value is {@code true}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether showing and hiding the indicator animate.
     *
     * @return whether showing and hiding the indicator animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether showing and hiding the indicator animate.
     *
     * @param value whether showing and hiding the indicator animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Pulsing ====================

    private final BooleanProperty pulsing = new SimpleBooleanProperty(this, "pulsing");

    /**
     * Whether a ring keeps expanding and fading from the indicator while it is
     * shown, marking something live or in progress. Initial value is
     * {@code false}.
     *
     * @return the pulsing property
     */
    public final BooleanProperty pulsingProperty() {
        return pulsing;
    }

    /**
     * Returns whether the indicator pulses.
     *
     * @return whether the indicator pulses
     */
    public final boolean isPulsing() {
        return pulsing.get();
    }

    /**
     * Sets whether the indicator pulses.
     *
     * @param value whether the indicator pulses
     */
    public final void setPulsing(boolean value) {
        pulsing.set(value);
    }

    // ==================== Indicator Showing ====================

    private final ReadOnlyBooleanWrapper indicatorShowing = new ReadOnlyBooleanWrapper(this, "indicatorShowing");

    /**
     * Whether the indicator is shown, as decided by the rules in the class
     * description.
     *
     * @return the read-only indicator-showing property
     */
    public final ReadOnlyBooleanProperty indicatorShowingProperty() {
        return indicatorShowing.getReadOnlyProperty();
    }

    /**
     * Returns whether the indicator is shown.
     *
     * @return whether the indicator is shown
     */
    public final boolean isIndicatorShowing() {
        return indicatorShowing.get();
    }

    // ==================== Indicator Text ====================

    private final ReadOnlyStringWrapper indicatorText = new ReadOnlyStringWrapper(this, "indicatorText", "");

    /**
     * The text the indicator shows: the text, the count, or the capped count
     * such as {@code "99+"}. Empty for a dot and while the indicator is hidden.
     *
     * @return the read-only indicator text property
     */
    public final ReadOnlyStringProperty indicatorTextProperty() {
        return indicatorText.getReadOnlyProperty();
    }

    /**
     * Returns the text the indicator shows.
     *
     * @return the indicator text, never {@code null}
     */
    public final String getIndicatorText() {
        return indicatorText.get();
    }

    // ==================== Indicator State ====================

    private void updateIndicator() {
        boolean dot = getVariant() == Variant.DOT;
        String shown = isIndicatorVisible() ? resolveText(dot) : null;
        pseudoClassStateChanged(DOT_PSEUDO_CLASS, dot);
        // Hiding clears the text last and showing sets it first, so the text is
        // never empty while a count or text indicator is reported as showing.
        if (shown == null) {
            indicatorShowing.set(false);
            indicatorText.set("");
        } else {
            indicatorText.set(shown);
            indicatorShowing.set(true);
        }
    }

    // The text to show, empty for a dot, or null when the indicator is hidden.
    private String resolveText(boolean dot) {
        if (dot) {
            return "";
        }
        String value = getText();
        if (value != null && !value.isEmpty()) {
            return value;
        }
        int number = getCount();
        if (number < 0 || number == 0 && !isShowZero()) {
            return null;
        }
        int max = getMaxCount() < 1 ? DEFAULT_MAX_COUNT : getMaxCount();
        return number > max ? max + "+" : String.valueOf(number);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXBadge, Variant> VARIANT =
                new CssMetaData<>("-rx-variant", new EnumConverter<>(Variant.class), DEFAULT_VARIANT) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.variant.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Variant> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Variant>) control.variantProperty();
                    }
                };

        private static final CssMetaData<RXBadge, Position> POSITION =
                new CssMetaData<>("-rx-position", new EnumConverter<>(Position.class), DEFAULT_POSITION) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.position.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Position> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Position>) control.positionProperty();
                    }
                };

        private static final CssMetaData<RXBadge, Overlap> OVERLAP =
                new CssMetaData<>("-rx-overlap", new EnumConverter<>(Overlap.class), DEFAULT_OVERLAP) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.overlap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Overlap> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Overlap>) control.overlapProperty();
                    }
                };

        private static final CssMetaData<RXBadge, Number> OFFSET_X =
                new CssMetaData<>("-rx-offset-x", SizeConverter.getInstance(), 0.0) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.offsetX.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Number>) control.offsetXProperty();
                    }
                };

        private static final CssMetaData<RXBadge, Number> OFFSET_Y =
                new CssMetaData<>("-rx-offset-y", SizeConverter.getInstance(), 0.0) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.offsetY.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Number>) control.offsetYProperty();
                    }
                };

        private static final CssMetaData<RXBadge, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXBadge control) {
                        return !control.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXBadge control) {
                        return (StyleableProperty<Boolean>) control.animatedProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(VARIANT);
            styleables.add(POSITION);
            styleables.add(OVERLAP);
            styleables.add(OFFSET_X);
            styleables.add(OFFSET_Y);
            styleables.add(ANIMATED);
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

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * What the indicator looks like.
     */
    public enum Variant {
        /**
         * A pill holding the count or the text.
         */
        STANDARD,
        /**
         * A small dot without text.
         */
        DOT
    }

    /**
     * The corner of the content the indicator is attached to. Start and end
     * follow the node orientation: start is the left side in a left-to-right
     * layout.
     */
    public enum Position {
        /**
         * Top corner on the start side.
         */
        TOP_START,
        /**
         * Top corner on the end side.
         */
        TOP_END,
        /**
         * Bottom corner on the start side.
         */
        BOTTOM_START,
        /**
         * Bottom corner on the end side.
         */
        BOTTOM_END;

        /**
         * Returns whether this is one of the top corners.
         *
         * @return {@code true} for {@code TOP_START} and {@code TOP_END}
         */
        public boolean isTop() {
            return this == TOP_START || this == TOP_END;
        }

        /**
         * Returns whether this is one of the corners on the end side.
         *
         * @return {@code true} for {@code TOP_END} and {@code BOTTOM_END}
         */
        public boolean isEnd() {
            return this == TOP_END || this == BOTTOM_END;
        }
    }

    /**
     * The outline of the content the indicator is placed on.
     */
    public enum Overlap {
        /**
         * The corner of the content's bounding box.
         */
        RECTANGULAR,
        /**
         * The edge of the circle or ellipse inscribed in the content's bounds.
         */
        CIRCULAR
    }
}
