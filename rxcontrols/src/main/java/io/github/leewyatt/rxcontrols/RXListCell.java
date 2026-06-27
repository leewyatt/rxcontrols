package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXListCellSkin;
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
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cell for an {@link RXListView}. One cell renders one item; the list view
 * recycles a small pool of cells across the visible rows, so a cell's item and
 * index change as the view scrolls or relays out.
 *
 * <p>The cell is a three-segment row: a fixed leading <em>selection slot</em>
 * (a {@code CheckBox}, a checkmark or empty, chosen automatically from the list
 * view's {@link RXListView#selectionVisualModeProperty() selectionVisualMode}),
 * then the content area, then a trailing area. <strong>The selection slot is
 * managed entirely by the base cell</strong>, so a custom cell automatically has
 * a checkbox in {@code CHECKBOX} mode without wiring anything.
 *
 * <p>To customize the content while keeping the selection slot / checkbox /
 * selection behavior / ripple, subclass and override {@link #createContent(Object)}
 * to return the content node (mirroring {@code RXCascaderCell}); do not override
 * {@link #updateItem(Object, boolean)}. A {@code null} item is a legal value, not
 * an empty cell: emptiness is decided solely by the index (see
 * {@link #updateIndex(int)}).
 *
 * @param <T> the item type
 */
public class RXListCell<T> extends IndexedCell<T> {

    // The skin drives the keyboard focus ring through this pseudo-class. It is the
    // same "focused" pseudo-class Cell exposes; because list cells are never
    // focus-traversable (see the constructor) Cell's own Node-focus listener never
    // fires, so this manual toggle is the sole writer and is not overridden.
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

    private final HBox container = new HBox();
    private final StackPane selectionSlot = new StackPane();
    private final CheckBox checkBox = new CheckBox();
    private final Region checkmark = new Region();
    private final StackPane contentHolder = new StackPane();
    private final VBox textBox = new VBox();
    private final Label primaryLabel = new Label();

    /**
     * Creates an empty list cell.
     */
    public RXListCell() {
        getStyleClass().add("rx-list-cell");
        setAccessibleRole(AccessibleRole.LIST_ITEM);
        // Cells are not Tab stops — the list view is the single focus owner — so
        // they never receive Node focus and the focus ring above stays under skin
        // control.
        setFocusTraversable(false);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        buildStructure();
    }

    private void buildStructure() {
        selectionSlot.getStyleClass().add("selection-slot");
        // The checkbox / checkmark are pure visual + a11y indicators of the single
        // selection state; all pointer toggling is handled centrally by the list
        // view's skin, so they never independently react to the mouse or take
        // keyboard focus. The checkbox simply mirrors this cell's selected state
        // (one-way bind; never user-driven), and the checkmark's visibility follows
        // the :selected pseudo-class via CSS.
        checkBox.setFocusTraversable(false);
        checkBox.setMouseTransparent(true);
        checkBox.setAllowIndeterminate(false);
        checkBox.selectedProperty().bind(selectedProperty());
        checkmark.getStyleClass().add("checkmark");
        checkmark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        checkmark.setMouseTransparent(true);

        textBox.getStyleClass().add("text-box");
        textBox.getChildren().add(primaryLabel);

        contentHolder.getStyleClass().add("content");
        contentHolder.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(contentHolder, Priority.ALWAYS);

        container.getStyleClass().add("container");
        container.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().setAll(selectionSlot, contentHolder);
    }

    // ==================== Accessibility ====================

    /**
     * Reports this cell's index and single-selection state to assistive technologies
     * (mirroring {@code ListCell}); other attributes defer to the superclass. Only the
     * realized (visible) cells are exposed — the self-built viewport keeps no off-screen
     * accessibility peers, so screen readers see the visible window rather than the full
     * item list.
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

    // ==================== List View ====================

    private final ReadOnlyObjectWrapper<RXListView<T>> listView =
            new ReadOnlyObjectWrapper<>(this, "listView");

    /**
     * The list view that owns this cell.
     *
     * @return the read-only list-view property
     */
    public final ReadOnlyObjectProperty<RXListView<T>> listViewProperty() {
        return listView.getReadOnlyProperty();
    }

    /**
     * Returns the list view that owns this cell.
     *
     * @return the owning list view, or {@code null} if not attached
     */
    public final RXListView<T> getListView() {
        return listView.get();
    }

    /**
     * Updates the owning list view. Intended for the skin / viewport that hosts
     * this cell; not for application code. Called once when the cell is created.
     *
     * @param listView the owning list view
     */
    public final void updateListView(RXListView<T> listView) {
        this.listView.set(listView);
    }

    // ==================== Focus ring ====================

    /**
     * Sets the keyboard focus ring on this cell. Intended for the skin / viewport;
     * the cell carries the focus ring for the item it currently renders, re-applied
     * whenever the cell is recycled to another index.
     *
     * @param focused whether this cell holds the keyboard focus
     */
    public final void updateListFocus(boolean focused) {
        pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
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
                    return RXListCell.this;
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
                    return RXListCell.this;
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
                    return RXListCell.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether pressing the row creates a ripple. Initial value is
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
                    return RXListCell.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the low-opacity state overlay shows while the pointer is inside the
     * row. Initial value is {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state overlay may show.
     *
     * @return whether the state overlay may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state overlay may show.
     *
     * @param value {@code true} to allow the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== Item resolution ====================

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the cell's item from the owning list view's items list at index
     * {@code i} and pushes it through {@link #updateItem(Object, boolean)}.
     * Emptiness is decided by the index alone — {@code i < 0} or out of the list's
     * bounds — so a {@code null} stored at a valid index is delivered as a
     * non-empty {@code null} item.
     */
    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        RXListView<T> view = getListView();
        ObservableList<T> list = (view == null) ? null : view.getItems();
        boolean empty = (list == null) || i < 0 || i >= list.size();
        T item = empty ? null : list.get(i);
        updateItem(item, empty);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Orchestrates the recycled-cell contract: the empty branch fully clears the
     * row (content, selection slot, selection / focus state); the non-empty branch
     * refreshes the selection slot from the visual mode and rebuilds the content via
     * {@link #createContent(Object)}. The selection indicator (checkbox checked /
     * checkmark visible / row highlight) follows the {@code selected} state, applied
     * by the viewport right after {@link #updateIndex(int)}. Override
     * {@link #createContent(Object)}, not this method.
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            // Fully clear a recycled / parked slot (cell-reuse discipline): the
            // viewport re-applies real :selected / focus to a re-bound visible cell
            // right after updateIndex, so a non-empty rebind is unaffected. The
            // checkbox follows selected via its bind; the ripple is cleared by the
            // skin via its index / item listeners.
            setGraphic(null);
            setText(null);
            primaryLabel.setText(null);
            contentHolder.getChildren().clear();
            selectionSlot.getChildren().clear();
            updateSelected(false);
            updateListFocus(false);
            return;
        }
        setGraphic(container);
        setText(null);
        refreshSelectionSlot();
        Node content = createContent(item);
        if (content == null) {
            contentHolder.getChildren().clear();
        } else if (contentHolder.getChildren().size() != 1 || contentHolder.getChildren().get(0) != content) {
            contentHolder.getChildren().setAll(content);
        }
    }

    /**
     * Returns the content node for the given item, rendered in the content area to
     * the right of the selection slot. The default sets the built-in primary label
     * to the item's text (via the list view's {@link RXListView#converterProperty()
     * converter}, falling back to {@code item.toString()}) and returns it. Override
     * to render richer content; returning {@code null} renders an empty content
     * area. Called on each (re)bind, so cache expensive content keyed by item.
     *
     * @param item the item to render (never used as the emptiness signal — empty
     *             cells never reach this method)
     * @return the content node, or {@code null} for none
     */
    protected Node createContent(T item) {
        primaryLabel.setText(primaryText(item));
        return textBox;
    }

    /**
     * Resolves the primary text for {@code item} through the list view's converter,
     * falling back to {@code item.toString()} (and the empty string for a
     * {@code null} item) when no converter is set. For use by subclasses overriding
     * {@link #createContent(Object)}.
     *
     * @param item the item to render
     * @return the primary text, never {@code null}
     */
    protected final String primaryText(T item) {
        RXListView<T> view = getListView();
        StringConverter<T> converter = view == null ? null : view.getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    // ==================== Selection slot ====================

    private void refreshSelectionSlot() {
        switch (effectiveVisualMode()) {
            case CHECKBOX -> {
                setSlotChild(checkBox);
                selectionSlot.setVisible(true);
            }
            case CHECKMARK -> {
                setSlotChild(checkmark);
                selectionSlot.setVisible(true);
            }
            default -> {
                // ROW: keep the slot's reserved width (managed) but invisible, so
                // switching modes does not shift the text left edge.
                setSlotChild(null);
                selectionSlot.setVisible(false);
            }
        }
    }

    private void setSlotChild(Node child) {
        if (child == null) {
            selectionSlot.getChildren().clear();
        } else if (selectionSlot.getChildren().size() != 1 || selectionSlot.getChildren().get(0) != child) {
            selectionSlot.getChildren().setAll(child);
        }
    }

    private RXListSelectionVisualMode effectiveVisualMode() {
        RXListView<T> view = getListView();
        if (view == null) {
            return RXListSelectionVisualMode.ROW;
        }
        return RXListSelectionVisualMode.resolve(view.getSelectionVisualMode(), view.getSelectionMode());
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXListCellSkin<>(this);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXListCell<?>, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXListCell<?> cell) {
                        return !cell.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXListCell<?> cell) {
                        return (StyleableProperty<Paint>) cell.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXListCell<?>, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXListCell<?> cell) {
                        return !cell.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXListCell<?> cell) {
                        return (StyleableProperty<Number>) cell.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXListCell<?>, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXListCell<?> cell) {
                        return !cell.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXListCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXListCell<?>, Boolean> STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXListCell<?> cell) {
                        return !cell.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXListCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.stateOverlayEnabledProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Labeled.getClassCssMetaData());
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
