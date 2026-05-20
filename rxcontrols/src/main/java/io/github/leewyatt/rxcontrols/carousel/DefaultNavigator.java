package io.github.leewyatt.rxcontrols.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * The default navigator for {@link RXCarousel}.
 *
 * <p>When the page count is within {@link #getMaxDots()} (default 20),
 * it displays one dot per page — clicking a dot jumps to that page.
 * When the page count exceeds that threshold, it automatically switches
 * to a compact mode: {@code [◄] 3 / 200 [►]} with arrow buttons for
 * navigation.</p>
 */
public class DefaultNavigator implements CarouselNavigator {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final int DEFAULT_MAX_DOTS = 20;

    private int maxDots = DEFAULT_MAX_DOTS;
    private StackPane container;
    private HBox dotBox;
    private final List<Region> dots = new ArrayList<>();
    private int currentIndex = -1;
    private RXCarousel carousel;

    // Compact mode: [◄] label [►]
    private HBox compactBox;
    private Label compactLabel;
    private boolean usingCompact;

    /**
     * Returns the maximum number of dots before this navigator switches
     * to compact mode.
     *
     * @return the maximum dot count
     */
    public int getMaxDots() {
        return maxDots;
    }

    /**
     * Sets the maximum number of dots. Set to 0 to always use compact mode.
     *
     * @param maxDots the maximum dot count
     */
    public void setMaxDots(int maxDots) {
        this.maxDots = maxDots;
    }

    @Override
    public Node createNode(RXCarousel carousel) {
        this.carousel = carousel;

        dotBox = new HBox();
        dotBox.getStyleClass().add("dot-indicator");

        container = new StackPane(dotBox);
        container.getStyleClass().add("carousel-navigator");
        container.setPickOnBounds(false);

        return container;
    }

    @Override
    public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
        if (container == null) {
            return;
        }

        if (pageCount > maxDots) {
            if (!usingCompact) {
                switchToCompact();
            }
            updateCompactLabel(newIndex, pageCount);
            currentIndex = newIndex;
            return;
        }

        if (usingCompact) {
            switchBackToDots();
        }

        rebuildDotsIfNeeded(pageCount);

        if (currentIndex >= 0 && currentIndex < dots.size()) {
            dots.get(currentIndex).pseudoClassStateChanged(SELECTED, false);
        }
        if (newIndex >= 0 && newIndex < dots.size()) {
            dots.get(newIndex).pseudoClassStateChanged(SELECTED, true);
        }
        currentIndex = newIndex;
    }

    @Override
    public void dispose() {
        dots.clear();
        dotBox = null;
        compactBox = null;
        compactLabel = null;
        container = null;
        carousel = null;
        currentIndex = -1;
        usingCompact = false;
    }

    private void rebuildDotsIfNeeded(int pageCount) {
        if (dots.size() == pageCount) {
            return;
        }

        dots.clear();
        dotBox.getChildren().clear();
        currentIndex = -1;

        for (int i = 0; i < pageCount; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("dot");
            final int index = i;
            dot.setOnMouseClicked(e -> {
                if (carousel != null) {
                    carousel.goToPage(index);
                }
            });
            dots.add(dot);
            dotBox.getChildren().add(dot);
        }
    }

    private void switchToCompact() {
        usingCompact = true;
        dots.clear();
        dotBox.getChildren().clear();

        if (compactBox == null) {
            Region prevArrow = new Region();
            prevArrow.getStyleClass().add("arrow");

            Button prevBtn = new Button();
            prevBtn.setGraphic(prevArrow);
            prevBtn.getStyleClass().add("prev-button");
            prevBtn.setOnAction(e -> {
                if (carousel != null) {
                    carousel.previous();
                }
            });

            Region nextArrow = new Region();
            nextArrow.getStyleClass().add("arrow");

            Button nextBtn = new Button();
            nextBtn.setGraphic(nextArrow);
            nextBtn.getStyleClass().add("next-button");
            nextBtn.setOnAction(e -> {
                if (carousel != null) {
                    carousel.next();
                }
            });

            compactLabel = new Label();
            compactLabel.getStyleClass().add("fraction-label");

            compactBox = new HBox(6, prevBtn, compactLabel, nextBtn);
            compactBox.setAlignment(Pos.CENTER);
            compactBox.getStyleClass().add("compact-navigator");
        }
        container.getChildren().setAll(compactBox);
    }

    private void switchBackToDots() {
        usingCompact = false;
        currentIndex = -1;
        container.getChildren().setAll(dotBox);
        dotBox.getChildren().clear();
    }

    private void updateCompactLabel(int newIndex, int pageCount) {
        if (compactLabel != null) {
            if (pageCount == 0) {
                compactLabel.setText("");
            } else {
                compactLabel.setText((newIndex + 1) + " / " + pageCount);
            }
        }
    }
}
