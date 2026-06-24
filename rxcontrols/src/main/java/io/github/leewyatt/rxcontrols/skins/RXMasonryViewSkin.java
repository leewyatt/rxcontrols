package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.CellHeightContext;
import io.github.leewyatt.rxcontrols.CellHeightProvider;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns.Resolution;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

import java.util.Map;

/**
 * Skin for {@link RXMasonryView}. It hosts a virtualizing {@link RXMasonryViewport}
 * and a placeholder layer, builds the immutable {@link RXMasonryPlacement} each pass
 * (resolving the column count and track width, then each item's exact or estimated
 * height), and publishes the read-only metrics.
 *
 * <p>It distinguishes two widths so the responsive decision stays stable: the active
 * breakpoint and the breakpoint column cascade always read the pre-scrollbar content
 * width, while the {@code columnWidth} auto floor and the track width read the width
 * actually available this pass. When the content overflows, a second pass re-resolves
 * against the width minus the scroll bar, exactly as the tile view does.</p>
 *
 * <p>With a {@link RXMasonryView#cellHeightProviderProperty() cellHeightProvider} the
 * placement is exact and never jumps. Without one it uses a fixed
 * {@link RXMasonryView#estimatedCellHeightProperty() estimatedCellHeight} with no
 * measurement — deterministic, not yet convergent (measurement re-pack is a later
 * phase).</p>
 *
 * @param <T> the item type
 */
public class RXMasonryViewSkin<T> extends RXSkinBase<RXMasonryView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // Skin-local fallbacks coerce the control's lenient (non-positive / non-finite)
    // sizing values at the use site; the control's own defaults stay private.
    private static final double FALLBACK_COLUMN_WIDTH = 260.0;
    private static final double FALLBACK_GAP = 8.0;
    private static final double FALLBACK_ESTIMATED_CELL_HEIGHT = 200.0;
    private static final int DEFAULT_VISIBLE_ROWS = 3;
    private static final double MIN_VIEWPORT_CONTENT_WIDTH = 2.0;
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    private final RXMasonryViewport<T> viewport;
    private final StackPane placeholderRegion;

    private final ListChangeListener<T> itemsContentListener = change -> onItemsContentChanged();
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);
    private ObservableList<T> observedItems;

    private record PlacementResult(RXMasonryPlacement placement, RXBreakpoint activeBreakpoint) {
    }

    /**
     * Creates a skin for the given masonry view.
     *
     * @param control the masonry view
     */
    public RXMasonryViewSkin(RXMasonryView<T> control) {
        super(control);

        viewport = new RXMasonryViewport<>(control);
        getChildren().add(viewport);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setManaged(false);
        getChildren().add(placeholderRegion);

        registerListeners(control);
        attachItems(control.getItems());
        updatePlaceholder();
        disposer.registerDisposeTask(this::detachItems);
    }

    private void registerListeners(RXMasonryView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.placeholderProperty(), this::updatePlaceholder);
        disposer.registerListener(control.cellFactoryProperty(), viewport::recreateCells);

        // Every property that changes the placement geometry asks for a relayout; the
        // control's plain (no-invalidated) styleable properties rely on this.
        disposer.registerListener(control.columnWidthProperty(), this::requestRelayout);
        disposer.registerListener(control.hgapProperty(), this::requestRelayout);
        disposer.registerListener(control.vgapProperty(), this::requestRelayout);
        disposer.registerListener(control.columnCountProperty(), this::requestRelayout);
        disposer.registerListener(control.maxColumnsProperty(), this::requestRelayout);
        disposer.registerListener(control.fillWidthProperty(), this::requestRelayout);
        disposer.registerListener(control.alignmentProperty(), this::requestRelayout);
        disposer.registerListener(control.breakpointProfileProperty(), this::requestRelayout);
        disposer.registerListener(control.estimatedCellHeightProperty(), this::requestRelayout);
        disposer.registerListener(control.cellHeightProviderProperty(), this::requestRelayout);
        disposer.registerListener(control.columnSpanFactoryProperty(), this::requestRelayout);
        // Breakpoint overrides live in an observable map, not a property, but change the
        // resolved column count just the same.
        disposer.registerListener(control.getBreakpointColumnOverrides(), this::requestRelayout);
        // prefColumns only feeds computePrefWidth (a parent size hint), not the
        // placement, so it relays out the control rather than re-filling the viewport.
        disposer.registerListener(control.prefColumnsProperty(), () -> getSkinnable().requestLayout());

        // Selection state is applied during fill (applyCellState reads the selection
        // model); the live selection / focus listeners and the focus-ring focus model
        // (viewport.setFocusModel / refreshSelectionAndFocus) are wired in the
        // interaction phase, so the focus ring stays off until then.
    }

    // Dirties the viewport (forcing a re-fill) and, by propagation, the control (so the
    // skin re-runs buildPlacement and republishes metrics). Requesting layout on the
    // control alone would rebuild the placement but not re-fill the viewport when the
    // content box size is unchanged.
    private void requestRelayout() {
        viewport.requestLayout();
    }

    // ==================== Items / Placeholder / :empty ====================

    private void onItemsListSwapped() {
        attachItems(getSkinnable().getItems());
        // The anchor referred to an index in the previous list; drop it so the next
        // fill re-pins fresh instead of re-pinning a now-shifted index (which jumps).
        viewport.resetAnchor();
        updatePlaceholder();
        requestRelayout();
    }

    private void onItemsContentChanged() {
        viewport.resetAnchor();
        updatePlaceholder();
        requestRelayout();
    }

    private void attachItems(ObservableList<T> items) {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
        }
        observedItems = items;
        if (items != null) {
            items.addListener(weakItemsContentListener);
        }
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
            observedItems = null;
        }
    }

    private void updatePlaceholder() {
        RXMasonryView<T> control = getSkinnable();
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

    private int itemCount() {
        ObservableList<T> items = getSkinnable().getItems();
        return items == null ? 0 : items.size();
    }

    // ==================== Scrolling ====================

    private void consumePendingScroll() {
        RXMasonryView<T> control = getSkinnable();
        if (!control.hasPendingScroll()) {
            return;
        }
        int itemCount = itemCount();
        if (itemCount == 0) {
            control.clearPendingScroll();
            return;
        }
        int index = Math.max(0, Math.min(control.getPendingScrollIndex(), itemCount - 1));
        // Clear only when the request was actually applied. On a zero-height pass
        // scrollToIndex cannot compute geometry and returns false; keeping the request
        // armed lets the first sized pass honor it.
        if (viewport.scrollToIndex(index, control.getPendingScrollAlignment())) {
            control.clearPendingScroll();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        RXMasonryView<T> control = getSkinnable();
        double rightInset = Math.max(0.0, control.getWidth() - contentX - contentWidth);
        double bottomInset = Math.max(0.0, control.getHeight() - contentY - contentHeight);
        viewport.setChromeInsets(contentX, contentY, rightInset, bottomInset);

        PlacementResult result = buildPlacement(contentWidth, contentHeight);
        viewport.setPlacement(result.placement());
        control.setActualColumnCount(result.placement().columns());
        control.setActiveBreakpoint(result.activeBreakpoint());

        viewport.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        placeholderRegion.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        consumePendingScroll();
        // Force the viewport to realize cells now so the scroll request and the
        // published visible bounds reflect this pass, not the previous one.
        viewport.layout();
        control.setFirstVisibleIndex(viewport.getVisibleFirstIndex());
        control.setLastVisibleIndex(viewport.getVisibleLastIndex());
    }

    // The breakpoint-driving width is the pre-scrollbar content width, so the active
    // breakpoint never flips when the bar appears; the track-driving width drops the
    // bar breadth on the overflow pass.
    private PlacementResult buildPlacement(double breakpointWidth, double availableHeight) {
        RXMasonryView<T> control = getSkinnable();
        double snappedColumnWidth = snapSizeX(columnWidthOrDefault(control));
        double snappedHgap = snapSpaceX(gapOrDefault(control.getHgap()));
        // Clamp to zero: the per-column binary-search visibility query requires items
        // to stack monotonically, so the virtualized view does not overlap.
        double snappedVgap = Math.max(0.0, snapSpaceY(gapOrDefault(control.getVgap())));
        RXBreakpointProfile profile = breakpointProfileOrDefault(control);
        Map<String, Integer> overrides = control.getBreakpointColumnOverrides();

        Resolution first = MasonryColumns.resolve(breakpointWidth, breakpointWidth, control.getColumnCount(),
                snappedColumnWidth, snappedHgap, control.getMaxColumns(), control.isFillWidth(), profile, overrides);
        RXMasonryPlacement firstPlacement = placementFor(first, breakpointWidth, snappedHgap, snappedVgap);
        if (firstPlacement.contentHeight() <= availableHeight) {
            return new PlacementResult(firstPlacement, first.activeBreakpoint());
        }

        // Overflow: a vertical bar is needed, so re-resolve the columns / track against
        // the narrower width. The active breakpoint is unchanged (it ignores layoutWidth).
        double layoutWidth = Math.max(0.0, breakpointWidth - viewport.scrollBarBreadth());
        Resolution second = MasonryColumns.resolve(breakpointWidth, layoutWidth, control.getColumnCount(),
                snappedColumnWidth, snappedHgap, control.getMaxColumns(), control.isFillWidth(), profile, overrides);
        RXMasonryPlacement secondPlacement = placementFor(second, layoutWidth, snappedHgap, snappedVgap);
        return new PlacementResult(secondPlacement, second.activeBreakpoint());
    }

    private RXMasonryPlacement placementFor(Resolution resolution, double layoutWidth,
                                            double snappedHgap, double snappedVgap) {
        RXMasonryView<T> control = getSkinnable();
        ObservableList<T> items = control.getItems();
        int count = items == null ? 0 : items.size();
        int columns = resolution.columns();
        double track = resolution.trackWidth();
        double startX = horizontalAlignmentOffset(control, layoutWidth, resolution.usedWidth());

        double[] heights = new double[count];
        int[] spans = new int[count];
        CellHeightProvider<T> provider = control.getCellHeightProvider();
        double estimated = estimatedCellHeightOrDefault(control);
        Callback<T, Integer> spanFactory = control.getColumnSpanFactory();
        for (int i = 0; i < count; i++) {
            T item = items.get(i);
            int span = resolveSpan(spanFactory, item, columns);
            spans[i] = span;
            double cellWidth = span * track + (span - 1) * snappedHgap;
            double height = estimated;
            if (provider != null) {
                double provided = provider.computeHeight(
                        new CellHeightContext<>(item, i, cellWidth, span, track, columns));
                if (Double.isFinite(provided) && provided >= 0.0) {
                    height = provided;
                }
            }
            heights[i] = snapSizeY(height);
        }
        return new RXMasonryPlacement(columns, track, snappedHgap, snappedVgap, startX, heights, spans);
    }

    private static int resolveSpan(Callback<?, Integer> spanFactory, Object item, int columns) {
        int span = 1;
        if (spanFactory != null) {
            @SuppressWarnings("unchecked")
            Callback<Object, Integer> typed = (Callback<Object, Integer>) spanFactory;
            Integer value = typed.call(item);
            if (value != null) {
                span = value;
            }
        }
        if (span < 1) {
            return 1;
        }
        return Math.min(span, columns);
    }

    private static double horizontalAlignmentOffset(RXMasonryView<?> control, double layoutWidth,
                                                    double usedWidth) {
        double slack = Math.max(0.0, layoutWidth - usedWidth);
        HPos hpos = alignmentOrDefault(control).getHpos();
        return switch (hpos) {
            case CENTER -> slack / 2.0;
            case RIGHT -> slack;
            default -> 0.0;
        };
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        RXMasonryView<T> control = getSkinnable();
        double columnWidth = columnWidthOrDefault(control);
        double gap = gapOrDefault(control.getHgap());
        int columns = capColumns(control.getPrefColumns(), control.getMaxColumns());
        double content = columns * columnWidth + (columns - 1) * gap;
        return leftInset + content + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXMasonryView<T> control = getSkinnable();
        double slot = estimatedCellHeightOrDefault(control) + gapOrDefault(control.getVgap());
        return topInset + DEFAULT_VISIBLE_ROWS * slot + bottomInset;
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
        // detachItems runs via the disposer (registerDisposeTask).
        viewport.dispose();
        placeholderRegion.getChildren().clear();
    }

    // ==================== Lenient value helpers ====================

    static double columnWidthOrDefault(RXMasonryView<?> control) {
        double value = control.getColumnWidth();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_COLUMN_WIDTH;
    }

    static double estimatedCellHeightOrDefault(RXMasonryView<?> control) {
        double value = control.getEstimatedCellHeight();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_ESTIMATED_CELL_HEIGHT;
    }

    static double gapOrDefault(double value) {
        return Double.isFinite(value) ? value : FALLBACK_GAP;
    }

    private static Pos alignmentOrDefault(RXMasonryView<?> control) {
        Pos value = control.getAlignment();
        return value == null ? Pos.TOP_LEFT : value;
    }

    private static RXBreakpointProfile breakpointProfileOrDefault(RXMasonryView<?> control) {
        RXBreakpointProfile value = control.getBreakpointProfile();
        return value == null ? RXBreakpointProfile.ANT_DESIGN : value;
    }

    private static int capColumns(int columns, int maxColumns) {
        int capped = Math.max(1, columns);
        if (maxColumns > 0 && capped > maxColumns) {
            capped = maxColumns;
        }
        return Math.min(capped, MAX_RESOLVED_COLUMNS);
    }
}
