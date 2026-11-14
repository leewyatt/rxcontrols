package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXRibbonBadgeSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
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
 * A text ribbon laid over the top of a content node such as a card. The ribbon
 * reaches past one side of the content and folds back behind it.
 *
 * <p><b>Layout.</b> The ribbon badge is a transparent wrapper: its minimum,
 * preferred and maximum sizes, content bias and baseline are those of the content
 * plus its own padding. The ribbon takes no layout space; the part reaching past
 * the content by {@link #overhangProperty() overhang} is drawn outside the
 * bounds, where a clipping ancestor such as a scroll pane cuts it off. Padding on
 * the ribbon badge reserves room for it, since the ribbon follows the content.
 * A ribbon wider than the content is shortened with an ellipsis.</p>
 *
 * <p>The ribbon is hidden while the {@link #textProperty() text} is empty or
 * there is no content. It
 * never receives mouse events; events on the content reach it and its ancestors
 * as if it were not wrapped.</p>
 *
 * <pre>{@code
 * RXRibbonBadge ribbon = new RXRibbonBadge(card, "NEW");
 * ribbon.setPlacement(RXRibbonBadge.Placement.START);
 * ribbon.getStyleClass().add("success");
 * }</pre>
 *
 * <p><b>CSS.</b> The root style class is {@code rx-ribbon-badge}; the ribbon is
 * {@code .rx-ribbon-badge > .ribbon} and the folded corner
 * {@code .rx-ribbon-badge > .fold}. The colors come from {@code -rx-ribbon-fill}
 * and {@code -rx-ribbon-on-fill}; the fold is a darker shade of the fill. The
 * style classes {@code success}, {@code warning}, {@code danger} and {@code info}
 * switch the fill from the default primary color. The pseudo-classes
 * {@code :start} and {@code :end} follow the placement.</p>
 */
@DefaultProperty("content")
public class RXRibbonBadge extends Control {

    // ==================== Constants ====================

    /**
     * Default placement, also the {@code null} fallback.
     */
    public static final Placement DEFAULT_PLACEMENT = Placement.END;

    /**
     * Default distance between the top of the content and the ribbon.
     */
    public static final double DEFAULT_RIBBON_OFFSET = 8.0;

    /**
     * Default reach of the ribbon past the side of the content.
     */
    public static final double DEFAULT_OVERHANG = 8.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-ribbon-badge";

    private static final PseudoClass START_PSEUDO_CLASS = PseudoClass.getPseudoClass("start");
    private static final PseudoClass END_PSEUDO_CLASS = PseudoClass.getPseudoClass("end");

    // ==================== Constructors ====================

    /**
     * Creates a ribbon badge without content or text.
     */
    public RXRibbonBadge() {
        this(null, null);
    }

    /**
     * Creates a ribbon badge over the given content, without text.
     *
     * @param content the content node, or {@code null}
     */
    public RXRibbonBadge(Node content) {
        this(content, null);
    }

    /**
     * Creates a ribbon badge over the given content.
     *
     * @param content the content node, or {@code null}
     * @param text    the ribbon text, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXRibbonBadge(@NamedArg("content") Node content, @NamedArg("text") String text) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // A null style origin leaves -fx-focus-traversable settable from CSS.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        // Only the content is picked: the empty box around it, and the area the
        // ribbon overflows into, stay transparent to the mouse.
        setPickOnBounds(false);
        setContent(content);
        setText(text);
        updatePlacementPseudoClass();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXRibbonBadgeSkin(this);
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

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * The node the ribbon is laid over.
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

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * The text on the ribbon. The ribbon is hidden while it is {@code null} or
     * empty.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the ribbon text.
     *
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the ribbon text.
     *
     * @param value the text, or {@code null} to hide the ribbon
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Placement ====================

    private final ObjectProperty<Placement> placement = new StyleableObjectProperty<>(DEFAULT_PLACEMENT) {
        @Override
        protected void invalidated() {
            updatePlacementPseudoClass();
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Placement> getCssMetaData() {
            return StyleableProperties.PLACEMENT;
        }

        @Override
        public Object getBean() {
            return RXRibbonBadge.this;
        }

        @Override
        public String getName() {
            return "placement";
        }
    };

    /**
     * The side of the content the ribbon reaches past. Initial value is
     * {@link #DEFAULT_PLACEMENT}; {@code null} is treated as that default.
     *
     * @return the placement property
     */
    public final ObjectProperty<Placement> placementProperty() {
        return placement;
    }

    /**
     * Returns the placement.
     *
     * @return the placement, possibly {@code null}
     */
    public final Placement getPlacement() {
        return placement.get();
    }

    /**
     * Sets the placement.
     *
     * @param value the placement, or {@code null} for the default
     */
    public final void setPlacement(Placement value) {
        placement.set(value);
    }

    private void updatePlacementPseudoClass() {
        boolean start = getPlacement() == Placement.START;
        pseudoClassStateChanged(START_PSEUDO_CLASS, start);
        pseudoClassStateChanged(END_PSEUDO_CLASS, !start);
    }

    // ==================== Ribbon Offset ====================

    private final DoubleProperty ribbonOffset = new StyleableDoubleProperty(DEFAULT_RIBBON_OFFSET) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.RIBBON_OFFSET;
        }

        @Override
        public Object getBean() {
            return RXRibbonBadge.this;
        }

        @Override
        public String getName() {
            return "ribbonOffset";
        }
    };

    /**
     * Distance between the top of the content and the top of the ribbon. Initial
     * value is {@link #DEFAULT_RIBBON_OFFSET}; a non-finite value counts as
     * zero.
     *
     * @return the ribbon offset property
     */
    public final DoubleProperty ribbonOffsetProperty() {
        return ribbonOffset;
    }

    /**
     * Returns the ribbon offset.
     *
     * @return the ribbon offset
     */
    public final double getRibbonOffset() {
        return ribbonOffset.get();
    }

    /**
     * Sets the ribbon offset.
     *
     * @param value the ribbon offset
     */
    public final void setRibbonOffset(double value) {
        ribbonOffset.set(value);
    }

    // ==================== Overhang ====================

    private final DoubleProperty overhang = new StyleableDoubleProperty(DEFAULT_OVERHANG) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OVERHANG;
        }

        @Override
        public Object getBean() {
            return RXRibbonBadge.this;
        }

        @Override
        public String getName() {
            return "overhang";
        }
    };

    /**
     * How far the ribbon reaches past the side of the content, which is also the
     * width of the folded corner under it. With zero the ribbon ends flush with
     * the content and has no fold. Initial value is {@link #DEFAULT_OVERHANG}; a
     * negative or non-finite value counts as zero, and the value is rounded to
     * whole pixels.
     *
     * @return the overhang property
     */
    public final DoubleProperty overhangProperty() {
        return overhang;
    }

    /**
     * Returns the overhang.
     *
     * @return the overhang
     */
    public final double getOverhang() {
        return overhang.get();
    }

    /**
     * Sets the overhang.
     *
     * @param value the overhang
     */
    public final void setOverhang(double value) {
        overhang.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXRibbonBadge, Placement> PLACEMENT =
                new CssMetaData<>("-rx-placement", new EnumConverter<>(Placement.class), DEFAULT_PLACEMENT) {
                    @Override
                    public boolean isSettable(RXRibbonBadge control) {
                        return !control.placement.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Placement> getStyleableProperty(RXRibbonBadge control) {
                        return (StyleableProperty<Placement>) control.placementProperty();
                    }
                };

        private static final CssMetaData<RXRibbonBadge, Number> RIBBON_OFFSET =
                new CssMetaData<>("-rx-ribbon-offset", SizeConverter.getInstance(), DEFAULT_RIBBON_OFFSET) {
                    @Override
                    public boolean isSettable(RXRibbonBadge control) {
                        return !control.ribbonOffset.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRibbonBadge control) {
                        return (StyleableProperty<Number>) control.ribbonOffsetProperty();
                    }
                };

        private static final CssMetaData<RXRibbonBadge, Number> OVERHANG =
                new CssMetaData<>("-rx-overhang", SizeConverter.getInstance(), DEFAULT_OVERHANG) {
                    @Override
                    public boolean isSettable(RXRibbonBadge control) {
                        return !control.overhang.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRibbonBadge control) {
                        return (StyleableProperty<Number>) control.overhangProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(PLACEMENT);
            styleables.add(RIBBON_OFFSET);
            styleables.add(OVERHANG);
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
     * The side of the content the ribbon reaches past. Start and end follow the
     * node orientation: start is the left side in a left-to-right layout.
     */
    public enum Placement {
        /**
         * The ribbon reaches past the start side.
         */
        START,
        /**
         * The ribbon reaches past the end side.
         */
        END
    }
}
