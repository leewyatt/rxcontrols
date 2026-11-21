package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.DisplayMode;
import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.animation.page.AnimNone;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.internal.CarouselSignals;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default skin for the {@link RXCarousel} control.
 *
 * <p>Each cached page node sits in a page wrapper of the skin (style class
 * {@code page}). Animations, visibility and the multi-page layout act on the
 * wrappers only, so a page node keeps its own properties.</p>
 *
 * <p>The skin fires the page lifecycle events itself, at the same points of
 * every page change whatever the animation. Every change is processed to
 * completion before the next one starts: a change made by an event handler or
 * a listener is picked up once the current step has finished.</p>
 *
 * <p>The skin takes the carousel over when it is installed, not when it is
 * constructed. A replacement skin continues the cached pages without events;
 * removing the skin closes the open page and evicts the cache.</p>
 */
public class RXCarouselSkin extends RXSkinBase<RXCarousel> {

    private static final Duration MIN_AUTOPLAY_INTERVAL = Duration.millis(100);
    private static final Duration FADE_DURATION = Duration.millis(200);
    private static final AnimNone FALLBACK_NONE = new AnimNone();
    private static final String EMPTY_CAROUSEL_TEXT = "No Pages";
    // Kept in the carousel's properties from the dispose of a replaced skin
    // until its replacement takes the carousel over.
    private static final Object HANDOVER_KEY = new Object();

    private final StackPane contentPane;
    private final Rectangle contentClip;

    // Navigation arrows
    private final StackPane prevButton;
    private final StackPane nextButton;
    private final Region prevArrow;
    private final Region nextArrow;

    // Navigator node (managed by CarouselNavigator)
    private Node navigatorNode;

    private StackPane placeholderRegion;

    // ==================== Page state ====================

    private final Map<Integer, Page> pageCache = new HashMap<>();
    // The page shown, or animating in while a transition plays.
    private Page shown;
    // The index the pages have settled on: the shown page's, or one the
    // factory gave no node for; -1 while no page is due.
    private int shownIndex = -1;
    // Pages to evict once no transition plays them.
    private final Set<Page> doomed = new LinkedHashSet<>();

    // ==================== Transition state ====================

    private final PageTransitionEngine engine = new PageTransitionEngine();
    // The page animating out while a transition plays.
    private Page leaving;
    // The animation of the transition in progress.
    private PageAnimation playing;
    // A transition is in progress: playing, or ended and not completed yet.
    private boolean transitioning;
    // The transition has ended and is still to be completed.
    private boolean settlePending;
    // Tags the transition in progress, so callbacks of an ended one are ignored.
    private int transitionId;
    // A step of the loop in sync() is in progress.
    private boolean busy;
    // The housekeeping of the settled pages is due once the loop converges.
    private boolean idlePending;
    // The next page change cuts: taking the carousel over never animates.
    private boolean cutNext;
    // How the latest change of the selected index was requested.
    private boolean requestAnimated = true;
    private TransitionDirection requestDirection;
    // The multi-page animation whose layout the idle pages show.
    private PageAnimation layoutAnimation;
    // Pages created while an animation runs its own code; their CACHED waits
    // until that code has returned, so no handler runs inside it.
    private boolean deferCached;
    private final List<Page> pendingCached = new ArrayList<>();
    // The carousel holds the focus taken from a page it left before the
    // shown page became visible.
    private boolean focusHandOff;

    // ==================== Auto play state ====================

    // Auto play (Timeline drives autoPlayProgress from 0→1)
    private Timeline autoPlayTimeline;
    private final DoubleProperty autoPlayProgressInternal = new SimpleDoubleProperty(0);

    // Fade animations for arrows and navigator
    private FadeTransition prevButtonFade;
    private FadeTransition nextButtonFade;
    private FadeTransition navigatorFade;

    // Mouse hover state (for hoverPause interaction with keyboard navigation)
    private boolean mouseHovering;

    // Tree-showing state, created once the skin is installed
    private ReadOnlyBooleanProperty treeShowing;

    // Handlers for dynamic nodes
    private final EventHandler<MouseEvent> navigatorFocusHandler = e -> getSkinnable().requestFocus();

    // ==================== Skin lifecycle state ====================

    private boolean activated;
    private boolean disposed;
    private final InvalidationListener skinListener = observable -> activate();
    // Cache clears requested between installation and take-over.
    private boolean clearAllOnTakeOver;
    private final Set<Integer> clearOnTakeOver = new HashSet<>();

    /**
     * Creates the default skin for the given carousel. The skin starts showing
     * the pages once it is installed as the carousel's skin.
     *
     * @param carousel the carousel control
     */
    public RXCarouselSkin(RXCarousel carousel) {
        super(carousel);

        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");

        contentClip = new Rectangle();
        contentPane.setClip(contentClip);

        // Navigation arrows
        prevArrow = new Region();
        prevArrow.getStyleClass().add("arrow");
        prevButton = new StackPane(prevArrow);
        prevButton.getStyleClass().add("prev-button");

        nextArrow = new Region();
        nextArrow.getStyleClass().add("arrow");
        nextButton = new StackPane(nextArrow);
        nextButton.getStyleClass().add("next-button");

        // Weak: setSkin drops a skin of the installed class without disposing
        // it, and nothing else may keep such a skin reachable from the carousel.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        carousel.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> carousel.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a
    // second skin of the installed class. Taking the carousel over only once
    // installed keeps a skin that never gets installed from adding nodes,
    // moving the pages, firing events or running the auto-play, and runs after
    // the replaced skin has been disposed: the shared navigator is disposed by
    // the old skin before this one creates its node.
    private void activate() {
        RXCarousel carousel = getSkinnable();
        if (activated || disposed || carousel == null || carousel.getSkin() != this) {
            return;
        }
        activated = true;
        // Taken before the properties listener is registered: removing a key
        // must not start a sync before the pages are taken over.
        Handover handover = (Handover) carousel.getProperties().remove(HANDOVER_KEY);
        boolean pagesChanged = carousel.getProperties().remove(CarouselSignals.PAGES_CHANGED) != null;
        treeShowing = controlTreeShowingProperty();

        // Like a button, the arrows answer the primary button only.
        disposer.registerEventHandler(prevButton, MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                carousel.previous();
                carousel.requestFocus();
            }
        });
        disposer.registerEventHandler(nextButton, MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                carousel.next();
                carousel.requestFocus();
            }
        });
        getChildren().addAll(contentPane, prevButton, nextButton);

        // Sync internal progress property to the public read-only property on RXCarousel
        disposer.registerListener(autoPlayProgressInternal,
                () -> carousel.setAutoPlayProgress(autoPlayProgressInternal.get()));

        setupListeners(carousel);
        updateNavigator(null, carousel.getNavigator(), carousel);
        updateNavigatorVisibility(carousel);
        updateArrowVisibility(carousel);
        updateArrowDisabledState(carousel);
        takeOver(carousel, handover, pagesChanged);
    }

    // Continues the pages a replaced skin handed over: the page it left open
    // stays open without events while it is still the selected page;
    // otherwise the first step closes it and cuts to the selected page.
    // Pages of another factory than the carousel's are stale, so the loop
    // evicts them first. Without a hand-over the selected page is opened from
    // scratch.
    private void takeOver(RXCarousel carousel, Handover handover, boolean pagesChanged) {
        if (handover != null) {
            for (HandedPage handed : handover.pages) {
                Page page = new Page(handed.index(), handed.node(), handed.factory());
                pageCache.put(page.index, page);
                if (handed.stale()) {
                    doomed.add(page);
                }
                if (page.index == handover.openIndex) {
                    page.phase = Phase.OPENED;
                    showOnly(page);
                    shown = page;
                    shownIndex = page.index;
                }
            }
            if (pagesChanged) {
                checkPages(carousel);
            }
        }
        if (clearAllOnTakeOver) {
            doomed.addAll(pageCache.values());
        }
        for (int index : clearOnTakeOver) {
            Page page = pageCache.get(index);
            if (page != null) {
                doomed.add(page);
            }
        }
        clearAllOnTakeOver = false;
        clearOnTakeOver.clear();
        cutNext = true;
        idlePending = true;
        sync();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        if (!activated || disposed) {
            return;
        }
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

        // The multi-page layout follows the pane's size while idle. Layout
        // only arranges cached pages: it creates none and fires no events.
        if (!transitioning && layoutAnimation != null && shown != null) {
            layoutAnimation.setupInitialLayout(
                    context(shown, shown.index, shown, shown.index, TransitionDirection.FORWARD, false));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return 600 + leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return 400 + topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Listeners ====================

    private void setupListeners(RXCarousel carousel) {
        disposer.registerListener(carousel.selectedIndexProperty(), (obs, oldVal, newVal) -> {
            if (disposed) {
                return;
            }
            // The carousel sets these for the call that changes the index
            // only; the change itself may be processed after the call.
            requestAnimated = carousel.isAnimateTransition();
            requestDirection = carousel.getDirectionHint();
            notifyNavigator(oldVal.intValue(), carousel);
            updateArrowDisabledState(carousel);
            sync();
        });

        // Pages beyond the new count are evicted by the loop; the carousel has
        // already moved the selected index within the pages.
        disposer.registerListener(carousel.pageCountProperty(), () -> {
            if (disposed) {
                return;
            }
            forgetMissingPage();
            idlePending = true;
            updateArrowDisabledState(carousel);
            notifyNavigator(-1, carousel);
            sync();
        });

        disposer.registerListener(carousel.getProperties(), () -> {
            if (disposed) {
                return;
            }
            if (carousel.getProperties().remove(CarouselSignals.PAGES_CHANGED) != null) {
                checkPages(carousel);
            }
            sync();
        });

        disposer.registerListener(carousel.pageFactoryProperty(), () -> {
            if (disposed) {
                return;
            }
            // Every cached page came from the previous factory.
            doomed.addAll(pageCache.values());
            forgetMissingPage();
            idlePending = true;
            sync();
        });

        // Circular mode change
        disposer.registerListener(carousel.circularProperty(), (obs, oldVal, newVal) -> {
            if (disposed) {
                return;
            }
            updateArrowDisabledState(carousel);
            // Back to circular resumes an auto-play the last page had
            // stopped; away from it on the last page stops one running.
            refreshAutoplay(carousel);
            // The neighbours to prefetch change with the wrap-around.
            idlePending = true;
            sync();
        });

        disposer.registerListener(carousel.hoverPauseProperty(), () -> {
            if (disposed) {
                return;
            }
            if (mouseHovering && carousel.isHoverPause()) {
                pauseAutoplay();
            } else if (canRunAutoplay(carousel)) {
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

        // Placeholder: shown by the housekeeping while the carousel has no pages
        disposer.registerListener(carousel.placeholderProperty(), () -> {
            idlePending = true;
            sync();
        });

        // Auto play properties
        disposer.registerListener(carousel.autoPlayProperty(), (obs, oldVal, newVal) -> {
            if (!newVal) {
                stopAutoplay(carousel);
            } else if (canRunAutoplay(carousel)) {
                startAutoplay(carousel);
            }
        });

        // A new interval counts from zero, from when the auto-play may run.
        disposer.registerListener(carousel.autoPlayIntervalProperty(), (obs, oldVal, newVal) -> {
            if (carousel.isAutoPlay()) {
                stopAutoplay(carousel);
                if (canRunAutoplay(carousel)) {
                    startAutoplay(carousel);
                }
            }
        });

        // A new animation is used from the next page change on; while idle,
        // the housekeeping replaces the multi-page layout of the old one.
        disposer.registerListener(carousel.animationProperty(), () -> {
            idlePending = true;
            sync();
        });

        // Mouse enter/exit for hover effects
        disposer.registerEventHandler(carousel, MouseEvent.MOUSE_ENTERED,
                e -> onMouseEntered(carousel));
        disposer.registerEventHandler(carousel, MouseEvent.MOUSE_EXITED,
                e -> onMouseExited(carousel));

        // Keyboard navigation. Page only when the carousel itself owns focus: an
        // arrow key bubbling up from a focused node inside a page targets that
        // node, and consuming it here would flip the page and block the scene's
        // arrow-key focus traversal for controls that leave the key unhandled.
        // Right to left, the arrow keys swap like the mirrored arrows.
        disposer.registerEventHandler(carousel, KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() != carousel) {
                return;
            }
            boolean rtl = carousel.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.KP_LEFT) {
                if (rtl) {
                    carousel.next();
                } else {
                    carousel.previous();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.KP_RIGHT) {
                if (rtl) {
                    carousel.previous();
                } else {
                    carousel.next();
                }
                e.consume();
            }
        });

        installTreeShowingListener(carousel);
    }

    // ==================== Page Changes ====================

    // Converges the pages and their lifecycle onto the carousel's state.
    // Called for page and structure changes, natural transition ends,
    // deferred external stops and the take-over. While a step is in progress
    // a further call returns at once: the loop re-reads the state after the
    // step.
    private void sync() {
        if (!activated || disposed || busy
                || getSkinnable().getProperties().containsKey(CarouselSignals.PAGES_UPDATING)) {
            return;
        }
        busy = true;
        try {
            while (!disposed && step()) {
                // Each step fires events or notifies listeners, which may change the carousel again.
            }
        } finally {
            busy = false;
        }
        // The cut is for the take-over only, also when its page was already shown.
        cutNext = false;
    }

    // Returns false once nothing is left to do.
    private boolean step() {
        if (settlePending) {
            settle();
            return true;
        }
        int target = targetIndex();
        if (transitioning) {
            if (target == shown.index && !isStale(shown) && !isStale(leaving)) {
                return false;
            }
            // Latest wins: the stop runs the animation's finish action before
            // returning, so the transition can be completed right away. An
            // animation that ignores jumpToEnd is treated as ended as well.
            engine.interrupt();
            settlePending = true;
            return true;
        }
        Page stale = nextStalePage();
        if (stale != null) {
            evict(stale);
            return true;
        }
        if (target != shownIndex) {
            changePage();
            return true;
        }
        if (idlePending) {
            idle();
            return true;
        }
        return false;
    }

    // Closes the shown page and opens the target page: animated when
    // possible, otherwise with a direct cut.
    private void changePage() {
        RXCarousel carousel = getSkinnable();
        boolean cut = cutNext;
        cutNext = false;
        stopAutoplay(carousel);
        Page from = shown;
        prepareFor(animationOrNone(carousel), from);
        if (from != null && from.phase == Phase.OPENED
                && !announce(RXPageLifecycleEvent.CLOSING, from)) {
            return;
        }
        proceedFrom(from, cut);
    }

    // `from` has received CLOSING (or is null) and still shows. Opens the
    // latest target: animated from `from` when possible, otherwise by
    // closing `from` first and cutting to the target.
    private void proceedFrom(Page from, boolean cut) {
        RXCarousel carousel = getSkinnable();
        while (true) {
            int target = targetIndex();
            Page to = target < 0 ? null : getOrCreatePage(target, carousel);
            if (disposed) {
                return;
            }
            if (targetIndex() != target) {
                // A CACHED handler moved on; the page stays cached, unannounced.
                continue;
            }
            if (isStale(from) || isStale(to)) {
                // A handler replaced pages: the shown page closes without
                // animation, and the loop evicts the stale pages before it
                // opens the target, so a replaced page never opens.
                if (from != null) {
                    closeShown(from);
                }
                return;
            }
            PageAnimation animation = animationOrNone(carousel);
            Duration duration = carousel.getAnimationDuration();
            boolean animate = !cut && requestAnimated && from != null && to != null && to != from
                    && PageTransitionEngine.canAnimate(animation, true, carousel.getPageCount(),
                            duration, true);
            if (!animate) {
                if (from != null && !closeShown(from)) {
                    return;
                }
                openDirect();
                return;
            }
            attachHidden(to);
            if (!announce(RXPageLifecycleEvent.OPENING, to)) {
                return;
            }
            if (targetIndex() != to.index || isStale(to)) {
                // Replaced before the animation started: never show it, and
                // try again from the same outgoing page.
                if (!abandon(to)) {
                    return;
                }
                continue;
            }
            play(from, to, animation);
            return;
        }
    }

    // Nothing is shown. Cuts to the latest target.
    private void openDirect() {
        RXCarousel carousel = getSkinnable();
        while (true) {
            int target = targetIndex();
            if (target < 0) {
                shownIndex = -1;
                idlePending = true;
                return;
            }
            Page page = getOrCreatePage(target, carousel);
            if (disposed) {
                return;
            }
            if (targetIndex() != target) {
                continue;
            }
            if (isStale(page)) {
                // A handler replaced the page: the loop evicts it first.
                shownIndex = -1;
                return;
            }
            if (page == null) {
                // The factory gave no node: nothing shows until the pages change.
                shownIndex = target;
                idlePending = true;
                return;
            }
            attachHidden(page);
            shown = page;
            shownIndex = target;
            if (!announce(RXPageLifecycleEvent.OPENING, page)) {
                return;
            }
            if (targetIndex() != target || isStale(page)) {
                shown = null;
                shownIndex = -1;
                if (!abandon(page)) {
                    return;
                }
                continue;
            }
            showOnly(page);
            if (focusHandOff) {
                moveFocusOutOf(null);
            }
            if (!announce(RXPageLifecycleEvent.OPENED, page)) {
                return;
            }
            idlePending = true;
            return;
        }
    }

    // Ends the shown page without animation: hidden, CLOSED (when it
    // received CLOSING), then detached.
    private boolean closeShown(Page page) {
        page.wrapper.setVisible(false);
        shown = null;
        shownIndex = -1;
        moveFocusOutOf(page);
        if (page.phase == Phase.CLOSING && !announce(RXPageLifecycleEvent.CLOSED, page)) {
            return false;
        }
        detach(page);
        return true;
    }

    // Completes the pairs of a page that received OPENING but was replaced
    // before it showed, then detaches it.
    private boolean abandon(Page page) {
        if (!announce(RXPageLifecycleEvent.OPENED, page)
                || !announce(RXPageLifecycleEvent.CLOSING, page)
                || !announce(RXPageLifecycleEvent.CLOSED, page)) {
            return false;
        }
        detach(page);
        return true;
    }

    // A different animation is about to be used: clear what the previous one
    // left on the pages, and the side pages a multi-page layout kept.
    private void prepareFor(PageAnimation animation, Page from) {
        int index = from == null ? -1 : from.index;
        PageAnimation previous = engine.clearEffectsIfChanged(animation,
                () -> context(from, index, from, index, TransitionDirection.FORWARD, false));
        boolean leavingLayout = layoutAnimation != null && layoutAnimation != animation;
        if (leavingLayout && layoutAnimation != previous) {
            layoutAnimation.clearEffects(context(from, index, from, index, TransitionDirection.FORWARD, false));
        }
        if (leavingLayout || (previous != null && previous.isMultiPageDisplay())) {
            layoutAnimation = null;
            detachAllBut(from);
        }
    }

    // ==================== Transitions ====================

    private void play(Page from, Page to, PageAnimation animation) {
        TransitionDirection direction = requestDirection != null ? requestDirection
                : (to.index > from.index ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD);
        leaving = from;
        shown = to;
        shownIndex = to.index;
        transitioning = true;
        playing = animation;
        int id = ++transitionId;
        TransitionContext context = context(from, from.index, to, to.index, direction, true);
        deferCached = true;
        try {
            engine.play(animation, context,
                    () -> onTransitionStarted(id),
                    () -> onTransitionFinished(id),
                    () -> onTransitionStopped(id));
        } catch (RuntimeException e) {
            // An animation that fails to build is treated as ended.
            report(e);
            settlePending = true;
        } finally {
            deferCached = false;
        }
        announcePendingCached();
    }

    // Runs once the animation plays, so the incoming page has usually been
    // made visible by then.
    private void onTransitionStarted(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        getSkinnable().setPageTransitioning(true);
        if (!disposed) {
            moveFocusOutOf(leaving);
        }
    }

    // Natural end: in a pulse, or inside play() for a zero-length animation,
    // where the loop completes it after the current step.
    private void onTransitionFinished(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        sync();
    }

    // External stop: runs inside Animation.stop(), before the animation's
    // finish action restores the pages, so the completion must wait. A stop
    // made during a step is completed by the loop right after the step.
    private void onTransitionStopped(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        if (!busy) {
            Platform.runLater(this::sync);
        }
    }

    // Completes an ended transition: the outgoing page hides,
    // pageTransitioning turns false, CLOSED goes to the outgoing page and
    // OPENED to the incoming one, then only the incoming page stays attached.
    // A multi-page animation keeps the layout its finish action left.
    private void settle() {
        settlePending = false;
        transitionId++;
        transitioning = false;
        Page from = leaving;
        Page to = shown;
        PageAnimation used = playing;
        leaving = null;
        playing = null;
        boolean multiPage = used != null && used.isMultiPageDisplay();
        if (!multiPage) {
            if (from != null) {
                from.wrapper.setVisible(false);
            }
            to.wrapper.setVisible(true);
        }
        layoutAnimation = multiPage ? used : null;
        idlePending = true;
        getSkinnable().setPageTransitioning(false);
        if (disposed) {
            return;
        }
        if (from != null && from.phase == Phase.CLOSING
                && !announce(RXPageLifecycleEvent.CLOSED, from)) {
            return;
        }
        if (to.phase == Phase.OPENING && !announce(RXPageLifecycleEvent.OPENED, to)) {
            return;
        }
        if (!multiPage) {
            detachAllBut(to);
        }
        if (focusHandOff) {
            moveFocusOutOf(null);
        }
    }

    // ==================== Focus ====================

    // Focus left on a hidden or detached page would be replaced by the
    // scene's first focusable node at the next pulse. Focus inside the page
    // being left moves to the first focusable node of the shown page, or to
    // the carousel while that page is not visible yet (some animations reveal
    // it late, a zero-length one only when it settles); the carousel passes
    // it on once the page shows. Focus elsewhere is left alone.
    private void moveFocusOutOf(Page leaving) {
        RXCarousel carousel = getSkinnable();
        Scene scene = carousel.getScene();
        Node owner = scene == null ? null : scene.getFocusOwner();
        boolean inLeavingPage = owner != null && leaving != null && isInside(owner, leaving.wrapper);
        boolean heldForHandOff = focusHandOff && owner == carousel;
        focusHandOff = false;
        if (!inLeavingPage && !heldForHandOff) {
            return;
        }
        if (shown == null || !shown.wrapper.isVisible() || shown.wrapper.getParent() != contentPane) {
            focusHandOff = true;
            carousel.requestFocus();
            return;
        }
        Node target = firstFocusableIn(shown.node);
        (target == null ? carousel : target).requestFocus();
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static Node firstFocusableIn(Node root) {
        if (root == null || !root.isVisible() || root.isDisabled()) {
            return null;
        }
        if (root.isFocusTraversable()) {
            return root;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = firstFocusableIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ==================== Housekeeping ====================

    // Runs once the pages have settled on the target: the placeholder, the
    // multi-page layout, the neighbours to prefetch, the pages to evict by
    // distance, and the auto-play.
    private void idle() {
        idlePending = false;
        RXCarousel carousel = getSkinnable();
        updatePlaceholder(carousel);
        if (shown != null) {
            applyLayout(carousel);
            if (disposed) {
                return;
            }
            ensureAdjacentPages(shown.index, carousel);
            if (disposed) {
                return;
            }
            if (targetIndex() != shownIndex || !doomed.isEmpty()) {
                // A handler changed the carousel: housekeep once it has settled again.
                idlePending = true;
                return;
            }
            evictDistantPages(shown.index, carousel);
            if (disposed) {
                return;
            }
        }
        // The countdown restarts once the page has settled, so the interval
        // is the time a page shows.
        refreshAutoplay(carousel);
    }

    // Lays the idle pages out for the configured animation: a multi-page
    // animation arranges the side pages, any other shows the page alone.
    private void applyLayout(RXCarousel carousel) {
        PageAnimation animation = carousel.getAnimation();
        if (layoutAnimation != null && layoutAnimation != animation) {
            layoutAnimation.clearEffects(
                    context(shown, shown.index, shown, shown.index, TransitionDirection.FORWARD, false));
            layoutAnimation = null;
            showOnly(shown);
        }
        if (animation != null && animation.isMultiPageDisplay()) {
            layoutAnimation = animation;
            deferCached = true;
            try {
                animation.setupInitialLayout(
                        context(shown, shown.index, shown, shown.index, TransitionDirection.FORWARD, true));
            } finally {
                deferCached = false;
            }
            announcePendingCached();
        }
    }

    // ==================== Page Cache ====================

    // Not computeIfAbsent: the factory and the CACHED handlers are the
    // application's code and may change the cache themselves.
    private Page getOrCreatePage(int index, RXCarousel carousel) {
        while (true) {
            Page page = pageCache.get(index);
            if (page != null || disposed) {
                return page;
            }
            Callback<Integer, Node> factory = carousel.getPageFactory();
            if (factory == null || index < 0 || index >= carousel.getPageCount()) {
                return null;
            }
            Node node = callFactory(factory, index);
            if (disposed) {
                return null;
            }
            if (carousel.getPageFactory() != factory) {
                // Replaced while it ran: the node belongs to no page; ask the new factory.
                continue;
            }
            if (node == null || index >= carousel.getPageCount()) {
                return null;
            }
            page = new Page(index, node, factory);
            pageCache.put(index, page);
            if (deferCached) {
                pendingCached.add(page);
            } else {
                announce(RXPageLifecycleEvent.CACHED, page);
            }
            return page;
        }
    }

    private void announcePendingCached() {
        while (!pendingCached.isEmpty() && !disposed) {
            Page page = pendingCached.remove(0);
            if (pageCache.get(page.index) == page) {
                announce(RXPageLifecycleEvent.CACHED, page);
            }
        }
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

    // Pages a multi-page layout shows stay cached whatever the distance.
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

        for (Page page : sortedPages()) {
            boolean onDisplay = page.wrapper.isVisible() && page.wrapper.getParent() == contentPane;
            if (!keep.contains(page.index) && page != shown && !onDisplay
                    && pageCache.get(page.index) == page) {
                evict(page);
                if (disposed) {
                    return;
                }
            }
        }
    }

    // The next page to evict: one beyond the page count or one asked for.
    // The shown page goes first, so the page on screen closes before the
    // others are evicted.
    private Page nextStalePage() {
        doomed.removeIf(page -> pageCache.get(page.index) != page);
        Page next = null;
        for (Page page : pageCache.values()) {
            if (isStale(page)) {
                if (page == shown) {
                    return page;
                }
                if (next == null || page.index < next.index) {
                    next = page;
                }
            }
        }
        return next;
    }

    // A page is stale when asked for, past the page count, or made by another
    // factory than the carousel's.
    private boolean isStale(Page page) {
        if (page == null) {
            return false;
        }
        RXCarousel carousel = getSkinnable();
        return doomed.contains(page) || page.index >= carousel.getPageCount()
                || page.factory != carousel.getPageFactory();
    }

    // Evicts a page after completing what it started: the open page closes
    // first (CLOSING, CLOSED), then EVICTED. The node leaves the skin's
    // wrapper before EVICTED, so a handler can put it somewhere else. The
    // page stays marked until it has left the cache, so a skin replaced from
    // a CLOSING handler still hands the eviction over.
    private void evict(Page page) {
        if (page != shown && page.wrapper.isVisible() && page.wrapper.getParent() == contentPane) {
            // A side page of a multi-page layout: lay the pages out again.
            idlePending = true;
        }
        if (page == shown) {
            if (page.phase == Phase.OPENED && !announce(RXPageLifecycleEvent.CLOSING, page)) {
                return;
            }
            if (!closeShown(page)) {
                return;
            }
        }
        if (pageCache.get(page.index) == page) {
            pageCache.remove(page.index);
        }
        doomed.remove(page);
        detach(page);
        page.wrapper.getChildren().clear();
        announce(RXPageLifecycleEvent.EVICTED, page);
    }

    // The nodes behind the indices may have changed: a cached page whose
    // index is past the pages, or that another factory made, is evicted
    // anyway; one whose node the factory no longer gives for its index is
    // evicted too. The index is checked first, as a shorter list cannot be
    // asked for it.
    private void checkPages(RXCarousel carousel) {
        Callback<Integer, Node> factory = carousel.getPageFactory();
        int count = carousel.getPageCount();
        for (Page page : sortedPages()) {
            if (page.index < count && page.factory == factory && callFactory(factory, page.index) != page.node) {
                doomed.add(page);
            }
        }
        forgetMissingPage();
        idlePending = true;
    }

    // The shown page was given up on: a later change tries the factory again.
    private void forgetMissingPage() {
        if (shown == null) {
            shownIndex = -1;
        }
    }

    /**
     * Evicts every cached page; the current page is closed first and then
     * opened again from the page factory. Called from
     * {@link RXCarousel#clearPageCache()}.
     */
    public void clearPageCache() {
        if (disposed) {
            return;
        }
        if (!activated) {
            // Installed while the replaced skin fires its last events: the
            // take-over clears what it is handed.
            if (isInstalled()) {
                clearAllOnTakeOver = true;
            }
            return;
        }
        doomed.addAll(pageCache.values());
        forgetMissingPage();
        sync();
    }

    /**
     * Evicts the cached page at the given index; the current page is closed
     * first and then opened again from the page factory. Called from
     * {@link RXCarousel#clearPageCache(int)}.
     *
     * @param index the page index to clear
     */
    public void clearPageCache(int index) {
        if (disposed) {
            return;
        }
        if (!activated) {
            if (isInstalled()) {
                clearOnTakeOver.add(index);
            }
            return;
        }
        Page page = pageCache.get(index);
        if (page != null) {
            doomed.add(page);
            forgetMissingPage();
            sync();
        }
    }

    private boolean isInstalled() {
        RXCarousel carousel = getSkinnable();
        return carousel != null && carousel.getSkin() == this;
    }

    // ==================== Pages in the Content Pane ====================

    private void attachHidden(Page page) {
        page.wrapper.setVisible(false);
        if (page.wrapper.getParent() != contentPane) {
            contentPane.getChildren().add(page.wrapper);
        }
    }

    private void detach(Page page) {
        contentPane.getChildren().remove(page.wrapper);
    }

    // Shows the page alone: it leaves only one child to the content pane
    // while idle, which keeps hidden pages out of a PerspectiveCamera's depth
    // buffer.
    private void showOnly(Page page) {
        if (page.wrapper.getParent() != contentPane) {
            contentPane.getChildren().add(page.wrapper);
        }
        page.wrapper.setVisible(true);
        detachAllBut(page);
    }

    private void detachAllBut(Page page) {
        Node keep = page == null ? null : page.wrapper;
        contentPane.getChildren().removeIf(child -> child != keep);
    }

    // ==================== Contexts ====================

    // The page the carousel should show, or -1 while it has none.
    private int targetIndex() {
        RXCarousel carousel = getSkinnable();
        int count = carousel.getPageCount();
        if (count <= 0 || carousel.getPageFactory() == null) {
            return -1;
        }
        return Math.max(0, Math.min(carousel.getSelectedIndex(), count - 1));
    }

    private static PageAnimation animationOrNone(RXCarousel carousel) {
        PageAnimation animation = carousel.getAnimation();
        return animation != null ? animation : FALLBACK_NONE;
    }

    // Animations get the wrappers, never the application's page nodes. Pages
    // they fetch are created only when createPages is set.
    private TransitionContext context(Page from, int fromIndex, Page to, int toIndex,
                                      TransitionDirection direction, boolean createPages) {
        RXCarousel carousel = getSkinnable();
        return new TransitionContext(
                from == null ? null : from.wrapper, to == null ? null : to.wrapper,
                fromIndex, toIndex,
                carousel.getPageCount(),
                direction, carousel.getAnimationDuration(),
                contentPane,
                index -> {
                    Page page = createPages ? getOrCreatePage(index, carousel) : pageCache.get(index);
                    if (page == null) {
                        return null;
                    }
                    if (page.wrapper.getParent() != contentPane) {
                        attachHidden(page);
                    }
                    return page.wrapper;
                });
    }

    // ==================== Lifecycle Events ====================

    // Fires one of the page's lifecycle events; returns false once this skin
    // has been disposed (a handler may replace or remove the skin), so the
    // caller stops touching the pages.
    private boolean announce(EventType<RXPageLifecycleEvent> type, Page page) {
        if (disposed) {
            return false;
        }
        fire(type, page, getSkinnable());
        return !disposed;
    }

    // Records the event on the page before handlers can see it, so a handler
    // that removes the skin finds the pairs it has to complete. A throwing
    // handler ends this event's dispatch, as for any JavaFX event; the
    // sequence itself must still complete, so the exception goes where
    // listener and animation exceptions go.
    private static void fire(EventType<RXPageLifecycleEvent> type, Page page, RXCarousel carousel) {
        if (type == RXPageLifecycleEvent.OPENING) {
            page.phase = Phase.OPENING;
        } else if (type == RXPageLifecycleEvent.OPENED) {
            page.phase = Phase.OPENED;
        } else if (type == RXPageLifecycleEvent.CLOSING) {
            page.phase = Phase.CLOSING;
        } else if (type == RXPageLifecycleEvent.CLOSED) {
            page.phase = Phase.CLOSED;
        }
        try {
            carousel.fireEvent(new RXPageLifecycleEvent(type, page.index, page.node));
        } catch (RuntimeException e) {
            report(e);
        }
    }

    // The factory is the application's code too: a factory that throws is
    // reported the same way and leaves the page without a node.
    private static Node callFactory(Callback<Integer, Node> factory, int index) {
        try {
            return factory.call(index);
        } catch (RuntimeException e) {
            report(e);
            return null;
        }
    }

    private static void report(RuntimeException e) {
        Thread thread = Thread.currentThread();
        thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
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

        // A countdown the pointer paused resumes; during a page change it
        // restarts once the pages settle.
        if (canRunAutoplay(carousel)) {
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

    private static void stopFade(FadeTransition fade) {
        if (fade != null) {
            fade.stop();
        }
    }

    // ==================== Tree Showing Detection ====================

    private void installTreeShowingListener(RXCarousel carousel) {
        disposer.registerListener(treeShowing,
                (obs, oldVal, newVal) -> onTreeShowingChanged(carousel, newVal));
    }

    private void onTreeShowingChanged(RXCarousel carousel, boolean showing) {
        if (!showing) {
            pauseAutoplay();
        } else if (canRunAutoplay(carousel)) {
            startAutoplay(carousel);
        }
    }

    // ==================== Auto Play ====================

    // The one condition every start of the countdown checks: auto-play is on,
    // the carousel is showing, no page change is in progress, the pointer
    // does not pause it, and a next page exists.
    private boolean canRunAutoplay(RXCarousel carousel) {
        return carousel.isAutoPlay()
                && treeShowing.get()
                && !transitioning
                && !(mouseHovering && carousel.isHoverPause())
                && hasNextPage(carousel);
    }

    private static boolean hasNextPage(RXCarousel carousel) {
        return carousel.isCircular() || carousel.getSelectedIndex() < carousel.getPageCount() - 1;
    }

    // Starts the countdown when it may run; stops one running once no next
    // page exists, as on the last page of a carousel that stopped wrapping or
    // lost pages. A countdown paused for another reason stays paused.
    private void refreshAutoplay(RXCarousel carousel) {
        if (canRunAutoplay(carousel)) {
            startAutoplay(carousel);
        } else if (!hasNextPage(carousel)) {
            stopAutoplay(carousel);
        }
    }

    /**
     * Starts (or resumes) the auto-play countdown. Creates a new Timeline
     * that animates autoPlayProgress from its current value to 1.0 over
     * the remaining duration. When it reaches 1.0, the next page is shown.
     */
    private void startAutoplay(RXCarousel carousel) {
        Duration interval = carousel.getAutoPlayInterval();
        // isUnknown()/isIndefinite() pass the lessThan clamp (NaN compares
        // false, infinity is not less) and would feed NaN / a never-firing
        // keyframe into the countdown timeline.
        if (interval == null || interval.isUnknown() || interval.isIndefinite()
                || interval.lessThan(MIN_AUTOPLAY_INTERVAL)) {
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

    private void updatePlaceholder(RXCarousel carousel) {
        if (targetIndex() < 0) {
            showPlaceholder(carousel);
        } else {
            hidePlaceholder();
        }
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

    private void hidePlaceholder() {
        if (placeholderRegion != null) {
            placeholderRegion.setVisible(false);
            placeholderRegion.setManaged(false);
            placeholderRegion.getChildren().clear();
        }
        contentPane.setVisible(true);
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        if (disposed) {
            return;
        }
        disposed = true;
        RXCarousel carousel = getSkinnable();
        if (!activated) {
            // Never installed: the animation and the navigator belong to the
            // installed skin.
            engine.dispose(null);
            return;
        }
        stopAutoplay(carousel);
        stopFade(prevButtonFade);
        stopFade(nextButtonFade);
        stopFade(navigatorFade);

        // setSkin has already stored the replacement when it disposes this
        // skin. A replacement continues the pages without events; without
        // one the open page is closed and the cache emptied.
        Skin<?> installed = carousel.getSkin();
        boolean replaced = installed instanceof RXCarouselSkin
                && installed != this
                && ((RXCarouselSkin) installed).getSkinnable() == carousel;
        // The stop runs the animation's finish action; the transition
        // callbacks see this skin disposed and do nothing.
        engine.interrupt();
        transitioning = false;
        settlePending = false;
        leaving = null;
        playing = null;
        carousel.setPageTransitioning(false);
        for (Page page : pendingCached) {
            if (pageCache.get(page.index) == page) {
                fire(RXPageLifecycleEvent.CACHED, page, carousel);
            }
        }
        pendingCached.clear();
        finishPairs(carousel, !replaced);

        List<Page> pages = sortedPages();
        if (replaced) {
            carousel.getProperties().put(HANDOVER_KEY, new Handover(pages, openPage(), doomed));
        } else {
            carousel.getProperties().remove(HANDOVER_KEY);
        }
        pageCache.clear();
        doomed.clear();
        shown = null;
        // The page nodes belong to the application: release them, or they
        // would keep this skin's wrappers reachable.
        for (Page page : pages) {
            detach(page);
            page.wrapper.getChildren().clear();
        }
        if (!replaced) {
            for (Page page : pages) {
                fire(RXPageLifecycleEvent.EVICTED, page, carousel);
            }
        }
        contentPane.getChildren().clear();

        if (navigatorNode != null) {
            navigatorNode.removeEventHandler(MouseEvent.MOUSE_CLICKED, navigatorFocusHandler);
            getChildren().remove(navigatorNode);
            navigatorNode = null;
        }
        CarouselNavigator nav = carousel.getNavigator();
        if (nav != null) {
            nav.dispose();
        }
        getChildren().removeAll(contentPane, prevButton, nextButton);
        if (placeholderRegion != null) {
            placeholderRegion.getChildren().clear();
            getChildren().remove(placeholderRegion);
        }
        engine.dispose(carousel.getAnimation());
    }

    // Completes every started pair: closing pages get CLOSED, opening pages
    // OPENED. A skin that is removed rather than replaced closes the open
    // page as well.
    private void finishPairs(RXCarousel carousel, boolean closeOpenPage) {
        for (Page page : sortedPages()) {
            if (page.phase == Phase.CLOSING) {
                page.wrapper.setVisible(false);
                fire(RXPageLifecycleEvent.CLOSED, page, carousel);
            }
        }
        for (Page page : sortedPages()) {
            if (page.phase == Phase.OPENING) {
                page.wrapper.setVisible(true);
                fire(RXPageLifecycleEvent.OPENED, page, carousel);
            }
        }
        if (closeOpenPage) {
            for (Page page : sortedPages()) {
                if (page.phase == Phase.OPENED) {
                    fire(RXPageLifecycleEvent.CLOSING, page, carousel);
                    page.wrapper.setVisible(false);
                    fire(RXPageLifecycleEvent.CLOSED, page, carousel);
                }
            }
        }
    }

    private List<Page> sortedPages() {
        List<Page> pages = new ArrayList<>(pageCache.values());
        pages.sort(Comparator.comparingInt(page -> page.index));
        return pages;
    }

    private Page openPage() {
        for (Page page : sortedPages()) {
            if (page.phase == Phase.OPENED) {
                return page;
            }
        }
        return null;
    }

    // ==================== Page ====================

    // Which of the opening and closing events a page has received since it
    // was cached or last closed.
    private enum Phase {
        CLOSED, OPENING, OPENED, CLOSING
    }

    // A cached page: the application's node inside a wrapper the skin owns.
    // Animations and the skin change the wrapper only, so the node keeps its
    // own visibility, opacity, transforms and effect. The page remembers the
    // factory that made it: once the carousel has another factory, it is
    // stale whatever happened in between.
    private static final class Page {

        final int index;
        final Node node;
        final Callback<Integer, Node> factory;
        final StackPane wrapper = new StackPane();
        Phase phase = Phase.CLOSED;

        Page(int index, Node node, Callback<Integer, Node> factory) {
            this.index = index;
            this.node = node;
            this.factory = factory;
            wrapper.getStyleClass().add("page");
            wrapper.getChildren().add(node);
        }
    }

    // The pages a replaced skin leaves to its replacement, which continues
    // them without events.
    private static final class Handover {

        final List<HandedPage> pages = new ArrayList<>();
        final int openIndex;

        Handover(List<Page> cached, Page open, Set<Page> stalePages) {
            for (Page page : cached) {
                pages.add(new HandedPage(page.index, page.node, page.factory, stalePages.contains(page)));
            }
            this.openIndex = open == null ? -1 : open.index;
        }
    }

    // A handed-over page; stale when the replaced skin had yet to evict it.
    private record HandedPage(int index, Node node, Callback<Integer, Node> factory, boolean stale) {
    }
}
