package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXChipSetSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A wrapping group of {@link RXChip} nodes with an optional selection model
 * (none / single / multiple, optionally requiring a selection). Display-and-select only — it
 * has no text editor and no autocomplete; for tag entry use {@code RXChipInput}.
 *
 * <p>A {@link Control} whose skin composes an
 * {@link io.github.leewyatt.rxcontrols.layout.RXFlowPane} for the wrap layout.
 * Selection is coordinated at the control layer from each chip's
 * {@link RXChip#selectedProperty() selected} state (no JavaFX {@code ToggleGroup}):
 * in {@link SelectionMode#SINGLE} selecting one chip deselects the rest; in
 * {@link SelectionMode#MULTIPLE} any number may be selected;
 * {@link #allowEmptySelectionProperty() allowEmptySelection}{@code =false} prevents
 * deselecting the last selected chip. The read-only
 * {@link #selectedChipsProperty() selectedChips} tracks the current selection and a
 * {@link RXChipEvent#SELECTION_CHANGED} event fires when it changes.</p>
 *
 * <p>A member chip may also carry a remove affordance: setting a chip
 * {@link RXChip#removableProperty() removable} lets the user delete it from the set
 * (its close button, or DELETE / BACKSPACE on a focused chip). The set removes it from
 * {@link #getChips() chips}; a {@link RXChipEvent#REMOVED} fires whenever a chip leaves
 * the set (from the affordance or a direct {@code chips} mutation). Removal is
 * structural, so it is allowed even for the sole selected chip under
 * {@code allowEmptySelection=false} (that floor guards only user deselection).</p>
 */
public class RXChipSet extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-chip-set";

    /** Default gap between chips (horizontal and vertical). */
    public static final double DEFAULT_GAP = 8.0;

    /** Default block alignment of the wrapped chips. */
    public static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;

    // ==================== Selection Mode ====================

    /**
     * How many chips of the set may be selected at once.
     */
    public enum SelectionMode {
        /** No selection is offered; the set is display-only and clears any selection. */
        NONE,
        /** At most one chip may be selected; selecting one deselects the others. */
        SINGLE,
        /** Any number of chips may be selected. */
        MULTIPLE
    }

    // ==================== Fields ====================

    private final ObservableList<RXChip> chips = FXCollections.observableArrayList();

    /** Reentrancy guard: selection coordination mutates chip states, which re-enter here. */
    private boolean adjusting;

    private final Map<RXChip, InvalidationListener> selectedListeners = new IdentityHashMap<>();

    // ==================== Constructors ====================

    /**
     * Creates an empty chip set.
     */
    public RXChipSet() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        chips.addListener((ListChangeListener<RXChip>) this::onChipsListChanged);
        // Honor a removable chip's remove affordance: a REMOVE bubbling from a chip
        // node deletes it from this set (unless a chip-level handler vetoed it).
        addEventHandler(RXChipEvent.REMOVE, this::onChipRemoveRequested);
    }

    /**
     * Creates a chip set holding the given chips.
     *
     * @param chips the initial chips
     */
    public RXChipSet(RXChip... chips) {
        this();
        getChips().addAll(chips);
    }

    /**
     * Returns the mutable list of chips in this set. Add and remove chips in place.
     *
     * @return the chip list
     */
    public final ObservableList<RXChip> getChips() {
        return chips;
    }

    // ==================== Selection Mode Property ====================

    private final ObjectProperty<SelectionMode> selectionMode =
            new ObjectPropertyBase<>(SelectionMode.NONE) {
                @Override
                protected void invalidated() {
                    enforceSelectionStructure();
                }

                @Override
                public Object getBean() {
                    return RXChipSet.this;
                }

                @Override
                public String getName() {
                    return "selectionMode";
                }
            };

    /**
     * The selection mode. Default {@link SelectionMode#NONE}. Changing to
     * {@code NONE} clears any current selection; changing to {@code SINGLE}
     * collapses any multiple selection to at most one. {@code null} is treated as
     * {@code NONE}.
     *
     * @return the selection mode property
     */
    public final ObjectProperty<SelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /**
     * Returns the selection mode.
     *
     * @return the selection mode, or {@code null}
     */
    public final SelectionMode getSelectionMode() {
        return selectionMode.get();
    }

    /**
     * Sets the selection mode.
     *
     * @param value the selection mode, or {@code null} for the default
     */
    public final void setSelectionMode(SelectionMode value) {
        selectionMode.set(value);
    }

    private SelectionMode selectionModeOrDefault() {
        SelectionMode value = getSelectionMode();
        return value == null ? SelectionMode.NONE : value;
    }

    // ==================== Allow empty selection ====================

    private final BooleanProperty allowEmptySelection = new BooleanPropertyBase(true) {
        @Override
        public Object getBean() {
            return RXChipSet.this;
        }

        @Override
        public String getName() {
            return "allowEmptySelection";
        }
    };

    /**
     * Whether the selection may become empty. With {@code allowEmptySelection}
     * {@code false}, deselecting the last selected chip is reverted so at least one
     * chip stays selected once a selection exists. The guard applies to user
     * <em>deselection</em> only: it never seeds an initial selection, and it does not
     * block structural removal — deleting the sole selected chip (through its remove
     * affordance or a {@link #getChips() chips} mutation) still leaves the selection
     * empty. So an {@code allowEmptySelection=false} set can legitimately sit fully
     * empty: before anything is selected, or after the last selected chip is removed.
     * Ignored in {@link SelectionMode#NONE}.
     *
     * @return the allow-empty-selection property
     */
    public final BooleanProperty allowEmptySelectionProperty() {
        return allowEmptySelection;
    }

    /**
     * Returns whether the selection may become empty.
     *
     * @return whether an empty selection is allowed
     */
    public final boolean isAllowEmptySelection() {
        return allowEmptySelection.get();
    }

    /**
     * Sets whether the selection may become empty.
     *
     * @param value {@code false} to prevent deselecting the last selected chip
     */
    public final void setAllowEmptySelection(boolean value) {
        allowEmptySelection.set(value);
    }

    // ==================== Selected Chips (read-only) ====================

    private final ObservableList<RXChip> selectedChipsBacking = FXCollections.observableArrayList();

    private final ReadOnlyListWrapper<RXChip> selectedChips =
            new ReadOnlyListWrapper<>(this, "selectedChips",
                    FXCollections.unmodifiableObservableList(selectedChipsBacking));

    /**
     * The currently selected chips, in the order they appear in {@link #getChips()}.
     *
     * @return the read-only selected-chips property
     */
    public final ReadOnlyListProperty<RXChip> selectedChipsProperty() {
        return selectedChips.getReadOnlyProperty();
    }

    /**
     * Returns the currently selected chips. The returned list is unmodifiable;
     * change the selection through the chips' {@link RXChip#selectedProperty()}.
     *
     * @return an unmodifiable list of the selected chips
     */
    public final ObservableList<RXChip> getSelectedChips() {
        return selectedChips.getReadOnlyProperty().get();
    }

    // ==================== On Selection Change ====================

    private final ObjectProperty<EventHandler<RXChipEvent>> onSelectionChange =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXChipEvent.SELECTION_CHANGED, get());
                }

                @Override
                public Object getBean() {
                    return RXChipSet.this;
                }

                @Override
                public String getName() {
                    return "onSelectionChange";
                }
            };

    /**
     * The handler invoked when the selection changes (the
     * {@link RXChipEvent#SELECTION_CHANGED} event).
     *
     * @return the on-selection-change property
     */
    public final ObjectProperty<EventHandler<RXChipEvent>> onSelectionChangeProperty() {
        return onSelectionChange;
    }

    /**
     * Returns the on-selection-change handler.
     *
     * @return the on-selection-change handler, or {@code null}
     */
    public final EventHandler<RXChipEvent> getOnSelectionChange() {
        return onSelectionChange.get();
    }

    /**
     * Sets the on-selection-change handler.
     *
     * @param value the on-selection-change handler, or {@code null}
     */
    public final void setOnSelectionChange(EventHandler<RXChipEvent> value) {
        onSelectionChange.set(value);
    }

    // ==================== Removal ====================

    /**
     * Honors a chip's remove affordance. When a {@link RXChip#removableProperty()
     * removable} chip in this set requests removal (its close button, or DELETE /
     * BACKSPACE on a focused chip, fires a vetoable {@link RXChipEvent#REMOVE}), the
     * set removes it from {@link #getChips() chips} — which fires
     * {@link RXChipEvent#REMOVED} and reconciles the selection. A handler that
     * {@link javafx.event.Event#consume() consumes} the {@code REMOVE} on the chip
     * vetoes it — this handler never runs. Removal is structural and ignores
     * {@link #allowEmptySelectionProperty() allowEmptySelection}: deleting the sole
     * selected chip is allowed and simply leaves the selection empty (that floor only
     * guards user deselection, not membership).
     *
     * @param event the remove request bubbling from a chip node
     */
    private void onChipRemoveRequested(RXChipEvent event) {
        RXChip chip = event.getChip();
        if (chip != null && chips.remove(chip)) {
            // The chip-list listener fires REMOVED and reconciles the selection;
            // consume so the request does not bubble past the set.
            event.consume();
        }
    }

    private void onChipsListChanged(ListChangeListener.Change<? extends RXChip> change) {
        // Collect the chips leaving the set — from any cause (the remove affordance or a
        // direct getChips() mutation) — so REMOVED fires for each, matching RXChipInput
        // (the shared event then means the same thing on both controls).
        List<RXChip> removed = new ArrayList<>();
        while (change.next()) {
            if (change.wasRemoved()) {
                removed.addAll(change.getRemoved());
            }
        }
        // Reconcile selection first so a REMOVED handler observes a consistent model
        // (the removed chip already gone from both chips and selectedChips).
        onChipsChanged();
        for (RXChip chip : removed) {
            fireEvent(new RXChipEvent(RXChipEvent.REMOVED, chip, null));
        }
    }

    private final ObjectProperty<EventHandler<RXChipEvent>> onChipRemoved =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXChipEvent.REMOVED, get());
                }

                @Override
                public Object getBean() {
                    return RXChipSet.this;
                }

                @Override
                public String getName() {
                    return "onChipRemoved";
                }
            };

    /**
     * The handler invoked after a chip is removed from the set — through its remove
     * affordance or a direct {@link #getChips() chips} mutation (the
     * {@link RXChipEvent#REMOVED} event).
     *
     * @return the on-chip-removed property
     */
    public final ObjectProperty<EventHandler<RXChipEvent>> onChipRemovedProperty() {
        return onChipRemoved;
    }

    /**
     * Returns the on-chip-removed handler.
     *
     * @return the on-chip-removed handler, or {@code null}
     */
    public final EventHandler<RXChipEvent> getOnChipRemoved() {
        return onChipRemoved.get();
    }

    /**
     * Sets the on-chip-removed handler.
     *
     * @param value the on-chip-removed handler, or {@code null}
     */
    public final void setOnChipRemoved(EventHandler<RXChipEvent> value) {
        onChipRemoved.set(value);
    }

    // ==================== Selection coordination ====================

    private void onChipsChanged() {
        reconcileChipListeners();
        enforceSelectionStructure();
    }

    private void reconcileChipListeners() {
        Iterator<Map.Entry<RXChip, InvalidationListener>> iterator = selectedListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RXChip, InvalidationListener> entry = iterator.next();
            if (!chips.contains(entry.getKey())) {
                entry.getKey().selectedProperty().removeListener(entry.getValue());
                iterator.remove();
            }
        }
        for (RXChip chip : chips) {
            if (!selectedListeners.containsKey(chip)) {
                InvalidationListener listener = observable -> onChipSelectedChanged(chip);
                chip.selectedProperty().addListener(listener);
                selectedListeners.put(chip, listener);
            }
        }
    }

    private void onChipSelectedChanged(RXChip chip) {
        if (adjusting) {
            return;
        }
        SelectionMode mode = selectionModeOrDefault();
        adjusting = true;
        try {
            if (chip.isSelected()) {
                if (mode == SelectionMode.NONE) {
                    chip.setSelected(false);
                } else if (mode == SelectionMode.SINGLE) {
                    for (RXChip other : chips) {
                        if (other != chip) {
                            other.setSelected(false);
                        }
                    }
                }
            } else if (mode != SelectionMode.NONE && !isAllowEmptySelection() && noneSelected()) {
                // Reverting a deselection that would empty a selection where empty is not allowed.
                chip.setSelected(true);
            }
        } finally {
            adjusting = false;
        }
        updateSelectedChips();
    }

    private void enforceSelectionStructure() {
        if (adjusting) {
            return;
        }
        SelectionMode mode = selectionModeOrDefault();
        adjusting = true;
        try {
            if (mode == SelectionMode.NONE) {
                for (RXChip chip : chips) {
                    chip.setSelected(false);
                }
            } else if (mode == SelectionMode.SINGLE) {
                boolean kept = false;
                for (RXChip chip : chips) {
                    if (chip.isSelected()) {
                        if (kept) {
                            chip.setSelected(false);
                        } else {
                            kept = true;
                        }
                    }
                }
            }
        } finally {
            adjusting = false;
        }
        updateSelectedChips();
    }

    private boolean noneSelected() {
        for (RXChip chip : chips) {
            if (chip.isSelected()) {
                return false;
            }
        }
        return true;
    }

    private void updateSelectedChips() {
        List<RXChip> current = new ArrayList<>();
        for (RXChip chip : chips) {
            if (chip.isSelected()) {
                current.add(chip);
            }
        }
        if (!current.equals(getSelectedChips())) {
            selectedChipsBacking.setAll(current);
            fireEvent(new RXChipEvent(RXChipEvent.SELECTION_CHANGED, null, null));
        }
    }

    // ==================== Gaps / alignment (styleable) ====================

    private final DoubleProperty hgap =
            new SimpleStyleableDoubleProperty(StyleableProperties.HGAP, this, "hgap", DEFAULT_GAP);

    /**
     * The horizontal gap between chips in a row. Settable from CSS via
     * {@code -rx-hgap}.
     *
     * @return the hgap property
     */
    public final DoubleProperty hgapProperty() {
        return hgap;
    }

    /**
     * Returns the horizontal gap.
     *
     * @return the horizontal gap
     */
    public final double getHgap() {
        return hgap.get();
    }

    /**
     * Sets the horizontal gap.
     *
     * @param value the horizontal gap
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    private final DoubleProperty vgap =
            new SimpleStyleableDoubleProperty(StyleableProperties.VGAP, this, "vgap", DEFAULT_GAP);

    /**
     * The vertical gap between rows of chips. Settable from CSS via {@code -rx-vgap}.
     *
     * @return the vgap property
     */
    public final DoubleProperty vgapProperty() {
        return vgap;
    }

    /**
     * Returns the vertical gap.
     *
     * @return the vertical gap
     */
    public final double getVgap() {
        return vgap.get();
    }

    /**
     * Sets the vertical gap.
     *
     * @param value the vertical gap
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    private final ObjectProperty<Pos> alignment =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ALIGNMENT, this, "alignment", DEFAULT_ALIGNMENT);

    /**
     * The block alignment of the wrapped chips within the set. Default
     * {@link Pos#TOP_LEFT}. Settable from CSS via {@code -rx-alignment}. {@code null}
     * is treated as the default by the skin.
     *
     * @return the alignment property
     */
    public final ObjectProperty<Pos> alignmentProperty() {
        return alignment;
    }

    /**
     * Returns the block alignment.
     *
     * @return the block alignment, or {@code null}
     */
    public final Pos getAlignment() {
        return alignment.get();
    }

    /**
     * Sets the block alignment.
     *
     * @param value the block alignment, or {@code null} for the default
     */
    public final void setAlignment(Pos value) {
        alignment.set(value);
    }

    // ==================== Control ====================

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXChipSetSkin(this);
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * The chip set wraps chips horizontally, so it reports a horizontal content
     * bias: its height depends on the width it is given.
     *
     * @return {@link Orientation#HORIZONTAL}
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXChipSet, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_GAP) {
                    @Override
                    public boolean isSettable(RXChipSet set) {
                        return !set.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChipSet set) {
                        return (StyleableProperty<Number>) set.hgapProperty();
                    }
                };

        private static final CssMetaData<RXChipSet, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_GAP) {
                    @Override
                    public boolean isSettable(RXChipSet set) {
                        return !set.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChipSet set) {
                        return (StyleableProperty<Number>) set.vgapProperty();
                    }
                };

        private static final CssMetaData<RXChipSet, Pos> ALIGNMENT =
                new CssMetaData<>("-rx-alignment", new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXChipSet set) {
                        return !set.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXChipSet set) {
                        return (StyleableProperty<Pos>) set.alignmentProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(HGAP);
            styleables.add(VGAP);
            styleables.add(ALIGNMENT);
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

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
