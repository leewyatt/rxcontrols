package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMenuHeader;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXMenuList;
import io.github.leewyatt.rxcontrols.RXMenuSeparator;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Skin for {@link RXMenuList}: renders each item into a focusable cell inside a
 * non-virtualized {@code VBox}, drives roving keyboard focus (Down/Up wrap and
 * skip non-focusable items, Home/End, type-ahead), activates on Enter/Space,
 * wires the pointer-press and keyboard-center ripple, and maps command-menu
 * accessibility roles. Escape/Tab dismissal belongs to the hosting popup and is
 * not handled here.
 */
public class RXMenuListSkin extends RXSkinBase<RXMenuList> {

    // A first-letter type-ahead burst resets after this idle gap.
    private static final long TYPE_AHEAD_RESET_MS = 1000L;

    private static final PseudoClass CHECKED_PSEUDO_CLASS = PseudoClass.getPseudoClass("checked");
    private static final PseudoClass DANGER_PSEUDO_CLASS = PseudoClass.getPseudoClass("danger");
    // The platform :disabled pseudo-class, toggled manually only in the
    // disabledItemsFocusable (APG) mode where the cell is not JavaFX-disabled.
    private static final PseudoClass DISABLED_PSEUDO_CLASS = PseudoClass.getPseudoClass("disabled");

    private final VBox container = new VBox();
    private final ScrollPane scroller = new ScrollPane(container);
    private final IdentityHashMap<RXMenuItem, Region> cellByItem = new IdentityHashMap<>();
    private final List<Runnable> cellTeardowns = new ArrayList<>();
    private final StringBuilder typeAheadBuffer = new StringBuilder();
    private long lastTypeAheadTime;

    /**
     * Creates the skin.
     *
     * @param control the menu list this skin is attached to
     */
    public RXMenuListSkin(RXMenuList control) {
        super(control);
        container.getStyleClass().add("container");
        scroller.getStyleClass().add("scroll");
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setFocusTraversable(false);
        getChildren().add(scroller);

        rebuildCells();
        disposer.registerListener(control.getItems(), this::rebuildCells);
        // The disabled-focusable mode changes how each cell wires its disabled
        // state (bound vs. pseudo-class only), so cells are rebuilt when it flips.
        disposer.registerListener(control.disabledItemsFocusableProperty(), this::rebuildCells);
        disposer.registerListener(control.converterProperty(), this::refreshTexts);
        // A filter (not a bubbling handler) so navigation keys are consumed before
        // the ScrollPane's own arrow-key scrolling can act on them.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Sizing ====================
    // Measure the inner VBox (not the ScrollPane, whose default sizing reserves
    // scrollbar space) so inline lists size exactly to content and the popup
    // geometry sees the true natural height before it decides to cap + scroll.

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + container.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + container.prefHeight(width - leftInset - rightInset) + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Allow the popup geometry to cap below pref height; the ScrollPane scrolls.
        return topInset + bottomInset;
    }

    // ==================== Cell building ====================

    private void rebuildCells() {
        teardownCells();
        container.getChildren().clear();
        cellByItem.clear();
        for (RXMenuItem item : getSkinnable().getItems()) {
            Region cell = buildCell(item);
            cellByItem.put(item, cell);
            container.getChildren().add(cell);
        }
    }

    private void teardownCells() {
        for (Runnable teardown : cellTeardowns) {
            teardown.run();
        }
        cellTeardowns.clear();
    }

    private Region buildCell(RXMenuItem item) {
        if (item instanceof RXMenuSeparator) {
            Region separator = new Region();
            separator.getStyleClass().add("menu-separator");
            separator.setMouseTransparent(true);
            separator.setFocusTraversable(false);
            return separator;
        }
        if (item instanceof RXMenuHeader) {
            Label header = new Label();
            header.getStyleClass().add("menu-header");
            header.setMouseTransparent(true);
            header.setFocusTraversable(false);
            header.textProperty().bind(item.textProperty());
            cellTeardowns.add(header.textProperty()::unbind);
            return header;
        }
        CommandCell cell = new CommandCell(item);
        cellTeardowns.add(cell::dispose);
        return cell;
    }

    private void refreshTexts() {
        for (Node node : container.getChildren()) {
            if (node instanceof CommandCell cell) {
                cell.refreshLabel();
            }
        }
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN, KP_DOWN -> {
                moveFocus(1);
                event.consume();
            }
            case UP, KP_UP -> {
                moveFocus(-1);
                event.consume();
            }
            case HOME -> {
                focusEdge(true);
                event.consume();
            }
            case END -> {
                focusEdge(false);
                event.consume();
            }
            case ENTER, SPACE -> {
                activateFocused();
                event.consume();
            }
            default -> {
                if (isTypeAheadKey(event)) {
                    typeAhead(event.getText());
                    event.consume();
                }
            }
        }
    }

    private void moveFocus(int direction) {
        RXMenuList control = getSkinnable();
        List<RXMenuItem> items = control.getItems();
        int n = items.size();
        if (n == 0) {
            return;
        }
        int from = currentFocusIndex();
        boolean wrap = control.isWrapAround();
        int start = from < 0 ? (direction > 0 ? -1 : n) : from;
        for (int step = 1; step <= n; step++) {
            int i = wrap ? Math.floorMod(start + direction * step, n) : start + direction * step;
            if (i < 0 || i >= n) {
                return;
            }
            if (isNavigable(items.get(i))) {
                focusIndex(i);
                return;
            }
        }
    }

    private void focusEdge(boolean first) {
        List<RXMenuItem> items = getSkinnable().getItems();
        int n = items.size();
        for (int step = 0; step < n; step++) {
            int i = first ? step : n - 1 - step;
            if (isNavigable(items.get(i))) {
                focusIndex(i);
                return;
            }
        }
    }

    private void activateFocused() {
        int index = currentFocusIndex();
        if (index < 0) {
            return;
        }
        RXMenuItem item = getSkinnable().getItems().get(index);
        Region cell = cellByItem.get(item);
        if (cell instanceof CommandCell command && !item.isDisable()) {
            command.centerPressRipple();
            getSkinnable().activate(item);
        }
    }

    /**
     * Focuses the first (or currently selected) focusable item, per the menu
     * list's {@code initialFocus} mode. Called by the hosting popup when the
     * menu opens; usable directly on an inline list too.
     */
    public void focusInitial() {
        if (getSkinnable().getInitialFocus() == RXMenuList.InitialFocus.SELECTED) {
            int selected = firstSelectedIndex();
            if (selected >= 0) {
                focusIndex(selected);
                return;
            }
        }
        // FIRST, or SELECTED with nothing selected (documented fallback).
        focusEdge(true);
    }

    private int firstSelectedIndex() {
        List<RXMenuItem> items = getSkinnable().getItems();
        for (int i = 0; i < items.size(); i++) {
            RXMenuItem item = items.get(i);
            if (isNavigable(item) && item.isSelectable() && item.isSelected()) {
                return i;
            }
        }
        return -1;
    }

    private void focusIndex(int index) {
        RXMenuItem item = getSkinnable().getItems().get(index);
        Region cell = cellByItem.get(item);
        if (cell != null) {
            cell.requestFocus();
            scrollCellIntoView(cell);
        }
    }

    // Roving focus does not move the ScrollPane on its own, so a capped (scrolling)
    // menu must scroll the focused cell into view — the NEAREST minimal scroll: only
    // when the cell sits above or below the viewport, never otherwise.
    private void scrollCellIntoView(Region cell) {
        double viewportHeight = scroller.getViewportBounds().getHeight();
        double contentHeight = container.getHeight();
        double scrollable = contentHeight - viewportHeight;
        if (viewportHeight <= 0.0 || scrollable <= 0.0) {
            return;
        }
        double top = cell.getBoundsInParent().getMinY();
        double bottom = cell.getBoundsInParent().getMaxY();
        double offset = scroller.getVvalue() * scrollable;
        if (top < offset) {
            scroller.setVvalue(top / scrollable);
        } else if (bottom > offset + viewportHeight) {
            scroller.setVvalue((bottom - viewportHeight) / scrollable);
        }
    }

    private int currentFocusIndex() {
        if (getSkinnable().getScene() == null) {
            return -1;
        }
        Node focusOwner = getSkinnable().getScene().getFocusOwner();
        if (focusOwner == null) {
            return -1;
        }
        List<RXMenuItem> items = getSkinnable().getItems();
        for (int i = 0; i < items.size(); i++) {
            if (cellByItem.get(items.get(i)) == focusOwner) {
                return i;
            }
        }
        return -1;
    }

    private boolean isNavigable(RXMenuItem item) {
        if (item instanceof RXMenuSeparator || item instanceof RXMenuHeader) {
            return false;
        }
        // Default: only enabled items are navigable. In the APG mode a disabled
        // item is focusable (but still not activatable — activate() guards it).
        return item.isFocusable() || (item.isDisable() && getSkinnable().isDisabledItemsFocusable());
    }

    // ==================== Type-ahead ====================

    private boolean isTypeAheadKey(KeyEvent event) {
        if (event.isShortcutDown() || event.isAltDown()) {
            return false;
        }
        String text = event.getText();
        return text != null && text.length() == 1 && !Character.isISOControl(text.charAt(0));
    }

    private void typeAhead(String ch) {
        long now = System.currentTimeMillis();
        if (now - lastTypeAheadTime > TYPE_AHEAD_RESET_MS) {
            typeAheadBuffer.setLength(0);
        }
        lastTypeAheadTime = now;
        String lower = ch.toLowerCase(Locale.ROOT);
        if (typeAheadBuffer.length() > 0 && isAllSameChar(typeAheadBuffer, lower.charAt(0))) {
            // Repeating the same letter cycles among its matches (start = focus+1)
            // instead of growing the prefix, which would stop matching.
            typeAheadBuffer.setLength(0);
        }
        typeAheadBuffer.append(lower);
        int match = findTypeAheadMatch(typeAheadBuffer.toString());
        if (match >= 0) {
            focusIndex(match);
        }
    }

    private static boolean isAllSameChar(CharSequence buffer, char ch) {
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) != ch) {
                return false;
            }
        }
        return true;
    }

    private int findTypeAheadMatch(String prefixLower) {
        List<RXMenuItem> items = getSkinnable().getItems();
        int n = items.size();
        if (n == 0) {
            return -1;
        }
        int focus = currentFocusIndex();
        int start = prefixLower.length() <= 1 ? focus + 1 : Math.max(0, focus);
        for (int i = 0; i < n; i++) {
            int index = Math.floorMod(start + i, n);
            RXMenuItem item = items.get(index);
            if (!isNavigable(item)) {
                continue;
            }
            if (itemText(item).toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                return index;
            }
        }
        return -1;
    }

    private String itemText(RXMenuItem item) {
        StringConverter<RXMenuItem> converter = getSkinnable().getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item.getText() == null ? "" : item.getText();
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        teardownCells();
        cellByItem.clear();
        container.getChildren().clear();
    }

    // ==================== Command cell ====================

    private final class CommandCell extends HBox {

        private final RXMenuItem item;
        private final Label label = new Label();
        private final Label trailing = new Label();
        private final RippleDecoration ripple;
        private final SkinDisposer itemDisposer = new SkinDisposer();
        private Node graphicNode;
        private Region indicatorMark;

        private CommandCell(RXMenuItem item) {
            this.item = item;
            getStyleClass().add("rx-menu-item");
            getStyleClass().addAll(item.getStyleClass());
            setFocusTraversable(false);
            setAccessibleRole(accessibleRoleFor(item));

            // label keeps Label's built-in ".label"; trailing needs a local name
            // to be distinguished (it is also a Label) for the accelerator slot.
            HBox.setHgrow(label, Priority.ALWAYS);
            label.setMaxWidth(Double.MAX_VALUE);
            trailing.getStyleClass().add("trailing");

            RXMenuList control = getSkinnable();
            // The hover state overlay must not tint a disabled item (disabled = no
            // interaction feedback, matching the press / activate paths). In the
            // default mode the cell is JavaFX-disabled and RippleDecoration already
            // gates the overlay on that; in APG mode the cell stays enabled for
            // roving focus, so gate on the item's own disabled flag here too.
            BooleanBinding overlayEnabled = Bindings.createBooleanBinding(
                    () -> control.isStateOverlayEnabled() && !item.isDisable(),
                    control.stateOverlayEnabledProperty(), item.disableProperty());
            itemDisposer.registerDisposeTask(overlayEnabled::dispose);
            ripple = new RippleDecoration(this, control.rippleEnabledProperty(),
                    overlayEnabled, control.rippleFillProperty(),
                    control::getRippleOpacity, null, null);
            getChildren().add(ripple.getLayer());
            // A selectable item reserves a leading indicator slot (checkmark for a
            // checkbox, dot for a radio) shown by CSS under :checked.
            if (item.isSelectable()) {
                getChildren().add(buildLeadingIndicator(item));
            }
            getChildren().addAll(label, trailing);
            setGraphicNode(item.getGraphic());

            refreshLabel();
            refreshAccelerator();

            // Default: bind the cell's disabled to the item's (JavaFX-disabled, so it
            // is skipped in navigation). APG mode keeps the cell enabled — so it can
            // receive roving focus — and reflects :disabled via a pseudo-class only.
            if (control.isDisabledItemsFocusable()) {
                itemDisposer.registerListener(item.disableProperty(), this::refreshDisabledPseudoClass);
                // Re-apply after Node recomputes the cell's own disabled (e.g. an
                // ancestor is disabled then re-enabled), which would otherwise clear
                // the manually applied pseudo-class.
                itemDisposer.registerListener(disabledProperty(), this::refreshDisabledPseudoClass);
                refreshDisabledPseudoClass();
            } else {
                itemDisposer.registerBinding(disableProperty(), item.disableProperty());
            }

            // :checked follows the checked state (selectable items only); :danger
            // follows the destructive flag.
            if (item.isSelectable()) {
                itemDisposer.registerListener(item.selectedProperty(), this::refreshChecked);
                refreshChecked();
                // Keep the role and indicator (checkbox vs radio) in step if a toggle
                // group is assigned or cleared after the cell was built.
                itemDisposer.registerListener(item.toggleGroupProperty(), this::refreshSelectableKind);
            }
            itemDisposer.registerListener(item.dangerProperty(), this::refreshDanger);
            refreshDanger();

            itemDisposer.registerListener(item.textProperty(), this::refreshLabel);
            itemDisposer.registerListener(item.acceleratorProperty(), this::refreshAccelerator);
            itemDisposer.registerListener(item.graphicProperty(), () -> setGraphicNode(item.getGraphic()));

            itemDisposer.registerEventHandler(this, MouseEvent.MOUSE_ENTERED, e -> {
                if (isNavigable(item)) {
                    requestFocus();
                }
            });
            itemDisposer.registerEventHandler(this, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
            itemDisposer.registerEventHandler(this, MouseEvent.MOUSE_RELEASED, e -> ripple.release());
            itemDisposer.registerEventHandler(this, MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() == MouseButton.PRIMARY && !item.isDisable()) {
                    control.activate(item);
                }
            });
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            // The ripple layer is unmanaged, so HBox never sizes it; size and clip
            // it to the cell each layout pass (mirrors RippleCellSkinBase).
            ripple.layout(getWidth(), getHeight());
        }

        private void onMousePressed(MouseEvent event) {
            if (event.getButton() != MouseButton.PRIMARY
                    || !getSkinnable().isRippleEnabled()
                    || item.isDisable()) {
                return;
            }
            Point2D local = sceneToLocal(event.getSceneX(), event.getSceneY());
            ripple.press(local.getX(), local.getY(), false);
        }

        private void centerPressRipple() {
            if (getSkinnable().isRippleEnabled() && !item.isDisable()) {
                ripple.press(0, 0, true);
                ripple.release();
            }
        }

        private void refreshLabel() {
            String text = itemText(item);
            label.setText(text);
            setAccessibleText(effectiveAccessibleText());
        }

        private void refreshAccelerator() {
            boolean present = item.getAccelerator() != null;
            trailing.setText(present ? item.getAccelerator().getDisplayText() : "");
            trailing.setVisible(present);
            trailing.setManaged(present);
        }

        private void setGraphicNode(Node newGraphic) {
            if (graphicNode != null) {
                getChildren().remove(graphicNode);
                graphicNode = null;
            }
            if (newGraphic != null) {
                graphicNode = newGraphic;
                // Insert immediately before the label (after the ripple layer and any
                // leading selection indicator).
                getChildren().add(getChildren().indexOf(label), graphicNode);
            }
            setAccessibleText(effectiveAccessibleText());
        }

        private void refreshChecked() {
            pseudoClassStateChanged(CHECKED_PSEUDO_CLASS, item.isSelected());
        }

        private void refreshSelectableKind() {
            setAccessibleRole(accessibleRoleFor(item));
            if (indicatorMark != null) {
                indicatorMark.getStyleClass().setAll(indicatorStyleClass(item));
            }
        }

        private void refreshDanger() {
            pseudoClassStateChanged(DANGER_PSEUDO_CLASS, item.isDanger());
        }

        private void refreshDisabledPseudoClass() {
            // Disabled-looking when the item is disabled or the cell is effectively
            // disabled by an ancestor (APG mode: the cell itself is not bound disabled).
            pseudoClassStateChanged(DISABLED_PSEUDO_CLASS, item.isDisable() || isDisabled());
        }

        private static AccessibleRole accessibleRoleFor(RXMenuItem item) {
            if (!item.isSelectable()) {
                return AccessibleRole.MENU_ITEM;
            }
            return item.getToggleGroup() != null
                    ? AccessibleRole.RADIO_MENU_ITEM
                    : AccessibleRole.CHECK_MENU_ITEM;
        }

        private Region buildLeadingIndicator(RXMenuItem item) {
            StackPane leading = new StackPane();
            leading.getStyleClass().add("leading");
            leading.setMouseTransparent(true);
            leading.setFocusTraversable(false);
            indicatorMark = new Region();
            indicatorMark.getStyleClass().add(indicatorStyleClass(item));
            indicatorMark.setMouseTransparent(true);
            // Clamp to the CSS pref size so the StackPane cannot stretch the shape
            // into an oversized blob (the icon anti-stretch convention).
            indicatorMark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            leading.getChildren().add(indicatorMark);
            return leading;
        }

        private static String indicatorStyleClass(RXMenuItem item) {
            return item.getToggleGroup() != null ? "radiomark" : "checkmark";
        }

        private String effectiveAccessibleText() {
            String text = itemText(item);
            if (!text.isEmpty()) {
                return text;
            }
            return graphicNode == null ? "" : graphicNode.getAccessibleText();
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            return switch (attribute) {
                case ACCELERATOR -> item.getAccelerator();
                case DISABLED -> item.isDisable();
                case SELECTED -> item.isSelected();
                case TEXT -> effectiveAccessibleText();
                default -> super.queryAccessibleAttribute(attribute, parameters);
            };
        }

        @Override
        public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
            if (action == AccessibleAction.FIRE) {
                getSkinnable().activate(item);
            } else {
                super.executeAccessibleAction(action, parameters);
            }
        }

        private void dispose() {
            itemDisposer.dispose();
            ripple.dispose();
            getChildren().remove(ripple.getLayer());
            if (graphicNode != null) {
                getChildren().remove(graphicNode);
                graphicNode = null;
            }
        }
    }
}
