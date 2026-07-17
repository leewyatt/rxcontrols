package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSidebarSkin;

import javafx.animation.Interpolator;
import javafx.beans.value.WritableValue;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A permanent, in-layout application navigation sidebar that switches between
 * {@link SidebarMode#EXPANDED} (icon + text) and {@link SidebarMode#MINI}
 * (icon only). It hosts navigation and action items, tracks one
 * {@link #selectedItemProperty() selectedItem}, and animates its width on mode
 * change. It is NOT a drawer: it never opens, closes, overlays, or pushes.
 *
 * <p>In {@code MINI} mode only item graphics are shown (labels are hidden), so
 * give each item a graphic to keep it visible when collapsed.</p>
 */
public class RXSidebar extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-sidebar";
    // ==================== Enums ====================

    /**
     * Width state of an {@link RXSidebar}. The sidebar is always present; only its
     * width and label visibility change.
     */
    public enum SidebarMode {

        /**
         * Icon + text, full {@code expandedWidth}.
         */
        EXPANDED,

        /**
         * Icon only, narrow {@code miniWidth}; labels hidden, exposed via tooltip.
         */
        MINI
    }

    // ==================== Constants ====================

    /**
     * Default mode (expanded).
     */
    public static final SidebarMode DEFAULT_MODE = SidebarMode.EXPANDED;
    /**
     * Default expanded width.
     */
    public static final double DEFAULT_EXPANDED_WIDTH = 260.0;
    /**
     * Default mini width; icon (24px) is centered at miniWidth/2.
     */
    public static final double DEFAULT_MINI_WIDTH = 64.0;
    /**
     * Default whether mode transitions animate.
     */
    private static final boolean DEFAULT_ANIMATED = true;
    /**
     * Default mode-transition duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    /**
     * Default mode-transition interpolator, also the null fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass MINI_PSEUDO_CLASS = PseudoClass.getPseudoClass("mini");

    private final ObservableList<RXSidebarItem> topItems = FXCollections.observableArrayList();
    private final ObservableList<RXSidebarItem> items = FXCollections.observableArrayList();
    private final ObservableList<RXSidebarItem> bottomItems = FXCollections.observableArrayList();

    // ==================== Constructor ====================

    /**
     * Creates an empty, expanded sidebar with default settings.
     */
    public RXSidebar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // The rail is a single Tab stop and that stop is one of its items, never
        // the rail itself. Seeding via applyStyle with a null StyleOrigin (rather
        // than setFocusTraversable) leaves -fx-focus-traversable able to override
        // it; a plain setter would look like a user write and lock CSS out. Same
        // opt-out ScrollPane performs.
        // The WritableValue cast is not decoration: it pins the type argument so
        // the StyleableProperty cast is provably checked rather than unchecked.
        ((StyleableProperty<Boolean>) (WritableValue<Boolean>) focusTraversableProperty())
                .applyStyle(null, Boolean.FALSE);
        updateModePseudoClass();
        initSelection();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSidebarSkin(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The rail is a single Tab stop and that stop is one of its items, never
     * the rail itself, so it defaults to non-traversable. Overriding the initial
     * value (rather than calling {@code setFocusTraversable}) keeps
     * {@code -fx-focus-traversable} able to override it, matching how
     * {@code ScrollPane} opts out.</p>
     */
    @Override
    protected Boolean getInitialFocusTraversable() {
        return Boolean.FALSE;
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Item Lists ====================

    /**
     * @return the live list of pinned-top items
     */
    public final ObservableList<RXSidebarItem> getTopItems() {
        return topItems;
    }

    /**
     * @return the live list of main (scrollable) items
     */
    public final ObservableList<RXSidebarItem> getItems() {
        return items;
    }

    /**
     * @return the live list of pinned-bottom items
     */
    public final ObservableList<RXSidebarItem> getBottomItems() {
        return bottomItems;
    }

    // ==================== Mode ====================

    private final ObjectProperty<SidebarMode> mode =
            new SimpleObjectProperty<>(this, "mode", DEFAULT_MODE) {
                @Override
                protected void invalidated() {
                    updateModePseudoClass(); // side-effect only; null resolved at use-site
                }
            };

    /**
     * The display mode ({@link SidebarMode#EXPANDED} or {@link SidebarMode#MINI}).
     * A {@code null} value is not rejected; it resolves to {@link #DEFAULT_MODE}
     * at the use site. Bindable for external responsive logic.
     *
     * @return the mode property
     */
    public final ObjectProperty<SidebarMode> modeProperty() {
        return mode;
    }

    /**
     * @return the mode, possibly {@code null}
     */
    public final SidebarMode getMode() {
        return mode.get();
    }

    /**
     * @param value the mode, or {@code null} to fall back to the default
     */
    public final void setMode(SidebarMode value) {
        mode.set(value);
    }

    // ==================== Selected Item ====================

    private final ReadOnlyObjectWrapper<RXSidebarNavItem> selectedItem =
            new ReadOnlyObjectWrapper<>(this, "selectedItem") {
                @Override
                protected void invalidated() {
                    onSelectedItemInvalidated(); // mirror to the group
                }
            };

    /**
     * The selected navigation item, or {@code null} when nothing is selected.
     * Selection is mutually exclusive across all three item lists. Action items
     * never participate, so they can never appear here.
     *
     * <p>Read-only by design: the sidebar owns this state, because a user click
     * must be able to write it. Drive it with {@link #selectItem(RXSidebarNavItem)}
     * and {@link #clearSelection()}, and observe it with a listener.</p>
     *
     * @return the read-only selected-item property
     */
    public final ReadOnlyObjectProperty<RXSidebarNavItem> selectedItemProperty() {
        return selectedItem.getReadOnlyProperty();
    }

    /**
     * @return the selected navigation item, or {@code null}
     */
    public final RXSidebarNavItem getSelectedItem() {
        return selectedItem.get();
    }

    /**
     * Selects the given navigation item, clearing any previous selection. Use
     * this to drive the rail from outside (a route change, a button elsewhere,
     * restoring state at startup).
     *
     * <p>An item that is not in any of the three lists yet is still accepted:
     * it becomes the selection, and the rail adopts it once it is added. This
     * supports selecting before populating.</p>
     *
     * @param item the item to select, or {@code null} to clear the selection
     */
    public final void selectItem(RXSidebarNavItem item) {
        selectedItem.set(item);
    }

    /**
     * Clears the selection, leaving no item selected.
     */
    public final void clearSelection() {
        selectedItem.set(null);
    }

    // ==================== Expanded Width ====================

    private final DoubleProperty expandedWidth =
            new SimpleDoubleProperty(this, "expandedWidth", DEFAULT_EXPANDED_WIDTH) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * @return the expanded-width property (clamped/defaulted by the skin)
     */
    public final DoubleProperty expandedWidthProperty() {
        return expandedWidth;
    }

    /**
     * @return the expanded width
     */
    public final double getExpandedWidth() {
        return expandedWidth.get();
    }

    /**
     * @param value the expanded width
     */
    public final void setExpandedWidth(double value) {
        expandedWidth.set(value);
    }

    // ==================== Mini Width ====================

    private final DoubleProperty miniWidth =
            new SimpleDoubleProperty(this, "miniWidth", DEFAULT_MINI_WIDTH) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * @return the mini-width property (clamped/defaulted by the skin)
     */
    public final DoubleProperty miniWidthProperty() {
        return miniWidth;
    }

    /**
     * @return the mini width
     */
    public final double getMiniWidth() {
        return miniWidth.get();
    }

    /**
     * @param value the mini width
     */
    public final void setMiniWidth(double value) {
        miniWidth.set(value);
    }

    // ==================== Header / Footer ====================

    private final ObjectProperty<Node> header = new SimpleObjectProperty<>(this, "header");

    /**
     * @return the header slot property
     */
    public final ObjectProperty<Node> headerProperty() {
        return header;
    }

    /**
     * @return the header node, or {@code null}
     */
    public final Node getHeader() {
        return header.get();
    }

    /**
     * @param value the header node, or {@code null}
     */
    public final void setHeader(Node value) {
        header.set(value);
    }

    private final ObjectProperty<Node> footer = new SimpleObjectProperty<>(this, "footer");

    /**
     * @return the footer slot property
     */
    public final ObjectProperty<Node> footerProperty() {
        return footer;
    }

    /**
     * @return the footer node, or {@code null}
     */
    public final Node getFooter() {
        return footer.get();
    }

    /**
     * @param value the footer node, or {@code null}
     */
    public final void setFooter(Node value) {
        footer.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXSidebar.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * @return whether mode transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * @param value whether mode transitions animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXSidebar.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * @return the animation-duration property; null/non-positive disables animation
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * @return the animation duration, possibly {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * @param value the duration; {@code null} or non-positive disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * @return the animation-interpolator property; {@code null} uses the default
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * @return the animation interpolator, possibly {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * @param value the interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Selection (control-owned; survives skin replacement) ====================

    // Private, never exposed. selectedItem is the only public face of the selection.
    private final ToggleGroup navGroup = new ToggleGroup();
    // Guards the two-way mirror selectedItem <-> navGroup against re-entrant ping-pong.
    private boolean syncingSelection = false;
    // The outgoing selection. Deselecting it is this control's job, not the
    // group's: ToggleGroup only ever deselects its own members, and an item
    // selected before it was added to a list is not one. A stale selected flag
    // left on such an item would later make the group adopt it on add and
    // silently hijack whatever is selected by then.
    private RXSidebarNavItem previousSelection;

    // Called from the constructor.
    private void initSelection() {
        ListChangeListener<RXSidebarItem> membership = this::onItemListChanged;
        topItems.addListener(membership);
        items.addListener(membership);
        bottomItems.addListener(membership);

        // group -> selectedItem (user click path: ToggleButton flips selected, group updates).
        // Only reads the guard, never holds it: selectedItem.set notifies application
        // listeners before it returns, and one of those may legitimately re-select
        // (route normalization). Holding the guard across that would make the mirror
        // mistake the application's write for its own echo and skip it.
        navGroup.selectedToggleProperty().addListener((obs, old, toggle) -> {
            if (syncingSelection) {
                return;
            }
            selectedItem.set(toggle instanceof RXSidebarNavItem nav ? nav : null);
        });
        // selectedItem -> group is mirrored in selectedItem's invalidated() (onSelectedItemInvalidated).
    }

    // nav items auto-join the group on add and leave on remove; action items never join.
    private void onItemListChanged(ListChangeListener.Change<? extends RXSidebarItem> c) {
        while (c.next()) {
            for (RXSidebarItem removed : c.getRemoved()) {
                if (removed instanceof RXSidebarNavItem nav) {
                    nav.setToggleGroup(null);
                }
            }
            for (RXSidebarItem added : c.getAddedSubList()) {
                if (added instanceof RXSidebarNavItem nav) {
                    nav.setToggleGroup(navGroup);
                }
            }
        }
    }

    // Invoked from selectedItem.invalidated(). Mirrors selectedItem -> group and
    // keeps the invariant "exactly the selected item carries selected == true".
    // Idempotent, so it costs nothing on the click path where the group has
    // already done the same work.
    private void onSelectedItemInvalidated() {
        RXSidebarNavItem previous = previousSelection;
        RXSidebarNavItem current = getSelectedItem();
        previousSelection = current;

        // The guard means "this control is writing to the group; ignore the echo".
        // It is released before the property notifies application listeners, so a
        // listener that re-selects is handled as a fresh write, not as an echo.
        syncingSelection = true;
        try {
            if (previous != null && previous != current) {
                // Deselecting a group member makes ToggleGroup clear its
                // selectedToggle, which echoes back through our listener.
                previous.setSelected(false);
            }
            if (current != null) {
                // A member is adopted by the group, which also deselects any other
                // member; a pending item just carries the flag until it is added,
                // and the group adopts it then.
                current.setSelected(true);
            } else {
                navGroup.selectToggle(null);
            }
        } finally {
            syncingSelection = false;
        }
    }

    // ==================== PseudoClass ====================

    private void updateModePseudoClass() {
        SidebarMode current = getMode();
        if (current == null) {
            current = DEFAULT_MODE;
        }
        pseudoClassStateChanged(EXPANDED_PSEUDO_CLASS, current == SidebarMode.EXPANDED);
        pseudoClassStateChanged(MINI_PSEUDO_CLASS, current == SidebarMode.MINI);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {
        private static final CssMetaData<RXSidebar, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXSidebar node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSidebar node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };
        private static final CssMetaData<RXSidebar, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXSidebar node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSidebar node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * @return the CSS metadata associated with this class
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
