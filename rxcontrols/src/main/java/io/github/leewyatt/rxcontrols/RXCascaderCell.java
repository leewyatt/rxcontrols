package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.CascaderText;
import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Default list cell for a {@link RXCascaderView} column. It owns the cascader
 * interaction contract — tri-state check box, branch arrow, loading glyph,
 * disabled inheritance, the {@code active} / {@code in-active-path} /
 * {@code in-checked-path} / {@code indeterminate} / {@code loading} /
 * {@code leaf} pseudo classes, and click routing to
 * {@link RXCascaderView#activate}, {@link RXCascaderView#toggleCheck} and
 * {@link RXCascaderView#expand}.
 *
 * <p><strong>Customizing the content.</strong> Subclass this cell and override
 * {@link #createContent(RXCascaderItem)} to render a custom node in the middle
 * content area while keeping the contract above. The content is built once per
 * item (it is rebuilt when the item or its value changes, not on every state
 * change); to make content react to state, target the cell pseudo classes from
 * CSS, for example:
 *
 * <pre>{@code
 * .rx-cascader-cell:disabled .my-content { -fx-opacity: 0.4; }
 * .rx-cascader-cell:in-checked-path .my-badge { visibility: visible; }
 * }</pre>
 *
 * Install a subclass through
 * {@link RXCascaderView#cellFactoryProperty() cellFactory}. For full control of
 * the row structure, return your own {@link ListCell} from the factory and route
 * interaction to the view yourself, or copy this class — it depends only on the
 * public {@link RXCascaderView} / {@link RXCascaderItem} API.
 *
 * @param <T> application value type
 */
public class RXCascaderCell<T> extends ListCell<RXCascaderItem<T>> {

    // ==================== Shared loading animation ====================

    private static final long LOADING_SPINNER_CYCLE_NANOS = 900_000_000L;

    /** Single rotation source every visible loading glyph binds to. */
    private static final DoubleProperty LOADING_ANGLE = new SimpleDoubleProperty();

    /** Cells currently showing a loading glyph on screen; weak so GC/column rebuilds self-heal. */
    private static final Set<RXCascaderCell<?>> ACTIVE_SPINNERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static AnimationTimer spinnerTimer;
    private static boolean spinnerRunning;

    private static void joinSpinners(RXCascaderCell<?> cell) {
        ACTIVE_SPINNERS.add(cell);
        startSpinner();
    }

    private static void leaveSpinners(RXCascaderCell<?> cell) {
        ACTIVE_SPINNERS.remove(cell);
    }

    private static void startSpinner() {
        if (spinnerRunning) {
            return;
        }
        if (spinnerTimer == null) {
            spinnerTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (ACTIVE_SPINNERS.isEmpty()) {
                        stopSpinner();
                        return;
                    }
                    LOADING_ANGLE.set((now % LOADING_SPINNER_CYCLE_NANOS) * 360.0 / LOADING_SPINNER_CYCLE_NANOS);
                }
            };
        }
        spinnerRunning = true;
        spinnerTimer.start();
    }

    private static void stopSpinner() {
        if (!spinnerRunning) {
            return;
        }
        spinnerRunning = false;
        spinnerTimer.stop();
        LOADING_ANGLE.set(0.0);
    }

    // ==================== Pseudo classes ====================

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass IN_ACTIVE_PATH = PseudoClass.getPseudoClass("in-active-path");
    private static final PseudoClass IN_CHECKED_PATH = PseudoClass.getPseudoClass("in-checked-path");
    private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
    private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");
    private static final PseudoClass LEAF = PseudoClass.getPseudoClass("leaf");

    // ==================== Nodes ====================

    private final RXCascaderView<T> view;
    private final HBox container = new HBox();
    private final CheckBox checkBox = new CheckBox();
    private final Region selectedCheck = new Region();
    private final StackPane content = new StackPane();
    private final Label textLabel = new Label();
    private final Region arrow = new Region();
    private final Region loadingGlyph = new Region();

    /** True only when this cell is in a visible chain on a showing window. */
    private final ReadOnlyBooleanProperty treeShowing = TreeShowingProperty.of(this);

    // ==================== Listeners ====================

    private final InvalidationListener stateListener = observable -> updateState();
    private final InvalidationListener contentListener = observable -> updateContent();
    private final ListChangeListener<RXCascaderItem<T>> childrenListener = change -> updateState();
    private final WeakInvalidationListener weakStateListener = new WeakInvalidationListener(stateListener);
    private final WeakInvalidationListener weakContentListener = new WeakInvalidationListener(contentListener);
    private final WeakListChangeListener<RXCascaderItem<T>> weakChildrenListener =
            new WeakListChangeListener<>(childrenListener);

    private RXCascaderItem<T> observedItem;

    // ==================== Constructor ====================

    /**
     * Creates a cell bound to the given view.
     *
     * @param view owning cascader view
     */
    public RXCascaderCell(RXCascaderView<T> view) {
        this.view = view;
        initializeNodes();
        registerHandlers();
        loadingGlyph.rotateProperty().bind(LOADING_ANGLE);
        treeShowing.addListener(observable -> updateSpinnerMembership());
    }

    private void initializeNodes() {
        getStyleClass().add("rx-cascader-cell");
        container.getStyleClass().add("container");
        checkBox.setAllowIndeterminate(false);
        checkBox.setFocusTraversable(false);
        // Single-selection check marker: occupies a fixed left slot so all rows
        // align, shown only on the selected leaf. Mutually exclusive with the
        // multiple-mode check box on the same side.
        selectedCheck.getStyleClass().add("selected-check");
        selectedCheck.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        selectedCheck.setMouseTransparent(true);
        content.getStyleClass().add("content");
        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        arrow.setMouseTransparent(true);
        loadingGlyph.getStyleClass().add("loading");
        loadingGlyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        loadingGlyph.setMouseTransparent(true);
        HBox.setHgrow(content, Priority.ALWAYS);
        content.setMaxWidth(Double.MAX_VALUE);
        container.getChildren().setAll(checkBox, selectedCheck, content, loadingGlyph, arrow);
    }

    private void registerHandlers() {
        checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            RXCascaderItem<T> item = getItem();
            if (item != null && !view.isEffectivelyDisabled(item)) {
                view.toggleCheck(item);
                // Also focus the operated item: expand a branch one level so the
                // displayed column path follows the checkbox we just toggled,
                // instead of staying on an unrelated expanded branch.
                if (!view.isLeaf(item)) {
                    view.expand(item);
                }
            }
            event.consume();
        });
        checkBox.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && getItem() != null) {
                view.activate(getItem());
                event.consume();
            }
        });
    }

    // ==================== Customization ====================

    /**
     * Returns the node rendered in the middle content area for the given item.
     * Called once per item (and when the item value changes), not on every state
     * change. The default returns a reused {@link Label} set to
     * {@link #getDisplayText(Object) getDisplayText(item.getValue())}. Returning
     * {@code null} renders an empty content area.
     *
     * @param item item to render content for
     * @return content node, or {@code null} for no content
     */
    protected Node createContent(RXCascaderItem<T> item) {
        textLabel.setText(getDisplayText(item.getValue()));
        return textLabel;
    }

    /**
     * Resolves the display text for a value using the view's
     * {@link RXCascaderView#getItemTextFactory() itemTextFactory}, falling back to
     * {@code String.valueOf(value)} when none is set. A {@code null} value, or a
     * factory that returns {@code null}, yields the empty string. For use by
     * subclasses overriding {@link #createContent(RXCascaderItem)}.
     *
     * @param value value to render
     * @return display text, never {@code null}
     */
    protected final String getDisplayText(T value) {
        return CascaderText.resolve(view.getItemTextFactory(), value);
    }

    /**
     * Returns the owning view, for use by subclasses overriding
     * {@link #createContent(RXCascaderItem)}.
     *
     * @return owning cascader view
     */
    protected final RXCascaderView<T> getView() {
        return view;
    }

    // ==================== Cell lifecycle ====================

    @Override
    protected final void updateItem(RXCascaderItem<T> item, boolean empty) {
        detachObservedItem();
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setDisable(false);
            checkBox.setVisible(false);
            checkBox.setManaged(false);
            checkBox.setSelected(false);
            checkBox.setIndeterminate(false);
            checkBox.setDisable(false);
            selectedCheck.setVisible(false);
            selectedCheck.setManaged(false);
            arrow.setVisible(false);
            arrow.setManaged(false);
            loadingGlyph.setVisible(false);
            loadingGlyph.setManaged(false);
            textLabel.setText(null);
            content.getChildren().clear();
            resetPseudoClasses();
            updateSpinnerMembership();
            return;
        }

        observedItem = item;
        attachObservedItem(item);
        setText(null);
        setGraphic(container);
        updateContent();
        updateState();
    }

    private void attachObservedItem(RXCascaderItem<T> item) {
        item.checkedProperty().addListener(weakStateListener);
        item.indeterminateProperty().addListener(weakStateListener);
        item.disabledProperty().addListener(weakStateListener);
        item.loadingProperty().addListener(weakStateListener);
        item.leafHintProperty().addListener(weakStateListener);
        item.valueProperty().addListener(weakContentListener);
        item.getChildren().addListener(weakChildrenListener);
    }

    private void detachObservedItem() {
        if (observedItem == null) {
            return;
        }
        observedItem.checkedProperty().removeListener(weakStateListener);
        observedItem.indeterminateProperty().removeListener(weakStateListener);
        observedItem.disabledProperty().removeListener(weakStateListener);
        observedItem.loadingProperty().removeListener(weakStateListener);
        observedItem.leafHintProperty().removeListener(weakStateListener);
        observedItem.valueProperty().removeListener(weakContentListener);
        observedItem.getChildren().removeListener(weakChildrenListener);
        observedItem = null;
    }

    private void updateContent() {
        RXCascaderItem<T> item = getItem();
        if (item == null) {
            content.getChildren().clear();
            return;
        }
        Node node = createContent(item);
        if (node == null) {
            content.getChildren().clear();
        } else {
            content.getChildren().setAll(node);
        }
    }

    private void updateState() {
        RXCascaderItem<T> item = getItem();
        if (item == null) {
            return;
        }
        boolean multiple = view.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE;
        boolean disabled = view.isEffectivelyDisabled(item);
        boolean leaf = view.isLeaf(item);
        boolean loading = item.isLoading();
        RXCascaderPath<T> selectedPath = view.getSelectedPath();
        boolean active = selectedPath != null && selectedPath.getLeaf() == item;
        boolean inActivePath = view.getActivePath().contains(item);
        boolean inCheckedPath = isInCheckedPath(item);

        checkBox.setVisible(multiple);
        checkBox.setManaged(multiple);
        checkBox.setDisable(disabled);
        checkBox.setSelected(item.isChecked());
        checkBox.setIndeterminate(item.isIndeterminate());
        // Single mode keeps the left slot reserved on every row (managed) and
        // reveals the mark only on the selected leaf, so rows stay aligned.
        boolean singleCheckSlot = !multiple;
        selectedCheck.setManaged(singleCheckSlot);
        selectedCheck.setVisible(singleCheckSlot && active);
        setDisable(disabled);
        boolean showArrow = !loading && !leaf;
        arrow.setVisible(showArrow);
        arrow.setManaged(showArrow);
        loadingGlyph.setVisible(loading);
        loadingGlyph.setManaged(loading);

        pseudoClassStateChanged(ACTIVE, active);
        pseudoClassStateChanged(IN_ACTIVE_PATH, inActivePath);
        pseudoClassStateChanged(IN_CHECKED_PATH, inCheckedPath);
        pseudoClassStateChanged(INDETERMINATE, item.isIndeterminate());
        pseudoClassStateChanged(LOADING, loading);
        pseudoClassStateChanged(LEAF, leaf);
        updateSpinnerMembership();
    }

    private boolean isInCheckedPath(RXCascaderItem<T> item) {
        for (RXCascaderPath<T> path : view.getCheckedPaths()) {
            if (path.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private void resetPseudoClasses() {
        pseudoClassStateChanged(ACTIVE, false);
        pseudoClassStateChanged(IN_ACTIVE_PATH, false);
        pseudoClassStateChanged(IN_CHECKED_PATH, false);
        pseudoClassStateChanged(INDETERMINATE, false);
        pseudoClassStateChanged(LOADING, false);
        pseudoClassStateChanged(LEAF, false);
    }

    private void updateSpinnerMembership() {
        // tree-showing (not just scene): the popup attaches its scene to the view
        // at skin construction, so a scene check would keep the timer running while
        // the popup is hidden. tree-showing reacts to the window hiding too.
        if (loadingGlyph.isVisible() && treeShowing.get()) {
            joinSpinners(this);
        } else {
            leaveSpinners(this);
        }
    }
}
