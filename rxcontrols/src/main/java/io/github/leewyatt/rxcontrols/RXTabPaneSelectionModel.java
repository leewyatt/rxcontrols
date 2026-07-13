package io.github.leewyatt.rxcontrols;

import javafx.collections.ObservableList;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.SingleSelectionModel;

/**
 * The built-in {@link SingleSelectionModel} for {@link RXTabPane}. It is
 * deliberately <b>thin</b>: it answers "what is selected" against the pane's
 * {@code tabs} list and nothing more. It does not listen to the tabs list and
 * does not mutate {@link RXTab#selectedProperty() RXTab.selected} or
 * {@link RXTab#tabPaneProperty() RXTab.tabPane} — the control owns those so a
 * custom or replaced selection model stays correct without extra wiring.
 */
public class RXTabPaneSelectionModel extends SingleSelectionModel<RXTab> {

    private final RXTabPane tabPane;

    /**
     * Creates a selection model bound to the given pane.
     *
     * @param tabPane the owning pane; must not be {@code null}
     * @throws NullPointerException if {@code tabPane} is {@code null}
     */
    public RXTabPaneSelectionModel(RXTabPane tabPane) {
        if (tabPane == null) {
            throw new NullPointerException("tabPane");
        }
        this.tabPane = tabPane;
    }

    /**
     * Selects the tab at the given index. {@code -1} clears the selection; an
     * out-of-range index is ignored. May target a disabled tab (programmatic
     * selection is not restricted by disabled state).
     *
     * @param index the tab index, or {@code -1} to clear
     */
    @Override
    public void select(int index) {
        if (index == -1) {
            clearSelection();
            return;
        }
        if (index < 0 || index >= getItemCount()) {
            return;
        }
        if (index == getSelectedIndex() && getSelectedItem() == getModelItem(index)) {
            return;
        }
        setSelectedIndex(index);
        // Re-read the index before deriving the item: a re-entrant listener on
        // the just-set selectedIndex may have redirected the selection, so both
        // properties must derive from the same re-read index (RT-32139 defense).
        int current = getSelectedIndex();
        setSelectedItem(current >= 0 && current < getItemCount() ? getModelItem(current) : null);
        tabPane.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
    }

    /**
     * Selects the first tab equal to {@code tab}. A {@code null} argument or a
     * tab not present in the list is ignored (no selection change), matching the
     * native {@code TabPane} model.
     *
     * @param tab the tab to select
     */
    @Override
    public void select(RXTab tab) {
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            RXTab candidate = getModelItem(i);
            if (candidate != null && candidate.equals(tab)) {
                select(i);
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RXTab getModelItem(int index) {
        ObservableList<RXTab> tabs = tabPane.getTabs();
        if (index < 0 || index >= tabs.size()) {
            return null;
        }
        return tabs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getItemCount() {
        return tabPane.getTabs().size();
    }
}
