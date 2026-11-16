package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXListSectionCellSkin;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Cell;
import javafx.scene.control.Skin;

/**
 * Section-header cell for an {@link RXListView}. The list view recycles a small
 * pool of these across the visible section rows, calling
 * {@link #updateSection(RXListSection)} to bind each one to the section it
 * currently represents.
 *
 * <p>Override {@link #updateItem(RXListSection, boolean)} to render the header
 * and install the cell through
 * {@link RXListView#sectionHeaderFactoryProperty() sectionHeaderFactory}. The
 * base implementation carries no rendering opinion — when no factory is set the
 * list view installs a default factory that shows the section key as text. To
 * build a rich header from the section's items, slice the live list with
 * {@code getItems().subList(section.firstItemIndex(), section.endItemIndex())}.
 *
 * <p>While the list view's {@link RXListView#collapsibleSectionsProperty()
 * collapsibleSections} is on, the header is the section's expand / collapse toggle:
 * {@link #isCollapsible()} is {@code true}, {@link #isExpanded()} tells whether the
 * section's items are shown, the {@code :expanded} / {@code :collapsed}
 * pseudo-classes follow it, and the default skin adds a disclosure arrow
 * ({@code .disclosure > .arrow}) in front of the content — for custom headers too.
 * Hide it with {@code visibility: hidden} on the {@code .disclosure} node, or return
 * a plain {@code CellSkinBase} from {@link #createDefaultSkin()}. Both states are
 * pushed by the list view before {@link #updateItem(RXListSection, boolean)} runs, so
 * a custom header can read them while rendering.
 */
public class RXListSectionCell extends Cell<RXListSection> {

    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass COLLAPSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("collapsed");
    // The same "focused" pseudo-class Cell exposes; header cells are never
    // focus-traversable, so Cell's own Node-focus listener never fires and this
    // manual toggle is the sole writer.
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

    /**
     * Creates an empty section-header cell.
     */
    public RXListSectionCell() {
        getStyleClass().add("rx-list-section-header");
    }

    /**
     * Binds this cell to the given section. Intended for the skin / viewport that
     * hosts this cell; a {@code null} section renders the cell empty.
     *
     * @param section the section to display, or {@code null} for empty
     */
    public final void updateSection(RXListSection section) {
        updateItem(section, section == null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base implementation clears text and graphic when the cell is empty
     * and otherwise renders nothing — rendering of a non-empty section is the job
     * of the section-header factory (or the list view's default key-text factory).
     */
    @Override
    protected void updateItem(RXListSection item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXListSectionCellSkin(this);
    }

    // ==================== List View ====================

    private final ReadOnlyObjectWrapper<RXListView<?>> listView = new ReadOnlyObjectWrapper<>(this, "listView");

    /**
     * The list view that owns this cell.
     *
     * @return the read-only list-view property
     */
    public final ReadOnlyObjectProperty<RXListView<?>> listViewProperty() {
        return listView.getReadOnlyProperty();
    }

    /**
     * Returns the list view that owns this cell.
     *
     * @return the owning list view, or {@code null} if not attached
     */
    public final RXListView<?> getListView() {
        return listView.get();
    }

    /**
     * Updates the owning list view. Intended for the skin / viewport that hosts
     * this cell; called once when the cell is created.
     *
     * @param value the owning list view
     */
    public final void updateListView(RXListView<?> value) {
        listView.set(value);
    }

    // ==================== Expansion ====================

    private final ReadOnlyBooleanWrapper collapsible = new ReadOnlyBooleanWrapper(this, "collapsible") {
        @Override
        protected void invalidated() {
            updateExpansionPseudoClasses();
            notifyAccessibleAttributeChanged(AccessibleAttribute.ROLE);
        }
    };

    /**
     * Whether this header is its section's expand / collapse toggle, that is,
     * whether the owning list view's collapsible sections are on. Pushed by the list
     * view.
     *
     * @return the read-only collapsible property
     */
    public final ReadOnlyBooleanProperty collapsibleProperty() {
        return collapsible.getReadOnlyProperty();
    }

    /**
     * Returns whether this header is its section's expand / collapse toggle.
     *
     * @return {@code true} if the header toggles its section
     */
    public final boolean isCollapsible() {
        return collapsible.get();
    }

    private final ReadOnlyBooleanWrapper expanded = new ReadOnlyBooleanWrapper(this, "expanded", true) {
        @Override
        protected void invalidated() {
            updateExpansionPseudoClasses();
            notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
        }
    };

    /**
     * Whether the section's items are shown — {@code false} only while the header
     * is {@link #collapsibleProperty() collapsible} and its section is collapsed.
     * Pushed by the list view.
     *
     * @return the read-only expanded property
     */
    public final ReadOnlyBooleanProperty expandedProperty() {
        return expanded.getReadOnlyProperty();
    }

    /**
     * Returns whether the section's items are shown.
     *
     * @return {@code true} unless the section is collapsed
     */
    public final boolean isExpanded() {
        return expanded.get();
    }

    /**
     * Updates the collapsible and expanded state. Intended for the skin / viewport
     * that hosts this cell; a non-collapsible header is always expanded.
     *
     * @param collapsible whether the header toggles its section
     * @param expanded    whether the section's items are shown; ignored (taken as
     *                    {@code true}) when {@code collapsible} is {@code false}
     */
    public final void updateExpansion(boolean collapsible, boolean expanded) {
        this.collapsible.set(collapsible);
        this.expanded.set(!collapsible || expanded);
    }

    private void updateExpansionPseudoClasses() {
        boolean toggle = isCollapsible();
        boolean open = isExpanded();
        pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, toggle && open);
        pseudoClassStateChanged(COLLAPSED_PSEUDO_CLASS, toggle && !open);
    }

    // ==================== Focus ring ====================

    /**
     * Sets the keyboard focus ring on this header. Intended for the skin /
     * viewport: with collapsible sections the list view's keyboard cursor can rest
     * on a header row.
     *
     * @param focused whether this header holds the keyboard cursor
     */
    public final void updateListFocus(boolean focused) {
        pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
    }

    // ==================== Accessibility ====================

    /**
     * Reports a collapsible header as a disclosure ({@link AccessibleRole#TITLED_PANE})
     * with its {@link AccessibleAttribute#EXPANDED} state; other attributes, and all
     * attributes of a non-collapsible header, defer to the superclass.
     *
     * @param attribute  the requested accessible attribute
     * @param parameters optional attribute parameters
     * @return the attribute value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (isCollapsible() && attribute == AccessibleAttribute.ROLE) {
            return AccessibleRole.TITLED_PANE;
        }
        if (isCollapsible() && attribute == AccessibleAttribute.EXPANDED) {
            return isExpanded();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    /**
     * Expands or collapses the section of a collapsible header on
     * {@link AccessibleAction#EXPAND} / {@link AccessibleAction#COLLAPSE}; other
     * actions defer to the superclass.
     *
     * @param action     the requested accessible action
     * @param parameters optional action parameters
     */
    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        RXListView<?> view = getListView();
        RXListSection section = getItem();
        boolean toggles = isCollapsible() && view != null && section != null;
        if (toggles && action == AccessibleAction.EXPAND) {
            view.setSectionExpanded(section.key(), true);
        } else if (toggles && action == AccessibleAction.COLLAPSE) {
            view.setSectionExpanded(section.key(), false);
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }
}
