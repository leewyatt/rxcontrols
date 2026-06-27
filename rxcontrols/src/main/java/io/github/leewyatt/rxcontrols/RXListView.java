package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXListViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXListViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A virtualized, single-column list of items with selection, keyboard navigation
 * and type-ahead, built on a self-contained viewport (not {@code VirtualFlow}).
 *
 * <p>{@code RXListView} lays a flat {@link #itemsProperty() items} list out as a
 * uniform single column and virtualizes by row, so only the visible rows hold
 * live cells. Each item is rendered by an {@link RXListCell} produced by the
 * {@link #cellFactoryProperty() cellFactory}; the built-in default cell renders
 * the item's primary text via the {@link #converterProperty() converter}. A
 * {@code null} item is a legal value — empty cells are decided by the index, not
 * by a {@code null} item.
 *
 * <p>Selection is held by a {@link #selectionModelProperty() selectionModel}
 * (single by default; switch with {@link #setSelectionMode(SelectionMode)}). The
 * view is a single Tab stop; once focused it navigates with the arrow keys,
 * {@code Home}/{@code End} and {@code Page Up}/{@code Down}, extends a range with
 * {@code Shift}, moves focus without selecting with {@code Shortcut}, toggles
 * with {@code Space}, selects all with {@code Shortcut+A}, jumps to an item by
 * first-letter type-ahead, and activates the focused item — firing
 * {@link #onActionProperty() onAction} — with {@code Enter} or a double-click.
 *
 * @param <T> the item type
 */
public class RXListView<T> extends Control {

    // ==================== Constants ====================

    /** Default fixed row height, in pixels. */
    public static final double DEFAULT_FIXED_CELL_SIZE = 28.0;

    /** Default estimate for an unmeasured row in variable-height mode, in pixels. */
    public static final double DEFAULT_ESTIMATED_CELL_SIZE = 48.0;

    /** Default section-header row height, in pixels. */
    public static final double DEFAULT_SECTION_HEADER_HEIGHT = 32.0;

    /** Default extra spacing inserted before each section after the first, in pixels. */
    public static final double DEFAULT_SECTION_SPACING = 0.0;

    /** Default for {@link #showSectionHeadersProperty()}. */
    public static final boolean DEFAULT_SHOW_SECTION_HEADERS = true;

    /** Default for {@link #stickySectionHeaderProperty()}. */
    public static final boolean DEFAULT_STICKY_SECTION_HEADER = true;

    private static final String DEFAULT_STYLE_CLASS = "rx-list-view";

    // ==================== Constructors ====================

    /**
     * Creates an empty list view.
     */
    public RXListView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.LIST_VIEW);
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
     * Creates a list view backed by the given items.
     *
     * @param items the items to display; may be {@code null}
     */
    public RXListView(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXListViewSkin<>(this);
    }

    /**
     * Reports whether this list allows multiple selection to assistive technologies
     * (mirroring {@code ListView}); other attributes defer to the superclass. The
     * per-item index and selected state are reported by {@link RXListCell}.
     *
     * @param attribute  the requested accessible attribute
     * @param parameters optional attribute parameters
     * @return the attribute value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case MULTIPLE_SELECTION -> getSelectionMode() == SelectionMode.MULTIPLE;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    // Section derivation is width-independent, so it lives on the control, not the
    // skin. The control observes its items list directly; the listener is re-pointed
    // on every list swap (detach old, attach new) by the items property's invalidated().
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

    private final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Factory that creates the cells rendering each item. When {@code null}, the
     * view uses a default factory that renders the item's primary text via the
     * {@link #converterProperty() converter}.
     *
     * @return the cell-factory property
     */
    public final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return the cell factory, or {@code null} for the default
     */
    public final Callback<RXListView<T>, RXListCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value the cell factory, or {@code null} for the default
     */
    public final void setCellFactory(Callback<RXListView<T>, RXListCell<T>> value) {
        cellFactory.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Converter supplying the primary text of the built-in default cell. When
     * {@code null}, the default cell falls back to {@code item.toString()} (and
     * the empty string for a {@code null} item). A custom
     * {@link #cellFactoryProperty() cellFactory} renders its own content and may
     * ignore this property.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null} for the {@code toString()} fallback
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<MultipleSelectionModel<T>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel", new RXIndexedSelectionModel<>(itemsProperty()));

    /**
     * The selection model. Defaults to a non-null {@link RXIndexedSelectionModel}
     * in single-selection mode; switch to multiple selection via
     * {@link #setSelectionMode(SelectionMode)} or
     * {@code getSelectionModel().setSelectionMode(...)}.
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

    // ==================== Selection cardinality (delegating, no own property) ====================

    /**
     * Convenience pass-through to the current selection model's selection mode;
     * the cardinality axis lives solely on the selection model (no control-level
     * property), mirroring the native {@code ListView} idiom.
     *
     * @return the current selection mode, or {@code null} if there is no model
     */
    public final SelectionMode getSelectionMode() {
        MultipleSelectionModel<T> sm = getSelectionModel();
        return sm == null ? null : sm.getSelectionMode();
    }

    /**
     * Convenience pass-through to the current selection model's selection mode.
     *
     * @param value the selection mode to set
     */
    public final void setSelectionMode(SelectionMode value) {
        MultipleSelectionModel<T> sm = getSelectionModel();
        if (sm != null) {
            sm.setSelectionMode(value);
        }
    }

    // ==================== Selection Visual Mode ====================

    private final ObjectProperty<RXListSelectionVisualMode> selectionVisualMode =
            new SimpleObjectProperty<>(this, "selectionVisualMode", RXListSelectionVisualMode.AUTO);

    /**
     * The visual affordance used to express selection (the "how it looks" axis,
     * orthogonal to the cardinality on the selection model). {@code AUTO} (the
     * default) derives from the cardinality: single selection looks like
     * {@link RXListSelectionVisualMode#ROW ROW}, multiple like
     * {@link RXListSelectionVisualMode#CHECKBOX CHECKBOX}. A {@code null} value is
     * treated as {@code AUTO}. Not styleable: switching to {@code CHECKBOX} changes
     * which model the checked state is written to (a data-semantics change), which a
     * theme must not make.
     *
     * @return the selection-visual-mode property
     */
    public final ObjectProperty<RXListSelectionVisualMode> selectionVisualModeProperty() {
        return selectionVisualMode;
    }

    /**
     * Returns the selection visual mode.
     *
     * @return the selection visual mode, possibly {@code null}
     */
    public final RXListSelectionVisualMode getSelectionVisualMode() {
        return selectionVisualMode.get();
    }

    /**
     * Sets the selection visual mode.
     *
     * @param value the selection visual mode, or {@code null} for {@code AUTO}
     */
    public final void setSelectionVisualMode(RXListSelectionVisualMode value) {
        selectionVisualMode.set(value);
    }

    // ==================== Fixed Cell Size ====================

    private final DoubleProperty fixedCellSize =
            new SimpleDoubleProperty(this, "fixedCellSize", DEFAULT_FIXED_CELL_SIZE);

    /**
     * Fixed height of every row, in pixels (the cell fills it). A positive, finite
     * value enables the uniform fixed-height fast path. A non-positive or non-finite
     * value (for example {@link javafx.scene.layout.Region#USE_COMPUTED_SIZE}) instead
     * enables variable-height mode, where each row is sized to its content's preferred
     * height — matching the {@code <= 0} sentinel of {@link javafx.scene.control.ListView}.
     * In that mode unmeasured rows are sized from {@link #estimatedCellSizeProperty()
     * estimatedCellSize} until they scroll into view and are measured. Sized by the skin
     * rather than CSS so the row geometry stays a single source of truth.
     *
     * @return the fixed-cell-size property
     */
    public final DoubleProperty fixedCellSizeProperty() {
        return fixedCellSize;
    }

    /**
     * Returns the fixed row height.
     *
     * @return the fixed row height
     */
    public final double getFixedCellSize() {
        return fixedCellSize.get();
    }

    /**
     * Sets the fixed row height.
     *
     * @param value the fixed row height
     */
    public final void setFixedCellSize(double value) {
        fixedCellSize.set(value);
    }

    // ==================== Estimated Cell Size ====================

    private final DoubleProperty estimatedCellSize =
            new SimpleDoubleProperty(this, "estimatedCellSize", DEFAULT_ESTIMATED_CELL_SIZE);

    /**
     * Provisional height of a not-yet-measured row in variable-height mode (when
     * {@link #fixedCellSizeProperty() fixedCellSize} is non-positive). It seeds the
     * total content height and the scroll bar before the rows scroll into view and are
     * measured, so a value close to the typical row height keeps the scroll thumb
     * steady; it has no effect on the fixed-height fast path. A non-positive or
     * non-finite value is accepted and resolved to {@link #DEFAULT_ESTIMATED_CELL_SIZE}
     * at layout time.
     *
     * @return the estimated-cell-size property
     */
    public final DoubleProperty estimatedCellSizeProperty() {
        return estimatedCellSize;
    }

    /**
     * Returns the estimated row height used for unmeasured rows in variable-height mode.
     *
     * @return the estimated row height
     */
    public final double getEstimatedCellSize() {
        return estimatedCellSize.get();
    }

    /**
     * Sets the estimated row height used for unmeasured rows in variable-height mode.
     *
     * @param value the estimated row height
     */
    public final void setEstimatedCellSize(double value) {
        estimatedCellSize.set(value);
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

    private ObjectProperty<EventHandler<RXListViewActionEvent<T>>> onAction;

    /**
     * Handler invoked when an item is activated — by pressing {@code Enter} on the
     * focused row or double-clicking it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<RXListViewActionEvent<T>>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXListViewActionEvent.actionType(), get());
                }

                @Override
                public Object getBean() {
                    return RXListView.this;
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
    public final EventHandler<RXListViewActionEvent<T>> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /**
     * Sets the activation handler.
     *
     * @param value the activation handler, or {@code null} for none
     */
    public final void setOnAction(EventHandler<RXListViewActionEvent<T>> value) {
        onActionProperty().set(value);
    }

    // ==================== Sections ====================

    private final ObjectProperty<Callback<T, Object>> sectionKeyFactory =
            new SimpleObjectProperty<>(this, "sectionKeyFactory") {
                @Override
                protected void invalidated() {
                    recomputeSections();
                }
            };

    /**
     * Groups adjacent items that map to the same key into sections, deriving the
     * read-only {@link #sectionsProperty() sections} and enabling section headers.
     * When {@code null} (the default) the view is flat. The items are not
     * reordered, so two non-adjacent runs of the same key form two sections.
     *
     * @return the section-key-factory property
     */
    public final ObjectProperty<Callback<T, Object>> sectionKeyFactoryProperty() {
        return sectionKeyFactory;
    }

    /**
     * Returns the section key factory.
     *
     * @return the section key factory, or {@code null} for a flat view
     */
    public final Callback<T, Object> getSectionKeyFactory() {
        return sectionKeyFactory.get();
    }

    /**
     * Sets the section key factory.
     *
     * @param value the section key factory, or {@code null} for a flat view
     */
    public final void setSectionKeyFactory(Callback<T, Object> value) {
        sectionKeyFactory.set(value);
    }

    private final ObjectProperty<Callback<RXListView<T>, RXListSectionCell>> sectionHeaderFactory =
            new SimpleObjectProperty<>(this, "sectionHeaderFactory");

    /**
     * Factory that creates the section-header cells. When {@code null}, the view
     * uses a default factory that renders the section key as text.
     *
     * @return the section-header-factory property
     */
    public final ObjectProperty<Callback<RXListView<T>, RXListSectionCell>> sectionHeaderFactoryProperty() {
        return sectionHeaderFactory;
    }

    /**
     * Returns the section header factory.
     *
     * @return the section header factory, or {@code null} for the default
     */
    public final Callback<RXListView<T>, RXListSectionCell> getSectionHeaderFactory() {
        return sectionHeaderFactory.get();
    }

    /**
     * Sets the section header factory.
     *
     * @param value the section header factory, or {@code null} for the default
     */
    public final void setSectionHeaderFactory(Callback<RXListView<T>, RXListSectionCell> value) {
        sectionHeaderFactory.set(value);
    }

    private final BooleanProperty showSectionHeaders =
            new SimpleBooleanProperty(this, "showSectionHeaders", DEFAULT_SHOW_SECTION_HEADERS);

    /**
     * Whether section-header rows are rendered. Combined with the
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory}: with no factory the
     * view is flat (no headers); with a factory and {@code true} (the default)
     * headers are rendered; with a factory and {@code false} sections are still
     * computed (so {@link #scrollToSection(Object) scrollToSection} and
     * {@link #visibleSectionProperty() visibleSection} work) but no header rows
     * appear.
     *
     * @return the show-section-headers property
     */
    public final BooleanProperty showSectionHeadersProperty() {
        return showSectionHeaders;
    }

    /**
     * Returns whether section headers are rendered.
     *
     * @return {@code true} if section headers are rendered
     */
    public final boolean isShowSectionHeaders() {
        return showSectionHeaders.get();
    }

    /**
     * Sets whether section headers are rendered.
     *
     * @param value {@code true} to render section headers
     */
    public final void setShowSectionHeaders(boolean value) {
        showSectionHeaders.set(value);
    }

    private final DoubleProperty sectionHeaderHeight =
            new SimpleDoubleProperty(this, "sectionHeaderHeight", DEFAULT_SECTION_HEADER_HEIGHT);

    /**
     * Fixed height of every section-header row, in pixels. A non-positive or
     * non-finite value is accepted and resolved to
     * {@link #DEFAULT_SECTION_HEADER_HEIGHT} at layout time. Sized by the skin
     * rather than CSS so the row geometry stays a single source of truth (matching
     * {@link #fixedCellSizeProperty() fixedCellSize}).
     *
     * @return the section-header-height property
     */
    public final DoubleProperty sectionHeaderHeightProperty() {
        return sectionHeaderHeight;
    }

    /**
     * Returns the section-header row height.
     *
     * @return the section-header row height
     */
    public final double getSectionHeaderHeight() {
        return sectionHeaderHeight.get();
    }

    /**
     * Sets the section-header row height.
     *
     * @param value the section-header row height
     */
    public final void setSectionHeaderHeight(double value) {
        sectionHeaderHeight.set(value);
    }

    private final DoubleProperty sectionSpacing =
            new SimpleDoubleProperty(this, "sectionSpacing", DEFAULT_SECTION_SPACING);

    /**
     * Extra blank space inserted before each section after the first, in pixels. A
     * non-positive or non-finite value is treated as zero at layout time. It only
     * has a visible effect with two or more sections.
     *
     * @return the section-spacing property
     */
    public final DoubleProperty sectionSpacingProperty() {
        return sectionSpacing;
    }

    /**
     * Returns the inter-section spacing.
     *
     * @return the inter-section spacing
     */
    public final double getSectionSpacing() {
        return sectionSpacing.get();
    }

    /**
     * Sets the inter-section spacing.
     *
     * @param value the inter-section spacing
     */
    public final void setSectionSpacing(double value) {
        sectionSpacing.set(value);
    }

    private final BooleanProperty stickySectionHeader =
            new SimpleBooleanProperty(this, "stickySectionHeader", DEFAULT_STICKY_SECTION_HEADER);

    /**
     * Whether the current section's header pins to the top of the viewport as its
     * items scroll under it, handing off to the next header as that section rises
     * into view. Defaults to {@code true} (the common list idiom). Only has an
     * effect when sections are grouped and {@link #showSectionHeadersProperty()
     * showSectionHeaders} is {@code true}.
     *
     * @return the sticky-section-header property
     */
    public final BooleanProperty stickySectionHeaderProperty() {
        return stickySectionHeader;
    }

    /**
     * Returns whether the section header is sticky.
     *
     * @return {@code true} if the section header pins to the top
     */
    public final boolean isStickySectionHeader() {
        return stickySectionHeader.get();
    }

    /**
     * Sets whether the section header is sticky.
     *
     * @param value {@code true} to pin the current section's header to the top
     */
    public final void setStickySectionHeader(boolean value) {
        stickySectionHeader.set(value);
    }

    private final ObservableList<RXListSection> sectionsBacking = FXCollections.observableArrayList();

    private final ReadOnlyListWrapper<RXListSection> sections = new ReadOnlyListWrapper<>(
            this, "sections", FXCollections.unmodifiableObservableList(sectionsBacking));

    /**
     * The sections derived from the items and the
     * {@link #sectionKeyFactoryProperty() sectionKeyFactory}, in item order. Empty
     * when the view is flat. Every section has at least one item.
     *
     * @return the read-only sections property
     */
    public final ReadOnlyListProperty<RXListSection> sectionsProperty() {
        return sections.getReadOnlyProperty();
    }

    /**
     * Returns the derived sections.
     *
     * @return the sections, never {@code null}
     */
    public final ObservableList<RXListSection> getSections() {
        return sections.get();
    }

    // Adjacent items mapping to the same key (by equals) form one section; a
    // non-adjacent run of the same key yields a second section. With no factory (or
    // no items) the view is flat and the section list is empty.
    private void recomputeSections() {
        Callback<T, Object> factory = getSectionKeyFactory();
        ObservableList<T> list = getItems();
        if (factory == null || list == null || list.isEmpty()) {
            if (!sectionsBacking.isEmpty()) {
                sectionsBacking.clear();
            }
            return;
        }
        List<RXListSection> built = new ArrayList<>();
        int size = list.size();
        int runStart = 0;
        Object runKey = factory.call(list.get(0));
        int sectionIndex = 0;
        for (int i = 1; i < size; i++) {
            Object key = factory.call(list.get(i));
            if (!Objects.equals(key, runKey)) {
                built.add(new RXListSection(runKey, sectionIndex++, runStart, i - runStart));
                runStart = i;
                runKey = key;
            }
        }
        built.add(new RXListSection(runKey, sectionIndex, runStart, size - runStart));
        sectionsBacking.setAll(built);
    }

    private final ReadOnlyObjectWrapper<RXListSection> visibleSection =
            new ReadOnlyObjectWrapper<>(this, "visibleSection");

    /**
     * The section at the top of the viewport, or {@code null} when the view is flat
     * or empty. Refreshed after each layout pass.
     *
     * @return the read-only visible-section property
     */
    public final ReadOnlyObjectProperty<RXListSection> visibleSectionProperty() {
        return visibleSection.getReadOnlyProperty();
    }

    /**
     * Returns the section at the top of the viewport.
     *
     * @return the visible section, or {@code null}
     */
    public final RXListSection getVisibleSection() {
        return visibleSection.get();
    }

    /**
     * Updates the visible section. Intended for skins / behaviors.
     *
     * @param value the visible section, or {@code null} for none
     */
    public final void setVisibleSection(RXListSection value) {
        visibleSection.set(value);
    }

    // ==================== Row Count (read-only) ====================

    private final ReadOnlyIntegerWrapper rowCount = new ReadOnlyIntegerWrapper(this, "rowCount", 0);

    /**
     * The number of data rows resolved by the most recent layout (the item count
     * for a flat single-column list).
     *
     * @return the read-only row-count property
     */
    public final ReadOnlyIntegerProperty rowCountProperty() {
        return rowCount.getReadOnlyProperty();
    }

    /**
     * Returns the number of data rows resolved by the most recent layout.
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

    private final ReadOnlyObjectWrapper<RXListVisibleRange> visibleRange =
            new ReadOnlyObjectWrapper<>(this, "visibleRange", RXListVisibleRange.EMPTY);

    /**
     * The item / row range currently realized in the viewport, refreshed when the
     * realized range changes. Immutable snapshots keep listeners from seeing torn
     * reads.
     *
     * @return the read-only visible-range property
     */
    public final ReadOnlyObjectProperty<RXListVisibleRange> visibleRangeProperty() {
        return visibleRange.getReadOnlyProperty();
    }

    /**
     * Returns the current visible range.
     *
     * @return the visible range, never {@code null}
     */
    public final RXListVisibleRange getVisibleRange() {
        return visibleRange.get();
    }

    /**
     * Updates the visible range. Intended for skins / behaviors.
     *
     * @param value the visible range; {@code null} is coerced to
     *              {@link RXListVisibleRange#EMPTY}
     */
    public final void setVisibleRange(RXListVisibleRange value) {
        visibleRange.set(value == null ? RXListVisibleRange.EMPTY : value);
    }

    // ==================== Scrolling ====================

    private boolean pendingScroll;
    // >= 0 : scroll the row at this index per pendingScrollAlignment.
    // -1   : relative scroll by pendingScrollDelta pixels.
    private int pendingScrollIndex = -1;
    // >= 0 : the pending request targets this section (takes priority over the index).
    private int pendingScrollSectionIndex = -1;
    private double pendingScrollDelta;
    private ScrollAlignment pendingScrollAlignment = ScrollAlignment.NEAREST;

    /**
     * Scrolls the minimum distance needed to make the item at {@code index}
     * visible ({@link ScrollAlignment#NEAREST}); does nothing if it is already
     * visible.
     *
     * @param index the item index; out-of-range values are clamped during layout
     */
    public final void scrollTo(int index) {
        scrollTo(index, ScrollAlignment.NEAREST);
    }

    /**
     * Scrolls so the item at {@code index} is visible with the given alignment.
     * The request is applied on the next layout pass. In variable-height mode
     * (see {@link #fixedCellSizeProperty() fixedCellSize}) a target far outside the
     * current window is positioned from provisional row heights (estimated, or measured
     * at a previous width) and refined as the surrounding rows are measured, so
     * {@code CENTER} / {@code END} may land approximately until then.
     *
     * @param index     the item index; out-of-range values are clamped during layout
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link ScrollAlignment#START}
     */
    public final void scrollTo(int index, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = index;
        pendingScrollSectionIndex = -1;
        pendingScrollDelta = 0.0;
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Scrolls the minimum distance needed to make the given item visible
     * ({@link ScrollAlignment#NEAREST}). Does nothing if the item is not in the
     * list.
     *
     * @param item the item to scroll to
     */
    public final void scrollTo(T item) {
        scrollTo(item, ScrollAlignment.NEAREST);
    }

    /**
     * Scrolls so the given item is visible with the given alignment. Does nothing
     * if the item is not in the list (matched by {@code equals}; the first match
     * is used).
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
     * Scrolls so the first section with the given key is visible
     * ({@link ScrollAlignment#START}). Does nothing if no section matches or the
     * view is flat.
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
        for (RXListSection section : getSections()) {
            if (Objects.equals(section.key(), sectionKey)) {
                requestSectionScroll(section, alignment);
                return;
            }
        }
    }

    /**
     * Scrolls so the section at the given index (handling duplicate keys) is
     * visible ({@link ScrollAlignment#START}). Does nothing if the index is out of
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
     * @param alignment    where the target should land; {@code null} is treated as
     *                     {@link ScrollAlignment#START}
     */
    public final void scrollToSectionIndex(int sectionIndex, ScrollAlignment alignment) {
        List<RXListSection> list = getSections();
        if (sectionIndex >= 0 && sectionIndex < list.size()) {
            requestSectionScroll(list.get(sectionIndex), alignment);
        }
    }

    private void requestSectionScroll(RXListSection section, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = section.firstItemIndex();
        pendingScrollSectionIndex = section.sectionIndex();
        pendingScrollDelta = 0.0;
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Scrolls the viewport by a relative pixel delta (positive scrolls down,
     * negative up), clamped to the scrollable range on the next layout pass.
     * Multiple calls before a layout pass accumulate.
     *
     * @param deltaY the signed pixel delta
     */
    public final void scrollBy(double deltaY) {
        pendingScroll = true;
        pendingScrollIndex = -1;
        pendingScrollSectionIndex = -1;
        pendingScrollDelta += deltaY;
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
     * The item index of the pending scroll request, or {@code -1} when the request
     * is a relative-delta scroll. Intended for skins / behaviors.
     *
     * @return the pending scroll item index, or {@code -1}
     */
    public final int getPendingScrollIndex() {
        return pendingScrollIndex;
    }

    /**
     * The section index of the pending scroll request, or {@code -1} when the
     * request does not target a section. Takes priority over
     * {@link #getPendingScrollIndex()} when {@code >= 0}. Intended for skins /
     * behaviors.
     *
     * @return the pending scroll section index, or {@code -1}
     */
    public final int getPendingScrollSectionIndex() {
        return pendingScrollSectionIndex;
    }

    /**
     * The accumulated relative-scroll delta of the pending request, in pixels.
     * Only meaningful when {@link #getPendingScrollIndex()} is {@code -1}.
     * Intended for skins / behaviors.
     *
     * @return the pending relative-scroll delta
     */
    public final double getPendingScrollDelta() {
        return pendingScrollDelta;
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
     * Clears the pending scroll request after it has been applied. Intended for
     * skins / behaviors.
     */
    public final void clearPendingScroll() {
        pendingScroll = false;
        pendingScrollIndex = -1;
        pendingScrollSectionIndex = -1;
        pendingScrollDelta = 0.0;
    }
}
