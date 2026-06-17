package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSegmentedControl}. Lays out each segment cell
 * directly (no nested {@code HBox}) so the indicator can be positioned from the
 * same set of computed cell geometries within a single layout pass, avoiding the
 * stale-bounds trap of reading a child container's bounds in the same frame.
 *
 * <p>The children are {@code [indicator, cell0, cell1, ...]}: the indicator is
 * an unmanaged, mouse-transparent {@code Region} drawn underneath the cells so a
 * selected segment's text stays readable on top of the pill.
 *
 * <p>The indicator slides between segments: {@link #indicatorX} and
 * {@link #indicatorWidth} are animated by a field-held {@link Timeline} rebuilt
 * on each selection change (latest-wins). The first positioning and any
 * non-animated change snap immediately; a resize / CSS relayout calibrates the
 * resting geometry (snapping when idle, retargeting an in-flight slide only when
 * the target really moved, so a {@code :selected} font-weight relayout cannot
 * kill the slide). The slide is a short one-shot tween, so it is not
 * tree-showing-paused (per the project animation guidance and the
 * {@code RXCircularProgressIndicatorSkin} progress-tween precedent); it settles
 * within its short duration regardless of visibility.
 *
 * @param <T> application value type
 */
public class RXSegmentedControlSkin<T> extends RXSkinBase<RXSegmentedControl<T>> {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass BLOCK = PseudoClass.getPseudoClass("block");
    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");
    private static final PseudoClass ONLY = PseudoClass.getPseudoClass("only");

    private static final double GEOMETRY_EPSILON = 0.5;

    /** Symmetric ease-in-out matching Ant Design's {@code motionEaseInOut}. */
    private static final Interpolator SLIDE_EASING = Interpolator.SPLINE(0.645, 0.045, 0.355, 1.0);

    // ==================== Nodes ====================

    private final Region indicator = new Region();
    private final List<SegmentCell> cells = new ArrayList<>();

    // ==================== Indicator animation state ====================

    private final DoubleProperty indicatorX = new SimpleDoubleProperty(this, "indicatorX", 0.0);
    private final DoubleProperty indicatorWidth = new SimpleDoubleProperty(this, "indicatorWidth", 0.0);
    private double indicatorY;
    private double indicatorHeight;
    private double animTargetX;
    private double animTargetWidth;

    private Timeline slideTimeline;
    private boolean indicatorPositioned;
    private boolean pendingSelectionAnimation;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXSegmentedControlSkin(RXSegmentedControl<T> control) {
        super(control);

        indicator.getStyleClass().add("indicator");
        indicator.setManaged(false);
        indicator.setMouseTransparent(true);

        rebuildCells();
        control.pseudoClassStateChanged(BLOCK, control.isBlock());

        disposer.registerListener(indicatorX, this::applyIndicatorGeometry);
        disposer.registerListener(indicatorWidth, this::applyIndicatorGeometry);
        disposer.registerListener(control.getItems(), this::rebuildCells);
        disposer.registerListener(control.selectedIndexProperty(), () -> {
            updateSelectedPseudoClass();
            pendingSelectionAnimation = true;
            control.requestLayout();
        });
        disposer.registerListener(control.blockProperty(), () -> {
            control.pseudoClassStateChanged(BLOCK, control.isBlock());
            control.requestLayout();
        });
        disposer.registerListener(control.equalSegmentWidthProperty(), control::requestLayout);
        disposer.registerListener(control.segmentSpacingProperty(), control::requestLayout);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Cell building ====================

    private void rebuildCells() {
        for (SegmentCell cell : cells) {
            cell.detach();
        }
        cells.clear();
        List<Node> children = new ArrayList<>();
        children.add(indicator);
        List<RXSegmentedItem<T>> items = getSkinnable().getItems();
        for (int i = 0; i < items.size(); i++) {
            SegmentCell cell = new SegmentCell(items.get(i), i);
            cells.add(cell);
            children.add(cell);
        }
        getChildren().setAll(children);
        // A structural change reflows the strip: re-anchor the indicator by
        // snapping to the (new) selected cell rather than sliding across the
        // reflow. Resetting this makes the next layout take the snap branch,
        // which is robust to the items-listener ordering (the skin's rebuild
        // runs before the control corrects the selection). Plain selection
        // changes leave it set, so those still animate.
        indicatorPositioned = false;
        updateSegmentPositions();
        updateSelectedPseudoClass();
        getSkinnable().requestLayout();
    }

    private void updateSegmentPositions() {
        int count = cells.size();
        boolean only = count == 1;
        for (int i = 0; i < count; i++) {
            SegmentCell cell = cells.get(i);
            cell.pseudoClassStateChanged(ONLY, only);
            cell.pseudoClassStateChanged(FIRST, !only && i == 0);
            cell.pseudoClassStateChanged(LAST, !only && i == count - 1);
        }
    }

    private void updateSelectedPseudoClass() {
        int selected = getSkinnable().getSelectedIndex();
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).pseudoClassStateChanged(SELECTED, i == selected);
        }
    }

    private void onCellPressed(SegmentCell cell) {
        RXSegmentedControl<T> control = getSkinnable();
        if (cell.item.isDisabled()) {
            return;
        }
        control.requestFocus();
        int index = cell.index;
        if (index == control.getSelectedIndex()) {
            // Clicking the already-selected segment: radio (no-op) unless empty
            // selection is allowed, in which case it deselects.
            if (control.isAllowEmptySelection()) {
                control.clearSelection();
            }
        } else {
            control.selectIndex(index);
        }
    }

    // ==================== Keyboard navigation ====================

    private void onKeyPressed(KeyEvent event) {
        if (cells.isEmpty()) {
            return;
        }
        int current = getSkinnable().getSelectedIndex();
        int target;
        switch (event.getCode()) {
            case LEFT:
                target = previousEnabled(current);
                break;
            case RIGHT:
                target = nextEnabled(current);
                break;
            case HOME:
                target = nextEnabled(-1);
                break;
            case END:
                target = previousEnabled(cells.size());
                break;
            default:
                return;
        }
        if (target >= 0 && target != current) {
            // Selection follows focus (radio-group / iOS model).
            getSkinnable().selectIndex(target);
        }
        event.consume();
    }

    private int nextEnabled(int from) {
        for (int i = from + 1; i < cells.size(); i++) {
            if (isEnabled(i)) {
                return i;
            }
        }
        return -1;
    }

    private int previousEnabled(int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (isEnabled(i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEnabled(int index) {
        return !cells.get(index).item.isDisabled();
    }

    private static String displayText(RXSegmentedItem<?> item) {
        String text = item.getText();
        if (text != null && !text.isBlank()) {
            return text;
        }
        return String.valueOf(item.getValue());
    }

    // ==================== Indicator positioning ====================

    private void applyIndicatorGeometry() {
        indicator.resizeRelocate(indicatorX.get(), indicatorY, indicatorWidth.get(), indicatorHeight);
    }

    private void positionIndicator(double targetX, double targetY, double targetWidth, double targetHeight) {
        indicatorY = targetY;
        indicatorHeight = targetHeight;

        if (!indicatorPositioned) {
            // First positioning: snap, never fly in from a corner.
            indicatorPositioned = true;
            pendingSelectionAnimation = false;
            snapTo(targetX, targetWidth);
            return;
        }
        if (pendingSelectionAnimation) {
            // playSlide snaps internally when animation is disabled.
            pendingSelectionAnimation = false;
            playSlide(targetX, targetWidth);
            return;
        }
        // Resize / CSS / font relayout (no selection change this pass).
        if (slideTimeline == null) {
            snapTo(targetX, targetWidth);
        } else if (Math.abs(targetX - animTargetX) > GEOMETRY_EPSILON
                || Math.abs(targetWidth - animTargetWidth) > GEOMETRY_EPSILON) {
            // Target really moved: retarget the in-flight slide from the current
            // frame rather than killing it.
            playSlide(targetX, targetWidth);
        } else {
            // Geometry unchanged (e.g. a :selected font-weight forceParentLayout):
            // let the slide continue; only re-apply the y/height that may shift.
            applyIndicatorGeometry();
        }
    }

    private void snapTo(double targetX, double targetWidth) {
        stopSlide();
        animTargetX = targetX;
        animTargetWidth = targetWidth;
        indicatorX.set(targetX);
        indicatorWidth.set(targetWidth);
        // x / width may be unchanged (so the listeners would not fire); apply
        // directly to pick up any y / height change.
        applyIndicatorGeometry();
    }

    private void playSlide(double targetX, double targetWidth) {
        if (!shouldAnimate()) {
            // Not animating (animated=false or non-positive/non-finite duration):
            // snap. This also guards the resize retarget path, not just the
            // selection-change path.
            snapTo(targetX, targetWidth);
            return;
        }
        Duration duration = getSkinnable().getAnimationDuration();
        double startX = indicatorX.get();
        double startWidth = indicatorWidth.get();
        stopSlide();
        // Explicitly re-write the captured frame so correctness does not depend
        // on Timeline.stop()'s internal jump behaviour (latest-wins, no snap-back).
        indicatorX.set(startX);
        indicatorWidth.set(startWidth);
        animTargetX = targetX;
        animTargetWidth = targetWidth;
        slideTimeline = new Timeline(new KeyFrame(duration,
                new KeyValue(indicatorX, targetX, SLIDE_EASING),
                new KeyValue(indicatorWidth, targetWidth, SLIDE_EASING)));
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

    private void hideIndicator(double x, double y) {
        stopSlide();
        indicatorPositioned = false;
        pendingSelectionAnimation = false;
        indicator.setVisible(false);
        indicator.resizeRelocate(x, y, 0.0, 0.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        int count = cells.size();
        if (count == 0 || w <= 0.0 || h <= 0.0) {
            // Degenerate: collapse the indicator to a deterministic hidden pose
            // rather than leaving last frame's geometry behind.
            hideIndicator(x, y);
            return;
        }

        layoutCells(x, y, w, h);

        int selected = getSkinnable().getSelectedIndex();
        if (selected < 0 || selected >= count) {
            hideIndicator(x, y);
            return;
        }
        indicator.setVisible(true);
        SegmentCell cell = cells.get(selected);
        positionIndicator(cell.getLayoutX(), cell.getLayoutY(), cell.getWidth(), cell.getHeight());
    }

    private boolean isEqualized() {
        return getSkinnable().isBlock() || getSkinnable().isEqualSegmentWidth();
    }

    private void layoutCells(double x, double y, double w, double h) {
        int count = cells.size();
        double spacing = Math.max(0.0, getSkinnable().getSegmentSpacing());
        if (isEqualized()) {
            // Equal segments filling the available width; the per-cell remainder
            // is distributed by snapping cumulative edges so segments meet with
            // no sub-pixel gap or overlap.
            double available = Math.max(0.0, w - spacing * (count - 1));
            double cursor = x;
            double consumed = 0.0;
            for (int i = 0; i < count; i++) {
                double snappedEdge = snapSizeX(available * (i + 1) / count);
                double cellWidth = snappedEdge - consumed;
                cells.get(i).resizeRelocate(snapPositionX(cursor), y, cellWidth, h);
                consumed = snappedEdge;
                cursor += cellWidth + spacing;
            }
        } else {
            // Content width: each segment takes its preferred width, but is
            // compressed proportionally toward its minimum when the available
            // width is short, so the strip never overflows its background.
            double[] widths = contentWidths(count, w - spacing * (count - 1), h);
            double cursor = x;
            for (int i = 0; i < count; i++) {
                double cellX = snapPositionX(cursor);
                cells.get(i).resizeRelocate(cellX, y, widths[i], h);
                cursor = cellX + widths[i] + spacing;
            }
        }
    }

    /**
     * Computes content-mode segment widths: preferred widths when they fit the
     * available space, otherwise an HBox-style proportional shrink toward each
     * segment's minimum (clamped at the minimum). Cumulative edges are snapped
     * so adjacent segments meet without a sub-pixel gap.
     */
    private double[] contentWidths(int count, double available, double height) {
        double[] pref = new double[count];
        double totalPref = 0.0;
        for (int i = 0; i < count; i++) {
            pref[i] = cells.get(i).prefWidth(height);
            totalPref += pref[i];
        }
        double[] widths = new double[count];
        if (totalPref <= available) {
            for (int i = 0; i < count; i++) {
                widths[i] = snapSizeX(pref[i]);
            }
            return widths;
        }
        double[] min = new double[count];
        double totalMin = 0.0;
        for (int i = 0; i < count; i++) {
            min[i] = cells.get(i).minWidth(height);
            totalMin += min[i];
        }
        double headroom = totalPref - totalMin;
        // Shrink each segment toward its minimum in proportion to its own
        // pref-to-min headroom. When even the minimums do not fit, segments are
        // scaled below their minimum so the strip still fits (labels ellipsize).
        double shrinkRatio = headroom <= 0.0 ? 1.0 : (totalPref - available) / headroom;
        double consumed = 0.0;
        double idealEdge = 0.0;
        for (int i = 0; i < count; i++) {
            if (i < count - 1) {
                idealEdge += pref[i] - (pref[i] - min[i]) * shrinkRatio;
                double snappedEdge = snapSizeX(idealEdge);
                widths[i] = Math.max(0.0, snappedEdge - consumed);
                consumed = snappedEdge;
            } else {
                // The last segment absorbs the snapping remainder so the strip
                // fills the available width exactly, never overflowing it.
                widths[i] = Math.max(0.0, available - consumed);
            }
        }
        return widths;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + cellsWidth(true) + rightInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + cellsWidth(false) + rightInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // block lets the strip stretch to fill the parent; otherwise it hugs.
        return getSkinnable().isBlock() ? Double.MAX_VALUE : getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        // Height comes from content (font line height + segment / container
        // padding); CSS adjusts it. No hard-coded value.
        double content = 0.0;
        for (SegmentCell cell : cells) {
            content = Math.max(content, cell.prefHeight(-1));
        }
        return topInset + content + bottomInset;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    private double cellsWidth(boolean minimum) {
        int count = cells.size();
        if (count == 0) {
            return 0.0;
        }
        double cellTotal;
        if (isEqualized()) {
            double widest = 0.0;
            for (SegmentCell cell : cells) {
                widest = Math.max(widest, minimum ? cell.minWidth(-1) : cell.prefWidth(-1));
            }
            cellTotal = widest * count;
        } else {
            double sum = 0.0;
            for (SegmentCell cell : cells) {
                sum += minimum ? cell.minWidth(-1) : cell.prefWidth(-1);
            }
            cellTotal = sum;
        }
        double spacing = Math.max(0.0, getSkinnable().getSegmentSpacing());
        return cellTotal + spacing * (count - 1);
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // The slide Timeline is rebuilt many times; stop the current one by
        // reading the live field (a disposer task would hold a stale reference).
        stopSlide();
        for (SegmentCell cell : cells) {
            cell.detach();
        }
    }

    // ==================== Segment cell ====================

    private final class SegmentCell extends StackPane {

        private final RXSegmentedItem<T> item;
        private final Label label = new Label();
        private final int index;

        // Per-item listeners are held as fields and removed in detach(): a
        // discarded cell would otherwise leak through the (user-owned) item.
        private final InvalidationListener contentListener = observable -> updateContent();
        private final InvalidationListener disabledListener = observable -> updateDisabledState();
        private final InvalidationListener styleClassListener = observable -> updateStyleClass();
        private final InvalidationListener tooltipListener = observable -> updateTooltip();
        private final EventHandler<MouseEvent> pressHandler = event -> {
            onCellPressed(this);
            event.consume();
        };

        private Tooltip tooltip;

        SegmentCell(RXSegmentedItem<T> item, int index) {
            this.item = item;
            this.index = index;
            // Basic accessibility: announce each segment as a radio option.
            setAccessibleRole(AccessibleRole.RADIO_BUTTON);
            label.setMouseTransparent(true);
            updateStyleClass();
            updateContent();
            updateTooltip();
            updateDisabledState();

            item.textProperty().addListener(contentListener);
            item.graphicProperty().addListener(contentListener);
            item.contentProperty().addListener(contentListener);
            item.disabledProperty().addListener(disabledListener);
            item.tooltipProperty().addListener(tooltipListener);
            item.getStyleClass().addListener(styleClassListener);
            addEventHandler(MouseEvent.MOUSE_PRESSED, pressHandler);
        }

        private void updateContent() {
            Node content = item.getContent();
            if (content != null) {
                getChildren().setAll(content);
            } else {
                label.setText(displayText(item));
                label.setGraphic(item.getGraphic());
                getChildren().setAll(label);
            }
        }

        private void updateDisabledState() {
            // setDisable drives the framework-owned :disabled pseudo-class
            // natively and also blocks pointer events / hover on the segment.
            setDisable(item.isDisabled());
        }

        private void updateStyleClass() {
            getStyleClass().setAll("segment");
            getStyleClass().addAll(item.getStyleClass());
        }

        private void updateTooltip() {
            String text = item.getTooltip();
            if (text == null || text.isEmpty()) {
                if (tooltip != null) {
                    Tooltip.uninstall(this, tooltip);
                    tooltip = null;
                }
            } else {
                if (tooltip == null) {
                    tooltip = new Tooltip();
                    Tooltip.install(this, tooltip);
                }
                tooltip.setText(text);
            }
        }

        private void detach() {
            item.textProperty().removeListener(contentListener);
            item.graphicProperty().removeListener(contentListener);
            item.contentProperty().removeListener(contentListener);
            item.disabledProperty().removeListener(disabledListener);
            item.tooltipProperty().removeListener(tooltipListener);
            item.getStyleClass().removeListener(styleClassListener);
            removeEventHandler(MouseEvent.MOUSE_PRESSED, pressHandler);
            if (tooltip != null) {
                Tooltip.uninstall(this, tooltip);
                tooltip = null;
            }
        }
    }
}
