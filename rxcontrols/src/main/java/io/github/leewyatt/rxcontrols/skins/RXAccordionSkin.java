package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin of {@link RXAccordion}: stacks the panes top to bottom at their
 * preferred heights and moves focus between pane headers with Up / Down / Home / End.
 *
 * <p>Panes that touch form a segment, drawn as one {@code .segment} card behind them
 * with a {@code .divider} line between neighbours. An expanded pane opens a gap of
 * {@link RXAccordion#expandedSpacingProperty() expandedSpacing} above and below it,
 * except at the ends of the group; the gap follows the reveal progress of the
 * neighbouring panes, so it grows and shrinks with their animation and splits the
 * segment once it opens. With a spacing of zero the group is a single card. The
 * card edge width comes from the {@code .segment} padding.</p>
 *
 * <p>Navigation only reacts to key events whose target is the header of one of this
 * accordion's own panes (as rendered by {@link RXTitledPaneSkin}), so keys typed in a
 * pane body or in a nested accordion are left alone.</p>
 */
public class RXAccordionSkin extends RXSkinBase<RXAccordion> {

    // Decorations are pooled per pane and created on list changes only, so layout
    // just positions and shows / hides them.
    private final List<Region> segments = new ArrayList<>();
    private final List<Region> dividers = new ArrayList<>();

    /**
     * Creates the skin for the given accordion.
     *
     * @param control the accordion
     */
    public RXAccordionSkin(RXAccordion control) {
        super(control);
        rebuildChildren();
        // Rebuilt from the list rather than bound by index: a pane the application moves
        // to another parent leaves the children without a matching list change.
        disposer.registerListener(control.getPanes(), this::rebuildChildren);
        disposer.registerListener(control.expandedSpacingProperty(), control::requestLayout);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    private void rebuildChildren() {
        int count = getSkinnable().getPanes().size();
        while (segments.size() < count) {
            segments.add(decoration("segment"));
        }
        int dividerCount = Math.max(0, count - 1);
        while (dividers.size() < dividerCount) {
            dividers.add(decoration("divider"));
        }
        List<Node> children = new ArrayList<>(segments.subList(0, count));
        children.addAll(getSkinnable().getPanes());
        children.addAll(dividers.subList(0, dividerCount));
        getChildren().setAll(children);
    }

    private static Region decoration(String styleClass) {
        Region region = new Region();
        region.getStyleClass().add(styleClass);
        region.setManaged(false);
        region.setMouseTransparent(true);
        region.setVisible(false);
        return region;
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code != KeyCode.UP && code != KeyCode.DOWN && code != KeyCode.HOME && code != KeyCode.END) {
            return;
        }
        if (event.isShortcutDown() || event.isAltDown() || event.isShiftDown()) {
            return;
        }
        RXTitledPane source = triggerPane(event.getTarget());
        if (source == null) {
            return;
        }
        List<RXTitledPane> candidates = navigablePanes(source);
        int index = candidates.indexOf(source);
        RXTitledPane next = null;
        if (code == KeyCode.HOME) {
            next = candidates.isEmpty() ? null : candidates.get(0);
        } else if (code == KeyCode.END) {
            next = candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } else if (code == KeyCode.UP && index > 0) {
            next = candidates.get(index - 1);
        } else if (code == KeyCode.DOWN && index >= 0 && index < candidates.size() - 1) {
            next = candidates.get(index + 1);
        }
        if (next != null && next != source) {
            focusHeader(next);
        }
        // Consumed even at an edge so an enclosing ScrollPane does not scroll instead.
        event.consume();
    }

    private RXTitledPane triggerPane(EventTarget target) {
        if (!(target instanceof Node)) {
            return null;
        }
        Node node = (Node) target;
        if (!node.hasProperties()) {
            return null;
        }
        Object pane = node.getProperties().get(RXTitledPaneSkin.TRIGGER_KEY);
        if (pane instanceof RXTitledPane && getSkinnable().getPanes().contains(pane)) {
            return (RXTitledPane) pane;
        }
        return null;
    }

    private List<RXTitledPane> navigablePanes(RXTitledPane source) {
        List<RXTitledPane> result = new ArrayList<>();
        for (RXTitledPane pane : getSkinnable().getPanes()) {
            boolean usable = pane.isVisible() && !pane.isDisabled()
                    && pane.getSkin() instanceof RXTitledPaneSkin;
            if (usable || pane == source) {
                result.add(pane);
            }
        }
        return result;
    }

    private static void focusHeader(RXTitledPane pane) {
        Skin<?> skin = pane.getSkin();
        if (skin instanceof RXTitledPaneSkin) {
            ((RXTitledPaneSkin) skin).focusTrigger();
        }
    }

    // ==================== Geometry ====================

    private List<RXTitledPane> managedPanes() {
        List<RXTitledPane> result = new ArrayList<>();
        for (RXTitledPane pane : getSkinnable().getPanes()) {
            if (pane.isManaged()) {
                result.add(pane);
            }
        }
        return result;
    }

    private static double reveal(RXTitledPane pane) {
        Skin<?> skin = pane.getSkin();
        if (skin instanceof RXTitledPaneSkin) {
            return ((RXTitledPaneSkin) skin).revealProgress();
        }
        return pane.isExpanded() ? 1.0 : 0.0;
    }

    private double expandedSpacingOrZero() {
        double spacing = getSkinnable().getExpandedSpacing();
        return Double.isFinite(spacing) && spacing > 0 ? spacing : 0.0;
    }

    private double dividerHeight() {
        return dividers.isEmpty() ? 0.0 : snapSizeY(dividers.get(0).prefHeight(-1));
    }

    private double edgeTop() {
        return segments.isEmpty() ? 0.0 : segments.get(0).snappedTopInset();
    }

    private double edgeBottom() {
        return segments.isEmpty() ? 0.0 : segments.get(0).snappedBottomInset();
    }

    private double edgeLeft() {
        return segments.isEmpty() ? 0.0 : segments.get(0).snappedLeftInset();
    }

    private double edgeRight() {
        return segments.isEmpty() ? 0.0 : segments.get(0).snappedRightInset();
    }

    /**
     * Gap between {@code panes[i]} and {@code panes[i + 1]}: the expanded spacing
     * scaled by the larger reveal progress of the two.
     */
    private double[] gaps(List<RXTitledPane> panes) {
        double spacing = expandedSpacingOrZero();
        double[] gaps = new double[Math.max(0, panes.size() - 1)];
        for (int i = 0; i < gaps.length; i++) {
            double p = Math.max(reveal(panes.get(i)), reveal(panes.get(i + 1)));
            gaps[i] = snapSpaceY(spacing * p);
        }
        return gaps;
    }

    // Neighbours are always the divider plus the gap apart. The segment splits only
    // once the gap exceeds the two card edges a split adds, so that distance stays
    // continuous while the gap opens and closes.
    private boolean splits(double gap) {
        return gap > edgeTop() + edgeBottom();
    }

    private static double paneHeight(Node pane, double width) {
        double min = pane.minHeight(width);
        double pref = pane.prefHeight(width);
        double max = pane.maxHeight(width);
        return Math.max(min, Math.min(pref, max));
    }

    private double innerWidth(double width) {
        return width < 0 ? -1 : Math.max(0.0, width - edgeLeft() - edgeRight());
    }

    private double contentWidth(double width, double leftInset, double rightInset) {
        return width < 0 ? -1 : Math.max(0.0, width - leftInset - rightInset);
    }

    private double stackHeight(List<RXTitledPane> panes, double width) {
        if (panes.isEmpty()) {
            return 0.0;
        }
        double inner = innerWidth(width);
        double total = edgeTop() + edgeBottom();
        for (RXTitledPane pane : panes) {
            total += snapSizeY(paneHeight(pane, inner));
        }
        double divider = dividerHeight();
        for (double gap : gaps(panes)) {
            total += divider + gap;
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = contentWidth(width, leftInset, rightInset);
        return topInset + stackHeight(managedPanes(), w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Panes are always stacked at their preferred heights, so the group cannot shrink.
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double max = 0.0;
        for (Node pane : managedPanes()) {
            max = Math.max(max, snapSizeX(pane.prefWidth(-1)));
        }
        return leftInset + edgeLeft() + max + edgeRight() + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double max = 0.0;
        for (Node pane : managedPanes()) {
            max = Math.max(max, snapSizeX(pane.minWidth(-1)));
        }
        return leftInset + edgeLeft() + max + edgeRight() + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        for (Region segment : segments) {
            segment.setVisible(false);
        }
        for (Region divider : dividers) {
            divider.setVisible(false);
        }
        List<RXTitledPane> panes = managedPanes();
        if (panes.isEmpty()) {
            return;
        }
        double top = edgeTop();
        double bottom = edgeBottom();
        double left = edgeLeft();
        double inner = Math.max(0.0, w - left - edgeRight());
        double divider = dividerHeight();
        double[] gaps = gaps(panes);

        int segmentIndex = 0;
        int dividerIndex = 0;
        double segmentTop = y;
        double cursor = y + top;
        for (int i = 0; i < panes.size(); i++) {
            RXTitledPane pane = panes.get(i);
            double height = snapSizeY(paneHeight(pane, inner));
            pane.resizeRelocate(x + left, cursor, inner, height);
            cursor += height;
            boolean last = i == panes.size() - 1;
            if (last || splits(gaps[i])) {
                double segmentBottom = cursor + bottom;
                placeSegment(segmentIndex++, x, segmentTop, w, segmentBottom - segmentTop);
                if (!last) {
                    // The two card edges take part of the divider + gap distance.
                    segmentTop = segmentBottom + divider + gaps[i] - top - bottom;
                    cursor = segmentTop + top;
                }
            } else {
                Region line = dividers.get(dividerIndex++);
                line.resizeRelocate(x + left, cursor, inner, divider);
                line.setVisible(true);
                cursor += divider + gaps[i];
            }
        }
    }

    private void placeSegment(int index, double x, double y, double w, double h) {
        Region segment = segments.get(index);
        segment.resizeRelocate(x, y, w, Math.max(0.0, h));
        segment.setVisible(true);
    }
}
