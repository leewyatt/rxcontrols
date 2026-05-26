package io.github.leewyatt.rxcontrols.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Carousel navigator that renders the current auto-play countdown as a
 * segmented progress bar.
 *
 * <p>The navigator consumes {@link RXCarousel#selectedIndexProperty()},
 * {@link RXCarousel#pageCountProperty()}, and
 * {@link RXCarousel#autoPlayProgressProperty()} to display overall progress as
 * {@code (selectedIndex + autoPlayProgress) / pageCount}. When auto-play is
 * disabled, the selected page's segment is rendered filled so the control still
 * behaves as a page navigator.</p>
 *
 * <p>Page counts up to
 * {@link RXSegmentedProgressBar#MAX_SEGMENT_COUNT} are represented one segment
 * per page. Larger page counts are grouped into the maximum segment count and
 * clicks map to representative pages across the full range.</p>
 */
public class SegmentedProgressNavigator implements CarouselNavigator {

    private static final double DEFAULT_HORIZONTAL_INSET = 24.0;
    private static final double DEFAULT_HIT_HEIGHT = 24.0;

    private boolean clickToJump = true;
    private boolean hoverToJump;
    private boolean animateJump = true;
    private double horizontalInset = DEFAULT_HORIZONTAL_INSET;

    private RXCarousel carousel;
    private StackPane container;
    private RXSegmentedProgressBar progressBar;
    private Region hitLayer;
    private int renderedSegmentCount;
    private int hoverIndex = -1;

    private final ChangeListener<Number> autoPlayProgressListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> autoPlayListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> circularListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final ChangeListener<Boolean> pageTransitioningListener =
            (obs, oldValue, newValue) -> updateProgress();
    private final EventHandler<MouseEvent> mouseClickedHandler = this::handleMouseClicked;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;

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

        progressBar = new RXSegmentedProgressBar(0.0);
        progressBar.setMouseTransparent(true);
        // The carousel already animates autoPlayProgress; a second tween in
        // the inner bar would lag behind the page countdown. Binding also
        // prevents author CSS from reintroducing a transition duration.
        progressBar.progressTransitionDurationProperty().bind(
                new SimpleObjectProperty<>(Duration.ZERO));

        hitLayer = new Region();
        hitLayer.getStyleClass().add("hit-area");
        hitLayer.setMinHeight(DEFAULT_HIT_HEIGHT);
        hitLayer.setPrefHeight(DEFAULT_HIT_HEIGHT);

        hitLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        hitLayer.setPickOnBounds(true);
        hitLayer.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
        hitLayer.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        hitLayer.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);

        container = new StackPane(progressBar, hitLayer);
        container.getStyleClass().addAll("carousel-navigator", "segmented-progress-navigator");
        container.setMinWidth(0.0);
        container.setPickOnBounds(false);
        bindContainerWidth();

        carousel.autoPlayProgressProperty().addListener(autoPlayProgressListener);
        carousel.autoPlayProperty().addListener(autoPlayListener);
        carousel.circularProperty().addListener(circularListener);
        carousel.pageTransitioningProperty().addListener(pageTransitioningListener);

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
        if (hitLayer != null) {
            hitLayer.removeEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
            hitLayer.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            hitLayer.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        }
        if (progressBar != null) {
            progressBar.progressTransitionDurationProperty().unbind();
        }
        if (container != null) {
            container.prefWidthProperty().unbind();
            container.getChildren().clear();
        }

        carousel = null;
        container = null;
        progressBar = null;
        hitLayer = null;
        renderedSegmentCount = 0;
        hoverIndex = -1;
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
        if (carousel == null || progressBar == null) {
            return;
        }

        int pageCount = carousel.getPageCount();
        if (pageCount <= 0) {
            renderedSegmentCount = 0;
            progressBar.setVisible(false);
            hitLayer.setDisable(true);
            progressBar.setProgress(0.0);
            return;
        }
        progressBar.setVisible(true);
        hitLayer.setDisable(false);
        syncSegmentCount(pageCount);

        int selectedIndex = RXMath.clamp(carousel.getSelectedIndex(), 0, pageCount - 1);
        double segmentProgress;
        if (carousel.isPageTransitioning()) {
            segmentProgress = 0.0;
        } else if (!carousel.isAutoPlay()
                || (!carousel.isCircular() && selectedIndex >= pageCount - 1)) {
            segmentProgress = 1.0;
        } else {
            segmentProgress = RXMath.clamp0To1(carousel.getAutoPlayProgress());
        }
        progressBar.setProgress(RXMath.clamp0To1((selectedIndex + segmentProgress) / pageCount));
    }

    private void syncSegmentCount(int pageCount) {
        if (pageCount <= 0) {
            return;
        }
        int next = RXMath.clamp(pageCount, 1, RXSegmentedProgressBar.MAX_SEGMENT_COUNT);
        if (next != renderedSegmentCount) {
            renderedSegmentCount = next;
            progressBar.setSegmentCount(next);
        }
    }

    private void handleMouseClicked(MouseEvent event) {
        if (clickToJump) {
            navigateToSegment(segmentIndexAt(event.getX()));
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        if (!hoverToJump) {
            return;
        }
        int index = segmentIndexAt(event.getX());
        if (index != hoverIndex) {
            hoverIndex = index;
            navigateToSegment(index);
        }
    }

    private void handleMouseExited(MouseEvent event) {
        hoverIndex = -1;
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
        if (pageCount <= renderedSegmentCount) {
            return segmentIndex;
        }
        if (renderedSegmentCount <= 1) {
            return 0;
        }
        return (int) Math.round(segmentIndex * (pageCount - 1.0) / (renderedSegmentCount - 1.0));
    }

    private int segmentIndexAt(double x) {
        if (progressBar == null || hitLayer == null || renderedSegmentCount <= 0) {
            return -1;
        }
        Point2D barPoint = progressBar.sceneToLocal(hitLayer.localToScene(x, 0.0));
        Insets insets = progressBar.getInsets();
        double leftInset = insets.getLeft();
        double rightInset = insets.getRight();
        double width = progressBar.getWidth() - leftInset - rightInset;
        double localX = barPoint.getX() - leftInset;
        if (width <= 0.0 || localX < 0.0 || localX > width) {
            return -1;
        }

        double gap = Math.max(0.0, progressBar.getSegmentGap());
        double totalGap = gap * Math.max(0, renderedSegmentCount - 1);
        double segmentWidth = (width - totalGap) / renderedSegmentCount;
        if (segmentWidth <= 0.0) {
            int index = (int) Math.floor(localX / width * renderedSegmentCount);
            return RXMath.clamp(index, 0, renderedSegmentCount - 1);
        }

        for (int i = 0; i < renderedSegmentCount; i++) {
            double start = i * (segmentWidth + gap);
            double end = start + segmentWidth;
            if (localX >= start && (localX < end || i == renderedSegmentCount - 1 && localX <= end)) {
                return i;
            }
        }
        return -1;
    }

    private void updateCursor() {
        if (hitLayer != null) {
            hitLayer.setCursor(clickToJump || hoverToJump ? Cursor.HAND : Cursor.DEFAULT);
        }
    }
}
