package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXBreadcrumbBar.BranchMode;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXPopupMenu;
import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import io.github.leewyatt.rxcontrols.internal.BreadcrumbFitter;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default skin of {@link RXBreadcrumbBar}.
 *
 * <p>Builds one item node per path position (a link {@code Button} for ancestors, a
 * {@code Label} for a non-navigable current item), an optional branch arrow per
 * position, one shared overflow button and a pool of separators. The crumbs are
 * rebuilt only when the path or a rendering input changes; layout measures them,
 * asks {@link BreadcrumbFitter} which run to collapse, and only toggles visibility
 * and positions. One {@link RXPopupMenu} serves the overflow and every branch menu;
 * its entries are created when it opens.</p>
 *
 * @param <T> the item type
 */
public class RXBreadcrumbBarSkin<T> extends RXSkinBase<RXBreadcrumbBar<T>> {

    private static final PseudoClass CURRENT_PSEUDO_CLASS = PseudoClass.getPseudoClass("current");
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");
    // Not :focus-visible: from JavaFX 19 the platform writes that pseudo-class on
    // every node, and two writers would overwrite each other.
    private static final PseudoClass FOCUS_SHOWN_PSEUDO_CLASS = PseudoClass.getPseudoClass("focus-shown");
    private static final String CURRENT_ENTRY_STYLE_CLASS = "breadcrumb-current";

    private final RXPopupMenu menu = new RXPopupMenu();
    private final MenuTrigger overflowButton = new MenuTrigger("overflow-button", "icon");
    private final List<Crumb> crumbs = new ArrayList<>();
    private final List<StackPane> separators = new ArrayList<>();
    private final ListChangeListener<T> itemsListener = change -> onPathChanged();

    private ObservableList<T> observedItems;
    private List<T> path = Collections.emptyList();
    private BreadcrumbFitter.Fit fit = new BreadcrumbFitter.Fit(0, 0, 0.0);
    // Bumped on every path change; a request compares it across dispatch to learn
    // whether a handler already answered it.
    private long pathVersion;
    private MenuTrigger menuTrigger;
    private MenuTrigger suppressReopen;
    private FocusSlot pendingFocus;
    private boolean focusRestoreScheduled;
    // True while focus inside the bar came from a pointer press: the focus ring is held
    // back until the next non-modifier key press or until focus leaves the bar.
    private boolean pointerFocus;

    // ==================== Constructor ====================

    /**
     * Creates the skin and builds the crumbs of the control's current path.
     *
     * @param control the breadcrumb bar
     */
    public RXBreadcrumbBarSkin(RXBreadcrumbBar<T> control) {
        super(control);
        overflowButton.setAccessibleText("Show hidden path items");
        overflowButton.addEventHandler(ActionEvent.ACTION, e -> openOverflowMenu());
        trackFocusShown(overflowButton);
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, this::onMousePressedFilter);
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::onKeyPressedFilter);

        disposer.registerListener(control.itemsProperty(), (obs, oldItems, newItems) -> {
            observeItems(newItems);
            onPathChanged();
        });
        disposer.registerListener(control.converterProperty(), this::rebuild);
        disposer.registerListener(control.graphicFactoryProperty(), this::rebuild);
        disposer.registerListener(control.branchFactoryProperty(), this::rebuild);
        disposer.registerListener(control.branchModeProperty(), this::rebuild);
        disposer.registerListener(control.currentNavigableProperty(), this::rebuild);
        disposer.registerListener(control.maxVisibleItemsProperty(), control::requestLayout);
        disposer.registerListener(control.getProperties(), this::onPropertiesChanged);
        disposer.registerListener(menu.showingProperty(), this::onMenuShowingChanged);

        // A refresh posted before this skin existed is already covered by the build below.
        control.getProperties().remove(FeedbackSignals.BREADCRUMB_REFRESH);
        observeItems(control.getItems());
        rebuild();
    }

    // ==================== Path ====================

    private void observeItems(ObservableList<T> items) {
        if (observedItems != null) {
            observedItems.removeListener(itemsListener);
        }
        observedItems = items;
        if (observedItems != null) {
            observedItems.addListener(itemsListener);
        }
    }

    private void onPathChanged() {
        pathVersion++;
        rebuild();
    }

    private void onPropertiesChanged() {
        RXBreadcrumbBar<T> control = getSkinnable();
        if (control.getProperties().remove(FeedbackSignals.BREADCRUMB_REFRESH) != null) {
            rebuild();
        }
    }

    // ==================== Crumbs ====================

    private void rebuild() {
        RXBreadcrumbBar<T> control = getSkinnable();
        FocusSlot focus = captureFocus();
        if (menu.isShowing()) {
            menu.hide();
        }
        ObservableList<T> items = control.getItems();
        path = items == null ? Collections.emptyList() : new ArrayList<>(items);
        crumbs.clear();
        for (int i = 0; i < path.size(); i++) {
            crumbs.add(createCrumb(i));
        }
        while (separators.size() < path.size()) {
            separators.add(createSeparator());
        }

        List<Node> children = new ArrayList<>();
        for (int i = 0; i < crumbs.size(); i++) {
            Crumb crumb = crumbs.get(i);
            children.add(crumb.item);
            if (crumb.arrow != null) {
                children.add(crumb.arrow);
            }
            // Child order is the Tab order: the overflow button sits right after the
            // root, which is where it appears whether or not the root is hidden.
            if (i == 0) {
                children.add(overflowButton);
            }
        }
        children.addAll(separators);
        getChildren().setAll(children);
        control.requestLayout();
        if (focus != null) {
            scheduleFocusRestore(focus);
        }
    }

    private Crumb createCrumb(int index) {
        RXBreadcrumbBar<T> control = getSkinnable();
        boolean current = index == path.size() - 1;
        T item = path.get(index);
        String text = textOf(item);

        Labeled itemNode;
        if (!current || control.isCurrentNavigable()) {
            List<T> target = prefix(index + 1);
            CrumbLink link = new CrumbLink(text, graphicOf(item));
            link.addEventHandler(ActionEvent.ACTION, e -> requestNavigation(target));
            trackFocusShown(link);
            itemNode = link;
        } else {
            itemNode = new Label(text, graphicOf(item));
            // Plain text: no hover feedback, and a press falls through to the bar.
            itemNode.setMouseTransparent(true);
        }
        itemNode.getStyleClass().setAll("item");
        itemNode.setMnemonicParsing(false);
        itemNode.pseudoClassStateChanged(CURRENT_PSEUDO_CLASS, current);
        if (current) {
            itemNode.setAccessibleRoleDescription("current page");
        }

        List<T> entries = branchEntries(index);
        MenuTrigger arrow = null;
        if (!entries.isEmpty()) {
            MenuTrigger trigger = new MenuTrigger("branch-button", "arrow");
            trigger.setAccessibleText(text + " options");
            trigger.pseudoClassStateChanged(CURRENT_PSEUDO_CLASS, current);
            trigger.addEventHandler(ActionEvent.ACTION, e -> openBranchMenu(trigger, index, entries));
            trackFocusShown(trigger);
            arrow = trigger;
        }
        return new Crumb(itemNode, arrow);
    }

    private StackPane createSeparator() {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane separator = new StackPane(icon);
        separator.getStyleClass().add("separator");
        separator.setMouseTransparent(true);
        separator.setFocusTraversable(false);
        return separator;
    }

    private List<T> branchEntries(int index) {
        Callback<List<T>, List<T>> factory = getSkinnable().getBranchFactory();
        if (factory == null) {
            return Collections.emptyList();
        }
        List<T> entries = factory.call(Collections.unmodifiableList(prefix(index + 1)));
        return entries == null ? Collections.emptyList() : new ArrayList<>(entries);
    }

    private List<T> prefix(int length) {
        return new ArrayList<>(path.subList(0, length));
    }

    private String textOf(T item) {
        StringConverter<T> converter = getSkinnable().getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    private Node graphicOf(T item) {
        Callback<T, Node> factory = getSkinnable().getGraphicFactory();
        return factory == null ? null : factory.call(item);
    }

    // ==================== Navigation ====================

    private void requestNavigation(List<T> target) {
        RXBreadcrumbBar<T> control = getSkinnable();
        if (control == null) {
            return;
        }
        long versionBefore = pathVersion;
        RXBreadcrumbEvent<T> event = new RXBreadcrumbEvent<>(control, target);
        control.fireEvent(event);
        // A handler may have swapped the skin out; a disposed skin no longer tracks
        // the path, so it cannot tell whether the request was answered.
        if (getSkinnable() == null) {
            return;
        }
        if (control.isAutoNavigate() && !event.isConsumed() && pathVersion == versionBefore) {
            ObservableList<T> items = control.getItems();
            if (items != null) {
                items.setAll(target);
            }
        }
    }

    // ==================== Menus ====================

    private void openOverflowMenu() {
        List<RXMenuItem> entries = new ArrayList<>();
        for (int i = fit.hiddenFrom(); i < fit.hiddenTo() && i < path.size(); i++) {
            T item = path.get(i);
            List<T> target = prefix(i + 1);
            RXMenuItem entry = new RXMenuItem(textOf(item), graphicOf(item));
            entry.setOnAction(e -> requestNavigation(target));
            entries.add(entry);
        }
        showMenu(overflowButton, entries);
    }

    private void openBranchMenu(MenuTrigger trigger, int index, List<T> branch) {
        BranchMode mode = getSkinnable().getBranchMode();
        boolean children = mode == BranchMode.CHILDREN;
        // The prefix a chosen entry is grafted onto: SIBLINGS replaces the position,
        // CHILDREN appends below it; either way everything after is dropped.
        int keep = children ? index + 1 : index;
        int markedIndex = children ? index + 1 : index;
        T onPath = markedIndex < path.size() ? path.get(markedIndex) : null;
        boolean hasOnPath = markedIndex < path.size();

        List<RXMenuItem> entries = new ArrayList<>();
        for (T value : branch) {
            List<T> target = prefix(keep);
            target.add(value);
            RXMenuItem entry = new RXMenuItem(textOf(value), graphicOf(value));
            if (hasOnPath && Objects.equals(value, onPath)) {
                entry.getStyleClass().add(CURRENT_ENTRY_STYLE_CLASS);
            }
            entry.setOnAction(e -> requestNavigation(target));
            entries.add(entry);
        }
        showMenu(trigger, entries);
    }

    private void showMenu(MenuTrigger trigger, List<RXMenuItem> entries) {
        if (menu.isShowing()) {
            boolean sameTrigger = menuTrigger == trigger;
            menu.hide();
            if (sameTrigger) {
                return;
            }
        }
        // The press that auto-hid this trigger's menu must not reopen it on release.
        if (suppressReopen == trigger) {
            return;
        }
        menu.getItems().setAll(entries);
        menuTrigger = trigger;
        menu.show(trigger);
        if (!menu.isShowing()) {
            menuTrigger = null;
        }
    }

    private void onMenuShowingChanged() {
        if (menuTrigger == null) {
            return;
        }
        boolean showing = menu.isShowing();
        menuTrigger.pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, showing);
        if (!showing) {
            MenuTrigger closed = menuTrigger;
            menuTrigger = null;
            suppressReopen = closed;
            Platform.runLater(() -> {
                if (suppressReopen == closed) {
                    suppressReopen = null;
                }
            });
        }
    }

    // ==================== Focus ====================

    // The listener lives on a node this skin created; once the skin is disposed (the
    // node may still lose focus a pulse later) it must stay silent.
    private void trackFocusShown(Button button) {
        button.focusedProperty().addListener((obs, was, focused) -> {
            if (getSkinnable() == null) {
                return;
            }
            if (!focused && !isInside(focusOwner())) {
                pointerFocus = false;
            }
            updateFocusShown(button);
        });
    }

    private void updateFocusShown(Node node) {
        node.pseudoClassStateChanged(FOCUS_SHOWN_PSEUDO_CLASS, node.isFocused() && !pointerFocus);
    }

    private void onMousePressedFilter(MouseEvent event) {
        if (event.getTarget() instanceof Node target && crumbButtonOf(target) != null) {
            pointerFocus = true;
        }
    }

    // The bar's own button containing the node (a press may target its text), if any.
    private Button crumbButtonOf(Node node) {
        RXBreadcrumbBar<T> control = getSkinnable();
        Node current = node;
        while (current != null && current.getParent() != control) {
            current = current.getParent();
        }
        return current instanceof Button button ? button : null;
    }

    private void onKeyPressedFilter(KeyEvent event) {
        if (!pointerFocus || event.getCode().isModifierKey()) {
            return;
        }
        pointerFocus = false;
        Node owner = focusOwner();
        if (isInside(owner)) {
            updateFocusShown(owner);
        }
    }

    private boolean isInside(Node node) {
        RXBreadcrumbBar<T> control = getSkinnable();
        return node != null && control != null && node.getParent() == control;
    }

    private FocusSlot captureFocus() {
        Node owner = menu.isShowing() ? menuTrigger : focusOwner();
        return owner == null ? null : slotOf(owner);
    }

    private Node focusOwner() {
        RXBreadcrumbBar<T> control = getSkinnable();
        Scene scene = control == null ? null : control.getScene();
        return scene == null ? null : scene.getFocusOwner();
    }

    private FocusSlot slotOf(Node node) {
        if (node == overflowButton) {
            return new FocusSlot(fit.hiddenFrom(), false, true);
        }
        if (crumbs.isEmpty() && node.getParent() == getSkinnable()) {
            return foreignSlotOf(node);
        }
        for (int i = 0; i < crumbs.size(); i++) {
            Crumb crumb = crumbs.get(i);
            if (crumb.item == node) {
                return new FocusSlot(i, false, false);
            }
            if (crumb.arrow == node) {
                return new FocusSlot(i, true, false);
            }
        }
        return null;
    }

    // A node left by a previous skin of the same control (this skin is being built):
    // locate it by role and position among the control's children.
    private FocusSlot foreignSlotOf(Node node) {
        if (node.getStyleClass().contains("overflow-button")) {
            return new FocusSlot(0, false, true);
        }
        int index = -1;
        for (Node child : getSkinnable().getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("item")) {
                index++;
            }
            if (child == node) {
                return index < 0 ? null : new FocusSlot(index, node.getStyleClass().contains("branch-button"), false);
            }
        }
        return null;
    }

    // Focus is never moved inside a layout pass; the restore runs on a later turn of
    // the event loop. If it lands before the next layout hides its target, the layout
    // pass notices the hidden focus owner and schedules another restore.
    private void scheduleFocusRestore(FocusSlot slot) {
        pendingFocus = slot;
        if (focusRestoreScheduled) {
            return;
        }
        focusRestoreScheduled = true;
        Platform.runLater(() -> {
            focusRestoreScheduled = false;
            FocusSlot target = pendingFocus;
            pendingFocus = null;
            if (target != null && getSkinnable() != null) {
                Node next = fallbackFor(target);
                if (next != null) {
                    next.requestFocus();
                }
            }
        });
    }

    private Node fallbackFor(FocusSlot slot) {
        if (crumbs.isEmpty()) {
            return null;
        }
        int index = Math.min(slot.index, crumbs.size() - 1);
        List<Node> candidates = new ArrayList<>();
        if (slot.overflow || fit.isHidden(index)) {
            candidates.add(overflowButton);
        }
        Crumb crumb = crumbs.get(index);
        if (slot.arrow) {
            candidates.add(crumb.arrow);
            candidates.add(crumb.item);
        } else {
            candidates.add(crumb.item);
            candidates.add(crumb.arrow);
        }
        for (int i = index - 1; i >= 0; i--) {
            candidates.add(crumbs.get(i).arrow);
            candidates.add(crumbs.get(i).item);
        }
        candidates.add(overflowButton);
        for (Node candidate : candidates) {
            if (isFocusable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isFocusable(Node node) {
        return node instanceof Button
                && node.getParent() == getSkinnable()
                && node.isVisible()
                && !node.isDisabled();
    }

    // ==================== Layout ====================

    /** {@inheritDoc} */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        int n = crumbs.size();
        if (n == 0) {
            overflowButton.setVisible(false);
            hideSeparators(0);
            fit = new BreadcrumbFitter.Fit(0, 0, 0.0);
            return;
        }
        BreadcrumbFitter.Fit previous = fit;
        double separatorWidth = separatorWidth();
        double overflowWidth = snapSizeX(overflowButton.prefWidth(-1));
        double[] widths = measureWidths();
        fit = BreadcrumbFitter.fit(widths, currentMinWidth(), separatorWidth, overflowWidth, w,
                getSkinnable().getMaxVisibleItems());

        double cursor = x;
        int separatorIndex = 0;
        boolean first = true;
        for (int i = 0; i < n; i++) {
            Crumb crumb = crumbs.get(i);
            boolean hidden = fit.isHidden(i);
            crumb.setVisible(!hidden);
            if (i == fit.hiddenFrom() && fit.hasHidden()) {
                if (!first) {
                    cursor = place(separators.get(separatorIndex++), cursor, y, separatorWidth, h);
                }
                cursor = place(overflowButton, cursor, y, overflowWidth, h);
                first = false;
            }
            if (hidden) {
                continue;
            }
            if (!first) {
                cursor = place(separators.get(separatorIndex++), cursor, y, separatorWidth, h);
            }
            double arrowWidth = crumb.arrow == null ? 0.0 : snapSizeX(crumb.arrow.prefWidth(-1));
            double itemWidth = i == n - 1 ? fit.currentWidth() - arrowWidth : widths[i] - arrowWidth;
            cursor = place(crumb.item, cursor, y, Math.max(0.0, itemWidth), h);
            if (crumb.arrow != null) {
                cursor = place(crumb.arrow, cursor, y, arrowWidth, h);
            }
            first = false;
        }
        overflowButton.setVisible(fit.hasHidden());
        hideSeparators(separatorIndex);

        closeMenuIfStale(previous);
        Node owner = focusOwner();
        if (owner != null && owner.getParent() == getSkinnable() && !owner.isVisible()) {
            FocusSlot slot = slotOf(owner);
            if (slot != null) {
                scheduleFocusRestore(slot);
            }
        }
    }

    private double place(Node node, double x, double y, double width, double h) {
        node.setVisible(true);
        layoutInArea(node, x, y, width, h, 0.0, HPos.LEFT, VPos.CENTER);
        return x + width;
    }

    private void hideSeparators(int from) {
        for (int i = from; i < separators.size(); i++) {
            separators.get(i).setVisible(false);
        }
    }

    // A menu may not outlive the layout that produced it: its trigger got hidden, or
    // the overflow menu now lists a different run. Closing waits for the layout pass
    // to finish.
    private void closeMenuIfStale(BreadcrumbFitter.Fit previous) {
        if (!menu.isShowing() || menuTrigger == null) {
            return;
        }
        boolean stale = !menuTrigger.isVisible()
                || (menuTrigger == overflowButton
                    && (previous.hiddenFrom() != fit.hiddenFrom() || previous.hiddenTo() != fit.hiddenTo()));
        if (stale) {
            Platform.runLater(() -> {
                if (menu.isShowing()) {
                    menu.hide();
                }
            });
        }
    }

    private double[] measureWidths() {
        double[] widths = new double[crumbs.size()];
        for (int i = 0; i < widths.length; i++) {
            Crumb crumb = crumbs.get(i);
            widths[i] = snapSizeX(crumb.item.prefWidth(-1));
            if (crumb.arrow != null) {
                widths[i] += snapSizeX(crumb.arrow.prefWidth(-1));
            }
        }
        return widths;
    }

    private double currentMinWidth() {
        Crumb current = crumbs.get(crumbs.size() - 1);
        double width = snapSizeX(current.item.minWidth(-1));
        if (current.arrow != null) {
            width += snapSizeX(current.arrow.prefWidth(-1));
        }
        return width;
    }

    private double separatorWidth() {
        return separators.isEmpty() ? 0.0 : snapSizeX(separators.get(0).prefWidth(-1));
    }

    /** {@inheritDoc} */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        if (crumbs.isEmpty()) {
            return leftInset + rightInset;
        }
        return leftInset + BreadcrumbFitter.minWidth(measureWidths(), currentMinWidth(), separatorWidth(),
                snapSizeX(overflowButton.prefWidth(-1)), getSkinnable().getMaxVisibleItems()) + rightInset;
    }

    /** {@inheritDoc} */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        if (crumbs.isEmpty()) {
            return leftInset + rightInset;
        }
        double[] widths = measureWidths();
        double separatorWidth = separatorWidth();
        double overflowWidth = snapSizeX(overflowButton.prefWidth(-1));
        BreadcrumbFitter.Fit full = BreadcrumbFitter.fit(widths, currentMinWidth(), separatorWidth,
                overflowWidth, Double.MAX_VALUE, getSkinnable().getMaxVisibleItems());
        return leftInset + BreadcrumbFitter.arrangedWidth(full, widths, separatorWidth, overflowWidth) + rightInset;
    }

    /** {@inheritDoc} */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    // Every unit counts, hidden or not, so collapsing never changes the height.
    /** {@inheritDoc} */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        if (crumbs.isEmpty()) {
            return topInset + bottomInset;
        }
        double height = snapSizeY(overflowButton.prefHeight(-1));
        for (Crumb crumb : crumbs) {
            height = Math.max(height, snapSizeY(crumb.item.prefHeight(-1)));
            if (crumb.arrow != null) {
                height = Math.max(height, snapSizeY(crumb.arrow.prefHeight(-1)));
            }
        }
        for (StackPane separator : separators) {
            height = Math.max(height, snapSizeY(separator.prefHeight(-1)));
        }
        return topInset + height + bottomInset;
    }

    /** {@inheritDoc} */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // Computed from the preferred geometry rather than the children's layout
    // positions: a parent asks for the baseline before this control is laid out.
    /** {@inheritDoc} */
    @Override
    protected double computeBaselineOffset(double topInset, double rightInset,
                                           double bottomInset, double leftInset) {
        for (int i = 0; i < crumbs.size(); i++) {
            if (fit.isHidden(i)) {
                continue;
            }
            Labeled item = crumbs.get(i).item;
            double contentHeight = computePrefHeight(-1, topInset, rightInset, bottomInset, leftInset)
                    - topInset - bottomInset;
            double itemHeight = snapSizeY(item.prefHeight(-1));
            return topInset + (contentHeight - itemHeight) / 2.0 + item.getBaselineOffset();
        }
        return super.computeBaselineOffset(topInset, rightInset, bottomInset, leftInset);
    }

    // ==================== Dispose ====================

    /** {@inheritDoc} */
    @Override
    protected void disposeSkin() {
        observeItems(null);
        menu.dispose();
        menuTrigger = null;
        suppressReopen = null;
        pendingFocus = null;
        crumbs.clear();
    }

    // ==================== Nodes ====================

    private static final class Crumb {

        private final Labeled item;
        private final MenuTrigger arrow;

        private Crumb(Labeled item, MenuTrigger arrow) {
            this.item = item;
            this.arrow = arrow;
        }

        private void setVisible(boolean visible) {
            item.setVisible(visible);
            if (arrow != null) {
                arrow.setVisible(visible);
            }
        }
    }

    private record FocusSlot(int index, boolean arrow, boolean overflow) {
    }

    // An ancestor link: a Button for focus and activation, exposed to assistive
    // technology as a hyperlink.
    private static final class CrumbLink extends Button {

        private CrumbLink(String text, Node graphic) {
            super(text, graphic);
            setAccessibleRole(AccessibleRole.HYPERLINK);
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            // The mac link role queries VISITED, which only Hyperlink answers.
            if (attribute == AccessibleAttribute.VISITED) {
                return Boolean.FALSE;
            }
            return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    // A shape-only button that opens a menu: a branch arrow or the overflow button.
    private static final class MenuTrigger extends Button {

        private MenuTrigger(String styleClass, String iconStyleClass) {
            Region icon = new Region();
            icon.getStyleClass().add(iconStyleClass);
            icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            icon.setMouseTransparent(true);
            setGraphic(icon);
            getStyleClass().setAll(styleClass);
            setMnemonicParsing(false);
            setAccessibleRole(AccessibleRole.MENU_BUTTON);
            addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.KP_DOWN) {
                    fire();
                    event.consume();
                }
            });
        }
    }
}
