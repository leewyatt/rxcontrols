package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXIndexedSelectionModel;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXListVisibleRange;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.event.RXListViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXIndexedSelectionMutationGuard;
import io.github.leewyatt.rxcontrols.internal.RenderScaleWatch;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Skin for {@link RXListView}. It assembles the self-built virtualizing
 * {@link RXListViewport}, builds the single-column fixed-height
 * {@link RXListRowPlan} and shares it with the viewport, drives the placeholder
 * and the {@code :empty} state, consumes pending scroll requests, publishes the
 * read-only {@code rowCount} / {@code visibleRange} metrics after every pass, and
 * carries the keyboard (including type-ahead) and mouse selection behavior. With
 * collapsible sections it also toggles sections on header presses, lets the keyboard
 * cursor rest on header rows, keeps the cursor off hidden items and anchors the view
 * across a collapse or expand.
 *
 * @param <T> the item type
 */
public class RXListViewSkin<T> extends RXSkinBase<RXListView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass SELECTION_ROW_PSEUDO_CLASS = PseudoClass.getPseudoClass("selection-row");
    private static final PseudoClass SELECTION_CHECKMARK_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("selection-checkmark");
    private static final PseudoClass SELECTION_CHECKBOX_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("selection-checkbox");

    private static final double FALLBACK_FIXED_CELL_SIZE = RXListView.DEFAULT_FIXED_CELL_SIZE;
    // Fallback content extents when the control is laid out unconstrained; real
    // sizes come from the parent / explicit pref sizes in practice.
    private static final int DEFAULT_VISIBLE_ROW_COUNT = 10;
    private static final double DEFAULT_CONTENT_WIDTH = 200.0;
    private static final double MIN_VIEWPORT_CONTENT_WIDTH = 2.0;
    // A first-letter type-ahead burst resets after this idle gap.
    private static final long TYPE_AHEAD_RESET_MS = 1000L;

    // Cap consecutive no-progress re-pack passes so a non-deterministic-height cell can
    // never spin layout forever; a pass that measures a new item resets the counter, so a
    // genuine multi-step convergence still completes.
    private static final int MAX_STALLED_REPACK_PASSES = 8;

    // Key for the shift-range selection anchor, stashed in the control's property
    // map (mirrors ListView's CellBehaviorBase anchor) so it needs no new API.
    private static final String ANCHOR_KEY = "rx-list-view-selection-anchor";

    private final RXListViewport<T> viewport;
    private final StackPane placeholderRegion;

    // Persistent variable-height (measure-on-scroll) state; unused on the fixed path. A
    // measured row height reflects the row's rendering at its last-visible width, so the
    // skin invalidates it with three strengths depending on what changed:
    //   - clear() — wholesale changes where the old measurements describe nothing here: an
    //     items-list swap, a cellFactory change (a different renderer), a fixed/variable flip.
    //   - invalidateAllMeasured() — a converter change (onConverterChanged): it re-sources
    //     the default cell's text, which can change a row's line count (and height), so the
    //     old measurements can be far off; drop them and let the rows re-measure (off-screen
    //     rows fall back to the estimate until scrolled in). Rare, so the scroll bar
    //     adjusting once is fine.
    //   - lazy (no invalidation; visible rows just re-measure each pass) — a content-width
    //     change (resize / scroll-bar toggle) and a selection-visual-mode / selection-mode
    //     change (which only swaps the small leading checkbox / checkmark). Here the old
    //     off-screen measurement stays CLOSE to the new height (variable content dominates
    //     the row), so keeping it beats reverting to the estimate and jumping the bar; only
    //     the off-screen scroll extent / scrollTo offset is approximate until those rows
    //     scroll in (the anchor pin keeps the rendered content put).
    private final IndexedHeightCache heightCache = new IndexedHeightCache();
    private boolean heightsDirty;
    // Tracks the fixedCellSize variable/fixed mode so a boundary crossing can drop the
    // now-stale height cache (the cache is dormant — its splice skipped — in fixed mode).
    private boolean lastVariable;

    private final ListChangeListener<T> itemsContentListener = this::onItemsContentChanged;
    private ObservableList<T> observedItems;

    private final RXIndexedFocusModel<T> focusModel;
    private MultipleSelectionModel<T> observedSelectionModel;
    private final ListChangeListener<Integer> selectionListener = change -> onSelectionChanged();
    private final ChangeListener<Number> selectedIndexListener = (obs, oldIndex, newIndex) -> syncFocusSelectionLead();
    private final ChangeListener<SelectionMode> selectionModeListener =
            (obs, oldMode, newMode) -> onSelectionModeChanged();

    private final StringBuilder typeAheadBuffer = new StringBuilder();
    private long lastTypeAheadTime;

    // Row-plan cache: the plan is width-independent (single column), so it only
    // changes when the revision (sections content or collapsed state), header flag,
    // header / row height, item count or section spacing change. The revision is
    // bumped on a sections change so a same-size regrouping still rebuilds, and on a
    // collapsed-state change (the key set or the collapsibleSections switch).
    private int rowPlanRevision;
    private RowPlanKey cachedRowPlanKey;
    private RXListRowPlan cachedRowPlan;

    private record RowPlanKey(int revision, boolean showHeaders, double headerHeight,
                              double rowHeight, int itemCount, double sectionSpacing) {
    }

    // The plan installed by the most recent layout pass — the geometry the current
    // scroll offset refers to until the next pass replaces it.
    private RXListRowPlan currentPlan;

    // Set by a collapsed-state change; the next layout pass anchors the view across it
    // (see anchorReflow).
    private boolean reflowAnchorPending;

    // Keyboard cursor on a section header (collapsible sections only): the section
    // index and its key (to find it again after a regrouping). While it is set the
    // focus model holds no item (-1); focusing an item clears it.
    private int cursorSection = -1;
    private Object cursorSectionKey;
    private int cursorSectionFirstItem = -1;
    // The keyboard's own scroll-into-view request (see scrollCursorIntoView).
    private boolean cursorScrollPending;

    /**
     * Creates the skin for the given list view.
     *
     * @param control the list view this skin is attached to
     */
    public RXListViewSkin(RXListView<T> control) {
        super(control);

        viewport = new RXListViewport<>(control);
        // This skin plans the rows and the viewport places them; both must snap, or not
        // snap, alike, so the viewport follows the control's setting.
        disposer.registerBinding(viewport.snapToPixelProperty(), control.snapToPixelProperty());
        getChildren().add(viewport);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setVisible(false);
        getChildren().add(placeholderRegion);

        focusModel = new RXIndexedFocusModel<>(control.itemsProperty(), control::getSelectionModel);
        viewport.setFocusModel(focusModel);
        viewport.setHeightSink(this::recordMeasuredHeight);
        // Mirror the keyboard cursor into the control's read-only focusedIndex;
        // it is skin-driven, so it resets when the skin is replaced.
        // No initial push: the control defaults to -1, a replaced skin's dispose
        // task resets it, and a fresh focus model always starts at -1 — pushing
        // here would clobber live state on the same-class setSkin short-circuit
        // (which constructs but never installs the new skin).
        disposer.registerListener(focusModel.focusedIndexProperty(),
                () -> control.setFocusedIndex(focusModel.getFocusedIndex()));
        disposer.registerDisposeTask(() -> control.setFocusedIndex(-1));
        lastVariable = isVariableHeight(control);

        attachItems(control.getItems());
        updatePlaceholder();
        registerListeners(control);
        // Initial sync: the property listener only fires on change, so honor a value
        // set before the skin existed (FXML / builder).
        viewport.setStickyEnabled(control.isStickySectionHeader());
        attachSelectionModel(control.getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        updateControlPseudoClasses();
        disposer.registerDisposeTask(this::detachSelectionModel);
    }

    private void registerListeners(RXListView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.cellFactoryProperty(), this::onCellFactoryChanged);
        disposer.registerListener(control.converterProperty(), this::onConverterChanged);
        disposer.registerListener(control.fixedCellSizeProperty(), this::onFixedCellSizeChanged);
        disposer.registerListener(control.estimatedCellSizeProperty(), this::requestLayoutPass);
        // Region re-lays out only the parent when snapToPixel changes; the viewport, whose
        // size need not change, would leave the rows where the old setting put them.
        disposer.registerListener(control.snapToPixelProperty(), this::requestLayoutPass);
        // Region lays nothing out when the render scale changes, and the scroll bar would
        // keep the breadth it measured at the old one, off the new pixel grid.
        RenderScaleWatch scaleWatch = new RenderScaleWatch(control, this::onRenderScaleChanged);
        disposer.registerDisposeTask(scaleWatch::dispose);
        disposer.registerListener(control.smoothScrollingProperty(), viewport::stopSmoothScrolling);
        disposer.registerListener(control.smoothScrollModeProperty(), viewport::resetSmoothScrolling);
        disposer.registerListener(control.placeholderProperty(), this::onPlaceholderChanged);
        // Sections: a sections-content change bumps the row-plan revision; the header
        // flag / heights / spacing flow through the row-plan key; a section-header
        // factory change rebuilds the header pool.
        disposer.registerListener(control.sectionsProperty(), this::onSectionsChanged);
        disposer.registerListener(control.showSectionHeadersProperty(), this::onShowSectionHeadersChanged);
        disposer.registerListener(control.sectionHeaderHeightProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionSpacingProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionHeaderFactoryProperty(), this::onSectionHeaderFactoryChanged);
        disposer.registerListener(control.stickySectionHeaderProperty(), this::onStickyChanged);
        disposer.registerListener(control.collapsibleSectionsProperty(), this::onCollapsedStateChanged);
        disposer.registerListener(control.getCollapsedSectionKeys(), this::onCollapsedStateChanged);
        // Visual mode: re-render the cells (their selection slot is derived) and
        // refresh the control-root pseudo-classes.
        disposer.registerListener(control.selectionVisualModeProperty(), this::onDecorationStateChanged);
        // Selection / focus: re-apply the per-cell state on change; re-wire on a
        // selection-model swap; install keyboard and mouse handlers on the control.
        disposer.registerListener(focusModel.focusedIndexProperty(), this::refreshSelectionAndFocus);
        disposer.registerListener(focusModel.focusedIndexProperty(), this::onFocusedIndexChanged);
        disposer.registerListener(control.selectionModelProperty(), this::onSelectionModelSwapped);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        disposer.registerDisposeTask(this::detachItems);
    }

    // Dirties the viewport (forcing a re-fill) and, by propagation, the control
    // (so the skin rebuilds the row plan and republishes the metrics).
    private void requestLayoutPass() {
        viewport.requestLayout();
    }

    private void onRenderScaleChanged() {
        viewport.remeasureScrollBar();
        requestLayoutPass();
    }

    // The converter re-sources the default cell's text, which can change a row's line
    // count (a multi-line string) and thus its height. In variable-height mode the old
    // measurements can be far off (not merely approximate), so drop them and let the rows
    // re-measure under the new converter. Rare (the converter is usually set once).
    private void onConverterChanged() {
        if (isVariableHeight(getSkinnable())) {
            heightCache.invalidateAllMeasured();
        }
        requestLayoutPass();
    }

    // A fixedCellSize change that crosses the variable/fixed boundary invalidates the
    // height cache: measured heights from the other mode no longer apply, and items may
    // have shifted while the cache lay dormant in fixed mode (where its splice is skipped).
    // Mirrors RXMasonryViewSkin.onHeightSourceChanged for the analogous source switch.
    private void onFixedCellSizeChanged() {
        boolean variable = isVariableHeight(getSkinnable());
        if (variable != lastVariable) {
            heightCache.clear();
            viewport.resetAnchor();
            lastVariable = variable;
        }
        requestLayoutPass();
    }

    // ==================== Items ====================

    private void onItemsListSwapped() {
        attachItems(getSkinnable().getItems());
        // The shift-range anchor referred to the previous list; drop it so a later
        // Shift+arrow / Shift+click starts a fresh range instead of a stale origin.
        resetAnchor();
        // Every index meaning is gone with the old list (selection anchor + height pin).
        viewport.resetAnchor();
        heightCache.clear();
        updatePlaceholder();
        viewport.requestLayout();
    }

    private void onItemsContentChanged(ListChangeListener.Change<? extends T> change) {
        resetAnchor();
        viewport.resetAnchor();
        // Keep the variable-height cache index-aligned with the spliced list.
        applyHeightCacheChange(change);
        updatePlaceholder();
        viewport.requestLayout();
    }

    private void attachItems(ObservableList<T> items) {
        detachItems();
        observedItems = items;
        if (items != null) {
            items.addListener(itemsContentListener);
        }
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(itemsContentListener);
            observedItems = null;
        }
    }

    // ==================== Refresh paths ====================

    private void onCellFactoryChanged() {
        // New cells produce new heights, so every cached measured height is stale.
        heightCache.clear();
        // recreateCells() discards the pool and requests a viewport layout.
        viewport.recreateCells();
    }

    private void onSectionsChanged() {
        // The sections list content changed; the row-plan key cannot cheaply hash it,
        // so a revision bump forces a rebuild.
        rowPlanRevision++;
        relocateHeaderCursor();
        requestLayoutPass();
    }

    private void onCollapsedStateChanged() {
        reflowAnchorPending = true;
        // The collapsed flags are read from the key set when the plan is built; the
        // revision bump makes the cached fixed-mode plan miss.
        rowPlanRevision++;
        repairCursor();
        requestLayoutPass();
    }

    private void onShowSectionHeadersChanged() {
        // Showing or hiding the headers also switches collapsing on or off.
        RXListView<T> control = getSkinnable();
        if (control.isCollapsibleSections() && !control.getCollapsedSectionKeys().isEmpty()) {
            reflowAnchorPending = true;
        }
        repairCursor();
        requestLayoutPass();
    }

    // ==================== Keyboard cursor ====================

    // The cursor is either on an item (the focus model's index) or on a header row
    // (cursorSection); header rows only hold it while they are navigable.
    private boolean headersNavigable() {
        RXListView<T> control = getSkinnable();
        return control.isCollapsibleSections() && control.isShowSectionHeaders() && !control.getSections().isEmpty();
    }

    private void onFocusedIndexChanged() {
        if (focusModel.getFocusedIndex() >= 0 && cursorSection >= 0) {
            setHeaderCursor(-1);
        }
    }

    private void focusHeader(int sectionIndex) {
        focusModel.focus(-1);
        setHeaderCursor(sectionIndex);
    }

    private void setHeaderCursor(int sectionIndex) {
        List<RXListSection> sections = getSkinnable().getSections();
        boolean valid = sectionIndex >= 0 && sectionIndex < sections.size();
        cursorSection = valid ? sectionIndex : -1;
        cursorSectionKey = valid ? sections.get(sectionIndex).key() : null;
        cursorSectionFirstItem = valid ? sections.get(sectionIndex).firstItemIndex() : -1;
        viewport.setFocusedHeaderSection(cursorSection);
    }

    // After a regrouping the header cursor follows its key. When the view turned flat
    // (no sections left, the items unchanged) it moves to its section's first item;
    // when the key is simply gone the cursor is lost.
    private void relocateHeaderCursor() {
        if (cursorSection < 0) {
            return;
        }
        List<RXListSection> sections = getSkinnable().getSections();
        int index = resolveSection(sections, cursorSection, cursorSectionKey);
        if (index < 0 && sections.isEmpty() && cursorSectionFirstItem < itemCount()) {
            int item = cursorSectionFirstItem;
            setHeaderCursor(-1);
            focusModel.focus(item);
            return;
        }
        setHeaderCursor(index);
        repairCursor();
    }

    // A section remembered by index and key, in the given sections: the same index
    // while it still carries the key, else the first section with the key, else -1.
    private static int resolveSection(List<RXListSection> sections, int index, Object key) {
        if (index >= 0 && index < sections.size() && Objects.equals(sections.get(index).key(), key)) {
            return index;
        }
        for (RXListSection section : sections) {
            if (Objects.equals(section.key(), key)) {
                return section.sectionIndex();
            }
        }
        return -1;
    }

    // Keeps the cursor on a shown row: an item that became hidden hands the cursor to
    // its section's header (always shown while anything is hidden); a header that is
    // no longer navigable hands it to its section's first item (shown, since nothing
    // collapses then).
    private void repairCursor() {
        if (cursorSection >= 0) {
            if (!headersNavigable()) {
                List<RXListSection> sections = getSkinnable().getSections();
                int item = cursorSection < sections.size() ? sections.get(cursorSection).firstItemIndex() : -1;
                setHeaderCursor(-1);
                focusModel.focus(item);
            }
            return;
        }
        int focus = focusModel.getFocusedIndex();
        int section = sectionIndexOfItem(focus);
        if (section >= 0 && isSectionLaidOutCollapsed(section)) {
            focusHeader(section);
        }
    }

    // Whether the section is collapsed in the layout right now (switch on, headers
    // shown, key recorded) — independent of the last built plan.
    private boolean isSectionLaidOutCollapsed(int sectionIndex) {
        RXListView<T> control = getSkinnable();
        List<RXListSection> sections = control.getSections();
        return headersNavigable() && sectionIndex >= 0 && sectionIndex < sections.size()
                && control.getCollapsedSectionKeys().contains(sections.get(sectionIndex).key());
    }

    // The section holding the item, by binary search over the live sections; -1 when
    // flat or out of range.
    private int sectionIndexOfItem(int itemIndex) {
        return RXListRowPlan.findSectionOfItem(getSkinnable().getSections(), itemIndex);
    }

    private void toggleSection(int sectionIndex) {
        RXListView<T> control = getSkinnable();
        Object key = control.getSections().get(sectionIndex).key();
        control.setSectionExpanded(key, !control.isSectionExpanded(key));
    }

    // ==================== Reflow anchor ====================

    // A collapsed-state change keeps the content at the top of the viewport put,
    // identically in fixed and variable height mode: the section S at the top keeps
    // the viewport's offset inside it (so toggling a section above it, or expanding S
    // itself, does not move what is on screen), except that collapsing S aligns its
    // header to the top — S's items vanish, and keeping the offset would land the
    // view in an unrelated section further down.
    //
    // Runs at the start of the layout pass, before the new plan replaces currentPlan,
    // so the anchor is read from the geometry the live scroll offset refers to (any
    // wheel / bar scroll since the change included). S is identified by key and by its
    // occurrence among same-key runs, so a filter applied in the same pulse that
    // renumbers the sections still finds it; when S is gone the offset is left alone.
    private void anchorReflow() {
        RXListRowPlan oldPlan = currentPlan;
        reflowAnchorPending = false;
        if (oldPlan == null || oldPlan.sectionCount() == 0 || oldPlan.totalVisualRows() == 0) {
            updateRowPlan();
            return;
        }
        double scrollY = viewport.scrollOffset();
        int oldIndex = oldPlan.sectionIndexAt(scrollY);
        Object key = oldPlan.section(oldIndex).key();
        int occurrence = keyOccurrence(oldPlan, oldIndex);
        double offset = scrollY - oldPlan.sectionTop(oldIndex);
        boolean wasCollapsed = oldPlan.sectionCollapsed(oldIndex);

        updateRowPlan();
        RXListRowPlan plan = currentPlan;
        int index = sectionWithKey(plan, key, occurrence);
        if (index < 0) {
            return;
        }
        double top = plan.sectionTop(index);
        boolean collapseHidSection = plan.sectionCollapsed(index) && !wasCollapsed;
        // A filter can shrink S below the offset; then show S from its header.
        boolean offsetOutside = offset >= sectionExtent(plan, index);
        viewport.applyReflowScroll(collapseHidSection || offsetOutside ? top : top + offset);
    }

    // How many sections before sectionIndex share its key.
    private static int keyOccurrence(RXListRowPlan plan, int sectionIndex) {
        Object key = plan.section(sectionIndex).key();
        int occurrence = 0;
        for (int s = 0; s < sectionIndex; s++) {
            if (Objects.equals(plan.section(s).key(), key)) {
                occurrence++;
            }
        }
        return occurrence;
    }

    // The index of the occurrence-th section with the given key, or -1.
    private static int sectionWithKey(RXListRowPlan plan, Object key, int occurrence) {
        int seen = 0;
        for (int s = 0; s < plan.sectionCount(); s++) {
            if (Objects.equals(plan.section(s).key(), key) && seen++ == occurrence) {
                return s;
            }
        }
        return -1;
    }

    // The content-space height a section occupies, up to the next section's top.
    private static double sectionExtent(RXListRowPlan plan, int sectionIndex) {
        double end = sectionIndex + 1 < plan.sectionCount()
                ? plan.sectionTop(sectionIndex + 1) : plan.contentHeight();
        return end - plan.sectionTop(sectionIndex);
    }

    private void onSectionHeaderFactoryChanged() {
        // recreateHeaders() discards the header pool and requests a viewport layout.
        viewport.recreateHeaders();
    }

    private void onStickyChanged() {
        // Adds or removes the pinned sticky header and requests a viewport layout.
        viewport.setStickyEnabled(getSkinnable().isStickySectionHeader());
    }

    private void onPlaceholderChanged() {
        updatePlaceholder();
        viewport.requestLayout();
    }

    // ==================== Row plan ====================

    private void updateRowPlan() {
        RXListView<T> control = getSkinnable();
        RXListRowPlan plan = buildPlan();
        // Visual rows (data rows plus shown section-header rows), matching the
        // tile view's rowCount so the family metric reads the same everywhere.
        if (control.getRowCount() != plan.totalVisualRows()) {
            control.setRowCount(plan.totalVisualRows());
        }
        currentPlan = plan;
        viewport.setRowPlan(plan);
    }

    // The fixed-mode plan is purely a function of the sections + resolved heights + item
    // count (width-independent), so a single cached slot keyed on those reuses it across
    // scroll passes and rebuilds only when something it depends on changes. Variable mode
    // reads mutable per-item heights, so it bypasses the cache (see buildVariablePlan).
    private RXListRowPlan buildPlan() {
        RXListView<T> control = getSkinnable();
        if (isVariableHeight(control)) {
            return buildVariablePlan(control);
        }
        RowPlanKey key = new RowPlanKey(rowPlanRevision, control.isShowSectionHeaders(),
                snapSizeY(sectionHeaderHeightOrDefault(control)), snapSizeY(fixedCellSizeOrDefault(control)),
                itemCount(), snapSizeY(sectionSpacingOrZero(control.getSectionSpacing())));
        if (key.equals(cachedRowPlanKey)) {
            return cachedRowPlan;
        }
        RXListRowPlan plan = RXListRowPlan.fixed(control.getSections(), control.isShowSectionHeaders(),
                key.headerHeight(), key.rowHeight(), key.itemCount(), key.sectionSpacing(),
                collapsedFlags(control));
        cachedRowPlanKey = key;
        cachedRowPlan = plan;
        return plan;
    }

    // Per-section collapsed flags for the plan, or null when nothing can collapse. The
    // plan itself ignores them while headers are hidden.
    private static boolean[] collapsedFlags(RXListView<?> control) {
        Set<Object> collapsedKeys = control.getCollapsedSectionKeys();
        if (!control.isCollapsibleSections() || collapsedKeys.isEmpty()) {
            return null;
        }
        List<RXListSection> sections = control.getSections();
        boolean[] flags = new boolean[sections.size()];
        for (int s = 0; s < flags.length; s++) {
            flags[s] = collapsedKeys.contains(sections.get(s).key());
        }
        return flags;
    }

    // Variable mode: heights come from the cache (measured or estimated). The plan is
    // rebuilt fresh each pass because those heights are mutable — the converge loop relies
    // on rebuilding from the just-updated cache, and a pure scroll pays the O(items)
    // prefix-sum rebuild that variable rows inherently cost (opt-in via fixedCellSize <= 0).
    private RXListRowPlan buildVariablePlan(RXListView<T> control) {
        int count = itemCount();
        // Unsnapped like a measured height: each is snapped once below, and snapped twice
        // JavaFX can ceil a height a device pixel further than a measured row's.
        double estimated = estimatedCellSizeOrDefault(control);
        heightCache.ensureCapacity(count, estimated);
        double[] heights = new double[count];
        for (int i = 0; i < count; i++) {
            heights[i] = snapSizeY(heightCache.heightAt(i, estimated));
        }
        return RXListRowPlan.variable(control.getSections(), control.isShowSectionHeaders(),
                snapSizeY(sectionHeaderHeightOrDefault(control)), heights,
                snapSizeY(sectionSpacingOrZero(control.getSectionSpacing())), collapsedFlags(control));
    }

    private RXListSelectionVisualMode effectiveSelectionVisualMode() {
        RXListView<T> control = getSkinnable();
        return RXListSelectionVisualMode.resolve(control.getSelectionVisualMode(), control.getSelectionMode());
    }

    // A visual-mode change re-renders the (derived) cell selection slots and
    // refreshes the control-root pseudo-classes used by the stylesheet.
    private void onDecorationStateChanged() {
        updateControlPseudoClasses();
        requestLayoutPass();
    }

    private void updateControlPseudoClasses() {
        RXListView<T> control = getSkinnable();
        RXListSelectionVisualMode mode = effectiveSelectionVisualMode();
        control.pseudoClassStateChanged(SELECTION_ROW_PSEUDO_CLASS, mode == RXListSelectionVisualMode.ROW);
        control.pseudoClassStateChanged(SELECTION_CHECKMARK_PSEUDO_CLASS, mode == RXListSelectionVisualMode.CHECKMARK);
        control.pseudoClassStateChanged(SELECTION_CHECKBOX_PSEUDO_CLASS, mode == RXListSelectionVisualMode.CHECKBOX);
    }

    // ==================== Visible range ====================

    private void updateVisibleRange() {
        RXListView<T> control = getSkinnable();
        int firstIndex = viewport.getVisibleFirstIndex();
        int lastIndex = viewport.getVisibleLastIndex();
        RXListRowPlan plan = currentPlan;
        int firstRow = plan == null ? -1 : plan.dataRowOfItem(firstIndex);
        int lastRow = plan == null ? -1 : plan.dataRowOfItem(lastIndex);
        if (firstIndex < 0 || lastIndex < 0 || firstRow < 0 || lastRow < 0) {
            setVisibleRangeIfChanged(control, RXListVisibleRange.EMPTY);
            return;
        }
        // Rows are the shown data rows: contiguous even when a collapsed section's
        // hidden items lie between the item bounds.
        setVisibleRangeIfChanged(control, new RXListVisibleRange(firstIndex, lastIndex,
                firstRow, lastRow, lastRow - firstRow + 1));
    }

    private void setVisibleRangeIfChanged(RXListView<T> control, RXListVisibleRange range) {
        if (!range.equals(control.getVisibleRange())) {
            control.setVisibleRange(range);
        }
    }

    // ==================== Visible section ====================

    private void updateVisibleSection() {
        // The viewport reports the section at the top of the window (null when flat).
        getSkinnable().setVisibleSection(viewport.getTopSection());
    }

    // ==================== Placeholder / :empty ====================

    private void updatePlaceholder() {
        RXListView<T> control = getSkinnable();
        boolean empty = itemCount() == 0;
        control.pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, empty);

        Node placeholder = control.getPlaceholder();
        boolean showPlaceholder = empty && placeholder != null;
        if (showPlaceholder) {
            placeholderRegion.getChildren().setAll(placeholder);
        } else {
            placeholderRegion.getChildren().clear();
        }
        placeholderRegion.setVisible(showPlaceholder);
        viewport.setVisible(!showPlaceholder);
    }

    // ==================== Scrolling ====================

    // The keyboard scrolls the cursor into view through this skin-internal request,
    // never through the control's public scrollTo (which expands a collapsed section
    // to reveal its target). Setting it drops any earlier control request, so a
    // control request still pending at layout time was issued later and wins —
    // the same latest-wins rule as the control's single request slot.
    private void scrollCursorIntoView() {
        getSkinnable().clearPendingScroll();
        cursorScrollPending = true;
        getSkinnable().requestLayout();
    }

    private void consumePendingScroll() {
        RXListView<T> control = getSkinnable();
        if (!control.hasPendingScroll()) {
            consumeCursorScroll();
            return;
        }
        cursorScrollPending = false;
        int itemCount = itemCount();
        if (itemCount == 0) {
            control.clearPendingScroll();
            return;
        }
        int sectionIndex = control.getPendingScrollSectionIndex();
        if (sectionIndex >= 0) {
            // Section scroll takes priority; a stale section index (cleared sections)
            // is dropped rather than left armed.
            if (sectionIndex >= control.getSections().size()
                    || viewport.scrollToSectionIndex(sectionIndex, control.getPendingScrollAlignment())) {
                control.clearPendingScroll();
            }
            return;
        }
        int index = control.getPendingScrollIndex();
        if (index >= 0) {
            int clamped = RXMath.clamp(index, 0, itemCount - 1);
            // Clear only when the request was actually applied. On a zero-height pass
            // scrollToIndex cannot compute geometry and returns false; keeping the
            // request armed lets the first sized pass honor it.
            if (viewport.scrollToIndex(clamped, control.getPendingScrollAlignment())) {
                control.clearPendingScroll();
            }
            return;
        }
        if (viewport.scrollByPixels(control.getPendingScrollDelta())) {
            control.clearPendingScroll();
        }
    }

    // Scrolls to wherever the cursor is now (an item or a header row), the minimum
    // distance. Kept armed until the viewport has a height.
    private void consumeCursorScroll() {
        if (!cursorScrollPending) {
            return;
        }
        boolean applied;
        int focus = focusModel.getFocusedIndex();
        if (cursorSection >= 0) {
            applied = viewport.scrollToSectionHeader(cursorSection);
        } else if (focus >= 0 && focus < itemCount()) {
            applied = viewport.scrollToIndex(focus, ScrollAlignment.NEAREST);
        } else {
            applied = true;
        }
        if (applied) {
            cursorScrollPending = false;
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        RXListView<T> control = getSkinnable();
        double rightInset = Math.max(0.0, control.getWidth() - contentX - contentWidth);
        double bottomInset = Math.max(0.0, control.getHeight() - contentY - contentHeight);
        viewport.setChromeInsets(contentX, contentY, rightInset, bottomInset);
        // An item mutation can leave the focus model on an item hidden in a collapsed
        // section (it re-targets focus at the end of the items listener chain, after
        // this skin's listener ran); move the cursor to that section's header.
        repairCursor();
        // Sized first: the anchor clamps its offset against the viewport height.
        viewport.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        placeholderRegion.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        // Both paths install the new plan; the anchor runs before the pending scroll so
        // an explicit scrollTo issued with the change wins.
        if (reflowAnchorPending) {
            anchorReflow();
        } else {
            updateRowPlan();
        }
        consumePendingScroll();
        boolean variable = isVariableHeight(control);
        viewport.setMeasureGate(variable);
        heightsDirty = false;
        // Force the viewport to realize cells now so the scroll request and the published
        // visible range reflect this pass, not the previous one. On the variable path the
        // realized cells are also measured; a changed height re-packs within this pass.
        viewport.layout();
        if (variable) {
            convergeVariableHeights();
        }
        updateVisibleRange();
        updateVisibleSection();
    }

    // A measured height differing from the placed (estimated) one re-packs the layout.
    // Converge WITHIN this pass: rebuild the plan from the updated cache, re-fill and
    // re-measure, looping until nothing changes (a requestLayout issued from inside
    // layoutChildren is not reliably honored). A pass that measures a NEW item is progress
    // (resets the stall counter), so a genuine multi-step convergence runs to completion;
    // a pass that only re-flips known heights counts toward the cap so a non-deterministic-
    // height cell cannot spin forever. The viewport's anchor pin keeps the top visible item
    // put across each re-pack.
    private void convergeVariableHeights() {
        if (!heightsDirty) {
            return;
        }
        int lastMeasured = heightCache.measuredCount();
        int stalled = 0;
        while (heightsDirty && stalled < MAX_STALLED_REPACK_PASSES) {
            heightsDirty = false;
            updateRowPlan();
            viewport.requestLayout();
            viewport.layout();
            int measured = heightCache.measuredCount();
            stalled = measured > lastMeasured ? 0 : stalled + 1;
            lastMeasured = measured;
        }
    }

    // The variable-height measure sink (installed on the viewport): a changed measured
    // height marks the cache dirty so layoutChildren re-packs within the pass.
    private void recordMeasuredHeight(int index, double measuredHeight) {
        if (!isVariableHeight(getSkinnable()) || !Double.isFinite(measuredHeight) || measuredHeight < 0.0) {
            return;
        }
        // The viewport reports only a height that differs on the current grid from the one
        // placed, so the cache takes it as it is: compared unsnapped with the stored height,
        // a change that crosses a pixel boundary can be small enough to be dropped.
        if (heightCache.record(index, measuredHeight, 0.0)) {
            heightsDirty = true;
        }
    }

    // Keep the variable-height cache index-aligned with the list (mirrors the index shift
    // in the selection / focus models); a no-op on the fixed path.
    private void applyHeightCacheChange(ListChangeListener.Change<? extends T> change) {
        if (!isVariableHeight(getSkinnable())) {
            return;
        }
        double estimated = estimatedCellSizeOrDefault(getSkinnable());
        while (change.next()) {
            if (change.wasPermutated() || change.wasUpdated()) {
                // Reordered or changed in place: the stored heights no longer describe the
                // range, so drop them and re-measure.
                heightCache.invalidateRange(change.getFrom(), change.getTo(), estimated);
            } else {
                heightCache.shift(change.getFrom(), change.getRemovedSize(), change.getAddedSize(), estimated);
            }
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_CONTENT_WIDTH + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXListView<T> control = getSkinnable();
        double rowEstimate = isVariableHeight(control)
                ? estimatedCellSizeOrDefault(control) : fixedCellSizeOrDefault(control);
        return topInset + DEFAULT_VISIBLE_ROW_COUNT * rowEstimate + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + MIN_VIEWPORT_CONTENT_WIDTH + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected void disposeSkin() {
        // detachItems / detachSelectionModel run via the disposer (registerDisposeTask).
        viewport.dispose();
        placeholderRegion.getChildren().clear();
        // JFX does not remove a replaced SkinBase skin's nodes, and a replacement skin
        // has already added its own: remove exactly ours, or the old viewport stays in
        // the control and keeps laying out (and re-creating cells) under the new one.
        getChildren().removeAll(viewport, placeholderRegion);
    }

    // ==================== Selection model wiring ====================

    // Routes through the viewport; defined as a method so field initializers and
    // listeners can reference it before the viewport field is assigned.
    private void refreshSelectionAndFocus() {
        viewport.refreshSelectionAndFocus();
    }

    private void onSelectionChanged() {
        syncFocusSelectionLead();
        refreshSelectionAndFocus();
    }

    private void syncFocusSelectionLead() {
        if (!RXIndexedSelectionMutationGuard.isActive(observedSelectionModel)) {
            focusModel.syncSelectionLeadState();
        }
    }

    private void onSelectionModelSwapped() {
        resetAnchor();
        attachSelectionModel(getSkinnable().getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        refreshSelectionAndFocus();
        // The new model's cardinality may change the AUTO visual mode.
        updateControlPseudoClasses();
        getSkinnable().requestLayout();
    }

    private void attachSelectionModel(MultipleSelectionModel<T> model) {
        detachSelectionModel();
        observedSelectionModel = model;
        if (model != null) {
            model.getSelectedIndices().addListener(selectionListener);
            model.selectedIndexProperty().addListener(selectedIndexListener);
            model.selectionModeProperty().addListener(selectionModeListener);
        }
    }

    private void onSelectionModeChanged() {
        resetAnchor();
        // The cardinality drives the AUTO visual mode, so refresh the pseudo-classes
        // and re-render the cells.
        updateControlPseudoClasses();
        requestLayoutPass();
    }

    private void detachSelectionModel() {
        if (observedSelectionModel != null) {
            observedSelectionModel.getSelectedIndices().removeListener(selectionListener);
            observedSelectionModel.selectedIndexProperty().removeListener(selectedIndexListener);
            observedSelectionModel.selectionModeProperty().removeListener(selectionModeListener);
            observedSelectionModel = null;
        }
    }

    // ==================== Anchor ====================

    private void setAnchor(int index) {
        getSkinnable().getProperties().put(ANCHOR_KEY, index);
    }

    private int getAnchor() {
        Object anchor = getSkinnable().getProperties().get(ANCHOR_KEY);
        return anchor instanceof Integer value ? value : focusModel.getFocusedIndex();
    }

    private void resetAnchor() {
        getSkinnable().getProperties().remove(ANCHOR_KEY);
    }

    // The Shift-range origin for a move to target: the stored anchor, else the
    // focused item. With the cursor on a header and nothing stored the range starts
    // at the target itself — the header is not an item to range from.
    private int rangeAnchor(int target) {
        boolean stored = getSkinnable().getProperties().get(ANCHOR_KEY) instanceof Integer;
        int anchor = !stored && cursorSection >= 0 ? target : getAnchor();
        return clampIndex(anchor, itemCount());
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        RXListView<T> control = getSkinnable();
        // A null selection model disables selection, not the keyboard: navigation,
        // Enter activation and type-ahead still run (selection updates are skipped
        // at their use sites), mirroring the mouse side where double-click stays live.
        MultipleSelectionModel<T> sm = control.getSelectionModel();
        int itemCount = itemCount();
        if (itemCount == 0) {
            return;
        }
        // An item mutation since the last layout pass may have left focus on a hidden
        // item; repair before any key acts on it.
        repairCursor();
        int focus = focusModel.getFocusedIndex();
        boolean shift = event.isShiftDown();
        boolean shortcut = event.isShortcutDown();
        KeyCode code = event.getCode();
        // Select-all shortcut first, so Ctrl/Cmd+A is not consumed by type-ahead.
        if (shortcut && code == KeyCode.A) {
            if (sm != null) {
                sm.selectAll();
                focusModel.syncSelectionLeadState();
            }
            event.consume();
            return;
        }
        // Collapsible sections navigate visual rows (headers included); everything
        // that path does not handle falls through to the item keys below.
        if (headersNavigable() && handleRowKey(event, shift, shortcut)) {
            event.consume();
            return;
        }
        switch (code) {
            case UP, KP_UP -> consume(event, () -> verticalArrow(focus, -1, shift, shortcut));
            case DOWN, KP_DOWN -> consume(event, () -> verticalArrow(focus, 1, shift, shortcut));
            case HOME -> consume(event, () -> moveTo(0, shift, shortcut));
            case END -> consume(event, () -> moveTo(itemCount - 1, shift, shortcut));
            case PAGE_UP -> consume(event, () -> page(-1, shift, shortcut));
            case PAGE_DOWN -> consume(event, () -> page(1, shift, shortcut));
            case SPACE -> {
                if (focus >= 0) {
                    toggleFocused(focus);
                    event.consume();
                }
            }
            case ENTER -> {
                if (focus >= 0) {
                    activate(focus);
                    event.consume();
                }
            }
            default -> {
                if (isTypeAheadKey(event)) {
                    typeAhead(event.getText());
                    event.consume();
                }
            }
        }
    }

    private static void consume(KeyEvent event, Runnable action) {
        action.run();
        event.consume();
    }

    // ==================== Keyboard: collapsible sections ====================

    // With navigable headers the cursor moves over visual rows — header rows and the
    // shown items — so hidden items are skipped by construction. Returns whether the
    // key was handled.
    private boolean handleRowKey(KeyEvent event, boolean shift, boolean shortcut) {
        RXListRowPlan plan = structurePlan();
        if (plan.totalVisualRows() == 0) {
            return false;
        }
        switch (event.getCode()) {
            case UP, KP_UP -> rowStep(plan, -1, shift, shortcut);
            case DOWN, KP_DOWN -> rowStep(plan, 1, shift, shortcut);
            case HOME -> moveToRow(plan, edgeRow(plan, true), shift, shortcut);
            case END -> moveToRow(plan, edgeRow(plan, false), shift, shortcut);
            case PAGE_UP -> rowPage(plan, -1, shift, shortcut);
            case PAGE_DOWN -> rowPage(plan, 1, shift, shortcut);
            case LEFT, KP_LEFT, RIGHT, KP_RIGHT -> horizontalArrow(event.getCode(), shift, shortcut);
            case SPACE, ENTER -> {
                if (cursorSection < 0) {
                    return false;
                }
                toggleSection(cursorSection);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // The row structure of the current state (sections, header flag, collapsed keys)
    // with unit heights: the navigation only needs row order, never pixel geometry,
    // and building it is O(sections) — it does not wait for the next layout pass.
    private RXListRowPlan structurePlan() {
        RXListView<T> control = getSkinnable();
        return RXListRowPlan.fixed(control.getSections(), control.isShowSectionHeaders(), 1.0, 1.0,
                itemCount(), 0.0, collapsedFlags(control));
    }

    // The cursor's visual row, or -1 when there is no cursor. A focused item that is
    // hidden (not yet repaired) counts as its section's header.
    private int cursorRow(RXListRowPlan plan) {
        if (cursorSection >= 0) {
            return plan.visualRowOfSection(cursorSection);
        }
        int focus = focusModel.getFocusedIndex();
        if (focus < 0 || focus >= plan.itemCount()) {
            return -1;
        }
        int row = plan.visualRowOfItem(focus);
        return row >= 0 ? row : plan.visualRowOfSection(plan.sectionIndexOfItem(focus));
    }

    // The first (forward) or last shown item's row; the first / last header row when
    // every section is collapsed. Also the first step when there is no cursor yet.
    private static int edgeRow(RXListRowPlan plan, boolean forward) {
        int dataRows = plan.dataRowCount();
        if (dataRows > 0) {
            return plan.visualRowOfItem(plan.itemOfDataRow(forward ? 0 : dataRows - 1));
        }
        return forward ? 0 : plan.totalVisualRows() - 1;
    }

    private void rowStep(RXListRowPlan plan, int direction, boolean shift, boolean shortcut) {
        int row = cursorRow(plan);
        if (row < 0) {
            moveToRow(plan, edgeRow(plan, direction > 0), shift, shortcut);
            return;
        }
        int target = RXMath.clamp(row + direction, 0, plan.totalVisualRows() - 1);
        if (target != row) {
            moveToRow(plan, target, shift, shortcut);
        }
    }

    // The row-wise "sink then page" of page(): first to the realized window's edge
    // row, then by the window's height in rows. Headers count as rows here.
    private void rowPage(RXListRowPlan plan, int direction, boolean shift, boolean shortcut) {
        int row = cursorRow(plan);
        if (row < 0) {
            moveToRow(plan, edgeRow(plan, direction > 0), shift, shortcut);
            return;
        }
        int lastRow = plan.totalVisualRows() - 1;
        int top = viewport.getVisibleFirstRow();
        int bottom = viewport.getVisibleLastRow();
        if (top < 0 || bottom < 0) {
            top = row;
            bottom = row;
        }
        top = RXMath.clamp(top, 0, lastRow);
        bottom = RXMath.clamp(bottom, top, lastRow);
        int step = Math.max(1, bottom - top);
        int target;
        if (direction > 0) {
            target = row < bottom ? bottom : Math.min(lastRow, row + step);
        } else {
            target = row > top ? top : Math.max(0, row - step);
        }
        if (target != row) {
            moveToRow(plan, target, shift, shortcut);
        }
    }

    private void moveToRow(RXListRowPlan plan, int row, boolean shift, boolean shortcut) {
        RXListRowPlan.RowInfo info = plan.rowInfo(row);
        if (info.header()) {
            moveToHeader(info.section().sectionIndex());
        } else {
            moveTo(info.itemIndex(), shift, shortcut);
        }
    }

    // A header is not selectable: the cursor moves, the selection and the range
    // anchor stay. An anchor that was only implied by the focused item is stored
    // first, so a Shift range continued past the header still starts there.
    private void moveToHeader(int sectionIndex) {
        int focus = focusModel.getFocusedIndex();
        if (focus >= 0 && !(getSkinnable().getProperties().get(ANCHOR_KEY) instanceof Integer)) {
            setAnchor(focus);
        }
        focusHeader(sectionIndex);
        focusModel.syncSelectionLeadState();
        scrollCursorIntoView();
    }

    // Tree-style left / right (mirrored in RTL, like TreeView): on a header the
    // collapse key collapses and the expand key expands, or enters the first item
    // when already expanded; on an item the collapse key moves to its header.
    private void horizontalArrow(KeyCode code, boolean shift, boolean shortcut) {
        RXListView<T> control = getSkinnable();
        boolean left = code == KeyCode.LEFT || code == KeyCode.KP_LEFT;
        boolean rtl = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        boolean collapseKey = left != rtl;
        if (cursorSection >= 0) {
            RXListSection section = control.getSections().get(cursorSection);
            boolean collapsed = isSectionLaidOutCollapsed(cursorSection);
            if (collapseKey) {
                if (!collapsed) {
                    control.setSectionExpanded(section.key(), false);
                }
            } else if (collapsed) {
                control.setSectionExpanded(section.key(), true);
            } else {
                moveTo(section.firstItemIndex(), shift, shortcut);
            }
            return;
        }
        int section = sectionIndexOfItem(focusModel.getFocusedIndex());
        if (collapseKey && section >= 0) {
            moveToHeader(section);
        }
    }

    private void verticalArrow(int focus, int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        if (focus < 0) {
            moveTo(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        int target = clampIndex(focus + direction, itemCount);
        if (target == focus) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    // PageUp/Down follow ListView's "sink then page": the first press moves the
    // focus to the item at the viewport's bottom (PageDown) / top (PageUp) visible
    // row without scrolling past it; only once the focus already sits on that edge
    // does a press page by a viewport-height of rows.
    private void page(int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int focus = focusModel.getFocusedIndex();
        if (focus < 0) {
            moveTo(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        RXListVisibleRange range = getSkinnable().getVisibleRange();
        int visibleRows = range.isEmpty() ? 1 : Math.max(1, range.lastIndex() - range.firstIndex() + 1);
        int pageStep = Math.max(1, visibleRows - 1);
        int target;
        if (direction > 0) {
            int bottom = range.isEmpty() ? focus : range.lastIndex();
            target = focus < bottom ? bottom : Math.min(itemCount - 1, focus + pageStep);
        } else {
            int top = range.isEmpty() ? focus : range.firstIndex();
            target = focus > top ? top : Math.max(0, focus - pageStep);
        }
        if (target == focus) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    // Navigation works on item indices and the focus model; selection follows the
    // modifier keys and the visual mode: Shortcut = move focus only; Shift = extend
    // range from the anchor; a plain arrow selects in ROW / single-selection
    // (highlight follows the cursor) but only moves focus in an accumulate mode
    // (checkbox / checkmark multiple), so arrowing never wipes the accumulated
    // selection — Space toggles there instead.
    private void moveTo(int target, boolean shift, boolean shortcut) {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        // Without a selection model navigation still moves the keyboard focus and
        // scrolls; only the selection updates are skipped.
        if (sm == null) {
            focusModel.focus(target);
            setAnchor(target);
            scrollCursorIntoView();
            return;
        }
        // Capture the range anchor from the CURRENT focus before moving it.
        // getAnchor() falls back to the focused index, so reading it after
        // focus(target) would collapse a Shift-extend to a single row whenever no
        // explicit anchor is stored (mirrors CellBehaviorBase, which records the
        // anchor before fm.focus).
        int anchor = rangeAnchor(target);
        boolean accumulate = isAccumulateMode();
        focusModel.focus(target);
        if (shortcut) {
            // Move focus only; selection is unchanged. Shortcut wins over Shift,
            // matching the pointer path and the sibling views.
            setAnchor(target);
        } else if (shift && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            // Persist the fallback anchor so consecutive Shift-extends keep growing
            // from the same origin (mirrors ListViewBehavior.alsoSelectNextRow).
            setAnchor(anchor);
            clearAndSelectRange(sm, anchor, target);
        } else if (accumulate) {
            // Move focus only; selection is unchanged.
            setAnchor(target);
        } else {
            setAnchor(target);
            sm.clearAndSelect(target);
        }
        focusModel.syncSelectionLeadState();
        scrollCursorIntoView();
    }

    private void toggleFocused(int focus) {
        if (focus < 0) {
            return;
        }
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null) {
            return;
        }
        // Space toggles in multiple selection; in single selection it (re)selects
        // and never empties, matching the sibling views.
        if (sm.getSelectionMode() == SelectionMode.SINGLE) {
            sm.clearAndSelect(focus);
        } else {
            toggleAt(sm, focus);
        }
        setAnchor(focus);
        focusModel.syncSelectionLeadState();
    }

    private void activate(int index) {
        RXListView<T> control = getSkinnable();
        if (index < 0 || index >= itemCount()) {
            return;
        }
        T item = control.getItems().get(index);
        control.fireEvent(new RXListViewActionEvent<>(control, item, index));
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
            // Pressing the same letter again cycles among its matches (start = focus+1)
            // rather than growing the prefix, which would stop matching.
            typeAheadBuffer.setLength(0);
        }
        typeAheadBuffer.append(lower);
        int match = findTypeAheadMatch(typeAheadBuffer.toString());
        if (match >= 0) {
            moveTo(match, false, false);
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
        int itemCount = itemCount();
        if (itemCount == 0) {
            return -1;
        }
        // A single-character burst cycles to the next match; a growing buffer
        // refines from the current focus. A cursor on a header searches from its
        // section's first item; items hidden in collapsed sections never match.
        int focus = focusModel.getFocusedIndex();
        int start;
        if (cursorSection >= 0) {
            start = getSkinnable().getSections().get(cursorSection).firstItemIndex();
        } else {
            start = prefixLower.length() <= 1 ? focus + 1 : Math.max(0, focus);
        }
        boolean mayHide = headersNavigable();
        for (int i = 0; i < itemCount; i++) {
            int index = Math.floorMod(start + i, itemCount);
            if (mayHide && isItemHidden(index)) {
                continue;
            }
            if (cellText(index).toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isItemHidden(int itemIndex) {
        return isSectionLaidOutCollapsed(sectionIndexOfItem(itemIndex));
    }

    private String cellText(int index) {
        RXListView<T> control = getSkinnable();
        T item = control.getItems().get(index);
        StringConverter<T> converter = control.getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXListView<T> control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        repairCursor();
        RXListSectionCell header = viewport.headerAt(event.getTarget());
        if (header != null) {
            onHeaderPressed(header);
            return;
        }
        RXListCell<T> cell = viewport.cellAt(event.getTarget());
        if (cell == null) {
            // Blank area below the items: the list has no marquee selection (a
            // single-column list gains little from a drag rectangle), so no-op.
            return;
        }
        int index = cell.getIndex();
        handlePointerSelect(event, index, isAccumulateMode());
    }

    // Every press on a header (in-flow or sticky) toggles its section and puts the
    // keyboard cursor on it; the selection stays. Like TitledPane and the TreeCell
    // disclosure arrow, a double-click toggles twice. Nodes inside a custom header
    // that consume their press never get here.
    private void onHeaderPressed(RXListSectionCell header) {
        if (!headersNavigable()) {
            return;
        }
        // The header's record was bound by an earlier pass; find it in the live sections.
        RXListSection bound = header.getItem();
        int sectionIndex = resolveSection(getSkinnable().getSections(), bound.sectionIndex(), bound.key());
        if (sectionIndex < 0) {
            return;
        }
        focusHeader(sectionIndex);
        focusModel.syncSelectionLeadState();
        toggleSection(sectionIndex);
    }

    // "Accumulate" modes show a per-item indicator (checkbox / checkmark) in
    // multiple-selection, where a plain click toggles that one row (and a plain
    // arrow only moves the cursor) — independent toggling, not "replace with the
    // clicked row". ROW (a whole-row highlight) and any single-selection mode use
    // the native replace-on-plain semantics instead.
    private boolean isAccumulateMode() {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null || sm.getSelectionMode() != SelectionMode.MULTIPLE) {
            return false;
        }
        RXListSelectionVisualMode mode = effectiveSelectionVisualMode();
        return mode == RXListSelectionVisualMode.CHECKBOX || mode == RXListSelectionVisualMode.CHECKMARK;
    }

    // Pointer selection: any press first updates focus. Ctrl toggles just that row
    // (priority over Shift, mirroring CellBehaviorBase); Shift extends the range
    // from the anchor (replace); a plain press toggles in an accumulate mode (so
    // rapid clicking selects / deselects freely) or replaces otherwise.
    private void handlePointerSelect(MouseEvent event, int index, boolean accumulate) {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        // Capture the range anchor from the current focus before moving it (see moveTo).
        int anchor = rangeAnchor(index);
        focusModel.focus(index);
        // Without a selection model the press still moves the focus cursor (like
        // the keyboard path); only the selection updates are skipped.
        if (sm == null) {
            setAnchor(index);
            return;
        }
        if (event.isShortcutDown()) {
            toggleAt(sm, index);
            setAnchor(index);
        } else if (event.isShiftDown() && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            setAnchor(anchor);
            clearAndSelectRange(sm, anchor, index);
        } else if (accumulate) {
            toggleAt(sm, index);
            setAnchor(index);
        } else {
            setAnchor(index);
            sm.clearAndSelect(index);
        }
        focusModel.syncSelectionLeadState();
    }

    private void toggleAt(MultipleSelectionModel<T> sm, int index) {
        if (sm.isSelected(index)) {
            sm.clearSelection(index);
        } else {
            // In single-selection mode select() replaces, matching clearAndSelect.
            sm.select(index);
        }
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            RXListCell<T> cell = viewport.cellAt(event.getTarget());
            if (cell != null) {
                activate(cell.getIndex());
            }
        }
    }

    private void clearAndSelectRange(MultipleSelectionModel<T> selectionModel, int anchor, int target) {
        if (headersNavigable() && !getSkinnable().getCollapsedSectionKeys().isEmpty()) {
            clearAndSelectShownRange(selectionModel, anchor, target);
            return;
        }
        int end = target >= anchor ? target + 1 : target - 1;
        if (selectionModel instanceof RXIndexedSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXIndexedSelectionModel<T> typedModel = (RXIndexedSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectRange(anchor, end);
        } else {
            selectionModel.clearSelection();
            selectionModel.selectRange(anchor, end);
        }
    }

    // A range is what lies between anchor and target on screen: items hidden in a
    // collapsed section in between are left out (and, as with any range, a previous
    // selection among them is replaced). The target stays the selection lead.
    private void clearAndSelectShownRange(MultipleSelectionModel<T> selectionModel, int anchor, int target) {
        int step = target >= anchor ? 1 : -1;
        List<Integer> shown = new ArrayList<>();
        for (int i = anchor; ; i += step) {
            if (!isItemHidden(i)) {
                shown.add(i);
            }
            if (i == target) {
                break;
            }
        }
        if (selectionModel instanceof RXIndexedSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXIndexedSelectionModel<T> typedModel = (RXIndexedSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectIndices(shown, target);
            return;
        }
        selectionModel.clearSelection();
        int[] rest = new int[shown.size() - 1];
        for (int i = 1; i < shown.size(); i++) {
            rest[i - 1] = shown.get(i);
        }
        selectionModel.selectIndices(shown.get(0), rest);
    }

    // ==================== Shared helpers ====================

    private int itemCount() {
        ObservableList<T> items = getSkinnable().getItems();
        return items == null ? 0 : items.size();
    }

    private static int clampIndex(int index, int itemCount) {
        if (index < 0) {
            return 0;
        }
        if (index >= itemCount) {
            return itemCount - 1;
        }
        return index;
    }

    // Variable-height mode is the fixedCellSize <= 0 / non-finite sentinel (matching
    // ListView): each row is sized to its content rather than a uniform height.
    static boolean isVariableHeight(RXListView<?> control) {
        double value = control.getFixedCellSize();
        return !(Double.isFinite(value) && value > 0.0);
    }

    static double fixedCellSizeOrDefault(RXListView<?> control) {
        double value = control.getFixedCellSize();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_FIXED_CELL_SIZE;
    }

    static double estimatedCellSizeOrDefault(RXListView<?> control) {
        double value = control.getEstimatedCellSize();
        return Double.isFinite(value) && value > 0.0 ? value : RXListView.DEFAULT_ESTIMATED_CELL_SIZE;
    }

    static double sectionHeaderHeightOrDefault(RXListView<?> control) {
        double value = control.getSectionHeaderHeight();
        return Double.isFinite(value) && value > 0.0 ? value : RXListView.DEFAULT_SECTION_HEADER_HEIGHT;
    }

    static double sectionSpacingOrZero(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}
