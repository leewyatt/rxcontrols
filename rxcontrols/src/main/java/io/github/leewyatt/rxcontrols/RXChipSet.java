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
 * (none / single / multiple, optionally mandatory). Display-and-select only — it
 * has no text editor and no autocomplete; for tag entry use {@code RXChipInput}.
 *
 * <p>A {@link Control} whose skin composes an
 * {@link io.github.leewyatt.rxcontrols.layout.RXFlowPane} for the wrap layout.
 * Selection is coordinated at the control layer from each chip's
 * {@link RXChip#selectedProperty() selected} state (no JavaFX {@code ToggleGroup}):
 * in {@link SelectionMode#SINGLE} selecting one chip deselects the rest; in
 * {@link SelectionMode#MULTIPLE} any number may be selected; {@link #mandatoryProperty()
 * mandatory} prevents deselecting the last selected chip. The read-only
 * {@link #selectedChipsProperty() selectedChips} tracks the current selection and a
 * {@link RXChipEvent#SELECTION_CHANGED} event fires when it changes.</p>
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
        chips.addListener((ListChangeListener<RXChip>) change -> onChipsChanged());
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

    // ==================== Mandatory ====================

    private final BooleanProperty mandatory = new BooleanPropertyBase(false) {
        @Override
        public Object getBean() {
            return RXChipSet.this;
        }

        @Override
        public String getName() {
            return "mandatory";
        }
    };

    /**
     * Whether at least one chip must stay selected once a selection exists: with
     * {@code mandatory} true, deselecting the last selected chip is reverted. It
     * does not force an initial selection. Ignored in {@link SelectionMode#NONE}.
     *
     * @return the mandatory property
     */
    public final BooleanProperty mandatoryProperty() {
        return mandatory;
    }

    /**
     * Returns whether a selection is mandatory.
     *
     * @return whether a selection is mandatory
     */
    public final boolean isMandatory() {
        return mandatory.get();
    }

    /**
     * Sets whether a selection is mandatory.
     *
     * @param value {@code true} to prevent deselecting the last chip
     */
    public final void setMandatory(boolean value) {
        mandatory.set(value);
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
            } else if (mode != SelectionMode.NONE && isMandatory() && noneSelected()) {
                // Reverting a deselection that would empty a mandatory selection.
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
