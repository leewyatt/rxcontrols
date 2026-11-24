package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator.TitleSide;
import io.github.leewyatt.rxcontrols.event.RXSegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSegmentedStepIndicator}. Renders a row of
 * equal-width segment regions, clips the selected segment's fill according
 * to {@link RXSegmentedStepIndicator#segmentProgressProperty()}, and places
 * one {@code .title} box per segment when a title factory is set.
 *
 * <p>The skin builds its nodes when it is installed, not when it is
 * constructed, so a skin that is never installed leaves the indicator's
 * children and the application's title nodes alone.</p>
 */
public class RXSegmentedStepIndicatorSkin extends RXSkinBase<RXSegmentedStepIndicator> {

    // ==================== Constants ====================

    private static final double HALF = 0.5;

    private static final double DEFAULT_PREF_WIDTH = 150.0;

    private static final double MIN_SEGMENT_WIDTH = 1.0;

    private static final Object SEGMENT_INDEX_KEY = new Object();

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");

    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private static final PseudoClass UPCOMING = PseudoClass.getPseudoClass("upcoming");

    // ==================== Nodes ====================

    private final List<Region> trackRegions = new ArrayList<>();
    private final List<Region> fillRegions = new ArrayList<>();
    private final List<Rectangle> fillClips = new ArrayList<>();
    private final List<StackPane> titleBoxes = new ArrayList<>();
    private final List<Rectangle> titleClips = new ArrayList<>();

    // ==================== Handlers ====================

    private final EventHandler<MouseEvent> segmentClickedHandler = this::handleSegmentClicked;
    private final EventHandler<MouseEvent> segmentEnteredHandler = this::handleSegmentEntered;

    // ==================== State ====================

    private final InvalidationListener skinListener = observable -> activate();
    private boolean activated;
    private boolean disposed;
    private boolean rebuilding;
    private boolean rebuildPending;

    /**
     * Creates a skin for the given control. The skin builds its nodes once it
     * is installed as the control's skin.
     *
     * @param control the skinnable control
     */
    public RXSegmentedStepIndicatorSkin(RXSegmentedStepIndicator control) {
        super(control);
        // Weak: setSkin drops a skin of the installed class without disposing
        // it, and nothing else may keep such a skin reachable from the control.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        control.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> control.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Activation ====================

    // setSkin ignores a new skin of the installed class once its constructor has
    // run. Building the nodes only when installed keeps such a skin from
    // replacing the installed skin's children or taking the title nodes, and
    // runs after a replaced skin has been disposed.
    private void activate() {
        RXSegmentedStepIndicator control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        registerListeners(control);
        rebuildSegments();
    }

    private void registerListeners(RXSegmentedStepIndicator control) {
        disposer.registerListener(control.stepCountProperty(), () -> {
            rebuildSegments();
            control.requestLayout();
        });
        disposer.registerListener(control.titleFactoryProperty(), () -> {
            rebuildSegments();
            control.requestLayout();
        });
        disposer.registerListener(control.selectedIndexProperty(), () -> {
            updateSegmentStates();
            applyFills();
        });
        disposer.registerListener(control.segmentProgressProperty(), this::applyFills);
        disposer.registerListener(control.completedProperty(), () -> {
            updateSegmentStates();
            applyFills();
        });
        disposer.registerListener(control.segmentGapProperty(), control::requestLayout);
        disposer.registerListener(control.segmentHeightProperty(), control::requestLayout);
        disposer.registerListener(control.titleSideProperty(), control::requestLayout);
        disposer.registerListener(control.titleGapProperty(), control::requestLayout);
    }

    // ==================== Segment composition ====================

    // The title factory is application code and may change the indicator again;
    // such a change is picked up once the current pass has finished.
    private void rebuildSegments() {
        if (rebuilding) {
            rebuildPending = true;
            return;
        }
        rebuilding = true;
        try {
            do {
                rebuildPending = false;
                buildSegments();
            } while (rebuildPending && !disposed);
        } finally {
            rebuilding = false;
        }
    }

    private void buildSegments() {
        int n = renderedStepCount();
        List<StackPane> titles = createTitles(getSkinnable().getTitleFactory(), n);
        if (titles == null) {
            return;
        }
        clearSegments();

        if (n == 0) {
            return;
        }

        List<Node> children = new ArrayList<>(n * 2 + titles.size());
        for (int i = 0; i < n; i++) {
            Region track = new Region();
            track.getStyleClass().setAll("track");
            track.setManaged(false);
            track.setPickOnBounds(true);
            track.getProperties().put(SEGMENT_INDEX_KEY, i);
            track.addEventHandler(MouseEvent.MOUSE_CLICKED, segmentClickedHandler);
            track.addEventHandler(MouseEvent.MOUSE_ENTERED, segmentEnteredHandler);

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
        for (StackPane box : titles) {
            titleBoxes.add(box);
            titleClips.add((Rectangle) box.getClip());
            children.add(box);
        }
        getChildren().addAll(children);
        updateSegmentStates();
    }

    // Returns null when the factory disposed this skin; the boxes made so far
    // then let go of the application's nodes.
    private List<StackPane> createTitles(Callback<Integer, Node> factory, int n) {
        List<StackPane> titles = new ArrayList<>();
        if (factory == null) {
            return titles;
        }
        for (int i = 0; i < n; i++) {
            Node title = callFactory(factory, i);
            if (disposed) {
                for (StackPane box : titles) {
                    box.getChildren().clear();
                }
                return null;
            }
            // Managed, unlike the segments: a title that changes its size has
            // to reach the indicator, whose preferred size depends on it.
            StackPane box = new StackPane();
            box.getStyleClass().setAll("title");
            box.setPickOnBounds(true);
            box.getProperties().put(SEGMENT_INDEX_KEY, i);
            box.addEventHandler(MouseEvent.MOUSE_CLICKED, segmentClickedHandler);
            box.addEventHandler(MouseEvent.MOUSE_ENTERED, segmentEnteredHandler);
            box.setClip(new Rectangle());
            box.pseudoClassStateChanged(FIRST, i == 0);
            box.pseudoClassStateChanged(LAST, i == n - 1);
            if (title != null) {
                box.getChildren().add(title);
            }
            titles.add(box);
        }
        return titles;
    }

    // The factory is application code: a factory that throws is reported and
    // leaves that segment without a title.
    private static Node callFactory(Callback<Integer, Node> factory, int index) {
        try {
            return factory.call(index);
        } catch (RuntimeException e) {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
            return null;
        }
    }

    private void clearSegments() {
        for (Region track : trackRegions) {
            track.removeEventHandler(MouseEvent.MOUSE_CLICKED, segmentClickedHandler);
            track.removeEventHandler(MouseEvent.MOUSE_ENTERED, segmentEnteredHandler);
            track.getProperties().remove(SEGMENT_INDEX_KEY);
        }
        for (Region fill : fillRegions) {
            fill.setClip(null);
        }
        for (StackPane box : titleBoxes) {
            box.removeEventHandler(MouseEvent.MOUSE_CLICKED, segmentClickedHandler);
            box.removeEventHandler(MouseEvent.MOUSE_ENTERED, segmentEnteredHandler);
            box.getProperties().remove(SEGMENT_INDEX_KEY);
            box.setClip(null);
            box.getChildren().clear();
        }
        // Only our own nodes: the indicator's child list is not ours alone.
        getChildren().removeAll(trackRegions);
        getChildren().removeAll(fillRegions);
        getChildren().removeAll(titleBoxes);
        trackRegions.clear();
        fillRegions.clear();
        fillClips.clear();
        titleBoxes.clear();
        titleClips.clear();
    }

    private int renderedStepCount() {
        return RXMath.clamp(getSkinnable().getStepCount(), 0, RXSegmentedStepIndicator.MAX_STEP_COUNT);
    }

    // The index the n rendered segments are drawn against: n, past the last
    // segment, once every step is completed; otherwise the selected index kept
    // within the segments.
    private int renderedSelectedIndex(int n) {
        RXSegmentedStepIndicator control = getSkinnable();
        if (control.isCompleted()) {
            return n;
        }
        return RXMath.clamp(control.getSelectedIndex(), 0, n - 1);
    }

    // ==================== Interaction ====================

    private void handleSegmentClicked(MouseEvent event) {
        int index = segmentIndexFrom(event);
        if (index >= 0) {
            getSkinnable().fireEvent(new RXSegmentInteractionEvent(RXSegmentInteractionEvent.CLICKED, index));
        }
    }

    private void handleSegmentEntered(MouseEvent event) {
        int index = segmentIndexFrom(event);
        if (index >= 0) {
            getSkinnable().fireEvent(new RXSegmentInteractionEvent(RXSegmentInteractionEvent.ENTERED, index));
        }
    }

    private int segmentIndexFrom(MouseEvent event) {
        Object source = event.getSource();
        if (source instanceof Region region) {
            Object value = region.getProperties().get(SEGMENT_INDEX_KEY);
            if (value instanceof Integer index) {
                return index;
            }
        }
        return -1;
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
            if (i < titleBoxes.size()) {
                StackPane box = titleBoxes.get(i);
                box.pseudoClassStateChanged(COMPLETED, completed);
                box.pseudoClassStateChanged(SELECTED, selectedState);
                box.pseudoClassStateChanged(UPCOMING, upcoming);
            }
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

    // ==================== Titles ====================

    private double titleRowHeight() {
        double height = 0.0;
        for (StackPane box : titleBoxes) {
            height = Math.max(height, box.prefHeight(-1));
        }
        return snapSizeY(height);
    }

    private double widestTitle() {
        double width = 0.0;
        for (StackPane box : titleBoxes) {
            width = Math.max(width, box.prefWidth(-1));
        }
        return snapSizeX(width);
    }

    private double titleBlockHeight() {
        if (titleBoxes.isEmpty()) {
            return 0.0;
        }
        return RXMath.sanitizeFiniteNonNegative(getSkinnable().getTitleGap()) + titleRowHeight();
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
            for (int i = 0; i < titleBoxes.size(); i++) {
                titleBoxes.get(i).resizeRelocate(contentX, contentY, 0.0, 0.0);
                titleClips.get(i).setWidth(0.0);
                titleClips.get(i).setHeight(0.0);
            }
            return;
        }

        RXSegmentedStepIndicator control = getSkinnable();
        double gap = RXMath.sanitizeFiniteNonNegative(control.getSegmentGap());
        double segmentHeight = RXMath.sanitizeFiniteNonNegative(control.getSegmentHeight());
        double renderedHeight = Math.min(segmentHeight, contentHeight);

        // When the content area is shorter than needed, the segments keep their
        // height and the titles get what is left.
        boolean titled = !titleBoxes.isEmpty();
        double titleGap = titled
                ? Math.min(RXMath.sanitizeFiniteNonNegative(control.getTitleGap()), contentHeight - renderedHeight)
                : 0.0;
        double titleHeight = titled
                ? Math.min(titleRowHeight(), contentHeight - renderedHeight - titleGap)
                : 0.0;
        TitleSide side = control.getTitleSide() == null
                ? RXSegmentedStepIndicator.DEFAULT_TITLE_SIDE
                : control.getTitleSide();
        double top = contentY + (contentHeight - renderedHeight - titleGap - titleHeight) * HALF;
        double y = side == TitleSide.TOP ? top + titleHeight + titleGap : top;
        double titleY = snapPositionY(side == TitleSide.TOP ? top : top + renderedHeight + titleGap);

        double totalGap = gap * Math.max(0, n - 1);
        double segmentWidth = Math.max(0.0, (contentWidth - totalGap) / n);
        for (int i = 0; i < n; i++) {
            double x = contentX + i * (segmentWidth + gap);
            trackRegions.get(i).resizeRelocate(x, y, segmentWidth, renderedHeight);
            fillRegions.get(i).resizeRelocate(x, y, segmentWidth, renderedHeight);

            Rectangle clip = fillClips.get(i);
            clip.setX(0.0);
            clip.setY(0.0);
            clip.setHeight(renderedHeight);

            if (titled) {
                // Snapped so the title text lands on whole pixels.
                double left = snapPositionX(x);
                double width = snapPositionX(x + segmentWidth) - left;
                titleBoxes.get(i).resizeRelocate(left, titleY, width, titleHeight);
                titleClips.get(i).setWidth(width);
                titleClips.get(i).setHeight(titleHeight);
            }
        }
        applyFills();
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        int n = renderedStepCount();
        double gap = RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentGap());
        return leftInset + n * MIN_SEGMENT_WIDTH + gap * Math.max(0, n - 1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        int n = titleBoxes.size();
        double titles = 0.0;
        if (n > 0) {
            double gap = RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentGap());
            titles = n * widestTitle() + gap * (n - 1);
        }
        return leftInset + Math.max(DEFAULT_PREF_WIDTH, titles) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset
                + RXMath.sanitizeFiniteNonNegative(getSkinnable().getSegmentHeight())
                + titleBlockHeight()
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
    protected void disposeSkin() {
        disposed = true;
        clearSegments();
    }
}
