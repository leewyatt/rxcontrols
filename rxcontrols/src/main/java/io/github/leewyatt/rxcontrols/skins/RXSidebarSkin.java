package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.event.EventTarget;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Skin for {@link RXSidebar}. Builds the five-region container tree (header,
 * pinned-top, scrollable main, pinned-bottom, footer), locks the rail width via
 * the {@code compute*Width} methods, and keeps each item's icon in a fixed left
 * column so icons never move between {@link SidebarMode#EXPANDED} and
 * {@link SidebarMode#MINI}.
 *
 * <p>Cleanup follows the target's lifetime. Anything attached to a fixed target
 * (the control's own properties, this skin's own nodes) is registered with the
 * disposer. Anything attached to a moving target — the items, which come and go
 * with the lists, and the scene, which changes under the control — is paired by
 * hand at the attach site and released in {@code disposeSkin()}, because the
 * disposer would capture whichever target happened to be current at
 * registration.</p>
 */
public class RXSidebarSkin extends RXSkinBase<RXSidebar> {

    // ==================== Constants ====================

    private static final double ITEM_HEIGHT = 40.0;
    private static final double ITEM_GAP = 4.0;
    private static final double ICON_SIZE = 24.0;       // matches the CSS .graphic -fx-pref-* size
    private static final double RIGHT_INSET = 12.0;
    private static final double MIN_LEFT_INSET = 0.0;

    // ==================== Container tree ====================

    private final VBox root = new VBox();
    private final StackPane headerSlot = new StackPane();
    private final VBox topBox = new VBox(ITEM_GAP);
    private final ScrollPane mainScroll = new ScrollPane();
    private final VBox mainBox = new VBox(ITEM_GAP);
    private final VBox bottomBox = new VBox(ITEM_GAP);
    private final StackPane footerSlot = new StackPane();

    // Per-item auto tooltip (text mirrors the item; installed only in MINI). Doubles
    // as the registry of wired items for incremental wire/unwire and dispose cleanup.
    private final Map<RXSidebarItem, Tooltip> itemTooltips = new IdentityHashMap<>();

    // Re-establishes the single Tab stop when a wired item's visibility/disabled state
    // changes (those move it in/out of the roving ring). Shared instance, added per
    // item in wireItem and removed in unwireItem.
    private final InvalidationListener focusabilityListener = obs -> refreshTabStop();

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
                    getSkinnable().requestLayout(); // single relayout request; layout pass consumes it
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
        VBox.setVgrow(mainScroll, Priority.ALWAYS);

        root.getChildren().addAll(headerSlot, topBox, mainScroll, bottomBox, footerSlot);
        // setAll (not add): a replacing skin's constructor takes ownership of the
        // children, clearing any nodes left by a prior skin.
        getChildren().setAll(root);

        bindItems(control.getTopItems(), topBox);
        bindItems(control.getItems(), mainBox);
        bindItems(control.getBottomItems(), bottomBox);

        updateHeader();
        updateFooter();
        disposer.registerListener(control.headerProperty(), this::updateHeader);
        disposer.registerListener(control.footerProperty(), this::updateFooter);

        disposer.registerListener(control.modeProperty(), this::onModeChanged);
        disposer.registerListener(control.miniWidthProperty(), this::updateIconColumns);

        // The rail is a single Tab stop; roving moves focus inside it. The rail
        // itself is never that stop — RXSidebar's constructor seeds its own
        // focusTraversable to false.
        disposer.registerListener(control.selectedItemProperty(), this::refreshTabStop);
        disposer.registerListener(control.sceneProperty(), this::trackFocusOwner);
        installKeyboardNavigation();

        snapToMode(); // initialize fraction + per-item mode without animating
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

    // ==================== Item lists -> bands ====================

    private void bindItems(ObservableList<RXSidebarItem> source, Pane band) {
        for (RXSidebarItem item : source) {
            wireItem(item);
        }
        band.getChildren().setAll(nodesOf(source));

        ListChangeListener<RXSidebarItem> listener = change -> onBandChange(change, band);
        source.addListener(listener);
        disposer.registerDisposeTask(() -> source.removeListener(listener));
    }

    private void onBandChange(ListChangeListener.Change<? extends RXSidebarItem> change, Pane band) {
        while (change.next()) {
            if (change.wasPermutated()) {
                band.getChildren().setAll(nodesOf(change.getList()));
            } else {
                if (change.wasRemoved()) {
                    for (RXSidebarItem removed : change.getRemoved()) {
                        unwireItem(removed);
                        band.getChildren().remove(removed.asNode());
                    }
                }
                if (change.wasAdded()) {
                    int insert = change.getFrom();
                    for (RXSidebarItem added : change.getAddedSubList()) {
                        wireItem(added);
                        band.getChildren().add(insert++, added.asNode());
                    }
                }
            }
        }
        refreshTabStop(); // new items default to focus-traversable; keep one Tab stop
    }

    private static List<Node> nodesOf(List<? extends RXSidebarItem> items) {
        List<Node> nodes = new ArrayList<>(items.size());
        for (RXSidebarItem item : items) {
            nodes.add(item.asNode());
        }
        return nodes;
    }

    // Per-item configuration: fixed row height, fill-width band, fixed icon column,
    // accessibleText + mini tooltip bound to text, and the committed mode's content
    // display. Alignment / text overrun stay in CSS; the dynamic left padding and
    // content display are owned by the skin. Idempotent: the
    // text/tooltip bindings are set once per wired item (re-add re-wires fresh).
    private void wireItem(RXSidebarItem item) {
        // Both permitted item types are Labeled (ToggleButton / Button); the
        // sealed interface is what makes this cast safe.
        Labeled node = (Labeled) item.asNode();
        node.setMinHeight(ITEM_HEIGHT);
        node.setPrefHeight(ITEM_HEIGHT);
        node.setMaxWidth(Double.MAX_VALUE);
        applyIconColumn(node);
        if (!itemTooltips.containsKey(item)) {
            // accessibleText mirrors text so screen readers keep the name in MINI.
            node.accessibleTextProperty().bind(node.textProperty());
            Tooltip tip = new Tooltip();
            tip.textProperty().bind(node.textProperty());
            itemTooltips.put(item, tip);
            node.visibleProperty().addListener(focusabilityListener);
            node.disabledProperty().addListener(focusabilityListener);
        }
        applyModeToItem(item, committedMode());
    }

    // Reverse of wireItem for a removed item: uninstall + unbind its tooltip, drop the
    // focusability listener, unbind accessibleText, and restore focus-traversability.
    private void unwireItem(RXSidebarItem item) {
        Labeled node = (Labeled) item.asNode();
        Tooltip tip = itemTooltips.remove(item);
        if (tip != null) {
            Tooltip.uninstall(node, tip);
            tip.textProperty().unbind();
        }
        node.visibleProperty().removeListener(focusabilityListener);
        node.disabledProperty().removeListener(focusabilityListener);
        node.accessibleTextProperty().unbind();
        // Both permitted item types are ButtonBase (default focusTraversable =
        // true); restore that default so a reused item behaves like a normal node.
        node.setFocusTraversable(true);
    }

    // ==================== Zero-jump icon column ====================

    // The icon's left edge sits at leftInset in BOTH modes, so it never moves during
    // a transition. leftInset = max(MIN_LEFT_INSET, (miniWidth - ICON_SIZE) / 2) centers
    // the icon at miniWidth/2 unless miniWidth < ICON_SIZE, where it clamps to
    // MIN_LEFT_INSET (the left edge is still stable; the center is no longer miniWidth/2).
    private void applyIconColumn(Labeled node) {
        double leftInset = Math.max(MIN_LEFT_INSET, (resolvedMiniWidth() - ICON_SIZE) / 2.0);
        node.setPadding(new Insets(0.0, RIGHT_INSET, 0.0, leftInset));
    }

    private void updateIconColumns() {
        forEachItem(item -> applyIconColumn((Labeled) item.asNode()));
    }

    // ==================== Mode transition (single Timeline) ====================

    private void onModeChanged() {
        SidebarMode target = committedMode(); // direction follows the committed mode, not the transient fraction
        if (!animationsActive()) {
            snapToMode(); // animated=false / illegal duration / off-scene => instant
            return;
        }
        stopAnimation();
        // During the tween every item shows icon + text (LEFT) so the label wipes /
        // reveals with the width via CLIP; the steady-state content display is set on
        // finish by finalizeMini/finalizeExpand.
        forEachItem(item -> ((Labeled) item.asNode()).setContentDisplay(ContentDisplay.LEFT));

        double targetFraction = (target == SidebarMode.EXPANDED) ? 1.0 : 0.0;
        Runnable onFinish = (target == SidebarMode.EXPANDED) ? this::finalizeExpand : this::finalizeMini;
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(expansionFraction, targetFraction, interpolatorOrDefault())));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null; // a superseded Timeline must not clear a newer one
            }
            onFinish.run();
        });
        animation = timeline;
        timeline.play();
    }

    private void snapToMode() {
        stopAnimation();
        boolean expanded = committedMode() == SidebarMode.EXPANDED;
        expansionFraction.set(expanded ? 1.0 : 0.0);
        applyMode(committedMode());
    }

    private void finalizeExpand() {
        expansionFraction.set(1.0);
        applyMode(SidebarMode.EXPANDED);
    }

    private void finalizeMini() {
        expansionFraction.set(0.0);
        applyMode(SidebarMode.MINI);
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

    private void applyMode(SidebarMode mode) {
        forEachItem(item -> applyModeToItem(item, mode));
    }

    private void applyModeToItem(RXSidebarItem item, SidebarMode mode) {
        Labeled node = (Labeled) item.asNode();
        // MINI steady state collapses to icon-only (visually identical to a
        // CLIP-wiped width=miniWidth row, no jump); EXPANDED shows icon + text.
        node.setContentDisplay(mode == SidebarMode.MINI ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        // Tooltip shows the label only in MINI (the label is hidden there). Uninstall
        // first so repeated MINI applications never stack a second install.
        Tooltip tip = itemTooltips.get(item);
        if (tip != null) {
            Tooltip.uninstall(node, tip);
            if (mode == SidebarMode.MINI) {
                Tooltip.install(node, tip);
            }
        }
    }

    private SidebarMode committedMode() {
        SidebarMode mode = getSkinnable().getMode();
        return mode == null ? RXSidebar.DEFAULT_MODE : mode; // null resolves to default (LENIENT)
    }

    private void forEachItem(Consumer<RXSidebarItem> action) {
        RXSidebar control = getSkinnable();
        control.getTopItems().forEach(action);
        control.getItems().forEach(action);
        control.getBottomItems().forEach(action);
    }

    // ==================== Keyboard roving ====================

    private void installKeyboardNavigation() {
        // A capturing FILTER on root, not a bubbling handler: a ScrollPane consumes
        // arrow keys for scrolling on the way up, so a bubbling handler above it
        // would never see them. The filter runs before the ScrollPane and items.
        // Enter/Space (and any non-arrow key) propagate to the focused item.
        disposer.registerEventFilter(root, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventFilter(root, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
    }

    // JavaFX ties click-to-focus to focusTraversable: ButtonBehavior only requests
    // focus on a node that is traversable. With a roving tab stop every item but
    // one is non-traversable, so a click would move focus nowhere (the web's
    // tabindex="-1" still allows click-focus; focusTraversable=false does not).
    // Take focus here, ahead of the button behavior, so the pointer and the roving
    // point never disagree.
    private void onMousePressed(MouseEvent event) {
        Node member = ringMemberOf(event.getTarget(), focusRing());
        if (member != null) {
            member.requestFocus();   // the focus-owner listener moves the tab stop
        }
    }

    // The press may land on an item's graphic or label rather than the item node.
    // Walking out of the rail (header / footer content) simply finds no member.
    private static Node ringMemberOf(EventTarget target, List<Node> ring) {
        Node node = (target instanceof Node) ? (Node) target : null;
        while (node != null) {
            if (ring.contains(node)) {
                return node;
            }
            node = node.getParent();
        }
        return null;
    }

    private void onKeyPressed(KeyEvent event) {
        List<Node> ring = focusRing();
        int current = indexOfFocused(ring);
        if (current < 0) {
            return; // focus is not on a rail item (e.g. header/footer content) — leave it alone
        }
        int size = ring.size();
        switch (event.getCode()) {
            case UP -> {
                focusRingMember(ring, Math.floorMod(current - 1, size));
                event.consume();
            }
            case DOWN -> {
                focusRingMember(ring, Math.floorMod(current + 1, size));
                event.consume();
            }
            case HOME -> {
                focusRingMember(ring, 0);
                event.consume();
            }
            case END -> {
                focusRingMember(ring, size - 1);
                event.consume();
            }
            default -> {
                // Enter / Space propagate to the focused item; Tab and others go to
                // the scene traversal engine. The filter consumes nothing here.
            }
        }
    }

    // The arrow-roving ring: every visible, enabled item node across the three lists.
    // (focusTraversable is the dynamic single-Tab-stop marker, NOT an arrow filter.)
    private List<Node> focusRing() {
        List<Node> ring = new ArrayList<>();
        addFocusable(ring, getSkinnable().getTopItems());
        addFocusable(ring, getSkinnable().getItems());
        addFocusable(ring, getSkinnable().getBottomItems());
        return ring;
    }

    private void addFocusable(List<Node> ring, List<? extends RXSidebarItem> source) {
        for (RXSidebarItem item : source) {
            Node node = item.asNode(); // interface lacks isVisible/isDisabled; go via the node
            if (node.isVisible() && !node.isDisabled()) {
                ring.add(node);
            }
        }
    }

    private void focusRingMember(List<Node> ring, int index) {
        // requestFocus works on a non-traversable node (Scene.requestFocus gates on
        // scene/disabled/visible, not on focusTraversable). The focus-owner listener
        // moves the tab stop from here.
        ring.get(index).requestFocus();
    }

    // Uses the scene focus owner (not Node.isFocused, which is false when the window
    // is unfocused — and always false headless) so roving is window-focus independent.
    private int indexOfFocused(List<Node> ring) {
        Scene scene = getSkinnable().getScene();
        Node focused = (scene == null) ? null : scene.getFocusOwner();
        return (focused == null) ? -1 : ring.indexOf(focused);
    }

    // Single Tab stop: exactly one ring member is Tab-reachable. Idempotent, so
    // every transition (roving, click, selection, membership, visibility) just
    // calls this and the rule below decides.
    private void refreshTabStop() {
        List<Node> ring = focusRing();
        setSoleTabStop(ring, preferredTabStop(ring));
    }

    // The rule: the tab stop follows focus.
    //
    // While a rail item holds focus it MUST be the Tab-reachable one. Anything
    // else leaves it focused-but-unreachable, and Shift+Tab out of it would find
    // the other traversable item and re-enter the rail instead of leaving it.
    // Only when focus is elsewhere does the tab stop become the way in: the
    // selected item, or the first one when nothing is selected.
    //
    // This also removes the need to drag focus around on selection changes:
    // because the focused item stays the tab stop, a selection change can never
    // strand it.
    private Node preferredTabStop(List<Node> ring) {
        if (ring.isEmpty()) {
            return null;
        }
        int focused = indexOfFocused(ring);
        if (focused >= 0) {
            return ring.get(focused);
        }
        RXSidebarNavItem selected = getSkinnable().getSelectedItem();
        if (selected != null && ring.contains(selected.asNode())) {
            return selected.asNode();
        }
        return ring.get(0);
    }

    private void setSoleTabStop(List<Node> ring, Node target) {
        for (Node node : ring) {
            node.setFocusTraversable(node == target);
        }
    }

    // ==================== Width resolution + compute ====================

    // Any unusable width (NaN, +/-infinity, negative) resolves to the default
    // rather than to zero: infinity must be rejected here because railWidth()
    // interpolates mini -> expanded, and infinity - infinity is NaN, which would
    // poison the whole layout with a NaN rail width.
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
        // Per-item resources (tooltips, accessibleText bindings, roving tab-stop) are
        // managed manually, paired with item add/remove, so release the still-wired
        // ones here (dynamic-node resources don't go through the disposer).
        for (RXSidebarItem item : List.copyOf(itemTooltips.keySet())) {
            unwireItem(item);
        }
        // Pair the constructor's getChildren().setAll(root): remove our root so a
        // setSkin(null) (no replacing skin to clear it) leaves no stale node
        // behind. Listeners/event filters are released by the disposer.
        getChildren().remove(root);
    }
}
