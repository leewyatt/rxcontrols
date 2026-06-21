package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXGridViewSkin;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A virtualized, responsive grid of items. {@code RXGridView} lays a flat
 * {@link #itemsProperty() items} list out in a uniform grid: it derives the
 * column count from {@link #cellWidthProperty() cellWidth} and the available
 * width (or honors a forced {@link #columnCountProperty() columnCount}), wraps
 * items into rows, and virtualizes by row so only the visible rows hold live
 * cells.
 *
 * <p>Each item is rendered by a {@link RXGridCell} produced by the
 * {@link #cellFactoryProperty() cellFactory}; when no factory is set the grid
 * shows {@code item.toString()}. A {@code null} item is a legal value — empty
 * cells are decided by the index, not by the item being {@code null}.
 *
 * <p>Within a row, cells of width {@code cellWidth} are separated by
 * {@link #hgapProperty() hgap} and rows by {@link #vgapProperty() vgap}. How a
 * row uses its spare width — position the block, grow the gaps, or grow the
 * cells (up to {@link #maxCellWidthProperty() maxCellWidth}) — is controlled by
 * {@link #itemsJustifyProperty() itemsJustify}.
 *
 * <p>The grid publishes read-only metrics after each layout —
 * {@link #actualColumnCountProperty() actualColumnCount},
 * {@link #rowCountProperty() rowCount} and
 * {@link #visibleRangeProperty() visibleRange} — and can scroll to an item with
 * {@link #scrollTo(int)} / {@link #scrollTo(Object)}.
 *
 * <p>V1 is display-only: selection, focus and keyboard navigation are not part
 * of this release.
 *
 * @param <T> the item type
 */
public class RXGridView<T> extends Control {

    // ==================== Constants ====================

    private static final double DEFAULT_CELL_WIDTH = 100.0;
    private static final double DEFAULT_CELL_HEIGHT = 100.0;
    private static final double DEFAULT_MAX_CELL_WIDTH = 0.0;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final int DEFAULT_COLUMN_COUNT = 0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final RXGridJustify DEFAULT_ITEMS_JUSTIFY = RXGridJustify.START;

    private static final String DEFAULT_STYLE_CLASS = "rx-grid-view";

    /**
     * Sentinel for {@link #columnCountProperty() columnCount} and
     * {@link #maxColumnsProperty() maxColumns} meaning "derive automatically":
     * an automatic column count for {@code columnCount}, and no upper bound for
     * {@code maxColumns}. Aligns with {@code RXMasonryPane.AUTO_COLUMNS}.
     */
    public static final int AUTO_COLUMNS = 0;

    // ==================== Constructors ====================

    /**
     * Creates an empty grid view.
     */
    public RXGridView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleRoleDescription("grid");
    }

    /**
     * Creates a grid view backed by the given items.
     *
     * @param items the items to display; may be {@code null}
     */
    public RXGridView(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXGridViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The items shown by the grid. A {@code null} list is treated as empty.
     *
     * @return the items property
     */
    public final ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    /**
     * Returns the items shown by the grid.
     *
     * @return the items list, or {@code null}
     */
    public final ObservableList<T> getItems() {
        return items.get();
    }

    /**
     * Sets the items shown by the grid.
     *
     * @param value the items list, or {@code null} for an empty grid
     */
    public final void setItems(ObservableList<T> value) {
        items.set(value);
    }

    // ==================== Cell Factory ====================

    private final ObjectProperty<Callback<RXGridView<T>, RXGridCell<T>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Factory that creates the cells rendering each item. When {@code null}, the
     * grid uses a default factory that shows {@code item.toString()}.
     *
     * @return the cell-factory property
     */
    public final ObjectProperty<Callback<RXGridView<T>, RXGridCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return the cell factory, or {@code null} for the default
     */
    public final Callback<RXGridView<T>, RXGridCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value the cell factory, or {@code null} for the default
     */
    public final void setCellFactory(Callback<RXGridView<T>, RXGridCell<T>> value) {
        cellFactory.set(value);
    }

    // ==================== Cell Width ====================

    private final DoubleProperty cellWidth = new StyleableDoubleProperty(DEFAULT_CELL_WIDTH) {
        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_CELL_WIDTH);
                }
                throw new IllegalArgumentException("cellWidth must be a finite positive number");
            }
        }

        @Override
        public CssMetaData<RXGridView<?>, Number> getCssMetaData() {
            return StyleableProperties.CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXGridView.this;
        }

        @Override
        public String getName() {
            return "cellWidth";
        }
    };

    /**
     * Width of each cell, in pixels. Must be a finite positive number. Drives the
     * automatic column count when {@code columnCount} is automatic.
     *
     * @return the cell-width property
     */
    public final DoubleProperty cellWidthProperty() {
        return cellWidth;
    }

    /**
     * Returns the cell width.
     *
     * @return the cell width
     */
    public final double getCellWidth() {
        return cellWidth.get();
    }

    /**
     * Sets the cell width.
     *
     * @param value the cell width
     * @throws IllegalArgumentException if {@code value} is not a finite positive number
     */
    public final void setCellWidth(double value) {
        cellWidth.set(value);
    }

    // ==================== Cell Height ====================

    private final DoubleProperty cellHeight = new StyleableDoubleProperty(DEFAULT_CELL_HEIGHT) {
        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_CELL_HEIGHT);
                }
                throw new IllegalArgumentException("cellHeight must be a finite positive number");
            }
        }

        @Override
        public CssMetaData<RXGridView<?>, Number> getCssMetaData() {
            return StyleableProperties.CELL_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXGridView.this;
        }

        @Override
        public String getName() {
            return "cellHeight";
        }
    };

    /**
     * Height of each cell, in pixels. Must be a finite positive number.
     *
     * @return the cell-height property
     */
    public final DoubleProperty cellHeightProperty() {
        return cellHeight;
    }

    /**
     * Returns the cell height.
     *
     * @return the cell height
     */
    public final double getCellHeight() {
        return cellHeight.get();
    }

    /**
     * Sets the cell height.
     *
     * @param value the cell height
     * @throws IllegalArgumentException if {@code value} is not a finite positive number
     */
    public final void setCellHeight(double value) {
        cellHeight.set(value);
    }

    // ==================== Max Cell Width ====================

    private final DoubleProperty maxCellWidth = new StyleableDoubleProperty(DEFAULT_MAX_CELL_WIDTH) {
        @Override
        public CssMetaData<RXGridView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXGridView.this;
        }

        @Override
        public String getName() {
            return "maxCellWidth";
        }
    };

    /**
     * Upper bound on how wide a cell may grow when
     * {@link #itemsJustifyProperty() itemsJustify} is
     * {@link RXGridJustify#STRETCH}. {@code 0} (the default) or any non-positive
     * value means unbounded. Has no effect in the other justification modes,
     * where cells keep {@link #cellWidthProperty() cellWidth}.
     *
     * <p>Together with {@code cellWidth} (the minimum that drives the column
     * count) this mirrors a CSS Grid {@code minmax(cellWidth, maxCellWidth)}
     * auto-fill track. A cap smaller than {@code cellWidth} is degenerate
     * ({@code max < min}) and is treated as {@code cellWidth}; cells are never
     * shrunk below their configured width.
     *
     * @return the max-cell-width property
     */
    public final DoubleProperty maxCellWidthProperty() {
        return maxCellWidth;
    }

    /**
     * Returns the maximum cell width used in {@link RXGridJustify#STRETCH} mode.
     *
     * @return the maximum cell width, or {@code 0} for unbounded
     */
    public final double getMaxCellWidth() {
        return maxCellWidth.get();
    }

    /**
     * Sets the maximum cell width used in {@link RXGridJustify#STRETCH} mode.
     *
     * @param value a positive cap, or {@code 0} (or any non-positive value) for
     *              unbounded
     */
    public final void setMaxCellWidth(double value) {
        maxCellWidth.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = new StyleableDoubleProperty(DEFAULT_HGAP) {
        @Override
        public CssMetaData<RXGridView<?>, Number> getCssMetaData() {
            return StyleableProperties.HGAP;
        }

        @Override
        public Object getBean() {
            return RXGridView.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between cells in a row. Negative values are treated as zero
     * during layout.
     *
     * @return the hgap property
     */
    public final DoubleProperty hgapProperty() {
        return hgap;
    }

    /**
     * Returns the horizontal gap between cells.
     *
     * @return the hgap
     */
    public final double getHgap() {
        return hgap.get();
    }

    /**
     * Sets the horizontal gap between cells.
     *
     * @param value the hgap
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    // ==================== Vgap ====================

    private final DoubleProperty vgap = new StyleableDoubleProperty(DEFAULT_VGAP) {
        @Override
        public CssMetaData<RXGridView<?>, Number> getCssMetaData() {
            return StyleableProperties.VGAP;
        }

        @Override
        public Object getBean() {
            return RXGridView.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between rows. Negative values are treated as zero during
     * layout.
     *
     * @return the vgap property
     */
    public final DoubleProperty vgapProperty() {
        return vgap;
    }

    /**
     * Returns the vertical gap between rows.
     *
     * @return the vgap
     */
    public final double getVgap() {
        return vgap.get();
    }

    /**
     * Sets the vertical gap between rows.
     *
     * @param value the vgap
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount =
            new SimpleIntegerProperty(this, "columnCount", DEFAULT_COLUMN_COUNT);

    /**
     * Forced number of columns. A positive value overrides the automatic count;
     * {@link #AUTO_COLUMNS} (or any value {@code <= 0}) derives the count from
     * {@code cellWidth} and the available width.
     *
     * @return the column-count property
     */
    public final IntegerProperty columnCountProperty() {
        return columnCount;
    }

    /**
     * Returns the forced column count.
     *
     * @return the forced column count, or {@link #AUTO_COLUMNS} for automatic
     */
    public final int getColumnCount() {
        return columnCount.get();
    }

    /**
     * Sets the forced column count.
     *
     * @param value a positive count, or {@link #AUTO_COLUMNS} for automatic
     */
    public final void setColumnCount(int value) {
        columnCount.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns =
            new SimpleIntegerProperty(this, "maxColumns", DEFAULT_MAX_COLUMNS);

    /**
     * Upper bound on the resolved column count. {@link #AUTO_COLUMNS} (or any
     * value {@code <= 0}) means no upper bound. Has no effect when
     * {@code columnCount} forces a count.
     *
     * @return the max-columns property
     */
    public final IntegerProperty maxColumnsProperty() {
        return maxColumns;
    }

    /**
     * Returns the maximum column count.
     *
     * @return the maximum column count, or {@link #AUTO_COLUMNS} for unbounded
     */
    public final int getMaxColumns() {
        return maxColumns.get();
    }

    /**
     * Sets the maximum column count.
     *
     * @param value a positive bound, or {@link #AUTO_COLUMNS} for unbounded
     */
    public final void setMaxColumns(int value) {
        maxColumns.set(value);
    }

    // ==================== Items Justify ====================

    private final ObjectProperty<RXGridJustify> itemsJustify =
            new StyleableObjectProperty<>(DEFAULT_ITEMS_JUSTIFY) {
                @Override
                public CssMetaData<RXGridView<?>, RXGridJustify> getCssMetaData() {
                    return StyleableProperties.ITEMS_JUSTIFY;
                }

                @Override
                public Object getBean() {
                    return RXGridView.this;
                }

                @Override
                public String getName() {
                    return "itemsJustify";
                }
            };

    /**
     * How a row uses its spare horizontal width: position the fixed-width block
     * ({@code START} / {@code CENTER} / {@code END}), grow the gaps
     * ({@code SPACE_BETWEEN} / {@code SPACE_AROUND} / {@code SPACE_EVENLY}) or
     * grow the cells ({@link RXGridJustify#STRETCH}, capped by
     * {@link #maxCellWidthProperty() maxCellWidth}). A {@code null} value is
     * treated as {@link RXGridJustify#START}.
     *
     * @return the items-justify property
     */
    public final ObjectProperty<RXGridJustify> itemsJustifyProperty() {
        return itemsJustify;
    }

    /**
     * Returns the row justification.
     *
     * @return the row justification, possibly {@code null}
     */
    public final RXGridJustify getItemsJustify() {
        return itemsJustify.get();
    }

    /**
     * Sets the row justification.
     *
     * @param value the row justification, or {@code null} for the default
     */
    public final void setItemsJustify(RXGridJustify value) {
        itemsJustify.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * Node shown when the grid has no items. {@code null} shows nothing.
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

    // ==================== Row Count (read-only) ====================

    private final ReadOnlyIntegerWrapper rowCount = new ReadOnlyIntegerWrapper(this, "rowCount", 0);

    /**
     * The number of rows resolved by the most recent layout
     * ({@code ceil(itemCount / actualColumnCount)}).
     *
     * @return the read-only row-count property
     */
    public final ReadOnlyIntegerProperty rowCountProperty() {
        return rowCount.getReadOnlyProperty();
    }

    /**
     * Returns the number of rows resolved by the most recent layout.
     *
     * @return the row count
     */
    public final int getRowCount() {
        return rowCount.get();
    }

    /**
     * Updates the resolved row count. Intended for skins / behaviors.
     *
     * @param value the resolved row count
     */
    public final void setRowCount(int value) {
        rowCount.set(value);
    }

    // ==================== Visible Range (read-only) ====================

    private final ReadOnlyObjectWrapper<RXGridVisibleRange> visibleRange =
            new ReadOnlyObjectWrapper<>(this, "visibleRange", RXGridVisibleRange.EMPTY);

    /**
     * The item / row range currently realized in the viewport, refreshed after
     * each layout pass.
     *
     * @return the read-only visible-range property
     */
    public final ReadOnlyObjectProperty<RXGridVisibleRange> visibleRangeProperty() {
        return visibleRange.getReadOnlyProperty();
    }

    /**
     * Returns the current visible range.
     *
     * @return the visible range, never {@code null}
     */
    public final RXGridVisibleRange getVisibleRange() {
        return visibleRange.get();
    }

    /**
     * Updates the visible range. Intended for skins / behaviors.
     *
     * @param value the visible range; {@code null} is coerced to
     *              {@link RXGridVisibleRange#EMPTY}
     */
    public final void setVisibleRange(RXGridVisibleRange value) {
        visibleRange.set(value == null ? RXGridVisibleRange.EMPTY : value);
    }

    // ==================== Scrolling ====================

    private boolean pendingScroll;
    private int pendingScrollIndex;
    private RXGridScrollAlignment pendingScrollAlignment = RXGridScrollAlignment.START;

    /**
     * Scrolls so the item at {@code index} is visible at the top of the
     * viewport.
     *
     * @param index the item index; out-of-range values are clamped during layout
     */
    public final void scrollTo(int index) {
        scrollTo(index, RXGridScrollAlignment.START);
    }

    /**
     * Scrolls so the given item is visible. Does nothing if the item is not in
     * the list.
     *
     * @param item the item to scroll to
     */
    public final void scrollTo(T item) {
        ObservableList<T> list = getItems();
        if (list == null) {
            return;
        }
        int index = list.indexOf(item);
        if (index >= 0) {
            scrollTo(index);
        }
    }

    /**
     * Scrolls so the item at {@code index} is visible with the given alignment.
     * The request is applied on the next layout pass. In V1 only
     * {@link RXGridScrollAlignment#START} and
     * {@link RXGridScrollAlignment#NEAREST} have distinct behavior;
     * {@code CENTER} and {@code END} behave as {@code START}.
     *
     * @param index     the item index; out-of-range values are clamped during layout
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link RXGridScrollAlignment#START}
     */
    public final void scrollTo(int index, RXGridScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = index;
        pendingScrollAlignment = alignment == null ? RXGridScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Whether a scroll request is waiting to be applied. Intended for skins /
     * behaviors.
     *
     * @return {@code true} if a scroll request is pending
     */
    public final boolean hasPendingScroll() {
        return pendingScroll;
    }

    /**
     * The item index of the pending scroll request. Only meaningful when
     * {@link #hasPendingScroll()} is {@code true}. Intended for skins /
     * behaviors.
     *
     * @return the pending scroll item index
     */
    public final int getPendingScrollIndex() {
        return pendingScrollIndex;
    }

    /**
     * The alignment of the pending scroll request. Intended for skins /
     * behaviors.
     *
     * @return the pending scroll alignment, never {@code null}
     */
    public final RXGridScrollAlignment getPendingScrollAlignment() {
        return pendingScrollAlignment;
    }

    /**
     * Clears the pending scroll request after it has been applied. Intended for
     * skins / behaviors.
     */
    public final void clearPendingScroll() {
        pendingScroll = false;
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXGridView<?>, Number> CELL_WIDTH =
                new CssMetaData<>("-rx-cell-width", SizeConverter.getInstance(), DEFAULT_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.cellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<Number>) node.cellWidthProperty();
                    }
                };

        private static final CssMetaData<RXGridView<?>, Number> CELL_HEIGHT =
                new CssMetaData<>("-rx-cell-height", SizeConverter.getInstance(), DEFAULT_CELL_HEIGHT) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.cellHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<Number>) node.cellHeightProperty();
                    }
                };

        private static final CssMetaData<RXGridView<?>, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXGridView<?>, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXGridView<?>, Number> MAX_CELL_WIDTH =
                new CssMetaData<>("-rx-max-cell-width", SizeConverter.getInstance(), DEFAULT_MAX_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.maxCellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<Number>) node.maxCellWidthProperty();
                    }
                };

        private static final CssMetaData<RXGridView<?>, RXGridJustify> ITEMS_JUSTIFY =
                new CssMetaData<>("-rx-items-justify",
                        new EnumConverter<>(RXGridJustify.class), DEFAULT_ITEMS_JUSTIFY) {
                    @Override
                    public boolean isSettable(RXGridView<?> node) {
                        return !node.itemsJustify.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXGridJustify> getStyleableProperty(RXGridView<?> node) {
                        return (StyleableProperty<RXGridJustify>) node.itemsJustifyProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, CELL_WIDTH, CELL_HEIGHT, MAX_CELL_WIDTH, HGAP, VGAP, ITEMS_JUSTIFY);
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
