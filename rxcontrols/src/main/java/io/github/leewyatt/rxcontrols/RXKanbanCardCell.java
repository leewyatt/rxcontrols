package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXKanbanCardCellSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Card cell for an {@link RXKanbanView}. One cell renders one card; each column's
 * viewport recycles a small pool of cells across its visible rows, so a cell's
 * card and index change as the column scrolls or relays out.
 *
 * <p>A cell knows both the {@link #getColumn() column} it belongs to (injected by
 * the owning column viewport) and the {@link #getKanbanView() board} (for board-
 * level selection / focus and the shared converter). {@link #updateIndex(int)}
 * resolves the card from {@code getColumn().getCards().get(i)}.
 *
 * <p>Content follows the standard {@link javafx.scene.control.Cell} contract:
 * customize by overriding {@link #updateItem(Object, boolean)}, calling
 * {@code super.updateItem(item, empty)} first, clearing {@code text} /
 * {@code graphic} on the empty branch and rendering them otherwise. Sub-nodes
 * must be cached fields — {@code updateItem} runs on every re-bind, not once per
 * card. A bare {@code RXKanbanCardCell} renders nothing — the column viewport's
 * default cell factory supplies the converter-driven text rendering.
 *
 * <p>Full-card content: a resizable {@code graphic} under
 * {@link ContentDisplay#GRAPHIC_ONLY} is resized by the skin to fill the card's
 * content area (min/max respected), so rich cards need no manual width binding.
 *
 * <p>A {@code null} card is a legal value, not an empty cell: emptiness is
 * decided solely by the index.
 *
 * @param <T> the card type
 */
public class RXKanbanCardCell<T> extends IndexedCell<T> {

    // The skin drives the keyboard focus ring through this pseudo-class. It is the
    // same "focused" pseudo-class Cell exposes; because card cells are never
    // focus-traversable, Cell's own Node-focus listener never fires, so this manual
    // toggle is the sole writer.
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

    /**
     * Creates an empty card cell.
     */
    public RXKanbanCardCell() {
        getStyleClass().add("rx-kanban-card-cell");
        setAccessibleRole(AccessibleRole.LIST_ITEM);
        // Cells are not Tab stops — the board is the single focus owner — so they
        // never receive Node focus and the focus ring above stays under skin control.
        setFocusTraversable(false);
    }

    // ==================== Accessibility ====================

    /**
     * Reports this cell's index and single-selection state to assistive
     * technologies; other attributes defer to the superclass. Only the realized
     * (visible) cells are exposed — the self-built viewport keeps no off-screen
     * accessibility peers.
     *
     * @param attribute  the requested accessible attribute
     * @param parameters optional attribute parameters
     * @return the attribute value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case INDEX -> getIndex();
            case SELECTED -> isSelected();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    // ==================== Column ====================

    private final ReadOnlyObjectWrapper<RXKanbanColumn<T>> column =
            new ReadOnlyObjectWrapper<>(this, "column");

    /**
     * The column this cell currently renders a card of.
     *
     * @return the read-only column property
     */
    public final ReadOnlyObjectProperty<RXKanbanColumn<T>> columnProperty() {
        return column.getReadOnlyProperty();
    }

    /**
     * Returns the column this cell belongs to.
     *
     * @return the owning column, or {@code null} if not attached
     */
    public final RXKanbanColumn<T> getColumn() {
        return column.get();
    }

    /**
     * Updates the owning column. Intended for the column viewport that hosts this
     * cell; not for application code. Called once when the cell is created.
     *
     * @param column the owning column
     */
    public final void updateColumn(RXKanbanColumn<T> column) {
        this.column.set(column);
    }

    // ==================== Kanban View ====================

    private final ReadOnlyObjectWrapper<RXKanbanView<T>> kanbanView =
            new ReadOnlyObjectWrapper<>(this, "kanbanView");

    /**
     * The kanban view that owns this cell.
     *
     * @return the read-only kanban-view property
     */
    public final ReadOnlyObjectProperty<RXKanbanView<T>> kanbanViewProperty() {
        return kanbanView.getReadOnlyProperty();
    }

    /**
     * Returns the kanban view that owns this cell.
     *
     * @return the owning kanban view, or {@code null} if not attached
     */
    public final RXKanbanView<T> getKanbanView() {
        return kanbanView.get();
    }

    /**
     * Updates the owning kanban view. Intended for the column viewport that hosts
     * this cell; not for application code. Called once when the cell is created.
     *
     * @param kanbanView the owning kanban view
     */
    public final void updateKanbanView(RXKanbanView<T> kanbanView) {
        this.kanbanView.set(kanbanView);
    }

    // ==================== Focus ring ====================

    /**
     * Sets the keyboard focus ring on this cell. Intended for the skin / viewport;
     * the cell carries the focus ring for the card it currently renders, re-applied
     * whenever the cell is recycled to another index.
     *
     * @param focused whether this cell holds the board's keyboard focus
     */
    public final void updateCardFocus(boolean focused) {
        pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
    }

    // ==================== Item resolution ====================

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the cell's card from the owning column's cards list at index
     * {@code i} and pushes it through {@link #updateItem(Object, boolean)}.
     * Emptiness is decided by the index alone — {@code i < 0} or out of the list's
     * bounds — so a {@code null} stored at a valid index is delivered as a
     * non-empty {@code null} card.
     */
    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        RXKanbanColumn<T> owningColumn = getColumn();
        ObservableList<T> cards = (owningColumn == null) ? null : owningColumn.getCards();
        boolean empty = (cards == null) || i < 0 || i >= cards.size();
        T item = empty ? null : cards.get(i);
        updateItem(item, empty);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The standard customization point, per the {@link javafx.scene.control.Cell}
     * contract: override, call {@code super.updateItem(item, empty)} first, clear
     * {@code text} / {@code graphic} on the empty branch and render them otherwise
     * (with cached sub-nodes — this runs on every re-bind). This base implementation
     * never touches text or graphic; it only resets a recycled slot's framework
     * state (the board re-applies real {@code :selected} / focus to a re-bound
     * visible cell right after {@link #updateIndex(int)}).
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            updateSelected(false);
            updateCardFocus(false);
        }
    }

    /**
     * Resolves the primary text for {@code item} through the kanban view's
     * converter, falling back to {@code item.toString()} (and the empty string for
     * a {@code null} card) when no converter is set. For use by subclasses
     * overriding {@link #updateItem(Object, boolean)}.
     *
     * @param item the card to render
     * @return the primary text, never {@code null}
     */
    protected final String primaryText(T item) {
        RXKanbanView<T> view = getKanbanView();
        StringConverter<T> converter = view == null ? null : view.getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXKanbanCardCellSkin<>(this);
    }

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(RXRipplePane.DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXKanbanCardCell.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the press ripple and the hover state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; the user-agent stylesheet points it
     * at the {@code -rx-state-overlay-color} token.
     *
     * @return the ripple-fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill, or {@code null} for no fill
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Ripple Opacity ====================

    private final DoubleProperty rippleOpacity =
            new StyleableDoubleProperty(RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.RIPPLE_OPACITY;
                }

                @Override
                public Object getBean() {
                    return RXKanbanCardCell.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for the press ripple and state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
     *
     * @return the ripple-opacity property
     */
    public final DoubleProperty rippleOpacityProperty() {
        return rippleOpacity;
    }

    /**
     * Returns the ripple opacity.
     *
     * @return the ripple opacity
     */
    public final double getRippleOpacity() {
        return rippleOpacity.get();
    }

    /**
     * Sets the ripple opacity.
     *
     * @param value the ripple opacity
     */
    public final void setRippleOpacity(double value) {
        rippleOpacity.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXKanbanCardCell.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether pressing the card creates a ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_ENABLED}.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether ripple interaction is enabled.
     *
     * @return whether ripple interaction is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether ripple interaction is enabled.
     *
     * @param value {@code true} to enable ripple interaction
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXKanbanCardCell.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether hovering the card paints a state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the hover state overlay is enabled.
     *
     * @return whether the state overlay is enabled
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the hover state overlay is enabled.
     *
     * @param value {@code true} to enable the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXKanbanCardCell<?>, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXKanbanCardCell<?> cell) {
                        return !cell.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXKanbanCardCell<?> cell) {
                        return (StyleableProperty<Paint>) cell.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXKanbanCardCell<?>, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXKanbanCardCell<?> cell) {
                        return !cell.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanCardCell<?> cell) {
                        return (StyleableProperty<Number>) cell.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXKanbanCardCell<?>, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXKanbanCardCell<?> cell) {
                        return !cell.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXKanbanCardCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXKanbanCardCell<?>, Boolean> STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXKanbanCardCell<?> cell) {
                        return !cell.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXKanbanCardCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.stateOverlayEnabledProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(IndexedCell.getClassCssMetaData());
            Collections.addAll(styleables, RIPPLE_FILL, RIPPLE_OPACITY, RIPPLE_ENABLED, STATE_OVERLAY_ENABLED);
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
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
