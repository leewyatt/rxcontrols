package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPlacement;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarItemBase;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import io.github.leewyatt.rxcontrols.internal.SidebarVisibility;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Skin for {@link RXSidebar}. Builds the five-region container (header,
 * pinned-top, scrollable main, pinned-bottom, footer), renders every
 * {@link RXSidebarItem} of the tree into a row it owns, locks the sidebar width
 * via the {@code compute*Width} methods, and keeps each top-level graphic in a
 * fixed left column so icons never move between {@link SidebarMode#EXPANDED}
 * and {@link SidebarMode#MINI}.
 *
 * <p>The rows belong to this skin; only an item's {@code graphic} and
 * {@code trailing} nodes are the caller's. A row takes them in when it is built
 * and lets go of them on dispose, unless a replacing skin has already moved
 * them into its own row.</p>
 */
public class RXSidebarSkin extends RXSkinBase<RXSidebar> {

    // ==================== Constants ====================

    private static final double ITEM_GAP = 4.0;
    // The width of the icon column. It is a nominal size rather than a measured
    // one: graphics of different sizes are centred in it so every row's text
    // starts at the same x, and it is what miniWidth centres on. Wider than the
    // stylesheet's default ".graphic", which leaves the icon some air.
    private static final double ICON_COLUMN_WIDTH = 24.0;
    private static final double GRAPHIC_TEXT_GAP = 12.0;
    private static final double TRAILING_GAP = 8.0;
    private static final Insets ROW_MARGIN = new Insets(ITEM_GAP / 2.0, 0.0, ITEM_GAP / 2.0, 0.0);

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass CHILD_SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("child-selected");
    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass COLLAPSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("collapsed");
    private static final PseudoClass SELECTABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("selectable");
    private static final PseudoClass ARMED_PSEUDO_CLASS = PseudoClass.getPseudoClass("armed");
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");
    // How long the pointer rests on a group before its flyout opens, and how
    // long it may be off the group and its flyouts before they close. The
    // second is what lets the pointer cross from the group to the flyout.
    private static final Duration FLYOUT_HOVER_DELAY = Duration.millis(100.0);

    // ==================== Container tree ====================

    private final VBox root = new VBox();
    private final StackPane headerSlot = new StackPane();
    private final VBox topBox = new VBox();
    private final ScrollPane mainScroll = new ScrollPane();
    private final VBox mainBox = new VBox();
    // Takes the place of mainBox in the scroll pane while it is on display.
    private final StackPane placeholderSlot = new StackPane();
    // What the items' visible flags and the filter leave on display. Rebuilt
    // when the filter, the tree or a visible flag changes, not on every refresh.
    private Set<RXSidebarItemBase> shownItems = Set.of();
    // Whether shownItems was computed under a filter. Kept with it rather than
    // read off the control, so the two cannot disagree.
    private boolean filtering;
    private final VBox bottomBox = new VBox();
    private final StackPane footerSlot = new StackPane();
    // Set on the container only while the width is in transition; see wipeExtra().
    private final Rectangle wipeClip = new Rectangle();

    private final Map<RXSidebarItemBase, ItemView<?>> views = new IdentityHashMap<>();

    // The open flyouts, shallowest first: each one is owned by a row of the one
    // before it, the first by a top-level row.
    private final List<Flyout> flyouts = new ArrayList<>();
    private final PauseTransition flyoutOpenTimer = new PauseTransition(FLYOUT_HOVER_DELAY);
    private final PauseTransition flyoutCloseTimer = new PauseTransition(FLYOUT_HOVER_DELAY);
    // The top-level group the open timer is running for.
    private RowView hoveredGroup;

    // True from the press of Enter or Space that activated a row until that key
    // is released: the presses the platform repeats while a key is held are not
    // further activations.
    private boolean activationKeyDown;
    private final EventHandler<KeyEvent> keyReleasedFilter = this::onKeyReleased;

    // True while the sidebar rests in MINI: top-level rows show their graphic
    // only. False from the moment a transition starts, so text is wiped and
    // revealed by the moving edge rather than popping.
    private boolean compactRows;

    // The Tab-stop rule keys off the scene's focus owner, so it has to re-run on every
    // focus move — including a plain requestFocus() from the application, which no
    // roving or click path observes. Node.focused cannot serve here: it is false
    // whenever the window is not focused.
    private final ChangeListener<Node> focusOwnerListener = (obs, old, node) -> {
        activationKeyDown = false;
        refreshTabStop();
    };

    // The scene focusOwnerListener and keyReleasedFilter are attached to. Re-attached whenever the control
    // changes scene, so it cannot go through the disposer (which captures its target
    // at registration); released in disposeSkin instead.
    private Scene trackedScene;

    // Rebuilt per transition; NOT registered with the disposer (which would hold a
    // stale reference). Stopped explicitly in disposeSkin().
    private Timeline animation;

    // Whether the refresh in progress may animate groups opening and closing.
    // Set only for changes the user watches happen: a group toggling and a
    // mode change.
    private boolean animateGroups;
    private int runningGroupAnimations;

    // The row to keep in view, checked after each layout pass until every
    // running transition has settled: the row keeps moving while the groups
    // above it unfold.
    private RowView revealTarget;
    // Set when a sync builds the row of the selected item: at construction, and
    // when an item selected ahead of time is added.
    private boolean selectionArrived;
    private Scene revealScene;
    private final Runnable revealListener = this::revealAfterLayout;

    // 0 = MINI, 1 = EXPANDED. Must equal (DEFAULT_MODE == EXPANDED ? 1 : 0).
    private final DoubleProperty expansionFraction =
            new SimpleDoubleProperty(this, "expansionFraction", 1.0) {
                @Override
                protected void invalidated() {
                    getSkinnable().requestLayout();
                }
            };

    // ==================== Constructor ====================

    /**
     * Constructs the skin for the given sidebar.
     *
     * @param control the sidebar this skin is attached to
     */
    public RXSidebarSkin(RXSidebar control) {
        super(control);

        root.getStyleClass().add("container");
        headerSlot.getStyleClass().add("header");
        topBox.getStyleClass().addAll("section", "top");
        mainScroll.getStyleClass().add("content");
        mainBox.getStyleClass().add("section");
        placeholderSlot.getStyleClass().add("placeholder");
        bottomBox.getStyleClass().addAll("section", "bottom");
        footerSlot.getStyleClass().add("footer");

        mainScroll.setContent(mainBox);
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        mainScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        mainScroll.setFocusTraversable(false);
        VBox.setVgrow(mainScroll, Priority.ALWAYS);
        // A VBox short of room shrinks every child towards its minimum. Only
        // the scrolling list may give way; the bands around it keep their height.
        for (Region fixed : List.of(headerSlot, topBox, bottomBox, footerSlot)) {
            fixed.setMinHeight(Region.USE_PREF_SIZE);
        }

        root.getChildren().addAll(headerSlot, topBox, mainScroll, bottomBox, footerSlot);
        // setAll (not add): a replacing skin's constructor takes ownership of the
        // children, clearing any nodes left by a prior skin.
        getChildren().setAll(root);

        updateHeader();
        updateFooter();
        disposer.registerListener(control.headerProperty(), this::updateHeader);
        disposer.registerListener(control.footerProperty(), this::updateFooter);
        updatePlaceholder();
        disposer.registerListener(control.placeholderProperty(), this::updatePlaceholder);
        disposer.registerListener(control.filterProperty(), this::onFilterChanged);
        disposer.registerListener(control.flyoutTriggerProperty(), this::cancelFlyoutTimers);
        // Rows in a flyout are in another scene and do not inherit the state.
        disposer.registerListener(control.disabledProperty(), (obs, old, disabled) -> closeFlyoutsFrom(0));
        flyoutOpenTimer.setOnFinished(event -> openHoveredGroup());
        flyoutCloseTimer.setOnFinished(event -> closeFlyoutsFrom(0));
        disposer.registerDisposeTask(this::cancelFlyoutTimers);

        disposer.registerListener(control.getTopItems(), this::syncTree);
        disposer.registerListener(control.getItems(), this::syncTree);
        disposer.registerListener(control.getBottomItems(), this::syncTree);

        disposer.registerListener(control.modeProperty(), this::onModeChanged);
        disposer.registerListener(control.miniWidthProperty(), this::requestRowLayout);
        disposer.registerListener(control.expandedWidthProperty(), this::requestRowLayout);
        disposer.registerListener(control.indentProperty(), this::requestRowLayout);
        disposer.registerListener(control.selectedItemProperty(), (obs, old, item) -> {
            refreshAll();
            reveal(item);
        });
        disposer.registerListener(control.sceneProperty(), this::trackFocusOwner);

        // A capturing FILTER on root, not a bubbling handler: a ScrollPane consumes
        // arrow keys for scrolling on the way up, so a bubbling handler above it
        // would never see them.
        disposer.registerEventFilter(root, KeyEvent.KEY_PRESSED, this::onKeyPressed);

        boolean expanded = committedMode() == SidebarMode.EXPANDED;
        expansionFraction.set(expanded ? 1.0 : 0.0);
        compactRows = !expanded;
        syncTree();
        trackFocusOwner();
    }

    // Follows the control across scenes, keeping focusOwnerListener attached to the
    // live one; also re-runs the rule, since the focus owner changes with the scene.
    private void trackFocusOwner() {
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().removeListener(focusOwnerListener);
            trackedScene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedFilter);
        }
        trackedScene = getSkinnable().getScene();
        activationKeyDown = false;
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().addListener(focusOwnerListener);
            // On the scene, not on the sidebar: the release has to be seen
            // wherever focus is once the activation has run.
            trackedScene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleasedFilter);
        }
        refreshTabStop();
        closeFlyoutsFrom(0);
        // The scene left behind no longer lays this control out.
        stopRevealing();
        reveal(getSkinnable().getSelectedItem());
    }

    // ==================== Header / Footer slots ====================

    private void updateHeader() {
        swapSlot(headerSlot, getSkinnable().getHeader());
    }

    private void updateFooter() {
        swapSlot(footerSlot, getSkinnable().getFooter());
    }

    private void updatePlaceholder() {
        swapSlot(placeholderSlot, getSkinnable().getPlaceholder());
        showPlaceholderIfEmpty();
    }

    // The placeholder stands in for the scrolling list when the filter leaves
    // none of it on display. The settled rail is too narrow to hold it.
    private void showPlaceholderIfEmpty() {
        RXSidebar control = getSkinnable();
        boolean empty = filtering && control.getPlaceholder() != null && !compactRows
                && !hasShown(control.getItems());
        Node content = empty ? placeholderSlot : mainBox;
        if (mainScroll.getContent() != content) {
            mainScroll.setContent(content);
        }
    }

    private boolean hasShown(List<RXSidebarItemBase> items) {
        for (RXSidebarItemBase item : items) {
            if (isShown(item)) {
                return true;
            }
        }
        return false;
    }

    private static void swapSlot(StackPane slot, Node content) {
        if (content == null) {
            slot.getChildren().clear();
        } else {
            slot.getChildren().setAll(content);
        }
        slot.setVisible(content != null);
        slot.setManaged(content != null);
    }

    // ==================== Tree -> rows ====================

    // Brings the row tree in line with the item tree. Runs on any structural
    // change anywhere in the tree; containers whose items did not change keep
    // their nodes untouched, so focus and CSS state survive.
    private void syncTree() {
        RXSidebar control = getSkinnable();
        // First, while no row has moved yet: taking a row that owns a popup
        // out of its scene hides the popup from inside the list change.
        for (int i = 0; i < flyouts.size(); i++) {
            RXSidebarItem owner = flyouts.get(i).owner.item;
            if (owner.getSidebar() != control || !isGroup(owner)) {
                closeFlyoutsFrom(i);
                break;
            }
        }
        syncList(control.getTopItems(), topBox.getChildren(), 0);
        syncList(control.getItems(), mainBox.getChildren(), 0);
        syncList(control.getBottomItems(), bottomBox.getChildren(), 0);

        Iterator<ItemView<?>> iterator = views.values().iterator();
        while (iterator.hasNext()) {
            ItemView<?> view = iterator.next();
            if (view.item.getSidebar() != control) {
                view.dispose();
                iterator.remove();
            }
        }
        refreshShown();
        if (selectionArrived) {
            selectionArrived = false;
            reveal(control.getSelectedItem());
        }
    }

    private void refreshAnimated() {
        animateGroups = true;
        try {
            refreshAll();
        } finally {
            animateGroups = false;
        }
    }

    private void syncList(List<RXSidebarItemBase> items, ObservableList<Node> target, int depth) {
        List<Node> nodes = new ArrayList<>();
        for (RXSidebarItemBase item : items) {
            ItemView<?> view = views.get(item);
            if (view == null) {
                view = createView(item);
                views.put(item, view);
                selectionArrived |= item instanceof RXSidebarItem candidate && candidate.isSelected();
            }
            view.depth = depth;
            view.syncChildren();
            view.collectNodes(nodes);
        }
        reconcile(target, nodes);
    }

    private ItemView<?> createView(RXSidebarItemBase item) {
        if (item instanceof RXSidebarSeparator separator) {
            return new SeparatorView(separator);
        }
        if (item instanceof RXSidebarHeader header) {
            return new CaptionView(header);
        }
        return new RowView((RXSidebarItem) item);
    }

    // Edits target into desired with the fewest removals: a node that keeps its
    // place is never taken out of the scene, which would drop its focus.
    private static void reconcile(ObservableList<Node> target, List<Node> desired) {
        if (target.equals(desired)) {
            return;
        }
        Set<Node> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        wanted.addAll(desired);
        target.removeIf(node -> !wanted.contains(node));
        for (int i = 0; i < desired.size(); i++) {
            Node node = desired.get(i);
            if (i < target.size() && target.get(i) == node) {
                continue;
            }
            target.remove(node);
            target.add(i, node);
        }
    }

    // ==================== Refresh ====================

    // Re-derives every row's state from the model. Idempotent; every change that
    // can affect what a row shows funnels through here.
    private void refreshAll() {
        Set<RXSidebarItem> selectionPath = Collections.newSetFromMap(new IdentityHashMap<>());
        RXSidebarItem selected = getSkinnable().getSelectedItem();
        if (selected != null && selected.isSelected()) {
            for (RXSidebarItem group = selected.getParent(); group != null; group = group.getParent()) {
                selectionPath.add(group);
            }
        }
        closeStaleFlyouts();
        for (ItemView<?> view : views.values()) {
            view.refresh(selectionPath);
        }
        // A pinned band with nothing on display takes no room, so the gap
        // its style sheet padding puts next to the list goes with it.
        setShown(topBox, hasShown(getSkinnable().getTopItems()));
        setShown(bottomBox, hasShown(getSkinnable().getBottomItems()));
        showPlaceholderIfEmpty();
        refreshTabStop();
    }

    private void refreshShown() {
        RXSidebar control = getSkinnable();
        Predicate<? super RXSidebarItem> filter = control.getFilter();
        shownItems = SidebarVisibility.shownItems(
                List.of(control.getTopItems(), control.getItems(), control.getBottomItems()), filter);
        filtering = filter != null;
        refreshAll();
    }

    private void onFilterChanged() {
        refreshShown();
        reveal(getSkinnable().getSelectedItem());
    }

    private void requestRowLayout() {
        for (ItemView<?> view : views.values()) {
            view.requestLayout();
        }
    }

    private boolean isShown(RXSidebarItemBase item) {
        return shownItems.contains(item);
    }

    private static boolean isGroup(RXSidebarItem item) {
        return !item.getItems().isEmpty();
    }

    // Whether a group's child items are on display below its row. Every
    // group folds away in MINI, where child items are reached through flyouts,
    // without its expanded state being touched, so it unfolds again by itself
    // on the way back. A filter holds every group open.
    private boolean isOpen(RXSidebarItem item) {
        if (committedMode() == SidebarMode.MINI) {
            return false;
        }
        return filtering || item.isExpanded();
    }

    private double iconInset() {
        return Math.max(0.0, (resolvedMiniWidth() - ICON_COLUMN_WIDTH) / 2.0);
    }

    private double indentOrDefault() {
        double value = getSkinnable().getIndent();
        return (Double.isFinite(value) && value >= 0.0) ? value : RXSidebar.DEFAULT_INDENT;
    }

    // How much wider the sidebar is when fully expanded than it is right now.
    // Rows lay their text and trailing parts out for the full width and let them
    // overflow by this much, clipped by the container, so a width transition
    // wipes over content that stays in place.
    private double wipeExtra() {
        return compactRows ? 0.0 : Math.max(0.0, resolvedExpandedWidth() - railWidth());
    }

    // ==================== Activation ====================

    private void activate(RowView view, boolean byKeyboard) {
        RXSidebarItem item = view.item;
        if (view.row.isDisabled()) {
            return;
        }
        if (isGroup(item)) {
            if (!opensFlyout(view)) {
                setExpandedByUser(view, !item.isExpanded());
            } else if (!byKeyboard && view.flyout != null && flyoutTriggerOrDefault() == FlyoutTrigger.CLICK) {
                closeFlyoutsFrom(flyouts.indexOf(view.flyout));
            } else {
                openFlyout(view, byKeyboard);
            }
            return;
        }
        if (item.isSelectable()) {
            getSkinnable().selectItem(item);
            if (getSkinnable().getSelectedItem() != item) {
                // A listener on the selection turned it down.
                return;
            }
        }
        closeFlyoutsFrom(0);
        EventHandler<ActionEvent> handler = item.getOnAction();
        if (handler != null) {
            handler.handle(new ActionEvent(item, null));
        }
    }

    // One activation per press of the key, however long it is held.
    private void activateByKey(RowView view) {
        if (!activationKeyDown) {
            activationKeyDown = true;
            activate(view, true);
        }
    }

    private void onKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            activationKeyDown = false;
        }
    }

    // A group has nothing to unfold into in MINI, a filter holds every group
    // open, and a bound property is not the sidebar's to write.
    private void setExpandedByUser(RowView view, boolean expanded) {
        if (committedMode() == SidebarMode.MINI) {
            return;
        }
        if (filtering) {
            return;
        }
        if (!view.item.expandedProperty().isBound()) {
            view.item.setExpanded(expanded);
        }
    }

    // ==================== Reveal ====================

    // Scrolls the item's row into view once layout has caught up. Only rows of
    // the scrolling list can be brought into view.
    private void reveal(RXSidebarItem item) {
        Scene scene = getSkinnable().getScene();
        if (!(views.get(item) instanceof RowView view) || scene == null) {
            revealTarget = null;
            return;
        }
        if (revealScene != scene) {
            stopRevealing();
            revealScene = scene;
            scene.addPostLayoutPulseListener(revealListener);
        }
        revealTarget = view;
    }

    private void revealAfterLayout() {
        RowView view = revealTarget;
        if (view != null && isOnDisplayIn(view.row, mainBox)) {
            scrollIntoView(view.row);
        }
        if (animation == null && runningGroupAnimations == 0) {
            stopRevealing();
        }
    }

    private void stopRevealing() {
        if (revealScene != null) {
            revealScene.removePostLayoutPulseListener(revealListener);
            revealScene = null;
        }
        revealTarget = null;
    }

    private static boolean isOnDisplayIn(Node node, Node container) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == container) {
                return true;
            }
            if (!current.isVisible()) {
                return false;
            }
        }
        return false;
    }

    // The minimal scroll: only when the row sits above or below the viewport.
    private void scrollIntoView(Node row) {
        scrollIntoView(mainScroll, mainBox, row);
    }

    private static void scrollIntoView(ScrollPane scroll, Region content, Node row) {
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double scrollable = content.getHeight() - viewportHeight;
        if (viewportHeight <= 0.0 || scrollable <= 0.0) {
            return;
        }
        Bounds bounds = content.sceneToLocal(row.localToScene(row.getBoundsInLocal()));
        double offset = scroll.getVvalue() * scrollable;
        if (bounds.getMinY() < offset) {
            scroll.setVvalue(Math.max(0.0, bounds.getMinY() / scrollable));
        } else if (bounds.getMaxY() > offset + viewportHeight) {
            scroll.setVvalue(Math.min(1.0, (bounds.getMaxY() - viewportHeight) / scrollable));
        }
    }

    // Moves keyboard focus to the row and keeps it in view.
    private void focusByKey(RowView view) {
        view.row.requestFocus();
        Flyout host = hostOf(view);
        if (host != null) {
            scrollIntoView(host.scroll, host.box, view.row);
        } else if (isOnDisplayIn(view.row, mainBox)) {
            scrollIntoView(view.row);
        }
    }

    // ==================== Flyouts ====================

    private FlyoutTrigger flyoutTriggerOrDefault() {
        FlyoutTrigger value = getSkinnable().getFlyoutTrigger();
        return value == null ? RXSidebar.DEFAULT_FLYOUT_TRIGGER : value;
    }

    private boolean isInFlyout(RXSidebarItemBase item) {
        return views.get(item.getParent()) instanceof RowView parent && parent.flyout != null;
    }

    // The flyout the row is on display in, or null for a row of the sidebar.
    private Flyout hostOf(RowView view) {
        return views.get(view.item.getParent()) instanceof RowView parent ? parent.flyout : null;
    }

    // Whether activating the group opens a flyout rather than unfolding it in
    // place: a top-level group while the sidebar rests in MINI, and any group
    // on display in a flyout.
    private boolean opensFlyout(RowView view) {
        if (!compactRows || (view.depth != 0 && hostOf(view) == null)) {
            return false;
        }
        for (RXSidebarItemBase child : view.item.getItems()) {
            if (isShown(child)) {
                return true;
            }
        }
        return false;
    }

    private void openFlyout(RowView owner, boolean focusFirst) {
        cancelFlyoutTimers();
        if (owner.flyout == null) {
            Flyout host = hostOf(owner);
            closeFlyoutsFrom(host == null ? 0 : flyouts.indexOf(host) + 1);
            Flyout flyout = new Flyout(owner);
            flyouts.add(flyout);
            flyout.takeRows();
            // The rows take their in-flyout form before the popup measures them.
            refreshAll();
            flyout.show();
        }
        // Showing fails without a showing window; the flyout has then already
        // been closed again.
        if (focusFirst && owner.flyout != null) {
            List<RowView> ring = owner.flyout.ring();
            if (!ring.isEmpty()) {
                ring.get(0).row.requestFocus();
            }
        }
    }

    // Closes the flyout at the index and every one after it, deepest first:
    // the rows go back into the sidebar before their flyout goes away.
    private void closeFlyoutsFrom(int index) {
        if (index < 0 || index >= flyouts.size()) {
            return;
        }
        if (index == 0) {
            // The open timer is left alone: it may be running for the group
            // the pointer has moved on to.
            flyoutCloseTimer.stop();
        }
        for (int i = flyouts.size() - 1; i >= index; i--) {
            flyouts.remove(i).close();
        }
        refreshAll();
    }

    // A flyout goes when its group can no longer own one: out of the tree,
    // hidden, filtered out, disabled, no longer a group, left with no child
    // item to show, or the sidebar no longer resting in MINI.
    private void closeStaleFlyouts() {
        for (int i = 0; i < flyouts.size(); i++) {
            RowView owner = flyouts.get(i).owner;
            if (views.get(owner.item) != owner || !compactRows || !isGroup(owner.item)
                    || !isShown(owner.item) || owner.row.isDisabled() || !opensFlyout(owner)) {
                for (int j = flyouts.size() - 1; j >= i; j--) {
                    flyouts.remove(j).close();
                }
                if (flyouts.isEmpty()) {
                    flyoutCloseTimer.stop();
                }
                return;
            }
        }
    }

    private void cancelFlyoutTimers() {
        flyoutOpenTimer.stop();
        flyoutCloseTimer.stop();
        hoveredGroup = null;
    }

    private void openHoveredGroup() {
        RowView group = hoveredGroup;
        hoveredGroup = null;
        if (group != null && views.get(group.item) == group && opensFlyout(group)
                && isShown(group.item) && !group.row.isDisabled() && group.row.isHover()) {
            openFlyout(group, false);
        }
    }

    // The first level opens and closes after a delay, which is what lets the
    // pointer travel from the group to its flyout. Deeper levels follow the
    // pointer at once and are never closed by it leaving.
    private void onRowEntered(RowView view) {
        if (!compactRows || flyoutTriggerOrDefault() != FlyoutTrigger.HOVER) {
            return;
        }
        Flyout host = hostOf(view);
        if (host != null) {
            if (opensFlyout(view) && !view.row.isDisabled()) {
                openFlyout(view, false);
            } else {
                closeFlyoutsFrom(flyouts.indexOf(host) + 1);
            }
            return;
        }
        if (view.depth != 0) {
            return;
        }
        if (view.flyout != null) {
            cancelFlyoutTimers();
        } else if (opensFlyout(view) && !view.row.isDisabled()) {
            hoveredGroup = view;
            flyoutOpenTimer.playFromStart();
        }
    }

    private void onRowExited(RowView view) {
        if (hoveredGroup == view) {
            flyoutOpenTimer.stop();
            hoveredGroup = null;
        }
        if (view.flyout != null && view.depth == 0) {
            onFlyoutExited();
        }
    }

    private void onFlyoutEntered() {
        flyoutCloseTimer.stop();
    }

    private void onFlyoutExited() {
        if (!flyouts.isEmpty() && flyoutTriggerOrDefault() == FlyoutTrigger.HOVER) {
            flyoutCloseTimer.playFromStart();
        }
    }

    // Navigation keys never leave the flyout they are pressed in.
    private void onFlyoutKeyPressed(Flyout flyout, KeyEvent event) {
        List<RowView> ring = flyout.ring();
        RowView focused = flyout.focusedView(ring);
        int size = ring.size();
        switch (event.getCode()) {
            case UP -> {
                if (size > 0) {
                    focusByKey(ring.get(focused == null ? size - 1 : Math.floorMod(ring.indexOf(focused) - 1, size)));
                }
                event.consume();
            }
            case DOWN -> {
                if (size > 0) {
                    focusByKey(ring.get(focused == null ? 0 : Math.floorMod(ring.indexOf(focused) + 1, size)));
                }
                event.consume();
            }
            case HOME -> {
                if (size > 0) {
                    focusByKey(ring.get(0));
                }
                event.consume();
            }
            case END -> {
                if (size > 0) {
                    focusByKey(ring.get(size - 1));
                }
                event.consume();
            }
            case LEFT, RIGHT -> {
                // A focusable node inside the row keeps these for itself.
                if (focused != null && flyout.focusOwner() != focused.row) {
                    return;
                }
                boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
                if ((event.getCode() == KeyCode.RIGHT) != rtl) {
                    if (focused != null && opensFlyout(focused)) {
                        openFlyout(focused, true);
                    }
                } else {
                    closeFlyoutsFrom(flyouts.indexOf(flyout));
                    flyout.owner.row.requestFocus();
                }
                event.consume();
            }
            case ENTER, SPACE -> {
                if (focused != null && flyout.focusOwner() == focused.row) {
                    activateByKey(focused);
                    event.consume();
                }
            }
            case ESCAPE -> {
                RowView top = flyouts.get(0).owner;
                closeFlyoutsFrom(0);
                top.row.requestFocus();
                event.consume();
            }
            case TAB -> {
                // The traversal continues from the group's row in the sidebar,
                // where the key is delivered again.
                RowView top = flyouts.get(0).owner;
                closeFlyoutsFrom(0);
                top.row.requestFocus();
                top.row.fireEvent(event.copyFor(top.row, top.row));
                event.consume();
            }
            default -> {
                // Everything else is left to the focused node.
            }
        }
    }

    /**
     * One level of the flyout chain: a popup beside the group's row that holds
     * the rows of the group's child items for as long as it is open. The rows
     * are the same nodes the sidebar shows them with, moved here and back.
     */
    private final class Flyout {

        private final RowView owner;
        private final VBox panel = new VBox();
        private final VBox box = new VBox();
        private final ScrollPane scroll = new ScrollPane(box);
        private final Label title = new Label();
        private final RXPopupSupport popup = new RXPopupSupport(panel);
        private final EventHandler<KeyEvent> keyFilter = event -> onFlyoutKeyPressed(this, event);
        private final EventHandler<KeyEvent> unclaimedKeyHandler = this::onUnclaimedKeyPressed;
        // The popup's scene while unclaimedKeyHandler is attached to it.
        private Scene scene;
        private final EventHandler<MouseEvent> enteredHandler = event -> onFlyoutEntered();
        private final EventHandler<MouseEvent> exitedHandler = event -> onFlyoutExited();
        private boolean closed;

        Flyout(RowView owner) {
            this.owner = owner;
            panel.getStyleClass().add("rx-sidebar-flyout");
            if (owner.depth == 0) {
                // The group's row shows no text in MINI, so the flyout names it.
                title.getStyleClass().add("title");
                title.textProperty().bind(owner.item.textProperty());
                title.setMaxWidth(Double.MAX_VALUE);
                panel.getChildren().add(title);
            }
            box.getStyleClass().add("section");
            scroll.getStyleClass().add("content");
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            scroll.setFocusTraversable(false);
            panel.getChildren().add(scroll);
        }

        void takeRows() {
            List<Node> nodes = new ArrayList<>(owner.pane.box.getChildren());
            owner.pane.box.getChildren().clear();
            box.getChildren().setAll(nodes);
            owner.flyout = this;
        }

        void show() {
            panel.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            panel.addEventFilter(KeyEvent.KEY_RELEASED, keyReleasedFilter);
            panel.addEventHandler(MouseEvent.MOUSE_ENTERED, enteredHandler);
            panel.addEventHandler(MouseEvent.MOUSE_EXITED, exitedHandler);
            boolean rtl = owner.row.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
            popup.setPlacement(rtl ? RXPlacement.LEFT_START : RXPlacement.RIGHT_START);
            popup.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
            popup.setOnHidden(this::onHidden);
            popup.show(owner.row);
            // Showing fails without a showing window, and closes the flyout.
            if (!closed) {
                scene = panel.getScene();
                scene.addEventHandler(KeyEvent.KEY_PRESSED, unclaimedKeyHandler);
            }
        }

        // A flyout opened with the pointer holds no focus, so the key filter of
        // its panel never runs and Escape arrives at the popup's scene. Left
        // unconsumed it would close the chain and then go on to the window
        // behind, where it may cancel a dialog.
        private void onUnclaimedKeyPressed(KeyEvent event) {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeFlyoutsFrom(0);
                event.consume();
            }
        }

        // The platform closed the popup: a press outside, Escape, or the popup
        // it hangs off going away.
        private void onHidden() {
            if (!closed) {
                closeFlyoutsFrom(flyouts.indexOf(this));
            }
        }

        // Rows first, popup second, so the rows are never left in a popup that
        // is gone.
        void close() {
            if (closed) {
                return;
            }
            closed = true;
            List<Node> nodes = new ArrayList<>(box.getChildren());
            box.getChildren().clear();
            if (owner.flyout == this) {
                owner.flyout = null;
                if (owner.pane != null) {
                    owner.pane.box.getChildren().setAll(nodes);
                }
            }
            title.textProperty().unbind();
            panel.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            panel.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedFilter);
            if (scene != null) {
                scene.removeEventHandler(KeyEvent.KEY_PRESSED, unclaimedKeyHandler);
                scene = null;
            }
            panel.removeEventHandler(MouseEvent.MOUSE_ENTERED, enteredHandler);
            panel.removeEventHandler(MouseEvent.MOUSE_EXITED, exitedHandler);
            popup.dispose();
        }

        // The rows of this level a key can land on.
        List<RowView> ring() {
            List<RowView> ring = new ArrayList<>();
            for (RXSidebarItemBase item : owner.item.getItems()) {
                if (views.get(item) instanceof RowView view && isShown(item) && !view.row.isDisabled()) {
                    ring.add(view);
                }
            }
            return ring;
        }

        Node focusOwner() {
            Scene scene = panel.getScene();
            return scene == null ? null : scene.getFocusOwner();
        }

        RowView focusedView(List<RowView> ring) {
            for (Node node = focusOwner(); node != null; node = node.getParent()) {
                if (node instanceof Row row && row.view != null && ring.contains(row.view)) {
                    return row.view;
                }
            }
            return null;
        }
    }

    // ==================== Mode transition (single Timeline) ====================

    private void onModeChanged() {
        SidebarMode target = committedMode();
        cancelFlyoutTimers();
        closeFlyoutsFrom(0);
        stopAnimation();
        if (!animationsActive()) {
            expansionFraction.set(target == SidebarMode.EXPANDED ? 1.0 : 0.0);
            settleMode();
            return;
        }
        double targetFraction = (target == SidebarMode.EXPANDED) ? 1.0 : 0.0;
        // A transition reversed midway only has the rest of the way to go, the
        // same as the groups that fold along with it.
        double distance = Math.abs(targetFraction - expansionFraction.get());
        if (distance == 0.0) {
            settleMode();
            return;
        }
        compactRows = false;
        refreshAnimated();
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration().multiply(distance),
                new KeyValue(expansionFraction, targetFraction, interpolatorOrDefault())));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null; // a superseded Timeline must not clear a newer one
            }
            expansionFraction.set(targetFraction);
            settleMode();
        });
        animation = timeline;
        timeline.play();
    }

    private void settleMode() {
        compactRows = committedMode() == SidebarMode.MINI;
        refreshAll();
        requestRowLayout();
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    private boolean animationsActive() {
        return getSkinnable().isAnimated()
                && getSkinnable().getScene() != null
                && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = getSkinnable().getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXSidebar.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private SidebarMode committedMode() {
        SidebarMode mode = getSkinnable().getMode();
        return mode == null ? RXSidebar.DEFAULT_MODE : mode;
    }

    // ==================== Keyboard roving ====================

    private void onKeyPressed(KeyEvent event) {
        List<RowView> ring = focusRing();
        RowView focused = focusedView(ring);
        if (focused == null) {
            return; // focus is not on a row (e.g. header/footer content) — leave it alone
        }
        int current = ring.indexOf(focused);
        int size = ring.size();
        switch (event.getCode()) {
            case UP, DOWN, HOME, END -> closeFlyoutsFrom(0);
            default -> {
            }
        }
        switch (event.getCode()) {
            case UP -> {
                focusByKey(ring.get(Math.floorMod(current - 1, size)));
                event.consume();
            }
            case DOWN -> {
                focusByKey(ring.get(Math.floorMod(current + 1, size)));
                event.consume();
            }
            case HOME -> {
                focusByKey(ring.get(0));
                event.consume();
            }
            case END -> {
                focusByKey(ring.get(size - 1));
                event.consume();
            }
            case LEFT, RIGHT -> {
                // A focusable node inside the row keeps these for itself.
                if (focusOwner() != focused.row) {
                    return;
                }
                boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
                if ((event.getCode() == KeyCode.RIGHT) != rtl) {
                    stepInto(focused, ring);
                } else {
                    stepOut(focused);
                }
                event.consume();
            }
            case ENTER, SPACE -> {
                // Only when the row itself holds focus: a focusable node inside
                // the row keeps its own activation keys.
                if (focusOwner() == focused.row) {
                    activateByKey(focused);
                    event.consume();
                }
            }
            default -> {
                // Tab and everything else go to the scene.
            }
        }
    }

    // Towards the leaves: opens a closed group, moves into an open one.
    private void stepInto(RowView view, List<RowView> ring) {
        if (!isGroup(view.item)) {
            return;
        }
        if (opensFlyout(view)) {
            openFlyout(view, true);
            return;
        }
        if (!isOpen(view.item)) {
            setExpandedByUser(view, true);
            return;
        }
        int next = ring.indexOf(view) + 1;
        if (next < ring.size() && ring.get(next).item.getParent() == view.item) {
            focusByKey(ring.get(next));
        }
    }

    // Towards the root: closes an open group, otherwise moves to the parent.
    private void stepOut(RowView view) {
        if (isGroup(view.item) && isOpen(view.item)) {
            setExpandedByUser(view, false);
            return;
        }
        if (views.get(view.item.getParent()) instanceof RowView parent) {
            focusByKey(parent);
        }
    }

    // The arrow-roving ring: every row a key can land on, in display order
    // across the three lists. A row is in it when it is shown, enabled, and
    // every group above it is open.
    private List<RowView> focusRing() {
        List<RowView> ring = new ArrayList<>();
        RXSidebar control = getSkinnable();
        addFocusable(ring, control.getTopItems(), 0);
        addFocusable(ring, control.getItems(), 0);
        addFocusable(ring, control.getBottomItems(), 0);
        return ring;
    }

    private void addFocusable(List<RowView> ring, List<RXSidebarItemBase> items, int depth) {
        for (RXSidebarItemBase item : items) {
            if (!(views.get(item) instanceof RowView view) || !isShown(item) || view.row.isDisabled()) {
                continue;
            }
            ring.add(view);
            if (isGroup(view.item) && isOpen(view.item)) {
                addFocusable(ring, view.item.getItems(), depth + 1);
            }
        }
    }

    // Uses the scene focus owner (not Node.isFocused, which is false when the window
    // is unfocused — and always false headless) so roving is window-focus independent.
    private Node focusOwner() {
        Scene scene = getSkinnable().getScene();
        return scene == null ? null : scene.getFocusOwner();
    }

    // Focus may sit on something a row contains rather than on the row itself —
    // an item may carry a focusable graphic — and the row still counts as focused
    // there: arrows keep roving from the owning row.
    private RowView focusedView(List<RowView> ring) {
        Node node = focusOwner();
        while (node != null) {
            if (node instanceof Row row && row.view != null && ring.contains(row.view)) {
                return row.view;
            }
            node = node.getParent();
        }
        return null;
    }

    // Single Tab stop: exactly one row is Tab-reachable. Idempotent, so every
    // transition (roving, click, selection, structure, visibility) just calls
    // this and the rule below decides.
    //
    // The rule: the tab stop follows focus. While a row holds focus it MUST be
    // the Tab-reachable one; anything else leaves it focused-but-unreachable,
    // and Shift+Tab out of it would find the other traversable row and re-enter
    // the sidebar instead of leaving it. Only when focus is elsewhere does the
    // tab stop become the way in: the selected row, or the first one.
    private void refreshTabStop() {
        List<RowView> ring = focusRing();
        RowView stop = focusedView(ring);
        if (stop == null && !ring.isEmpty()) {
            RXSidebarItem selected = getSkinnable().getSelectedItem();
            stop = (views.get(selected) instanceof RowView view && ring.contains(view)) ? view : ring.get(0);
        }
        for (ItemView<?> view : views.values()) {
            if (view instanceof RowView rowView) {
                rowView.row.setFocusTraversable(rowView == stop);
            }
        }
    }

    // ==================== Width resolution + compute ====================

    // Any unusable width (NaN, +/-infinity, negative) resolves to the default
    // rather than to zero: infinity must be rejected here because railWidth()
    // interpolates mini -> expanded, and infinity - infinity is NaN, which would
    // poison the whole layout with a NaN width.
    private double resolvedMiniWidth() {
        double v = getSkinnable().getMiniWidth();
        return (Double.isFinite(v) && v >= 0.0) ? v : RXSidebar.DEFAULT_MINI_WIDTH;
    }

    private double resolvedExpandedWidth() {
        double mini = resolvedMiniWidth();
        double v = getSkinnable().getExpandedWidth();
        if (!Double.isFinite(v) || v < mini) {
            return Math.max(mini, RXSidebar.DEFAULT_EXPANDED_WIDTH);
        }
        return v;
    }

    private double railWidth() {
        double mini = resolvedMiniWidth();
        double expanded = resolvedExpandedWidth();
        double frac = clamp01(expansionFraction.get());
        return mini + frac * (expanded - mini);
    }

    private static double clamp01(double x) {
        if (!(x > 0.0)) { // NaN or <= 0 -> 0
            return 0.0;
        }
        return (x > 1.0) ? 1.0 : x;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + railWidth() + rightInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Lock min = pref (computed from the same source, not the prefWidth cache).
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Lock max = pref => a fixed-width column the parent layout never stretches.
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Do not force the parent to be tall; the ScrollPane absorbs overflow.
        return topInset + bottomInset;
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
        if (wipeExtra() > 0.0) {
            wipeClip.setWidth(contentWidth);
            wipeClip.setHeight(contentHeight);
            root.setClip(wipeClip);
        } else {
            root.setClip(null);
        }
    }

    @Override
    protected void disposeSkin() {
        closeFlyoutsFrom(0);
        // The rebuilt-per-transition Timeline would be a stale reference in the
        // disposer; stop it explicitly by reading the live field.
        stopAnimation();
        stopRevealing();
        // Paired with trackFocusOwner: the scene is a moving target, so the disposer
        // could not hold it.
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().removeListener(focusOwnerListener);
            trackedScene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedFilter);
            trackedScene = null;
        }
        for (ItemView<?> view : views.values()) {
            view.dispose();
        }
        views.clear();
        // A replacing skin has already moved these into its own slots; otherwise
        // let go of the caller's nodes.
        headerSlot.getChildren().clear();
        footerSlot.getChildren().clear();
        placeholderSlot.getChildren().clear();
        root.setClip(null);
        // Pair the constructor's getChildren().setAll(root): remove our root so a
        // setSkin(null) (no replacing skin to clear it) leaves no stale node
        // behind. Listeners/event filters are released by the disposer.
        getChildren().remove(root);
    }

    // ==================== Item views ====================

    /**
     * What this skin holds for one item: its nodes and the listeners that keep
     * them current. The listeners are per item, so they live in the view's own
     * disposer and go when the item leaves the tree.
     */
    private abstract class ItemView<T extends RXSidebarItemBase> {

        final T item;
        final SkinDisposer itemDisposer = new SkinDisposer();
        int depth;

        ItemView(T item) {
            this.item = item;
            itemDisposer.registerListener(item.visibleProperty(), (obs, old, value) -> refreshShown());
        }

        // A change listener, not an invalidation listener: nothing guarantees
        // the property is read back between two changes.
        final void watch(ObservableValue<?> property) {
            itemDisposer.registerListener(property, (obs, old, value) -> refreshAll());
        }

        final void bindIdentity(Node node, String styleClass) {
            node.getStyleClass().setAll(styleClass);
            node.getStyleClass().addAll(item.getStyleClass());
            itemDisposer.registerListener(item.getStyleClass(), () -> {
                node.getStyleClass().setAll(styleClass);
                node.getStyleClass().addAll(item.getStyleClass());
            });
            itemDisposer.registerBinding(node.idProperty(), item.idProperty());
        }

        void syncChildren() {
        }

        abstract void collectNodes(List<Node> nodes);

        abstract void refresh(Set<RXSidebarItem> selectionPath);

        abstract void requestLayout();

        void dispose() {
            itemDisposer.dispose();
        }
    }

    private final class SeparatorView extends ItemView<RXSidebarSeparator> {

        private final Region line = new Region();

        SeparatorView(RXSidebarSeparator item) {
            super(item);
            bindIdentity(line, "separator");
            VBox.setMargin(line, ROW_MARGIN);
        }

        @Override
        void collectNodes(List<Node> nodes) {
            nodes.add(line);
        }

        @Override
        void refresh(Set<RXSidebarItem> selectionPath) {
            setShown(line, isShown(item));
        }

        @Override
        void requestLayout() {
        }
    }

    private final class CaptionView extends ItemView<RXSidebarHeader> {

        private final Row caption = new Row(null);
        // Stands in for the caption in MINI, where there is no room for its
        // text.
        private final Region line = new Region();

        CaptionView(RXSidebarHeader item) {
            super(item);
            bindIdentity(caption, "caption");
            caption.setMouseTransparent(true);
            itemDisposer.registerBinding(caption.label.textProperty(), item.textProperty());
            line.getStyleClass().add("separator");
            VBox.setMargin(caption, ROW_MARGIN);
            VBox.setMargin(line, ROW_MARGIN);
        }

        @Override
        void collectNodes(List<Node> nodes) {
            nodes.add(caption);
            nodes.add(line);
        }

        @Override
        void refresh(Set<RXSidebarItem> selectionPath) {
            boolean shown = isShown(item);
            // Follows the mode the sidebar is heading for, not the settled one:
            // swapping at the end of the collapse would shift every row below
            // by the height difference after the motion has stopped.
            boolean asLine = committedMode() == SidebarMode.MINI && depth == 0;
            setShown(caption, shown && !asLine);
            setShown(line, shown && asLine);
            caption.depth = depth;
            caption.inFlyout = isInFlyout(item);
        }

        @Override
        void requestLayout() {
            caption.requestLayout();
        }
    }

    private final class RowView extends ItemView<RXSidebarItem> {

        private final Row row = new Row(this);
        // Created the first time the item has child items.
        private ChildrenPane pane;
        // Set while the rows of the child items are on display in a flyout
        // instead of in the pane.
        private Flyout flyout;
        // Shows the text of a top-level row while the sidebar rests in MINI and
        // the item has no tooltip of its own. Created on first use.
        private Tooltip lentTooltip;
        private Tooltip installedTooltip;

        RowView(RXSidebarItem item) {
            super(item);
            bindIdentity(row, "item");
            VBox.setMargin(row, ROW_MARGIN);

            itemDisposer.registerBinding(row.label.textProperty(), item.textProperty());
            itemDisposer.registerBinding(row.disableProperty(), item.disableProperty());
            row.setGraphic(item.getGraphic());
            row.setTrailing(item.getTrailing());
            itemDisposer.registerListener(item.graphicProperty(), (obs, old, node) -> row.setGraphic(node));
            itemDisposer.registerListener(item.trailingProperty(), (obs, old, node) -> row.setTrailing(node));
            itemDisposer.registerListener(item.getItems(), RXSidebarSkin.this::syncTree);

            watch(item.selectableProperty());
            itemDisposer.registerListener(item.expandedProperty(), (obs, old, value) -> refreshAnimated());
            watch(item.tooltipProperty());
            watch(item.selectedProperty());
            watch(item.disableProperty());
            itemDisposer.registerListener(item.textProperty(), (obs, old, value) ->
                    row.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT));
            itemDisposer.registerListener(item.accessibleTextProperty(), (obs, old, value) ->
                    row.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT));

            // The window going inactive takes focus from the row without a
            // change of focus owner, and the release of a held key is then
            // delivered elsewhere.
            itemDisposer.registerListener(row.focusedProperty(), (obs, old, focused) -> {
                if (!focused && row.getScene() == trackedScene) {
                    activationKeyDown = false;
                }
            });
            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_ENTERED, event -> onRowEntered(this));
            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_EXITED, event -> onRowExited(this));
        }

        private void onMousePressed(MouseEvent event) {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            // Focus first, so the pointer and the roving point never disagree;
            // the focus-owner listener moves the tab stop.
            row.requestFocus();
            row.pseudoClassStateChanged(ARMED_PSEUDO_CLASS, true);
            // As a control would: an enclosing ScrollPane takes focus on any
            // press that reaches it.
            event.consume();
        }

        private void onMouseReleased(MouseEvent event) {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            boolean armed = row.getPseudoClassStates().contains(ARMED_PSEUDO_CLASS);
            row.pseudoClassStateChanged(ARMED_PSEUDO_CLASS, false);
            if (armed && row.contains(event.getX(), event.getY())) {
                activate(this, false);
            }
            event.consume();
        }

        @Override
        void syncChildren() {
            if (!isGroup(item)) {
                if (pane != null) {
                    childNodes().clear();
                }
                return;
            }
            if (pane == null) {
                pane = new ChildrenPane(row);
                itemDisposer.registerBinding(pane.disableProperty(), item.disableProperty());
            }
            syncList(item.getItems(), childNodes(), depth + 1);
        }

        // Where the rows of the child items currently are.
        private ObservableList<Node> childNodes() {
            return flyout != null ? flyout.box.getChildren() : pane.box.getChildren();
        }

        @Override
        void collectNodes(List<Node> nodes) {
            nodes.add(row);
            if (isGroup(item)) {
                nodes.add(pane);
            }
        }

        @Override
        void refresh(Set<RXSidebarItem> selectionPath) {
            boolean shown = isShown(item);
            boolean group = isGroup(item);
            boolean open = group && shown && isOpen(item);
            setShown(row, shown);
            if (pane != null) {
                // Closing over the focused row would leave focus on a row that
                // is no longer on display.
                if (!open && pane.open && shown && isInside(focusOwner(), pane)) {
                    focusByKey(this);
                }
                pane.setOpen(open, animateGroups);
            }

            row.setAccessibleRole(!group && item.isSelectable()
                    ? AccessibleRole.RADIO_BUTTON : AccessibleRole.BUTTON);
            row.depth = depth;
            row.inFlyout = isInFlyout(item);
            row.setGroup(group);
            row.setCompact(compactRows && depth == 0);
            row.pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, flyout != null);
            row.pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, group && open);
            row.pseudoClassStateChanged(COLLAPSED_PSEUDO_CLASS, group && !open);
            row.pseudoClassStateChanged(SELECTABLE_PSEUDO_CLASS, !group && item.isSelectable());
            row.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, item.isSelected());
            row.pseudoClassStateChanged(CHILD_SELECTED_PSEUDO_CLASS, selectionPath.contains(item));
            row.requestLayout();
            row.notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
            row.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
            refreshTooltip(group);
        }

        // Tooltip.install rather than a Control tooltip property: a row is a
        // plain Region. Assistive technology reads the tooltip through the row's
        // HELP attribute instead.
        private void refreshTooltip(boolean group) {
            Tooltip wanted = item.getTooltip();
            if (wanted == null && row.compact && !group) {
                if (lentTooltip == null) {
                    lentTooltip = new Tooltip();
                    itemDisposer.registerBinding(lentTooltip.textProperty(), item.textProperty());
                }
                wanted = lentTooltip;
            }
            if (wanted == installedTooltip) {
                return;
            }
            if (installedTooltip != null) {
                Tooltip.uninstall(row, installedTooltip);
            }
            if (wanted != null) {
                Tooltip.install(row, wanted);
            }
            installedTooltip = wanted;
        }

        @Override
        void requestLayout() {
            row.requestLayout();
        }

        @Override
        void dispose() {
            super.dispose();
            if (installedTooltip != null) {
                Tooltip.uninstall(row, installedTooltip);
                installedTooltip = null;
            }
            row.setGraphic(null);
            row.setTrailing(null);
            if (pane != null) {
                pane.stop();
            }
        }
    }

    private static boolean isInside(Node node, Node container) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == container) {
                return true;
            }
        }
        return false;
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // ==================== Row ====================

    /**
     * One line of the sidebar: graphic, text, trailing content and, for a
     * group, the disclosure arrow at the far end. Also used, without a view, as
     * the body of a section caption, which shares the indent rule.
     *
     * <p>The left edge of the content is the icon-column inset plus one indent
     * per nesting level; it is applied in layout rather than as padding, so the
     * row's CSS padding stays the author's.</p>
     */
    private final class Row extends Region {

        private final RowView view;
        private final Label label = new Label();
        private final StackPane trailingSlot = new StackPane();
        private final StackPane disclosure = new StackPane();
        private Node graphic;
        private int depth;
        private boolean compact;
        // A row in a flyout starts at the edge: no icon column inset and no
        // indent by depth.
        private boolean inFlyout;

        Row(RowView view) {
            this.view = view;
            label.setMinWidth(0.0);
            trailingSlot.getStyleClass().add("trailing");
            disclosure.getStyleClass().add("disclosure");
            Region arrow = new Region();
            arrow.getStyleClass().add("arrow");
            arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            disclosure.getChildren().add(arrow);
            disclosure.setMouseTransparent(true);
            setShown(trailingSlot, false);
            setShown(disclosure, false);
            getChildren().addAll(label, trailingSlot, disclosure);
        }

        void setGraphic(Node node) {
            if (graphic != null) {
                getChildren().remove(graphic);
            }
            graphic = node;
            if (node != null) {
                getChildren().add(0, node);
            }
        }

        void setTrailing(Node node) {
            if (node == null) {
                trailingSlot.getChildren().clear();
            } else {
                trailingSlot.getChildren().setAll(node);
            }
            trailingSlot.setManaged(node != null);
            trailingSlot.setVisible(node != null && !compact);
        }

        void setGroup(boolean group) {
            disclosure.setManaged(group);
        }

        // Graphic only. The hidden parts stay managed: that flag records
        // whether the row has them at all.
        void setCompact(boolean value) {
            compact = value;
            label.setVisible(!value);
            trailingSlot.setVisible(!value && trailingSlot.isManaged());
            disclosure.setVisible(!value && disclosure.isManaged());
        }

        private double contentLeft() {
            if (inFlyout) {
                return snappedLeftInset();
            }
            return snappedLeftInset() + snapPositionX(iconInset() + depth * indentOrDefault());
        }

        private double graphicColumnWidth() {
            if (graphic == null || !graphic.isManaged()) {
                return 0.0;
            }
            return Math.max(ICON_COLUMN_WIDTH, snapSizeX(graphic.prefWidth(-1)));
        }

        @Override
        protected double computeMinWidth(double height) {
            return snappedLeftInset() + snappedRightInset();
        }

        @Override
        protected double computePrefWidth(double height) {
            double column = graphicColumnWidth();
            double width = contentLeft() + column + (column > 0.0 ? GRAPHIC_TEXT_GAP : 0.0)
                    + snapSizeX(label.prefWidth(-1));
            if (trailingSlot.isManaged()) {
                width += TRAILING_GAP + snapSizeX(trailingSlot.prefWidth(-1));
            }
            if (disclosure.isManaged()) {
                width += TRAILING_GAP + snapSizeX(disclosure.prefWidth(-1));
            }
            return width + snappedRightInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            double height = label.prefHeight(-1);
            if (graphic != null && graphic.isManaged()) {
                height = Math.max(height, graphic.prefHeight(-1));
            }
            if (trailingSlot.isManaged()) {
                height = Math.max(height, trailingSlot.prefHeight(-1));
            }
            return snappedTopInset() + snapSizeY(height) + snappedBottomInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double top = snappedTopInset();
            double height = Math.max(0.0, getHeight() - top - snappedBottomInset());
            double left = contentLeft();
            double column = graphicColumnWidth();
            if (column > 0.0) {
                layoutInArea(graphic, left, top, column, height, 0.0, HPos.CENTER, VPos.CENTER);
                left += column + GRAPHIC_TEXT_GAP;
            }
            if (compact) {
                return;
            }
            double right = getWidth() + (inFlyout ? 0.0 : wipeExtra()) - snappedRightInset();
            if (disclosure.isManaged()) {
                double width = snapSizeX(disclosure.prefWidth(-1));
                right -= width;
                layoutInArea(disclosure, right, top, width, height, 0.0, HPos.CENTER, VPos.CENTER);
                right -= TRAILING_GAP;
            }
            if (trailingSlot.isManaged()) {
                double width = snapSizeX(trailingSlot.prefWidth(-1));
                right -= width;
                layoutInArea(trailingSlot, right, top, width, height, 0.0, HPos.CENTER, VPos.CENTER);
                right -= TRAILING_GAP;
            }
            layoutInArea(label, left, top, Math.max(0.0, right - left), height, 0.0, HPos.LEFT, VPos.CENTER);
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            if (view == null) {
                return super.queryAccessibleAttribute(attribute, parameters);
            }
            RXSidebarItem item = view.item;
            switch (attribute) {
                case TEXT -> {
                    String text = item.getAccessibleText();
                    return text != null ? text : item.getText();
                }
                case SELECTED -> {
                    return item.isSelected();
                }
                case EXPANDED -> {
                    return isGroup(item) && (isOpen(item) || view.flyout != null);
                }
                case HELP -> {
                    Tooltip tooltip = view.installedTooltip;
                    return tooltip == null ? null : tooltip.getText();
                }
                default -> {
                    return super.queryAccessibleAttribute(attribute, parameters);
                }
            }
        }

        @Override
        public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
            if (view != null && action == AccessibleAction.FIRE) {
                activate(view, true);
            } else {
                super.executeAccessibleAction(action, parameters);
            }
        }
    }

    // ==================== Children pane ====================

    /**
     * The viewport over a group's child rows. The rows keep their natural
     * height; the viewport's own height is that height scaled by how far the
     * group is open, and it clips what does not fit.
     */
    private final class ChildrenPane extends Region {

        private final VBox box = new VBox();
        private final Rectangle clip = new Rectangle();
        // 0 = closed, 1 = open.
        private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress") {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };
        private boolean open;
        // Rebuilt per transition and stopped through the live field.
        private Timeline timeline;

        private final Node groupRow;

        ChildrenPane(Node groupRow) {
            this.groupRow = groupRow;
            getStyleClass().add("children");
            getChildren().add(box);
            setClip(clip);
            setShown(this, false);
        }

        // Idempotent: asking for the state already headed for leaves a running
        // transition alone.
        void setOpen(boolean value, boolean animate) {
            if (value == open) {
                return;
            }
            open = value;
            stop();
            double target = value ? 1.0 : 0.0;
            double distance = Math.abs(target - progress.get());
            setShown(this, true);
            if (!animate || !animationsActive() || distance == 0.0) {
                progress.set(target);
                settle();
                return;
            }
            Timeline next = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration().multiply(distance),
                    new KeyValue(progress, target, interpolatorOrDefault())));
            next.setOnFinished(event -> {
                if (timeline == next) {
                    timeline = null;
                    runningGroupAnimations--;
                    settle();
                }
            });
            timeline = next;
            runningGroupAnimations++;
            next.play();
        }

        private void settle() {
            // Focus can enter the rows while they are still folding away.
            if (!open && isInside(focusOwner(), this)) {
                groupRow.requestFocus();
                if (isOnDisplayIn(groupRow, mainBox)) {
                    scrollIntoView(groupRow);
                }
            }
            setShown(this, open);
        }

        void stop() {
            if (timeline != null) {
                Timeline running = timeline;
                timeline = null;
                runningGroupAnimations--;
                running.stop();
            }
        }

        private double naturalHeight(double width) {
            double contentWidth = width < 0.0 ? -1.0 : Math.max(0.0, width - snappedLeftInset() - snappedRightInset());
            return snappedTopInset() + box.prefHeight(contentWidth) + snappedBottomInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            return snapSizeY(naturalHeight(width) * progress.get());
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double left = snappedLeftInset();
            double width = Math.max(0.0, getWidth() - left - snappedRightInset());
            box.resizeRelocate(left, snappedTopInset(), width, box.prefHeight(width));
            // The width transition lets rows overflow to the right, under the
            // container's own clip; only the height is this viewport's to cut.
            clip.setWidth(getWidth() + wipeExtra());
            clip.setHeight(getHeight());
        }
    }
}
