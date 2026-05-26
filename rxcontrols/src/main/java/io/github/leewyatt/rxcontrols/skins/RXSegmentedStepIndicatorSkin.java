package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.event.SegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSegmentedStepIndicator}. Renders a row of
 * equal-width segment regions and clips the selected segment's fill according
 * to {@link RXSegmentedStepIndicator#segmentProgressProperty()}.
 */
public class RXSegmentedStepIndicatorSkin extends RXSkinBase<RXSegmentedStepIndicator> {

    // ==================== Constants ====================

    private static final double HALF = 0.5;

    private static final double DEFAULT_PREF_WIDTH = 150.0;

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");

    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private static final PseudoClass UPCOMING = PseudoClass.getPseudoClass("upcoming");

    // ==================== Nodes ====================

    private final List<Region> trackRegions = new ArrayList<>();
    private final List<Region> fillRegions = new ArrayList<>();
    private final List<Rectangle> fillClips = new ArrayList<>();

    // ==================== Handlers ====================

    private final EventHandler<MouseEvent> mouseClickedHandler = this::handleMouseClicked;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;

    private int hoverIndex = -1;

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXSegmentedStepIndicatorSkin(RXSegmentedStepIndicator control) {
        super(control);
        rebuildSegments();
        registerListeners(control);

        control.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
        control.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        control.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        disposer.registerDisposeTask(() -> control.removeEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler));
        disposer.registerDisposeTask(() -> control.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler));
        disposer.registerDisposeTask(() -> control.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler));
    }

    // ==================== Init ====================

    private void registerListeners(RXSegmentedStepIndicator control) {
        disposer.registerListener(control.stepCountProperty(), () -> {
            rebuildSegments();
            control.requestLayout();
        });
        disposer.registerListener(control.selectedIndexProperty(), () -> {
            updateSegmentStates();
            applyFills();
        });
        disposer.registerListener(control.segmentProgressProperty(), this::applyFills);
        disposer.registerListener(control.segmentGapProperty(), control::requestLayout);
        disposer.registerListener(control.segmentHeightProperty(), control::requestLayout);
    }

    // ==================== Segment composition ====================

    private void rebuildSegments() {
        int n = renderedStepCount();

        for (Region fill : fillRegions) {
            fill.setClip(null);
        }
        trackRegions.clear();
        fillRegions.clear();
        fillClips.clear();

        if (n == 0) {
            getChildren().clear();
            return;
        }

        List<Region> children = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            Region track = new Region();
            track.getStyleClass().setAll("track");
            track.setManaged(false);
            track.setMouseTransparent(true);

            Region fill = new Region();
            fill.getStyleClass().setAll("segment-fill");
            fill.setManaged(false);
            fill.setMouseTransparent(true);
            fill.setVisible(false);

            Rectangle clip = new Rectangle();
            fill.setClip(clip);

            boolean first = i == 0;
            boolean last = i == n - 1;
            track.pseudoClassStateChanged(FIRST, first);
            track.pseudoClassStateChanged(LAST, last);
            fill.pseudoClassStateChanged(FIRST, first);
            fill.pseudoClassStateChanged(LAST, last);

            trackRegions.add(track);
            fillRegions.add(fill);
            fillClips.add(clip);
            children.add(track);
            children.add(fill);
        }
        getChildren().setAll(children);
        updateSegmentStates();
    }

    private int renderedStepCount() {
        return RXMath.clamp(getSkinnable().getStepCount(), 0, RXSegmentedStepIndicator.MAX_STEP_COUNT);
    }

    private int renderedSelectedIndex(int n) {
        return RXMath.clamp(getSkinnable().getSelectedIndex(), 0, n - 1);
    }

    // ==================== Interaction ====================

    private void handleMouseClicked(MouseEvent event) {
        RXSegmentedStepIndicator control = getSkinnable();
        int index = control.segmentIndexAt(event.getX());
        if (index >= 0) {
            control.fireEvent(new SegmentInteractionEvent(SegmentInteractionEvent.CLICKED, index));
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        RXSegmentedStepIndicator control = getSkinnable();
        int index = control.segmentIndexAt(event.getX());
        if (index != hoverIndex) {
            hoverIndex = index;
            if (index >= 0) {
                control.fireEvent(new SegmentInteractionEvent(SegmentInteractionEvent.ENTERED, index));
            }
        }
    }

    private void handleMouseExited(MouseEvent event) {
        hoverIndex = -1;
    }

    // ==================== Segment state ====================

    private void updateSegmentStates() {
        int n = trackRegions.size();
        if (n == 0) {
            return;
        }

        int selected = renderedSelectedIndex(n);
        for (int i = 0; i < n; i++) {
            boolean completed = i < selected;
            boolean selectedState = i == selected;
            boolean upcoming = i > selected;

            Region track = trackRegions.get(i);
            Region fill = fillRegions.get(i);
            track.pseudoClassStateChanged(COMPLETED, completed);
            track.pseudoClassStateChanged(SELECTED, selectedState);
            track.pseudoClassStateChanged(UPCOMING, upcoming);
            fill.pseudoClassStateChanged(COMPLETED, completed);
            fill.pseudoClassStateChanged(SELECTED, selectedState);
            fill.pseudoClassStateChanged(UPCOMING, upcoming);
        }
    }

    // ==================== Fill rendering ====================

    private void applyFills() {
        int n = fillRegions.size();
        if (n == 0) {
            return;
        }

        int selected = renderedSelectedIndex(n);
        double progress = RXMath.clamp0To1(getSkinnable().getSegmentProgress());
        for (int i = 0; i < n; i++) {
            double width = fillRegions.get(i).getWidth();
            if (i < selected) {
                setFillClip(i, width);
            } else if (i == selected) {
                setFillClip(i, width * progress);
            } else {
                setFillClip(i, 0.0);
            }
        }
    }

    private void setFillClip(int index, double width) {
        Rectangle clip = fillClips.get(index);
        clip.setX(0.0);
        clip.setWidth(width);
        fillRegions.get(index).setVisible(width > 0.0);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        int n = fillRegions.size();
        if (n == 0 || contentWidth <= 0.0 || contentHeight <= 0.0) {
            for (int i = 0; i < n; i++) {
                trackRegions.get(i).resizeRelocate(contentX, contentY, 0.0, 0.0);
                fillRegions.get(i).resizeRelocate(contentX, contentY, 0.0, 0.0);
                fillRegions.get(i).setVisible(false);
                fillClips.get(i).setX(0.0);
                fillClips.get(i).setY(0.0);
                fillClips.get(i).setWidth(0.0);
                fillClips.get(i).setHeight(0.0);
            }
            return;
        }

        RXSegmentedStepIndicator control = getSkinnable();
        double gap = RXMath.sanitizeFiniteNonNegative(control.getSegmentGap());
        double segmentHeight = RXMath.sanitizeFiniteNonNegative(control.getSegmentHeight());
        double renderedHeight = Math.min(segmentHeight, contentHeight);

        double totalGap = gap * Math.max(0, n - 1);
        double segmentWidth = Math.max(0.0, (contentWidth - totalGap) / n);
        double y = contentY + (contentHeight - renderedHeight) * HALF;
        for (int i = 0; i < n; i++) {
            double x = contentX + i * (segmentWidth + gap);
            trackRegions.get(i).resizeRelocate(x, y, segmentWidth, renderedHeight);
            fillRegions.get(i).resizeRelocate(x, y, segmentWidth, renderedHeight);

            Rectangle clip = fillClips.get(i);
            clip.setX(0.0);
            clip.setY(0.0);
            clip.setHeight(renderedHeight);
        }
        applyFills();
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        int n = renderedStepCount();
        double gap = RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentGap());
        return leftInset + n + gap * Math.max(0, n - 1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset
                + RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentHeight())
                + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset
                + RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentHeight())
                + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        for (Region fill : fillRegions) {
            fill.setClip(null);
        }
        super.dispose();
    }
}
