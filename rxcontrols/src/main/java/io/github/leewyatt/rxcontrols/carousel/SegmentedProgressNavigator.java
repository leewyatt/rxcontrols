package io.github.leewyatt.rxcontrols.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.event.RXSegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * Carousel navigator that renders the current auto-play countdown with a
 * segmented step indicator.
 *
 * <p>The navigator consumes {@link RXCarousel#selectedIndexProperty()},
 * {@link RXCarousel#pageCountProperty()}, and
 * {@link RXCarousel#autoPlayProgressProperty()} to display overall progress as
 * {@code (selectedIndex + autoPlayProgress) / pageCount}. When auto-play is
 * disabled, the selected page's segment is rendered filled so the control still
 * behaves as a page navigator.</p>
 *
 * <p>Page counts up to
 * {@link RXSegmentedStepIndicator#MAX_STEP_COUNT} are represented one segment
 * per page. Larger page counts are grouped into the maximum segment count and
 * clicks map to representative pages across the full range.</p>
 */
public class SegmentedProgressNavigator implements CarouselNavigator {

    private static final double DEFAULT_HORIZONTAL_INSET = 24.0;

    private boolean clickToJump = true;
    private boolean hoverToJump;
    private boolean animateJump = true;
    private double horizontalInset = DEFAULT_HORIZONTAL_INSET;

    private RXCarousel carousel;
    private StackPane container;
    private RXSegmentedStepIndicator indicator;

    private final ChangeListener<Number> autoPlayProgressListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> autoPlayListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> circularListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> pageTransitioningListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final EventHandler<RXSegmentInteractionEvent> segmentClickedHandler = this::handleSegmentClicked;
    private final EventHandler<RXSegmentInteractionEvent> segmentEnteredHandler = this::handleSegmentEntered;

    /**
     * Creates a segmented progress navigator.
     */
    public SegmentedProgressNavigator() {
    }

    /**
     * Returns whether clicking a segment navigates to the corresponding page.
     *
     * @return true if segment clicks navigate
     */
    public boolean isClickToJump() {
        return clickToJump;
    }

    /**
     * Sets whether clicking a segment navigates to the corresponding page.
     *
     * @param clickToJump true to enable click navigation
     */
    public void setClickToJump(boolean clickToJump) {
        this.clickToJump = clickToJump;
        updateClickHandler();
        updateCursor();
    }

    /**
     * Returns whether hovering over a segment navigates to the corresponding page.
     *
     * @return true if segment hover navigates
     */
    public boolean isHoverToJump() {
        return hoverToJump;
    }

    /**
     * Sets whether hovering over a segment navigates to the corresponding page.
     *
     * <p>This works best with {@link RXCarousel#setHoverPause(boolean)} set to
     * false; otherwise the carousel's whole-control hover pause will freeze
     * auto-play while the pointer is over the navigator.</p>
     *
     * @param hoverToJump true to enable hover navigation
     */
    public void setHoverToJump(boolean hoverToJump) {
        this.hoverToJump = hoverToJump;
        updateHoverHandler();
        updateCursor();
    }

    /**
     * Returns whether segment navigation uses the carousel transition animation.
     *
     * @return true if jumps are animated
     */
    public boolean isAnimateJump() {
        return animateJump;
    }

    /**
     * Sets whether segment navigation uses the carousel transition animation.
     *
     * @param animateJump true to animate jumps
     */
    public void setAnimateJump(boolean animateJump) {
        this.animateJump = animateJump;
    }

    /**
     * Returns the horizontal inset subtracted from the carousel width.
     *
     * @return the horizontal inset in pixels
     */
    public double getHorizontalInset() {
        return horizontalInset;
    }

    /**
     * Sets the horizontal inset subtracted from the carousel width.
     *
     * @param horizontalInset the horizontal inset in pixels; negative and
     *                        non-finite values are treated as {@code 0}
     */
    public void setHorizontalInset(double horizontalInset) {
        this.horizontalInset = Double.isFinite(horizontalInset) && horizontalInset > 0.0
                ? horizontalInset
                : 0.0;
        bindContainerWidth();
    }

    /**
     * Creates the navigator node for the given carousel.
     *
     * @param carousel the carousel this navigator belongs to
     * @return the navigator node
     */
    @Override
    public Node createNode(RXCarousel carousel) {
        dispose();
        this.carousel = carousel;

        indicator = new RXSegmentedStepIndicator(0);
        indicator.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        indicator.setMinWidth(0.0);

        container = new StackPane(indicator);
        container.getStyleClass().addAll("carousel-navigator", "segmented-progress-navigator");
        container.setMinWidth(0.0);
        container.setPickOnBounds(false);
        bindContainerWidth();

        carousel.autoPlayProgressProperty().addListener(autoPlayProgressListener);
        carousel.autoPlayProperty().addListener(autoPlayListener);
        carousel.circularProperty().addListener(circularListener);
        carousel.pageTransitioningProperty().addListener(pageTransitioningListener);

        updateClickHandler();
        updateHoverHandler();
        updateCursor();
        updateProgress();
        return container;
    }

    /**
     * Updates the navigator after the carousel selected page changes.
     *
     * @param oldIndex  the previous page index, or -1 if no previous page
     * @param newIndex  the new page index
     * @param pageCount the total number of pages
     */
    @Override
    public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
        updateProgress();
    }

    /**
     * Releases listeners and nodes owned by this navigator.
     */
    @Override
    public void dispose() {
        if (carousel != null) {
            carousel.autoPlayProgressProperty().removeListener(autoPlayProgressListener);
            carousel.autoPlayProperty().removeListener(autoPlayListener);
            carousel.circularProperty().removeListener(circularListener);
            carousel.pageTransitioningProperty().removeListener(pageTransitioningListener);
        }
        if (indicator != null) {
            indicator.setOnSegmentClicked(null);
            indicator.setOnSegmentEntered(null);
        }
        if (container != null) {
            container.prefWidthProperty().unbind();
            container.getChildren().clear();
        }

        carousel = null;
        container = null;
        indicator = null;
    }

    private void bindContainerWidth() {
        if (container == null || carousel == null) {
            return;
        }
        container.prefWidthProperty().unbind();
        container.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0.0, carousel.getWidth() - horizontalInset * 2.0),
                carousel.widthProperty()));
    }

    private void updateProgress() {
        if (carousel == null || indicator == null) {
            return;
        }

        int pageCount = carousel.getPageCount();
        if (pageCount <= 0) {
            indicator.setStepCount(0);
            indicator.setSelectedIndex(0);
            indicator.setSegmentProgress(0.0);
            indicator.setVisible(false);
            indicator.setDisable(true);
            return;
        }
        indicator.setVisible(true);
        indicator.setDisable(false);

        int stepCount = RXMath.clamp(pageCount, 1, RXSegmentedStepIndicator.MAX_STEP_COUNT);
        indicator.setStepCount(stepCount);

        int selectedIndex = RXMath.clamp(carousel.getSelectedIndex(), 0, pageCount - 1);
        double pageProgress;
        if (carousel.isPageTransitioning()) {
            pageProgress = 0.0;
        } else if (!carousel.isAutoPlay()
                || (!carousel.isCircular() && selectedIndex >= pageCount - 1)) {
            pageProgress = 1.0;
        } else {
            pageProgress = RXMath.clamp0To1(carousel.getAutoPlayProgress());
        }

        double scaled = (selectedIndex + pageProgress) / pageCount * stepCount;
        if (scaled >= stepCount) {
            indicator.setSelectedIndex(stepCount - 1);
            indicator.setSegmentProgress(1.0);
            return;
        }

        int indicatorSelected = (int) Math.floor(scaled);
        indicator.setSelectedIndex(indicatorSelected);
        indicator.setSegmentProgress(scaled - indicatorSelected);
    }

    private void handleSegmentClicked(RXSegmentInteractionEvent event) {
        navigateToSegment(event.getSegmentIndex());
    }

    private void handleSegmentEntered(RXSegmentInteractionEvent event) {
        navigateToSegment(event.getSegmentIndex());
    }

    private void navigateToSegment(int segmentIndex) {
        if (carousel == null || segmentIndex < 0) {
            return;
        }
        int pageCount = carousel.getPageCount();
        if (pageCount <= 0) {
            return;
        }

        int target = pageIndexForSegment(segmentIndex, pageCount);
        if (target >= 0 && target < pageCount) {
            carousel.goToPage(target, animateJump);
            carousel.requestFocus();
        }
    }

    private int pageIndexForSegment(int segmentIndex, int pageCount) {
        int renderedStepCount = renderedStepCount();
        if (pageCount <= renderedStepCount) {
            return segmentIndex;
        }
        if (renderedStepCount <= 1) {
            return 0;
        }
        return (int) Math.round(segmentIndex * (pageCount - 1.0) / (renderedStepCount - 1.0));
    }

    private int renderedStepCount() {
        if (indicator == null) {
            return 0;
        }
        return RXMath.clamp(indicator.getStepCount(), 0, RXSegmentedStepIndicator.MAX_STEP_COUNT);
    }

    private void updateClickHandler() {
        if (indicator != null) {
            indicator.setOnSegmentClicked(clickToJump ? segmentClickedHandler : null);
        }
    }

    private void updateHoverHandler() {
        if (indicator != null) {
            indicator.setOnSegmentEntered(hoverToJump ? segmentEnteredHandler : null);
        }
    }

    private void updateCursor() {
        if (indicator != null) {
            indicator.setCursor(clickToJump || hoverToJump ? Cursor.HAND : Cursor.DEFAULT);
        }
    }
}
