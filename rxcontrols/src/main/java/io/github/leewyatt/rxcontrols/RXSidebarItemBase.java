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

/**
 * What the lists of an {@link RXSidebar} hold: an {@link RXSidebarItem}, an
 * {@link RXSidebarCaption} or an {@link RXSidebarSeparator}. This class carries
 * what the three share — visibility, the id and style classes copied to the
 * rendered node, user data, and the position in the tree. These are property
 * beans, not nodes; the sidebar's skin renders them.
 *
 * <p>An entry belongs to at most one list at a time: adding one that is already
 * in a sidebar or under an item is rejected with an
 * {@link IllegalArgumentException}. Remove it from where it is first. To
 * reorder a list, sort it or pass the new order to {@code setAll}; writing an
 * entry to a second index with {@code set} is rejected like any other second
 * membership. Identity equality is intentional ({@code equals} /
 * {@code hashCode} are not overridden).</p>
 *
 * <p>The three kinds above are the only ones; this class cannot be extended
 * outside the library.</p>
 */
public abstract class RXSidebarItemBase {

    RXSidebarItemBase() {
    }

    // ==================== Visible ====================

    private final BooleanProperty visible = new SimpleBooleanProperty(this, "visible", true);

    /**
     * Whether the entry is shown. Hiding a group hides everything below it.
     * This is independent of the sidebar's filter: a hidden entry is never shown
     * and never matched.
     *
     * @return the visible property
     */
    public final BooleanProperty visibleProperty() {
        return visible;
    }

    /**
     * @return whether the entry is shown
     */
    public final boolean isVisible() {
        return visible.get();
    }

    /**
     * @param value whether the entry is shown
     */
    public final void setVisible(boolean value) {
        visible.set(value);
    }

    // ==================== User Data ====================

    private final ObjectProperty<Object> userData = new SimpleObjectProperty<>(this, "userData");

    /**
     * An application value attached to the entry, such as the route it stands
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
     * The id copied to the entry's node, so a stylesheet can address it with
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

    // ==================== Style Class ====================

    private final ObservableList<String> styleClass = FXCollections.observableArrayList();

    /**
     * @return the style classes copied to the entry's node
     */
    public final ObservableList<String> getStyleClass() {
        return styleClass;
    }

    // ==================== Parent ====================

    private final ReadOnlyObjectWrapper<RXSidebarItem> parent = new ReadOnlyObjectWrapper<>(this, "parent");

    /**
     * The group this entry is a child of; {@code null} for a top-level entry
     * and for an entry that is in no list.
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
     * The sidebar whose tree this entry is in, at any depth; {@code null}
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

    // The list holding this entry; written synchronously by that list. parent
    // and sidebar are derived from it once a list change has finished.
    private SidebarItemList owningList;

    final SidebarItemList getOwningList() {
        return owningList;
    }

    final void setOwningList(SidebarItemList list) {
        owningList = list;
    }

    // Derives parent and sidebar from the owning list, for this entry and
    // everything below it.
    final void syncOwnership() {
        parent.set(owningList == null ? null : owningList.getOwnerItem());
        syncSidebar(owningList == null ? null : owningList.resolveSidebar());
    }

    void syncSidebar(RXSidebar value) {
        sidebar.set(value);
    }
}
