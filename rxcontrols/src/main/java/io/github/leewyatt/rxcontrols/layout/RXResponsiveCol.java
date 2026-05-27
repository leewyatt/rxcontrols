package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Column wrapper used by {@link RXResponsiveRow}.
 *
 * <p>The base {@link #spanProperty() span} and {@link #offsetProperty() offset}
 * are used until a responsive {@link RXColSpec} for the active row breakpoint
 * overrides them. Responsive specs are mobile-first: a spec stays active for
 * larger breakpoints until another spec overrides the same field. Use
 * {@link RXColSpec#of(int, int)} with {@code offset=0} when a larger
 * breakpoint should clear an inherited offset.</p>
 */
public class RXResponsiveCol extends StackPane {

    /**
     * Default base span. Rows with fewer columns clamp this value to the row's
     * current column count during measurement and layout.
     */
    public static final int DEFAULT_SPAN = RXBreakpointProfile.ELEMENT.getColumns();

    /**
     * Default left offset.
     */
    public static final int DEFAULT_OFFSET = 0;

    private static final String DEFAULT_STYLE_CLASS = "rx-responsive-col";

    private double responsiveGutter;
    private final Map<String, RXColSpec> namedSpecs = new HashMap<>();

    // ==================== Constructors ====================

    /**
     * Creates an empty responsive column.
     */
    public RXResponsiveCol() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a responsive column containing the given children.
     *
     * @param children the initial children
     */
    public RXResponsiveCol(Node... children) {
        this();
        getChildren().addAll(children);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Span ====================

    private final IntegerProperty span = new StyleableIntegerProperty(DEFAULT_SPAN) {
        private int lastValid = DEFAULT_SPAN;

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("span cannot be negative");
            }
            lastValid = value;
            requestRowLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.SPAN;
        }

        @Override
        public Object getBean() {
            return RXResponsiveCol.this;
        }

        @Override
        public String getName() {
            return "span";
        }
    };

    /**
     * Number of columns occupied by this column before responsive overrides.
     * A value of {@code 0} keeps the column in a deterministic zero-size
     * layout state. Breakpoint specs are mobile-first and override this base
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
     * @throws IllegalArgumentException if {@code value < 0}
     */
    public final void setSpan(int value) {
        span.set(value);
    }

    // ==================== Offset ====================

    private final IntegerProperty offset = new StyleableIntegerProperty(DEFAULT_OFFSET) {
        private int lastValid = DEFAULT_OFFSET;

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("offset cannot be negative");
            }
            lastValid = value;
            requestRowLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OFFSET;
        }

        @Override
        public Object getBean() {
            return RXResponsiveCol.this;
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
     * @throws IllegalArgumentException if {@code value < 0}
     */
    public final void setOffset(int value) {
        offset.set(value);
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
     * active row profile contains a breakpoint named {@code xxl}.
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

    // ==================== Named Specs ====================

    /**
     * Returns the spec registered for an arbitrary breakpoint name.
     *
     * @param breakpointName the breakpoint name
     * @return the spec, or {@code null} if none is registered
     * @throws NullPointerException if {@code breakpointName} is {@code null}
     */
    public final RXColSpec getBreakpointSpec(String breakpointName) {
        return namedSpecs.get(requireBreakpointName(breakpointName));
    }

    /**
     * Registers a spec for an arbitrary breakpoint name. Passing {@code null}
     * removes the registered spec.
     *
     * <p>The typed xs/sm/md/lg/xl/xxl properties take precedence when both APIs
     * define the same breakpoint name.</p>
     *
     * @param breakpointName the breakpoint name
     * @param spec           the spec, or {@code null} to remove it
     * @throws NullPointerException     if {@code breakpointName} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code breakpointName} is blank
     */
    public final void setBreakpointSpec(String breakpointName, RXColSpec spec) {
        String normalizedName = requireBreakpointName(breakpointName);
        if (spec == null) {
            namedSpecs.remove(normalizedName);
        } else {
            namedSpecs.put(normalizedName, spec);
        }
        requestRowLayout();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        List<Node> managed = getManagedChildren();
        Pos align = getAlignment();
        HPos alignHpos = align.getHpos();
        VPos alignVpos = align.getVpos();
        Insets insets = getInsets();
        double halfGutter = snapSpaceX(responsiveGutter / 2.0);
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

    void setResponsiveGutter(double value) {
        double normalized = normalizeGutter(value);
        if (Double.compare(responsiveGutter, normalized) != 0) {
            responsiveGutter = normalized;
            requestLayout();
        }
    }

    RXColSpec getSpec(String breakpointName) {
        RXColSpec standardSpec = switch (breakpointName) {
            case "xs" -> getXs();
            case "sm" -> getSm();
            case "md" -> getMd();
            case "lg" -> getLg();
            case "xl" -> getXl();
            case "xxl" -> getXxl();
            default -> null;
        };
        return standardSpec == null ? namedSpecs.get(breakpointName) : standardSpec;
    }

    private ObjectProperty<RXColSpec> createSpecProperty(String name) {
        return new SimpleObjectProperty<>(this, name) {
            @Override
            protected void invalidated() {
                requestRowLayout();
            }
        };
    }

    private String requireBreakpointName(String breakpointName) {
        if (breakpointName == null) {
            throw new NullPointerException("breakpointName cannot be null");
        }
        if (breakpointName.isBlank()) {
            throw new IllegalArgumentException("breakpointName cannot be blank");
        }
        return breakpointName;
    }

    private void requestRowLayout() {
        requestLayout();
        Parent parent = getParent();
        if (parent != null) {
            parent.requestLayout();
        }
    }

    private double boundedPrefWidth(Node child, double height) {
        double prefWidth = child.prefWidth(height);
        if (!child.isResizable()) {
            return snapSizeX(prefWidth);
        }
        return snapSizeX(boundedSize(child.minWidth(height), prefWidth, child.maxWidth(height)));
    }

    private double boundedPrefHeight(Node child, double width) {
        double prefHeight = child.prefHeight(width);
        if (!child.isResizable()) {
            return snapSizeY(prefHeight);
        }
        return snapSizeY(boundedSize(child.minHeight(width), prefHeight, child.maxHeight(width)));
    }

    private static double boundedSize(double min, double pref, double max) {
        double bounded = Math.max(min, pref);
        return Math.min(bounded, Math.max(min, max));
    }

    private static Insets marginOrEmpty(Node child) {
        Insets margin = StackPane.getMargin(child);
        return margin == null ? Insets.EMPTY : margin;
    }

    private static double normalizeGutter(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {
        private static final CssMetaData<RXResponsiveCol, Number> SPAN =
                new CssMetaData<>("-rx-span", SizeConverter.getInstance(), DEFAULT_SPAN) {
                    @Override
                    public boolean isSettable(RXResponsiveCol col) {
                        return !col.span.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXResponsiveCol col) {
                        return (StyleableProperty<Number>) col.spanProperty();
                    }
                };

        private static final CssMetaData<RXResponsiveCol, Number> OFFSET =
                new CssMetaData<>("-rx-offset", SizeConverter.getInstance(), DEFAULT_OFFSET) {
                    @Override
                    public boolean isSettable(RXResponsiveCol col) {
                        return !col.offset.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXResponsiveCol col) {
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
