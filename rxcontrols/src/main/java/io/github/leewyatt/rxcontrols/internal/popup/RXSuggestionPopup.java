package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.transform.Scale;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Anchored suggestion / autocomplete popup, built on {@link RXPopupSupport} and
 * an {@link RXListView} content body. Owns the suggestion data model (a swappable
 * source mirrored into a stable {@link FilteredList}), a keyboard-navigation
 * bridge driven by the host editor, mouse selection, a light entrance animation,
 * and optional filter debouncing. Content-selection semantics (how a chosen item
 * is written back) stay with the consumer via {@link #setOnSuggestionSelected}.
 *
 * <p>Kept internal: the first consumer is {@code RXAutoComplete}. Focus stays on
 * the host editor — the list is not focus-traversable and is never asked to take
 * focus; navigation is expressed through the list's public selection model as a
 * highlight cursor. Use on the JavaFX Application Thread.
 *
 * @param <T> suggestion item type
 */
public final class RXSuggestionPopup<T> {

    // ==================== Constants ====================

    private static final String POPUP_STYLE_CLASS = "rx-suggestion-popup";
    private static final int DEFAULT_MAX_VISIBLE_ROWS = 8;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180);
    private static final double ENTRANCE_START_SCALE = 0.92;

    // ==================== Content + support ====================

    private final RXListView<T> listView = new RXListView<>();
    private final RXPopupSupport support;
    private final SkinDisposer disposer = new SkinDisposer();

    // ==================== Config ====================

    private int maxVisibleRows = DEFAULT_MAX_VISIBLE_ROWS;
    private boolean hideOnSelect = true;
    private boolean animated = true;
    private Duration animationDuration = DEFAULT_ANIMATION_DURATION;
    private Duration filterDelay = Duration.ZERO;
    private Consumer<T> onSuggestionSelected;

    // ==================== Animation ====================

    private final Scale entranceScale = new Scale(1, 1);
    private Timeline entrance;
    private PauseTransition filterDebounce;

    // ==================== Constructor ====================

    /**
     * Creates an empty suggestion popup.
     */
    public RXSuggestionPopup() {
        // Focus stays on the host editor; the list is a passive highlight surface.
        listView.setFocusTraversable(false);
        listView.setItems(filtered);
        listView.getTransforms().add(entranceScale);

        support = new RXPopupSupport(listView);
        support.setPopupStyleClass(POPUP_STYLE_CLASS);

        disposer.registerListener(support.showingProperty(), this::onShowingChanged);
        disposer.registerListener(filtered, this::onFilteredChanged);
        disposer.registerListener(listView.insetsProperty(), this::updatePopupHeight);
        disposer.registerEventHandler(listView, MouseEvent.MOUSE_CLICKED, this::handleListClicked);
        disposer.registerDisposeTask(() -> listView.getTransforms().remove(entranceScale));

        // Observe the initial suggestions list too (mirrors RXListView's constructor):
        // a SimpleObjectProperty does not fire invalidated() for its initial value, so
        // without this a getSuggestions() mutation before the first setSuggestions()
        // would not reach the backing/filtered view.
        rebindSuggestions();

        updatePopupHeight();
    }

    // ==================== Lifecycle ====================

    /**
     * Shows the popup anchored to the given node.
     *
     * @param anchor the node to anchor to
     */
    public void show(Node anchor) {
        updatePopupHeight();
        support.show(anchor);
    }

    /**
     * Rebinds the anchor without changing showing state (see
     * {@link RXPopupSupport#setAnchor(Node)}).
     *
     * @param anchor the new anchor node
     */
    public void setAnchor(Node anchor) {
        support.setAnchor(anchor);
    }

    /**
     * Hides the popup.
     */
    public void hide() {
        support.hide();
    }

    /**
     * Returns whether the popup is showing.
     *
     * @return {@code true} if showing
     */
    public boolean isShowing() {
        return support.isShowing();
    }

    /**
     * Returns the logical showing state as a read-only property.
     *
     * @return the showing property
     */
    public ReadOnlyBooleanProperty showingProperty() {
        return support.showingProperty();
    }

    /**
     * Recomputes the popup position for the current anchor.
     */
    public void requestReposition() {
        support.requestReposition();
    }

    /**
     * Releases all resources (animation, listeners, the underlying popup support).
     */
    public void dispose() {
        stopEntrance();
        if (filterDebounce != null) {
            filterDebounce.stop();
            filterDebounce = null;
        }
        if (observedSuggestions != null) {
            observedSuggestions.removeListener(weakSuggestionsListener);
            observedSuggestions = null;
        }
        disposer.dispose();
        support.dispose();
    }

    // ==================== Keyboard bridge ====================

    /**
     * Moves the highlight cursor by {@code delta} rows (clamped to the filtered
     * range) and scrolls it into view. From no highlight, a positive delta lands on
     * the first row and a negative delta on the last. Drives the list's public
     * selection model — focus stays on the editor.
     *
     * @param delta signed number of rows to move
     */
    public void moveHighlight(int delta) {
        MultipleSelectionModel<T> model = listView.getSelectionModel();
        int count = filtered.size();
        if (model == null || count == 0) {
            return;
        }
        int current = model.getSelectedIndex();
        int next = (current < 0) ? (delta >= 0 ? 0 : count - 1) : current + delta;
        if (next < 0) {
            next = 0;
        }
        if (next > count - 1) {
            next = count - 1;
        }
        model.select(next);
        listView.scrollTo(next);
    }

    /**
     * Returns the currently highlighted item, or {@code null} if none.
     *
     * @return the highlighted item
     */
    public T highlightedItem() {
        MultipleSelectionModel<T> model = listView.getSelectionModel();
        return model == null ? null : model.getSelectedItem();
    }

    /**
     * Commits the currently highlighted item (fires the selection callback and, if
     * {@link #isHideOnSelect() hideOnSelect}, hides the popup).
     *
     * @return the committed item, or {@code null} if nothing was highlighted
     */
    public T selectHighlighted() {
        T item = highlightedItem();
        if (item != null) {
            commit(item);
        }
        return item;
    }

    // ==================== Suggestions ====================

    // FilteredList's source is fixed at construction, so a stable backing list
    // always mirrors the current `suggestions` source; the FilteredList (and thus
    // filteredSuggestions) is never rebuilt, keeping its reference stable across
    // source swaps.
    private final ObservableList<T> backing = FXCollections.observableArrayList();
    private final FilteredList<T> filtered = new FilteredList<>(backing);
    private final ObservableList<T> filteredView = FXCollections.unmodifiableObservableList(filtered);

    private ObservableList<T> observedSuggestions;
    private final ListChangeListener<T> suggestionsListener = change -> syncBacking();
    private final WeakListChangeListener<T> weakSuggestionsListener =
            new WeakListChangeListener<>(suggestionsListener);

    private final ObjectProperty<ObservableList<T>> suggestions =
            new SimpleObjectProperty<>(this, "suggestions", FXCollections.observableArrayList()) {
                @Override
                protected void invalidated() {
                    rebindSuggestions();
                }
            };

    /**
     * The full suggestion source. The list may be replaced wholesale; a
     * {@code null} source is treated as empty. The read-only
     * {@link #getFilteredSuggestions() filtered view} stays reference-stable across
     * swaps.
     *
     * @return the suggestions property
     */
    public ObjectProperty<ObservableList<T>> suggestionsProperty() {
        return suggestions;
    }

    /**
     * Returns the suggestion source.
     *
     * @return the source list, or {@code null}
     */
    public ObservableList<T> getSuggestions() {
        return suggestions.get();
    }

    /**
     * Sets the suggestion source.
     *
     * @param value the source list, or {@code null} for empty
     */
    public void setSuggestions(ObservableList<T> value) {
        suggestions.set(value);
    }

    /**
     * Returns the read-only, reference-stable filtered view of the suggestions.
     *
     * @return the filtered suggestions
     */
    public ObservableList<T> getFilteredSuggestions() {
        return filteredView;
    }

    // ==================== Filter ====================

    private final ObjectProperty<Predicate<T>> filterPredicate =
            new SimpleObjectProperty<>(this, "filterPredicate") {
                @Override
                protected void invalidated() {
                    scheduleFilter();
                }
            };

    /**
     * The predicate selecting which suggestions are visible. {@code null} shows all.
     *
     * @return the filter-predicate property
     */
    public ObjectProperty<Predicate<T>> filterPredicateProperty() {
        return filterPredicate;
    }

    /**
     * Returns the filter predicate.
     *
     * @return the predicate, or {@code null}
     */
    public Predicate<T> getFilterPredicate() {
        return filterPredicate.get();
    }

    /**
     * Sets the filter predicate.
     *
     * @param value the predicate, or {@code null} to show all
     */
    public void setFilterPredicate(Predicate<T> value) {
        filterPredicate.set(value);
    }

    // ==================== Content config ====================

    /**
     * Sets the converter supplying the default cell text.
     *
     * @param converter the converter, or {@code null} for {@code toString()}
     */
    public void setConverter(StringConverter<T> converter) {
        listView.setConverter(converter);
    }

    /**
     * Sets a custom cell factory.
     *
     * @param cellFactory the cell factory, or {@code null} for the default
     */
    public void setCellFactory(Callback<RXListView<T>, RXListCell<T>> cellFactory) {
        listView.setCellFactory(cellFactory);
    }

    /**
     * Sets the placeholder shown when there are no filtered suggestions.
     *
     * @param placeholder the placeholder node, or {@code null}
     */
    public void setPlaceholder(Node placeholder) {
        listView.setPlaceholder(placeholder);
    }

    /**
     * Sets the fixed row height (drives the popup height).
     *
     * @param value the fixed row height
     */
    public void setFixedCellSize(double value) {
        listView.setFixedCellSize(value);
        updatePopupHeight();
    }

    /**
     * Sets the maximum number of rows shown before the list scrolls.
     *
     * @param value the maximum visible rows
     */
    public void setMaxVisibleRows(int value) {
        maxVisibleRows = value;
        updatePopupHeight();
    }

    /**
     * Returns the maximum number of visible rows.
     *
     * @return the maximum visible rows
     */
    public int getMaxVisibleRows() {
        return maxVisibleRows;
    }

    // ==================== Behavior config ====================

    /**
     * Sets whether committing a suggestion hides the popup (default {@code true};
     * set {@code false} for multi-select scenarios).
     *
     * @param value hide-on-select flag
     */
    public void setHideOnSelect(boolean value) {
        hideOnSelect = value;
    }

    /**
     * Returns whether committing hides the popup.
     *
     * @return the hide-on-select flag
     */
    public boolean isHideOnSelect() {
        return hideOnSelect;
    }

    /**
     * Sets the selection callback invoked when a suggestion is committed.
     *
     * @param callback the callback, or {@code null}
     */
    public void setOnSuggestionSelected(Consumer<T> callback) {
        onSuggestionSelected = callback;
    }

    /**
     * Returns the selection callback.
     *
     * @return the callback, or {@code null}
     */
    public Consumer<T> getOnSuggestionSelected() {
        return onSuggestionSelected;
    }

    /**
     * Sets a callback invoked whenever the popup hides (any path). Forwarded to the
     * underlying support so the host can pull its own state back.
     *
     * @param callback the hidden callback, or {@code null}
     */
    public void setOnHidden(Runnable callback) {
        support.setOnHidden(callback);
    }

    /**
     * Sets whether the entrance animation plays (default {@code true}).
     *
     * @param value animated flag
     */
    public void setAnimated(boolean value) {
        animated = value;
    }

    /**
     * Returns whether the entrance animation plays.
     *
     * @return the animated flag
     */
    public boolean isAnimated() {
        return animated;
    }

    /**
     * Sets the entrance animation duration. {@code null} or non-positive disables it.
     *
     * @param value the duration
     */
    public void setAnimationDuration(Duration value) {
        animationDuration = value;
    }

    /**
     * Sets the filter debounce delay. {@code Duration.ZERO} (default) applies the
     * predicate immediately.
     *
     * @param value the debounce delay
     */
    public void setFilterDelay(Duration value) {
        filterDelay = value;
    }

    /**
     * Sets the popup width strategy.
     *
     * @param mode the width mode
     */
    public void setWidthMode(RXPopupWidthMode mode) {
        support.setWidthMode(mode);
    }

    /**
     * Sets the preferred placement.
     *
     * @param placement the placement
     */
    public void setPlacement(RXPlacement placement) {
        support.setPlacement(placement);
    }

    /**
     * Sets the gap from the anchor.
     *
     * @param offsetX horizontal offset
     * @param offsetY vertical offset
     */
    public void setOffset(double offsetX, double offsetY) {
        support.setOffset(offsetX, offsetY);
    }

    // ==================== Internal ====================

    private void rebindSuggestions() {
        if (observedSuggestions != null) {
            observedSuggestions.removeListener(weakSuggestionsListener);
        }
        observedSuggestions = suggestions.get();
        if (observedSuggestions != null) {
            observedSuggestions.addListener(weakSuggestionsListener);
        }
        syncBacking();
    }

    private void syncBacking() {
        ObservableList<T> source = suggestions.get();
        if (source == null) {
            backing.clear();
        } else {
            backing.setAll(source);
        }
    }

    private void scheduleFilter() {
        if (filterDelay == null || filterDelay.lessThanOrEqualTo(Duration.ZERO)) {
            applyFilter();
            return;
        }
        if (filterDebounce == null) {
            filterDebounce = new PauseTransition();
            filterDebounce.setOnFinished(event -> applyFilter());
        }
        filterDebounce.stop();
        filterDebounce.setDuration(filterDelay);
        filterDebounce.playFromStart();
    }

    private void applyFilter() {
        filtered.setPredicate(filterPredicate.get());
    }

    private void onFilteredChanged() {
        MultipleSelectionModel<T> model = listView.getSelectionModel();
        if (model != null) {
            model.clearSelection();
        }
        updatePopupHeight();
    }

    private void updatePopupHeight() {
        int count = filtered.size();
        // Empty reserves one row of height so the placeholder-when-empty node has
        // vertical room to render (the spec's min(count,rows) formula would give 0).
        int rows = (count == 0) ? 1 : Math.min(count, Math.max(1, maxVisibleRows));
        double cell = listView.getFixedCellSize();
        if (!(cell > 0.0)) {
            cell = RXListView.DEFAULT_FIXED_CELL_SIZE;
        }
        double insets = listView.getInsets().getTop() + listView.getInsets().getBottom();
        listView.setPrefHeight(rows * cell + insets);
        support.requestReposition();
    }

    private void commit(T item) {
        if (hideOnSelect) {
            hide();
        }
        if (onSuggestionSelected != null) {
            onSuggestionSelected.accept(item);
        }
    }

    private void handleListClicked(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXListCell<T> cell = enclosingCell(event.getTarget());
        if (cell != null && !cell.isEmpty()) {
            T item = cell.getItem();
            MultipleSelectionModel<T> model = listView.getSelectionModel();
            if (model != null) {
                model.select(item);
            }
            commit(item);
            event.consume();
        }
    }

    @SuppressWarnings("unchecked")
    private RXListCell<T> enclosingCell(Object target) {
        Node node = (target instanceof Node) ? (Node) target : null;
        while (node != null) {
            if (node instanceof RXListCell) {
                return (RXListCell<T>) node;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Entrance animation ====================

    private void onShowingChanged() {
        if (support.isShowing()) {
            playEntrance();
        } else {
            stopEntrance();
        }
    }

    private void playEntrance() {
        stopEntrance();
        if (!animated || animationDuration == null || animationDuration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        // Scale-in from the top edge (the common below-anchor case): an unfolding
        // dropdown. Fade runs alongside. Not replayed on re-filter (showing stays
        // true), so it never slows typing.
        entranceScale.setPivotX(0.0);
        entranceScale.setPivotY(0.0);
        entranceScale.setY(ENTRANCE_START_SCALE);
        listView.setOpacity(0.0);
        entrance = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(listView.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                        new KeyValue(entranceScale.yProperty(), ENTRANCE_START_SCALE, Interpolator.EASE_OUT)),
                new KeyFrame(animationDuration,
                        new KeyValue(listView.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(entranceScale.yProperty(), 1.0, Interpolator.EASE_OUT)));
        entrance.setOnFinished(event -> resetEntranceState());
        entrance.playFromStart();
    }

    private void stopEntrance() {
        if (entrance != null) {
            entrance.stop();
            entrance = null;
        }
        resetEntranceState();
    }

    private void resetEntranceState() {
        listView.setOpacity(1.0);
        entranceScale.setY(1.0);
    }
}
