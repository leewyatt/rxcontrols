package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * Node model used by {@link RXCascaderView}. It wraps an application value with
 * children, disabled state, lazy-loading hints, and tri-state check state.
 *
 * <p>Display text is not stored on the item: the owning view derives it from
 * the value via its {@code itemTextFactory} (falling back to
 * {@code String.valueOf(value)}).
 *
 * @param <T> application value type
 */
public class RXCascaderItem<T> {

    // ==================== Parent ====================

    private final ReadOnlyObjectWrapper<RXCascaderItem<T>> parent =
            new ReadOnlyObjectWrapper<>(this, "parent");

    /**
     * Parent item, or {@code null} for root items.
     *
     * @return read-only parent property
     */
    public final ReadOnlyObjectProperty<RXCascaderItem<T>> parentProperty() {
        return parent.getReadOnlyProperty();
    }

    /**
     * Returns the parent item.
     *
     * @return parent item, or {@code null}
     */
    public final RXCascaderItem<T> getParent() {
        return parent.get();
    }

    final void setParentItem(RXCascaderItem<T> value) {
        parent.set(value);
    }

    // ==================== Children ====================

    private final ObservableList<RXCascaderItem<T>> children =
            FXCollections.observableArrayList();

    // ==================== Constructors ====================

    /**
     * Creates an item with null value.
     */
    public RXCascaderItem() {
        this(null);
    }

    /**
     * Creates an item with the given value. Display text is derived from the
     * value by the owning view's {@code itemTextFactory} (or {@code value.toString()}
     * as a fallback), not stored on the item.
     *
     * @param value application value
     */
    public RXCascaderItem(@NamedArg("value") T value) {
        setValue(value);
        children.addListener((ListChangeListener<RXCascaderItem<T>>) change -> {
            while (change.next()) {
                for (RXCascaderItem<T> removed : change.getRemoved()) {
                    if (removed.getParent() == this) {
                        removed.setParentItem(null);
                    }
                }
                for (RXCascaderItem<T> added : change.getAddedSubList()) {
                    if (added != null) {
                        added.setParentItem(this);
                    }
                }
            }
        });
    }

    // ==================== Value ====================

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    /**
     * Application value represented by this item.
     *
     * @return value property
     */
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    /**
     * Returns the application value.
     *
     * @return application value
     */
    public final T getValue() {
        return value.get();
    }

    /**
     * Sets the application value.
     *
     * @param value application value
     */
    public final void setValue(T value) {
        this.value.set(value);
    }

    /**
     * Child items. Each node must have a single parent and the tree must be
     * acyclic (as with {@link javafx.scene.control.TreeItem}); adding a node to two
     * parents or introducing a cycle yields undefined path/recursion behavior.
     *
     * @return mutable child list
     */
    public final ObservableList<RXCascaderItem<T>> getChildren() {
        return children;
    }

    // ==================== Disabled ====================

    private final BooleanProperty disabled =
            new SimpleBooleanProperty(this, "disabled", false);

    /**
     * Whether this item itself is disabled.
     *
     * @return disabled property
     */
    public final BooleanProperty disabledProperty() {
        return disabled;
    }

    /**
     * Returns whether this item itself is disabled.
     *
     * @return {@code true} if disabled
     */
    public final boolean isDisabled() {
        return disabled.get();
    }

    /**
     * Sets whether this item itself is disabled.
     *
     * @param value {@code true} if disabled
     */
    public final void setDisabled(boolean value) {
        disabled.set(value);
    }

    // ==================== Leaf Hint ====================

    private final ObjectProperty<Boolean> leafHint =
            new SimpleObjectProperty<>(this, "leafHint");

    /**
     * Tri-state leaf override consumed by the owning {@link RXCascaderView}.
     *
     * <ul>
     *   <li>{@code true} — force this item to be a leaf: no expand arrow and no
     *       lazy load. In lazy mode (a children loader is set) this is the
     *       primary way to mark a node whose children are already known to be
     *       empty.</li>
     *   <li>{@code false} — force this item to be a branch even when it has no
     *       children, so an empty eager node still shows as expandable (and
     *       renders an empty column).</li>
     *   <li>{@code null} (default) — the view derives leaf state: eager mode
     *       uses {@code children.isEmpty()}; lazy mode treats an unloaded node
     *       as a branch until it has been loaded.</li>
     * </ul>
     *
     * @return leaf-hint property
     */
    public final ObjectProperty<Boolean> leafHintProperty() {
        return leafHint;
    }

    /**
     * Returns the leaf hint.
     *
     * @return leaf hint, or {@code null}
     */
    public final Boolean getLeafHint() {
        return leafHint.get();
    }

    /**
     * Sets the leaf hint.
     *
     * @param value leaf hint, or {@code null}
     */
    public final void setLeafHint(Boolean value) {
        leafHint.set(value);
    }

    // ==================== Loaded ====================

    private final ReadOnlyBooleanWrapper loaded =
            new ReadOnlyBooleanWrapper(this, "loaded", false);

    /**
     * Whether a children loader has already populated this item, observable but
     * not writable. It is meaningful only for lazy branches: it flips to
     * {@code true} after the loader successfully returns children, and stays
     * {@code false} when a load fails (so the branch can be retried). Eager and
     * leaf items never go through the loader and remain {@code false}.
     *
     * @return read-only loaded property
     */
    public final ReadOnlyBooleanProperty loadedProperty() {
        return loaded.getReadOnlyProperty();
    }

    /**
     * Returns whether a children loader has populated this item.
     *
     * @return {@code true} if loaded
     */
    public final boolean isLoaded() {
        return loaded.get();
    }

    final void setLoaded(boolean value) {
        loaded.set(value);
    }

    // ==================== Loading ====================

    private final ReadOnlyBooleanWrapper loading =
            new ReadOnlyBooleanWrapper(this, "loading", false);

    /**
     * Whether this item is currently running its children loader, observable but
     * not writable. The owning {@link RXCascaderView} drives it: {@code true}
     * while the loader stage is in flight, back to {@code false} on completion or
     * failure.
     *
     * @return read-only loading property
     */
    public final ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }

    /**
     * Returns whether this item is currently loading children.
     *
     * @return {@code true} if loading
     */
    public final boolean isLoading() {
        return loading.get();
    }

    final void setLoading(boolean value) {
        loading.set(value);
    }

    // ==================== Checked ====================

    private final BooleanProperty checked =
            new SimpleBooleanProperty(this, "checked", false);

    /**
     * Whether this item is checked.
     *
     * <p><strong>Writing this property directly only sets this single item.</strong>
     * It does not cascade to children, roll up to ancestors, or refresh the
     * owning panel's checked paths. Use it to seed an item's initial state (for
     * example a pre-checked locked item) before the tree is shown; for runtime
     * changes call {@code RXCascaderView.setCheckedCascade} or
     * {@code toggleCheck} instead so the tri-state machine stays consistent.
     *
     * @return checked property
     */
    public final BooleanProperty checkedProperty() {
        return checked;
    }

    /**
     * Returns whether this item is checked.
     *
     * @return {@code true} if checked
     */
    public final boolean isChecked() {
        return checked.get();
    }

    /**
     * Sets whether this item is checked.
     *
     * <p>This method only updates this item. It does not cascade to children,
     * roll up to ancestors, or refresh the owning panel's checked paths. Use it
     * for initial state seeding before display; use
     * {@code RXCascaderView.setCheckedCascade} or {@code toggleCheck} for
     * runtime selection changes.
     *
     * @param value {@code true} if checked
     */
    public final void setChecked(boolean value) {
        checked.set(value);
    }

    // ==================== Indeterminate ====================

    private final BooleanProperty indeterminate =
            new SimpleBooleanProperty(this, "indeterminate", false);

    /**
     * Whether this item is in the indeterminate state.
     *
     * <p>This is a derived display state normally written by the owning panel's
     * tri-state machine. Setting it directly is not cascaded or rolled up and is
     * generally only useful for seeding an item before the tree is shown; see
     * {@link #checkedProperty()}.
     *
     * @return indeterminate property
     */
    public final BooleanProperty indeterminateProperty() {
        return indeterminate;
    }

    /**
     * Returns whether this item is in the indeterminate state.
     *
     * @return {@code true} if indeterminate
     */
    public final boolean isIndeterminate() {
        return indeterminate.get();
    }

    /**
     * Sets whether this item is in the indeterminate state.
     *
     * <p>This method only updates this item. It does not cascade, roll up, or
     * refresh the owning panel's checked paths. It is normally written by the
     * panel's tri-state machine and should only be set directly for initial
     * state seeding before display.
     *
     * @param value {@code true} if indeterminate
     */
    public final void setIndeterminate(boolean value) {
        indeterminate.set(value);
    }

    // ==================== User Data ====================

    private final ObjectProperty<Object> userData =
            new SimpleObjectProperty<>(this, "userData");

    /**
     * Optional user data associated with this item.
     *
     * @return user-data property
     */
    public final ObjectProperty<Object> userDataProperty() {
        return userData;
    }

    /**
     * Returns user data.
     *
     * @return user data
     */
    public final Object getUserData() {
        return userData.get();
    }

    /**
     * Sets user data.
     *
     * @param value user data
     */
    public final void setUserData(Object value) {
        userData.set(value);
    }

    /**
     * Returns a debug representation based on the value. This is a fallback for
     * loggers and {@code RXCascaderPath.toString()}; the visible cascader text is
     * produced by the owning view's {@code itemTextFactory}, not by this method.
     *
     * @return {@code String.valueOf(getValue())}
     */
    @Override
    public String toString() {
        return String.valueOf(getValue());
    }
}
