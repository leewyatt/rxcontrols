package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.chip.ChipEditor;
import io.github.leewyatt.rxcontrols.internal.chip.ChipFlowLayout;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;
import io.github.leewyatt.rxcontrols.internal.popup.RXSuggestionPopup;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXChipInput}: it owns the tamed {@link ChipEditor}, the
 * {@link ChipFlowLayout} that wraps the chips and fills the row remainder with the
 * editor, and the index-aligned mapping from the control's chip items to chip nodes.
 *
 * <p>The control owns the model and the commit decision; this skin renders it and
 * orchestrates removal. Removing a chip (close click, Backspace on an empty editor,
 * or {@code removeChip}) fires the vetoable {@link RXChipEvent#REMOVE} on the chip
 * node; if not consumed the skin removes the item at that node's index, and the
 * chip-list listener detaches the node and fires {@link RXChipEvent#REMOVED}. Because
 * the mapping is by index (not {@code equals}), duplicate items are removed exactly.</p>
 *
 * @param <T> the chip item type
 */
public class RXChipInputSkin<T> extends RXSkinBase<RXChipInput<T>> {

    private static final String CONTENT_STYLE_CLASS = "content";
    private static final String SCROLL_STYLE_CLASS = "chip-scroll-pane";
    private static final Duration FILTER_DEBOUNCE = Duration.millis(150);
    private static final double POPUP_GAP = 4.0;

    private final ChipEditor editor;
    private final ChipFlowLayout flowLayout;
    private final List<RXChip> chipNodes = new ArrayList<>();
    private final RXSuggestionPopup<T> popup = new RXSuggestionPopup<>();
    private final PauseTransition filterDebounce = new PauseTransition(FILTER_DEBOUNCE);

    // Direction hint for the refocus after a keyboard chip removal: +1 (Delete) focuses
    // the next chip, -1 (Backspace) the previous; 0 (close-click) uses the default rule.
    private int pendingRemoveFocusDir;

    // Set while a chosen suggestion is written back, so the resulting editor-text
    // change does not immediately re-open the just-dismissed popup.
    private boolean suppressAutoShow;

    private ScrollPane scrollPane;
    private Region currentContent;

    /**
     * Creates a skin for the given chip input.
     *
     * @param control the chip input this skin is attached to
     */
    public RXChipInputSkin(RXChipInput<T> control) {
        super(control);

        editor = new ChipEditor(control::getEditorMinWidth);
        flowLayout = new ChipFlowLayout(editor, control::getEditorMinWidth, control::getHgap, control::getVgap);
        flowLayout.getStyleClass().add(CONTENT_STYLE_CLASS);

        // Editor <-> control wiring.
        editor.textProperty().bindBidirectional(control.editorTextProperty());
        disposer.registerDisposeTask(
                () -> editor.textProperty().unbindBidirectional(control.editorTextProperty()));
        disposer.registerBinding(editor.editableProperty(), control.editableProperty());
        disposer.registerBinding(editor.promptTextProperty(),
                Bindings.when(Bindings.isEmpty(control.getChips()))
                        .then(control.promptTextProperty())
                        .otherwise(""));

        disposer.registerEventFilter(editor, KeyEvent.KEY_PRESSED, this::onEditorKeyPressed);
        disposer.registerEventFilter(editor, KeyEvent.KEY_TYPED, this::onEditorKeyTyped);
        // Chip-focus navigation. A capturing filter so Delete / Backspace on a focused chip
        // are steered here (with a focus direction) before the chip removes itself.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::onChipNavKey);

        // Removal veto flow: a REMOVE bubbling from a chip node removes its item.
        disposer.registerEventHandler(control, RXChipEvent.REMOVE, this::onChipRemoveRequested);

        // A press on empty space focuses the editor, so the whole field behaves like a text
        // input; a press on a chip body is handled by the chip itself (which takes focus).
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onContentMousePressed);

        // Chip model -> nodes, kept index-aligned.
        rebuildAllChipNodes();
        ListChangeListener<T> chipsListener = this::onChipsChanged;
        control.getChips().addListener(chipsListener);
        disposer.registerDisposeTask(() -> control.getChips().removeListener(chipsListener));

        disposer.registerListener(control.maxRowsProperty(), this::updateContentStructure);
        disposer.registerListener(control.editorMinWidthProperty(), flowLayout::requestLayout);
        disposer.registerListener(control.hgapProperty(), flowLayout::requestLayout);
        disposer.registerListener(control.vgapProperty(), flowLayout::requestLayout);

        // Suggestion popup wiring (reuses the shared RXSuggestionPopup).
        popup.setSuggestions(control.getSuggestions());
        popup.setConverter(control.getConverter());
        popup.setCellFactory(control.getSuggestionCellFactory());
        popup.setMaxVisibleRows(control.getVisibleRowCount());
        popup.setHideOnSelect(control.isHideOnSelect());
        popup.setAnimated(control.isAnimated());
        popup.setOffset(0, POPUP_GAP);
        // Anchor to the editor (which rides the caret row) at content width, so the
        // list opens next to what is being typed rather than under the whole field.
        popup.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
        // Already-chosen options are shown greyed and non-selectable when they cannot be
        // added again (duplicates off); allowDuplicates on leaves them selectable.
        popup.setDisabledPredicate(item -> !control.isAllowDuplicates() && control.getChips().contains(item));
        popup.setAutoHighlightFirst(control.isAutoSelectFirstSuggestion());
        popup.setOnSuggestionSelected(this::commitSuggestion);
        popup.setOnHidden(filterDebounce::stop);
        filterDebounce.setOnFinished(event -> applyFilterAndMaybeShow(false));

        disposer.registerListener(editor.textProperty(), this::onEditorTextChanged);
        disposer.registerListener(editor.focusedProperty(), this::onEditorFocusChanged);
        disposer.registerListener(control.filterFunctionProperty(), this::onEditorTextChanged);
        disposer.registerListener(control.filterSelectedOptionsProperty(), this::refilterOpenPopup);
        disposer.registerListener(control.allowDuplicatesProperty(), this::refilterOpenPopup);
        disposer.registerListener(control.autoSelectFirstSuggestionProperty(),
                () -> popup.setAutoHighlightFirst(control.isAutoSelectFirstSuggestion()));
        disposer.registerListener(control.converterProperty(),
                () -> popup.setConverter(control.getConverter()));
        disposer.registerListener(control.suggestionCellFactoryProperty(),
                () -> popup.setCellFactory(control.getSuggestionCellFactory()));
        disposer.registerListener(control.visibleRowCountProperty(),
                () -> popup.setMaxVisibleRows(control.getVisibleRowCount()));
        disposer.registerListener(control.hideOnSelectProperty(),
                () -> popup.setHideOnSelect(control.isHideOnSelect()));
        disposer.registerListener(control.animatedProperty(),
                () -> popup.setAnimated(control.isAnimated()));
        disposer.registerListener(popup.showingProperty(),
                () -> control.setPopupShowing(popup.isShowing()));
        disposer.registerListener(popup.getFilteredSuggestions(), this::onFilteredSuggestionsChanged);
        disposer.registerDisposeTask(filterDebounce::stop);
        disposer.registerDisposeTask(popup::dispose);
        // RXPopupSupport.dispose() hides without flipping its logical showing state, so
        // clear the mirrored control state explicitly, else :popup-showing stays stuck.
        disposer.registerDisposeTask(() -> control.setPopupShowing(false));

        updateContentStructure();
    }

    // ==================== Chip node mapping ====================

    private void rebuildAllChipNodes() {
        chipNodes.clear();
        for (T item : getSkinnable().getChips()) {
            chipNodes.add(createChipNode(item));
        }
        flowLayout.setChipNodes(chipNodes);
    }

    private void onChipsChanged(ListChangeListener.Change<? extends T> change) {
        List<RXChipEvent> addedEvents = new ArrayList<>();
        List<RXChipEvent> removedEvents = new ArrayList<>();
        while (change.next()) {
            if (change.wasPermutated()) {
                int from = change.getFrom();
                int to = change.getTo();
                List<RXChip> moved = new ArrayList<>(chipNodes.subList(from, to));
                for (int i = from; i < to; i++) {
                    chipNodes.set(change.getPermutation(i), moved.get(i - from));
                }
            } else {
                if (change.wasRemoved()) {
                    List<? extends T> removedItems = change.getRemoved();
                    int from = change.getFrom();
                    for (int k = 0; k < removedItems.size(); k++) {
                        RXChip node = chipNodes.remove(from);
                        removedEvents.add(new RXChipEvent(RXChipEvent.REMOVED, node, removedItems.get(k)));
                    }
                }
                if (change.wasAdded()) {
                    List<? extends T> addedItems = change.getAddedSubList();
                    int from = change.getFrom();
                    for (int k = 0; k < addedItems.size(); k++) {
                        T item = addedItems.get(k);
                        RXChip node = createChipNode(item);
                        chipNodes.add(from + k, node);
                        addedEvents.add(new RXChipEvent(RXChipEvent.ADDED, node, item));
                    }
                }
            }
        }
        flowLayout.setChipNodes(chipNodes);
        RXChipInput<T> control = getSkinnable();
        for (RXChipEvent event : removedEvents) {
            control.fireEvent(event);
        }
        for (RXChipEvent event : addedEvents) {
            control.fireEvent(event);
        }
        // A just-added chip must drop out of an open suggestion list when duplicates are
        // not allowed (and a removed one may re-enter it). During a suggestion commit the
        // query here is still pre-clearInput, so skip this stale-query refresh and let
        // commitSuggestion's post-clear refresh be the single authority (else the
        // keep-open multi-tag popup would hide on the transient empty result).
        if (!suppressAutoShow) {
            refilterOpenPopup();
        }
    }

    private RXChip createChipNode(T item) {
        Callback<T, RXChip> factory = getSkinnable().getChipFactory();
        if (factory != null) {
            return factory.call(item);
        }
        RXChip chip = new RXChip(textFor(item));
        chip.setRemovable(true);
        return chip;
    }

    private String textFor(T item) {
        StringConverter<T> converter = getSkinnable().getConverter();
        return converter != null ? converter.toString(item) : String.valueOf(item);
    }

    // ==================== Removal ====================

    private void onChipRemoveRequested(RXChipEvent event) {
        RXChip chip = event.getChip();
        int index = chipNodes.indexOf(chip);
        if (index < 0) {
            return;
        }
        boolean wasFocused = isChipFocused(chip);
        int dir = pendingRemoveFocusDir;
        getSkinnable().getChips().remove(index);
        event.consume();
        if (wasFocused) {
            // Keep keyboard focus in the input rather than losing it with the removed node.
            refocusAfterRemoval(index, dir);
        }
    }

    // Moves focus after a removal at removedIndex: Backspace (dir < 0) prefers the previous
    // chip, Delete / close-click (dir >= 0) the chip that shifted into the slot; the editor
    // when no chip remains to take focus.
    private void refocusAfterRemoval(int removedIndex, int dir) {
        int target;
        if (dir < 0) {
            target = nextFocusableChip(removedIndex, -1);
            if (target < 0) {
                target = nextFocusableChip(removedIndex - 1, 1);
            }
        } else {
            target = nextFocusableChip(removedIndex - 1, 1);
            if (target < 0) {
                target = nextFocusableChip(removedIndex, -1);
            }
        }
        if (target >= 0) {
            chipNodes.get(target).requestFocus();
        } else {
            editor.requestFocus();
        }
    }

    private boolean isChipFocused(RXChip chip) {
        Scene scene = getSkinnable().getScene();
        return scene != null && scene.getFocusOwner() == chip;
    }

    /**
     * First chip strictly past {@code from} in the {@code step} direction that can take
     * focus (enabled and visible); {@code -1} if none — a disabled node refuses
     * {@code requestFocus()}, so disabled/invisible chips must be stepped over.
     */
    private int nextFocusableChip(int from, int step) {
        for (int i = from + step; i >= 0 && i < chipNodes.size(); i += step) {
            RXChip chip = chipNodes.get(i);
            if (!chip.isDisabled() && chip.isVisible()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Runs the vetoable removal flow for the chip at {@code index} (used by {@link
     * RXChipInput#removeChip}).
     *
     * @param index the chip-node index
     * @return {@code true} if the chip was removed (not vetoed)
     */
    public boolean requestRemoveAt(int index) {
        if (index < 0 || index >= chipNodes.size()) {
            return false;
        }
        int before = getSkinnable().getChips().size();
        chipNodes.get(index).remove();
        return getSkinnable().getChips().size() < before;
    }

    // ==================== Editor keys ====================

    private void onEditorKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        // A separator key (other than Enter) commits the current text like Enter. Only
        // an unmodified press counts: a modifier chord (e.g. Shift+Comma -> '<') is a
        // different character, not a separator, so it must fall through to normal typing.
        if (code != KeyCode.ENTER && !hasModifier(event) && getSkinnable().getSeparatorKeys().contains(code)) {
            handleSeparatorCommit(event);
            return;
        }
        switch (code) {
            case ENTER -> handleEnter(event);
            case DOWN -> handleDown(event);
            case UP -> handleUp(event);
            case ESCAPE -> handleEscape(event);
            case BACK_SPACE -> handleBackspace(event);
            case LEFT -> handleEditorCursorInto(event, false);
            case RIGHT -> handleEditorCursorInto(event, true);
            case HOME -> handleEditorCursorInto(event, false);
            default -> {
            }
        }
    }

    private void handleSeparatorCommit(KeyEvent event) {
        RXChipInput<T> control = getSkinnable();
        String text = control.getEditorText();
        if (text != null && !text.isEmpty()) {
            control.commitInput();
            event.consume();
        }
    }

    // Entering chip-focus mode from the editor: LEFT (RIGHT under RTL) with an empty editor
    // moves focus to the last chip; HOME jumps to the first chip. With text in the editor
    // the keys fall through so the TextField moves its own text caret.
    private void handleEditorCursorInto(KeyEvent event, boolean rightKey) {
        if (!editor.getText().isEmpty() || chipNodes.isEmpty()) {
            return;
        }
        boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        boolean home = event.getCode() == KeyCode.HOME;
        boolean towardChips = home || (rightKey == rtl);
        if (!towardChips) {
            return;
        }
        int target = home ? nextFocusableChip(-1, 1) : nextFocusableChip(chipNodes.size(), -1);
        if (target < 0) {
            return;
        }
        chipNodes.get(target).requestFocus();
        event.consume();
    }

    private void onEditorKeyTyped(KeyEvent event) {
        String character = event.getCharacter();
        if (character != null && !character.isEmpty() && separatorCharacters().contains(character)) {
            // Swallow the separator character so it does not leak into the editor.
            event.consume();
        }
    }

    private Set<String> separatorCharacters() {
        Set<String> characters = new HashSet<>();
        for (KeyCode code : getSkinnable().getSeparatorKeys()) {
            if (code != KeyCode.ENTER) {
                String character = code.getChar();
                if (character != null && !character.isEmpty()) {
                    characters.add(character);
                }
            }
        }
        return characters;
    }

    private static boolean hasModifier(KeyEvent event) {
        return event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    private void handleEnter(KeyEvent event) {
        // A highlighted suggestion commits first, regardless of policy.
        if (popup.isShowing() && popup.highlightedItem() != null) {
            popup.selectHighlighted();
            event.consume();
            return;
        }
        RXChipInput<T> control = getSkinnable();
        String text = control.getEditorText();
        if (text == null || text.isEmpty()) {
            return;
        }
        control.commitInput();
        // FREE / CREATE consume the Enter; STRICT keeps the text and lets it bubble.
        if (control.getCustomInputPolicy() != RXChipInput.CustomInputPolicy.STRICT) {
            event.consume();
        }
    }

    private void handleDown(KeyEvent event) {
        // Navigating now: cancel any pending re-filter that would clear the highlight.
        filterDebounce.stop();
        if (popup.isShowing()) {
            popup.moveHighlight(1);
        } else {
            applyFilterAndMaybeShow(true);
            // DOWN-to-open lands on the first row. With autoSelectFirstSuggestion the
            // popup already opens with row 0 highlighted, so only advance when nothing is
            // highlighted yet — otherwise DOWN would skip the auto-highlighted first row.
            if (popup.isShowing() && popup.highlightedItem() == null) {
                popup.moveHighlight(1);
            }
        }
        event.consume();
    }

    private void handleUp(KeyEvent event) {
        if (popup.isShowing()) {
            filterDebounce.stop();
            popup.moveHighlight(-1);
            event.consume();
        }
    }

    private void handleEscape(KeyEvent event) {
        if (popup.isShowing()) {
            hidePopup();
            event.consume();
        } else {
            filterDebounce.stop();
        }
    }

    private void handleBackspace(KeyEvent event) {
        // Backspace on an empty editor removes the last chip (fast trailing delete).
        if (editor.getText().isEmpty() && !chipNodes.isEmpty()) {
            chipNodes.get(chipNodes.size() - 1).remove();
            event.consume();
        }
    }

    // ==================== Chip-focus navigation ====================

    // Keys while a chip is focused. Registered as a capturing filter so Delete / Backspace
    // are handled here (with a focus direction) before the chip's own removal handler.
    private void onChipNavKey(KeyEvent event) {
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return;
        }
        int current = chipNodes.indexOf(scene.getFocusOwner());
        if (current < 0) {
            // Focus is on the editor (handled by its own filter) or elsewhere.
            return;
        }
        boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        switch (event.getCode()) {
            case LEFT -> moveChipCursor(current, rtl ? 1 : -1, event);
            case RIGHT -> moveChipCursor(current, rtl ? -1 : 1, event);
            case HOME -> {
                int first = nextFocusableChip(-1, 1);
                if (first >= 0) {
                    chipNodes.get(first).requestFocus();
                    event.consume();
                }
            }
            case END, ESCAPE -> {
                editor.requestFocus();
                event.consume();
            }
            case DELETE -> removeFocusedChip(current, 1, event);
            case BACK_SPACE -> removeFocusedChip(current, -1, event);
            default -> {
            }
        }
    }

    private void moveChipCursor(int current, int step, KeyEvent event) {
        int target = nextFocusableChip(current, step);
        if (target >= 0) {
            chipNodes.get(target).requestFocus();
            event.consume();
        } else if (step > 0) {
            // Past the last focusable chip: return to the editor.
            editor.requestFocus();
            event.consume();
        }
        // step < 0 with no focusable chip to the left: leave focus and do not consume,
        // so platform traversal can move out of the input.
    }

    // Removes the focused chip and records which way focus should move afterwards: Delete
    // (dir +1) toward the next chip, Backspace (dir -1) toward the previous.
    private void removeFocusedChip(int index, int dir, KeyEvent event) {
        RXChip chip = chipNodes.get(index);
        if (!chip.isRemovable()) {
            return;
        }
        event.consume();
        pendingRemoveFocusDir = dir;
        try {
            chip.remove();
        } finally {
            pendingRemoveFocusDir = 0;
        }
    }

    // ==================== Suggestion popup ====================

    private void onEditorTextChanged() {
        if (suppressAutoShow) {
            return;
        }
        filterDebounce.playFromStart();
    }

    private void onEditorFocusChanged() {
        if (!editor.isFocused()) {
            hidePopup();
        }
    }

    // One-way close-on-empty: an open dropdown whose matches vanish (a live
    // suggestions mutation) hides; re-opening stays typing / Down / showSuggestions
    // driven, so an empty popup never lingers and data arrival never pops it open.
    private void onFilteredSuggestionsChanged() {
        if (popup.isShowing() && popup.getFilteredSuggestions().isEmpty()) {
            hidePopup();
        }
    }

    private void hidePopup() {
        filterDebounce.stop();
        popup.hide();
    }

    private void applyFilterAndMaybeShow(boolean openOnEmptyText) {
        String query = editor.getText() == null ? "" : editor.getText();
        popup.setFilterPredicate(buildPredicate(query));
        boolean shouldShow = editor.isFocused()
                && !popup.getFilteredSuggestions().isEmpty()
                && (openOnEmptyText || !query.isEmpty());
        if (shouldShow) {
            popup.show(editor);
        } else {
            hidePopup();
        }
    }

    private Predicate<T> buildPredicate(String query) {
        RXChipInput<T> control = getSkinnable();
        Function<String, Predicate<T>> function = control.getFilterFunction();
        Predicate<T> base = function != null ? function.apply(query) : defaultPredicate(query);
        // Hiding already-chosen options is opt-in via filterSelectedOptions. Otherwise
        // they stay in the list, shown disabled when they cannot be added again — see the
        // popup's disabledPredicate wired in the constructor.
        if (control.isFilterSelectedOptions()) {
            return base.and(item -> !control.getChips().contains(item));
        }
        return base;
    }

    // Re-applies the current query's filter to an OPEN popup — used when the chip set,
    // allowDuplicates or filterSelectedOptions change. An emptied list closes the popup
    // via the filtered-suggestions listener (single close-on-empty funnel).
    private void refilterOpenPopup() {
        if (!popup.isShowing()) {
            return;
        }
        String query = editor.getText() == null ? "" : editor.getText();
        popup.setFilterPredicate(buildPredicate(query));
        // Chip set / allowDuplicates changed, so an item's selectable state may have too.
        popup.refreshDisabledState();
    }

    private Predicate<T> defaultPredicate(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return item -> {
            String text = textFor(item);
            return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
        };
    }

    private void commitSuggestion(T item) {
        filterDebounce.stop();
        RXChipInput<T> control = getSkinnable();
        suppressAutoShow = true;
        try {
            if (control.isAllowDuplicates() || !control.getChips().contains(item)) {
                control.getChips().add(item);
            }
            control.clearInput();
        } finally {
            suppressAutoShow = false;
        }
        // When the popup is kept open (multi-tagging), refresh it for the now-cleared
        // query so the just-chosen item drops out and an emptied list closes.
        refilterOpenPopup();
    }

    // ==================== Focus on click ====================

    private void onContentMousePressed(MouseEvent event) {
        if (!getSkinnable().isEditable()) {
            return;
        }
        if (event.getTarget() instanceof Node target && isInChipOrEditor(target)) {
            // A chip (which takes focus) or the editor itself handles its own press.
            return;
        }
        editor.requestFocus();
        editor.end();
        event.consume();
    }

    private boolean isInChipOrEditor(Node node) {
        for (Node n = node; n != null && n != getSkinnable(); n = n.getParent()) {
            if (n == editor || chipNodes.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens the suggestion popup (used by {@link RXChipInput#showSuggestions()}).
     */
    public void showSuggestions() {
        applyFilterAndMaybeShow(true);
    }

    /**
     * Closes the suggestion popup (used by {@link RXChipInput#hideSuggestions()}).
     */
    public void hideSuggestions() {
        hidePopup();
    }

    // ==================== Content structure (maxRows) ====================

    private void updateContentStructure() {
        boolean wantScroll = getSkinnable().getMaxRows() > 0;
        if (scrollPane != null) {
            scrollPane.setContent(null);
        }
        getChildren().clear();
        if (wantScroll) {
            if (scrollPane == null) {
                scrollPane = new ScrollPane();
                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scrollPane.getStyleClass().add(SCROLL_STYLE_CLASS);
            }
            scrollPane.setContent(flowLayout);
            currentContent = scrollPane;
        } else {
            currentContent = flowLayout;
        }
        getChildren().add(currentContent);
        getSkinnable().requestLayout();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        currentContent.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + flowLayout.minWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + flowLayout.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + cappedContentHeight(innerWidth(width, leftInset, rightInset), true) + bottomInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + cappedContentHeight(innerWidth(width, leftInset, rightInset), false) + bottomInset;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // An input sizes to its content (like a text field), not to whatever vertical
        // space a parent offers; SkinBase's default max of Double.MAX_VALUE would let a
        // VBox/StackPane stretch the field into a tall empty box.
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    private double cappedContentHeight(double innerWidth, boolean min) {
        double natural = min ? flowLayout.minHeight(innerWidth) : flowLayout.prefHeight(innerWidth);
        int maxRows = getSkinnable().getMaxRows();
        if (maxRows > 0) {
            return Math.min(natural, maxRowsHeight(maxRows));
        }
        return natural;
    }

    private double maxRowsHeight(int maxRows) {
        double rowHeight = flowLayout.singleRowHeight();
        if (rowHeight <= 0) {
            rowHeight = editor.prefHeight(-1);
        }
        Insets flowInsets = flowLayout.getInsets();
        return flowInsets.getTop() + flowInsets.getBottom()
                + maxRows * rowHeight + Math.max(0, maxRows - 1) * flowLayout.rowGap();
    }

    private static double innerWidth(double width, double leftInset, double rightInset) {
        return width < 0 ? -1 : width - leftInset - rightInset;
    }
}
