package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * Node model used by {@link RXCascaderPanel}. It wraps an application value
 * with display text, children, disabled state, lazy-loading hints, and
 * tri-state check state.
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
     * Creates an item with null value and empty text.
     */
    public RXCascaderItem() {
        this(null, "");
    }

    /**
     * Creates an item with the given value and text.
     *
     * @param value application value
     * @param text display text
     */
    public RXCascaderItem(@NamedArg("value") T value, @NamedArg("text") String text) {
        setValue(value);
        setText(text);
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

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    /**
     * Display text used by default cells and path text factories.
     *
     * @return text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the display text.
     *
     * @return display text
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the display text.
     *
     * @param value display text
     */
    public final void setText(String value) {
        text.set(value);
    }

    /**
     * Child items.
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
     * Optional leaf hint. {@code null} means the panel derives leaf state from
     * the child list.
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

    private final BooleanProperty loaded =
            new SimpleBooleanProperty(this, "loaded", true);

    /**
     * Whether this item's children are loaded.
     *
     * @return loaded property
     */
    public final BooleanProperty loadedProperty() {
        return loaded;
    }

    /**
     * Returns whether this item's children are loaded.
     *
     * @return {@code true} if loaded
     */
    public final boolean isLoaded() {
        return loaded.get();
    }

    /**
     * Sets whether this item's children are loaded.
     *
     * @param value {@code true} if loaded
     */
    public final void setLoaded(boolean value) {
        loaded.set(value);
    }

    // ==================== Loading ====================

    private final BooleanProperty loading =
            new SimpleBooleanProperty(this, "loading", false);

    /**
     * Whether this item is currently loading children.
     *
     * @return loading property
     */
    public final BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Returns whether this item is currently loading children.
     *
     * @return {@code true} if loading
     */
    public final boolean isLoading() {
        return loading.get();
    }

    /**
     * Sets whether this item is currently loading children.
     *
     * @param value {@code true} if loading
     */
    public final void setLoading(boolean value) {
        loading.set(value);
    }

    // ==================== Checked ====================

    private final BooleanProperty checked =
            new SimpleBooleanProperty(this, "checked", false);

    /**
     * Whether this item is checked.
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
     * Returns the display text for debugging and default JavaFX renderers.
     *
     * @return display text
     */
    @Override
    public String toString() {
        return getText();
    }
}
