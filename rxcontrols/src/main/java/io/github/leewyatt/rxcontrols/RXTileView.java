package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.TileViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTileViewSkin;
import javafx.animation.Interpolator;
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
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.CssMetaData;
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
 * uniform grid: it derives the column count from {@link #prefTileWidthProperty()
 * prefTileWidth} and the available width, wraps items into rows, and virtualizes by
 * row so only the visible rows hold live cells. Each item is
 * rendered by a {@link RXTileCell} produced by the
 * {@link #cellFactoryProperty() cellFactory}; a {@code null} item is a legal
 * value — empty cells are decided by the index, not by a {@code null} item.
 *
 * <p>When a {@link #sectionKeyFactoryProperty() sectionKeyFactory} is set, runs
 * of adjacent items sharing the same key form sections (the items are not
 * reordered — pass a {@code SortedList} to aggregate), each introduced by a
 * {@link RXTileSectionCell} header. With no factory the view is flat.
 *
 * <p>Within a row, cells target {@code prefTileWidth}, separated by
 * {@link #hgapProperty() hgap} and rows by {@link #vgapProperty() vgap}; spare
 * row width is distributed per {@link #itemsJustifyProperty() itemsJustify}.
 * As the viewport narrows the column count drops before any cell shrinks; only
 * when even a single column is wider than the content area does that lone cell
 * shrink to fit for that layout pass, with the configured gap preserved. The
 * default minimum width only keeps a tiny non-zero content viewport beside the
 * internal scroll bar; applications that require one full target-width column to
 * remain visible can set an explicit minimum width on the control.
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

    private static final double DEFAULT_PREF_TILE_WIDTH = 100.0;
    private static final double DEFAULT_PREF_TILE_HEIGHT = 100.0;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final double DEFAULT_SECTION_HEADER_HEIGHT = 32.0;
    private static final double DEFAULT_SECTION_SPACING = 0.0;
    private static final double DEFAULT_MAX_TILE_WIDTH = 0.0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final ItemsJustify DEFAULT_ITEMS_JUSTIFY = ItemsJustify.START;
    private static final boolean DEFAULT_SHOW_SECTION_HEADERS = true;
    private static final boolean DEFAULT_STICKY_SECTION_HEADER = false;
    private static final boolean DEFAULT_SMOOTH_SCROLLING = true;
    private static final SmoothScrollMode DEFAULT_SMOOTH_SCROLL_MODE = RXSmoothScrollOptions.DEFAULT_MODE;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final String DEFAULT_STYLE_CLASS = "rx-tile-view";

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

    // ==================== Pref Tile Width ====================

    private final DoubleProperty prefTileWidth = new StyleableDoubleProperty(DEFAULT_PREF_TILE_WIDTH) {
        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.PREF_TILE_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "prefTileWidth";
        }
    };

    /**
     * Target width of each tile slot, in pixels — the cell from the
     * {@link #cellFactoryProperty() cellFactory} fills it. Drives the derived
     * column count and preferred width, but is not the control's default minimum
     * width. Must be a concrete positive value: unlike {@code TilePane}'s
     * {@code prefTileWidth}, the {@code USE_COMPUTED_SIZE} auto-size sentinel is
     * not supported (a virtualized view cannot measure every cell). A non-positive
     * or non-finite value (including {@code USE_COMPUTED_SIZE}) is accepted but
     * resolved to the default at layout time.
     *
     * @return the pref-tile-width property
     */
    public final DoubleProperty prefTileWidthProperty() {
        return prefTileWidth;
    }

    /**
     * Returns the preferred tile width.
     *
     * @return the preferred tile width
     */
    public final double getPrefTileWidth() {
        return prefTileWidth.get();
    }

    /**
     * Sets the preferred tile width.
     *
     * @param value the preferred tile width
     */
    public final void setPrefTileWidth(double value) {
        prefTileWidth.set(value);
    }

    // ==================== Pref Tile Height ====================

    private final DoubleProperty prefTileHeight = new StyleableDoubleProperty(DEFAULT_PREF_TILE_HEIGHT) {
        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.PREF_TILE_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "prefTileHeight";
        }
    };

    /**
     * Height of each tile slot, in pixels (the cell fills it). Must be a concrete
     * positive value: like {@link #prefTileWidthProperty() prefTileWidth}, the
     * {@code USE_COMPUTED_SIZE} auto-size sentinel is not supported. A non-positive
     * or non-finite value is accepted but resolved to the default at layout time.
     *
     * @return the pref-tile-height property
     */
    public final DoubleProperty prefTileHeightProperty() {
        return prefTileHeight;
    }

    /**
     * Returns the preferred tile height.
     *
     * @return the preferred tile height
     */
    public final double getPrefTileHeight() {
        return prefTileHeight.get();
    }

    /**
     * Sets the preferred tile height.
     *
     * @param value the preferred tile height
     */
    public final void setPrefTileHeight(double value) {
        prefTileHeight.set(value);
    }

    // ==================== Max Tile Width ====================

    private final DoubleProperty maxTileWidth = new StyleableDoubleProperty(DEFAULT_MAX_TILE_WIDTH) {
        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_TILE_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "maxTileWidth";
        }
    };

    /**
     * Upper bound on how wide a tile may grow when
     * {@link #itemsJustifyProperty() itemsJustify} is
     * {@link ItemsJustify#STRETCH}. {@code 0} (the default) or any non-positive
     * value means unbounded. Has no effect in the other justification modes,
     * where cells normally keep the target {@link #prefTileWidthProperty() prefTileWidth}
     * while space permits.
     *
     * <p>A cap smaller than {@code prefTileWidth} is degenerate
     * ({@code max < min}) and is treated as {@code prefTileWidth}; the cap itself
     * never shrinks cells below their target width. Any justification mode may
     * still shrink cells when the available row width is narrower than the target
     * row width.
     *
     * @return the max-tile-width property
     */
    public final DoubleProperty maxTileWidthProperty() {
        return maxTileWidth;
    }

    /**
     * Returns the maximum tile width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @return the maximum tile width, or {@code 0} for unbounded
     */
    public final double getMaxTileWidth() {
        return maxTileWidth.get();
    }

    /**
     * Sets the maximum tile width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @param value a positive cap, or {@code 0} (or any non-positive value) for
     *              unbounded
     */
    public final void setMaxTileWidth(double value) {
        maxTileWidth.set(value);
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
     * {@code -fx-fixed-cell-size}). A non-positive or non-finite value is accepted
     * and resolved to the default section-header height at layout time.
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
     */
    public final void setSectionHeaderHeight(double value) {
        sectionHeaderHeight.set(value);
    }

    // ==================== Section Spacing ====================

    private final DoubleProperty sectionSpacing = new StyleableDoubleProperty(DEFAULT_SECTION_SPACING) {
        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.SECTION_SPACING;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "sectionSpacing";
        }
    };

    /**
     * Extra blank space inserted before each section after the first, on top of the
     * normal {@link #vgapProperty() vgap}, to strengthen the visual separation
     * between groups. It is added above a section's header (or, when headers are
     * hidden, above the section's first row), never below — so a header always hugs
     * its own content. {@code 0} (the default) means no extra spacing. A negative or
     * non-finite value is treated as zero at layout time. This only has a visible
     * effect when there are two or more sections; a flat view (no
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory}) or a single group never
     * shows it.
     *
     * @return the section-spacing property
     */
    public final DoubleProperty sectionSpacingProperty() {
        return sectionSpacing;
    }

    /**
     * Returns the extra spacing inserted before each section after the first.
     *
     * @return the section spacing
     */
    public final double getSectionSpacing() {
        return sectionSpacing.get();
    }

    /**
     * Sets the extra spacing inserted before each section after the first.
     *
     * @param value the section spacing
     */
    public final void setSectionSpacing(double value) {
        sectionSpacing.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns = new StyleableIntegerProperty(DEFAULT_MAX_COLUMNS) {
        @Override
        public CssMetaData<RXTileView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "maxColumns";
        }
    };

    /**
     * Upper bound on the resolved column count. Any value {@code <= 0} means no
     * upper bound.
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
     * How a row uses its spare horizontal width: position the target-width block
     * ({@code START} / {@code CENTER} / {@code END}), grow the gaps
     * ({@code SPACE_BETWEEN} / {@code SPACE_AROUND} / {@code SPACE_EVENLY}) or
     * grow the cells ({@link ItemsJustify#STRETCH}, capped by
     * {@link #maxTileWidthProperty() maxTileWidth}). A {@code null} value is
     * treated as {@link ItemsJustify#START}. {@code prefTileWidth} is the target
     * track width used for deriving columns and preferred size. When the row is
     * narrower than its target width, all modes shrink cells for that layout
     * pass; when the row has spare width, only {@code STRETCH} grows cells.
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

    // ==================== Sticky Section Header ====================

    private final BooleanProperty stickySectionHeader = new StyleableBooleanProperty(DEFAULT_STICKY_SECTION_HEADER) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.STICKY_SECTION_HEADER;
        }

        @Override
        public Object getBean() {
            return RXTileView.this;
        }

        @Override
        public String getName() {
            return "stickySectionHeader";
        }
    };

    /**
     * Whether the header of the section currently at the top of the viewport sticks
     * there while that section scrolls, sliding up only as the next section's header
     * arrives to replace it (the iOS / MUI "sticky subheader" effect). Off by
     * default. Only has a visible effect when a
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory} is set,
     * {@link #showSectionHeadersProperty() showSectionHeaders} is {@code true} and
     * at least one section exists. While disabled it adds no extra node.
     *
     * @return the sticky-section-header property
     */
    public final BooleanProperty stickySectionHeaderProperty() {
        return stickySectionHeader;
    }

    /**
     * Returns whether the top section header sticks to the viewport top.
     *
     * @return whether the top section header is sticky
     */
    public final boolean isStickySectionHeader() {
        return stickySectionHeader.get();
    }

    /**
     * Sets whether the top section header sticks to the viewport top.
     *
     * @param value whether the top section header is sticky
     */
    public final void setStickySectionHeader(boolean value) {
        stickySectionHeader.set(value);
    }

    // ==================== Smooth Scrolling ====================

    private final BooleanProperty smoothScrolling =
            new SimpleBooleanProperty(this, "smoothScrolling", DEFAULT_SMOOTH_SCROLLING);

    /**
     * Whether indirect wheel input scrolls with a short smooth animation. When
     * disabled, wheel input is applied immediately while keeping the same boundary
     * chaining behavior.
     *
     * @return the smooth-scrolling property
     */
    public final BooleanProperty smoothScrollingProperty() {
        return smoothScrolling;
    }

    /**
     * Returns whether smooth wheel scrolling is enabled.
     *
     * @return {@code true} when smooth scrolling is enabled
     */
    public final boolean isSmoothScrolling() {
        return smoothScrolling.get();
    }

    /**
     * Sets whether smooth wheel scrolling is enabled.
     *
     * @param value {@code true} to enable smooth wheel scrolling
     */
    public final void setSmoothScrolling(boolean value) {
        smoothScrolling.set(value);
    }

    // ==================== Smooth Scroll Mode ====================

    private final ObjectProperty<SmoothScrollMode> smoothScrollMode =
            new SimpleObjectProperty<>(this, "smoothScrollMode", DEFAULT_SMOOTH_SCROLL_MODE);

    /**
     * Smooth animation mode used while smooth scrolling is enabled. A
     * {@code null} value is treated as {@link RXSmoothScrollOptions#DEFAULT_MODE}.
     *
     * @return the smooth-scroll mode property
     */
    public final ObjectProperty<SmoothScrollMode> smoothScrollModeProperty() {
        return smoothScrollMode;
    }

    /**
     * Returns the smooth scroll mode.
     *
     * @return the smooth scroll mode, possibly {@code null}
     */
    public final SmoothScrollMode getSmoothScrollMode() {
        return smoothScrollMode.get();
    }

    /**
     * Sets the smooth scroll mode.
     *
     * @param value the mode, or {@code null} for the default
     */
    public final void setSmoothScrollMode(SmoothScrollMode value) {
        smoothScrollMode.set(value);
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

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the reorder glide. {@code null} falls back to
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

    // ==================== On Action ====================

    private ObjectProperty<EventHandler<TileViewActionEvent<T>>> onAction;

    /**
     * Handler invoked when a tile is activated — by pressing {@code Enter} on the
     * focused tile or double-clicking it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<TileViewActionEvent<T>>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(TileViewActionEvent.actionType(), get());
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
    public final EventHandler<TileViewActionEvent<T>> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /**
     * Sets the activation handler.
     *
     * @param value the activation handler, or {@code null} for none
     */
    public final void setOnAction(EventHandler<TileViewActionEvent<T>> value) {
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
     * The item / row range currently realized in the viewport, refreshed when
     * the realized range changes. Immutable snapshots keep listeners from seeing
     * torn reads.
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

        private static final CssMetaData<RXTileView<?>, Number> PREF_TILE_WIDTH =
                new CssMetaData<>("-rx-pref-tile-width", SizeConverter.getInstance(), DEFAULT_PREF_TILE_WIDTH) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.prefTileWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.prefTileWidthProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> PREF_TILE_HEIGHT =
                new CssMetaData<>("-rx-pref-tile-height", SizeConverter.getInstance(), DEFAULT_PREF_TILE_HEIGHT) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.prefTileHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.prefTileHeightProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> MAX_TILE_WIDTH =
                new CssMetaData<>("-rx-max-tile-width", SizeConverter.getInstance(), DEFAULT_MAX_TILE_WIDTH) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.maxTileWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.maxTileWidthProperty();
                    }
                };

        private static final CssMetaData<RXTileView<?>, Number> MAX_COLUMNS =
                new CssMetaData<>("-rx-max-columns", SizeConverter.getInstance(), DEFAULT_MAX_COLUMNS) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.maxColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.maxColumnsProperty();
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

        private static final CssMetaData<RXTileView<?>, Number> SECTION_SPACING =
                new CssMetaData<>("-rx-section-spacing", SizeConverter.getInstance(), DEFAULT_SECTION_SPACING) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.sectionSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Number>) node.sectionSpacingProperty();
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

        private static final CssMetaData<RXTileView<?>, Boolean> STICKY_SECTION_HEADER =
                new CssMetaData<>("-rx-sticky-section-header", BooleanConverter.getInstance(),
                        DEFAULT_STICKY_SECTION_HEADER) {
                    @Override
                    public boolean isSettable(RXTileView<?> node) {
                        return !node.stickySectionHeader.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXTileView<?> node) {
                        return (StyleableProperty<Boolean>) node.stickySectionHeaderProperty();
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
            Collections.addAll(styleables, PREF_TILE_WIDTH, PREF_TILE_HEIGHT, MAX_TILE_WIDTH, MAX_COLUMNS, HGAP, VGAP,
                    SECTION_HEADER_HEIGHT, SECTION_SPACING, ITEMS_JUSTIFY, STICKY_SECTION_HEADER, ANIMATED,
                    ANIMATION_DURATION);
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
