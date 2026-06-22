package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXTileViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTileViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.CssMetaData;
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
import java.util.Objects;

/**
 * A virtualized, responsive grid of items with optional grouping, selection,
 * keyboard navigation and reorder animation, built on a self-contained viewport
 * (not {@code VirtualFlow}).
 *
 * <p>{@code RXTileView} lays a flat {@link #itemsProperty() items} list out in a
 * uniform grid: it derives the column count from {@link #cellWidthProperty()
 * cellWidth} and the available width (or honors a forced
 * {@link #columnCountProperty() columnCount}), wraps items into rows, and
 * virtualizes by row so only the visible rows hold live cells. Each item is
 * rendered by a {@link RXTileCell} produced by the
 * {@link #cellFactoryProperty() cellFactory}; a {@code null} item is a legal
 * value — empty cells are decided by the index, not by a {@code null} item.
 *
 * <p>When a {@link #sectionKeyFactoryProperty() sectionKeyFactory} is set, runs
 * of adjacent items sharing the same key form sections (the items are not
 * reordered — pass a {@code SortedList} to aggregate), each introduced by a
 * {@link RXTileSectionCell} header. With no factory the view is flat.
 *
 * <p>Within a row, cells of width {@code cellWidth} are separated by
 * {@link #hgapProperty() hgap} and rows by {@link #vgapProperty() vgap}; spare
 * row width is distributed per {@link #itemsJustifyProperty() itemsJustify}.
 * The view publishes read-only metrics after each layout —
 * {@link #actualColumnCountProperty() actualColumnCount},
 * {@link #rowCountProperty() rowCount}, {@link #sectionsProperty() sections},
 * {@link #visibleRangeProperty() visibleRange} and
 * {@link #visibleSectionProperty() visibleSection} — and can scroll to an item
 * or section via the {@code scrollTo*} methods.
 *
 * <p>Selection is held by a {@link #selectionModelProperty() selectionModel}
 * (single by default). The view is a single Tab stop; once focused it navigates
 * with the arrow keys, {@code Home}/{@code End} and {@code Page Up}/{@code Down}
 * (item-by-item, so section headers are skipped), extends a range with
 * {@code Shift}, moves focus without selecting with {@code Shortcut}, toggles with
 * {@code Space}, selects all with {@code Shortcut+A}, and activates the focused
 * item — firing {@link #onActionProperty() onAction} — with {@code Enter} or a
 * double-click. There is no property to switch this off; to disable a key (or all
 * of them), add a consuming filter, e.g.
 * {@code tileView.addEventFilter(KeyEvent.KEY_PRESSED, KeyEvent::consume)}.
 *
 * @param <T> the item type
 */
public class RXTileView<T> extends Control {

    // ==================== Constants ====================

    private static final double DEFAULT_CELL_WIDTH = 100.0;
    private static final double DEFAULT_CELL_HEIGHT = 100.0;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final double DEFAULT_SECTION_HEADER_HEIGHT = 32.0;
    private static final double DEFAULT_MAX_CELL_WIDTH = 0.0;
    private static final int DEFAULT_COLUMN_COUNT = 0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final ItemsJustify DEFAULT_ITEMS_JUSTIFY = ItemsJustify.START;
    private static final boolean DEFAULT_SHOW_SECTION_HEADERS = true;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    private static final String DEFAULT_STYLE_CLASS = "rx-tile-view";

    /**
     * Sentinel for {@link #columnCountProperty() columnCount} and
     * {@link #maxColumnsProperty() maxColumns} meaning "derive automatically": an
     * automatic column count for {@code columnCount}, and no upper bound for
     * {@code maxColumns}. Aligns with {@code RXMasonryPane.AUTO_COLUMNS} and
     * {@code RXGridView.AUTO_COLUMNS}.
     */
    public static final int AUTO_COLUMNS = 0;

    // ==================== Constructors ====================

    /**
     * Creates an empty tile view.
     */
    public RXTileView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleRoleDescription("tile view");
        // The control is a single Tab stop; cells are not focus-traversable.
        setFocusTraversable(true);
        // The field initializer installs the default items list without firing the
        // property's invalidated(), so observe that initial list here.
        observedSectionItems = getItems();
        if (observedSectionItems != null) {
            observedSectionItems.addListener(weakItemsSectionListener);
        }
    }

    /**
     * Creates a tile view backed by the given items.
     *
     * @param items the items to display; may be {@code null}
     */
    public RXTileView(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTileViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    // Section derivation is width-independent (api §4.6), so it lives on the
    // control, not the skin. The control observes its items list directly; the
    // listener is re-pointed on every list swap (detach old, attach new).
    private final ListChangeListener<T> itemsSectionListener = change -> recomputeSections();
    private final WeakListChangeListener<T> weakItemsSectionListener =
            new WeakListChangeListener<>(itemsSectionListener);
    private ObservableList<T> observedSectionItems;

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList()) {
                @Override
                protected void invalidated() {
                    if (observedSectionItems != null) {
                        observedSectionItems.removeListener(weakItemsSectionListener);
                    }
                    observedSectionItems = get();
                    if (observedSectionItems != null) {
                        observedSectionItems.addListener(weakItemsSectionListener);
                    }
                    recomputeSections();
                }
            };

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

    private final ObjectProperty<Callback<RXTileView<T>, RXTileCell<T>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Factory that creates the cells rendering each item. When {@code null}, the
     * view uses a default factory that shows {@code item.toString()}.
     *
     * @return the cell-factory property
     */
    public final ObjectProperty<Callback<RXTileView<T>, RXTileCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return the cell factory, or {@code null} for the default
     */
    public final Callback<RXTileView<T>, RXTileCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value the cell factory, or {@code null} for the default
     */
    public final void setCellFactory(Callback<RXTileView<T>, RXTileCell<T>> value) {
        cellFactory.set(value);
    }

    // ==================== Section Key Factory ====================

    private final ObjectProperty<Callback<T, Object>> sectionKeyFactory =
            new SimpleObjectProperty<>(this, "sectionKeyFactory") {
                @Override
                protected void invalidated() {
                    recomputeSections();
                }
            };

    /**
     * Factory deriving a grouping key from each item. {@code null} (the default)
     * makes the view flat — no sections. Otherwise items are grouped into runs of
     * adjacent equal keys; the items are not reordered, so pass a
     * {@code SortedList} to aggregate scattered keys.
     *
     * @return the section-key-factory property
     */
    public final ObjectProperty<Callback<T, Object>> sectionKeyFactoryProperty() {
        return sectionKeyFactory;
    }

    /**
     * Returns the section-key factory.
     *
     * @return the section-key factory, or {@code null} for a flat view
     */
    public final Callback<T, Object> getSectionKeyFactory() {
        return sectionKeyFactory.get();
    }

    /**
     * Sets the section-key factory.
     *
     * @param value the section-key factory, or {@code null} for a flat view
     */
    public final void setSectionKeyFactory(Callback<T, Object> value) {
        sectionKeyFactory.set(value);
    }

    // ==================== Section Header Factory ====================

    private final ObjectProperty<Callback<RXTileView<T>, RXTileSectionCell>> sectionHeaderFactory =
            new SimpleObjectProperty<>(this, "sectionHeaderFactory");

    /**
     * Factory that creates the section-header cells. When {@code null}, the view
     * uses a default factory that shows the section key as text.
     *
     * @return the section-header-factory property
     */
    public final ObjectProperty<Callback<RXTileView<T>, RXTileSectionCell>> sectionHeaderFactoryProperty() {
        return sectionHeaderFactory;
    }

    /**
     * Returns the section-header factory.
     *
     * @return the section-header factory, or {@code null} for the default
     */
    public final Callback<RXTileView<T>, RXTileSectionCell> getSectionHeaderFactory() {
        return sectionHeaderFactory.get();
    }

    /**
     * Sets the section-header factory.
     *
     * @param value the section-header factory, or {@code null} for the default
     */
    public final void setSectionHeaderFactory(Callback<RXTileView<T>, RXTileSectionCell> value) {
        sectionHeaderFactory.set(value);
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
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "cellWidth";
        }
    };

    /**
     * Width of each cell, in pixels. Drives the automatic column count when
     * {@code columnCount} is automatic. Must be a finite positive number; an
     * illegal value is rejected with {@link IllegalArgumentException} and the
     * property is coerced back to its default (unless bound).
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
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.CELL_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "cellHeight";
        }
    };

    /**
     * Height of each cell, in pixels. Must be a finite positive number; an
     * illegal value is rejected with {@link IllegalArgumentException} and the
     * property is coerced back to its default (unless bound).
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
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "maxCellWidth";
        }
    };

    /**
     * Upper bound on how wide a cell may grow when
     * {@link #itemsJustifyProperty() itemsJustify} is
     * {@link ItemsJustify#STRETCH}. {@code 0} (the default) or any non-positive
     * value means unbounded. Has no effect in the other justification modes,
     * where cells keep {@link #cellWidthProperty() cellWidth}.
     *
     * <p>A cap smaller than {@code cellWidth} is degenerate
     * ({@code max < min}) and is treated as {@code cellWidth}; cells are never
     * shrunk below their configured width by the cap.
     *
     * @return the max-cell-width property
     */
    public final DoubleProperty maxCellWidthProperty() {
        return maxCellWidth;
    }

    /**
     * Returns the maximum cell width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @return the maximum cell width, or {@code 0} for unbounded
     */
    public final double getMaxCellWidth() {
        return maxCellWidth.get();
    }

    /**
     * Sets the maximum cell width used in {@link ItemsJustify#STRETCH} mode.
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
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.HGAP;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between cells in a row. Negative or non-finite values are
     * treated as zero during layout.
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
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.VGAP;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between rows. Negative or non-finite values are treated as
     * zero during layout.
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

    // ==================== Section Header Height ====================

    private final DoubleProperty sectionHeaderHeight = new StyleableDoubleProperty(DEFAULT_SECTION_HEADER_HEIGHT) {
        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_SECTION_HEADER_HEIGHT);
                }
                throw new IllegalArgumentException("sectionHeaderHeight must be a finite positive number");
            }
        }

        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.SECTION_HEADER_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "sectionHeaderHeight";
        }
    };

    /**
     * Fixed height of every section-header row, in pixels (declarative, mirroring
     * {@code -fx-fixed-cell-size}). Must be a finite positive number; an illegal
     * value is rejected with {@link IllegalArgumentException} and the property is
     * coerced back to its default (unless bound).
     *
     * @return the section-header-height property
     */
    public final DoubleProperty sectionHeaderHeightProperty() {
        return sectionHeaderHeight;
    }

    /**
     * Returns the section-header height.
     *
     * @return the section-header height
     */
    public final double getSectionHeaderHeight() {
        return sectionHeaderHeight.get();
    }

    /**
     * Sets the section-header height.
     *
     * @param value the section-header height
     * @throws IllegalArgumentException if {@code value} is not a finite positive number
     */
    public final void setSectionHeaderHeight(double value) {
        sectionHeaderHeight.set(value);
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount =
            new SimpleIntegerProperty(this, "columnCount", DEFAULT_COLUMN_COUNT);

    /**
     * Forced number of columns. A positive value pins the count, switching the
     * view out of automatic derivation but still subject to
     * {@link #maxColumnsProperty() maxColumns}; {@link #AUTO_COLUMNS} (or any
     * value {@code <= 0}) derives the count from {@code cellWidth} and the
     * available width.
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
     * value {@code <= 0}) means no upper bound. Applies to both automatic and
     * forced column counts.
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

    private final ObjectProperty<ItemsJustify> itemsJustify =
            new StyleableObjectProperty<>(DEFAULT_ITEMS_JUSTIFY) {
                @Override
                public CssMetaData<RXTileView<?>, ItemsJustify> getCssMetaData() {
                    return StyleableProperties.ITEMS_JUSTIFY;
                }

                @Override
                public Object getBean() {
                    return RXTileView.this;
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
     * grow the cells ({@link ItemsJustify#STRETCH}, capped by
     * {@link #maxCellWidthProperty() maxCellWidth}). A {@code null} value is
     * treated as {@link ItemsJustify#START}.
     *
     * @return the items-justify property
     */
    public final ObjectProperty<ItemsJustify> itemsJustifyProperty() {
        return itemsJustify;
    }

    /**
     * Returns the row justification.
     *
     * @return the row justification, possibly {@code null}
     */
    public final ItemsJustify getItemsJustify() {
        return itemsJustify.get();
    }

    /**
     * Sets the row justification.
     *
     * @param value the row justification, or {@code null} for the default
     */
    public final void setItemsJustify(ItemsJustify value) {
        itemsJustify.set(value);
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

    // ==================== Show Section Headers ====================

    private final BooleanProperty showSectionHeaders =
            new SimpleBooleanProperty(this, "showSectionHeaders", DEFAULT_SHOW_SECTION_HEADERS);

    /**
     * Whether section headers are shown. Three-state when combined with
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory}: with no factory the
     * view is flat regardless; with a factory and {@code true} sections are
     * computed and headers shown; with a factory and {@code false} sections are
     * still computed (so {@code scrollToSection} / {@code visibleSection} work)
     * but no header rows are rendered.
     *
     * @return the show-section-headers property
     */
    public final BooleanProperty showSectionHeadersProperty() {
        return showSectionHeaders;
    }

    /**
     * Returns whether section headers are shown.
     *
     * @return whether section headers are shown
     */
    public final boolean isShowSectionHeaders() {
        return showSectionHeaders.get();
    }

    /**
     * Sets whether section headers are shown.
     *
     * @param value whether section headers are shown
     */
    public final void setShowSectionHeaders(boolean value) {
        showSectionHeaders.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether cells and section headers glide to their new positions when a change
     * in the column count reflows the grid. Off by default; turning it off while a
     * reorder is in flight snaps every cell to its final position.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether reorder animation is enabled.
     *
     * @return whether reorder animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether reorder animation is enabled.
     *
     * @param value whether reorder animation is enabled
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
                    return RXTileView.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single reorder glide. A {@code null}, non-positive, unknown or
     * indefinite value is accepted and disables animation, exactly like
     * {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the reorder-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the reorder-animation duration.
     *
     * @param value the duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== On Action ====================

    private ObjectProperty<EventHandler<RXTileViewActionEvent<T>>> onAction;

    /**
     * Handler invoked when a tile is activated — by pressing {@code Enter} on the
     * focused tile or double-clicking it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<RXTileViewActionEvent<T>>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXTileViewActionEvent.actionType(), get());
                }

                @Override
                public Object getBean() {
                    return RXTileView.this;
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
    public final EventHandler<RXTileViewActionEvent<T>> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /**
     * Sets the activation handler.
     *
     * @param value the activation handler, or {@code null} for none
     */
    public final void setOnAction(EventHandler<RXTileViewActionEvent<T>> value) {
        onActionProperty().set(value);
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<MultipleSelectionModel<T>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel", new RXTileSelectionModel<>(this));

    /**
     * The selection model. Defaults to a non-null {@link RXTileSelectionModel} in
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

    // ==================== Row Count (read-only) ====================

    private final ReadOnlyIntegerWrapper rowCount = new ReadOnlyIntegerWrapper(this, "rowCount", 0);

    /**
     * The number of visual rows resolved by the most recent layout — data rows
     * plus, when grouping with headers shown, one row per section header.
     *
     * @return the read-only row-count property
     */
    public final ReadOnlyIntegerProperty rowCountProperty() {
        return rowCount.getReadOnlyProperty();
    }

    /**
     * Returns the number of visual rows resolved by the most recent layout.
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

    // ==================== Sections (read-only, control-derived) ====================

    // Mutated only by the control's section derivation (PR3). The wrapper holds an
    // unmodifiable view of it, so neither getSections() nor sectionsProperty().get()
    // can leak a writable handle to the derived state.
    private final ObservableList<RXTileSection> sectionsBacking = FXCollections.observableArrayList();

    private final ReadOnlyListWrapper<RXTileSection> sections = new ReadOnlyListWrapper<>(
            this, "sections", FXCollections.unmodifiableObservableList(sectionsBacking));

    /**
     * The sections derived from the items and the
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory}, in item order. The
     * list is empty when the view is flat (no factory). This metric is
     * width-independent, so it is available without a layout pass.
     *
     * @return the read-only sections property
     */
    public final ReadOnlyListProperty<RXTileSection> sectionsProperty() {
        return sections.getReadOnlyProperty();
    }

    /**
     * Returns the derived sections.
     *
     * @return the sections, never {@code null} (empty when flat)
     */
    public final ObservableList<RXTileSection> getSections() {
        return sections.get();
    }

    /**
     * Recomputes the derived sections from the current items and section-key
     * factory. Adjacent items sharing a key (by {@link Objects#equals}) form one
     * section; the items are not reordered, so a key reappearing after a different
     * key yields a second section. With no factory (or no items) the view is flat
     * and the section list is empty. Every section has at least one item.
     */
    private void recomputeSections() {
        Callback<T, Object> factory = getSectionKeyFactory();
        ObservableList<T> list = getItems();
        if (factory == null || list == null || list.isEmpty()) {
            if (!sectionsBacking.isEmpty()) {
                sectionsBacking.clear();
            }
            return;
        }
        List<RXTileSection> built = new ArrayList<>();
        int size = list.size();
        int runStart = 0;
        Object runKey = factory.call(list.get(0));
        int sectionIndex = 0;
        for (int i = 1; i < size; i++) {
            Object key = factory.call(list.get(i));
            if (!Objects.equals(key, runKey)) {
                built.add(new RXTileSection(runKey, sectionIndex++, runStart, i - runStart));
                runStart = i;
                runKey = key;
            }
        }
        built.add(new RXTileSection(runKey, sectionIndex, runStart, size - runStart));
        sectionsBacking.setAll(built);
    }

    // ==================== Visible Range (read-only) ====================

    private final ReadOnlyObjectWrapper<RXTileVisibleRange> visibleRange =
            new ReadOnlyObjectWrapper<>(this, "visibleRange", RXTileVisibleRange.EMPTY);

    /**
     * The item / row range currently realized in the viewport, refreshed after
     * each layout pass. Replaced wholesale, so listeners never see a torn read.
     *
     * @return the read-only visible-range property
     */
    public final ReadOnlyObjectProperty<RXTileVisibleRange> visibleRangeProperty() {
        return visibleRange.getReadOnlyProperty();
    }

    /**
     * Returns the current visible range.
     *
     * @return the visible range, never {@code null}
     */
    public final RXTileVisibleRange getVisibleRange() {
        return visibleRange.get();
    }

    /**
     * Updates the visible range. Intended for skins / behaviors.
     *
     * @param value the visible range; {@code null} is coerced to
     *              {@link RXTileVisibleRange#EMPTY}
     */
    public final void setVisibleRange(RXTileVisibleRange value) {
        visibleRange.set(value == null ? RXTileVisibleRange.EMPTY : value);
    }

    // ==================== Visible Section (read-only) ====================

    private final ReadOnlyObjectWrapper<RXTileSection> visibleSection =
            new ReadOnlyObjectWrapper<>(this, "visibleSection");

    /**
     * The section at the top of the viewport, or {@code null} when the view is
     * flat or empty. Refreshed after each layout pass.
     *
     * @return the read-only visible-section property
     */
    public final ReadOnlyObjectProperty<RXTileSection> visibleSectionProperty() {
        return visibleSection.getReadOnlyProperty();
    }

    /**
     * Returns the section at the top of the viewport.
     *
     * @return the visible section, or {@code null}
     */
    public final RXTileSection getVisibleSection() {
        return visibleSection.get();
    }

    /**
     * Updates the visible section. Intended for skins / behaviors.
     *
     * @param value the visible section, or {@code null} for none
     */
    public final void setVisibleSection(RXTileSection value) {
        visibleSection.set(value);
    }

    // ==================== Scrolling ====================

    private boolean pendingScroll;
    private int pendingScrollIndex;
    private int pendingScrollSectionIndex = -1;
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
     * Scrolls so the item at {@code index} is visible with the given alignment.
     * The request is applied on the next layout pass.
     *
     * @param index     the item index; out-of-range values are clamped during layout
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link ScrollAlignment#START}
     */
    public final void scrollTo(int index, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = index;
        pendingScrollSectionIndex = -1;
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Scrolls so the given item is visible at the top of the viewport. Does
     * nothing if the item is not in the list.
     *
     * @param item the item to scroll to
     */
    public final void scrollTo(T item) {
        scrollTo(item, ScrollAlignment.START);
    }

    /**
     * Scrolls so the given item is visible with the given alignment. Does nothing
     * if the item is not in the list.
     *
     * @param item      the item to scroll to
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link ScrollAlignment#START}
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
     * Scrolls so the first section with the given key is visible at the top of
     * the viewport. Does nothing if no section matches or the view is flat.
     *
     * @param sectionKey the section key to scroll to, matched by
     *                   {@link Objects#equals(Object, Object)}
     */
    public final void scrollToSection(Object sectionKey) {
        scrollToSection(sectionKey, ScrollAlignment.START);
    }

    /**
     * Scrolls so the first section with the given key is visible with the given
     * alignment. Does nothing if no section matches or the view is flat.
     *
     * @param sectionKey the section key to scroll to, matched by
     *                   {@link Objects#equals(Object, Object)}
     * @param alignment  where the target should land; {@code null} is treated as
     *                   {@link ScrollAlignment#START}
     */
    public final void scrollToSection(Object sectionKey, ScrollAlignment alignment) {
        for (RXTileSection section : getSections()) {
            if (Objects.equals(section.key(), sectionKey)) {
                requestSectionScroll(section, alignment);
                return;
            }
        }
    }

    /**
     * Scrolls so the section at the given index (handling duplicate keys) is
     * visible at the top of the viewport. Does nothing if the index is out of
     * range.
     *
     * @param sectionIndex the section index in {@link #getSections()}
     */
    public final void scrollToSectionIndex(int sectionIndex) {
        scrollToSectionIndex(sectionIndex, ScrollAlignment.START);
    }

    /**
     * Scrolls so the section at the given index is visible with the given
     * alignment. Does nothing if the index is out of range.
     *
     * @param sectionIndex the section index in {@link #getSections()}
     * @param alignment    where the target should land; {@code null} is treated
     *                     as {@link ScrollAlignment#START}
     */
    public final void scrollToSectionIndex(int sectionIndex, ScrollAlignment alignment) {
        List<RXTileSection> list = getSections();
        if (sectionIndex >= 0 && sectionIndex < list.size()) {
            requestSectionScroll(list.get(sectionIndex), alignment);
        }
    }

    private void requestSectionScroll(RXTileSection section, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = section.firstItemIndex();
        pendingScrollSectionIndex = section.sectionIndex();
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
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
     * {@link #hasPendingScroll()} is {@code true}. Intended for skins / behaviors.
     *
     * @return the pending scroll item index
     */
    public final int getPendingScrollIndex() {
        return pendingScrollIndex;
    }

    /**
     * The section index of the pending scroll request, or {@code -1} when the
     * request targets a data item. Intended for skins / behaviors.
     *
     * @return the pending section index, or {@code -1}
     */
    public final int getPendingScrollSectionIndex() {
        return pendingScrollSectionIndex;
    }

    /**
     * The alignment of the pending scroll request. Intended for skins /
     * behaviors.
     *
     * @return the pending scroll alignment, never {@code null}
     */
    public final ScrollAlignment getPendingScrollAlignment() {
        return pendingScrollAlignment;
    }

    /**
     * Clears the pending scroll request after it has been applied. Intended for
     * skins / behaviors.
     */
    public final void clearPendingScroll() {
        pendingScroll = false;
        pendingScrollSectionIndex = -1;
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTileView<?>, Number> CELL_WIDTH =
                new CssMetaData<>("-rx-cell-width", SizeConverter.getInstance(), DEFAULT_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.cellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.cellWidthProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> CELL_HEIGHT =
                new CssMetaData<>("-rx-cell-height", SizeConverter.getInstance(), DEFAULT_CELL_HEIGHT) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.cellHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.cellHeightProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> MAX_CELL_WIDTH =
                new CssMetaData<>("-rx-max-cell-width", SizeConverter.getInstance(), DEFAULT_MAX_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.maxCellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.maxCellWidthProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> SECTION_HEADER_HEIGHT =
                new CssMetaData<>("-rx-section-header-height", SizeConverter.getInstance(),
                        DEFAULT_SECTION_HEADER_HEIGHT) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.sectionHeaderHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.sectionHeaderHeightProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, ItemsJustify> ITEMS_JUSTIFY =
                new CssMetaData<>("-rx-items-justify",
                        new EnumConverter<>(ItemsJustify.class), DEFAULT_ITEMS_JUSTIFY) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.itemsJustify.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ItemsJustify> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<ItemsJustify>) node.itemsJustifyProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, CELL_WIDTH, CELL_HEIGHT, MAX_CELL_WIDTH, HGAP, VGAP,
                    SECTION_HEADER_HEIGHT, ITEMS_JUSTIFY, ANIMATED, ANIMATION_DURATION);
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
