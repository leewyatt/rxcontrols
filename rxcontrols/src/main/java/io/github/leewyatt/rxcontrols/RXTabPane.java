package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTabPaneSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A content-owning, Material-style tab container: a strip of tab headers with a
 * sliding underline indicator, hosting one page of content per tab. Unlike
 * {@link RXSegmentedControl} (a one-of-many value selector) it owns and swaps
 * page content.
 *
 * <p><b>Selection.</b> The pane is driven by a replaceable
 * {@link SingleSelectionModel} of {@link RXTab}. {@link #selectedIndexProperty()
 * selectedIndex} ({@code -1} when nothing is selected) and
 * {@link #selectedItemProperty() selectedItem} are read-only projections of that
 * model. While enabled tabs exist the pane keeps one selected (adding the first
 * enabled tab auto-selects it, removing the selected tab recovers to the nearest
 * available one); {@link SingleSelectionModel#clearSelection()} may still drive
 * it to {@code -1}, and an all-disabled pane stays at {@code -1}.</p>
 *
 * <p><b>Sides.</b> {@link #sideProperty() side} defaults to {@link Side#TOP}. All
 * four sides render with real geometry — TOP/BOTTOM lay the strip out horizontally,
 * LEFT/RIGHT vertically — and each drives the matching
 * {@code :top/:right/:bottom/:left} pseudo-class; only a {@code null} side falls back
 * to TOP (see {@link #effectiveSide()}).</p>
 */
@DefaultProperty("tabs")
public class RXTabPane extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-tab-pane";
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(250.0);
    private static final double DEFAULT_TAB_MIN_WIDTH = 0.0;
    private static final double DEFAULT_TAB_MAX_WIDTH = Double.MAX_VALUE;
    private static final Side DEFAULT_SIDE = Side.TOP;
    private static final Variant DEFAULT_VARIANT = Variant.STANDARD;
    private static final ScrollButtonPolicy DEFAULT_SCROLL_BUTTON_POLICY = ScrollButtonPolicy.AUTO;
    private static final TabClosingPolicy DEFAULT_TAB_CLOSING_POLICY = TabClosingPolicy.UNAVAILABLE;
    private static final TabAlignment DEFAULT_TAB_ALIGNMENT = TabAlignment.START;
    private static final boolean DEFAULT_PRESERVE_CONTENT = false;
    private static final boolean DEFAULT_DYNAMIC_HEIGHT = false;
    private static final boolean DEFAULT_WHEEL_SCROLL_ENABLED = true;

    private static final PseudoClass TOP_PSEUDO = PseudoClass.getPseudoClass("top");
    private static final PseudoClass RIGHT_PSEUDO = PseudoClass.getPseudoClass("right");
    private static final PseudoClass BOTTOM_PSEUDO = PseudoClass.getPseudoClass("bottom");
    private static final PseudoClass LEFT_PSEUDO = PseudoClass.getPseudoClass("left");
    private static final PseudoClass STANDARD_PSEUDO = PseudoClass.getPseudoClass("standard");
    private static final PseudoClass FULL_WIDTH_PSEUDO = PseudoClass.getPseudoClass("full-width");
    private static final PseudoClass SCROLLABLE_PSEUDO = PseudoClass.getPseudoClass("scrollable");

    // ==================== Enums ====================

    /**
     * How the tab strip distributes width across its tabs.
     */
    public enum Variant {
        /** Each tab hugs its content width; the strip overflows are compressed. */
        STANDARD,
        /** Tabs divide the available width equally, filling the pane. */
        FULL_WIDTH,
        /** Tabs keep their content width; the strip scrolls when it overflows. */
        SCROLLABLE
    }

    /**
     * When the scroll buttons are shown in the {@link Variant#SCROLLABLE} variant.
     */
    public enum ScrollButtonPolicy {
        /** Shown only while the strip overflows. */
        AUTO,
        /** Always shown. */
        ALWAYS,
        /** Never shown (scroll via wheel / programmatic only). */
        NEVER
    }

    /**
     * Which tabs may show a close affordance.
     */
    public enum TabClosingPolicy {
        /** No tab shows a close affordance. */
        UNAVAILABLE,
        /** Only the selected tab shows a close affordance. */
        SELECTED_TAB,
        /** Every closable tab shows a close affordance. */
        ALL_TABS
    }

    /**
     * Alignment of the tab group within the header when the tabs do not fill it.
     * Direction-independent: for horizontal sides START/CENTER/END read as
     * left/center/right, for vertical sides as top/center/bottom. Applies to the
     * {@link Variant#STANDARD} variant; ignored when {@link Variant#SCROLLABLE}.
     */
    public enum TabAlignment {
        /** Pack the tabs at the leading edge of the strip. */
        START,
        /** Center the tab group within the strip. */
        CENTER,
        /** Pack the tabs at the trailing edge of the strip. */
        END
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty tab pane.
     */
    public RXTabPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.TAB_PANE);
        // Single focus stop; the skin moves the roving focus between header cells.
        setFocusTraversable(true);
        setSelectionModel(new RXTabPaneSelectionModel(this));
        tabs.addListener((ListChangeListener<RXTab>) this::onTabsChanged);
        // Seed the default side/variant pseudo-classes so the contract holds before a
        // skin attaches (invalidated() only fires on change, not the initial value).
        updateSidePseudoClasses();
        updateVariantPseudoClasses();
    }

    /**
     * Creates a tab pane with the given tabs.
     *
     * @param tabs the initial tabs
     */
    public RXTabPane(RXTab... tabs) {
        this();
        getTabs().setAll(tabs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTabPaneSkin(this);
    }

    // ==================== Tabs ====================

    private final ObservableList<RXTab> tabs = FXCollections.observableArrayList();

    /**
     * The mutable list of tabs.
     *
     * @return the tabs list
     */
    public final ObservableList<RXTab> getTabs() {
        return tabs;
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<SingleSelectionModel<RXTab>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel") {
                private SingleSelectionModel<RXTab> oldModel;

                @Override
                protected void invalidated() {
                    SingleSelectionModel<RXTab> newModel = get();
                    onSelectionModelChanged(oldModel, newModel);
                    oldModel = newModel;
                }
            };

    /**
     * The selection model. Replaceable; a custom {@code SingleSelectionModel}
     * still gets correct {@link RXTab#selectedProperty() selected} and
     * {@link RXTab#tabPaneProperty() tabPane} maintenance because the control
     * owns that (not the model).
     *
     * @return the selection-model property
     */
    public final ObjectProperty<SingleSelectionModel<RXTab>> selectionModelProperty() {
        return selectionModel;
    }

    /**
     * Returns the selection model.
     *
     * @return the selection model, may be {@code null}
     */
    public final SingleSelectionModel<RXTab> getSelectionModel() {
        return selectionModel.get();
    }

    /**
     * Sets the selection model.
     *
     * @param value the selection model, or {@code null}
     */
    public final void setSelectionModel(SingleSelectionModel<RXTab> value) {
        selectionModel.set(value);
    }

    // ==================== Selected Index (read-only) ====================

    private final ReadOnlyIntegerWrapper selectedIndex = new ReadOnlyIntegerWrapper(this, "selectedIndex", -1);

    /**
     * Index of the selected tab, or {@code -1}. Read-only projection of the
     * selection model.
     *
     * @return the read-only selected-index property
     */
    public final ReadOnlyIntegerProperty selectedIndexProperty() {
        return selectedIndex.getReadOnlyProperty();
    }

    /**
     * Returns the selected index, or {@code -1}.
     *
     * @return the selected index
     */
    public final int getSelectedIndex() {
        return selectedIndex.get();
    }

    // ==================== Selected Item (read-only) ====================

    private final ReadOnlyObjectWrapper<RXTab> selectedItem = new ReadOnlyObjectWrapper<>(this, "selectedItem");

    /**
     * The selected tab, or {@code null}. Read-only projection of the selection
     * model.
     *
     * @return the read-only selected-item property
     */
    public final ReadOnlyObjectProperty<RXTab> selectedItemProperty() {
        return selectedItem.getReadOnlyProperty();
    }

    /**
     * Returns the selected tab, or {@code null}.
     *
     * @return the selected tab
     */
    public final RXTab getSelectedItem() {
        return selectedItem.get();
    }

    // ==================== Side ====================

    private final ObjectProperty<Side> side = new SimpleObjectProperty<>(this, "side", DEFAULT_SIDE) {
        @Override
        protected void invalidated() {
            updateSidePseudoClasses();
            requestLayout();
        }
    };

    /**
     * Applies the side pseudo-classes for the effective side (null -&gt; TOP) so the
     * CSS side hooks match the rendered geometry. Owned by the control (not the skin)
     * so the state holds from construction, before any skin is attached.
     */
    private void updateSidePseudoClasses() {
        Side s = effectiveSide();
        pseudoClassStateChanged(TOP_PSEUDO, s == Side.TOP);
        pseudoClassStateChanged(RIGHT_PSEUDO, s == Side.RIGHT);
        pseudoClassStateChanged(BOTTOM_PSEUDO, s == Side.BOTTOM);
        pseudoClassStateChanged(LEFT_PSEUDO, s == Side.LEFT);
    }

    /**
     * Position of the tab header. Initial value is {@link Side#TOP}; {@code null}
     * falls back to TOP. All four sides render with real geometry (see
     * {@link #effectiveSide()}).
     *
     * @return the side property
     */
    public final ObjectProperty<Side> sideProperty() {
        return side;
    }

    /**
     * Returns the header side.
     *
     * @return the side, or {@code null}
     */
    public final Side getSide() {
        return side.get();
    }

    /**
     * Sets the header side.
     *
     * @param value the side, or {@code null} for the default
     */
    public final void setSide(Side value) {
        side.set(value);
    }

    /**
     * The side actually used for layout, pseudo-classes and orientation — the
     * single source of truth that keeps them consistent. All four sides are
     * implemented ({@code TOP}/{@code BOTTOM} horizontal, {@code LEFT}/{@code RIGHT}
     * vertical); {@code null} falls back to {@link Side#TOP}.
     *
     * @return the effective side, never {@code null}
     */
    public final Side effectiveSide() {
        Side s = getSide();
        return s == null ? Side.TOP : s;
    }

    // ==================== Variant ====================

    private final ObjectProperty<Variant> variant = new SimpleObjectProperty<>(this, "variant", DEFAULT_VARIANT) {
        @Override
        protected void invalidated() {
            updateVariantPseudoClasses();
            requestLayout();
        }
    };

    /** Applies the variant pseudo-classes for the current variant (null -&gt; STANDARD). */
    private void updateVariantPseudoClasses() {
        Variant v = getVariant() == null ? DEFAULT_VARIANT : getVariant();
        pseudoClassStateChanged(STANDARD_PSEUDO, v == Variant.STANDARD);
        pseudoClassStateChanged(FULL_WIDTH_PSEUDO, v == Variant.FULL_WIDTH);
        pseudoClassStateChanged(SCROLLABLE_PSEUDO, v == Variant.SCROLLABLE);
    }

    /**
     * How the strip distributes width. Initial value is
     * {@link Variant#STANDARD}; {@code null} falls back to STANDARD.
     *
     * @return the variant property
     */
    public final ObjectProperty<Variant> variantProperty() {
        return variant;
    }

    /**
     * Returns the variant.
     *
     * @return the variant, or {@code null}
     */
    public final Variant getVariant() {
        return variant.get();
    }

    /**
     * Sets the variant.
     *
     * @param value the variant, or {@code null} for the default
     */
    public final void setVariant(Variant value) {
        variant.set(value);
    }

    // ==================== Scroll Button Policy ====================

    private final ObjectProperty<ScrollButtonPolicy> scrollButtonPolicy =
            new SimpleObjectProperty<>(this, "scrollButtonPolicy", DEFAULT_SCROLL_BUTTON_POLICY);

    /**
     * When scroll buttons appear in the {@link Variant#SCROLLABLE} variant.
     * Initial value is {@link ScrollButtonPolicy#AUTO}; {@code null} falls back
     * to AUTO.
     *
     * @return the scroll-button-policy property
     */
    public final ObjectProperty<ScrollButtonPolicy> scrollButtonPolicyProperty() {
        return scrollButtonPolicy;
    }

    /**
     * Returns the scroll-button policy.
     *
     * @return the policy, or {@code null}
     */
    public final ScrollButtonPolicy getScrollButtonPolicy() {
        return scrollButtonPolicy.get();
    }

    /**
     * Sets the scroll-button policy.
     *
     * @param value the policy, or {@code null} for the default
     */
    public final void setScrollButtonPolicy(ScrollButtonPolicy value) {
        scrollButtonPolicy.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether tab-pane motions animate: the sliding underline indicator, the content
     * transition (when a {@code contentAnimation} is set), and, in the {@code SCROLLABLE}
     * variant, the tab-strip scroll (momentum wheel and button glide). When {@code false}
     * each new motion is applied instantly. A tab-strip scroll already in flight when the
     * flag is cleared is halted (it would otherwise coast); an indicator slide or content
     * transition already in flight runs to completion, each settling to its own end state.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether tab-pane motions animate.
     *
     * @return {@code true} if animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether tab-pane motions animate.
     *
     * @param value {@code true} to animate
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
                    return RXTabPane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of the tab-pane motions (indicator slide, content transition, and
     * {@code SCROLLABLE} strip scroll). {@code null}, {@code Duration.ZERO}, negative or
     * non-finite values fall back to an immediate snap.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Selection Follows Focus ====================

    private final BooleanProperty selectionFollowsFocus =
            new SimpleBooleanProperty(this, "selectionFollowsFocus", true);

    /**
     * Whether moving the keyboard focus also selects the focused tab. {@code true}
     * (default) is automatic activation; {@code false} is manual activation
     * (arrow keys only move focus, {@code Space}/{@code Enter} selects).
     *
     * @return the selection-follows-focus property
     */
    public final BooleanProperty selectionFollowsFocusProperty() {
        return selectionFollowsFocus;
    }

    /**
     * Returns whether selection follows focus.
     *
     * @return {@code true} if selection follows focus
     */
    public final boolean isSelectionFollowsFocus() {
        return selectionFollowsFocus.get();
    }

    /**
     * Sets whether selection follows focus.
     *
     * @param value {@code true} for automatic activation
     */
    public final void setSelectionFollowsFocus(boolean value) {
        selectionFollowsFocus.set(value);
    }

    // ==================== Tab Min Width ====================

    private final DoubleProperty tabMinWidth = new StyleableDoubleProperty(DEFAULT_TAB_MIN_WIDTH) {
        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.TAB_MIN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTabPane.this;
        }

        @Override
        public String getName() {
            return "tabMinWidth";
        }
    };

    /**
     * Minimum width of each tab header, in pixels. Initial value is {@code 0}.
     *
     * @return the tab-min-width property
     */
    public final DoubleProperty tabMinWidthProperty() {
        return tabMinWidth;
    }

    /**
     * Returns the tab minimum width.
     *
     * @return the tab minimum width
     */
    public final double getTabMinWidth() {
        return tabMinWidth.get();
    }

    /**
     * Sets the tab minimum width.
     *
     * @param value the tab minimum width
     */
    public final void setTabMinWidth(double value) {
        tabMinWidth.set(value);
    }

    // ==================== Tab Max Width ====================

    private final DoubleProperty tabMaxWidth = new StyleableDoubleProperty(DEFAULT_TAB_MAX_WIDTH) {
        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.TAB_MAX_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTabPane.this;
        }

        @Override
        public String getName() {
            return "tabMaxWidth";
        }
    };

    /**
     * Maximum width of each tab header, in pixels. Initial value is
     * {@link Double#MAX_VALUE} (unbounded).
     *
     * @return the tab-max-width property
     */
    public final DoubleProperty tabMaxWidthProperty() {
        return tabMaxWidth;
    }

    /**
     * Returns the tab maximum width.
     *
     * @return the tab maximum width
     */
    public final double getTabMaxWidth() {
        return tabMaxWidth.get();
    }

    /**
     * Sets the tab maximum width.
     *
     * @param value the tab maximum width
     */
    public final void setTabMaxWidth(double value) {
        tabMaxWidth.set(value);
    }

    // ==================== Tab Closing Policy ====================

    private final ObjectProperty<TabClosingPolicy> tabClosingPolicy =
            new SimpleObjectProperty<>(this, "tabClosingPolicy", DEFAULT_TAB_CLOSING_POLICY);

    /**
     * Which tabs may show a close affordance. Initial value is
     * {@link TabClosingPolicy#UNAVAILABLE} (Material convention: no close button
     * unless explicitly enabled); {@code null} falls back to UNAVAILABLE.
     *
     * @return the tab-closing-policy property
     */
    public final ObjectProperty<TabClosingPolicy> tabClosingPolicyProperty() {
        return tabClosingPolicy;
    }

    /**
     * Returns the tab-closing policy.
     *
     * @return the policy, or {@code null}
     */
    public final TabClosingPolicy getTabClosingPolicy() {
        return tabClosingPolicy.get();
    }

    /**
     * Sets the tab-closing policy.
     *
     * @param value the policy, or {@code null} for the default
     */
    public final void setTabClosingPolicy(TabClosingPolicy value) {
        tabClosingPolicy.set(value);
    }

    // ==================== Tab Alignment ====================

    private final ObjectProperty<TabAlignment> tabAlignment =
            new SimpleObjectProperty<>(this, "tabAlignment", DEFAULT_TAB_ALIGNMENT);

    /**
     * Alignment of the tab group within the header when the tabs do not fill it
     * (STANDARD variant; ignored when SCROLLABLE). Initial value is
     * {@link TabAlignment#START}; {@code null} falls back to START.
     *
     * @return the tab-alignment property
     */
    public final ObjectProperty<TabAlignment> tabAlignmentProperty() {
        return tabAlignment;
    }

    /**
     * Returns the tab alignment.
     *
     * @return the alignment, or {@code null}
     */
    public final TabAlignment getTabAlignment() {
        return tabAlignment.get();
    }

    /**
     * Sets the tab alignment.
     *
     * @param value the alignment, or {@code null} for the default
     */
    public final void setTabAlignment(TabAlignment value) {
        tabAlignment.set(value);
    }

    // ==================== Content Animation ====================

    private final ObjectProperty<PageAnimation> contentAnimation =
            new SimpleObjectProperty<>(this, "contentAnimation");

    /**
     * Transition played when the displayed content changes. When {@code null}
     * (the default) the content swaps instantly; otherwise the given
     * {@link PageAnimation} is played (direction inferred from the selection delta).
     *
     * @return the content-animation property
     */
    public final ObjectProperty<PageAnimation> contentAnimationProperty() {
        return contentAnimation;
    }

    /**
     * Returns the content-change animation.
     *
     * @return the animation, or {@code null} for an instant swap
     */
    public final PageAnimation getContentAnimation() {
        return contentAnimation.get();
    }

    /**
     * Sets the content-change animation.
     *
     * @param value the animation, or {@code null} for an instant swap
     */
    public final void setContentAnimation(PageAnimation value) {
        contentAnimation.set(value);
    }

    // ==================== Preserve Content ====================

    private final BooleanProperty preserveContent =
            new SimpleBooleanProperty(this, "preserveContent", DEFAULT_PRESERVE_CONTENT);

    /**
     * Whether every tab's content stays attached to the scene graph. When
     * {@code false} (the default) only the selected tab's content is attached and
     * the pane's preferred size follows the current page; when {@code true} all
     * content stays attached (non-selected hidden) and the preferred size is the
     * maximum across all tabs, so switching does not resize the pane.
     *
     * @return the preserve-content property
     */
    public final BooleanProperty preserveContentProperty() {
        return preserveContent;
    }

    /**
     * Returns whether all tab content is kept attached.
     *
     * @return {@code true} to keep all content attached
     */
    public final boolean isPreserveContent() {
        return preserveContent.get();
    }

    /**
     * Sets whether all tab content is kept attached.
     *
     * @param value {@code true} to keep all content attached
     */
    public final void setPreserveContent(boolean value) {
        preserveContent.set(value);
    }

    // ==================== Dynamic Height ====================

    private final BooleanProperty dynamicHeight =
            new SimpleBooleanProperty(this, "dynamicHeight", DEFAULT_DYNAMIC_HEIGHT);

    /**
     * Whether the content area resizes to the selected page (only meaningful when
     * {@link #preserveContentProperty() preserveContent} is {@code true}). When
     * {@code false} (the default) the content area is locked to the maximum page
     * size so switching does not jump; when {@code true} it follows the selected
     * page. With the default detached content the height already follows the
     * selection, so this has no additional effect.
     *
     * @return the dynamic-height property
     */
    public final BooleanProperty dynamicHeightProperty() {
        return dynamicHeight;
    }

    /**
     * Returns whether the content area resizes to the selected page.
     *
     * @return {@code true} if the content height follows the selection
     */
    public final boolean isDynamicHeight() {
        return dynamicHeight.get();
    }

    /**
     * Sets whether the content area resizes to the selected page.
     *
     * @param value {@code true} to follow the selection
     */
    public final void setDynamicHeight(boolean value) {
        dynamicHeight.set(value);
    }

    // ==================== Wheel Scroll Enabled ====================

    private final BooleanProperty wheelScrollEnabled =
            new SimpleBooleanProperty(this, "wheelScrollEnabled", DEFAULT_WHEEL_SCROLL_ENABLED);

    /**
     * Whether wheel / trackpad gestures scroll the header in the
     * {@link Variant#SCROLLABLE} variant. Initial value is {@code true}; setting it
     * {@code false} disables only the wheel path (the scroll buttons and
     * programmatic scrolling still work).
     *
     * @return the wheel-scroll-enabled property
     */
    public final BooleanProperty wheelScrollEnabledProperty() {
        return wheelScrollEnabled;
    }

    /**
     * Returns whether wheel scrolling is enabled.
     *
     * @return {@code true} if the wheel scrolls the header
     */
    public final boolean isWheelScrollEnabled() {
        return wheelScrollEnabled.get();
    }

    /**
     * Sets whether wheel scrolling is enabled.
     *
     * @param value {@code true} to let the wheel scroll the header
     */
    public final void setWheelScrollEnabled(boolean value) {
        wheelScrollEnabled.set(value);
    }

    // ==================== Selection coordination ====================

    private final InvalidationListener modelIndexListener = observable -> {
        SingleSelectionModel<RXTab> model = getSelectionModel();
        selectedIndex.set(model == null ? -1 : model.getSelectedIndex());
    };

    private final ChangeListener<RXTab> modelItemListener = (observable, oldTab, newTab) -> {
        // Flip RXTab.selected before publishing the projection so any selectedItem
        // listener (content swap, close-button visibility) observes consistent flags.
        syncSelectedFlag(oldTab, newTab);
        selectedItem.set(newTab);
    };

    private void onSelectionModelChanged(SingleSelectionModel<RXTab> oldModel,
                                         SingleSelectionModel<RXTab> newModel) {
        if (oldModel != null) {
            oldModel.selectedIndexProperty().removeListener(modelIndexListener);
            oldModel.selectedItemProperty().removeListener(modelItemListener);
        }
        RXTab previouslySelected = selectedItem.get();
        if (newModel != null) {
            newModel.selectedIndexProperty().addListener(modelIndexListener);
            newModel.selectedItemProperty().addListener(modelItemListener);
        }
        RXTab newSelected = newModel == null ? null : newModel.getSelectedItem();
        selectedIndex.set(newModel == null ? -1 : newModel.getSelectedIndex());
        // Re-drive RXTab.selected onto the new model's selection (the old model's
        // selected tab, if different, is cleared) before publishing the projection.
        syncSelectedFlag(previouslySelected, newSelected);
        selectedItem.set(newSelected);
    }

    private void syncSelectedFlag(RXTab oldTab, RXTab newTab) {
        if (oldTab == newTab) {
            return;
        }
        if (oldTab != null) {
            oldTab.setSelected(false);
        }
        if (newTab != null) {
            newTab.setSelected(true);
        }
    }

    /**
     * Maintains the {@code RXTab.tabPane} back-pointer and the selection
     * invariants after a structural tabs change. Mirrors native {@code TabPane}:
     * recover the selection to the nearest available tab when the selected tab is
     * removed, resync the index after add/remove, and default a pane with enabled
     * tabs to a selection (catch-all).
     */
    private void onTabsChanged(ListChangeListener.Change<? extends RXTab> change) {
        SingleSelectionModel<RXTab> model = getSelectionModel();
        while (change.next()) {
            for (RXTab tab : change.getRemoved()) {
                // !contains guard: a permutation remove+add or a duplicate tab
                // keeps the tab in the list, so its back-pointer must survive.
                if (tab != null && !getTabs().contains(tab)) {
                    tab.setTabPane(null);
                    if (model != null && tab.isSelected()) {
                        int recovered = findNearestAvailableTab(change.getFrom());
                        if (recovered >= 0) {
                            model.select(recovered);
                        } else {
                            model.clearSelection();
                        }
                    }
                }
            }
            for (RXTab tab : change.getAddedSubList()) {
                if (tab != null) {
                    tab.setTabPane(this);
                }
            }
            RXTab selected = model == null ? null : model.getSelectedItem();
            if (selected != null && (change.wasAdded() || change.wasRemoved() || change.wasPermutated())) {
                // Re-sync the index to the (possibly shifted or reordered) selected
                // item; a pure permutation keeps the item but moves its index. Guarded
                // on a non-null selection so an empty selection is left to the removal
                // recovery / catch-all below (List.indexOf(null) would otherwise match a
                // stray null tab and drive a phantom selection).
                int itemIndex = getTabs().indexOf(selected);
                if (model.getSelectedIndex() != itemIndex) {
                    model.select(itemIndex);
                }
            }
        }
        if (model != null) {
            if (getTabs().isEmpty()) {
                model.clearSelection();
            } else if (model.getSelectedIndex() == -1 && model.getSelectedItem() == null) {
                // Source of "a pane with enabled tabs keeps a selection". Only fires
                // on a structural change, so it never fights an explicit
                // clearSelection(); all-disabled leaves it at -1.
                int first = findNearestAvailableTab(0);
                if (first >= 0) {
                    model.select(first);
                }
            }
        }
    }

    /**
     * Finds the nearest selectable (non-disabled) tab searching forward from
     * {@code fromIndex} first (APG: prefer the tab following a removed one), then
     * backward. Returns {@code -1} when no enabled tab exists.
     */
    private int findNearestAvailableTab(int fromIndex) {
        int count = getTabs().size();
        if (count == 0) {
            return -1;
        }
        int start = Math.max(0, Math.min(fromIndex, count));
        for (int i = start; i < count; i++) {
            if (isAvailable(getTabs().get(i))) {
                return i;
            }
        }
        for (int i = Math.min(start, count) - 1; i >= 0; i--) {
            if (isAvailable(getTabs().get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAvailable(RXTab tab) {
        return tab != null && !tab.isDisable();
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXTabPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXTabPane control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTabPane control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXTabPane, Number> TAB_MIN_WIDTH =
                new CssMetaData<>("-rx-tab-min-width",
                        SizeConverter.getInstance(), DEFAULT_TAB_MIN_WIDTH) {
                    @Override
                    public boolean isSettable(RXTabPane control) {
                        return !control.tabMinWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTabPane control) {
                        return (StyleableProperty<Number>) control.tabMinWidthProperty();
                    }
                };

        private static final CssMetaData<RXTabPane, Number> TAB_MAX_WIDTH =
                new CssMetaData<>("-rx-tab-max-width",
                        SizeConverter.getInstance(), DEFAULT_TAB_MAX_WIDTH) {
                    @Override
                    public boolean isSettable(RXTabPane control) {
                        return !control.tabMaxWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTabPane control) {
                        return (StyleableProperty<Number>) control.tabMaxWidthProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATION_DURATION);
            styleables.add(TAB_MIN_WIDTH);
            styleables.add(TAB_MAX_WIDTH);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
