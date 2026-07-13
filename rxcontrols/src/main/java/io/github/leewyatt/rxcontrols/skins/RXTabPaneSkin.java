package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXTab;
import io.github.leewyatt.rxcontrols.RXTabEvent;
import io.github.leewyatt.rxcontrols.RXTabPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXTabPane}. Builds a header strip of per-tab cells with
 * a sliding underline indicator and a single content region that hosts the
 * selected tab's page.
 *
 * <p>The header is a dedicated {@code .header} region that lays out its cells and
 * the indicator in one pass, so the indicator is positioned from the same cell
 * geometry computed that frame (avoiding the stale-bounds trap of reading a child
 * container's bounds). The indicator slides via a {@code translateX} transform
 * plus an unmanaged {@code resize} (never {@code layoutX}, which would dirty
 * ancestor layout every frame); the field-held {@link Timeline} is rebuilt on
 * each selection (latest-wins) and snaps immediately when animation is disabled
 * or the duration is non-positive.</p>
 *
 * <p>Content is detached by default: only the selected tab's content is attached
 * to the content region, so the pane's preferred size follows the current page.</p>
 */
public class RXTabPaneSkin extends RXSkinBase<RXTabPane> {

    // ==================== Pseudo-classes ====================

    // Side/variant pseudo-classes are owned by the control; the skin only uses
    // left/right to point the scroll-button chevrons.
    private static final PseudoClass LEFT_PSEUDO = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO = PseudoClass.getPseudoClass("right");

    private static final PseudoClass SELECTED_PSEUDO = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass FIRST_PSEUDO = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST_PSEUDO = PseudoClass.getPseudoClass("last");
    // The focus ring is drawn on the cell that holds the roving keyboard focus,
    // following the ListCell convention (cells are never real focus owners, so
    // this pseudo-class has a single writer).
    private static final PseudoClass FOCUSED_PSEUDO = PseudoClass.getPseudoClass("focused");

    // ==================== Constants ====================

    private static final String TAB_STYLE_CLASS = "tab";
    private static final String RIPPLE_PANE_STYLE_CLASS = "rx-ripple-pane";
    private static final double DEFAULT_HEADER_MIN_HEIGHT = 48.0;
    private static final double GEOMETRY_EPSILON = 0.5;
    /** Floor width for a scroll button when CSS has not yet supplied a preferred size. */
    private static final double MIN_SCROLL_BUTTON_WIDTH = 24.0;
    /** A content transition is a two-page swap (outgoing + incoming). */
    private static final int CONTENT_PAGE_COUNT = 2;

    /** Symmetric ease-in-out matching the Material standard-easing curve. */
    private static final Interpolator SLIDE_EASING = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    // ==================== Nodes ====================

    private final TabHeaderArea headerArea = new TabHeaderArea();
    private final StackPane contentRegion = new StackPane();
    private final Region indicator = new Region();
    private final List<TabHeaderCell> cells = new ArrayList<>();
    private final RXButton leftScrollButton = createScrollButton(true);
    private final RXButton rightScrollButton = createScrollButton(false);
    private final Rectangle headerClip = new Rectangle();

    private Node currentContent;
    private final PageTransitionEngine contentEngine = new PageTransitionEngine();
    /** Selected index at the last content settle, for inferring the transition direction. */
    private int lastContentIndex = -1;

    // ==================== Scroll state (SCROLLABLE variant) ====================

    /** Distance the cell strip is shifted left within the header viewport (&ge; 0). */
    private double scrollOffset;
    /** Set on a selection change so the next layout scrolls the selected cell into view. */
    private boolean ensureVisibleRequested;
    /** Primary-axis viewport extent and max offset from the last layout, so scroll gestures clamp between passes. */
    private double lastViewportPrimary;
    private double lastMaxScrollOffset;

    // ==================== Indicator animation state ====================

    // All four axes animate so the indicator slides on the primary axis for both
    // horizontal (TOP/BOTTOM: X/width) and vertical (LEFT/RIGHT: Y/height) sides.
    private final DoubleProperty indicatorX = new SimpleDoubleProperty(this, "indicatorX", 0.0);
    private final DoubleProperty indicatorY = new SimpleDoubleProperty(this, "indicatorY", 0.0);
    private final DoubleProperty indicatorWidth = new SimpleDoubleProperty(this, "indicatorWidth", 0.0);
    private final DoubleProperty indicatorHeight = new SimpleDoubleProperty(this, "indicatorHeight", 0.0);
    private double animTargetX;
    private double animTargetY;
    private double animTargetWidth;
    private double animTargetHeight;

    private Timeline slideTimeline;
    private boolean indicatorPositioned;
    private boolean pendingSelectionAnimation;

    // ==================== Keyboard state ====================

    /** Roving focus index within the header, or {@code -1} for no focused tab. */
    private int focusedIndex = -1;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given control.
     *
     * @param control the tab pane
     */
    public RXTabPaneSkin(RXTabPane control) {
        super(control);

        headerArea.getStyleClass().add("header");
        headerArea.setManaged(false);
        // Clip the header so overflowing cells in the SCROLLABLE variant never paint
        // past the header rectangle (or under the neighbouring content region).
        headerArea.setClip(headerClip);
        contentRegion.getStyleClass().add("content");
        indicator.getStyleClass().add("indicator");
        indicator.setManaged(false);
        indicator.setMouseTransparent(true);

        getChildren().setAll(contentRegion, headerArea);

        rebuildCells();
        updateContent();
        syncFocusToSelection();

        disposer.registerListener(indicatorX, this::applyIndicatorGeometry);
        disposer.registerListener(indicatorY, this::applyIndicatorGeometry);
        disposer.registerListener(indicatorWidth, this::applyIndicatorGeometry);
        disposer.registerListener(indicatorHeight, this::applyIndicatorGeometry);
        disposer.registerListener(control.getTabs(), this::rebuildCells);
        disposer.registerListener(control.selectedIndexProperty(), this::onSelectionChanged);
        // Content and SELECTED_TAB close-button visibility follow selectedItem (not
        // selectedIndex): the model sets the item after the index, so reading the
        // selected item / RXTab.selected flags from the index listener would see the
        // previous page.
        disposer.registerListener(control.selectedItemProperty(), this::onSelectedItemChanged);
        disposer.registerListener(control.variantProperty(), this::onVariantChanged);
        disposer.registerListener(control.tabMinWidthProperty(), control::requestLayout);
        disposer.registerListener(control.tabMaxWidthProperty(), control::requestLayout);
        disposer.registerListener(control.scrollButtonPolicyProperty(), control::requestLayout);
        disposer.registerListener(control.tabAlignmentProperty(), control::requestLayout);
        disposer.registerListener(control.dynamicHeightProperty(), control::requestLayout);
        disposer.registerListener(control.preserveContentProperty(), this::updateContent);
        disposer.registerListener(control.tabClosingPolicyProperty(), this::updateAllCloseButtons);
        disposer.registerListener(control.focusedProperty(), this::onFocusChanged);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(headerArea, ScrollEvent.SCROLL, this::onScroll);
        disposer.registerEventHandler(leftScrollButton, ActionEvent.ACTION, event -> {
            scrollByPage(-1);
            event.consume();
        });
        disposer.registerEventHandler(rightScrollButton, ActionEvent.ACTION, event -> {
            scrollByPage(1);
            event.consume();
        });
    }

    // ==================== Cell building ====================

    private void rebuildCells() {
        for (TabHeaderCell cell : cells) {
            cell.detach();
        }
        cells.clear();
        List<Node> children = new ArrayList<>();
        List<RXTab> tabs = getSkinnable().getTabs();
        for (int i = 0; i < tabs.size(); i++) {
            TabHeaderCell cell = new TabHeaderCell(tabs.get(i));
            cells.add(cell);
            children.add(cell);
        }
        children.add(indicator);
        // Scroll buttons render above the cells so a cell scrolled under them is masked.
        children.add(leftScrollButton);
        children.add(rightScrollButton);
        headerArea.updateChildren(children);
        // A structural change reflows the strip: re-anchor by snapping to the new
        // selected cell rather than sliding across the reflow.
        indicatorPositioned = false;
        updateCellPositionPseudoClasses();
        updateSelectedPseudoClass();
        updateAllCloseButtons();
        syncFocusToSelection();
        updateContent();
        getSkinnable().requestLayout();
    }

    private void updateCellPositionPseudoClasses() {
        int count = cells.size();
        for (int i = 0; i < count; i++) {
            TabHeaderCell cell = cells.get(i);
            cell.pseudoClassStateChanged(FIRST_PSEUDO, i == 0);
            cell.pseudoClassStateChanged(LAST_PSEUDO, i == count - 1);
        }
    }

    private void updateSelectedPseudoClass() {
        int selected = getSkinnable().getSelectedIndex();
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).pseudoClassStateChanged(SELECTED_PSEUDO, i == selected);
        }
    }

    private void onSelectionChanged() {
        updateSelectedPseudoClass();
        syncFocusToSelection();
        pendingSelectionAnimation = true;
        ensureVisibleRequested = true;
        getSkinnable().requestLayout();
    }

    private void onSelectedItemChanged() {
        updateContent();
        // SELECTED_TAB shows the close button only on the selected tab; keyed off the
        // RXTab.selected flags the control flips on the selectedItem change.
        updateAllCloseButtons();
    }

    private void onCellPressed(TabHeaderCell cell) {
        RXTabPane control = getSkinnable();
        if (cell.tab.isDisable()) {
            return;
        }
        control.requestFocus();
        int index = cells.indexOf(cell);
        focusedIndex = index;
        if (index != control.getSelectedIndex()) {
            selectTab(index);
        }
        updateFocusRing();
    }

    private void selectTab(int index) {
        if (getSkinnable().getSelectionModel() != null) {
            getSkinnable().getSelectionModel().select(index);
        }
    }

    // ==================== Content ====================

    private void updateContent() {
        RXTabPane control = getSkinnable();
        RXTab selected = control.getSelectedItem();
        Node newContent = selected == null ? null : selected.getContent();
        // A real page change animates when a contentAnimation is configured and both
        // pages exist; everything else (initial fill, to/from null, preserveContent or
        // alignment toggles that keep the same page) settles instantly.
        if (newContent != currentContent && control.getContentAnimation() != null
                && currentContent != null && newContent != null && canAnimateContent()) {
            animateContentChange(newContent);
        } else {
            setContentImmediate(newContent);
        }
    }

    private boolean canAnimateContent() {
        RXTabPane control = getSkinnable();
        return PageTransitionEngine.canAnimate(control.getContentAnimation(), control.isAnimated(),
                CONTENT_PAGE_COUNT, control.getAnimationDuration(), false);
    }

    private void setContentImmediate(Node newContent) {
        RXTabPane control = getSkinnable();
        // Cancel any in-flight page transition so its deferred onSettled cannot later
        // re-show a stale page over this immediate result (e.g. clearing the selection
        // or a structural rebuild mid-transition). When this runs AS a settle callback
        // the engine has already cleared the flag, so the guard is a no-op there.
        if (contentEngine.isTransitioning()) {
            contentEngine.interrupt();
        }
        currentContent = newContent;
        if (control.isPreserveContent()) {
            reconcilePreservedContent(newContent);
        } else {
            reconcileDetachedContent(newContent);
        }
        lastContentIndex = control.getSelectedIndex();
        control.requestLayout();
    }

    /** Detach mode: only the selected content stays attached. */
    private void reconcileDetachedContent(Node selected) {
        detachExcept(child -> child == selected);
        if (selected != null) {
            if (!contentRegion.getChildren().contains(selected)) {
                contentRegion.getChildren().add(selected);
            }
            resetPageState(selected);
        }
    }

    /** Keep-all mode: every tab's content stays attached; only the selected shows. */
    private void reconcilePreservedContent(Node selected) {
        List<Node> desired = new ArrayList<>();
        for (RXTab tab : getSkinnable().getTabs()) {
            Node content = tab.getContent();
            if (content != null && !desired.contains(content)) {
                desired.add(content);
            }
        }
        detachExcept(desired::contains);
        for (Node content : desired) {
            if (!contentRegion.getChildren().contains(content)) {
                contentRegion.getChildren().add(content);
            }
            if (content == selected) {
                resetPageState(content);
            } else {
                content.setVisible(false);
                content.setManaged(false);
            }
        }
    }

    /**
     * Detaches every content child not matching {@code keep}, first restoring each to a
     * neutral state so a page handed back to the application is never left hidden,
     * unmanaged, or carrying a mid-transition transform.
     */
    private void detachExcept(Predicate<Node> keep) {
        List<Node> stale = new ArrayList<>();
        for (Node child : contentRegion.getChildren()) {
            if (!keep.test(child)) {
                stale.add(child);
            }
        }
        for (Node child : stale) {
            resetPageState(child);
        }
        contentRegion.getChildren().removeAll(stale);
    }

    /**
     * Resets a page node to a neutral, reusable state: visible, managed, and free of
     * any residual transition transform. Used both to show the live page and to hand a
     * detached page back to the application in a clean state (a preserved page may be
     * hidden/unmanaged, and an animated swap leaves the outgoing page invisible).
     */
    private static void resetPageState(Node content) {
        content.setVisible(true);
        content.setManaged(true);
        content.setTranslateX(0.0);
        content.setTranslateY(0.0);
        content.setOpacity(1.0);
        content.setClip(null);
        // Page animations also drive scale/rotate (e.g. Zoom, Flip); clear them so a
        // page interrupted mid-tween is handed back without a residual transform.
        content.setScaleX(1.0);
        content.setScaleY(1.0);
        content.setRotate(0.0);
        content.setRotationAxis(Rotate.Z_AXIS);
    }

    private void animateContentChange(Node newContent) {
        RXTabPane control = getSkinnable();
        // Latest-wins: settle any in-flight tween first. The engine fires the
        // external-stop callback during this call, before the stopped animation's
        // finish action restores end state, so that callback must not remove pages.
        if (contentEngine.isTransitioning()) {
            contentEngine.interrupt();
        }
        Node oldContent = currentContent;
        // Keep exactly the outgoing + incoming pages attached for the tween; a
        // prior interrupt may have left a stale page behind. Detach it through
        // detachExcept so it is handed back neutral (not carrying a transform from
        // the interrupted tween). Preserve mode keeps every page attached.
        if (!control.isPreserveContent()) {
            detachExcept(child -> child == oldContent || child == newContent);
        }
        if (!contentRegion.getChildren().contains(newContent)) {
            contentRegion.getChildren().add(newContent);
        }
        resetPageState(oldContent);
        resetPageState(newContent);

        TransitionDirection direction = control.getSelectedIndex() >= lastContentIndex
                ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        // currentIndex 0, nextIndex 1, pageCount CONTENT_PAGE_COUNT: the engine's
        // two-page view of this swap; the page provider maps those indices to the nodes.
        TransitionContext context = new TransitionContext(oldContent, newContent, 0, 1, CONTENT_PAGE_COUNT,
                direction, control.getAnimationDuration(), contentRegion,
                index -> index == 0 ? oldContent : newContent,
                TransitionContext.LifecycleCallback.NOOP);

        contentEngine.play(control.getContentAnimation(), context,
                () -> {
                    // Mirror state advances as the tween starts: the engine runs
                    // this after wiring handlers, safe for zero-duration and for a
                    // re-entrant call reading currentContent as the next old page.
                    currentContent = newContent;
                    lastContentIndex = control.getSelectedIndex();
                },
                () -> setContentImmediate(newContent),
                () -> resetPageState(newContent));
        control.requestLayout();
    }

    private double contentPrefWidth() {
        if (getSkinnable().isPreserveContent()) {
            return preservedPref(maxContentPref(true),
                    contentRegion.snappedLeftInset() + contentRegion.snappedRightInset());
        }
        return currentContent == null ? 0.0 : contentRegion.prefWidth(-1);
    }

    private double contentPrefHeight() {
        RXTabPane control = getSkinnable();
        if (control.isPreserveContent()) {
            double insets = contentRegion.snappedTopInset() + contentRegion.snappedBottomInset();
            if (control.isDynamicHeight()) {
                return preservedPref(currentContent == null ? 0.0 : currentContent.prefHeight(-1), insets);
            }
            // Locked to the tallest page so switching does not jump the pane height.
            return preservedPref(maxContentPref(false), insets);
        }
        return currentContent == null ? 0.0 : contentRegion.prefHeight(-1);
    }

    /**
     * Preserve-mode content pref: the measured page size plus the {@code .content}
     * region insets (which the {@code StackPane} folds into {@code contentRegion.pref*}
     * on the detach path), or {@code 0} when there is no content to show.
     */
    private static double preservedPref(double contentSize, double insets) {
        return contentSize <= 0.0 ? 0.0 : contentSize + insets;
    }

    private double maxContentPref(boolean width) {
        double max = 0.0;
        for (RXTab tab : getSkinnable().getTabs()) {
            Node content = tab.getContent();
            if (content != null) {
                max = Math.max(max, width ? content.prefWidth(-1) : content.prefHeight(-1));
            }
        }
        return max;
    }

    private void onTabContentChanged(RXTab tab) {
        // Preserve mode tracks every tab's content; detach mode only the selected one.
        if (getSkinnable().isPreserveContent() || tab == getSkinnable().getSelectedItem()) {
            updateContent();
        }
    }

    // ==================== Closing ====================

    private boolean isCloseAllowed(RXTab tab) {
        if (!tab.isClosable()) {
            return false;
        }
        RXTabPane.TabClosingPolicy policy = getSkinnable().getTabClosingPolicy();
        if (policy == null) {
            policy = RXTabPane.TabClosingPolicy.UNAVAILABLE;
        }
        switch (policy) {
            case ALL_TABS:
                return true;
            case SELECTED_TAB:
                return tab.isSelected();
            default:
                return false;
        }
    }

    private void updateAllCloseButtons() {
        for (TabHeaderCell cell : cells) {
            cell.updateCloseButton();
        }
    }

    /**
     * Runs the close pipeline for a tab: fire {@code TAB_CLOSE_REQUEST} (a
     * single event, so either the tab or pane handler can veto by consuming),
     * and if not vetoed remove the tab and fire two fresh {@code TAB_CLOSED}
     * events (so a consumed tab handler cannot gag the pane handler).
     */
    private void requestClose(RXTab tab) {
        RXTabPane pane = getSkinnable();
        // The event fires on the pane because RXTab is not an EventTarget. A veto
        // survives dispatch via RXTabEvent's shared consumedFlag (in-scene dispatch
        // still copyFor()-clones per hop and resets the inherited consumed field), so
        // isConsumed() below reflects a consume on any copy — see RXTabEvent.
        RXTabEvent closeRequest = new RXTabEvent(tab, pane, RXTabEvent.TAB_CLOSE_REQUEST);
        EventHandler<RXTabEvent> onCloseRequest = tab.getOnCloseRequest();
        if (onCloseRequest != null) {
            onCloseRequest.handle(closeRequest);
        }
        if (closeRequest.isConsumed()) {
            return;
        }
        pane.fireEvent(closeRequest);
        if (closeRequest.isConsumed()) {
            return;
        }
        // Only fire TAB_CLOSED when this pipeline actually removed the tab; a
        // request handler may have already removed it without consuming, and a
        // CLOSED for a tab this pipeline did not remove would be spurious.
        if (!pane.getTabs().remove(tab)) {
            return;
        }
        // Fixed order: tab handler first, then pane. Fresh events so a consumed
        // tab handler does not short-circuit the pane's dispatch.
        EventHandler<RXTabEvent> onClosed = tab.getOnClosed();
        if (onClosed != null) {
            onClosed.handle(new RXTabEvent(tab, pane, RXTabEvent.TAB_CLOSED));
        }
        pane.fireEvent(new RXTabEvent(tab, pane, RXTabEvent.TAB_CLOSED));
    }

    // ==================== Keyboard navigation ====================

    private void onKeyPressed(KeyEvent event) {
        // Act as the tablist only when the control itself owns focus. A key event
        // bubbling up from a focused node inside the selected tab's content has that
        // node as its target (not the pane); handling it here would let the pane
        // hijack arrow/HOME/END/DELETE meant for the page — e.g. closing the tab the
        // user is editing. Events targeted at the focus-owning pane have target==pane.
        if (event.getTarget() != getSkinnable()) {
            return;
        }
        if (cells.isEmpty()) {
            return;
        }
        boolean vertical = getSkinnable().effectiveSide().isVertical();
        // Mirror the horizontal arrows in a right-to-left layout (Left advances).
        int horizontal = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT
                ? -1 : 1;
        int delta = 0;
        switch (event.getCode()) {
            case LEFT:
                delta = vertical ? 0 : -horizontal;
                break;
            case RIGHT:
                delta = vertical ? 0 : horizontal;
                break;
            case UP:
                delta = vertical ? -1 : 0;
                break;
            case DOWN:
                delta = vertical ? 1 : 0;
                break;
            case HOME:
                moveFocusTo(firstAvailable());
                event.consume();
                return;
            case END:
                moveFocusTo(lastAvailable());
                event.consume();
                return;
            case SPACE:
            case ENTER:
                if (!getSkinnable().isSelectionFollowsFocus() && focusedIndex >= 0) {
                    selectTab(focusedIndex);
                    event.consume();
                }
                return;
            case DELETE:
                if (focusedIndex >= 0 && focusedIndex < cells.size()) {
                    RXTab target = cells.get(focusedIndex).tab;
                    // A disabled tab is inert: the mouse close paths are already
                    // blocked by node-disable propagation, so guard the keyboard path
                    // to match (never close a disabled tab).
                    if (!target.isDisable() && isCloseAllowed(target)) {
                        requestClose(target);
                        event.consume();
                    }
                }
                return;
            default:
                return;
        }
        if (delta == 0) {
            return;
        }
        moveFocusBy(delta);
        event.consume();
    }

    private void moveFocusBy(int delta) {
        int target;
        if (focusedIndex < 0) {
            // From no focus: Right/Down start at the first available, Left/Up at the last.
            target = delta > 0 ? firstAvailable() : lastAvailable();
        } else {
            target = wrapToAvailable(focusedIndex, delta);
        }
        moveFocusTo(target);
    }

    private void moveFocusTo(int target) {
        if (target < 0) {
            return;
        }
        focusedIndex = target;
        if (getSkinnable().isSelectionFollowsFocus()) {
            // Automatic: focus and selection stay coincident.
            selectTab(target);
        }
        updateFocusRing();
        getSkinnable().notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
    }

    /** Steps one available tab in {@code delta} direction, wrapping around. */
    private int wrapToAvailable(int from, int delta) {
        int count = cells.size();
        for (int step = 1; step <= count; step++) {
            int index = Math.floorMod(from + delta * step, count);
            if (isAvailable(index)) {
                return index;
            }
        }
        return -1;
    }

    private int firstAvailable() {
        for (int i = 0; i < cells.size(); i++) {
            if (isAvailable(i)) {
                return i;
            }
        }
        return -1;
    }

    private int lastAvailable() {
        for (int i = cells.size() - 1; i >= 0; i--) {
            if (isAvailable(i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isAvailable(int index) {
        return index >= 0 && index < cells.size() && !cells.get(index).tab.isDisable();
    }

    /**
     * Re-derives the focus index from the selection: a selection change (or a
     * structural rebuild) moves the roving focus onto the selected tab, in both
     * automatic and manual modes. Plain manual arrow navigation does not change
     * the selection, so it does not call this and keeps its diverged focus.
     */
    private void syncFocusToSelection() {
        focusedIndex = getSkinnable().getSelectedIndex();
        updateFocusRing();
    }

    private void onFocusChanged() {
        // Entering the tablist with no focused tab lands on the first available
        // one (automatic then also selects it); see keyboard spec §2.3.
        if (getSkinnable().isFocused() && focusedIndex < 0) {
            int first = firstAvailable();
            if (first >= 0) {
                moveFocusTo(first);
                return;
            }
        }
        updateFocusRing();
    }

    private void updateFocusRing() {
        boolean showRing = getSkinnable().isFocused();
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).pseudoClassStateChanged(FOCUSED_PSEUDO, showRing && i == focusedIndex);
        }
    }

    // ==================== Variant change ====================

    private void onVariantChanged() {
        // The side/variant pseudo-classes are owned by the control (set in its
        // constructor and property invalidated()); the skin only reacts to layout.
        if (isScrollable()) {
            // Entering SCROLLABLE: reveal the current selection on the next pass.
            ensureVisibleRequested = true;
        } else {
            // Leaving SCROLLABLE drops any scroll shift so the strip re-anchors at the start.
            scrollOffset = 0.0;
        }
        getSkinnable().requestLayout();
    }

    // ==================== Scrolling (SCROLLABLE variant) ====================

    private boolean isScrollable() {
        return getSkinnable().getVariant() == RXTabPane.Variant.SCROLLABLE;
    }

    private boolean isVertical() {
        return getSkinnable().effectiveSide().isVertical();
    }

    private double alignmentOffset(double slack) {
        RXTabPane.TabAlignment align = getSkinnable().getTabAlignment();
        if (align == null) {
            align = RXTabPane.TabAlignment.START;
        }
        switch (align) {
            case CENTER:
                return slack / 2.0;
            case END:
                return slack;
            default:
                return 0.0;
        }
    }

    private void onScroll(ScrollEvent event) {
        if (!isScrollable() || !getSkinnable().isWheelScrollEnabled()) {
            return;
        }
        double delta;
        if (isVertical()) {
            // Vertical strip: only a vertical wheel/trackpad gesture advances it.
            delta = event.getDeltaY();
        } else {
            // Horizontal strip: a plain wheel reports deltaY, a trackpad deltaX.
            delta = Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY())
                    ? event.getDeltaX() : event.getDeltaY();
        }
        if (delta == 0.0) {
            return;
        }
        // Natural direction: scrolling down/right advances the strip (offset grows).
        if (scrollBy(-delta)) {
            event.consume();
        }
    }

    private void scrollByPage(int direction) {
        scrollBy(direction * headerViewportPrimary());
    }

    /** Shifts the strip by {@code delta} px (clamped in layout); returns whether it moved. */
    private boolean scrollBy(double delta) {
        double previous = scrollOffset;
        scrollOffset = clampScrollOffset(scrollOffset + delta);
        if (Math.abs(scrollOffset - previous) < GEOMETRY_EPSILON) {
            return false;
        }
        getSkinnable().requestLayout();
        return true;
    }

    private double clampScrollOffset(double value) {
        return Math.max(0.0, Math.min(value, lastMaxScrollOffset));
    }

    private double headerViewportPrimary() {
        return lastViewportPrimary;
    }

    private RXTabPane.ScrollButtonPolicy scrollButtonPolicy() {
        RXTabPane.ScrollButtonPolicy policy = getSkinnable().getScrollButtonPolicy();
        return policy == null ? RXTabPane.ScrollButtonPolicy.AUTO : policy;
    }

    /** Scroll-button size along the strip: its width (horizontal) or height (vertical). */
    private double scrollButtonPrimarySize(RXButton button, double crossExtent, boolean vertical) {
        double pref = vertical ? button.prefHeight(crossExtent) : button.prefWidth(crossExtent);
        double snapped = vertical ? snapSizeY(pref) : snapSizeX(pref);
        return Math.max(MIN_SCROLL_BUTTON_WIDTH, snapped);
    }

    private void layoutScrollButtons(boolean show, boolean vertical, double primaryStart, double crossStart,
                                     double crossExtent, double backSize, double fwdSize, double primaryExtent) {
        setScrollButtonVisible(leftScrollButton, show);
        setScrollButtonVisible(rightScrollButton, show);
        if (!show) {
            return;
        }
        double fwdStart = primaryStart + primaryExtent - fwdSize;
        if (vertical) {
            leftScrollButton.resizeRelocate(crossStart, primaryStart, crossExtent, backSize);
            rightScrollButton.resizeRelocate(crossStart, fwdStart, crossExtent, fwdSize);
        } else {
            leftScrollButton.resizeRelocate(primaryStart, crossStart, backSize, crossExtent);
            rightScrollButton.resizeRelocate(fwdStart, crossStart, fwdSize, crossExtent);
        }
        // Grey out at the travel limits (drives the :disabled chevron).
        leftScrollButton.setDisable(scrollOffset <= GEOMETRY_EPSILON);
        rightScrollButton.setDisable(scrollOffset >= lastMaxScrollOffset - GEOMETRY_EPSILON);
    }

    private void hideScrollButtons() {
        setScrollButtonVisible(leftScrollButton, false);
        setScrollButtonVisible(rightScrollButton, false);
    }

    private static void setScrollButtonVisible(RXButton button, boolean visible) {
        button.setVisible(visible);
    }

    private static RXButton createScrollButton(boolean leftSide) {
        RXButton button = new RXButton();
        button.getStyleClass().add("scroll-button");
        button.pseudoClassStateChanged(leftSide ? LEFT_PSEUDO : RIGHT_PSEUDO, true);
        Region arrow = new Region();
        arrow.getStyleClass().add("arrow");
        arrow.setMouseTransparent(true);
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        button.setGraphic(arrow);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        // Not a focus stop (the tablist keeps a single focus site); positioned manually.
        button.setFocusTraversable(false);
        button.setManaged(false);
        button.setVisible(false);
        return button;
    }

    // ==================== Indicator positioning ====================

    private void applyIndicatorGeometry() {
        // Transform-only positioning: an unmanaged node's layoutX change still
        // bubbles via requestParentLayout, whereas translateX + an unmanaged
        // resize do not. layoutX/Y stay at 0; the transform carries the pose.
        indicator.resize(indicatorWidth.get(), indicatorHeight.get());
        indicator.setLayoutX(0.0);
        indicator.setLayoutY(0.0);
        indicator.setTranslateX(indicatorX.get());
        indicator.setTranslateY(indicatorY.get());
    }

    private void positionIndicator(double x, double y, double width, double height) {
        if (!indicatorPositioned) {
            indicatorPositioned = true;
            pendingSelectionAnimation = false;
            snapTo(x, y, width, height);
            return;
        }
        if (pendingSelectionAnimation) {
            pendingSelectionAnimation = false;
            playSlide(x, y, width, height);
            return;
        }
        if (slideTimeline == null) {
            snapTo(x, y, width, height);
        } else if (differs(x, animTargetX) || differs(y, animTargetY)
                || differs(width, animTargetWidth) || differs(height, animTargetHeight)) {
            playSlide(x, y, width, height);
        } else {
            applyIndicatorGeometry();
        }
    }

    private static boolean differs(double a, double b) {
        return Math.abs(a - b) > GEOMETRY_EPSILON;
    }

    /**
     * Positions the indicator on the header's inner edge (adjacent to the content
     * region) at the selected cell's strip position. TOP/LEFT put the indicator on
     * the far cross-edge; BOTTOM/RIGHT on the near cross-edge.
     */
    private void positionSelectedIndicator(TabHeaderCell cell, boolean vertical,
                                           double crossStart, double crossExtent) {
        Side side = getSkinnable().effectiveSide();
        boolean innerAtFar = side == Side.TOP || side == Side.LEFT;
        double thickness = vertical ? indicator.prefWidth(-1) : indicator.prefHeight(-1);
        double crossPos = crossStart + (innerAtFar ? crossExtent - thickness : 0.0);
        if (vertical) {
            positionIndicator(crossPos, cell.getLayoutY(), thickness, cell.getHeight());
        } else {
            positionIndicator(cell.getLayoutX(), crossPos, cell.getWidth(), thickness);
        }
    }

    private void snapTo(double x, double y, double width, double height) {
        stopSlide();
        animTargetX = x;
        animTargetY = y;
        animTargetWidth = width;
        animTargetHeight = height;
        indicatorX.set(x);
        indicatorY.set(y);
        indicatorWidth.set(width);
        indicatorHeight.set(height);
        applyIndicatorGeometry();
    }

    private void playSlide(double x, double y, double width, double height) {
        if (!shouldAnimate()) {
            snapTo(x, y, width, height);
            return;
        }
        Duration duration = getSkinnable().getAnimationDuration();
        stopSlide();
        animTargetX = x;
        animTargetY = y;
        animTargetWidth = width;
        animTargetHeight = height;
        // The non-primary axis has an identical start/end value (no visual motion),
        // so animating all four keeps one code path for both orientations.
        slideTimeline = new Timeline(new KeyFrame(duration,
                new KeyValue(indicatorX, x, SLIDE_EASING),
                new KeyValue(indicatorY, y, SLIDE_EASING),
                new KeyValue(indicatorWidth, width, SLIDE_EASING),
                new KeyValue(indicatorHeight, height, SLIDE_EASING)));
        slideTimeline.setOnFinished(event -> slideTimeline = null);
        slideTimeline.play();
    }

    private void stopSlide() {
        if (slideTimeline != null) {
            slideTimeline.stop();
            slideTimeline = null;
        }
    }

    private boolean shouldAnimate() {
        return getSkinnable().isAnimated() && isPositiveFinite(getSkinnable().getAnimationDuration());
    }

    private static boolean isPositiveFinite(Duration duration) {
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private void hideIndicator() {
        stopSlide();
        indicatorPositioned = false;
        pendingSelectionAnimation = false;
        indicator.setVisible(false);
        indicator.resize(0.0, 0.0);
        indicator.setLayoutX(0.0);
        indicator.setLayoutY(0.0);
        indicator.setTranslateX(0.0);
        indicator.setTranslateY(0.0);
        indicatorX.set(0.0);
        indicatorY.set(0.0);
        indicatorWidth.set(0.0);
        indicatorHeight.set(0.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        Side side = getSkinnable().effectiveSide();
        boolean vertical = side.isVertical();
        double thickness = Math.min(vertical ? w : h, computeHeaderThickness());

        double headerX;
        double headerY;
        double headerW;
        double headerH;
        double contentX;
        double contentY;
        double contentW;
        double contentH;
        switch (side) {
            case LEFT:
                headerX = x;
                headerY = y;
                headerW = thickness;
                headerH = h;
                contentX = x + thickness;
                contentY = y;
                contentW = w - thickness;
                contentH = h;
                break;
            case RIGHT:
                headerX = x + w - thickness;
                headerY = y;
                headerW = thickness;
                headerH = h;
                contentX = x;
                contentY = y;
                contentW = w - thickness;
                contentH = h;
                break;
            case BOTTOM:
                headerX = x;
                headerY = y + h - thickness;
                headerW = w;
                headerH = thickness;
                contentX = x;
                contentY = y;
                contentW = w;
                contentH = h - thickness;
                break;
            case TOP:
            default:
                headerX = x;
                headerY = y;
                headerW = w;
                headerH = thickness;
                contentX = x;
                contentY = y + thickness;
                contentW = w;
                contentH = h - thickness;
                break;
        }
        headerArea.resizeRelocate(headerX, headerY, headerW, headerH);
        // Force the header to re-lay-out (and thus reposition the indicator) on
        // every pass: a selection change requests control layout without changing
        // the header's size, so the header would otherwise stay clean and the
        // indicator would not follow the new selection.
        headerArea.requestLayout();
        headerArea.layout();
        contentRegion.resizeRelocate(contentX, contentY, Math.max(0.0, contentW), Math.max(0.0, contentH));
    }

    /**
     * Sizes each tab along the strip (widths for horizontal sides, heights for
     * vertical) for the current variant: FULL_WIDTH divides the strip equally, the
     * others use each cell's content-preferred size (clamped by tabMin/MaxWidth on
     * horizontal, where the strip axis is the width).
     */
    private double[] cellPrimarySizes(double available, double crossExtent, boolean vertical) {
        int count = cells.size();
        double[] sizes = new double[count];
        if (getSkinnable().getVariant() == RXTabPane.Variant.FULL_WIDTH) {
            double consumed = 0.0;
            for (int i = 0; i < count; i++) {
                // Snap the cumulative edge on the strip axis: Y for vertical sides.
                double raw = available * (i + 1) / count;
                double edge = vertical ? snapSizeY(raw) : snapSizeX(raw);
                sizes[i] = edge - consumed;
                consumed = edge;
            }
            return sizes;
        }
        double min = getSkinnable().getTabMinWidth();
        double max = getSkinnable().getTabMaxWidth();
        for (int i = 0; i < count; i++) {
            TabHeaderCell cell = cells.get(i);
            double pref = vertical
                    ? snapSizeY(cell.prefHeight(crossExtent))
                    : snapSizeX(clampTabWidth(cell.prefWidth(crossExtent), min, max));
            sizes[i] = pref;
        }
        return sizes;
    }

    private static double clampTabWidth(double value, double min, double max) {
        double clamped = value;
        if (min > 0.0 && clamped < min) {
            clamped = min;
        }
        if (max > 0.0 && clamped > max) {
            clamped = max;
        }
        return Math.max(0.0, clamped);
    }

    /** Header cross-axis size: strip height (horizontal) or strip width (vertical). */
    private double computeHeaderThickness() {
        boolean vertical = isVertical();
        double min = getSkinnable().getTabMinWidth();
        double max = getSkinnable().getTabMaxWidth();
        double thick = 0.0;
        for (TabHeaderCell cell : cells) {
            double clampedWidth = clampTabWidth(cell.prefWidth(-1), min, max);
            // Horizontal thickness is the tallest cell measured at its actual (clamped)
            // width, so a wrapped label's second line is included in the header height.
            thick = Math.max(thick, vertical ? clampedWidth : cell.prefHeight(clampedWidth));
        }
        return Math.max(DEFAULT_HEADER_MIN_HEIGHT, thick);
    }

    /** Sum of tab sizes along the strip: widths (horizontal) or heights (vertical). */
    private double computeHeaderPrimary(boolean minimum) {
        boolean vertical = isVertical();
        double min = getSkinnable().getTabMinWidth();
        double max = getSkinnable().getTabMaxWidth();
        double sum = 0.0;
        for (TabHeaderCell cell : cells) {
            if (vertical) {
                sum += minimum ? cell.minHeight(-1) : cell.prefHeight(-1);
            } else {
                // Clamp to tabMin/MaxWidth so the reported strip length matches the
                // width the cells are actually laid out at (cellPrimarySizes clamps too).
                double raw = minimum ? cell.minWidth(-1) : cell.prefWidth(-1);
                sum += clampTabWidth(raw, min, max);
            }
        }
        return sum;
    }

    /** SCROLLABLE header min along the strip: one tab plus the scroll buttons. */
    private double scrollableMinHeaderPrimary() {
        boolean vertical = isVertical();
        double smallest = 0.0;
        boolean first = true;
        for (TabHeaderCell cell : cells) {
            double m = vertical ? cell.minHeight(-1) : cell.minWidth(-1);
            if (first || m < smallest) {
                smallest = m;
                first = false;
            }
        }
        return smallest + scrollButtonPrimarySize(leftScrollButton, -1, vertical)
                + scrollButtonPrimarySize(rightScrollButton, -1, vertical);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double content = currentContent == null ? 0.0 : contentRegion.minWidth(-1);
        double inner;
        if (isVertical()) {
            inner = computeHeaderThickness() + content;
        } else {
            double header = isScrollable() ? scrollableMinHeaderPrimary() : computeHeaderPrimary(true);
            inner = Math.max(header, content);
        }
        return leftInset + inner + rightInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double content = contentPrefWidth();
        double inner = isVertical()
                ? computeHeaderThickness() + content
                : Math.max(computeHeaderPrimary(false), content);
        return leftInset + inner + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double content = currentContent == null ? 0.0 : contentRegion.minHeight(-1);
        double inner;
        if (isVertical()) {
            double header = isScrollable() ? scrollableMinHeaderPrimary() : computeHeaderPrimary(true);
            inner = Math.max(header, content);
        } else {
            inner = computeHeaderThickness() + content;
        }
        return topInset + inner + bottomInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double content = contentPrefHeight();
        double inner = isVertical()
                ? Math.max(computeHeaderPrimary(false), content)
                : computeHeaderThickness() + content;
        return topInset + inner + bottomInset;
    }

    // ==================== Accessibility ====================

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case FOCUS_ITEM:
                return focusedIndex >= 0 && focusedIndex < cells.size() ? cells.get(focusedIndex) : null;
            case ITEM_COUNT:
                return cells.size();
            case ITEM_AT_INDEX:
                if (parameters != null && parameters.length > 0 && parameters[0] instanceof Integer) {
                    int index = (Integer) parameters[0];
                    return index >= 0 && index < cells.size() ? cells.get(index) : null;
                }
                return null;
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // The slide Timeline is rebuilt many times; stop the current one by reading
        // the live field (a disposer task would hold a stale reference).
        stopSlide();
        // dispose() clears the running flag before stopping the timeline, so the
        // external-stop callback stays silent during teardown.
        contentEngine.dispose(getSkinnable().getContentAnimation());
        for (TabHeaderCell cell : cells) {
            cell.detach();
        }
        // Hand every page back neutral: a stopped transition leaves frozen transforms
        // on its pages, and preserved non-selected pages are hidden/unmanaged.
        for (Node child : contentRegion.getChildren()) {
            resetPageState(child);
        }
        contentRegion.getChildren().clear();
        currentContent = null;
    }

    // ==================== Header area ====================

    private final class TabHeaderArea extends Region {
        void updateChildren(List<Node> nodes) {
            getChildren().setAll(nodes);
        }

        /** Lays out the cells, scroll buttons, and indicator in one pass. */
        @Override
        protected void layoutChildren() {
            int count = cells.size();
            double left = snappedLeftInset();
            double right = snappedRightInset();
            double top = snappedTopInset();
            double bottom = snappedBottomInset();
            double innerW = Math.max(0.0, getWidth() - left - right);
            double innerH = Math.max(0.0, getHeight() - top - bottom);

            // Clip to the header rectangle: the mask that hides cells scrolled out of
            // the viewport (SCROLLABLE) or overflowing the control edges.
            headerClip.setWidth(getWidth());
            headerClip.setHeight(getHeight());

            boolean vertical = isVertical();
            // Primary axis = the strip (X for horizontal sides, Y for vertical);
            // cross axis = the header thickness.
            double primaryExtent = vertical ? innerH : innerW;
            double crossExtent = vertical ? innerW : innerH;
            double primaryStart = vertical ? top : left;
            double crossStart = vertical ? left : top;

            if (count == 0 || innerW <= 0.0 || innerH <= 0.0) {
                hideIndicator();
                hideScrollButtons();
                lastViewportPrimary = primaryExtent;
                lastMaxScrollOffset = 0.0;
                return;
            }

            double[] sizes = cellPrimarySizes(primaryExtent, crossExtent, vertical);
            double total = 0.0;
            for (double size : sizes) {
                total += size;
            }

            boolean scrollable = isScrollable();
            boolean overflow = scrollable && total > primaryExtent + GEOMETRY_EPSILON;
            boolean showButtons = false;
            double backSize = 0.0;
            double fwdSize = 0.0;
            if (scrollable) {
                RXTabPane.ScrollButtonPolicy policy = scrollButtonPolicy();
                showButtons = policy == RXTabPane.ScrollButtonPolicy.ALWAYS
                        || (policy == RXTabPane.ScrollButtonPolicy.AUTO && overflow);
                if (showButtons) {
                    backSize = scrollButtonPrimarySize(leftScrollButton, crossExtent, vertical);
                    fwdSize = scrollButtonPrimarySize(rightScrollButton, crossExtent, vertical);
                }
            }
            double viewport = Math.max(0.0, primaryExtent - backSize - fwdSize);
            double viewportStart = primaryStart + backSize;
            double maxOffset = scrollable ? Math.max(0.0, total - viewport) : 0.0;
            lastViewportPrimary = viewport;
            lastMaxScrollOffset = maxOffset;

            int selected = getSkinnable().getSelectedIndex();
            if (scrollable && ensureVisibleRequested && selected >= 0 && selected < count) {
                ensureCellVisible(sizes, selected, viewport);
            }
            ensureVisibleRequested = false;
            scrollOffset = Math.max(0.0, Math.min(scrollOffset, maxOffset));

            // tabAlignment shifts a non-filling STANDARD strip within the header
            // (ignored when SCROLLABLE, or FULL_WIDTH which already fills).
            double alignOffset = 0.0;
            if (!scrollable && getSkinnable().getVariant() != RXTabPane.Variant.FULL_WIDTH) {
                alignOffset = alignmentOffset(Math.max(0.0, primaryExtent - total));
            }
            double cursor = viewportStart - scrollOffset + alignOffset;
            for (int i = 0; i < count; i++) {
                double pos = vertical ? snapPositionY(cursor) : snapPositionX(cursor);
                if (vertical) {
                    cells.get(i).resizeRelocate(crossStart, pos, crossExtent, sizes[i]);
                } else {
                    cells.get(i).resizeRelocate(pos, crossStart, sizes[i], crossExtent);
                }
                cursor = pos + sizes[i];
            }

            layoutScrollButtons(showButtons, vertical, primaryStart, crossStart, crossExtent,
                    backSize, fwdSize, primaryExtent);

            if (selected < 0 || selected >= count) {
                hideIndicator();
                return;
            }
            indicator.setVisible(true);
            positionSelectedIndicator(cells.get(selected), vertical, crossStart, crossExtent);
        }

        /** Nudges {@link #scrollOffset} so the selected cell sits inside the viewport. */
        private void ensureCellVisible(double[] sizes, int selected, double viewport) {
            double cellStart = 0.0;
            for (int i = 0; i < selected; i++) {
                cellStart += sizes[i];
            }
            double cellEnd = cellStart + sizes[selected];
            if (cellStart < scrollOffset) {
                scrollOffset = cellStart;
            } else if (cellEnd > scrollOffset + viewport) {
                scrollOffset = cellEnd - viewport;
            }
        }

        @Override
        protected double computePrefWidth(double height) {
            return isVertical() ? computeHeaderThickness() : computeHeaderPrimary(false);
        }

        @Override
        protected double computePrefHeight(double width) {
            return isVertical() ? computeHeaderPrimary(false) : computeHeaderThickness();
        }
    }

    // ==================== Header cell ====================

    private final class TabHeaderCell extends RXRipplePane {
        private final RXTab tab;
        private final Label label = new Label();
        private final HBox cellBox = new HBox();
        private final RXButton closeButton = new RXButton();
        private final Region closeGraphic = new Region();
        private final SkinDisposer cellDisposer = new SkinDisposer();
        private final EventHandler<MouseEvent> pressHandler;

        private List<String> appliedItemStyleClasses = new ArrayList<>();
        private Tooltip installedTooltip;

        TabHeaderCell(RXTab tab) {
            this.tab = tab;
            pressHandler = event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    onCellPressed(this);
                    event.consume();
                } else if (event.getButton() == MouseButton.MIDDLE && isCloseAllowed(tab)) {
                    requestClose(tab);
                    event.consume();
                }
            };
            getStyleClass().add(TAB_STYLE_CLASS);
            setAccessibleRole(AccessibleRole.TAB_ITEM);
            // Label carries text + graphic and stays mouse-transparent so a press on
            // it selects the tab; the close button stays pickable.
            label.setMouseTransparent(true);
            // Long labels wrap to a second line when the cell is width-constrained
            // (vertical sides, or a tabMaxWidth cap) rather than being truncated.
            label.setWrapText(true);
            label.setTextAlignment(TextAlignment.CENTER);
            closeGraphic.getStyleClass().add("graphic");
            closeGraphic.setMouseTransparent(true);
            closeGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            closeButton.getStyleClass().add("close-button");
            closeButton.setGraphic(closeGraphic);
            closeButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            // Not a keyboard focus stop (that would break the single-focus tablist);
            // close via mouse / accessibility action / Delete on the selected tab.
            closeButton.setFocusTraversable(false);
            cellBox.setAlignment(Pos.CENTER);
            cellBox.getChildren().setAll(label, closeButton);
            setContent(cellBox);

            updateContentDisplay();
            updateLabel();
            updateTooltip();
            updateDisabledState();
            updateMirroredId();
            updateMirroredStyle();
            updateStyleClass();
            updateCloseButton();

            cellDisposer.registerListener(tab.textProperty(), this::updateLabel);
            cellDisposer.registerListener(tab.graphicProperty(), this::updateLabel);
            cellDisposer.registerListener(tab.contentDisplayProperty(), this::updateContentDisplay);
            cellDisposer.registerListener(tab.disableProperty(), this::updateDisabledState);
            cellDisposer.registerListener(tab.tooltipProperty(), this::updateTooltip);
            cellDisposer.registerListener(tab.contentProperty(), () -> onTabContentChanged(tab));
            cellDisposer.registerListener(tab.closableProperty(), this::updateCloseButton);
            cellDisposer.registerListener(tab.idProperty(), this::updateMirroredId);
            cellDisposer.registerListener(tab.styleProperty(), this::updateMirroredStyle);
            cellDisposer.registerListener(tab.getStyleClass(), this::updateStyleClass);
            cellDisposer.registerEventHandler(this, MouseEvent.MOUSE_PRESSED, pressHandler);
            cellDisposer.registerEventHandler(closeButton, ActionEvent.ACTION, event -> {
                // Re-check the policy (button visibility is a UI hint, not the sole
                // gate) so it matches the middle-click and Delete paths.
                if (isCloseAllowed(tab)) {
                    requestClose(tab);
                }
                event.consume();
            });
            cellDisposer.registerDisposeTask(this::releaseItemResources);
        }

        void updateCloseButton() {
            boolean show = isCloseAllowed(tab);
            closeButton.setVisible(show);
            closeButton.setManaged(show);
        }

        private void updateLabel() {
            label.setText(tab.getText() == null ? "" : tab.getText());
            label.setGraphic(tab.getGraphic());
        }

        private void updateContentDisplay() {
            label.setContentDisplay(tab.getContentDisplay());
        }

        private void updateDisabledState() {
            setDisable(tab.isDisable());
        }

        private void updateMirroredId() {
            setId(tab.getId());
        }

        private void updateMirroredStyle() {
            setStyle(tab.getStyle() == null ? "" : tab.getStyle());
        }

        private void updateStyleClass() {
            // Preserve the structural classes (rx-ripple-pane + tab); replace only the
            // item-contributed extra classes so a colliding item class cannot strip them.
            getStyleClass().removeAll(appliedItemStyleClasses);
            appliedItemStyleClasses = new ArrayList<>();
            for (String styleClass : tab.getStyleClass()) {
                if (!TAB_STYLE_CLASS.equals(styleClass) && !RIPPLE_PANE_STYLE_CLASS.equals(styleClass)) {
                    appliedItemStyleClasses.add(styleClass);
                }
            }
            getStyleClass().addAll(appliedItemStyleClasses);
        }

        private void updateTooltip() {
            Tooltip next = tab.getTooltip();
            if (next == installedTooltip) {
                return;
            }
            if (installedTooltip != null) {
                Tooltip.uninstall(this, installedTooltip);
            }
            installedTooltip = next;
            if (installedTooltip != null) {
                Tooltip.install(this, installedTooltip);
            }
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            switch (attribute) {
                case TEXT:
                    String accessible = tab.getAccessibleText();
                    return accessible != null ? accessible : tab.getText();
                case SELECTED:
                    return tab.isSelected();
                default:
                    return super.queryAccessibleAttribute(attribute, parameters);
            }
        }

        void detach() {
            cellDisposer.dispose();
        }

        private void releaseItemResources() {
            setContent(null);
            label.setGraphic(null);
            if (installedTooltip != null) {
                Tooltip.uninstall(this, installedTooltip);
                installedTooltip = null;
            }
        }
    }
}
