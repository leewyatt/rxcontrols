package io.github.leewyatt.rxcontrols;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;

/**
 * A single entry in a command menu ({@code RXMenuList} / {@code RXPopupMenu} /
 * {@code RXMenuButton}).
 *
 * <p>This is a JavaFX property bean, not a {@link Node} and not a
 * {@link javafx.scene.control.ButtonBase ButtonBase}: it holds a command's
 * text, icon, accelerator hint, and action, and the menu skin renders each item
 * into its own cell node. The model/view split mirrors the platform
 * {@code MenuItem}, but this class is even more contained — it does not
 * implement {@code Styleable} or {@code EventTarget}. All getters and setters
 * are pure pass-through.
 *
 * <p>The base class instance <b>is</b> an ordinary command item. Two special
 * kinds extend it and override {@link #isFocusable()} to {@code false}:
 * {@link RXMenuSeparator} (a divider) and {@link RXMenuHeader} (a group
 * caption). Custom rich content is carried through the {@link #graphicProperty()
 * graphic} escape hatch rather than a dedicated subtype.
 *
 * <p><b>Single-occupancy contract:</b> {@link #graphicProperty() graphic} is a
 * live scene-graph node, and a JavaFX node may have only one parent. An
 * {@code RXMenuItem} instance (and the graphic it carries) must therefore appear
 * at most once across a single menu host, just as a {@code MenuItem} cannot
 * belong to two {@code Menu}s at once. Reusing one instance in two hosts is a
 * usage error.
 *
 * <p>Identity equality is intentional ({@code equals}/{@code hashCode} are not
 * overridden) — two distinct items are never "equal".
 *
 * <p><b>Selectable variants.</b> A base item is a plain command; setting
 * {@link #setSelectable(boolean) selectable} turns it into a checkbox / radio
 * entry that carries a {@link #selectedProperty() selected} state and a leading
 * indicator (the skin renders it and reflects {@code :checked}). Radio mutual
 * exclusion reuses the platform {@link ToggleGroup}: this class
 * {@code implements Toggle} (the {@code Toggle} interface has no {@code Node}
 * methods, so a non-{@code Node} bean satisfies it, exactly as the platform
 * {@code RadioMenuItem} — itself not a {@code Node} — does). Use the
 * {@link #checkbox(String, BooleanProperty)} / {@link #radio(String, ToggleGroup)}
 * factories for the common cases. {@link #dangerProperty() danger} flags a
 * destructive action for {@code :danger} styling.
 */
public class RXMenuItem implements Toggle {

    private static final boolean DEFAULT_KEEP_OPEN = false;
    private static final boolean DEFAULT_SELECTABLE = false;

    // ==================== Constructors ====================

    /**
     * Creates an empty command item.
     */
    public RXMenuItem() {
    }

    /**
     * Creates a command item with the given text.
     *
     * @param text the item text, or {@code null}
     */
    public RXMenuItem(String text) {
        setText(text);
    }

    /**
     * Creates a command item with the given text and leading graphic.
     *
     * @param text    the item text, or {@code null}
     * @param graphic the leading graphic node, or {@code null}
     */
    public RXMenuItem(String text, Node graphic) {
        setText(text);
        setGraphic(graphic);
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * The primary text shown in the item's label slot. A {@code null} value is
     * tolerated; the skin coalesces it to the empty string when rendering, and
     * an icon-only item is expected to leave this {@code null}.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the item text.
     *
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the item text.
     *
     * @param value the text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The leading graphic node (typically an icon), or a custom content node
     * used as the rich-content escape hatch. {@code null} means no graphic and
     * the leading slot collapses.
     *
     * <p>Subject to the single-occupancy contract described in the class
     * documentation.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the graphic node.
     *
     * @return the graphic node, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the graphic node.
     *
     * @param value the graphic node, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Accelerator ====================

    private final ObjectProperty<KeyCombination> accelerator = new SimpleObjectProperty<>(this, "accelerator");

    /**
     * The keyboard accelerator for this item. It is shown as a hint in the
     * item's trailing slot via {@link KeyCombination#getDisplayText()};
     * actually registering it as a global shortcut is a separate, opt-in
     * capability. {@code null} means no accelerator hint is shown.
     *
     * @return the accelerator property
     */
    public final ObjectProperty<KeyCombination> acceleratorProperty() {
        return accelerator;
    }

    /**
     * Returns the accelerator.
     *
     * @return the accelerator, or {@code null}
     */
    public final KeyCombination getAccelerator() {
        return accelerator.get();
    }

    /**
     * Sets the accelerator.
     *
     * @param value the accelerator, or {@code null}
     */
    public final void setAccelerator(KeyCombination value) {
        accelerator.set(value);
    }

    // ==================== Disable ====================

    private final BooleanProperty disable = new SimpleBooleanProperty(this, "disable", false);

    /**
     * Whether this item is disabled. The skin mirrors this onto the item cell's
     * {@code :disabled} pseudo-class and, by default, skips disabled items
     * during keyboard navigation. The name aligns with JavaFX
     * {@code Node.disable}, though this bean is not a {@code Node}.
     *
     * @return the disable property
     */
    public final BooleanProperty disableProperty() {
        return disable;
    }

    /**
     * Returns whether this item is disabled.
     *
     * @return {@code true} if the item is disabled
     */
    public final boolean isDisable() {
        return disable.get();
    }

    /**
     * Sets whether this item is disabled.
     *
     * @param value {@code true} to disable the item
     */
    public final void setDisable(boolean value) {
        disable.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>(this, "onAction");

    /**
     * The handler invoked when this command item is activated. Activation
     * follows a close-then-fire order: the hosting popup closes first, then the
     * handler runs (so a handler that opens another menu is safe, and a handler
     * exception does not keep the popup open). {@code null} means no action —
     * the item still closes the menu on activation.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the action handler.
     *
     * @return the action handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the action handler.
     *
     * @param value the action handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    // ==================== User Data ====================

    private final ObjectProperty<Object> userData = new SimpleObjectProperty<>(this, "userData");

    /**
     * Arbitrary host data associated with this item, mirroring the
     * {@code Node.userData} convention. {@code null} by default.
     *
     * @return the user-data property
     */
    public final ObjectProperty<Object> userDataProperty() {
        return userData;
    }

    /**
     * Returns the user data.
     *
     * @return the user data, or {@code null}
     */
    public final Object getUserData() {
        return userData.get();
    }

    /**
     * Sets the user data.
     *
     * @param value the user data, or {@code null}
     */
    public final void setUserData(Object value) {
        userData.set(value);
    }

    // ==================== Keep Open ====================

    private boolean keepOpen = DEFAULT_KEEP_OPEN;

    /**
     * Whether activating this item keeps the menu open instead of closing it.
     * A plain flag (not an observable property) because it is a construction-time
     * declaration that does not participate in CSS or binding. Defaults to
     * {@code false}: a command item closes the menu when activated.
     *
     * @return {@code true} if activation keeps the menu open
     */
    public final boolean isKeepOpen() {
        return keepOpen;
    }

    /**
     * Sets whether activating this item keeps the menu open.
     *
     * @param value {@code true} to keep the menu open on activation
     */
    public final void setKeepOpen(boolean value) {
        keepOpen = value;
    }

    // ==================== Selectable ====================

    private boolean selectable = DEFAULT_SELECTABLE;

    /**
     * Whether this item is a checkbox / radio entry (versus a plain command).
     * A plain flag (not an observable property, like {@link #isKeepOpen()}) read
     * by the skin when it builds the cell, so it is a construction-time
     * declaration: the {@link #checkbox(String, BooleanProperty)} /
     * {@link #radio(String, ToggleGroup)} factories set it. When {@code true} the
     * cell shows a leading indicator and toggles {@link #selectedProperty()
     * selected} on activation instead of closing. Defaults to {@code false}.
     *
     * @return {@code true} if the item is selectable
     */
    public final boolean isSelectable() {
        return selectable;
    }

    /**
     * Sets whether this item is a checkbox / radio entry.
     *
     * @param value {@code true} to make the item selectable
     */
    public final void setSelectable(boolean value) {
        selectable = value;
    }

    // ==================== Selected ====================

    private BooleanProperty selected;

    /**
     * The checkbox / radio checked state. Lazily created. Its {@code invalidated}
     * hook drives radio mutual exclusion through the {@link #toggleGroupProperty()
     * toggle group} (selecting this toggle when {@code true}, clearing the group's
     * selection when this was the selected toggle and it turns {@code false}); the
     * cell reflects it as the {@code :checked} pseudo-class. Only meaningful when
     * {@link #isSelectable() selectable}.
     *
     * @return the selected property
     */
    @Override
    public final BooleanProperty selectedProperty() {
        if (selected == null) {
            selected = new SimpleBooleanProperty(this, "selected", false) {
                @Override
                protected void invalidated() {
                    ToggleGroup group = getToggleGroup();
                    if (group != null) {
                        if (get()) {
                            group.selectToggle(RXMenuItem.this);
                        } else if (group.getSelectedToggle() == RXMenuItem.this) {
                            // ToggleGroup.clearSelectedToggle() is package-private and
                            // unreachable across packages; selectToggle(null) is the
                            // public equivalent, guarded so it only clears our own selection.
                            group.selectToggle(null);
                        }
                    }
                }
            };
        }
        return selected;
    }

    /**
     * Returns whether the item is checked.
     *
     * @return {@code true} if checked
     */
    @Override
    public final boolean isSelected() {
        return selected != null && selected.get();
    }

    /**
     * Sets whether the item is checked.
     *
     * @param value {@code true} to check the item
     */
    @Override
    public final void setSelected(boolean value) {
        selectedProperty().set(value);
    }

    // ==================== Toggle Group ====================

    private ObjectProperty<ToggleGroup> toggleGroup;

    /**
     * The radio group this item belongs to (reusing the platform
     * {@link ToggleGroup} for mutual exclusion), or {@code null} for a plain
     * checkbox with no exclusion. Lazily created; its {@code invalidated} hook
     * maintains group membership (adds this toggle to the new group, removes it
     * from the old), mirroring the platform {@code RadioMenuItem}.
     *
     * @return the toggle-group property
     */
    @Override
    public final ObjectProperty<ToggleGroup> toggleGroupProperty() {
        if (toggleGroup == null) {
            toggleGroup = new SimpleObjectProperty<>(this, "toggleGroup") {
                private ToggleGroup oldGroup;

                @Override
                protected void invalidated() {
                    ToggleGroup group = get();
                    if (group != null && !group.getToggles().contains(RXMenuItem.this)) {
                        if (oldGroup != null) {
                            oldGroup.getToggles().remove(RXMenuItem.this);
                        }
                        group.getToggles().add(RXMenuItem.this);
                    } else if (group == null && oldGroup != null) {
                        oldGroup.getToggles().remove(RXMenuItem.this);
                    }
                    oldGroup = group;
                }
            };
        }
        return toggleGroup;
    }

    /**
     * Returns the toggle group.
     *
     * @return the toggle group, or {@code null}
     */
    @Override
    public final ToggleGroup getToggleGroup() {
        return toggleGroup == null ? null : toggleGroup.get();
    }

    /**
     * Sets the toggle group.
     *
     * @param value the toggle group, or {@code null}
     */
    @Override
    public final void setToggleGroup(ToggleGroup value) {
        toggleGroupProperty().set(value);
    }

    // ==================== Danger ====================

    private BooleanProperty danger;

    /**
     * Whether this item is a destructive / dangerous action. Lazily created; the
     * cell reflects it as the {@code :danger} pseudo-class for accent (typically
     * red) styling. Defaults to {@code false}.
     *
     * @return the danger property
     */
    public final BooleanProperty dangerProperty() {
        if (danger == null) {
            danger = new SimpleBooleanProperty(this, "danger", false);
        }
        return danger;
    }

    /**
     * Returns whether this item is a destructive action.
     *
     * @return {@code true} if the item is dangerous
     */
    public final boolean isDanger() {
        return danger != null && danger.get();
    }

    /**
     * Sets whether this item is a destructive action.
     *
     * @param value {@code true} to flag the item as dangerous
     */
    public final void setDanger(boolean value) {
        dangerProperty().set(value);
    }

    // ==================== Properties (Toggle contract) ====================

    private ObservableMap<Object, Object> properties;

    /**
     * A general-purpose property map, part of the {@link Toggle} contract. Lazily
     * created.
     *
     * @return the property map
     */
    @Override
    public final ObservableMap<Object, Object> getProperties() {
        if (properties == null) {
            properties = FXCollections.observableHashMap();
        }
        return properties;
    }

    // ==================== Style Class ====================

    private final ObservableList<String> styleClass = FXCollections.observableArrayList();

    /**
     * The additional style classes forwarded to this item's cell node. Because
     * an {@code RXMenuItem} is not {@code Styleable}, these are a plain string
     * list the skin copies onto the cell it builds; CSS targets the cell, not
     * the model.
     *
     * @return the modifiable style-class list
     */
    public final ObservableList<String> getStyleClass() {
        return styleClass;
    }

    // ==================== Parent List ====================

    private final ReadOnlyObjectWrapper<RXMenuList> parentList =
            new ReadOnlyObjectWrapper<>(this, "parentList");

    /**
     * The {@link RXMenuList} this item currently belongs to, or {@code null}
     * when it is not in any list. Back-filled by the hosting list as items are
     * added and removed. An item belongs to at most one list at a time; adding
     * an item that already has a parent moves it (the old list drops it first).
     *
     * @return the read-only parent-list property
     */
    public final ReadOnlyObjectProperty<RXMenuList> parentListProperty() {
        return parentList.getReadOnlyProperty();
    }

    /**
     * Returns the list this item belongs to.
     *
     * @return the parent list, or {@code null}
     */
    public final RXMenuList getParentList() {
        return parentList.get();
    }

    // Package-private: only the hosting RXMenuList back-fills this as items move.
    final void setParentListInternal(RXMenuList value) {
        parentList.set(value);
    }

    // ==================== Behavior ====================

    /**
     * Fires this item's action. If an {@link #onActionProperty() onAction}
     * handler is set, it is invoked directly with an {@link ActionEvent} whose
     * source is this item; there is no event-dispatch chain (this bean is not an
     * {@code EventTarget}). A handler exception propagates to the caller.
     */
    public void fire() {
        EventHandler<ActionEvent> handler = getOnAction();
        if (handler != null) {
            handler.handle(new ActionEvent(this, null));
        }
    }

    /**
     * Whether this item can receive roving keyboard focus and be activated.
     * A command item is focusable when it is not disabled; the special
     * {@link RXMenuSeparator} and {@link RXMenuHeader} subtypes are never
     * focusable. This is the single entry point the skin queries for keyboard
     * navigation, hover, type-ahead, and accessibility.
     *
     * @return {@code true} if the item is focusable
     */
    public boolean isFocusable() {
        return !isDisable();
    }

    // ==================== Static Factories ====================

    /**
     * Creates a plain text command item.
     *
     * @param text the item text, or {@code null}
     * @return a new command item
     */
    public static RXMenuItem of(String text) {
        return new RXMenuItem(text);
    }

    /**
     * Creates a command item with text and a leading graphic.
     *
     * @param text    the item text, or {@code null}
     * @param graphic the leading graphic node, or {@code null}
     * @return a new command item
     */
    public static RXMenuItem of(String text, Node graphic) {
        return new RXMenuItem(text, graphic);
    }

    /**
     * Creates a command item with text, a leading graphic, and an action
     * handler.
     *
     * @param text    the item text, or {@code null}
     * @param graphic the leading graphic node, or {@code null}
     * @param handler the action handler, or {@code null}
     * @return a new command item
     */
    public static RXMenuItem action(String text, Node graphic, EventHandler<ActionEvent> handler) {
        RXMenuItem item = new RXMenuItem(text, graphic);
        item.setOnAction(handler);
        return item;
    }

    /**
     * Creates a separator item.
     *
     * @return a new {@link RXMenuSeparator}
     */
    public static RXMenuSeparator separator() {
        return new RXMenuSeparator();
    }

    /**
     * Creates a group-header item with the given caption text.
     *
     * @param text the header caption, or {@code null}
     * @return a new {@link RXMenuHeader}
     */
    public static RXMenuHeader header(String text) {
        return new RXMenuHeader(text);
    }

    /**
     * Creates a checkbox item whose checked state is bound bidirectionally to an
     * external boolean property. The item is {@link #isSelectable() selectable}
     * and {@link #isKeepOpen() keeps the menu open} on activation so it can be
     * toggled in place.
     *
     * @param text     the item text, or {@code null}
     * @param selected the external boolean to bind the checked state to
     * @return a new checkbox item
     */
    public static RXMenuItem checkbox(String text, BooleanProperty selected) {
        RXMenuItem item = new RXMenuItem(text);
        item.setSelectable(true);
        item.setKeepOpen(true);
        item.selectedProperty().bindBidirectional(selected);
        return item;
    }

    /**
     * Creates a radio item in the given {@link ToggleGroup} for mutual exclusion.
     * The item is {@link #isSelectable() selectable} and
     * {@link #isKeepOpen() keeps the menu open} on activation.
     *
     * @param text  the item text, or {@code null}
     * @param group the toggle group, or {@code null} for no exclusion
     * @return a new radio item
     */
    public static RXMenuItem radio(String text, ToggleGroup group) {
        RXMenuItem item = new RXMenuItem(text);
        item.setSelectable(true);
        item.setKeepOpen(true);
        item.setToggleGroup(group);
        return item;
    }
}
