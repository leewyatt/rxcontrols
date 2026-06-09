package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import io.github.leewyatt.rxcontrols.carousel.CarouselSkin;
import io.github.leewyatt.rxcontrols.carousel.DefaultNavigator;
import io.github.leewyatt.rxcontrols.carousel.Direction;
import io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSlide;
import io.github.leewyatt.rxcontrols.carousel.animation.CarouselAnimation;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.List;

/**
 * A carousel control that displays pages one at a time with animated transitions.
 *
 * <p>There are two ways to provide pages:</p>
 * <ul>
 *   <li><b>Simple:</b> {@link #setPages(Node...)} — pass page nodes directly.
 *       This is a convenience method that internally sets up the
 *       {@link #pageFactoryProperty() pageFactory} and {@link #pageCountProperty() pageCount}
 *       automatically.</li>
 *   <li><b>Lazy:</b> Set {@link #pageFactoryProperty() pageFactory} and
 *       {@link #pageCountProperty() pageCount} separately. The factory creates page
 *       nodes on demand, which is useful for large page counts.</li>
 * </ul>
 *
 * <p>The transition effect is controlled by the {@link #animationProperty() animation}
 * property, which accepts any {@link CarouselAnimation} implementation.</p>
 *
 * <pre>{@code
 * // Simple: fixed pages
 * RXCarousel carousel = new RXCarousel();
 * carousel.setPages(page1, page2, page3);
 * carousel.setAnimation(new AnimSlide());
 *
 * // Lazy: factory + count
 * carousel.setPageCount(100);
 * carousel.setPageFactory(index -> createPage(index));
 * }</pre>
 */
public class RXCarousel extends Control {

    private static final PseudoClass FIRST_PAGE = PseudoClass.getPseudoClass("first-page");
    private static final PseudoClass LAST_PAGE = PseudoClass.getPseudoClass("last-page");
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");

    private static final String DEFAULT_STYLE_CLASS = "rx-carousel";

    /**
     * Creates a new carousel with default settings.
     */
    public RXCarousel() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(true);
        updatePseudoClassStates();

        selectedIndex.addListener((obs, oldVal, newVal) -> updatePseudoClassStates());
        pageCount.addListener((obs, oldVal, newVal) -> updatePseudoClassStates());
        pageFactory.addListener((obs, oldVal, newVal) -> updatePseudoClassStates());
    }

    private void updatePseudoClassStates() {
        int idx = getSelectedIndex();
        int count = getPageCount();
        boolean empty = count <= 0 || getPageFactory() == null;
        pseudoClassStateChanged(EMPTY, empty);
        pseudoClassStateChanged(FIRST_PAGE, !empty && idx == 0);
        pseudoClassStateChanged(LAST_PAGE, !empty && idx == count - 1);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CarouselSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Page Management ====================

    private final IntegerProperty pageCount = new SimpleIntegerProperty(this, "pageCount", 0) {
        @Override
        protected void invalidated() {
            if (get() < 0) {
                if (!isBound()) {
                    set(0);
                }
                throw new IllegalArgumentException("pageCount cannot be negative");
            }
        }
    };

    /**
     * The total number of pages in the carousel.
     *
     * @return the page count property
     */
    public final IntegerProperty pageCountProperty() {
        return pageCount;
    }

    /**
     * Returns the total number of pages in the carousel.
     *
     * @return the page count
     */
    public final int getPageCount() {
        return pageCount.get();
    }

    /**
     * Sets the total number of pages in the carousel.
     *
     * @param value the page count
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public final void setPageCount(int value) {
        pageCount.set(value);
    }

    private final ObjectProperty<Callback<Integer, Node>> pageFactory =
            new SimpleObjectProperty<>(this, "pageFactory");

    /**
     * A factory that creates page nodes on demand. The factory receives a page
     * index and returns the corresponding node.
     *
     * @return the page factory property
     */
    public final ObjectProperty<Callback<Integer, Node>> pageFactoryProperty() {
        return pageFactory;
    }

    /**
     * Returns the page factory.
     *
     * @return the page factory
     */
    public final Callback<Integer, Node> getPageFactory() {
        return pageFactory.get();
    }

    /**
     * Sets the page factory.
     *
     * @param factory the page factory
     */
    public final void setPageFactory(Callback<Integer, Node> factory) {
        pageFactory.set(factory);
    }

    private final ReadOnlyIntegerWrapper selectedIndex =
            new ReadOnlyIntegerWrapper(this, "selectedIndex", 0);

    /**
     * The index of the currently displayed page (read-only).
     *
     * @return the selected index property
     */
    public final ReadOnlyIntegerProperty selectedIndexProperty() {
        return selectedIndex.getReadOnlyProperty();
    }

    /**
     * Returns the index of the currently displayed page.
     *
     * @return the selected index
     */
    public final int getSelectedIndex() {
        return selectedIndex.get();
    }

    /**
     * Sets the selected index. This method is intended to be used by experts,
     * primarily by those implementing new Skins or Behaviors. It is not common
     * for developers to access this method directly. Prefer using
     * {@link #goToPage(int)} for programmatic navigation.
     *
     * @param index the new selected index
     */
    public final void setSelectedIndex(int index) {
        selectedIndex.set(index);
    }

    // ==================== Animation Configuration ====================

    private final ObjectProperty<CarouselAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimSlide());

    /**
     * The animation used for page transitions.
     *
     * @return the animation property
     */
    public final ObjectProperty<CarouselAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for page transitions.
     *
     * @return the animation
     */
    public final CarouselAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for page transitions.
     *
     * @param animation the animation
     */
    public final void setAnimation(CarouselAnimation animation) {
        this.animation.set(animation);
    }

    private final ObjectProperty<Duration> animationDuration =
            new SimpleObjectProperty<>(this, "animationDuration", Duration.millis(500));

    /**
     * The duration of page transition animations.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of page transition animations.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of page transition animations.
     *
     * @param duration the animation duration
     */
    public final void setAnimationDuration(Duration duration) {
        animationDuration.set(duration);
    }

    // ==================== Auto Play ====================

    private final BooleanProperty autoPlay =
            new SimpleBooleanProperty(this, "autoPlay", false);

    /**
     * Whether the carousel automatically advances to the next page.
     *
     * @return the auto play property
     */
    public final BooleanProperty autoPlayProperty() {
        return autoPlay;
    }

    /**
     * Returns whether the carousel automatically advances to the next page.
     *
     * @return true if auto play is enabled
     */
    public final boolean isAutoPlay() {
        return autoPlay.get();
    }

    /**
     * Sets whether the carousel automatically advances to the next page.
     *
     * @param value true to enable auto play
     */
    public final void setAutoPlay(boolean value) {
        autoPlay.set(value);
    }

    private final ObjectProperty<Duration> autoPlayInterval =
            new SimpleObjectProperty<>(this, "autoPlayInterval", Duration.seconds(3));

    /**
     * The interval between automatic page advances. The minimum accepted value
     * is 100ms to prevent UI thread starvation.
     *
     * @return the auto play interval property
     */
    public final ObjectProperty<Duration> autoPlayIntervalProperty() {
        return autoPlayInterval;
    }

    /**
     * Returns the interval between automatic page advances.
     *
     * @return the auto play interval
     */
    public final Duration getAutoPlayInterval() {
        return autoPlayInterval.get();
    }

    /**
     * Sets the interval between automatic page advances.
     *
     * @param interval the auto play interval
     */
    public final void setAutoPlayInterval(Duration interval) {
        autoPlayInterval.set(interval);
    }

    private final BooleanProperty hoverPause =
            new SimpleBooleanProperty(this, "hoverPause", true);

    /**
     * Whether auto play pauses when the mouse hovers over the carousel.
     *
     * @return the hover pause property
     */
    public final BooleanProperty hoverPauseProperty() {
        return hoverPause;
    }

    /**
     * Returns whether auto play pauses when the mouse hovers over the carousel.
     *
     * @return true if hover pause is enabled
     */
    public final boolean isHoverPause() {
        return hoverPause.get();
    }

    /**
     * Sets whether auto play pauses when the mouse hovers over the carousel.
     *
     * @param value true to enable hover pause
     */
    public final void setHoverPause(boolean value) {
        hoverPause.set(value);
    }

    // ==================== Behavior ====================

    private final BooleanProperty circular =
            new SimpleBooleanProperty(this, "circular", true);

    /**
     * Whether the carousel wraps around from the last page to the first and vice versa.
     *
     * @return the circular property
     */
    public final BooleanProperty circularProperty() {
        return circular;
    }

    /**
     * Returns whether the carousel wraps around from the last page to the first
     * and vice versa.
     *
     * @return true if circular navigation is enabled
     */
    public final boolean isCircular() {
        return circular.get();
    }

    /**
     * Sets whether the carousel wraps around from the last page to the first
     * and vice versa.
     *
     * @param value true to enable circular navigation
     */
    public final void setCircular(boolean value) {
        circular.set(value);
    }

    // ==================== Navigation Arrows ====================

    private final ObjectProperty<DisplayMode> arrowDisplayMode =
            new SimpleObjectProperty<>(this, "arrowDisplayMode", DisplayMode.AUTO);

    /**
     * Controls the visibility of the left/right navigation arrows.
     *
     * @return the arrow display mode property
     */
    public final ObjectProperty<DisplayMode> arrowDisplayModeProperty() {
        return arrowDisplayMode;
    }

    /**
     * Returns the display mode of the left/right navigation arrows.
     *
     * @return the arrow display mode
     */
    public final DisplayMode getArrowDisplayMode() {
        return arrowDisplayMode.get();
    }

    /**
     * Sets the display mode of the left/right navigation arrows.
     *
     * @param mode the arrow display mode
     */
    public final void setArrowDisplayMode(DisplayMode mode) {
        arrowDisplayMode.set(mode);
    }

    // ==================== Navigator (Pagination) ====================

    private final ObjectProperty<CarouselNavigator> navigator =
            new SimpleObjectProperty<>(this, "navigator", new DefaultNavigator());

    /**
     * The bottom navigator (pagination) component. Set to {@code null} to
     * remove the bottom navigation entirely.
     *
     * @return the navigator property
     */
    public final ObjectProperty<CarouselNavigator> navigatorProperty() {
        return navigator;
    }

    /**
     * Returns the bottom navigator (pagination) component.
     *
     * @return the navigator
     */
    public final CarouselNavigator getNavigator() {
        return navigator.get();
    }

    /**
     * Sets the bottom navigator (pagination) component. Set to {@code null}
     * to remove the bottom navigation entirely.
     *
     * @param navigator the navigator
     */
    public final void setNavigator(CarouselNavigator navigator) {
        this.navigator.set(navigator);
    }

    private final ObjectProperty<DisplayMode> navigatorDisplayMode =
            new SimpleObjectProperty<>(this, "navigatorDisplayMode", DisplayMode.SHOW);

    /**
     * Controls the visibility of the bottom navigator.
     *
     * @return the navigator display mode property
     */
    public final ObjectProperty<DisplayMode> navigatorDisplayModeProperty() {
        return navigatorDisplayMode;
    }

    /**
     * Returns the display mode of the bottom navigator.
     *
     * @return the navigator display mode
     */
    public final DisplayMode getNavigatorDisplayMode() {
        return navigatorDisplayMode.get();
    }

    /**
     * Sets the display mode of the bottom navigator.
     *
     * @param mode the navigator display mode
     */
    public final void setNavigatorDisplayMode(DisplayMode mode) {
        navigatorDisplayMode.set(mode);
    }

    // ==================== Cache ====================

    private final IntegerProperty cacheDistance =
            new SimpleIntegerProperty(this, "cacheDistance", -1);

    /**
     * The number of pages to keep cached on each side of the current page.
     * <ul>
     *   <li>{@code -1} (default): cache all pages, never evict</li>
     *   <li>{@code 0}: only keep the current page</li>
     *   <li>{@code N}: keep the current page and N pages on each side (2N+1 total)</li>
     * </ul>
     *
     * @return the cache distance property
     */
    public final IntegerProperty cacheDistanceProperty() {
        return cacheDistance;
    }

    /**
     * Returns the number of pages to keep cached on each side of the current page.
     *
     * @return the cache distance
     */
    public final int getCacheDistance() {
        return cacheDistance.get();
    }

    /**
     * Sets the number of pages to keep cached on each side of the current page.
     *
     * @param distance the cache distance
     */
    public final void setCacheDistance(int distance) {
        cacheDistance.set(distance);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder =
            new SimpleObjectProperty<>(this, "placeholder");

    /**
     * A node to display when the carousel has no pages ({@code pageCount == 0}).
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder node displayed when the carousel has no pages.
     *
     * @return the placeholder node
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the placeholder node displayed when the carousel has no pages.
     *
     * @param placeholder the placeholder node
     */
    public final void setPlaceholder(Node placeholder) {
        this.placeholder.set(placeholder);
    }

    // ==================== Auto Play Progress ====================

    private final ReadOnlyDoubleWrapper autoPlayProgress =
            new ReadOnlyDoubleWrapper(this, "autoPlayProgress", 0.0);

    /**
     * The progress of the auto-play countdown for the current page, from 0.0 to 1.0
     * (read-only). When auto-play is enabled, this value advances from 0.0 to 1.0
     * over the duration of {@link #autoPlayIntervalProperty()}. When it reaches 1.0,
     * the carousel automatically advances to the next page.
     *
     * <p>This property can be used to drive a progress bar or countdown indicator.
     * The value is 0.0 when auto-play is disabled or during a page transition.</p>
     *
     * @return the auto-play progress property
     */
    public final ReadOnlyDoubleProperty autoPlayProgressProperty() {
        return autoPlayProgress.getReadOnlyProperty();
    }

    /**
     * Returns the current auto-play progress value.
     *
     * @return the progress value between 0.0 and 1.0
     */
    public final double getAutoPlayProgress() {
        return autoPlayProgress.get();
    }

    /**
     * Sets the auto-play progress. This method is intended to be used by experts,
     * primarily by those implementing new Skins or Behaviors. It is not common
     * for developers to access this method directly.
     *
     * @param value the progress value between 0.0 and 1.0
     */
    public final void setAutoPlayProgress(double value) {
        autoPlayProgress.set(value);
    }

    // ==================== Page Transitioning ====================

    private final ReadOnlyBooleanWrapper pageTransitioning =
            new ReadOnlyBooleanWrapper(this, "pageTransitioning", false);

    /**
     * Whether a page transition animation is currently playing (read-only).
     * This property is {@code true} from the moment a transition animation starts
     * until it finishes (naturally or via interruption). It remains {@code false}
     * during direct cuts ({@link #goToPage(int, boolean) goToPage(index, false)}).
     *
     * <p>Typical usage — disable controls while animating:</p>
     * <pre>{@code
     * button.disableProperty().bind(carousel.pageTransitioningProperty());
     * }</pre>
     *
     * @return the page transitioning property
     */
    public final ReadOnlyBooleanProperty pageTransitioningProperty() {
        return pageTransitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a page transition animation is currently playing.
     *
     * @return true if a page transition is in progress
     */
    public final boolean isPageTransitioning() {
        return pageTransitioning.get();
    }

    /**
     * Sets the page transitioning state. This method is intended to be used by experts,
     * primarily by those implementing new Skins or Behaviors. It is not common
     * for developers to access this method directly.
     *
     * @param value true if a page transition is in progress
     */
    public final void setPageTransitioning(boolean value) {
        pageTransitioning.set(value);
    }

    // ==================== Internal transition flags ====================

    private boolean animateTransition = true;
    private Direction directionHint;

    /**
     * Returns whether the current transition should be animated. This method
     * is intended to be used by experts, primarily by those implementing new
     * Skins or Behaviors. It is not common for developers to access this
     * method directly.
     *
     * @return true if the transition should be animated
     */
    public boolean isAnimateTransition() {
        return animateTransition;
    }

    /**
     * Returns the direction hint for the current transition, or {@code null}
     * if the skin should compute the direction from index comparison. This
     * method is intended to be used by experts, primarily by those implementing
     * new Skins or Behaviors. It is not common for developers to access this
     * method directly.
     *
     * @return the direction hint, or null
     */
    public Direction getDirectionHint() {
        return directionHint;
    }

    // ==================== Public API ====================

    /**
     * Advances to the next page. In non-circular mode, does nothing if already
     * on the last page.
     */
    public void next() {
        int count = getPageCount();
        if (count <= 1) {
            return;
        }
        int current = getSelectedIndex();
        int target;
        if (isCircular()) {
            target = (current + 1) % count;
        } else if (current < count - 1) {
            target = current + 1;
        } else {
            return;
        }
        directionHint = Direction.FORWARD;
        try {
            goToPage(target);
        } finally {
            directionHint = null;
        }
    }

    /**
     * Goes back to the previous page. In non-circular mode, does nothing if
     * already on the first page.
     */
    public void previous() {
        int count = getPageCount();
        if (count <= 1) {
            return;
        }
        int current = getSelectedIndex();
        int target;
        if (isCircular()) {
            target = (current - 1 + count) % count;
        } else if (current > 0) {
            target = current - 1;
        } else {
            return;
        }
        directionHint = Direction.BACKWARD;
        try {
            goToPage(target);
        } finally {
            directionHint = null;
        }
    }

    /**
     * Navigates to the page at the given index with animation.
     *
     * @param index the target page index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void goToPage(int index) {
        goToPage(index, true);
    }

    /**
     * Navigates to the page at the given index, optionally with animation.
     *
     * @param index   the target page index
     * @param animate whether to animate the transition
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void goToPage(int index, boolean animate) {
        int count = getPageCount();
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("Index: " + index + ", PageCount: " + count);
        }
        if (index != getSelectedIndex()) {
            animateTransition = animate;
            try {
                setSelectedIndex(index);
            } finally {
                animateTransition = true;
            }
        }
    }

    private ObservableList<Node> observablePages;
    private ListChangeListener<Node> pagesListener;

    private void clearPagesListener() {
        if (observablePages != null && pagesListener != null) {
            observablePages.removeListener(pagesListener);
            observablePages = null;
            pagesListener = null;
        }
    }

    /**
     * Sets the carousel pages from a fixed list of nodes. This is a convenience
     * method that configures the page factory and page count automatically.
     *
     * @param pages the page nodes
     */
    public void setPages(Node... pages) {
        clearPagesListener();
        List<Node> list = Arrays.asList(pages);
        setPageCount(list.size());
        setPageFactory(list::get);
    }

    /**
     * Sets the carousel pages from an observable list. The page count is
     * automatically updated when the list changes.
     *
     * @param pages the observable list of page nodes
     */
    public void setPages(ObservableList<Node> pages) {
        clearPagesListener();
        observablePages = pages;
        pagesListener = c -> setPageCount(pages.size());
        setPageCount(pages.size());
        setPageFactory(pages::get);
        pages.addListener(pagesListener);
    }

    // ==================== Page Lifecycle Event Handlers ====================

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageCached;

    /**
     * Called when a page enters the cache. This is a convenience property
     * equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.CACHED, handler)}, except
     * that it allows only a single handler (setting a new handler replaces
     * the previous one).
     *
     * @return the onPageCached handler property
     * @see PageLifecycleEvent#CACHED
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageCachedProperty() {
        if (onPageCached == null) {
            onPageCached = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.CACHED, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageCached";
                }
            };
        }
        return onPageCached;
    }

    /**
     * Sets the handler for page cached events.
     *
     * @param handler the event handler
     */
    public final void setOnPageCached(EventHandler<PageLifecycleEvent> handler) {
        onPageCachedProperty().set(handler);
    }

    /**
     * Returns the handler for page cached events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageCached() {
        return onPageCached == null ? null : onPageCached.get();
    }

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageOpening;

    /**
     * Called when a page begins to appear. This is a convenience property
     * equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.OPENING, handler)}, except
     * that it allows only a single handler.
     *
     * @return the onPageOpening handler property
     * @see PageLifecycleEvent#OPENING
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageOpeningProperty() {
        if (onPageOpening == null) {
            onPageOpening = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.OPENING, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageOpening";
                }
            };
        }
        return onPageOpening;
    }

    /**
     * Sets the handler for page opening events.
     *
     * @param handler the event handler
     */
    public final void setOnPageOpening(EventHandler<PageLifecycleEvent> handler) {
        onPageOpeningProperty().set(handler);
    }

    /**
     * Returns the handler for page opening events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageOpening() {
        return onPageOpening == null ? null : onPageOpening.get();
    }

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageOpened;

    /**
     * Called when a page has fully appeared and the transition is complete.
     * This is a convenience property equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.OPENED, handler)}, except
     * that it allows only a single handler.
     *
     * @return the onPageOpened handler property
     * @see PageLifecycleEvent#OPENED
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageOpenedProperty() {
        if (onPageOpened == null) {
            onPageOpened = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.OPENED, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageOpened";
                }
            };
        }
        return onPageOpened;
    }

    /**
     * Sets the handler for page opened events.
     *
     * @param handler the event handler
     */
    public final void setOnPageOpened(EventHandler<PageLifecycleEvent> handler) {
        onPageOpenedProperty().set(handler);
    }

    /**
     * Returns the handler for page opened events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageOpened() {
        return onPageOpened == null ? null : onPageOpened.get();
    }

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageClosing;

    /**
     * Called when a page begins to disappear. This is a convenience property
     * equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.CLOSING, handler)}, except
     * that it allows only a single handler.
     *
     * @return the onPageClosing handler property
     * @see PageLifecycleEvent#CLOSING
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageClosingProperty() {
        if (onPageClosing == null) {
            onPageClosing = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.CLOSING, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageClosing";
                }
            };
        }
        return onPageClosing;
    }

    /**
     * Sets the handler for page closing events.
     *
     * @param handler the event handler
     */
    public final void setOnPageClosing(EventHandler<PageLifecycleEvent> handler) {
        onPageClosingProperty().set(handler);
    }

    /**
     * Returns the handler for page closing events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageClosing() {
        return onPageClosing == null ? null : onPageClosing.get();
    }

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageClosed;

    /**
     * Called when a page has fully disappeared. This is a convenience property
     * equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.CLOSED, handler)}, except
     * that it allows only a single handler.
     *
     * @return the onPageClosed handler property
     * @see PageLifecycleEvent#CLOSED
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageClosedProperty() {
        if (onPageClosed == null) {
            onPageClosed = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.CLOSED, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageClosed";
                }
            };
        }
        return onPageClosed;
    }

    /**
     * Sets the handler for page closed events.
     *
     * @param handler the event handler
     */
    public final void setOnPageClosed(EventHandler<PageLifecycleEvent> handler) {
        onPageClosedProperty().set(handler);
    }

    /**
     * Returns the handler for page closed events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageClosed() {
        return onPageClosed == null ? null : onPageClosed.get();
    }

    private ObjectProperty<EventHandler<PageLifecycleEvent>> onPageEvicted;

    /**
     * Called when a page is evicted from the cache. This is a convenience
     * property equivalent to calling
     * {@code addEventHandler(PageLifecycleEvent.EVICTED, handler)}, except
     * that it allows only a single handler.
     *
     * @return the onPageEvicted handler property
     * @see PageLifecycleEvent#EVICTED
     */
    public final ObjectProperty<EventHandler<PageLifecycleEvent>> onPageEvictedProperty() {
        if (onPageEvicted == null) {
            onPageEvicted = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(PageLifecycleEvent.EVICTED, get());
                }

                @Override
                public Object getBean() {
                    return RXCarousel.this;
                }

                @Override
                public String getName() {
                    return "onPageEvicted";
                }
            };
        }
        return onPageEvicted;
    }

    /**
     * Sets the handler for page evicted events.
     *
     * @param handler the event handler
     */
    public final void setOnPageEvicted(EventHandler<PageLifecycleEvent> handler) {
        onPageEvictedProperty().set(handler);
    }

    /**
     * Returns the handler for page evicted events.
     *
     * @return the event handler
     */
    public final EventHandler<PageLifecycleEvent> getOnPageEvicted() {
        return onPageEvicted == null ? null : onPageEvicted.get();
    }

    // ==================== Page Cache ====================

    /**
     * Clears the entire page cache. All cached page nodes will be removed and
     * {@link PageLifecycleEvent#EVICTED} events will be fired.
     */
    public void clearPageCache() {
        if (getSkin() instanceof CarouselSkin) {
            ((CarouselSkin) getSkin()).clearPageCache();
        }
    }

    /**
     * Clears the cached page at the given index. A
     * {@link PageLifecycleEvent#EVICTED} event will be fired if the page
     * was cached.
     *
     * @param index the page index to clear
     */
    public void clearPageCache(int index) {
        if (getSkin() instanceof CarouselSkin) {
            ((CarouselSkin) getSkin()).clearPageCache(index);
        }
    }
}
