package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Around transition showing left-center-right pages with scaling perspective.
 * The center page is larger, side pages are smaller.
 *
 * <p>Requires at least 3 pages. When transitioning, the center page moves
 * to one side (shrinking), the incoming side page moves to center (growing),
 * and the back page rotates around from the opposite side.</p>
 *
 * <p>When {@code showSidePages} is false (default), the animation has three
 * phases: first the full-screen page shrinks and side pages slide in, then the
 * around rotation plays, and finally the new center page scales back to full
 * size. When true, side pages remain visible and no scaling bookends are
 * played.</p>
 *
 * <p><b>Note:</b> This animation is not resize-safe due to its complex
 * multi-page z-order management and piecewise motion. If the host
 * is resized while this animation is playing, the animation will jump
 * to its end state automatically.</p>
 */
public class AnimAround extends PageAnimationBase {

    private static final int MIN_PAGES = 3;

    private final boolean showSidePages;
    private final double middleScale;
    private final double sideScale;
    private double scaleTimeRatio = 0.2;

    /**
     * Creates an around animation with default parameters.
     * Center page at 0.7x scale, sides at 0.5x, with zoom bookends.
     */
    public AnimAround() {
        this(false);
    }

    /**
     * Creates an around animation.
     *
     * @param showSidePages if true, side pages remain visible after transition
     *                      (gallery mode); if false, the center page scales to
     *                      full size with zoom bookends at start and end
     */
    public AnimAround(boolean showSidePages) {
        this(0.7, 0.5, showSidePages);
    }

    /**
     * Creates an around animation with custom scale values.
     *
     * @param middleScale   the scale for the center page (must be &lt; 1 and &gt; sideScale)
     * @param sideScale     the scale for side pages (must be &lt; middleScale)
     * @param showSidePages if true, side pages remain visible after transition
     */
    public AnimAround(double middleScale, double sideScale, boolean showSidePages) {
        if (middleScale <= sideScale || middleScale >= 1 || sideScale >= 1) {
            throw new IllegalArgumentException(
                    "middleScale must be > sideScale and both must be < 1");
        }
        this.middleScale = middleScale;
        this.sideScale = sideScale;
        this.showSidePages = showSidePages;
    }

    /**
     * Returns the fraction of total duration used for each zoom bookend
     * (the opening shrink and the closing expand). Only applies when
     * {@code showSidePages} is false.
     *
     * @return the scale time ratio (0.0 to 0.4)
     */
    public double getScaleTimeRatio() {
        return scaleTimeRatio;
    }

    /**
     * Sets the fraction of total duration used for each zoom bookend.
     *
     * @param scaleTimeRatio value between 0.0 and 0.4 (clamped; values above 0.4
     *                       leave too little time for the main rotation)
     */
    public void setScaleTimeRatio(double scaleTimeRatio) {
        this.scaleTimeRatio = Math.max(0.0, Math.min(0.4, scaleTimeRatio));
    }

    @Override
    public int getMinimumPageCount() {
        return MIN_PAGES;
    }

    @Override
    public boolean isMultiPageDisplay() {
        return showSidePages;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        int pageCount = context.getPageCount();
        int currentIndex = context.getCurrentIndex();
        int nextIndex = context.getNextIndex();
        boolean forward = direction == TransitionDirection.FORWARD;
        double paneWidth = contentPane.getWidth();

        // Determine the third page that moves in the background
        int maxIdx = pageCount - 1;
        int sideToCenterBackIdx;
        int centerBackToSideIdx;

        if (forward) {
            sideToCenterBackIdx = currentIndex == 0 ? maxIdx : currentIndex - 1;
            centerBackToSideIdx = nextIndex == maxIdx ? 0 : nextIndex + 1;
            if (currentIndex == 0 && nextIndex == maxIdx) {
                sideToCenterBackIdx = maxIdx - 1;
                centerBackToSideIdx = sideToCenterBackIdx;
            }
        } else {
            sideToCenterBackIdx = currentIndex == maxIdx ? 0 : currentIndex + 1;
            centerBackToSideIdx = nextIndex == 0 ? maxIdx : nextIndex - 1;
            if (nextIndex == 0 && currentIndex == maxIdx) {
                sideToCenterBackIdx = 1;
                centerBackToSideIdx = sideToCenterBackIdx;
            }
        }

        // Fetch all involved pages
        Node sideToCenterBackPage = context.getPage(sideToCenterBackIdx);
        Node centerBackToSidePage = (sideToCenterBackIdx == centerBackToSideIdx || pageCount == MIN_PAGES)
                ? null
                : context.getPage(centerBackToSideIdx);

        installResizeGuard(contentPane);

        double scaledWidth = paneWidth * sideScale;
        double offsetX = (paneWidth - scaledWidth) / 2;

        // Duration allocation
        double bookendRatio = showSidePages ? 0 : scaleTimeRatio;
        Duration bookendTime = duration.multiply(bookendRatio);
        Duration mainTime = duration.multiply(1 - 2 * bookendRatio);
        Duration halfMainTime = mainTime.divide(2.0);

        // ===== Phase 1: Zoom-out bookend (showSidePages=false only) =====
        // Current page shrinks from 1.0 to middleScale, side pages slide in from offscreen

        ParallelTransition zoomOutAnim = null;
        if (!showSidePages) {
            // Start: currentPage at full size, others hidden offscreen
            for (Node child : contentPane.getChildren()) {
                child.setVisible(false);
            }
            if (currentPage != null) {
                currentPage.setVisible(true);
                setScale(1.0, currentPage);
                currentPage.toFront();
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                setScale(sideScale, nextPage);
                // Start offscreen, slide to side position
                nextPage.setTranslateX(forward ? paneWidth : -paneWidth);
            }
            if (sideToCenterBackPage != null) {
                sideToCenterBackPage.setVisible(true);
                setScale(sideScale, sideToCenterBackPage);
                sideToCenterBackPage.setTranslateX(forward ? -paneWidth : paneWidth);
                sideToCenterBackPage.toBack();
            }
            if (centerBackToSidePage != null) {
                centerBackToSidePage.setVisible(false);
                setScale(sideScale, centerBackToSidePage);
            }

            // Current page shrinks
            ScaleTransition shrinkCenter = createScale(bookendTime, currentPage, 1.0, middleScale);

            // Side pages slide in to their positions
            TranslateTransition slideNextIn = createMove(bookendTime, nextPage,
                    forward ? paneWidth : -paneWidth, forward ? offsetX : -offsetX);
            TranslateTransition slideBackIn = createMove(bookendTime, sideToCenterBackPage,
                    forward ? -paneWidth : paneWidth, forward ? -offsetX : offsetX);

            zoomOutAnim = new ParallelTransition(shrinkCenter, slideNextIn, slideBackIn);
        } else {
            // showSidePages=true: start already in scaled layout
            for (Node child : contentPane.getChildren()) {
                child.setVisible(false);
            }
            if (currentPage != null) {
                currentPage.setVisible(true);
                setScale(middleScale, currentPage);
                currentPage.toFront();
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                setScale(sideScale, nextPage);
                nextPage.setTranslateX(forward ? offsetX : -offsetX);
            }
            if (sideToCenterBackPage != null) {
                sideToCenterBackPage.setVisible(true);
                setScale(sideScale, sideToCenterBackPage);
                sideToCenterBackPage.setTranslateX(forward ? -offsetX : offsetX);
                sideToCenterBackPage.toBack();
            }
            if (centerBackToSidePage != null) {
                centerBackToSidePage.setVisible(false);
                setScale(sideScale, centerBackToSidePage);
            }
        }

        // ===== Phase 2: Main around rotation =====

        // Side → Middle (next page moves to center, grows)
        TranslateTransition moveSideToMiddle = createMove(mainTime, nextPage,
                forward ? offsetX : -offsetX, 0);
        ScaleTransition scaleSideToBigger = createScale(mainTime, nextPage,
                sideScale, middleScale);

        // Middle → Side (current page moves away, shrinks)
        TranslateTransition moveMiddleToSide = createMove(mainTime, currentPage,
                0, forward ? -offsetX : offsetX);
        ScaleTransition scaleMiddleToSmaller = createScale(mainTime, currentPage,
                middleScale, sideScale);

        // Background page: side → center back → other side
        TranslateTransition moveSideToCenterBack = createMove(halfMainTime, sideToCenterBackPage,
                forward ? -offsetX : offsetX, 0);
        moveSideToCenterBack.setInterpolator(Interpolator.LINEAR);

        ScaleTransition scaleSideToCenterBack = createScale(halfMainTime, sideToCenterBackPage,
                sideScale, sideScale);

        Node secondHalfNode = centerBackToSidePage != null ? centerBackToSidePage : sideToCenterBackPage;
        TranslateTransition moveCenterBackToSide = createMove(halfMainTime, secondHalfNode,
                0, forward ? offsetX : -offsetX);
        moveCenterBackToSide.setInterpolator(Interpolator.LINEAR);

        ScaleTransition scaleCenterBackToSide = createScale(halfMainTime,
                centerBackToSidePage != null ? sideToCenterBackPage : secondHalfNode,
                sideScale, sideScale);

        // At midpoint: swap visibility and z-order
        final Node finalCenterBackToSidePage = centerBackToSidePage;
        moveSideToCenterBack.setOnFinished(e -> {
            if (nextPage != null) {
                nextPage.toFront();
            }
            if (finalCenterBackToSidePage != null) {
                sideToCenterBackPage.setVisible(false);
                finalCenterBackToSidePage.toBack();
                finalCenterBackToSidePage.setVisible(true);
                setScale(sideScale, finalCenterBackToSidePage);
            }
        });

        SequentialTransition moveSideToSide = new SequentialTransition(
                moveSideToCenterBack, moveCenterBackToSide);
        moveSideToSide.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition aroundAnim = new ParallelTransition(
                moveSideToMiddle, scaleSideToBigger,
                moveMiddleToSide, scaleMiddleToSmaller,
                moveSideToSide, scaleSideToCenterBack, scaleCenterBackToSide);

        // ===== Phase 3: Zoom-in bookend (showSidePages=false only) =====

        ScaleTransition zoomInAnim = null;
        if (!showSidePages) {
            zoomInAnim = createScale(bookendTime, nextPage, middleScale, 1.0);
        }

        // ===== Finish action =====

        int leftOfNextIdx = (nextIndex - 1 + pageCount) % pageCount;
        int rightOfNextIdx = (nextIndex + 1) % pageCount;
        Node leftOfNext = context.getPage(leftOfNextIdx);
        Node rightOfNext = context.getPage(rightOfNextIdx);

        Runnable finish = () -> {
            removeResizeGuard();
            for (Node child : contentPane.getChildren()) {
                child.setTranslateX(0);
                child.setVisible(false);
                setScale(1.0, child);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.setTranslateX(0);
            }
            if (showSidePages) {
                setScale(middleScale, nextPage);
                double sw = paneWidth * sideScale;
                double ox = (paneWidth - sw) / 2;
                if (leftOfNext != null) {
                    leftOfNext.setVisible(true);
                    setScale(sideScale, leftOfNext);
                    leftOfNext.setTranslateX(-ox);
                }
                if (rightOfNext != null) {
                    rightOfNext.setVisible(true);
                    setScale(sideScale, rightOfNext);
                    rightOfNext.setTranslateX(ox);
                }
            }
        };
        setFinishAction(finish);

        // ===== Assemble timeline =====

        // Fire events at start
        PauseTransition startPause = new PauseTransition(Duration.ZERO);
        startPause.setOnFinished(e -> {
            context.fireClosing(context.getCurrentIndex());
            context.fireOpening(context.getNextIndex());
        });

        SequentialTransition animation = new SequentialTransition();
        animation.getChildren().add(startPause);
        if (zoomOutAnim != null) {
            animation.getChildren().add(zoomOutAnim);
        }
        animation.getChildren().add(aroundAnim);
        if (zoomInAnim != null) {
            animation.getChildren().add(zoomInAnim);
        }

        animation.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(animation);
        return animation;
    }

    private TranslateTransition createMove(Duration duration, Node node,
                                            double fromX, double toX) {
        TranslateTransition tt = new TranslateTransition(duration);
        if (node != null) {
            tt.setNode(node);
        }
        tt.setFromX(fromX);
        tt.setToX(toX);
        return tt;
    }

    private ScaleTransition createScale(Duration duration, Node node,
                                         double fromScale, double toScale) {
        ScaleTransition st = new ScaleTransition(duration);
        if (node != null) {
            st.setNode(node);
        }
        st.setFromX(fromScale);
        st.setFromY(fromScale);
        st.setToX(toScale);
        st.setToY(toScale);
        return st;
    }

    private void setScale(double scale, Node node) {
        if (node != null) {
            node.setScaleX(scale);
            node.setScaleY(scale);
        }
    }

    @Override
    public void setupInitialLayout(TransitionContext context) {
        if (!showSidePages) {
            return;
        }
        applySidePagesLayout(context);
    }

    private void applySidePagesLayout(TransitionContext context) {
        StackPane contentPane = context.getContentPane();
        int centerIndex = context.getNextIndex();
        int pageCount = context.getPageCount();
        Node centerPage = context.getPage(centerIndex);

        double w = contentPane.getWidth();
        double sw = w * sideScale;
        double ox = (w - sw) / 2;

        for (Node child : contentPane.getChildren()) {
            child.setTranslateX(0);
            child.setVisible(false);
            setScale(1.0, child);
        }

        if (centerPage != null) {
            centerPage.setVisible(true);
            setScale(middleScale, centerPage);
            centerPage.setTranslateX(0);
            centerPage.toFront();
        }

        if (pageCount >= MIN_PAGES) {
            int leftIdx = (centerIndex - 1 + pageCount) % pageCount;
            int rightIdx = (centerIndex + 1) % pageCount;

            Node leftPage = context.getPage(leftIdx);
            Node rightPage = context.getPage(rightIdx);

            if (leftPage != null) {
                leftPage.setVisible(true);
                setScale(sideScale, leftPage);
                leftPage.setTranslateX(-ox);
                leftPage.toBack();
            }
            if (rightPage != null) {
                rightPage.setVisible(true);
                setScale(sideScale, rightPage);
                rightPage.setTranslateX(ox);
                rightPage.toBack();
            }
        }
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        removeResizeGuard();
        for (Node child : context.getContentPane().getChildren()) {
            child.setScaleX(1.0);
            child.setScaleY(1.0);
            child.setTranslateX(0);
        }
    }
}
