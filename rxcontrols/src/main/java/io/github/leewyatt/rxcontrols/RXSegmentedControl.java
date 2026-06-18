package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSegmentedControlSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A one-of-many segmented selector: a horizontal strip of segments where a
 * single sliding indicator marks the current selection. It produces a
 * {@code value} (and read-only {@code selectedIndex}/{@code selectedItem}); it
 * is not a tab container and does not host page content.
 *
 * <p><b>Selection model.</b> {@link #valueProperty() value} is the writable,
 * bindable main truth. {@link #selectedIndexProperty() selectedIndex}
 * ({@code -1} when nothing is selected) and {@link #selectedItemProperty()
 * selectedItem} ({@code null} when nothing is selected) are read-only
 * projections. When the value changes it is resolved to the first item whose
 * value is equal; a {@code null} value clears the selection (it does not match
 * null-valued items); a non-null foreign value not present in the items is
 * preserved as-is with {@code selectedIndex == -1}. Item values should be
 * unique: with duplicate values the first match wins, and re-setting the value
 * of the already-selected segment keeps that segment (an unchanged value
 * triggers no re-resolution).
 *
 * <p><b>{@link #allowEmptySelectionProperty() allowEmptySelection}</b> defaults
 * to {@code false} (radio semantics): the control will not become empty through
 * user interaction, item removal or initialization — clicking the selected
 * segment is a no-op, removing the selected item recovers to the nearest
 * enabled segment, and an unset control selects its first enabled segment.
 * Programmatic clearing ({@code setValue(null)} / {@link #clearSelection()}) and
 * foreign values are still honored regardless of this flag. When {@code true},
 * clicking the selected segment deselects it.
 *
 * @param <T> application value type
 */
@DefaultProperty("items")
public class RXSegmentedControl<T> extends Control {

    // ==================== Constants ====================

    /**
     * Default sliding animation duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    /**
     * Default value of {@code animated}.
     */
    private static final boolean DEFAULT_ANIMATED = true;
    /**
     * Default value of {@code block}.
     */
    private static final boolean DEFAULT_BLOCK = false;
    /**
     * Default value of {@code equalSegmentWidth}.
     */
    private static final boolean DEFAULT_EQUAL_SEGMENT_WIDTH = false;
    /**
     * Default value of {@code allowEmptySelection}.
     */
    private static final boolean DEFAULT_ALLOW_EMPTY_SELECTION = false;
    /**
     * Default segment spacing.
     */
    private static final double DEFAULT_SEGMENT_SPACING = 0.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-segmented-control";

    // ==================== Selection coordination state ====================

    /**
     * Re-entrancy guard: while {@code true}, the {@code value} property's
     * {@code invalidated()} skips re-resolution because the change originates
     * from the index-authoritative path setting all three properties together.
     */
    private boolean adjustingSelection;

    /**
     * Becomes {@code true} once any explicit selection write happens
     * ({@code setValue}/{@code select}/{@code selectIndex}/{@code clearSelection}).
     * Gates the one-shot "auto-select first enabled segment" so a programmatic
     * clear or foreign value is never resurrected by a later items change.
     */
    private boolean selectionExplicit;

    // ==================== Constructors ====================

    /**
     * Creates an empty segmented control.
     */
    public RXSegmentedControl() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.RADIO_BUTTON);
        // Single focus stop; the skin moves the selection between segments via
        // the arrow / Home / End keys.
        setFocusTraversable(true);
        items.addListener((ListChangeListener<RXSegmentedItem<T>>) change -> onItemsChanged());
        onItemsChanged();
    }

    /**
     * Creates a segmented control backed by the given items list.
     *
     * @param items backing items list; must not be {@code null}
     */
    public RXSegmentedControl(ObservableList<RXSegmentedItem<T>> items) {
        this();
        this.items.setAll(items);
    }

    /**
     * Creates a segmented control with the given items.
     *
     * @param items the initial items
     */
    @SafeVarargs
    public RXSegmentedControl(RXSegmentedItem<T>... items) {
        this();
        this.items.setAll(items);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSegmentedControlSkin<>(this);
    }

    // ==================== Items ====================

    private final ObservableList<RXSegmentedItem<T>> items = FXCollections.observableArrayList();

    /**
     * The mutable list of segments.
     *
     * @return items list
     */
    public final ObservableList<RXSegmentedItem<T>> getItems() {
        return items;
    }

    // ==================== Value ====================

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value") {
        @Override
        protected void invalidated() {
            if (adjustingSelection) {
                return;
            }
            // External write (setValue / bind / CSS / FXML): value is authoritative.
            selectionExplicit = true;
            resolveValueToSelection();
        }
    };

    /**
     * The current value. Writable and bindable; the main selection truth. See
     * the class description for resolution and foreign-value semantics.
     *
     * @return value property
     */
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    /**
     * Returns the current value.
     *
     * @return current value, may be {@code null}
     */
    public final T getValue() {
        return value.get();
    }

    /**
     * Sets the current value.
     *
     * @param value the value, may be {@code null}
     */
    public final void setValue(T value) {
        this.value.set(value);
    }

    // ==================== Selected Index ====================

    private final ReadOnlyIntegerWrapper selectedIndex =
            new ReadOnlyIntegerWrapper(this, "selectedIndex", -1);

    /**
     * Index of the selected segment, or {@code -1} when nothing is selected.
     * Read-only projection of the selection.
     *
     * @return read-only selected-index property
     */
    public final ReadOnlyIntegerProperty selectedIndexProperty() {
        return selectedIndex.getReadOnlyProperty();
    }

    /**
     * Returns the selected index, or {@code -1}.
     *
     * @return selected index
     */
    public final int getSelectedIndex() {
        return selectedIndex.get();
    }

    // ==================== Selected Item ====================

    private final ReadOnlyObjectWrapper<RXSegmentedItem<T>> selectedItem =
            new ReadOnlyObjectWrapper<>(this, "selectedItem", null);

    /**
     * The selected item, or {@code null} when nothing is selected. Read-only
     * projection of the selection.
     *
     * @return read-only selected-item property
     */
    public final ReadOnlyObjectProperty<RXSegmentedItem<T>> selectedItemProperty() {
        return selectedItem.getReadOnlyProperty();
    }

    /**
     * Returns the selected item, or {@code null}.
     *
     * @return selected item
     */
    public final RXSegmentedItem<T> getSelectedItem() {
        return selectedItem.get();
    }

    // ==================== Allow Empty Selection ====================

    private final BooleanProperty allowEmptySelection =
            new SimpleBooleanProperty(this, "allowEmptySelection", DEFAULT_ALLOW_EMPTY_SELECTION);

    /**
     * Whether the control may have no selection through interaction. Defaults to
     * {@code false} (radio semantics). See the class description for its exact
     * scope.
     *
     * @return allow-empty-selection property
     */
    public final BooleanProperty allowEmptySelectionProperty() {
        return allowEmptySelection;
    }

    /**
     * Returns whether empty selection is allowed.
     *
     * @return {@code true} if empty selection is allowed
     */
    public final boolean isAllowEmptySelection() {
        return allowEmptySelection.get();
    }

    /**
     * Sets whether empty selection is allowed.
     *
     * @param value {@code true} to allow empty selection
     */
    public final void setAllowEmptySelection(boolean value) {
        allowEmptySelection.set(value);
    }

    // ==================== Selection commands ====================

    /**
     * Selects the first item whose value equals {@code value}. A {@code null}
     * argument clears the selection (it does not match null-valued items); a
     * non-null value not present in the items is preserved with no selected
     * index. Equivalent to {@code setValue(value)} for non-null values.
     *
     * @param value the value to select, may be {@code null}
     */
    public final void select(T value) {
        if (value == null) {
            clearSelection();
            return;
        }
        setValue(value);
    }

    /**
     * Selects the segment at the given index. {@code -1} clears the selection;
     * an out-of-range index is ignored. May target a disabled segment
     * (programmatic selection is not restricted by disabled state).
     *
     * @param index the segment index, or {@code -1} to clear
     */
    public final void selectIndex(int index) {
        if (index == -1) {
            clearSelection();
            return;
        }
        if (index < 0 || index >= items.size()) {
            return;
        }
        selectionExplicit = true;
        applySelection(index);
    }

    /**
     * Clears the selection. Always honored regardless of
     * {@link #allowEmptySelectionProperty() allowEmptySelection}.
     */
    public final void clearSelection() {
        selectionExplicit = true;
        clearSelectionInternal();
    }

    // ==================== Selection internals ====================

    /**
     * Index-authoritative update: sets index, item and value consistently. The
     * {@code adjustingSelection} guard prevents the {@code value} listener from
     * re-resolving (which would defeat index authority for duplicate values).
     * The guard is saved and restored rather than hard-reset so a re-entrant
     * selection write from a {@code selectedIndex}/{@code selectedItem} listener
     * cannot clear it while this outer frame is still running.
     */
    private void applySelection(int index) {
        boolean previous = adjustingSelection;
        adjustingSelection = true;
        try {
            selectedIndex.set(index);
            // Re-read the index before deriving item and value: a re-entrant
            // listener on the read-only selectedIndex may have redirected the
            // selection. All three properties derive from the same re-read index
            // so they stay consistent (RT-32139 defense).
            int current = selectedIndex.get();
            RXSegmentedItem<T> item = isValidIndex(current) ? items.get(current) : null;
            selectedItem.set(item);
            setValueInternal(item == null ? null : item.getValue());
        } finally {
            adjustingSelection = previous;
        }
    }

    private void clearSelectionInternal() {
        boolean previous = adjustingSelection;
        adjustingSelection = true;
        try {
            setValueInternal(null);
            selectedIndex.set(-1);
            selectedItem.set(null);
        } finally {
            adjustingSelection = previous;
        }
    }

    /**
     * Writes {@code value} from an index-authoritative path. The trailing read
     * re-validates the property: {@code invalidated()} only fires on a
     * valid-to-invalid transition, so without this a later external
     * {@code setValue} would not re-trigger resolution.
     */
    private void setValueInternal(T newValue) {
        value.set(newValue);
        value.get();
    }

    /**
     * Value-authoritative update: derives index/item from the current value
     * without touching value (it is the source).
     */
    private void resolveValueToSelection() {
        boolean previous = adjustingSelection;
        adjustingSelection = true;
        try {
            T current = getValue();
            int index = (current == null) ? -1 : indexOfValue(current);
            setDerivedSelection(index);
        } finally {
            adjustingSelection = previous;
        }
    }

    private void setDerivedSelection(int index) {
        selectedIndex.set(index);
        int current = selectedIndex.get();
        selectedItem.set(isValidIndex(current) ? items.get(current) : null);
    }

    /**
     * Maintains the selection invariants after a structural items change:
     * anchors on the selected item's identity when present, recovers to the
     * nearest enabled segment when the selected item was removed, otherwise
     * re-resolves from the current value and applies the one-shot initial
     * selection.
     */
    private void onItemsChanged() {
        RXSegmentedItem<T> selected = getSelectedItem();
        if (selected != null) {
            int index = items.indexOf(selected);
            if (index >= 0) {
                // Still present (possibly drifted / permuted): re-anchor the index.
                applySelection(index);
                return;
            }
            // Selected item was removed.
            if (!isAllowEmptySelection()) {
                int recovered = findNearestEnabled(getSelectedIndex());
                if (recovered >= 0) {
                    applySelection(recovered);
                    return;
                }
            }
            clearSelectionInternal();
            return;
        }
        T current = getValue();
        int index = (current == null) ? -1 : indexOfValue(current);
        if (index >= 0) {
            applySelection(index);
            return;
        }
        if (current == null && !isAllowEmptySelection() && !selectionExplicit) {
            int first = firstEnabledIndex();
            if (first >= 0) {
                applySelection(first);
                return;
            }
        }
        setDerivedSelection(-1);
    }

    private int indexOfValue(T target) {
        for (int i = 0; i < items.size(); i++) {
            RXSegmentedItem<T> item = items.get(i);
            if (item != null && Objects.equals(target, item.getValue())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < items.size();
    }

    private boolean isEnabled(int index) {
        RXSegmentedItem<T> item = items.get(index);
        return item != null && !item.isDisable();
    }

    private int firstEnabledIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (isEnabled(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Searches outward from {@code fromIndex} for the nearest enabled segment,
     * preferring forward then backward (TabPane-style recovery).
     */
    private int findNearestEnabled(int fromIndex) {
        int count = items.size();
        if (count == 0) {
            return -1;
        }
        int start = Math.min(Math.max(fromIndex, 0), count - 1);
        for (int offset = 0; offset < count; offset++) {
            int forward = start + offset;
            if (forward < count && isEnabled(forward)) {
                return forward;
            }
            int backward = start - offset;
            if (offset > 0 && backward >= 0 && isEnabled(backward)) {
                return backward;
            }
        }
        return -1;
    }

    // ==================== Block ====================

    private final BooleanProperty block = new SimpleBooleanProperty(this, "block", DEFAULT_BLOCK);

    /**
     * Whether the strip stretches to fill the available width, dividing it
     * equally among segments.
     *
     * @return block property
     */
    public final BooleanProperty blockProperty() {
        return block;
    }

    /**
     * Returns whether block mode is enabled.
     *
     * @return {@code true} if block mode is enabled
     */
    public final boolean isBlock() {
        return block.get();
    }

    /**
     * Sets whether block mode is enabled.
     *
     * @param value {@code true} to enable block mode
     */
    public final void setBlock(boolean value) {
        block.set(value);
    }

    // ==================== Equal Segment Width ====================

    private final BooleanProperty equalSegmentWidth =
            new SimpleBooleanProperty(this, "equalSegmentWidth", DEFAULT_EQUAL_SEGMENT_WIDTH);

    /**
     * Whether all segments share the width of the widest segment, while the
     * strip still hugs its content.
     *
     * @return equal-segment-width property
     */
    public final BooleanProperty equalSegmentWidthProperty() {
        return equalSegmentWidth;
    }

    /**
     * Returns whether equal-segment-width is enabled.
     *
     * @return {@code true} if equal-segment-width is enabled
     */
    public final boolean isEqualSegmentWidth() {
        return equalSegmentWidth.get();
    }

    /**
     * Sets whether equal-segment-width is enabled.
     *
     * @param value {@code true} to enable equal-segment-width
     */
    public final void setEqualSegmentWidth(boolean value) {
        equalSegmentWidth.set(value);
    }

    // ==================== Segment Spacing ====================

    private final DoubleProperty segmentSpacing =
            new SimpleDoubleProperty(this, "segmentSpacing", DEFAULT_SEGMENT_SPACING);

    /**
     * Gap between segments, in pixels. Defaults to {@code 0} (seamless).
     *
     * @return segment-spacing property
     */
    public final DoubleProperty segmentSpacingProperty() {
        return segmentSpacing;
    }

    /**
     * Returns the segment spacing.
     *
     * @return segment spacing in pixels
     */
    public final double getSegmentSpacing() {
        return segmentSpacing.get();
    }

    /**
     * Sets the segment spacing.
     *
     * @param value segment spacing in pixels
     */
    public final void setSegmentSpacing(double value) {
        segmentSpacing.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", DEFAULT_ANIMATED);

    /**
     * Whether selection changes animate the sliding indicator. When
     * {@code false} the indicator snaps immediately.
     *
     * @return animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the indicator animates.
     *
     * @return {@code true} if animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the indicator animates.
     *
     * @param value {@code true} to animate
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
                    return RXSegmentedControl.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of the sliding indicator animation. {@code Duration.ZERO},
     * {@code null}, negative or non-finite values fall back to an immediate snap
     * at render time.
     *
     * @return animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSegmentedControl<?>, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXSegmentedControl<?> control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSegmentedControl<?> control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATION_DURATION);
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
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
