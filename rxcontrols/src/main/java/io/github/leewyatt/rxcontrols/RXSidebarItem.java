package io.github.leewyatt.rxcontrols;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;

/**
 * An entry of an {@link RXSidebar}. This is a property bean, not a {@link Node}:
 * the sidebar's skin renders each item into its own row.
 *
 * <p>An instance of this class is an ordinary entry, and what it does when
 * activated follows from its state:</p>
 * <ul>
 *   <li><b>Page</b> — no child items, {@link #selectableProperty() selectable}
 *   (the default). Activating it selects it and then fires
 *   {@link #onActionProperty() onAction}.</li>
 *   <li><b>Action</b> — no child items, not selectable. Activating it only fires
 *   {@code onAction}; the selection does not change.</li>
 *   <li><b>Group</b> — has child {@link #getItems() items}. Activating it
 *   expands or collapses it. A group is never selected and never fires
 *   {@code onAction}, whatever {@code selectable} says.</li>
 * </ul>
 *
 * <p>Two special kinds extend this class: {@link RXSidebarHeader} (a section
 * caption) and {@link RXSidebarSeparator} (a divider). Neither can be focused or
 * activated.</p>
 *
 * <p>An item belongs to at most one list at a time: adding an item that is
 * already in a sidebar or under another item is rejected with an
 * {@link IllegalArgumentException}. Remove it from where it is first. To
 * reorder a list, sort it or pass the new order to {@code setAll}; writing an
 * item to a second index with {@code set} is rejected like any other second
 * membership. Identity
 * equality is intentional ({@code equals} / {@code hashCode} are not
 * overridden).</p>
 */
public class RXSidebarItem {

    // ==================== Constructors ====================

    /**
     * Creates an empty item.
     */
    public RXSidebarItem() {
    }

    /**
     * Creates an item with the given text.
     *
     * @param text the item text, or {@code null}
     */
    public RXSidebarItem(String text) {
        setText(text);
    }

    /**
     * Creates an item with the given text and graphic.
     *
     * @param text    the item text, or {@code null}
     * @param graphic the leading graphic, or {@code null}
     */
    public RXSidebarItem(String text, Node graphic) {
        setText(text);
        setGraphic(graphic);
    }

    // ==================== Factories ====================

    /**
     * Creates a page: a selectable item.
     *
     * @param text the item text, or {@code null}
     * @return the new item
     */
    public static RXSidebarItem of(String text) {
        return new RXSidebarItem(text);
    }

    /**
     * Creates a page: a selectable item.
     *
     * @param text    the item text, or {@code null}
     * @param graphic the leading graphic, or {@code null}
     * @return the new item
     */
    public static RXSidebarItem of(String text, Node graphic) {
        return new RXSidebarItem(text, graphic);
    }

    /**
     * Creates an action: an item that fires a command and is never selected.
     *
     * @param text     the item text, or {@code null}
     * @param graphic  the leading graphic, or {@code null}
     * @param onAction the handler fired on activation, or {@code null}
     * @return the new item
     */
    public static RXSidebarItem action(String text, Node graphic, EventHandler<ActionEvent> onAction) {
        RXSidebarItem item = new RXSidebarItem(text, graphic);
        item.setSelectable(false);
        item.setOnAction(onAction);
        return item;
    }

    /**
     * Creates a group holding the given child items.
     *
     * @param text     the item text, or {@code null}
     * @param graphic  the leading graphic, or {@code null}
     * @param children the child items
     * @return the new item
     * @throws IllegalArgumentException if a child is {@code null}, appears twice,
     *                                  or already belongs to a list
     */
    public static RXSidebarItem group(String text, Node graphic, RXSidebarItem... children) {
        RXSidebarItem item = new RXSidebarItem(text, graphic);
        item.getItems().addAll(children);
        return item;
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * The text shown in the row. {@code null} renders as no text.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * @param value the text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The leading graphic, typically an icon. It is the only content a top-level
     * row shows in {@link RXSidebar.SidebarMode#MINI}, so give top-level items
     * one.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * @return the graphic, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * @param value the graphic, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Trailing ====================

    private final ObjectProperty<Node> trailing = new SimpleObjectProperty<>(this, "trailing");

    /**
     * Content shown at the end of the row, such as a count or a badge. Hidden on
     * a top-level row in {@link RXSidebar.SidebarMode#MINI}.
     *
     * @return the trailing property
     */
    public final ObjectProperty<Node> trailingProperty() {
        return trailing;
    }

    /**
     * @return the trailing node, or {@code null}
     */
    public final Node getTrailing() {
        return trailing.get();
    }

    /**
     * @param value the trailing node, or {@code null}
     */
    public final void setTrailing(Node value) {
        trailing.set(value);
    }

    // ==================== Tooltip ====================

    private final ObjectProperty<Tooltip> tooltip = new SimpleObjectProperty<>(this, "tooltip");

    /**
     * The tooltip of the row. While this is {@code null}, a top-level item
     * without child items shows its text as a tooltip in
     * {@link RXSidebar.SidebarMode#MINI}, where the text itself is hidden.
     *
     * @return the tooltip property
     */
    public final ObjectProperty<Tooltip> tooltipProperty() {
        return tooltip;
    }

    /**
     * @return the tooltip, or {@code null}
     */
    public final Tooltip getTooltip() {
        return tooltip.get();
    }

    /**
     * @param value the tooltip, or {@code null}
     */
    public final void setTooltip(Tooltip value) {
        tooltip.set(value);
    }

    // ==================== Accessible Text ====================

    private final StringProperty accessibleText = new SimpleStringProperty(this, "accessibleText");

    /**
     * The text announced by assistive technology. While this is {@code null} the
     * item's text is announced.
     *
     * @return the accessible-text property
     */
    public final StringProperty accessibleTextProperty() {
        return accessibleText;
    }

    /**
     * @return the accessible text, or {@code null}
     */
    public final String getAccessibleText() {
        return accessibleText.get();
    }

    /**
     * @param value the accessible text, or {@code null}
     */
    public final void setAccessibleText(String value) {
        accessibleText.set(value);
    }

    // ==================== Disable ====================

    private final BooleanProperty disable = new SimpleBooleanProperty(this, "disable");

    /**
     * Whether the item is disabled. Disabling a group disables all of its
     * descendants.
     *
     * @return the disable property
     */
    public final BooleanProperty disableProperty() {
        return disable;
    }

    /**
     * @return whether the item is disabled
     */
    public final boolean isDisable() {
        return disable.get();
    }

    /**
     * @param value whether the item is disabled
     */
    public final void setDisable(boolean value) {
        disable.set(value);
    }

    // ==================== Visible ====================

    private final BooleanProperty visible = new SimpleBooleanProperty(this, "visible", true);

    /**
     * Whether the item is shown. Hiding a group hides all of its descendants.
     * This is independent of the sidebar's filter: a hidden item is never shown
     * and never matched.
     *
     * @return the visible property
     */
    public final BooleanProperty visibleProperty() {
        return visible;
    }

    /**
     * @return whether the item is shown
     */
    public final boolean isVisible() {
        return visible.get();
    }

    /**
     * @param value whether the item is shown
     */
    public final void setVisible(boolean value) {
        visible.set(value);
    }

    // ==================== Selectable ====================

    private final BooleanProperty selectable = new SimpleBooleanProperty(this, "selectable", true) {
        @Override
        protected void invalidated() {
            notifySelectabilityChanged();
        }
    };

    /**
     * Whether activating the item selects it. {@code true} makes it a page,
     * {@code false} an action. Ignored while the item has child items: a group
     * is never selected.
     *
     * @return the selectable property
     */
    public final BooleanProperty selectableProperty() {
        return selectable;
    }

    /**
     * @return whether activating the item selects it
     */
    public final boolean isSelectable() {
        return selectable.get();
    }

    /**
     * @param value whether activating the item selects it
     */
    public final void setSelectable(boolean value) {
        selectable.set(value);
    }

    // ==================== Expanded ====================

    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded");

    /**
     * Whether the group shows its child items in
     * {@link RXSidebar.SidebarMode#EXPANDED}. Only meaningful while the item has
     * child items. The sidebar writes this when the user toggles the group and
     * when a descendant becomes the selection; it leaves a bound property alone.
     *
     * @return the expanded property
     */
    public final BooleanProperty expandedProperty() {
        return expanded;
    }

    /**
     * @return whether the group shows its child items
     */
    public final boolean isExpanded() {
        return expanded.get();
    }

    /**
     * @param value whether the group shows its child items
     */
    public final void setExpanded(boolean value) {
        expanded.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction =
            new SimpleObjectProperty<>(this, "onAction");

    /**
     * The handler fired when the item is activated. The event's source is this
     * item. A group does not fire it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * @return the handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * @param value the handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    // ==================== User Data ====================

    private final ObjectProperty<Object> userData = new SimpleObjectProperty<>(this, "userData");

    /**
     * An application value attached to the item, such as the route it stands
     * for.
     *
     * @return the user-data property
     */
    public final ObjectProperty<Object> userDataProperty() {
        return userData;
    }

    /**
     * @return the user data, or {@code null}
     */
    public final Object getUserData() {
        return userData.get();
    }

    /**
     * @param value the user data, or {@code null}
     */
    public final void setUserData(Object value) {
        userData.set(value);
    }

    // ==================== Id ====================

    private final StringProperty id = new SimpleStringProperty(this, "id");

    /**
     * The id copied to the row, so a stylesheet can address it with
     * {@code #id}.
     *
     * @return the id property
     */
    public final StringProperty idProperty() {
        return id;
    }

    /**
     * @return the id, or {@code null}
     */
    public final String getId() {
        return id.get();
    }

    /**
     * @param value the id, or {@code null}
     */
    public final void setId(String value) {
        id.set(value);
    }

    // ==================== Style Class / Keywords / Items ====================

    private final ObservableList<String> styleClass = FXCollections.observableArrayList();
    private final ObservableList<String> keywords = FXCollections.observableArrayList();
    private final SidebarItemList items = new SidebarItemList(this);

    /**
     * @return the style classes copied to the row
     */
    public final ObservableList<String> getStyleClass() {
        return styleClass;
    }

    /**
     * Aliases matched by {@link RXSidebar#textFilter(String)} in addition to the
     * text. They are never shown.
     *
     * @return the live keyword list
     */
    public final ObservableList<String> getKeywords() {
        return keywords;
    }

    /**
     * The child items. A non-empty list makes this item a group.
     *
     * @return the live list of child items
     */
    public final ObservableList<RXSidebarItem> getItems() {
        return items;
    }

    // ==================== Selected ====================

    private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(this, "selected");

    /**
     * Whether this item is the selection of the sidebar it is in.
     *
     * @return the read-only selected property
     */
    public final ReadOnlyBooleanProperty selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    /**
     * @return whether this item is selected
     */
    public final boolean isSelected() {
        return selected.get();
    }

    final void setSelected(boolean value) {
        selected.set(value);
    }

    // ==================== Parent ====================

    private final ReadOnlyObjectWrapper<RXSidebarItem> parent = new ReadOnlyObjectWrapper<>(this, "parent");

    /**
     * The group this item is a child of; {@code null} for a top-level item and
     * for an item that is in no list.
     *
     * @return the read-only parent property
     */
    public final ReadOnlyObjectProperty<RXSidebarItem> parentProperty() {
        return parent.getReadOnlyProperty();
    }

    /**
     * @return the parent item, or {@code null}
     */
    public final RXSidebarItem getParent() {
        return parent.get();
    }

    // ==================== Sidebar ====================

    private final ReadOnlyObjectWrapper<RXSidebar> sidebar = new ReadOnlyObjectWrapper<>(this, "sidebar");

    /**
     * The sidebar whose tree this item is in, at any depth; {@code null}
     * otherwise.
     *
     * @return the read-only sidebar property
     */
    public final ReadOnlyObjectProperty<RXSidebar> sidebarProperty() {
        return sidebar.getReadOnlyProperty();
    }

    /**
     * @return the sidebar, or {@code null}
     */
    public final RXSidebar getSidebar() {
        return sidebar.get();
    }

    // ==================== Ownership (maintained by SidebarItemList) ====================

    // The list holding this item; written synchronously by that list. parent and
    // sidebar are derived from it once a list change has finished.
    private SidebarItemList owningList;

    final SidebarItemList getOwningList() {
        return owningList;
    }

    final void setOwningList(SidebarItemList list) {
        owningList = list;
    }

    // Derives parent and sidebar from the owning list, for this item and its
    // whole subtree.
    final void syncOwnership() {
        parent.set(owningList == null ? null : owningList.getOwnerItem());
        syncSidebar(owningList == null ? null : owningList.resolveSidebar());
    }

    private void syncSidebar(RXSidebar value) {
        sidebar.set(value);
        for (RXSidebarItem child : items) {
            child.syncSidebar(value);
        }
    }

    /**
     * @return whether this item can be the selection: a selectable item without
     * child items that is neither a caption nor a separator
     */
    boolean canBeSelected() {
        if (!isSelectable() || !items.isEmpty()) {
            return false;
        }
        // Items under a caption or a separator are never rendered.
        for (SidebarItemList list = owningList; list != null && list.getOwnerItem() != null;
                list = list.getOwnerItem().owningList) {
            RXSidebarItem owner = list.getOwnerItem();
            if (owner instanceof RXSidebarHeader || owner instanceof RXSidebarSeparator) {
                return false;
            }
        }
        return true;
    }

    final void notifySelectabilityChanged() {
        RXSidebar owner = getSidebar();
        if (owner != null) {
            owner.revalidateSelection();
        }
    }
}
