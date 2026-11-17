package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
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
    // starts at the same x, and it is what miniWidth centres on. The stylesheet
    // sizes an opted-in ".graphic" to match; the two must be changed together.
    private static final double ICON_SIZE = 24.0;
    private static final double GRAPHIC_TEXT_GAP = 12.0;
    private static final double TRAILING_GAP = 8.0;
    private static final Insets ROW_MARGIN = new Insets(ITEM_GAP / 2.0, 0.0, ITEM_GAP / 2.0, 0.0);

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass CHILD_SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("child-selected");
    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass COLLAPSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("collapsed");
    private static final PseudoClass SELECTABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("selectable");
    private static final PseudoClass ARMED_PSEUDO_CLASS = PseudoClass.getPseudoClass("armed");

    // ==================== Container tree ====================

    private final VBox root = new VBox();
    private final StackPane headerSlot = new StackPane();
    private final VBox topBox = new VBox();
    private final ScrollPane mainScroll = new ScrollPane();
    private final VBox mainBox = new VBox();
    private final VBox bottomBox = new VBox();
    private final StackPane footerSlot = new StackPane();
    // Set on the container only while the width is in transition; see wipeExtra().
    private final Rectangle wipeClip = new Rectangle();

    private final Map<RXSidebarItem, ItemView> views = new IdentityHashMap<>();

    // True while the sidebar rests in MINI: top-level rows show their graphic
    // only. False from the moment a transition starts, so text is wiped and
    // revealed by the moving edge rather than popping.
    private boolean compactRows;

    // The Tab-stop rule keys off the scene's focus owner, so it has to re-run on every
    // focus move — including a plain requestFocus() from the application, which no
    // roving or click path observes. Node.focused cannot serve here: it is false
    // whenever the window is not focused.
    private final ChangeListener<Node> focusOwnerListener = (obs, old, node) -> refreshTabStop();

    // The scene focusOwnerListener is attached to. Re-attached whenever the control
    // changes scene, so it cannot go through the disposer (which captures its target
    // at registration); released in disposeSkin instead.
    private Scene trackedScene;

    // Rebuilt per transition; NOT registered with the disposer (which would hold a
    // stale reference). Stopped explicitly in disposeSkin().
    private Timeline animation;

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
        topBox.getStyleClass().add("section");
        mainScroll.getStyleClass().add("content");
        mainBox.getStyleClass().add("section");
        bottomBox.getStyleClass().add("section");
        footerSlot.getStyleClass().add("footer");

        mainScroll.setContent(mainBox);
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        mainScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        mainScroll.setFocusTraversable(false);
        VBox.setVgrow(mainScroll, Priority.ALWAYS);

        root.getChildren().addAll(headerSlot, topBox, mainScroll, bottomBox, footerSlot);
        // setAll (not add): a replacing skin's constructor takes ownership of the
        // children, clearing any nodes left by a prior skin.
        getChildren().setAll(root);

        updateHeader();
        updateFooter();
        disposer.registerListener(control.headerProperty(), this::updateHeader);
        disposer.registerListener(control.footerProperty(), this::updateFooter);

        disposer.registerListener(control.getTopItems(), this::syncTree);
        disposer.registerListener(control.getItems(), this::syncTree);
        disposer.registerListener(control.getBottomItems(), this::syncTree);

        disposer.registerListener(control.modeProperty(), this::onModeChanged);
        disposer.registerListener(control.miniWidthProperty(), this::requestRowLayout);
        disposer.registerListener(control.expandedWidthProperty(), this::requestRowLayout);
        disposer.registerListener(control.indentProperty(), this::requestRowLayout);
        disposer.registerListener(control.selectedItemProperty(), (obs, old, item) -> refreshAll());
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
        }
        trackedScene = getSkinnable().getScene();
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().addListener(focusOwnerListener);
        }
        refreshTabStop();
    }

    // ==================== Header / Footer slots ====================

    private void updateHeader() {
        swapSlot(headerSlot, getSkinnable().getHeader());
    }

    private void updateFooter() {
        swapSlot(footerSlot, getSkinnable().getFooter());
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
        syncList(control.getTopItems(), topBox.getChildren(), 0);
        syncList(control.getItems(), mainBox.getChildren(), 0);
        syncList(control.getBottomItems(), bottomBox.getChildren(), 0);

        Iterator<ItemView> iterator = views.values().iterator();
        while (iterator.hasNext()) {
            ItemView view = iterator.next();
            if (view.item.getSidebar() != control) {
                view.dispose();
                iterator.remove();
            }
        }
        refreshAll();
    }

    private void syncList(List<RXSidebarItem> items, ObservableList<Node> target, int depth) {
        List<Node> nodes = new ArrayList<>();
        for (RXSidebarItem item : items) {
            ItemView view = views.get(item);
            if (view == null) {
                view = createView(item);
                views.put(item, view);
            }
            view.depth = depth;
            view.syncChildren();
            view.collectNodes(nodes);
        }
        reconcile(target, nodes);
    }

    private ItemView createView(RXSidebarItem item) {
        if (item instanceof RXSidebarSeparator) {
            return new SeparatorView(item);
        }
        if (item instanceof RXSidebarHeader) {
            return new CaptionView(item);
        }
        return new RowView(item);
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
        for (ItemView view : views.values()) {
            view.refresh(selectionPath);
        }
        refreshTabStop();
    }

    private void requestRowLayout() {
        for (ItemView view : views.values()) {
            view.requestLayout();
        }
    }

    private boolean isShown(RXSidebarItem item) {
        return item.isVisible();
    }

    private static boolean isGroup(RXSidebarItem item) {
        return !item.getItems().isEmpty();
    }

    // Whether a group's child items are on display. A top-level group folds
    // away in MINI without its expanded state being touched, so it unfolds
    // again by itself on the way back.
    private boolean isOpen(RXSidebarItem item, int depth) {
        if (depth == 0 && committedMode() == SidebarMode.MINI) {
            return false;
        }
        return item.isExpanded();
    }

    private double iconInset() {
        return Math.max(0.0, (resolvedMiniWidth() - ICON_SIZE) / 2.0);
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

    private void activate(RowView view) {
        RXSidebarItem item = view.item;
        if (view.row.isDisabled()) {
            return;
        }
        if (isGroup(item)) {
            if (committedMode() == SidebarMode.EXPANDED && !item.expandedProperty().isBound()) {
                item.setExpanded(!item.isExpanded());
            }
            return;
        }
        if (item.isSelectable()) {
            getSkinnable().selectItem(item);
        }
        EventHandler<ActionEvent> handler = item.getOnAction();
        if (handler != null) {
            handler.handle(new ActionEvent(item, null));
        }
    }

    // ==================== Mode transition (single Timeline) ====================

    private void onModeChanged() {
        SidebarMode target = committedMode();
        stopAnimation();
        if (!animationsActive()) {
            expansionFraction.set(target == SidebarMode.EXPANDED ? 1.0 : 0.0);
            settleMode();
            return;
        }
        compactRows = false;
        refreshAll();
        double targetFraction = (target == SidebarMode.EXPANDED) ? 1.0 : 0.0;
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
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
            case UP -> {
                ring.get(Math.floorMod(current - 1, size)).row.requestFocus();
                event.consume();
            }
            case DOWN -> {
                ring.get(Math.floorMod(current + 1, size)).row.requestFocus();
                event.consume();
            }
            case HOME -> {
                ring.get(0).row.requestFocus();
                event.consume();
            }
            case END -> {
                ring.get(size - 1).row.requestFocus();
                event.consume();
            }
            case ENTER, SPACE -> {
                // Only when the row itself holds focus: a focusable node inside
                // the row keeps its own activation keys.
                if (focusOwner() == focused.row) {
                    activate(focused);
                    event.consume();
                }
            }
            default -> {
                // Tab and everything else go to the scene.
            }
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

    private void addFocusable(List<RowView> ring, List<RXSidebarItem> items, int depth) {
        for (RXSidebarItem item : items) {
            if (!(views.get(item) instanceof RowView view) || !isShown(item) || view.row.isDisabled()) {
                continue;
            }
            ring.add(view);
            if (isGroup(item) && isOpen(item, depth)) {
                addFocusable(ring, item.getItems(), depth + 1);
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
        for (ItemView view : views.values()) {
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
        // The rebuilt-per-transition Timeline would be a stale reference in the
        // disposer; stop it explicitly by reading the live field.
        stopAnimation();
        // Paired with trackFocusOwner: the scene is a moving target, so the disposer
        // could not hold it.
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().removeListener(focusOwnerListener);
            trackedScene = null;
        }
        for (ItemView view : views.values()) {
            view.dispose();
        }
        views.clear();
        // A replacing skin has already moved these into its own slots; otherwise
        // let go of the caller's nodes.
        headerSlot.getChildren().clear();
        footerSlot.getChildren().clear();
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
    private abstract class ItemView {

        final RXSidebarItem item;
        final SkinDisposer itemDisposer = new SkinDisposer();
        int depth;

        ItemView(RXSidebarItem item) {
            this.item = item;
            watch(item.visibleProperty());
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

    private final class SeparatorView extends ItemView {

        private final Region line = new Region();

        SeparatorView(RXSidebarItem item) {
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

    private final class CaptionView extends ItemView {

        private final Row caption = new Row(null);
        // Stands in for the caption while the sidebar rests in MINI, where there
        // is no room for its text.
        private final Region line = new Region();

        CaptionView(RXSidebarItem item) {
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
            boolean asLine = compactRows && depth == 0;
            setShown(caption, shown && !asLine);
            setShown(line, shown && asLine);
            caption.depth = depth;
        }

        @Override
        void requestLayout() {
            caption.requestLayout();
        }
    }

    private final class RowView extends ItemView {

        private final Row row = new Row(this);
        // Created the first time the item has child items.
        private ChildrenPane pane;
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
            watch(item.expandedProperty());
            watch(item.tooltipProperty());
            watch(item.selectedProperty());
            watch(item.disableProperty());
            itemDisposer.registerListener(item.textProperty(), (obs, old, value) ->
                    row.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT));
            itemDisposer.registerListener(item.accessibleTextProperty(), (obs, old, value) ->
                    row.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT));

            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
            itemDisposer.registerEventHandler(row, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
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
                activate(this);
            }
            event.consume();
        }

        @Override
        void syncChildren() {
            if (!isGroup(item)) {
                if (pane != null) {
                    pane.box.getChildren().clear();
                }
                return;
            }
            if (pane == null) {
                pane = new ChildrenPane();
                itemDisposer.registerBinding(pane.disableProperty(), item.disableProperty());
            }
            syncList(item.getItems(), pane.box.getChildren(), depth + 1);
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
            boolean open = group && shown && isOpen(item, depth);
            setShown(row, shown);
            if (pane != null) {
                pane.setOpen(open);
            }

            row.setAccessibleRole(!group && item.isSelectable()
                    ? AccessibleRole.RADIO_BUTTON : AccessibleRole.BUTTON);
            row.depth = depth;
            row.setGroup(group);
            row.setCompact(compactRows && depth == 0);
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
        }
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
            return snappedLeftInset() + snapPositionX(iconInset() + depth * indentOrDefault());
        }

        private double graphicColumnWidth() {
            if (graphic == null || !graphic.isManaged()) {
                return 0.0;
            }
            return Math.max(ICON_SIZE, snapSizeX(graphic.prefWidth(-1)));
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
            double right = getWidth() + wipeExtra() - snappedRightInset();
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
                    return isGroup(item) && isOpen(item, depth);
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
                activate(view);
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
        private double progress;

        ChildrenPane() {
            getStyleClass().add("children");
            getChildren().add(box);
            setClip(clip);
            setShown(this, false);
        }

        void setOpen(boolean open) {
            progress = open ? 1.0 : 0.0;
            setShown(this, open);
            requestLayout();
        }

        private double naturalHeight(double width) {
            double contentWidth = width < 0.0 ? -1.0 : Math.max(0.0, width - snappedLeftInset() - snappedRightInset());
            return snappedTopInset() + box.prefHeight(contentWidth) + snappedBottomInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            return snapSizeY(naturalHeight(width) * progress);
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
