package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXMath;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Column wrapper used by {@link RXRow}.
 *
 * <p>Despite the {@code Col} name, this is a grid <em>cell</em> laid out
 * horizontally within its {@code RXRow}, following the web {@code Row}/{@code Col}
 * convention (Element UI, Bootstrap, Ant Design). It is not a vertical stacking
 * box; for that, use {@link RXBox} in vertical orientation. As a
 * {@link StackPane} it lays its own children on top of each other and occupies a
 * {@link #spanProperty() span} of the row's columns.</p>
 *
 * <p>The base {@link #spanProperty() span} and {@link #offsetProperty() offset}
 * are used until a responsive {@link RXColSpec} for the active row breakpoint
 * overrides them. Responsive specs are mobile-first: a spec stays active for
 * larger breakpoints until another spec overrides the same field. Use
 * {@link RXColSpec#of(int, int)} with {@code offset=0} when a larger
 * breakpoint should clear an inherited offset.</p>
 *
 * <p>{@link #orderProperty() order} changes visual layout order only; it does
 * not change the parent row's child list order or the default focus traversal
 * order.</p>
 *
 * <p>{@link #hiddenProperty() hidden} is managed by {@link RXRow} on
 * this wrapper. Do not bind this column's {@code visible} or {@code managed}
 * properties for responsive visibility; put business visibility on content
 * inside the column or on an outer wrapper instead.</p>
 */
public class RXCol extends StackPane {

    /**
     * Default base span. Rows with fewer columns clamp this value to the row's
     * current column count during measurement and layout.
     */
    public static final int DEFAULT_SPAN = RXBreakpointProfile.ANT_DESIGN.getColumns();

    /**
     * Default left offset.
     */
    private static final int DEFAULT_OFFSET = 0;

    /**
     * Default visual order.
     */
    public static final int DEFAULT_ORDER = 0;

    /**
     * Default hidden state.
     */
    private static final boolean DEFAULT_HIDDEN = false;

    private static final String DEFAULT_STYLE_CLASS = "rx-col";

    // ==================== Constructors ====================

    /**
     * Creates an empty responsive column.
     */
    public RXCol() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a responsive column containing the given children.
     *
     * @param children the initial children
     */
    public RXCol(Node... children) {
        this();
        getChildren().addAll(children);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Span ====================

    private final IntegerProperty span = new StyleableIntegerProperty(DEFAULT_SPAN) {
        @Override
        protected void invalidated() {
            requestRowLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.SPAN;
        }

        @Override
        public Object getBean() {
            return RXCol.this;
        }

        @Override
        public String getName() {
            return "span";
        }
    };

    /**
     * Number of columns occupied by this column before responsive overrides.
     * A value of {@code 0} keeps the column in a deterministic zero-size
     * layout state. RXBreakpoint specs are mobile-first and override this base
     * value only for the fields they define.
     *
     * @return the span property
     */
    public final IntegerProperty spanProperty() {
        return span;
    }

    /**
     * Returns the base span.
     *
     * @return the base span
     */
    public final int getSpan() {
        return span.get();
    }

    /**
     * Sets the base span.
     *
     * @param value the base span
     */
    public final void setSpan(int value) {
        span.set(value);
    }

    // ==================== Offset ====================

    private final IntegerProperty offset = new StyleableIntegerProperty(DEFAULT_OFFSET) {
        @Override
        protected void invalidated() {
            requestRowLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OFFSET;
        }

        @Override
        public Object getBean() {
            return RXCol.this;
        }

        @Override
        public String getName() {
            return "offset";
        }
    };

    /**
     * Number of empty columns inserted before this column before responsive
     * overrides.
     *
     * @return the offset property
     */
    public final IntegerProperty offsetProperty() {
        return offset;
    }

    /**
     * Returns the base offset.
     *
     * @return the base offset
     */
    public final int getOffset() {
        return offset.get();
    }

    /**
     * Sets the base offset.
     *
     * @param value the base offset
     */
    public final void setOffset(int value) {
        offset.set(value);
    }

    // ==================== Order ====================

    private final IntegerProperty order = new SimpleIntegerProperty(this, "order", DEFAULT_ORDER) {
        @Override
        protected void invalidated() {
            requestRowLayout();
        }
    };

    /**
     * Visual layout order before responsive overrides. Lower values are laid
     * out first. This does not change child list order, so focus traversal may
     * remain different from visual order.
     *
     * @return the order property
     */
    public final IntegerProperty orderProperty() {
        return order;
    }

    /**
     * Returns the base visual order.
     *
     * @return the base order
     */
    public final int getOrder() {
        return order.get();
    }

    /**
     * Sets the base visual order.
     *
     * @param value the base order
     */
    public final void setOrder(int value) {
        order.set(value);
    }

    // ==================== Hidden ====================

    private final BooleanProperty hidden =
            new SimpleBooleanProperty(this, "hidden", DEFAULT_HIDDEN) {
                @Override
                protected void invalidated() {
                    requestRowLayout();
                }
            };

    /**
     * Responsive hidden state before breakpoint overrides. This property is
     * distinct from {@link #visibleProperty()} and {@link #managedProperty()}.
     * The parent {@link RXRow} applies the effective value to this
     * wrapper while preserving the state it changes.
     *
     * @return the hidden property
     */
    public final BooleanProperty hiddenProperty() {
        return hidden;
    }

    /**
     * Returns the base hidden state.
     *
     * @return whether this column is hidden
     */
    public final boolean isHidden() {
        return hidden.get();
    }

    /**
     * Sets the base hidden state.
     *
     * @param value whether this column is hidden
     */
    public final void setHidden(boolean value) {
        hidden.set(value);
    }

    // ==================== XS ====================

    private final ObjectProperty<RXColSpec> xs = createSpecProperty("xs");

    /**
     * Responsive spec for the {@code xs} breakpoint.
     *
     * @return the xs spec property
     */
    public final ObjectProperty<RXColSpec> xsProperty() {
        return xs;
    }

    /**
     * Returns the {@code xs} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getXs() {
        return xs.get();
    }

    /**
     * Sets the {@code xs} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setXs(RXColSpec value) {
        xs.set(value);
    }

    // ==================== SM ====================

    private final ObjectProperty<RXColSpec> sm = createSpecProperty("sm");

    /**
     * Responsive spec for the {@code sm} breakpoint.
     *
     * @return the sm spec property
     */
    public final ObjectProperty<RXColSpec> smProperty() {
        return sm;
    }

    /**
     * Returns the {@code sm} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getSm() {
        return sm.get();
    }

    /**
     * Sets the {@code sm} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setSm(RXColSpec value) {
        sm.set(value);
    }

    // ==================== MD ====================

    private final ObjectProperty<RXColSpec> md = createSpecProperty("md");

    /**
     * Responsive spec for the {@code md} breakpoint.
     *
     * @return the md spec property
     */
    public final ObjectProperty<RXColSpec> mdProperty() {
        return md;
    }

    /**
     * Returns the {@code md} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getMd() {
        return md.get();
    }

    /**
     * Sets the {@code md} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setMd(RXColSpec value) {
        md.set(value);
    }

    // ==================== LG ====================

    private final ObjectProperty<RXColSpec> lg = createSpecProperty("lg");

    /**
     * Responsive spec for the {@code lg} breakpoint.
     *
     * @return the lg spec property
     */
    public final ObjectProperty<RXColSpec> lgProperty() {
        return lg;
    }

    /**
     * Returns the {@code lg} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getLg() {
        return lg.get();
    }

    /**
     * Sets the {@code lg} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setLg(RXColSpec value) {
        lg.set(value);
    }

    // ==================== XL ====================

    private final ObjectProperty<RXColSpec> xl = createSpecProperty("xl");

    /**
     * Responsive spec for the {@code xl} breakpoint.
     *
     * @return the xl spec property
     */
    public final ObjectProperty<RXColSpec> xlProperty() {
        return xl;
    }

    /**
     * Returns the {@code xl} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getXl() {
        return xl.get();
    }

    /**
     * Sets the {@code xl} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setXl(RXColSpec value) {
        xl.set(value);
    }

    // ==================== XXL ====================

    private final ObjectProperty<RXColSpec> xxl = createSpecProperty("xxl");

    /**
     * Responsive spec for the {@code xxl} breakpoint. It is used when the
     * active row profile uses the {@link RXBreakpoint#XXL} tier.
     *
     * @return the xxl spec property
     */
    public final ObjectProperty<RXColSpec> xxlProperty() {
        return xxl;
    }

    /**
     * Returns the {@code xxl} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getXxl() {
        return xxl.get();
    }

    /**
     * Sets the {@code xxl} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setXxl(RXColSpec value) {
        xxl.set(value);
    }

    // ==================== XXXL ====================

    private final ObjectProperty<RXColSpec> xxxl = createSpecProperty("xxxl");

    /**
     * Responsive spec for the {@code xxxl} breakpoint. It is used when the
     * active row profile uses the {@link RXBreakpoint#XXXL} tier, such as the
     * default {@link RXBreakpointProfile#ANT_DESIGN} profile.
     *
     * @return the xxxl spec property
     */
    public final ObjectProperty<RXColSpec> xxxlProperty() {
        return xxxl;
    }

    /**
     * Returns the {@code xxxl} breakpoint spec.
     *
     * @return the spec, or {@code null} to inherit
     */
    public final RXColSpec getXxxl() {
        return xxxl.get();
    }

    /**
     * Sets the {@code xxxl} breakpoint spec.
     *
     * @param value the spec, or {@code null} to inherit
     */
    public final void setXxxl(RXColSpec value) {
        xxxl.set(value);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        List<Node> managed = getManagedChildren();
        Pos align = getAlignment();
        HPos alignHpos = align.getHpos();
        VPos alignVpos = align.getVpos();
        Insets insets = getInsets();
        double halfGutter = snapSpaceX(resolveEffectiveGutter() / 2.0);
        double left = insets.getLeft() + halfGutter;
        double right = insets.getRight() + halfGutter;
        double top = insets.getTop();
        double bottom = insets.getBottom();
        double contentWidth = Math.max(0.0, getWidth() - left - right);
        double contentHeight = Math.max(0.0, getHeight() - top - bottom);

        for (Node child : managed) {
            Pos childAlignment = StackPane.getAlignment(child);
            HPos hpos = childAlignment == null ? alignHpos : childAlignment.getHpos();
            VPos vpos = childAlignment == null ? alignVpos : childAlignment.getVpos();
            layoutInArea(child, left, top, contentWidth, contentHeight,
                    0.0, StackPane.getMargin(child), true, true, hpos, vpos);
        }
    }

    @Override
    protected double computeMinWidth(double height) {
        return computeResponsiveMinWidth(height, resolveEffectiveGutter());
    }

    @Override
    protected double computeMinHeight(double width) {
        return computeResponsiveMinHeight(width, resolveEffectiveGutter());
    }

    @Override
    protected double computePrefWidth(double height) {
        return computeResponsivePrefWidth(height, resolveEffectiveGutter());
    }

    @Override
    protected double computePrefHeight(double width) {
        return computeResponsivePrefHeight(width, resolveEffectiveGutter());
    }

    double computeResponsiveMinWidth(double height, double gutter) {
        Insets insets = getInsets();
        double contentHeight = height == -1.0 ? -1.0
                : Math.max(0.0, height - insets.getTop() - insets.getBottom());
        double maxWidth = 0.0;
        for (Node child : getManagedChildren()) {
            Insets margin = marginOrEmpty(child);
            double childHeight = contentHeight == -1.0 ? -1.0
                    : Math.max(0.0, contentHeight - margin.getTop() - margin.getBottom());
            double childWidth = snapSizeX(child.minWidth(childHeight));
            maxWidth = Math.max(maxWidth, snapSpaceX(margin.getLeft())
                    + childWidth + snapSpaceX(margin.getRight()));
        }
        return insets.getLeft() + snapSizeX(maxWidth)
                + snapSpaceX(normalizeGutter(gutter)) + insets.getRight();
    }

    double computeResponsiveMinHeight(double width, double gutter) {
        Insets insets = getInsets();
        double contentWidth = width == -1.0 ? -1.0
                : Math.max(0.0, width - insets.getLeft() - insets.getRight()
                - snapSpaceX(normalizeGutter(gutter)));
        double maxHeight = 0.0;
        for (Node child : getManagedChildren()) {
            Insets margin = marginOrEmpty(child);
            double childWidth = contentWidth == -1.0 ? -1.0
                    : Math.max(0.0, contentWidth - margin.getLeft() - margin.getRight());
            double childHeight = snapSizeY(child.minHeight(childWidth));
            maxHeight = Math.max(maxHeight, snapSpaceY(margin.getTop())
                    + childHeight + snapSpaceY(margin.getBottom()));
        }
        return insets.getTop() + snapSizeY(maxHeight) + insets.getBottom();
    }

    double computeResponsivePrefWidth(double height, double gutter) {
        Insets insets = getInsets();
        double contentHeight = height == -1.0 ? -1.0
                : Math.max(0.0, height - insets.getTop() - insets.getBottom());
        double maxWidth = 0.0;
        for (Node child : getManagedChildren()) {
            Insets margin = marginOrEmpty(child);
            double childHeight = contentHeight == -1.0 ? -1.0
                    : Math.max(0.0, contentHeight - margin.getTop() - margin.getBottom());
            double childWidth = boundedPrefWidth(child, childHeight);
            maxWidth = Math.max(maxWidth, snapSpaceX(margin.getLeft())
                    + childWidth + snapSpaceX(margin.getRight()));
        }
        return insets.getLeft() + snapSizeX(maxWidth)
                + snapSpaceX(normalizeGutter(gutter)) + insets.getRight();
    }

    double computeResponsivePrefHeight(double width, double gutter) {
        Insets insets = getInsets();
        double contentWidth = width == -1.0 ? -1.0
                : Math.max(0.0, width - insets.getLeft() - insets.getRight()
                - snapSpaceX(normalizeGutter(gutter)));
        double maxHeight = 0.0;
        for (Node child : getManagedChildren()) {
            Insets margin = marginOrEmpty(child);
            double childWidth = contentWidth == -1.0 ? -1.0
                    : Math.max(0.0, contentWidth - margin.getLeft() - margin.getRight());
            double childHeight = boundedPrefHeight(child, childWidth);
            maxHeight = Math.max(maxHeight, snapSpaceY(margin.getTop())
                    + childHeight + snapSpaceY(margin.getBottom()));
        }
        return insets.getTop() + snapSizeY(maxHeight) + insets.getBottom();
    }

    RXColSpec getSpec(RXBreakpoint breakpoint) {
        return switch (breakpoint) {
            case XS -> getXs();
            case SM -> getSm();
            case MD -> getMd();
            case LG -> getLg();
            case XL -> getXl();
            case XXL -> getXxl();
            case XXXL -> getXxxl();
        };
    }

    private ObjectProperty<RXColSpec> createSpecProperty(String name) {
        return new SimpleObjectProperty<>(this, name) {
            @Override
            protected void invalidated() {
                requestRowLayout();
            }
        };
    }

    private void requestRowLayout() {
        requestLayout();
        Parent parent = getParent();
        if (parent != null) {
            parent.requestLayout();
        }
    }

    private double resolveEffectiveGutter() {
        Parent parent = getParent();
        if (parent instanceof RXRow row) {
            return normalizeGutter(row.getGutter());
        }
        return 0.0;
    }

    private double boundedPrefWidth(Node child, double height) {
        double prefWidth = child.prefWidth(height);
        if (!child.isResizable()) {
            return snapSizeX(prefWidth);
        }
        return snapSizeX(RXMath.clamp(prefWidth, child.minWidth(height), child.maxWidth(height)));
    }

    private double boundedPrefHeight(Node child, double width) {
        double prefHeight = child.prefHeight(width);
        if (!child.isResizable()) {
            return snapSizeY(prefHeight);
        }
        return snapSizeY(RXMath.clamp(prefHeight, child.minHeight(width), child.maxHeight(width)));
    }

    private static Insets marginOrEmpty(Node child) {
        Insets margin = StackPane.getMargin(child);
        return margin == null ? Insets.EMPTY : margin;
    }

    private static double normalizeGutter(double value) {
        return Double.isFinite(value) ? value : RXRow.DEFAULT_GUTTER;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {
        private static final CssMetaData<RXCol, Number> SPAN =
                new CssMetaData<>("-rx-span", SizeConverter.getInstance(), DEFAULT_SPAN) {
                    @Override
                    public boolean isSettable(RXCol col) {
                        return !col.span.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCol col) {
                        return (StyleableProperty<Number>) col.spanProperty();
                    }
                };

        private static final CssMetaData<RXCol, Number> OFFSET =
                new CssMetaData<>("-rx-offset", SizeConverter.getInstance(), DEFAULT_OFFSET) {
                    @Override
                    public boolean isSettable(RXCol col) {
                        return !col.offset.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCol col) {
                        return (StyleableProperty<Number>) col.offsetProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(SPAN);
            styleables.add(OFFSET);
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
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
