package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXBreadcrumbBarSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A breadcrumb trail: the path from a root to the current location, with each
 * ancestor a link back up the hierarchy.
 *
 * <p><b>Path.</b> The {@link #itemsProperty() items} list is the whole state: the
 * complete root-to-current path, whose last element is the current location. There
 * is no separate "selected" item. Positions, not values, identify crumbs, so equal
 * or {@code null} values may repeat. Item text comes from the
 * {@link #converterProperty() converter}, an optional icon from the
 * {@link #graphicFactoryProperty() graphicFactory}. The separator between positions
 * is a CSS shape unless a {@link #separatorFactoryProperty() separatorFactory}
 * supplies text or another node.</p>
 *
 * <p><b>Navigation is a request.</b> Activating an ancestor fires an
 * {@link RXBreadcrumbEvent} carrying the complete requested path; by default the
 * bar does not change its own path, so the application stays in charge of routing
 * (including asynchronous checks) and commits with
 * {@code bar.getItems().setAll(event.getPath())}. Turn on
 * {@link #autoNavigateProperty() autoNavigate} to have unanswered requests committed
 * automatically. Programmatic path changes never fire requests.</p>
 *
 * <p><b>Branch menus.</b> A {@link #childrenFactoryProperty() childrenFactory}
 * answers one question about the hierarchy — what lies directly below a path — and
 * the bar derives the rest: a position gets a separate arrow button that opens a
 * menu of its alternatives ({@link BranchMode#SIBLINGS}, the children of its parent)
 * or of the next level down ({@link BranchMode#CHILDREN}). The same factory serves
 * both modes. The arrow is distinct from the item text: opening the menu never
 * navigates, choosing an entry fires a request. The current item keeps its arrow
 * even when its text is not navigable. A
 * {@link #branchFilterProperty() branchFilter} keeps levels that should not be
 * listed (thousands of records, say) out of the menus.</p>
 *
 * <p>Flat data looks its children up by the last path element:</p>
 * <pre>{@code
 * bar.setChildrenFactory(path -> path.isEmpty() ? List.of() : catalog.get(path.get(path.size() - 1)));
 * }</pre>
 *
 * <p>A tree needs no adapter when the nodes themselves are the items — the path is
 * the ancestor chain of the current node:</p>
 * <pre>{@code
 * RXBreadcrumbBar<TreeItem<String>> bar = new RXBreadcrumbBar<>();
 * bar.setChildrenFactory(path -> path.isEmpty() ? List.of(root) : path.get(path.size() - 1).getChildren());
 * }</pre>
 *
 * <p><b>Fitting.</b> When the path does not fit its width (or exceeds
 * {@link #maxVisibleItemsProperty() maxVisibleItems}), a contiguous run of middle
 * positions collapses into one overflow button whose menu lists them in path
 * order. The root is kept as long as root, overflow and current fit, then it
 * collapses too; the current item is the last to give way and is ellipsized down to
 * its minimum width, which the bar reports as its own minimum width.</p>
 *
 * <p>Every interactive part is a regular Tab stop; separators are not focusable.
 * The default accessibility texts are English ("breadcrumb", "current page",
 * "&lt;item&gt; options", "Show hidden path items"); an application can replace the
 * bar's own with {@link #setAccessibleRoleDescription(String)} and
 * {@link #setAccessibleText(String)}. Not thread-safe; use on the JavaFX
 * Application Thread.</p>
 *
 * @param <T> the item type
 */
public class RXBreadcrumbBar<T> extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-breadcrumb-bar";
    private static final BranchMode DEFAULT_BRANCH_MODE = BranchMode.SIBLINGS;
    private static final boolean DEFAULT_CURRENT_ITEM_NAVIGABLE = false;
    private static final boolean DEFAULT_AUTO_NAVIGATE = false;
    private static final int DEFAULT_MAX_VISIBLE_ITEMS = 0;

    /**
     * What a branch menu lists, and therefore which path the children factory is
     * asked about and where a chosen entry lands in the requested path.
     */
    public enum BranchMode {
        /**
         * Alternatives for the position, the children of its parent: a chosen entry
         * replaces it and everything after it is dropped.
         */
        SIBLINGS,
        /**
         * The level below the position, its own children: a chosen entry is appended
         * after it and everything that followed is dropped.
         */
        CHILDREN
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty breadcrumb bar.
     */
    public RXBreadcrumbBar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleRoleDescription("breadcrumb");
    }

    /**
     * Creates a breadcrumb bar showing the given path.
     *
     * @param path the path, root first
     */
    @SafeVarargs
    public RXBreadcrumbBar(T... path) {
        this();
        getItems().setAll(path);
    }

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXBreadcrumbBarSkin<>(this);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * Rebuilds every crumb, re-reading item text and graphics and deciding again
     * which positions have a branch arrow. Call it after mutating an item in place
     * (renaming a mutable value) or when the hierarchy changed in a way that adds or
     * removes an arrow; replacing an element of {@link #getItems()} rebuilds on its
     * own, and menu entries are always read fresh when a menu opens.
     */
    public void refresh() {
        getProperties().put(FeedbackSignals.BREADCRUMB_REFRESH, Boolean.TRUE);
    }

    // ==================== Items ====================

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The complete path, root first; the last element is the current location. A
     * {@code null} list is treated as an empty path.
     *
     * @return the items property
     */
    public final ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    /**
     * Returns the path.
     *
     * @return the path list, or {@code null}
     */
    public final ObservableList<T> getItems() {
        return items.get();
    }

    /**
     * Sets the path list.
     *
     * @param value the path list, or {@code null} for an empty path
     */
    public final void setItems(ObservableList<T> value) {
        items.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Converter supplying the text of crumbs and menu entries. Only
     * {@code toString} is used; {@code fromString} is never called. When
     * {@code null}, the text is {@code item.toString()}, or the empty string for a
     * {@code null} item.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null} for the {@code toString()} fallback
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Graphic Factory ====================

    private final ObjectProperty<Callback<T, Node>> graphicFactory =
            new SimpleObjectProperty<>(this, "graphicFactory");

    /**
     * Factory for the icon shown before an item's text, in the trail and in menus.
     * It is called once per place the item appears and must return a new node on
     * every call (a node can have only one parent); a {@code null} result means no
     * icon. When the factory itself is {@code null}, no item has an icon.
     *
     * @return the graphic factory property
     */
    public final ObjectProperty<Callback<T, Node>> graphicFactoryProperty() {
        return graphicFactory;
    }

    /**
     * Returns the graphic factory.
     *
     * @return the graphic factory, or {@code null}
     */
    public final Callback<T, Node> getGraphicFactory() {
        return graphicFactory.get();
    }

    /**
     * Sets the graphic factory.
     *
     * @param value the graphic factory, or {@code null} for no icons
     */
    public final void setGraphicFactory(Callback<T, Node> value) {
        graphicFactory.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * The node shown while the path is empty, a hint such as
     * {@code new Label("No location")}. It is display only: it is not part of the
     * path and fires no request. When {@code null} (the default) an empty bar
     * shows nothing. Either way an empty bar keeps the height of one row of text,
     * so content below it does not move when a path appears.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder.
     *
     * @return the placeholder, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the placeholder.
     *
     * @param value the node to show while the path is empty, or {@code null} for none
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== Separator Factory ====================

    private final ObjectProperty<Supplier<Node>> separatorFactory =
            new SimpleObjectProperty<>(this, "separatorFactory");

    /**
     * Factory for the node drawn between two path positions, for a separator that
     * is text or any other node rather than a shape:
     * {@code bar.setSeparatorFactory(() -> new Label("/"))}. When {@code null} (the
     * default) the separator is a chevron shape, restyled from CSS through
     * {@code .rx-breadcrumb-bar > .separator > .icon}; a {@code null} result falls
     * back to that shape as well.
     *
     * <p>The factory is called once per separator and must return a new node on
     * every call (a node can have only one parent). The nodes must be alike: the
     * bar measures one separator and uses its width for all of them. Each node is
     * placed inside the bar's {@code .separator} container, which stays the place
     * to set spacing from CSS and does not receive mouse input; the node should
     * not be focusable.</p>
     *
     * <p>Under a right-to-left orientation shapes are mirrored but text glyphs are
     * not, so a directional character such as "›" keeps pointing right; choose the
     * character by orientation, or use a direction-neutral one.</p>
     *
     * @return the separator factory property
     */
    public final ObjectProperty<Supplier<Node>> separatorFactoryProperty() {
        return separatorFactory;
    }

    /**
     * Returns the separator factory.
     *
     * @return the separator factory, or {@code null}
     */
    public final Supplier<Node> getSeparatorFactory() {
        return separatorFactory.get();
    }

    /**
     * Sets the separator factory.
     *
     * @param value the separator factory, or {@code null} for the default shape
     */
    public final void setSeparatorFactory(Supplier<Node> value) {
        separatorFactory.set(value);
    }

    // ==================== Children Factory ====================

    private final ObjectProperty<Callback<List<T>, List<T>>> childrenFactory =
            new SimpleObjectProperty<>(this, "childrenFactory");

    /**
     * Supplies the level directly below a path, which is all the bar needs to know
     * about the hierarchy. The factory receives an unmodifiable path, root first,
     * and returns its children; {@code null} is treated as no children. Which path
     * a position asks about follows the {@link #branchModeProperty() branchMode}:
     * its own path in {@link BranchMode#CHILDREN}, its parent's path in
     * {@link BranchMode#SIBLINGS}. A chosen entry is always appended to the path
     * that was asked about.
     *
     * <p><b>The empty path.</b> In {@code SIBLINGS} mode the root position asks
     * about the empty path, whose children are the alternative roots (drives,
     * workspaces). A factory must therefore accept an empty list in either mode;
     * return an empty list from it when the root has no alternatives.</p>
     *
     * <p><b>When it is called.</b> Menu entries are read when a branch menu opens,
     * so they are always current; a result that is empty by then opens no menu.
     * Whether a position shows an arrow is settled earlier, when the crumbs are
     * rebuilt (path or a rendering property changes, or {@link #refresh()}):
     * a position that provably has entries — every position but the last in
     * {@code CHILDREN} mode, every position but the root in {@code SIBLINGS}
     * mode — gets one without a call, and the one remaining position is asked
     * once. With a {@link #branchFilterProperty() branchFilter} the filter decides
     * instead and the factory is not called during a rebuild at all. It is never
     * called during layout.</p>
     *
     * @return the children factory property
     */
    public final ObjectProperty<Callback<List<T>, List<T>>> childrenFactoryProperty() {
        return childrenFactory;
    }

    /**
     * Returns the children factory.
     *
     * @return the children factory, or {@code null}
     */
    public final Callback<List<T>, List<T>> getChildrenFactory() {
        return childrenFactory.get();
    }

    /**
     * Sets the children factory.
     *
     * @param value the children factory, or {@code null} for no branch menus
     */
    public final void setChildrenFactory(Callback<List<T>, List<T>> value) {
        childrenFactory.set(value);
    }

    // ==================== Branch Filter ====================

    private final ObjectProperty<Predicate<List<T>>> branchFilter =
            new SimpleObjectProperty<>(this, "branchFilter");

    /**
     * Decides which levels are offered as a branch menu. The filter receives the
     * same unmodifiable path the {@link #childrenFactoryProperty() childrenFactory}
     * would be asked about and returns whether that path's children should be
     * listed; a position whose path is rejected has no arrow. Because it is asked
     * about a path rather than a position, one filter is correct in both branch
     * modes: rejecting {@code [Home, Orders]} hides the arrow of an order in
     * {@code SIBLINGS} mode and the arrow of "Orders" in {@code CHILDREN} mode.
     *
     * <p>When set, the filter alone decides which positions have an arrow, so it
     * should also reject paths that have no children. When {@code null} (the
     * default) the bar decides as described for the children factory. The filter
     * has no effect without a children factory.</p>
     *
     * @return the branch filter property
     */
    public final ObjectProperty<Predicate<List<T>>> branchFilterProperty() {
        return branchFilter;
    }

    /**
     * Returns the branch filter.
     *
     * @return the branch filter, or {@code null}
     */
    public final Predicate<List<T>> getBranchFilter() {
        return branchFilter.get();
    }

    /**
     * Sets the branch filter.
     *
     * @param value the branch filter, or {@code null} to let the bar decide
     */
    public final void setBranchFilter(Predicate<List<T>> value) {
        branchFilter.set(value);
    }

    // ==================== Branch Mode ====================

    private final ObjectProperty<BranchMode> branchMode =
            new SimpleObjectProperty<>(this, "branchMode", DEFAULT_BRANCH_MODE);

    /**
     * What every branch menu of this bar lists. One mode per bar keeps the arrow's
     * meaning unambiguous. Defaults to {@link BranchMode#SIBLINGS}; {@code null} is
     * treated as {@code SIBLINGS}.
     *
     * @return the branch mode property
     */
    public final ObjectProperty<BranchMode> branchModeProperty() {
        return branchMode;
    }

    /**
     * Returns the branch mode.
     *
     * @return the branch mode, or {@code null}
     */
    public final BranchMode getBranchMode() {
        return branchMode.get();
    }

    /**
     * Sets the branch mode.
     *
     * @param value the branch mode, or {@code null} for {@code SIBLINGS}
     */
    public final void setBranchMode(BranchMode value) {
        branchMode.set(value);
    }

    // ==================== Current Item Navigable ====================

    private final BooleanProperty currentItemNavigable =
            new SimpleBooleanProperty(this, "currentItemNavigable", DEFAULT_CURRENT_ITEM_NAVIGABLE);

    /**
     * Whether the current (last) item is a link too. When {@code false} (the
     * default) it is plain text and never fires a request; when {@code true},
     * activating it fires a request for the unchanged path on every activation.
     *
     * @return the current-item-navigable property
     */
    public final BooleanProperty currentItemNavigableProperty() {
        return currentItemNavigable;
    }

    /**
     * Returns whether the current item is navigable.
     *
     * @return {@code true} if the current item is a link
     */
    public final boolean isCurrentItemNavigable() {
        return currentItemNavigable.get();
    }

    /**
     * Sets whether the current item is navigable.
     *
     * @param value {@code true} to make the current item a link
     */
    public final void setCurrentItemNavigable(boolean value) {
        currentItemNavigable.set(value);
    }

    // ==================== Auto Navigate ====================

    private final BooleanProperty autoNavigate =
            new SimpleBooleanProperty(this, "autoNavigate", DEFAULT_AUTO_NAVIGATE);

    /**
     * Whether the bar commits a navigation request itself. When {@code true}, after
     * a request is dispatched the bar writes its path into {@link #getItems()}
     * ({@code setAll}) unless a handler consumed the request or the path already
     * changed during dispatch. Defaults to {@code false}: requests are only reported.
     *
     * @return the auto-navigate property
     */
    public final BooleanProperty autoNavigateProperty() {
        return autoNavigate;
    }

    /**
     * Returns whether requests are committed automatically.
     *
     * @return {@code true} if auto navigation is on
     */
    public final boolean isAutoNavigate() {
        return autoNavigate.get();
    }

    /**
     * Sets whether requests are committed automatically.
     *
     * @param value {@code true} to commit unanswered requests automatically
     */
    public final void setAutoNavigate(boolean value) {
        autoNavigate.set(value);
    }

    // ==================== Max Visible Items ====================

    private final IntegerProperty maxVisibleItems =
            new SimpleIntegerProperty(this, "maxVisibleItems", DEFAULT_MAX_VISIBLE_ITEMS);

    /**
     * The maximum number of path positions shown at once, the overflow button not
     * counted; {@code 0} or less means no limit (the default). It is an upper bound
     * applied together with width fitting: whichever hides more wins. The current
     * item always counts as one of the visible positions, so {@code 1} shows only
     * the overflow button and the current item.
     *
     * @return the max-visible-items property
     */
    public final IntegerProperty maxVisibleItemsProperty() {
        return maxVisibleItems;
    }

    /**
     * Returns the visible-position limit.
     *
     * @return the limit, {@code 0} or less for none
     */
    public final int getMaxVisibleItems() {
        return maxVisibleItems.get();
    }

    /**
     * Sets the visible-position limit.
     *
     * @param value the limit, {@code 0} or less for none
     */
    public final void setMaxVisibleItems(int value) {
        maxVisibleItems.set(value);
    }

    // ==================== On Navigate ====================

    private ObjectProperty<EventHandler<RXBreadcrumbEvent<T>>> onNavigate;

    /**
     * Handler for navigation requests.
     *
     * @return the on-navigate property
     */
    public final ObjectProperty<EventHandler<RXBreadcrumbEvent<T>>> onNavigateProperty() {
        if (onNavigate == null) {
            onNavigate = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXBreadcrumbEvent.navigateType(), get());
                }

                @Override
                public Object getBean() {
                    return RXBreadcrumbBar.this;
                }

                @Override
                public String getName() {
                    return "onNavigate";
                }
            };
        }
        return onNavigate;
    }

    /**
     * Returns the navigation request handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXBreadcrumbEvent<T>> getOnNavigate() {
        return onNavigate == null ? null : onNavigate.get();
    }

    /**
     * Sets the navigation request handler.
     *
     * @param value the handler, or {@code null} for none
     */
    public final void setOnNavigate(EventHandler<RXBreadcrumbEvent<T>> value) {
        onNavigateProperty().set(value);
    }
}
