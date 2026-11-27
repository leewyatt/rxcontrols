package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.CascaderText;
import io.github.leewyatt.rxcontrols.skins.RXCascaderCellSkin;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default list cell for a {@link RXCascaderView} column. It owns the cascader
 * interaction contract — tri-state check box, branch arrow, loading glyph,
 * disabled inheritance, the {@code active} / {@code in-active-path} /
 * {@code in-checked-path} / {@code indeterminate} / {@code loading} /
 * {@code load-failed} / {@code leaf} pseudo classes, and click routing to
 * {@link RXCascaderView#activate}, {@link RXCascaderView#toggleCheck} and
 * {@link RXCascaderView#expand}.
 *
 * <p><strong>Customizing the content.</strong> Subclass this cell and override
 * {@link #createContent(RXCascaderItem)} to render a custom node in the middle
 * content area while keeping the contract above. The content is rebuilt whenever
 * the cell is bound to an item (every non-empty re-bind, including recycling),
 * when the item's value changes, and when the view's converter changes —
 * but never on pure state changes (selection, active path, loading, checked), so
 * cache sub-nodes as fields and make content react to state by targeting the cell
 * pseudo classes from CSS, for example:
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
 * <p><strong>Why a content slot instead of the standard {@code updateItem} +
 * {@code setText} / {@code setGraphic} idiom</strong> (compare {@link RXListCell},
 * which follows it): this cell is multi-slot interaction chrome — check box,
 * selection mark, content slot, loading glyph and branch arrow — which the
 * two-slot {@code Labeled} content model cannot express, and its content is
 * deliberately kept off the state-change path to avoid rebuild flicker. That is
 * why {@link #updateItem(RXCascaderItem, boolean)} is {@code final} here and the
 * supported customization surface is the content slot; cells without such chrome
 * (plain rows and cards) follow the standard idiom instead.
 *
 * <p>A press on the row plays a ripple, styled with {@code -rx-ripple-fill},
 * {@code -rx-ripple-opacity}, {@code -rx-ripple-enabled} and
 * {@code -rx-ripple-state-overlay-enabled}; the default skin is
 * {@link RXCascaderCellSkin}.
 *
 * @param <T> application value type
 */
public class RXCascaderCell<T> extends ListCell<RXCascaderItem<T>> {

    // ==================== Loading spinner ====================

    private static final Duration LOADING_SPINNER_CYCLE = Duration.seconds(0.9);

    /** Per-cell rotation animation for the loading glyph; created lazily on first use. */
    private RotateTransition spinner;

    private RotateTransition spinner() {
        if (spinner == null) {
            spinner = new RotateTransition(LOADING_SPINNER_CYCLE, loadingGlyph);
            spinner.setByAngle(360.0);
            spinner.setInterpolator(Interpolator.LINEAR);
            spinner.setCycleCount(Animation.INDEFINITE);
        }
        return spinner;
    }

    /** Single source of truth: spin iff the glyph is visible and on a showing window. */
    private void updateSpinner() {
        if (loadingGlyph.isVisible() && treeShowing.get()) {
            if (spinner().getStatus() != Animation.Status.RUNNING) {
                // play() resumes a paused spinner from its current angle (no snap)
                // and starts a fresh one from zero.
                spinner().play();
            }
        } else if (spinner != null) {
            // Pause keeps the current angle (cheap to resume) and detaches from the
            // pulse timer, so a recycled-off-screen cell never keeps spinning.
            spinner.pause();
        }
    }

    private void disposeSpinner() {
        if (spinner != null) {
            spinner.stop();
            loadingGlyph.setRotate(0.0);
            spinner = null;
        }
    }

    // ==================== Pseudo classes ====================

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass IN_ACTIVE_PATH = PseudoClass.getPseudoClass("in-active-path");
    private static final PseudoClass IN_CHECKED_PATH = PseudoClass.getPseudoClass("in-checked-path");
    private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
    private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");
    private static final PseudoClass LOAD_FAILED = PseudoClass.getPseudoClass("load-failed");
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
    private final ReadOnlyBooleanProperty treeShowing = RXTreeShowingProperty.of(this);

    // ==================== Listeners ====================

    private final InvalidationListener stateListener = observable -> updateState();
    private final InvalidationListener contentListener = observable -> updateContent();
    private final ListChangeListener<RXCascaderItem<T>> childrenListener = change -> updateState();
    private final WeakInvalidationListener weakStateListener = new WeakInvalidationListener(stateListener);
    private final WeakInvalidationListener weakContentListener = new WeakInvalidationListener(contentListener);
    private final WeakListChangeListener<RXCascaderItem<T>> weakChildrenListener =
            new WeakListChangeListener<>(childrenListener);

    private RXCascaderItem<T> observedItem;
    private final List<RXCascaderItem<T>> observedAncestors = new ArrayList<>();

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
        treeShowing.addListener(observable -> updateSpinner());
        // View-level display state (selection, navigation, checked paths, mode,
        // loader-driven leaf semantics) is not carried by the item, so observe
        // the view directly: a change flips only this cell's pseudo classes and
        // slot visibility. Without this the skin would have to rebuild every
        // cell through ListView.refresh() on each selection change, which
        // re-runs updateItem across all columns and rebuilds custom content
        // (visible flicker). Weak listeners: the virtual flow discards cells
        // without any dispose hook.
        view.selectedPathProperty().addListener(weakStateListener);
        view.selectionModeProperty().addListener(weakStateListener);
        view.getActivePath().addListener(weakStateListener);
        view.getCheckedPaths().addListener(weakStateListener);
        view.childrenLoaderProperty().addListener(weakStateListener);
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
        RXSizing.lockToPrefSize(selectedCheck);
        selectedCheck.setMouseTransparent(true);
        content.getStyleClass().add("content");
        arrow.getStyleClass().add("arrow");
        RXSizing.lockToPrefSize(arrow);
        arrow.setMouseTransparent(true);
        loadingGlyph.getStyleClass().add("loading");
        RXSizing.lockToPrefSize(loadingGlyph);
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
     * Called whenever the cell is bound to an item (every non-empty re-bind,
     * including recycling), when the item's value changes, and when the view's
     * converter changes — never on pure state changes, so reuse cached
     * node fields rather than allocating per call. The default returns a reused
     * {@link Label} set to {@link #getDisplayText(Object)
     * getDisplayText(item.getValue())}. Returning {@code null} renders an empty
     * content area.
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
     * {@link RXCascaderView#getConverter() converter}, falling back to
     * {@code String.valueOf(value)} when none is set. A {@code null} value, or a
     * converter that returns {@code null}, yields the empty string. For use by
     * subclasses overriding {@link #createContent(RXCascaderItem)}.
     *
     * @param value value to render
     * @return display text, never {@code null}
     */
    protected final String getDisplayText(T value) {
        return CascaderText.resolve(view.getConverter(), value);
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

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(RXRipplePane.DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXCascaderCell.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the row's press ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; setting {@code null} renders no
     * fill (transparent) per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill, or {@code null}
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Ripple Opacity ====================

    private final DoubleProperty rippleOpacity =
            new StyleableDoubleProperty(RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.RIPPLE_OPACITY;
                }

                @Override
                public Object getBean() {
                    return RXCascaderCell.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for the row's press ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
     *
     * @return the ripple opacity property
     */
    public final DoubleProperty rippleOpacityProperty() {
        return rippleOpacity;
    }

    /**
     * Returns the ripple opacity.
     *
     * @return the ripple opacity
     */
    public final double getRippleOpacity() {
        return rippleOpacity.get();
    }

    /**
     * Sets the ripple opacity.
     *
     * @param value the ripple opacity
     */
    public final void setRippleOpacity(double value) {
        rippleOpacity.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXCascaderCell.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether pressing the row shows a ripple. Turning this off clears any running
     * ripple.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether the press ripple is enabled.
     *
     * @return whether the press ripple is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether the press ripple is enabled.
     *
     * @param value {@code true} to enable the ripple
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXCascaderCell.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the low-opacity hover/press state overlay tints the row.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state overlay may show.
     *
     * @return whether the state overlay may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state overlay may show.
     *
     * @param value {@code true} to allow the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== Skin ====================

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCascaderCellSkin<>(this);
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
            disposeSpinner();
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
        item.disableProperty().addListener(weakStateListener);
        item.loadStateProperty().addListener(weakStateListener);
        item.leafHintProperty().addListener(weakStateListener);
        item.valueProperty().addListener(weakContentListener);
        item.getChildren().addListener(weakChildrenListener);
        // Effective-disabled depends on the whole ancestor chain, so react to an
        // ancestor becoming (un)disabled too, not only this item.
        for (RXCascaderItem<T> ancestor = item.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.disableProperty().addListener(weakStateListener);
            observedAncestors.add(ancestor);
        }
    }

    private void detachObservedItem() {
        if (observedItem == null) {
            return;
        }
        observedItem.checkedProperty().removeListener(weakStateListener);
        observedItem.indeterminateProperty().removeListener(weakStateListener);
        observedItem.disableProperty().removeListener(weakStateListener);
        observedItem.loadStateProperty().removeListener(weakStateListener);
        observedItem.leafHintProperty().removeListener(weakStateListener);
        observedItem.valueProperty().removeListener(weakContentListener);
        observedItem.getChildren().removeListener(weakChildrenListener);
        for (RXCascaderItem<T> ancestor : observedAncestors) {
            ancestor.disableProperty().removeListener(weakStateListener);
        }
        observedAncestors.clear();
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
        boolean multiple = view.getSelectionMode() == SelectionMode.MULTIPLE;
        boolean disabled = view.isEffectivelyDisabled(item);
        boolean leaf = view.isLeaf(item);
        boolean loading = item.getLoadState() == RXCascaderItem.LoadState.LOADING;
        boolean loadFailed = item.getLoadState() == RXCascaderItem.LoadState.FAILED;
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
        pseudoClassStateChanged(LOAD_FAILED, loadFailed);
        pseudoClassStateChanged(LEAF, leaf);
        updateSpinner();
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
        pseudoClassStateChanged(LOAD_FAILED, false);
        pseudoClassStateChanged(LEAF, false);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXCascaderCell<?>, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXCascaderCell<?> cell) {
                        return !cell.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXCascaderCell<?> cell) {
                        return (StyleableProperty<Paint>) cell.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXCascaderCell<?>, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXCascaderCell<?> cell) {
                        return !cell.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCascaderCell<?> cell) {
                        return (StyleableProperty<Number>) cell.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXCascaderCell<?>, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXCascaderCell<?> cell) {
                        return !cell.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXCascaderCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXCascaderCell<?>, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXCascaderCell<?> cell) {
                        return !cell.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXCascaderCell<?> cell) {
                        return (StyleableProperty<Boolean>) cell.stateOverlayEnabledProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ListCell.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /** {@inheritDoc} */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
