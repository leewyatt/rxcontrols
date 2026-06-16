package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.SidebarMode;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSidebarSkin;

import javafx.animation.Interpolator;
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

    // ==================== Constants ====================

    /** Default mode (expanded). */
    public static final SidebarMode DEFAULT_MODE = SidebarMode.EXPANDED;
    /** Default expanded width. */
    public static final double DEFAULT_EXPANDED_WIDTH = 260.0;
    /** Default mini width; icon (24px) is centered at miniWidth/2. */
    public static final double DEFAULT_MINI_WIDTH = 64.0;
    /** Default whether mode transitions animate. */
    public static final boolean DEFAULT_ANIMATED = true;
    /** Default mode-transition duration. */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    /** Default mode-transition interpolator, also the null fallback. */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final String DEFAULT_STYLE_CLASS = "rx-sidebar";

    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass MINI_PSEUDO_CLASS = PseudoClass.getPseudoClass("mini");

    private final ObservableList<RXSidebarItem> topItems = FXCollections.observableArrayList();
    private final ObservableList<RXSidebarItem> items = FXCollections.observableArrayList();
    private final ObservableList<RXSidebarItem> bottomItems = FXCollections.observableArrayList();

    // ==================== Constructor ====================

    /** Creates an empty, expanded sidebar with default settings. */
    public RXSidebar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        updateModePseudoClass();
        initSelection();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSidebarSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Item Lists ====================

    /** @return the live list of pinned-top items */
    public final ObservableList<RXSidebarItem> getTopItems() {
        return topItems;
    }

    /** @return the live list of main (scrollable) items */
    public final ObservableList<RXSidebarItem> getItems() {
        return items;
    }

    /** @return the live list of pinned-bottom items */
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

    /** @return the mode, possibly {@code null} */
    public final SidebarMode getMode() {
        return mode.get();
    }

    /** @param value the mode, or {@code null} to fall back to the default */
    public final void setMode(SidebarMode value) {
        mode.set(value);
    }

    // ==================== Selected Item ====================

    private final ObjectProperty<RXSidebarItem> selectedItem =
            new SimpleObjectProperty<>(this, "selectedItem") {
                @Override
                protected void invalidated() {
                    onSelectedItemInvalidated(); // mirror to group + derive typed view
                }
            };

    /**
     * The currently selected item, or {@code null} (allow-none). Setting a nav
     * item selects it and clears the previous selection; selecting is mutually
     * exclusive across all three item lists. A non-navigation item is stored
     * leniently but holds no group selection.
     *
     * @return the selected item property
     */
    public final ObjectProperty<RXSidebarItem> selectedItemProperty() {
        return selectedItem;
    }

    /** @return the selected item, or {@code null} */
    public final RXSidebarItem getSelectedItem() {
        return selectedItem.get();
    }

    /** @param value the item to select, or {@code null} */
    public final void setSelectedItem(RXSidebarItem value) {
        selectedItem.set(value);
    }

    // ==================== Selected Navigation Item (typed read-only view) ====================

    private final ReadOnlyObjectWrapper<RXSidebarNavItem> selectedNavigationItem =
            new ReadOnlyObjectWrapper<>(this, "selectedNavigationItem");

    /**
     * The selected item narrowed to a navigation item, or {@code null} when nothing
     * (or a non-navigation item) is selected. Derived from {@link #selectedItemProperty()}.
     *
     * @return the read-only selected-navigation-item property
     */
    public final ReadOnlyObjectProperty<RXSidebarNavItem> selectedNavigationItemProperty() {
        return selectedNavigationItem.getReadOnlyProperty();
    }

    /** @return the selected navigation item, or {@code null} */
    public final RXSidebarNavItem getSelectedNavigationItem() {
        return selectedNavigationItem.get();
    }

    // ==================== Expanded Width ====================

    private final DoubleProperty expandedWidth =
            new SimpleDoubleProperty(this, "expandedWidth", DEFAULT_EXPANDED_WIDTH) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /** @return the expanded-width property (clamped/defaulted by the skin) */
    public final DoubleProperty expandedWidthProperty() {
        return expandedWidth;
    }

    /** @return the expanded width */
    public final double getExpandedWidth() {
        return expandedWidth.get();
    }

    /** @param value the expanded width */
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

    /** @return the mini-width property (clamped/defaulted by the skin) */
    public final DoubleProperty miniWidthProperty() {
        return miniWidth;
    }

    /** @return the mini width */
    public final double getMiniWidth() {
        return miniWidth.get();
    }

    /** @param value the mini width */
    public final void setMiniWidth(double value) {
        miniWidth.set(value);
    }

    // ==================== Header / Footer ====================

    private final ObjectProperty<Node> header = new SimpleObjectProperty<>(this, "header");

    /** @return the header slot property */
    public final ObjectProperty<Node> headerProperty() {
        return header;
    }

    /** @return the header node, or {@code null} */
    public final Node getHeader() {
        return header.get();
    }

    /** @param value the header node, or {@code null} */
    public final void setHeader(Node value) {
        header.set(value);
    }

    private final ObjectProperty<Node> footer = new SimpleObjectProperty<>(this, "footer");

    /** @return the footer slot property */
    public final ObjectProperty<Node> footerProperty() {
        return footer;
    }

    /** @return the footer node, or {@code null} */
    public final Node getFooter() {
        return footer.get();
    }

    /** @param value the footer node, or {@code null} */
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

    /** @return the animated property */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /** @return whether mode transitions animate */
    public final boolean isAnimated() {
        return animated.get();
    }

    /** @param value whether mode transitions animate */
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

    /** @return the animation-duration property; null/non-positive disables animation */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /** @return the animation duration, possibly {@code null} */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /** @param value the duration; {@code null} or non-positive disables animation */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /** @return the animation-interpolator property; {@code null} uses the default */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /** @return the animation interpolator, possibly {@code null} */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /** @param value the interpolator, or {@code null} for the default */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Selection (control-owned; survives skin replacement) ====================

    // Private, never exposed. Only selectedItem (+ derived selectedNavigationItem) is public.
    private final ToggleGroup navGroup = new ToggleGroup();
    // Guards the two-way mirror selectedItem <-> navGroup against re-entrant ping-pong.
    private boolean syncingSelection = false;

    // Called from the constructor.
    private void initSelection() {
        ListChangeListener<RXSidebarItem> membership = this::onItemListChanged;
        topItems.addListener(membership);
        items.addListener(membership);
        bottomItems.addListener(membership);

        // group -> selectedItem (user click path: ToggleButton flips selected, group updates)
        navGroup.selectedToggleProperty().addListener((obs, old, toggle) -> {
            if (syncingSelection) {
                return;
            }
            syncingSelection = true;
            try {
                setSelectedItem(toggle instanceof RXSidebarItem item ? item : null);
            } finally {
                // Clear the guard even if a downstream selectedItem listener throws,
                // so a single bad listener cannot wedge selection permanently.
                syncingSelection = false;
            }
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

    // Invoked from selectedItem.invalidated(). Mirrors selectedItem -> group and derives the typed view.
    private void onSelectedItemInvalidated() {
        if (!syncingSelection) {
            syncingSelection = true;
            try {
                RXSidebarItem sel = getSelectedItem();
                if (sel instanceof RXSidebarNavItem nav) {
                    nav.setSelected(true);          // group clears the previously selected toggle
                } else {
                    navGroup.selectToggle(null);    // null or non-nav => no selection
                }
            } finally {
                syncingSelection = false;
            }
        }
        // Derived typed view is always updated (read-only downstream; no feedback loop).
        RXSidebarItem sel = getSelectedItem();
        selectedNavigationItem.set(sel instanceof RXSidebarNavItem nav ? nav : null);
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

    /** @return the CSS metadata associated with this class */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
