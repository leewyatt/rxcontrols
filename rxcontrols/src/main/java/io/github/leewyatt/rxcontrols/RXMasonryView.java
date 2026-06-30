package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.MasonryViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.skins.RXMasonryViewSkin;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A virtualized, responsive masonry (waterfall) grid of items: items flow into
 * equal-width columns, each placed in the currently shortest column, so cells of
 * different heights tile without row alignment. Built on a self-contained viewport
 * (not {@code VirtualFlow}), it virtualizes by vertical range so only the cells
 * intersecting the viewport hold live cells. It is to a waterfall layout what
 * {@link RXTileView} is to a uniform grid.
 *
 * <p>Cell height is the design's primary contract. Set a
 * {@link #cellHeightProviderProperty() cellHeightProvider} to give each item's exact
 * height from its data (an image gallery returns {@code cellWidth / aspectRatio});
 * the layout is then exact and never jumps. With no provider the view seeds each item
 * at the {@link #estimatedCellHeightProperty() estimatedCellHeight}, then measures each
 * cell as it is realized and re-packs to converge on the real heights.
 *
 * <p>The column count is derived from {@link #columnWidthProperty() columnWidth} and
 * the available width, or forced by {@link #columnCountProperty() columnCount} or by
 * mobile-first {@link #setBreakpointColumns(String, Integer) breakpoint overrides};
 * {@link #fillWidthProperty() fillWidth} stretches columns to fill the width. An item
 * may span several columns via a {@link #columnSpanFactoryProperty() columnSpanFactory}.
 * The view publishes read-only metrics after each layout —
 * {@link #actualColumnCountProperty() actualColumnCount},
 * {@link #activeBreakpointProperty() activeBreakpoint},
 * {@link #firstVisibleIndexProperty() firstVisibleIndex} and
 * {@link #lastVisibleIndexProperty() lastVisibleIndex} — and can scroll to an item via
 * the {@code scrollTo} methods.
 *
 * <p>Selection is held by a {@link #selectionModelProperty() selectionModel}
 * (single by default). The view is a single Tab stop; once focused it navigates with
 * the arrow keys, {@code Home}/{@code End} and {@code Page Up}/{@code Down}, extends a
 * range with {@code Shift}, moves focus without selecting with {@code Shortcut},
 * toggles with {@code Space}, selects all with {@code Shortcut+A}, and activates the
 * focused item — firing {@link #onActionProperty() onAction} — with {@code Enter} or a
 * double-click.
 *
 * @param <T> the item type
 */
public class RXMasonryView<T> extends Control {

    // ==================== Constants ====================

    /**
     * Breakpoint override sentinel: setting a breakpoint's column count to this value
     * breaks the mobile-first cascade and restores {@link #columnWidthProperty()
     * columnWidth} auto-calculation from that breakpoint up.
     */
    public static final int AUTO_COLUMNS = 0;

    private static final double DEFAULT_COLUMN_WIDTH = 260.0;
    private static final double DEFAULT_HGAP = 8.0;
    private static final double DEFAULT_VGAP = 8.0;
    private static final int DEFAULT_COLUMN_COUNT = 0;
    private static final int DEFAULT_PREF_COLUMNS = 3;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final boolean DEFAULT_FILL_WIDTH = true;
    private static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;
    private static final RXBreakpointProfile DEFAULT_BREAKPOINT_PROFILE = RXBreakpointProfile.ANT_DESIGN;
    private static final double DEFAULT_ESTIMATED_CELL_HEIGHT = 200.0;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final String DEFAULT_STYLE_CLASS = "rx-masonry-view";

    // ==================== Constructors ====================

    /**
     * Creates an empty masonry view.
     */
    public RXMasonryView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleRoleDescription("masonry view");
        // The control is a single Tab stop; cells are not focus-traversable.
        setFocusTraversable(true);
    }

    /**
     * Creates a masonry view backed by the given items.
     *
     * @param items the items to display; may be {@code null}
     */
    public RXMasonryView(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMasonryViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The items shown by the view. A {@code null} list is treated as empty.
     *
     * @return the items property
     */
    public final ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    /**
     * Returns the items shown by the view.
     *
     * @return the items list, or {@code null}
     */
    public final ObservableList<T> getItems() {
        return items.get();
    }

    /**
     * Sets the items shown by the view.
     *
     * @param value the items list, or {@code null} for an empty view
     */
    public final void setItems(ObservableList<T> value) {
        items.set(value);
    }

    // ==================== Cell Factory ====================

    private final ObjectProperty<Callback<RXMasonryView<T>, RXMasonryCell<T>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Factory that creates the cells rendering each item. When {@code null}, the view
     * uses a default factory that shows {@code item.toString()}.
     *
     * @return the cell-factory property
     */
    public final ObjectProperty<Callback<RXMasonryView<T>, RXMasonryCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return the cell factory, or {@code null} for the default
     */
    public final Callback<RXMasonryView<T>, RXMasonryCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value the cell factory, or {@code null} for the default
     */
    public final void setCellFactory(Callback<RXMasonryView<T>, RXMasonryCell<T>> value) {
        cellFactory.set(value);
    }

    // ==================== Cell Height Provider ====================

    private final ObjectProperty<CellHeightProvider<T>> cellHeightProvider =
            new SimpleObjectProperty<>(this, "cellHeightProvider");

    /**
     * Supplies each cell's exact height from its item and resolved slot width. This
     * is the primary, precise height path: when set, the view never measures a live
     * cell, so scrolling and the scroll bar are exact and the layout never jumps. When
     * {@code null} (the default), the view seeds each item at
     * {@link #estimatedCellHeightProperty() estimatedCellHeight}, then measures each
     * realized cell and re-packs to converge on the real heights.
     *
     * @return the cell-height-provider property
     */
    public final ObjectProperty<CellHeightProvider<T>> cellHeightProviderProperty() {
        return cellHeightProvider;
    }

    /**
     * Returns the cell-height provider.
     *
     * @return the cell-height provider, or {@code null}
     */
    public final CellHeightProvider<T> getCellHeightProvider() {
        return cellHeightProvider.get();
    }

    /**
     * Sets the cell-height provider.
     *
     * @param value the provider, or {@code null} to use the estimated height
     */
    public final void setCellHeightProvider(CellHeightProvider<T> value) {
        cellHeightProvider.set(value);
    }

    // ==================== Estimated Cell Height ====================

    private final DoubleProperty estimatedCellHeight =
            new SimpleDoubleProperty(this, "estimatedCellHeight", DEFAULT_ESTIMATED_CELL_HEIGHT);

    /**
     * Placeholder height for an item before its cell has been measured, used when no
     * {@link #cellHeightProviderProperty() cellHeightProvider} is set. With no provider
     * the view seeds each item at this height, then measures each cell as it is realized
     * and re-packs to converge on the real heights (so the layout settles as cells are
     * measured rather than staying at the estimate). A non-positive or non-finite value
     * is accepted but resolved to the default at layout time.
     *
     * @return the estimated-cell-height property
     */
    public final DoubleProperty estimatedCellHeightProperty() {
        return estimatedCellHeight;
    }

    /**
     * Returns the estimated cell height.
     *
     * @return the estimated cell height
     */
    public final double getEstimatedCellHeight() {
        return estimatedCellHeight.get();
    }

    /**
     * Sets the estimated cell height.
     *
     * @param value the estimated cell height
     */
    public final void setEstimatedCellHeight(double value) {
        estimatedCellHeight.set(value);
    }

    // ==================== Column Span Factory ====================

    private final ObjectProperty<Callback<T, Integer>> columnSpanFactory =
            new SimpleObjectProperty<>(this, "columnSpanFactory");

    /**
     * Factory deriving how many columns each item spans. {@code null} (the default) or
     * a value below one means a single column; a value larger than the resolved column
     * count is clamped to the full row. The items are not reordered.
     *
     * @return the column-span-factory property
     */
    public final ObjectProperty<Callback<T, Integer>> columnSpanFactoryProperty() {
        return columnSpanFactory;
    }

    /**
     * Returns the column-span factory.
     *
     * @return the column-span factory, or {@code null} for single-column items
     */
    public final Callback<T, Integer> getColumnSpanFactory() {
        return columnSpanFactory.get();
    }

    /**
     * Sets the column-span factory.
     *
     * @param value the column-span factory, or {@code null} for single-column items
     */
    public final void setColumnSpanFactory(Callback<T, Integer> value) {
        columnSpanFactory.set(value);
    }

    // ==================== Column Width ====================

    private final DoubleProperty columnWidth = new StyleableDoubleProperty(DEFAULT_COLUMN_WIDTH) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "columnWidth";
        }
    };

    /**
     * Target width of each column, in pixels, used to derive the column count when no
     * count is forced. Lenient: a non-positive or non-finite value is accepted but
     * resolved to the default at layout time.
     *
     * @return the column-width property
     */
    public final DoubleProperty columnWidthProperty() {
        return columnWidth;
    }

    /**
     * Returns the target column width.
     *
     * @return the target column width
     */
    public final double getColumnWidth() {
        return columnWidth.get();
    }

    /**
     * Sets the target column width.
     *
     * @param value the target column width
     */
    public final void setColumnWidth(double value) {
        columnWidth.set(value);
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount = new StyleableIntegerProperty(DEFAULT_COLUMN_COUNT) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_COUNT;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "columnCount";
        }
    };

    /**
     * Forced column count. A value of one or more overrides the
     * {@link #columnWidthProperty() columnWidth} auto-calculation and any breakpoint
     * overrides; a non-positive value (the default {@code 0}) means automatic.
     *
     * @return the column-count property
     */
    public final IntegerProperty columnCountProperty() {
        return columnCount;
    }

    /**
     * Returns the forced column count.
     *
     * @return the forced column count, or {@code 0} (or any non-positive value) for automatic
     */
    public final int getColumnCount() {
        return columnCount.get();
    }

    /**
     * Sets the forced column count.
     *
     * @param value a positive forced count, or {@code 0} for automatic
     */
    public final void setColumnCount(int value) {
        columnCount.set(value);
    }

    // ==================== Pref Columns ====================

    private final IntegerProperty prefColumns = new StyleableIntegerProperty(DEFAULT_PREF_COLUMNS) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.PREF_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "prefColumns";
        }
    };

    /**
     * Preferred number of columns, used only to compute the preferred width when the
     * view has no width constraint.
     *
     * @return the pref-columns property
     */
    public final IntegerProperty prefColumnsProperty() {
        return prefColumns;
    }

    /**
     * Returns the preferred column count.
     *
     * @return the preferred column count
     */
    public final int getPrefColumns() {
        return prefColumns.get();
    }

    /**
     * Sets the preferred column count.
     *
     * @param value the preferred column count
     */
    public final void setPrefColumns(int value) {
        prefColumns.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns = new StyleableIntegerProperty(DEFAULT_MAX_COLUMNS) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "maxColumns";
        }
    };

    /**
     * Upper bound on the resolved column count. Any value {@code <= 0} means no upper
     * bound.
     *
     * @return the max-columns property
     */
    public final IntegerProperty maxColumnsProperty() {
        return maxColumns;
    }

    /**
     * Returns the maximum column count.
     *
     * @return the maximum column count, or {@code <= 0} for unbounded
     */
    public final int getMaxColumns() {
        return maxColumns.get();
    }

    /**
     * Sets the maximum column count.
     *
     * @param value a positive bound, or {@code <= 0} for unbounded
     */
    public final void setMaxColumns(int value) {
        maxColumns.set(value);
    }

    // ==================== Fill Width ====================

    private final BooleanProperty fillWidth = new StyleableBooleanProperty(DEFAULT_FILL_WIDTH) {
        @Override
        public CssMetaData<RXMasonryView<?>, Boolean> getCssMetaData() {
            return StyleableProperties.FILL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "fillWidth";
        }
    };

    /**
     * Whether columns stretch to fill the available width ({@code true}, the default)
     * or stay at {@link #columnWidthProperty() columnWidth} with spare width left over.
     *
     * @return the fill-width property
     */
    public final BooleanProperty fillWidthProperty() {
        return fillWidth;
    }

    /**
     * Returns whether columns fill the available width.
     *
     * @return whether columns fill the available width
     */
    public final boolean isFillWidth() {
        return fillWidth.get();
    }

    /**
     * Sets whether columns fill the available width.
     *
     * @param value whether columns fill the available width
     */
    public final void setFillWidth(boolean value) {
        fillWidth.set(value);
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment = new StyleableObjectProperty<>(DEFAULT_ALIGNMENT) {
        @Override
        public CssMetaData<RXMasonryView<?>, Pos> getCssMetaData() {
            return StyleableProperties.ALIGNMENT;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "alignment";
        }
    };

    /**
     * How the block of columns is positioned within a wider viewport. Only the
     * horizontal component is honored; the vertical component is ignored because a
     * scrolling viewport always starts at the top. A {@code null} value is treated as
     * {@link Pos#TOP_LEFT}.
     *
     * @return the alignment property
     */
    public final ObjectProperty<Pos> alignmentProperty() {
        return alignment;
    }

    /**
     * Returns the alignment.
     *
     * @return the alignment, possibly {@code null}
     */
    public final Pos getAlignment() {
        return alignment.get();
    }

    /**
     * Sets the alignment.
     *
     * @param value the alignment, or {@code null} for the default
     */
    public final void setAlignment(Pos value) {
        alignment.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = new StyleableDoubleProperty(DEFAULT_HGAP) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.HGAP;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between columns. A non-finite value is resolved to the default at
     * layout time; a negative value overlaps columns.
     *
     * @return the hgap property
     */
    public final DoubleProperty hgapProperty() {
        return hgap;
    }

    /**
     * Returns the horizontal gap between columns.
     *
     * @return the hgap
     */
    public final double getHgap() {
        return hgap.get();
    }

    /**
     * Sets the horizontal gap between columns.
     *
     * @param value the hgap
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    // ==================== Vgap ====================

    private final DoubleProperty vgap = new StyleableDoubleProperty(DEFAULT_VGAP) {
        @Override
        public CssMetaData<RXMasonryView<?>, Number> getCssMetaData() {
            return StyleableProperties.VGAP;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between stacked items within a column. A non-finite value is
     * resolved to the default at layout time; a negative value is clamped to zero —
     * unlike the node-based {@code RXMasonryPane}, the virtualized view stacks items
     * without overlap, because its visible-window query requires monotonic stacking.
     *
     * @return the vgap property
     */
    public final DoubleProperty vgapProperty() {
        return vgap;
    }

    /**
     * Returns the vertical gap between items.
     *
     * @return the vgap
     */
    public final double getVgap() {
        return vgap.get();
    }

    /**
     * Sets the vertical gap between items.
     *
     * @param value the vgap
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    // ==================== Breakpoint Profile ====================

    private final ObservableMap<String, Integer> breakpointColumns = FXCollections.observableHashMap();
    private final ObservableMap<String, Integer> breakpointColumnsView =
            FXCollections.unmodifiableObservableMap(breakpointColumns);

    private final ObjectProperty<RXBreakpointProfile> breakpointProfile =
            new SimpleObjectProperty<>(this, "breakpointProfile", DEFAULT_BREAKPOINT_PROFILE);

    /**
     * Breakpoint profile used to resolve the active breakpoint from the view's content
     * width. Only the profile's breakpoint set and {@code resolve} are used; its grid
     * column count is ignored. A {@code null} value is not rejected; it resolves to the
     * default at the use site.
     *
     * @return the breakpoint-profile property
     */
    public final ObjectProperty<RXBreakpointProfile> breakpointProfileProperty() {
        return breakpointProfile;
    }

    /**
     * Returns the breakpoint profile.
     *
     * @return the breakpoint profile
     */
    public final RXBreakpointProfile getBreakpointProfile() {
        return breakpointProfile.get();
    }

    /**
     * Sets the breakpoint profile.
     *
     * @param value the breakpoint profile, or {@code null} to fall back to the default
     */
    public final void setBreakpointProfile(RXBreakpointProfile value) {
        breakpointProfile.set(value);
    }

    // ==================== Breakpoint Columns ====================

    /**
     * Sets the column count for a named breakpoint, overriding the
     * {@link #columnWidthProperty() columnWidth} auto-calculation. Overrides are
     * mobile-first: a value set at one breakpoint stays in effect for wider breakpoints
     * until another override replaces it. Passing {@link #AUTO_COLUMNS} sets an explicit
     * auto override that breaks the cascade and restores columnWidth auto-calculation
     * from that breakpoint up, until a wider breakpoint sets a positive count again.
     * Setting {@code null} clears the override entirely so the breakpoint inherits.
     *
     * @param breakpointName the breakpoint name (e.g. {@code "md"})
     * @param columns        a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                       auto, or {@code null} to clear
     * @throws NullPointerException     if {@code breakpointName} is {@code null}
     * @throws IllegalArgumentException if {@code breakpointName} is blank or {@code columns} is negative
     */
    public final void setBreakpointColumns(String breakpointName, Integer columns) {
        if (breakpointName == null) {
            throw new NullPointerException("breakpointName cannot be null");
        }
        if (breakpointName.isBlank()) {
            throw new IllegalArgumentException("breakpointName cannot be blank");
        }
        if (columns != null && columns < 0) {
            throw new IllegalArgumentException("columns cannot be negative");
        }
        if (columns == null) {
            breakpointColumns.remove(breakpointName);
        } else {
            breakpointColumns.put(breakpointName, columns);
        }
        // The map is observable; the skin relays out and re-fills in response.
    }

    /**
     * Returns the column count override for a named breakpoint.
     *
     * @param breakpointName the breakpoint name
     * @return the override, or {@code null} if none is set
     */
    public final Integer getBreakpointColumns(String breakpointName) {
        return breakpointColumns.get(breakpointName);
    }

    /**
     * Returns an unmodifiable, observable view of all breakpoint column overrides,
     * keyed by breakpoint name. The skin observes it to re-resolve the column count
     * when an override changes.
     *
     * @return the breakpoint column overrides
     */
    public final ObservableMap<String, Integer> getBreakpointColumnOverrides() {
        return breakpointColumnsView;
    }

    /**
     * Sets the {@code xs} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setXs(Integer columns) {
        setBreakpointColumns("xs", columns);
    }

    /**
     * Returns the {@code xs} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXs() {
        return getBreakpointColumns("xs");
    }

    /**
     * Sets the {@code sm} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setSm(Integer columns) {
        setBreakpointColumns("sm", columns);
    }

    /**
     * Returns the {@code sm} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getSm() {
        return getBreakpointColumns("sm");
    }

    /**
     * Sets the {@code md} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setMd(Integer columns) {
        setBreakpointColumns("md", columns);
    }

    /**
     * Returns the {@code md} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getMd() {
        return getBreakpointColumns("md");
    }

    /**
     * Sets the {@code lg} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setLg(Integer columns) {
        setBreakpointColumns("lg", columns);
    }

    /**
     * Returns the {@code lg} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getLg() {
        return getBreakpointColumns("lg");
    }

    /**
     * Sets the {@code xl} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setXl(Integer columns) {
        setBreakpointColumns("xl", columns);
    }

    /**
     * Returns the {@code xl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXl() {
        return getBreakpointColumns("xl");
    }

    /**
     * Sets the {@code xxl} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setXxl(Integer columns) {
        setBreakpointColumns("xxl", columns);
    }

    /**
     * Returns the {@code xxl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXxl() {
        return getBreakpointColumns("xxl");
    }

    /**
     * Sets the {@code xxxl} breakpoint column count.
     *
     * @param columns the column count, {@link #AUTO_COLUMNS} for explicit auto, or {@code null} to clear
     */
    public final void setXxxl(Integer columns) {
        setBreakpointColumns("xxxl", columns);
    }

    /**
     * Returns the {@code xxxl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXxxl() {
        return getBreakpointColumns("xxxl");
    }

    // ==================== Active Breakpoint (read-only) ====================

    private PseudoClass activeBreakpointPseudoClass;

    private final ReadOnlyObjectWrapper<RXBreakpoint> activeBreakpoint =
            new ReadOnlyObjectWrapper<>(this, "activeBreakpoint") {
                @Override
                protected void invalidated() {
                    updateActiveBreakpointPseudoClass(get());
                }
            };

    /**
     * Breakpoint resolved from the view's current content width, or {@code null}
     * before the view is laid out. Updated by the skin each layout pass, and drives
     * the {@code :<name>} pseudo-class.
     *
     * @return the read-only active-breakpoint property
     */
    public final ReadOnlyObjectProperty<RXBreakpoint> activeBreakpointProperty() {
        return activeBreakpoint.getReadOnlyProperty();
    }

    /**
     * Returns the active breakpoint.
     *
     * @return the active breakpoint, or {@code null} before the view is laid out
     */
    public final RXBreakpoint getActiveBreakpoint() {
        return activeBreakpoint.get();
    }

    /**
     * Updates the active breakpoint. Intended for the skin, which resolves it from the
     * stable pre-scrollbar width each layout pass.
     *
     * @param value the active breakpoint, or {@code null} for none
     */
    public final void setActiveBreakpoint(RXBreakpoint value) {
        activeBreakpoint.set(value);
    }

    private void updateActiveBreakpointPseudoClass(RXBreakpoint value) {
        PseudoClass next = value == null ? null : PseudoClass.getPseudoClass(value.getName());
        if (next == activeBreakpointPseudoClass) {
            return;
        }
        if (activeBreakpointPseudoClass != null) {
            pseudoClassStateChanged(activeBreakpointPseudoClass, false);
        }
        if (next != null) {
            pseudoClassStateChanged(next, true);
        }
        activeBreakpointPseudoClass = next;
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXMasonryView.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether cells glide to their new positions when a change in the column count or
     * the items reflows the grid. Off by default; turning it off while a reflow is in
     * flight snaps every cell to its final position.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether reflow animation is enabled.
     *
     * @return whether reflow animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether reflow animation is enabled.
     *
     * @param value whether reflow animation is enabled
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
                    return RXMasonryView.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single reflow glide. A {@code null}, non-positive, unknown or
     * indefinite value is accepted and disables animation, exactly like
     * {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the reflow-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the reflow-animation duration.
     *
     * @param value the duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the reflow glide. {@code null} falls back to
     * {@link Interpolator#EASE_BOTH}. Not styleable (no stable CSS converter).
     *
     * @return the animation-interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * Node shown when the view has no items. {@code null} shows nothing.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder node.
     *
     * @return the placeholder node, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the placeholder node.
     *
     * @param value the placeholder node, or {@code null} for none
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== On Action ====================

    private ObjectProperty<EventHandler<MasonryViewActionEvent<T>>> onAction;

    /**
     * Handler invoked when a cell is activated — by pressing {@code Enter} on the
     * focused cell or double-clicking it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<MasonryViewActionEvent<T>>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(MasonryViewActionEvent.actionType(), get());
                }

                @Override
                public Object getBean() {
                    return RXMasonryView.this;
                }

                @Override
                public String getName() {
                    return "onAction";
                }
            };
        }
        return onAction;
    }

    /**
     * Returns the activation handler.
     *
     * @return the activation handler, or {@code null}
     */
    public final EventHandler<MasonryViewActionEvent<T>> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /**
     * Sets the activation handler.
     *
     * @param value the activation handler, or {@code null} for none
     */
    public final void setOnAction(EventHandler<MasonryViewActionEvent<T>> value) {
        onActionProperty().set(value);
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<MultipleSelectionModel<T>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel", new RXIndexedSelectionModel<>(itemsProperty()));

    /**
     * The selection model. Defaults to a non-null {@link RXIndexedSelectionModel} in
     * single-selection mode; switch to multiple selection via
     * {@code getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)}.
     *
     * @return the selection-model property
     */
    public final ObjectProperty<MultipleSelectionModel<T>> selectionModelProperty() {
        return selectionModel;
    }

    /**
     * Returns the selection model.
     *
     * @return the selection model, possibly {@code null} if explicitly cleared
     */
    public final MultipleSelectionModel<T> getSelectionModel() {
        return selectionModel.get();
    }

    /**
     * Sets the selection model.
     *
     * @param value the selection model, or {@code null} to disable selection
     */
    public final void setSelectionModel(MultipleSelectionModel<T> value) {
        selectionModel.set(value);
    }

    // ==================== Actual Column Count (read-only) ====================

    private final ReadOnlyIntegerWrapper actualColumnCount =
            new ReadOnlyIntegerWrapper(this, "actualColumnCount", 0);

    /**
     * The column count resolved by the most recent layout.
     *
     * @return the read-only actual-column-count property
     */
    public final ReadOnlyIntegerProperty actualColumnCountProperty() {
        return actualColumnCount.getReadOnlyProperty();
    }

    /**
     * Returns the column count resolved by the most recent layout.
     *
     * @return the actual column count
     */
    public final int getActualColumnCount() {
        return actualColumnCount.get();
    }

    /**
     * Updates the resolved column count. Intended for skins / behaviors.
     *
     * @param value the resolved column count
     */
    public final void setActualColumnCount(int value) {
        actualColumnCount.set(value);
    }

    // ==================== Visible Index Bounds (read-only) ====================

    private final ReadOnlyIntegerWrapper firstVisibleIndex =
            new ReadOnlyIntegerWrapper(this, "firstVisibleIndex", -1);

    /**
     * The lowest item index currently realized in the viewport, or {@code -1} when
     * nothing is visible. Together with {@link #lastVisibleIndexProperty()
     * lastVisibleIndex} this is a bound, not a contiguous range: because tall items
     * make a column's visible items lag its neighbors, indices between the two are not
     * guaranteed visible.
     *
     * @return the read-only first-visible-index property
     */
    public final ReadOnlyIntegerProperty firstVisibleIndexProperty() {
        return firstVisibleIndex.getReadOnlyProperty();
    }

    /**
     * Returns the lowest realized item index.
     *
     * @return the first visible index, or {@code -1} when nothing is visible
     */
    public final int getFirstVisibleIndex() {
        return firstVisibleIndex.get();
    }

    /**
     * Updates the first visible index. Intended for skins / behaviors.
     *
     * @param value the first visible index, or {@code -1} for none
     */
    public final void setFirstVisibleIndex(int value) {
        firstVisibleIndex.set(value);
    }

    private final ReadOnlyIntegerWrapper lastVisibleIndex =
            new ReadOnlyIntegerWrapper(this, "lastVisibleIndex", -1);

    /**
     * The highest item index currently realized in the viewport, or {@code -1} when
     * nothing is visible. See {@link #firstVisibleIndexProperty() firstVisibleIndex}
     * for why this pair is a bound, not a contiguous range.
     *
     * @return the read-only last-visible-index property
     */
    public final ReadOnlyIntegerProperty lastVisibleIndexProperty() {
        return lastVisibleIndex.getReadOnlyProperty();
    }

    /**
     * Returns the highest realized item index.
     *
     * @return the last visible index, or {@code -1} when nothing is visible
     */
    public final int getLastVisibleIndex() {
        return lastVisibleIndex.get();
    }

    /**
     * Updates the last visible index. Intended for skins / behaviors.
     *
     * @param value the last visible index, or {@code -1} for none
     */
    public final void setLastVisibleIndex(int value) {
        lastVisibleIndex.set(value);
    }

    // ==================== Scrolling ====================

    private boolean pendingScroll;
    private int pendingScrollIndex;
    private ScrollAlignment pendingScrollAlignment = ScrollAlignment.START;

    /**
     * Scrolls so the item at {@code index} is visible at the top of the viewport.
     *
     * @param index the item index; out-of-range values are clamped during layout
     */
    public final void scrollTo(int index) {
        scrollTo(index, ScrollAlignment.START);
    }

    /**
     * Scrolls so the item at {@code index} is visible with the given alignment. The
     * request is applied on the next layout pass.
     *
     * @param index     the item index; out-of-range values are clamped during layout
     * @param alignment where the target should land; {@code null} is treated as
     *                  {@link ScrollAlignment#START}
     */
    public final void scrollTo(int index, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = index;
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Scrolls so the given item is visible at the top of the viewport. Does nothing if
     * the item is not in the list.
     *
     * @param item the item to scroll to
     */
    public final void scrollTo(T item) {
        scrollTo(item, ScrollAlignment.START);
    }

    /**
     * Scrolls so the given item is visible with the given alignment. Does nothing if
     * the item is not in the list.
     *
     * @param item      the item to scroll to
     * @param alignment where the target should land; {@code null} is treated as
     *                  {@link ScrollAlignment#START}
     */
    public final void scrollTo(T item, ScrollAlignment alignment) {
        ObservableList<T> list = getItems();
        if (list == null) {
            return;
        }
        int index = list.indexOf(item);
        if (index >= 0) {
            scrollTo(index, alignment);
        }
    }

    /**
     * Whether a scroll request is waiting to be applied. Intended for skins / behaviors.
     *
     * @return {@code true} if a scroll request is pending
     */
    public final boolean hasPendingScroll() {
        return pendingScroll;
    }

    /**
     * The item index of the pending scroll request. Only meaningful when
     * {@link #hasPendingScroll()} is {@code true}. Intended for skins / behaviors.
     *
     * @return the pending scroll item index
     */
    public final int getPendingScrollIndex() {
        return pendingScrollIndex;
    }

    /**
     * The alignment of the pending scroll request. Intended for skins / behaviors.
     *
     * @return the pending scroll alignment, never {@code null}
     */
    public final ScrollAlignment getPendingScrollAlignment() {
        return pendingScrollAlignment;
    }

    /**
     * Clears the pending scroll request after it has been applied. Intended for skins /
     * behaviors.
     */
    public final void clearPendingScroll() {
        pendingScroll = false;
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXMasonryView<?>, Number> COLUMN_WIDTH =
                new CssMetaData<>("-rx-column-width", SizeConverter.getInstance(), DEFAULT_COLUMN_WIDTH) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.columnWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.columnWidthProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Number> COLUMN_COUNT =
                new CssMetaData<>("-rx-column-count", SizeConverter.getInstance(), DEFAULT_COLUMN_COUNT) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.columnCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.columnCountProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Number> PREF_COLUMNS =
                new CssMetaData<>("-rx-pref-columns", SizeConverter.getInstance(), DEFAULT_PREF_COLUMNS) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.prefColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.prefColumnsProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Number> MAX_COLUMNS =
                new CssMetaData<>("-rx-max-columns", SizeConverter.getInstance(), DEFAULT_MAX_COLUMNS) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.maxColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Number>) node.maxColumnsProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Boolean> FILL_WIDTH =
                new CssMetaData<>("-rx-fill-width", BooleanConverter.getInstance(), DEFAULT_FILL_WIDTH) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.fillWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Boolean>) node.fillWidthProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Pos> ALIGNMENT =
                new CssMetaData<>("-rx-alignment", new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Pos>) node.alignmentProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXMasonryView<?>, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXMasonryView<?> node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXMasonryView<?> node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, COLUMN_WIDTH, HGAP, VGAP, COLUMN_COUNT, PREF_COLUMNS, MAX_COLUMNS,
                    FILL_WIDTH, ALIGNMENT, ANIMATED, ANIMATION_DURATION);
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

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
