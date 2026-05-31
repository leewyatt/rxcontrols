package io.github.leewyatt.rxcontrols.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimNone;
import io.github.leewyatt.rxcontrols.carousel.animation.CarouselAnimation;
import io.github.leewyatt.rxcontrols.carousel.animation.TransitionContext;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.skins.RXSkinBase;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Default skin for the {@link RXCarousel} control.
 */
public class CarouselSkin extends RXSkinBase<RXCarousel> {

    private static final Duration MIN_AUTOPLAY_INTERVAL = Duration.millis(100);
    private static final Duration FADE_DURATION = Duration.millis(200);
    private static final AnimNone FALLBACK_NONE = new AnimNone();

    private final StackPane contentPane;
    private final Rectangle contentClip;


    // Navigation arrows
    private final StackPane prevButton;
    private final StackPane nextButton;
    private final Region prevArrow;
    private final Region nextArrow;

    // Navigator node (managed by CarouselNavigator)
    private Node navigatorNode;

    // Page cache
    private final Map<Integer, Node> pageCache = new HashMap<>();

    // Animation state
    private Animation currentTransition;
    private CarouselAnimation usedAnimation;
    private boolean transitioning;
    private int animatingFromIndex = -1;
    private int animatingToIndex = -1;

    // Auto play (Timeline drives autoPlayProgress from 0→1)
    private Timeline autoPlayTimeline;
    private final DoubleProperty autoPlayProgressInternal = new SimpleDoubleProperty(0);

    // Fade animations for arrows and navigator
    private FadeTransition prevButtonFade;
    private FadeTransition nextButtonFade;
    private FadeTransition navigatorFade;

    // Mouse hover state (for hoverPause interaction with keyboard navigation)
    private boolean mouseHovering;

    // Tree-showing state
    private final ReadOnlyBooleanProperty treeShowing;

    // Handlers for dynamic nodes
    private final EventHandler<MouseEvent> navigatorFocusHandler = e -> getSkinnable().requestFocus();

    /**
     * Creates the default skin for the given carousel.
     *
     * @param carousel the carousel control
     */
    public CarouselSkin(RXCarousel carousel) {
        super(carousel);

        treeShowing = controlTreeShowingProperty();

        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");

        contentClip = new Rectangle();
        contentPane.setClip(contentClip);

        // Navigation arrows
        prevArrow = new Region();
        prevArrow.getStyleClass().add("arrow");
        prevButton = new StackPane(prevArrow);
        prevButton.getStyleClass().add("prev-button");
        disposer.registerEventHandler(prevButton, MouseEvent.MOUSE_CLICKED, e -> {
            carousel.previous();
            carousel.requestFocus();
        });

        nextArrow = new Region();
        nextArrow.getStyleClass().add("arrow");
        nextButton = new StackPane(nextArrow);
        nextButton.getStyleClass().add("next-button");
        disposer.registerEventHandler(nextButton, MouseEvent.MOUSE_CLICKED, e -> {
            carousel.next();
            carousel.requestFocus();
        });

        getChildren().addAll(contentPane, prevButton, nextButton);

        // Sync internal progress property to the public read-only property on RXCarousel
        autoPlayProgressInternal.addListener((obs, oldVal, newVal) ->
                carousel.setAutoPlayProgress(newVal.doubleValue()));

        setupListeners(carousel);
        initAutoPlayTimer(carousel);
        updateNavigator(null, carousel.getNavigator(), carousel);
        updateNavigatorVisibility(carousel);
        updateArrowVisibility(carousel);
        updateArrowDisabledState(carousel);

        if (shouldShowPlaceholder(carousel)) {
            showPlaceholder(carousel);
        } else {
            initializeCurrentPage(carousel);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        contentPane.resizeRelocate(x, y, w, h);
        contentClip.setWidth(w);
        contentClip.setHeight(h);

        if (placeholderRegion != null && placeholderRegion.isVisible()) {
            placeholderRegion.resizeRelocate(x, y, w, h);
        }

        double arrowW = prevButton.prefWidth(-1);
        double arrowH = prevButton.prefHeight(-1);
        double arrowY = y + (h - arrowH) / 2;
        prevButton.resizeRelocate(x + 10, arrowY, arrowW, arrowH);

        double nextArrowW = nextButton.prefWidth(-1);
        double nextArrowH = nextButton.prefHeight(-1);
        double nextArrowY = y + (h - nextArrowH) / 2;
        nextButton.resizeRelocate(x + w - nextArrowW - 10, nextArrowY, nextArrowW, nextArrowH);

        if (navigatorNode != null && navigatorNode.isManaged()) {
            double navW = navigatorNode.prefWidth(-1);
            double navH = navigatorNode.prefHeight(-1);
            double navX = x + (w - navW) / 2;
            double navY = y + h - navH - 10;
            navigatorNode.resizeRelocate(navX, navY, navW, navH);
        }

        RXCarousel carousel = getSkinnable();
        CarouselAnimation anim = carousel.getAnimation();
        if (!transitioning && anim != null && anim.isMultiPageDisplay()) {
            int idx = carousel.getSelectedIndex();
            if (idx >= 0 && idx < carousel.getPageCount()) {
                TransitionContext ctx = buildContext(idx, idx, carousel);
                anim.setupInitialLayout(ctx);
            }
        }
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return 600 + leftInset + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return 400 + topInset + bottomInset;
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

    // ==================== Listeners ====================

    private void setupListeners(RXCarousel carousel) {
        // Selected index change -> play animation
        disposer.registerListener(carousel.selectedIndexProperty(), (obs, oldVal, newVal) -> {
            int oldIndex = oldVal.intValue();
            int newIndex = newVal.intValue();
            if (oldIndex != newIndex) {
                onSelectedIndexChanged(oldIndex, newIndex, carousel);
            }
        });

        // Page count change
        disposer.registerListener(carousel.pageCountProperty(), (obs, oldVal, newVal) -> {
            int count = newVal.intValue();
            if (shouldShowPlaceholder(carousel)) {
                showPlaceholder(carousel);
            } else {
                hidePlaceholder(carousel);
                if (carousel.getSelectedIndex() >= count) {
                    carousel.setSelectedIndex(count - 1);
                }
                initializeCurrentPage(carousel);
            }
            updateArrowDisabledState(carousel);
            notifyNavigator(-1, carousel);
        });

        // Page factory change -> clear cache and reinitialize
        disposer.registerListener(carousel.pageFactoryProperty(), (obs, oldVal, newVal) -> {
            clearAllPagesFromCache(carousel);
            if (shouldShowPlaceholder(carousel)) {
                showPlaceholder(carousel);
            } else {
                hidePlaceholder(carousel);
                initializeCurrentPage(carousel);
            }
        });

        // Circular mode change
        disposer.registerListener(carousel.circularProperty(), (obs, oldVal, newVal) -> {
            updateArrowDisabledState(carousel);
            // When switching back to circular while autoPlay is on and
            // the timer was stopped (e.g., at the last page in non-circular
            // mode), restart it so auto-play resumes.
            if (newVal && carousel.isAutoPlay() && treeShowing.get() && !transitioning) {
                startAutoplay(carousel);
            }
        });

        // Arrow display mode
        disposer.registerListener(carousel.arrowDisplayModeProperty(), (obs, oldVal, newVal) -> {
            updateArrowVisibility(carousel);
        });

        // Navigator
        disposer.registerListener(carousel.navigatorProperty(), (obs, oldVal, newVal) ->
                updateNavigator(oldVal, newVal, carousel));

        // Navigator display mode
        disposer.registerListener(carousel.navigatorDisplayModeProperty(),
                (obs, oldVal, newVal) -> updateNavigatorVisibility(carousel));

        // Placeholder
        disposer.registerListener(carousel.placeholderProperty(), (obs, oldVal, newVal) -> {
            if (shouldShowPlaceholder(carousel)) {
                showPlaceholder(carousel);
            }
        });

        // Auto play properties
        disposer.registerListener(carousel.autoPlayProperty(), (obs, oldVal, newVal) -> {
            if (newVal && treeShowing.get()) {
                startAutoplay(carousel);
            } else {
                stopAutoplay(carousel);
            }
        });

        disposer.registerListener(carousel.autoPlayIntervalProperty(), (obs, oldVal, newVal) -> {
            if (carousel.isAutoPlay()) {
                restartAutoplay(carousel);
            }
        });

        // Animation type change — deferred until the next transition.
        // If no animation is running, multi-page display animations need
        // immediate layout cleanup/setup because their side pages are visible.
        // If an animation IS running, we do nothing here; the running
        // animation plays to completion and onSelectedIndexChanged handles
        // the cleanup on the next page change (anim != usedAnimation check).
        disposer.registerListener(carousel.animationProperty(), (obs, oldAnim, newAnim) -> {
            if (transitioning) {
                return;
            }

            if (oldAnim != null && oldAnim.isMultiPageDisplay()) {
                int idx = carousel.getSelectedIndex();
                if (idx >= 0 && idx < carousel.getPageCount()) {
                    TransitionContext ctx = buildContext(idx, idx, carousel);
                    oldAnim.clearEffects(ctx);
                }
                hideNonCurrentPages(carousel);
            }

            if (newAnim != null && newAnim.isMultiPageDisplay()) {
                int idx = carousel.getSelectedIndex();
                if (idx >= 0 && idx < carousel.getPageCount()) {
                    TransitionContext ctx = buildContext(idx, idx, carousel);
                    newAnim.setupInitialLayout(ctx);
                }
            }
        });

        // Mouse enter/exit for hover effects
        disposer.registerEventHandler(carousel, MouseEvent.MOUSE_ENTERED,
                e -> onMouseEntered(carousel));
        disposer.registerEventHandler(carousel, MouseEvent.MOUSE_EXITED,
                e -> onMouseExited(carousel));

        // Keyboard navigation
        disposer.registerEventHandler(carousel, KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.KP_LEFT) {
                carousel.previous();
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.KP_RIGHT) {
                carousel.next();
                e.consume();
            }
        });

        installTreeShowingListener(carousel);
    }

    // ==================== Page Management ====================

    private void initializeCurrentPage(RXCarousel carousel) {
        int count = carousel.getPageCount();
        if (count == 0 || carousel.getPageFactory() == null) {
            return;
        }
        int index = carousel.getSelectedIndex();
        if (index >= count) {
            index = 0;
        }
        Node page = getOrCreatePage(index, carousel);
        if (page != null) {
            if (!contentPane.getChildren().contains(page)) {
                contentPane.getChildren().add(page);
            }
            page.setVisible(true);
            // Hide all other pages
            for (Node child : contentPane.getChildren()) {
                if (child != page) {
                    child.setVisible(false);
                }
            }
            ensureAdjacentPages(index, carousel);

            CarouselAnimation anim = carousel.getAnimation();
            if (anim != null && anim.isMultiPageDisplay()) {
                TransitionContext ctx = buildContext(index, index, carousel);
                anim.setupInitialLayout(ctx);
            }

            // Fire lifecycle events for the initial page (no animation, so both fire immediately)
            carousel.fireEvent(new PageLifecycleEvent(PageLifecycleEvent.OPENING, index, page));
            carousel.fireEvent(new PageLifecycleEvent(PageLifecycleEvent.OPENED, index, page));
        }
        notifyNavigator(-1, carousel);
    }

    Node getOrCreatePage(int index, RXCarousel carousel) {
        return pageCache.computeIfAbsent(index, i -> {
            Callback<Integer, Node> factory = carousel.getPageFactory();
            if (factory == null) {
                return null;
            }
            Node page = factory.call(i);
            if (page != null) {
                carousel.fireEvent(new PageLifecycleEvent(
                        PageLifecycleEvent.CACHED, i, page));
            }
            return page;
        });
    }

    private void ensureAdjacentPages(int currentIndex, RXCarousel carousel) {
        int count = carousel.getPageCount();
        if (count <= 1) {
            return;
        }

        if (carousel.isCircular()) {
            getOrCreatePage((currentIndex - 1 + count) % count, carousel);
            getOrCreatePage((currentIndex + 1) % count, carousel);
        } else {
            if (currentIndex > 0) {
                getOrCreatePage(currentIndex - 1, carousel);
            }
            if (currentIndex < count - 1) {
                getOrCreatePage(currentIndex + 1, carousel);
            }
        }
    }

    private void evictDistantPages(int currentIndex, RXCarousel carousel) {
        int distance = carousel.getCacheDistance();
        if (distance < 0) {
            return;
        }

        int count = carousel.getPageCount();
        Set<Integer> keep = new HashSet<>();
        for (int d = -distance; d <= distance; d++) {
            int idx;
            if (carousel.isCircular()) {
                idx = ((currentIndex + d) % count + count) % count;
            } else {
                idx = currentIndex + d;
                if (idx < 0 || idx >= count) {
                    continue;
                }
            }
            keep.add(idx);
        }

        Iterator<Map.Entry<Integer, Node>> it = pageCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Node> entry = it.next();
            if (!keep.contains(entry.getKey())) {
                Node removed = entry.getValue();
                contentPane.getChildren().remove(removed);
                carousel.fireEvent(new PageLifecycleEvent(
                        PageLifecycleEvent.EVICTED, entry.getKey(), removed));
                it.remove();
            }
        }
    }

    /**
     * Clears the entire page cache. Called from {@link RXCarousel#clearPageCache()}.
     */
    public void clearPageCache() {
        RXCarousel carousel = getSkinnable();
        Iterator<Map.Entry<Integer, Node>> it = pageCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Node> entry = it.next();
            Node removed = entry.getValue();
            contentPane.getChildren().remove(removed);
            carousel.fireEvent(new PageLifecycleEvent(
                    PageLifecycleEvent.EVICTED, entry.getKey(), removed));
            it.remove();
        }
        initializeCurrentPage(carousel);
    }

    /**
     * Clears a single cached page. Called from {@link RXCarousel#clearPageCache(int)}.
     *
     * @param index the page index to clear
     */
    public void clearPageCache(int index) {
        RXCarousel carousel = getSkinnable();
        Node removed = pageCache.remove(index);
        if (removed != null) {
            contentPane.getChildren().remove(removed);
            carousel.fireEvent(new PageLifecycleEvent(
                    PageLifecycleEvent.EVICTED, index, removed));
            if (index == carousel.getSelectedIndex()) {
                initializeCurrentPage(carousel);
            }
        }
    }

    private void clearAllPagesFromCache(RXCarousel carousel) {
        Iterator<Map.Entry<Integer, Node>> it = pageCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Node> entry = it.next();
            Node removed = entry.getValue();
            contentPane.getChildren().remove(removed);
            carousel.fireEvent(new PageLifecycleEvent(
                    PageLifecycleEvent.EVICTED, entry.getKey(), removed));
            it.remove();
        }
    }

    // ==================== Animation Scheduling ====================

    private void onSelectedIndexChanged(int oldIndex, int newIndex, RXCarousel carousel) {
        if (carousel.getPageFactory() == null || carousel.getPageCount() == 0) {
            return;
        }

        // Jump current animation to end if running
        if (transitioning && usedAnimation != null) {
            usedAnimation.jumpToEnd();

            // Supplement lifecycle events that were lost because onFinished
            // does not run when an animation is stopped. Animations fire
            // CLOSING/OPENING in getAnimation() and CLOSED/OPENED in
            // onFinished(), so CLOSED and OPENED are always the missing pair.
            if (animatingFromIndex >= 0) {
                Node fromPage = pageCache.get(animatingFromIndex);
                if (fromPage != null) {
                    carousel.fireEvent(new PageLifecycleEvent(
                            PageLifecycleEvent.CLOSED, animatingFromIndex, fromPage));
                }
            }
            if (animatingToIndex >= 0) {
                Node toPage = pageCache.get(animatingToIndex);
                if (toPage != null) {
                    carousel.fireEvent(new PageLifecycleEvent(
                            PageLifecycleEvent.OPENED, animatingToIndex, toPage));
                }
            }

            transitioning = false;
            carousel.setPageTransitioning(false);
            currentTransition = null;
            animatingFromIndex = -1;
            animatingToIndex = -1;
        }

        // Fallback to AnimNone when animation is null
        final CarouselAnimation anim;
        CarouselAnimation configuredAnim = carousel.getAnimation();
        anim = (configuredAnim != null) ? configuredAnim : FALLBACK_NONE;

        // Clear effects from previous animation if animation type changed
        if (usedAnimation != null && anim != usedAnimation) {
            TransitionContext clearContext = buildContext(oldIndex, newIndex, carousel);
            usedAnimation.clearEffects(clearContext);

            // When switching away from a multi-page display animation,
            // remove residual side pages from contentPane. They remain
            // in pageCache and will be re-added when needed. This
            // mirrors the cleanup in animationListener (idle path) and
            // prevents leftover visible side pages from interfering
            // with the new animation and causing depth buffer artifacts.
            if (usedAnimation.isMultiPageDisplay()) {
                Node oldCurrent = pageCache.get(oldIndex);
                contentPane.getChildren().removeIf(child -> child != oldCurrent);
            }
        }

        Node currentPage = pageCache.get(oldIndex);
        Node nextPage = getOrCreatePage(newIndex, carousel);

        if (nextPage == null) {
            return;
        }

        // Ensure current page is in the scene graph (it may have been removed
        // by a previous hideNonCurrentPages call)
        if (currentPage != null && !contentPane.getChildren().contains(currentPage)) {
            contentPane.getChildren().add(currentPage);
            currentPage.setVisible(true);
        }

        // Ensure next page is in the scene graph
        if (!contentPane.getChildren().contains(nextPage)) {
            nextPage.setVisible(false);
            contentPane.getChildren().add(nextPage);
        }

        // Notify navigator immediately so dots update before animation starts
        notifyNavigator(oldIndex, carousel);

        // If animate flag is false or page count too low, direct cut
        if (!carousel.isAnimateTransition()
                || carousel.getPageCount() < anim.getMinimumPageCount()) {
            directCut(currentPage, nextPage, oldIndex, newIndex, carousel);
            return;
        }

        // Build context and play animation
        TransitionContext context = buildContext(oldIndex, newIndex, carousel);

        Animation transition = anim.getAnimation(context);
        usedAnimation = anim;
        currentTransition = transition;
        transitioning = true;
        carousel.setPageTransitioning(true);
        animatingFromIndex = oldIndex;
        animatingToIndex = newIndex;

        // Wrap the animation's own onFinished (which handles page visibility)
        // rather than overwriting it
        EventHandler<ActionEvent> animHandler = transition.getOnFinished();
        transition.setOnFinished(e -> {
            if (animHandler != null) {
                animHandler.handle(e);
            }
            transitioning = false;
            carousel.setPageTransitioning(false);
            currentTransition = null;
            animatingFromIndex = -1;
            animatingToIndex = -1;
            if (!anim.isMultiPageDisplay()) {
                hideNonCurrentPages(carousel);
            }
            onTransitionFinished(oldIndex, newIndex, carousel);
        });

        // Catch external stops (e.g., resizeGuard calling jumpToEnd).
        // When an animation is stopped externally, onFinished does NOT fire,
        // leaving transitioning=true and autoplay dead. This listener
        // resets the state flags and restarts autoplay.
        //
        // IMPORTANT: Do NOT call hideNonCurrentPages or onTransitionFinished
        // here. This listener fires during animation.stop(), BEFORE
        // finishAction runs. Calling hideNonCurrentPages would remove pages
        // from contentPane before finishAction can reset their visual
        // properties (scale, translate), leaving stale values in pageCache.
        //
        // When the animation finishes naturally, onFinished fires FIRST
        // (setting transitioning=false), then status changes to STOPPED.
        // The transitioning guard prevents double execution.
        transition.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == Animation.Status.STOPPED && transitioning) {
                transitioning = false;
                carousel.setPageTransitioning(false);
                currentTransition = null;
                animatingFromIndex = -1;
                animatingToIndex = -1;
                if (carousel.isAutoPlay() && treeShowing.get()
                        && !(mouseHovering && carousel.isHoverPause())) {
                    startAutoplay(carousel);
                }
            }
        });

        // Stop autoplay and reset progress while animating; it will be
        // restarted in onTransitionFinished after the animation completes.
        stopAutoplay(carousel);

        transition.play();
    }

    private void directCut(Node currentPage, Node nextPage, int oldIndex, int newIndex,
                           RXCarousel carousel) {
        stopAutoplay(carousel);
        if (currentPage != null) {
            carousel.fireEvent(new PageLifecycleEvent(
                    PageLifecycleEvent.CLOSING, oldIndex, currentPage));
            currentPage.setVisible(false);
            carousel.fireEvent(new PageLifecycleEvent(
                    PageLifecycleEvent.CLOSED, oldIndex, currentPage));
        }
        carousel.fireEvent(new PageLifecycleEvent(
                PageLifecycleEvent.OPENING, newIndex, nextPage));
        nextPage.setVisible(true);
        carousel.fireEvent(new PageLifecycleEvent(
                PageLifecycleEvent.OPENED, newIndex, nextPage));

        CarouselAnimation anim = carousel.getAnimation();
        if (anim != null && anim.isMultiPageDisplay()) {
            TransitionContext ctx = buildContext(newIndex, newIndex, carousel);
            anim.setupInitialLayout(ctx);
        }

        onTransitionFinished(oldIndex, newIndex, carousel);
    }

    private void onTransitionFinished(int oldIndex, int currentIndex, RXCarousel carousel) {
        ensureAdjacentPages(currentIndex, carousel);
        evictDistantPages(currentIndex, carousel);
        updateArrowDisabledState(carousel);

        // Restart autoplay AFTER the animation completes, so
        // autoPlayInterval = actual page display time.
        // Do NOT restart if mouse is hovering and hoverPause is active,
        // because keyboard/programmatic navigation during hover should
        // not resume autoplay.
        if (carousel.isAutoPlay() && treeShowing.get()
                && !(mouseHovering && carousel.isHoverPause())) {
            startAutoplay(carousel);
        }
    }

    private void hideNonCurrentPages(RXCarousel carousel) {
        Node currentPage = pageCache.get(carousel.getSelectedIndex());

        // Remove all non-current pages from contentPane entirely.
        // They remain in pageCache and will be re-added when needed.
        // This ensures contentPane has only one child during idle,
        // preventing depth buffer artifacts under PerspectiveCamera.
        // No visual properties are reset here — that is the animation's
        // responsibility via finishAction and clearEffects.
        contentPane.getChildren().removeIf(child -> child != currentPage);
    }

    private TransitionContext buildContext(int oldIndex, int newIndex, RXCarousel carousel) {
        Node currentPage = pageCache.get(oldIndex);
        Node nextPage = getOrCreatePage(newIndex, carousel);
        Direction direction = computeDirection(oldIndex, newIndex, carousel);
        Duration duration = carousel.getAnimationDuration();

        return new TransitionContext(
                currentPage, nextPage,
                oldIndex, newIndex,
                carousel.getPageCount(),
                direction, duration,
                contentPane, null,
                index -> {
                    Node page = getOrCreatePage(index, carousel);
                    if (page != null && !contentPane.getChildren().contains(page)) {
                        page.setVisible(false);
                        contentPane.getChildren().add(page);
                    }
                    return page;
                },
                new TransitionContext.LifecycleCallback() {
                    @Override
                    public void fireOpening(int pageIndex) {
                        Node page = pageCache.get(pageIndex);
                        if (page != null) {
                            carousel.fireEvent(new PageLifecycleEvent(
                                    PageLifecycleEvent.OPENING, pageIndex, page));
                        }
                    }
                    @Override
                    public void fireOpened(int pageIndex) {
                        Node page = pageCache.get(pageIndex);
                        if (page != null) {
                            carousel.fireEvent(new PageLifecycleEvent(
                                    PageLifecycleEvent.OPENED, pageIndex, page));
                        }
                    }
                    @Override
                    public void fireClosing(int pageIndex) {
                        Node page = pageCache.get(pageIndex);
                        if (page != null) {
                            carousel.fireEvent(new PageLifecycleEvent(
                                    PageLifecycleEvent.CLOSING, pageIndex, page));
                        }
                    }
                    @Override
                    public void fireClosed(int pageIndex) {
                        Node page = pageCache.get(pageIndex);
                        if (page != null) {
                            carousel.fireEvent(new PageLifecycleEvent(
                                    PageLifecycleEvent.CLOSED, pageIndex, page));
                        }
                    }
                }
        );
    }

    private Direction computeDirection(int oldIndex, int newIndex, RXCarousel carousel) {
        // next() and previous() set a direction hint; use it directly
        Direction hint = carousel.getDirectionHint();
        if (hint != null) {
            return hint;
        }
        // goToPage(): always use index comparison (INDEX_BASED)
        return newIndex > oldIndex ? Direction.FORWARD : Direction.BACKWARD;
    }

    // ==================== Navigation Arrows ====================

    private void updateArrowVisibility(RXCarousel carousel) {
        DisplayMode mode = carousel.getArrowDisplayMode();
        switch (mode) {
            case SHOW:
                prevButton.setVisible(true);
                prevButton.setManaged(true);
                prevButton.setOpacity(1);
                nextButton.setVisible(true);
                nextButton.setManaged(true);
                nextButton.setOpacity(1);
                break;
            case HIDE:
                prevButton.setVisible(false);
                prevButton.setManaged(false);
                nextButton.setVisible(false);
                nextButton.setManaged(false);
                break;
            case AUTO:
                prevButton.setVisible(false);
                prevButton.setManaged(false);
                prevButton.setOpacity(0);
                nextButton.setVisible(false);
                nextButton.setManaged(false);
                nextButton.setOpacity(0);
                break;
        }
    }

    private void updateArrowDisabledState(RXCarousel carousel) {
        if (carousel.isCircular()) {
            prevButton.setDisable(false);
            nextButton.setDisable(false);
        } else {
            prevButton.setDisable(carousel.getSelectedIndex() == 0);
            nextButton.setDisable(carousel.getPageCount() > 0 &&
                    carousel.getSelectedIndex() >= carousel.getPageCount() - 1);
        }
    }

    // ==================== Navigator ====================

    private void updateNavigator(CarouselNavigator oldNav, CarouselNavigator newNav,
                                 RXCarousel carousel) {
        if (oldNav != null) {
            oldNav.dispose();
            if (navigatorNode != null) {
                navigatorNode.removeEventHandler(MouseEvent.MOUSE_CLICKED, navigatorFocusHandler);
                getChildren().remove(navigatorNode);
                navigatorNode = null;
            }
        }
        if (newNav != null) {
            navigatorNode = newNav.createNode(carousel);
            if (navigatorNode != null) {
                navigatorNode.addEventHandler(MouseEvent.MOUSE_CLICKED, navigatorFocusHandler);
                getChildren().add(navigatorNode);
                updateNavigatorVisibility(carousel);
                newNav.onPageChanged(-1, carousel.getSelectedIndex(), carousel.getPageCount());
            }
        }
    }

    private void updateNavigatorVisibility(RXCarousel carousel) {
        if (navigatorNode == null) {
            return;
        }
        DisplayMode mode = carousel.getNavigatorDisplayMode();
        switch (mode) {
            case SHOW:
                navigatorNode.setVisible(true);
                navigatorNode.setManaged(true);
                navigatorNode.setOpacity(1);
                break;
            case HIDE:
                navigatorNode.setVisible(false);
                navigatorNode.setManaged(false);
                break;
            case AUTO:
                navigatorNode.setVisible(false);
                navigatorNode.setManaged(false);
                navigatorNode.setOpacity(0);
                break;
        }
    }

    private void notifyNavigator(int oldIndex, RXCarousel carousel) {
        CarouselNavigator nav = carousel.getNavigator();
        if (nav != null) {
            nav.onPageChanged(oldIndex, carousel.getSelectedIndex(), carousel.getPageCount());
        }
    }

    // ==================== Mouse Hover ====================

    private void onMouseEntered(RXCarousel carousel) {
        mouseHovering = true;

        // Hover pause: freeze progress at current value
        if (carousel.isAutoPlay() && carousel.isHoverPause()) {
            pauseAutoplay();
        }

        // Fade in arrows (AUTO mode)
        if (carousel.getArrowDisplayMode() == DisplayMode.AUTO) {
            prevButtonFade = playFade(prevButton, 1, prevButtonFade);
            nextButtonFade = playFade(nextButton, 1, nextButtonFade);
        }

        // Fade in navigator (AUTO mode)
        if (carousel.getNavigatorDisplayMode() == DisplayMode.AUTO && navigatorNode != null) {
            navigatorFade = playFade(navigatorNode, 1, navigatorFade);
        }
    }

    private void onMouseExited(RXCarousel carousel) {
        mouseHovering = false;

        // Resume autoplay only if no animation is in progress.
        // If transitioning, autoplay will be restarted by onTransitionFinished.
        if (carousel.isAutoPlay() && carousel.isHoverPause()
                && treeShowing.get() && !transitioning) {
            startAutoplay(carousel);
        }

        // Fade out arrows (AUTO mode)
        if (carousel.getArrowDisplayMode() == DisplayMode.AUTO) {
            prevButtonFade = playFade(prevButton, 0, prevButtonFade);
            nextButtonFade = playFade(nextButton, 0, nextButtonFade);
        }

        // Fade out navigator (AUTO mode)
        if (carousel.getNavigatorDisplayMode() == DisplayMode.AUTO && navigatorNode != null) {
            navigatorFade = playFade(navigatorNode, 0, navigatorFade);
        }
    }

    private FadeTransition playFade(Node node, double targetOpacity, FadeTransition previous) {
        if (previous != null) {
            previous.stop();
        }
        if (targetOpacity > 0) {
            node.setManaged(true);
            node.setVisible(true);
        }
        FadeTransition ft = new FadeTransition(FADE_DURATION, node);
        ft.setToValue(targetOpacity);
        if (targetOpacity == 0) {
            ft.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
            });
        }
        ft.play();
        return ft;
    }

    // ==================== Tree Showing Detection ====================

    private void installTreeShowingListener(RXCarousel carousel) {
        disposer.registerListener(treeShowing,
                (obs, oldVal, newVal) -> onTreeShowingChanged(carousel, newVal));
    }

    private void onTreeShowingChanged(RXCarousel carousel, boolean showing) {
        if (showing) {
            if (carousel.isAutoPlay()) {
                startAutoplay(carousel);
            }
        } else {
            pauseAutoplay();
        }
    }

    // ==================== Auto Play ====================

    private void initAutoPlayTimer(RXCarousel carousel) {
        if (carousel.isAutoPlay() && treeShowing.get()) {
            startAutoplay(carousel);
        }
    }

    /**
     * Starts (or resumes) the auto-play countdown. Creates a new Timeline
     * that animates autoPlayProgress from its current value to 1.0 over
     * the remaining duration. When it reaches 1.0, the next page is shown.
     */
    private void startAutoplay(RXCarousel carousel) {
        // Non-circular, already at last page: don't start
        if (!carousel.isCircular()
                && carousel.getSelectedIndex() >= carousel.getPageCount() - 1) {
            return;
        }

        Duration interval = carousel.getAutoPlayInterval();
        if (interval == null || interval.lessThan(MIN_AUTOPLAY_INTERVAL)) {
            interval = MIN_AUTOPLAY_INTERVAL;
        }

        double currentProgress = autoPlayProgressInternal.get();
        Duration remaining = interval.multiply(1.0 - currentProgress);

        if (remaining.lessThanOrEqualTo(Duration.ZERO)) {
            remaining = Duration.millis(1);
        }

        if (autoPlayTimeline != null) {
            autoPlayTimeline.stop();
        }

        autoPlayTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(autoPlayProgressInternal, currentProgress)),
                new KeyFrame(remaining,
                        new KeyValue(autoPlayProgressInternal, 1.0))
        );
        autoPlayTimeline.setOnFinished(e -> {
            autoPlayProgressInternal.set(0);
            if (carousel.isAutoPlay() && treeShowing.get()) {
                carousel.next();
            }
        });
        autoPlayTimeline.play();
    }

    /**
     * Restarts auto-play from the beginning (progress = 0).
     */
    private void restartAutoplay(RXCarousel carousel) {
        stopAutoplay(carousel);
        startAutoplay(carousel);
    }

    /**
     * Stops auto-play and resets progress to 0.
     */
    private void stopAutoplay(RXCarousel carousel) {
        if (autoPlayTimeline != null) {
            autoPlayTimeline.stop();
            autoPlayTimeline = null;
        }
        autoPlayProgressInternal.set(0);
    }

    /**
     * Pauses auto-play, keeping the current progress value.
     */
    private void pauseAutoplay() {
        if (autoPlayTimeline != null) {
            autoPlayTimeline.pause();
        }
    }

    // ==================== Placeholder ====================

    private static final String EMPTY_CAROUSEL_TEXT = "No Pages";

    private StackPane placeholderRegion;

    private boolean shouldShowPlaceholder(RXCarousel carousel) {
        return carousel.getPageCount() <= 0 || carousel.getPageFactory() == null;
    }

    private void showPlaceholder(RXCarousel carousel) {
        Node placeholderNode = carousel.getPlaceholder();
        if (placeholderNode == null) {
            placeholderNode = new Label(EMPTY_CAROUSEL_TEXT);
        }

        if (placeholderRegion == null) {
            placeholderRegion = new StackPane();
            placeholderRegion.getStyleClass().setAll("placeholder");
            getChildren().add(placeholderRegion);
        }
        placeholderRegion.getChildren().setAll(placeholderNode);
        placeholderRegion.setVisible(true);
        placeholderRegion.setManaged(true);

        contentPane.setVisible(false);
    }

    private void hidePlaceholder(RXCarousel carousel) {
        if (placeholderRegion != null) {
            placeholderRegion.setVisible(false);
            placeholderRegion.setManaged(false);
            placeholderRegion.getChildren().clear();
        }
        contentPane.setVisible(true);
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        RXCarousel carousel = getSkinnable();
        stopAutoplay(carousel);

        if (navigatorNode != null) {
            navigatorNode.removeEventHandler(MouseEvent.MOUSE_CLICKED, navigatorFocusHandler);
        }

        CarouselNavigator nav = carousel.getNavigator();
        if (nav != null) {
            nav.dispose();
        }
        CarouselAnimation anim = carousel.getAnimation();
        if (anim != null) {
            anim.dispose();
        }
        if (usedAnimation != null && usedAnimation != anim) {
            usedAnimation.dispose();
            usedAnimation = null;
        }
        pageCache.clear();
    }
}
