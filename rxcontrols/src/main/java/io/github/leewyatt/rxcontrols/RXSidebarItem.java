package io.github.leewyatt.rxcontrols;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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
 * An item of an {@link RXSidebar}: a page, an action or a group. This is a
 * property bean, not a {@link Node}: the sidebar's skin renders each item into
 * its own row. What an item does when activated follows from its state:
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
 * <p>The lists of a sidebar also take {@link RXSidebarHeader} (a section
 * caption) and {@link RXSidebarSeparator} (a divider); see
 * {@link RXSidebarItemBase} for what all three share and for the rules an added
 * entry must meet.</p>
 */
public class RXSidebarItem extends RXSidebarItemBase {

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
     * @param children the child items, captions and separators
     * @return the new item
     * @throws IllegalArgumentException if a child is {@code null}, appears twice,
     *                                  or already belongs to a list
     */
    public static RXSidebarItem group(String text, Node graphic, RXSidebarItemBase... children) {
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

    // ==================== Keywords / Items ====================

    private final ObservableList<String> keywords = FXCollections.observableArrayList();
    private final SidebarItemList items = new SidebarItemList(this);

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
     * The child items: items, captions and separators. A non-empty list makes
     * this item a group.
     *
     * @return the live list of child items
     */
    public final ObservableList<RXSidebarItemBase> getItems() {
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

    // ==================== Selection support ====================

    @Override
    void syncSidebar(RXSidebar value) {
        super.syncSidebar(value);
        for (RXSidebarItemBase child : items) {
            child.syncSidebar(value);
        }
    }

    /**
     * @return whether this item can be the selection: a selectable item without
     * child items
     */
    final boolean canBeSelected() {
        return isSelectable() && items.isEmpty();
    }

    private void notifySelectabilityChanged() {
        RXSidebar owner = getSidebar();
        if (owner != null) {
            owner.revalidateSelection();
        }
    }
}
