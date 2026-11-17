package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.SidebarVisibility;
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
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A permanent, in-layout navigation sidebar holding a tree of
 * {@link RXSidebarItem items}. It switches between
 * {@link SidebarMode#EXPANDED} (icon + text, groups unfold in place) and
 * {@link SidebarMode#MINI} (a single column of top-level icons), tracks one
 * {@link #selectedItemProperty() selectedItem}, and animates its width on mode
 * change. It is not a drawer: it never opens, closes, overlays, or pushes.
 *
 * <p>Items live in three lists: {@link #getTopItems() topItems} and
 * {@link #getBottomItems() bottomItems} are pinned, {@link #getItems() items}
 * scrolls between them. The pinned lists are laid out at their natural height,
 * so groups belong in {@code items}: a group unfolding in a pinned list takes
 * its height from the scrolling area.</p>
 *
 * <p>In {@code MINI} mode a top-level row shows only its graphic, so give each
 * top-level item one.</p>
 */
public class RXSidebar extends Control {

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
         * Icon only, narrow {@code miniWidth}. The text of a top-level item
         * without child items is shown as its tooltip; a group opens a flyout
         * holding its child items.
         */
        MINI
    }

    /**
     * What opens the flyout of a group while the sidebar rests in
     * {@link SidebarMode#MINI}.
     */
    public enum FlyoutTrigger {
        /**
         * Resting the pointer on the group opens its flyout; leaving the group
         * and its flyouts closes them. Clicking and the keyboard open it too.
         */
        HOVER,
        /** Only clicking the group or activating it from the keyboard opens its flyout. */
        CLICK
    }

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-sidebar";

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
     * Default flyout trigger, also the {@code null} fallback.
     */
    public static final FlyoutTrigger DEFAULT_FLYOUT_TRIGGER = FlyoutTrigger.HOVER;
    /**
     * Default whether mode transitions and group expansion animate.
     */
    private static final boolean DEFAULT_ANIMATED = true;
    /**
     * Default duration of a mode transition and of a full group expansion.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    /**
     * Default interpolator of the transitions, also the null fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;
    /**
     * Default indent added per nesting level.
     */
    public static final double DEFAULT_INDENT = 24.0;

    private static final PseudoClass EXPANDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass MINI_PSEUDO_CLASS = PseudoClass.getPseudoClass("mini");

    // ==================== Constructor ====================

    /**
     * Creates an empty, expanded sidebar with default settings.
     */
    public RXSidebar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Seeds the initial value of getInitialFocusTraversable; a plain setter
        // would count as a user write and lock CSS out.
        // The WritableValue cast is not decoration: it pins the type argument so
        // the StyleableProperty cast is provably checked rather than unchecked.
        ((StyleableProperty<Boolean>) (WritableValue<Boolean>) focusTraversableProperty())
                .applyStyle(null, Boolean.FALSE);
        updateModePseudoClass();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Item Lists ====================

    private final ObservableList<RXSidebarItemBase> topItems = new SidebarItemList(this);
    private final ObservableList<RXSidebarItemBase> items = new SidebarItemList(this);
    private final ObservableList<RXSidebarItemBase> bottomItems = new SidebarItemList(this);

    /**
     * @return the live list of pinned-top items; see {@link RXSidebarItemBase} for the rules an added entry must meet
     */
    public final ObservableList<RXSidebarItemBase> getTopItems() {
        return topItems;
    }

    /**
     * @return the live list of main (scrollable) items; see {@link RXSidebarItemBase} for the rules an added entry must meet
     */
    public final ObservableList<RXSidebarItemBase> getItems() {
        return items;
    }

    /**
     * @return the live list of pinned-bottom items; see {@link RXSidebarItemBase} for the rules an added entry must meet
     */
    public final ObservableList<RXSidebarItemBase> getBottomItems() {
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

    private final ReadOnlyObjectWrapper<RXSidebarItem> selectedItem =
            new ReadOnlyObjectWrapper<>(this, "selectedItem") {
                @Override
                protected void invalidated() {
                    onSelectedItemInvalidated();
                }
            };

    // The item whose selected flag this sidebar has set. Differs from
    // selectedItem while the selection is an item that is not in the tree yet.
    private RXSidebarItem flaggedItem;

    /**
     * The selected item, or {@code null} when nothing is selected. Only a page
     * — a selectable item without child items — can be the selection, and at
     * most one across all three lists.
     *
     * <p>Read-only: the sidebar owns this state, because a user click must be
     * able to write it. Drive it with {@link #selectItem(RXSidebarItem)} and
     * {@link #clearSelection()}, and observe it with a listener. To keep the
     * user on the current page, select it again from the listener: the item
     * that was turned down then does not fire its
     * {@link RXSidebarItem#onActionProperty() onAction}.</p>
     *
     * <p>The selection is cleared when the selected item leaves the tree, stops
     * being selectable, or is given child items. An item selected before it is
     * in the tree is not watched until it is added to this sidebar: it is
     * checked at that point, and until then it stays the selection whatever
     * becomes of it, including being added to another sidebar.</p>
     *
     * @return the read-only selected-item property
     */
    public final ReadOnlyObjectProperty<RXSidebarItem> selectedItemProperty() {
        return selectedItem.getReadOnlyProperty();
    }

    /**
     * @return the selected item, or {@code null}
     */
    public final RXSidebarItem getSelectedItem() {
        return selectedItem.get();
    }

    /**
     * Selects the given page, expands the groups it is nested in (those whose
     * {@code expanded} is not bound), and scrolls it into view. Selecting the
     * item that is already selected does nothing.
     *
     * <p>An item that is not in the tree yet is accepted: it becomes the
     * selection, and its {@code selected} flag is set once it is added. This
     * supports selecting before populating.</p>
     *
     * @param item the item to select, or {@code null} to clear the selection
     * @throws IllegalArgumentException if the item cannot be selected: a group,
     *                                  an action, or an item that is in another
     *                                  sidebar
     */
    public final void selectItem(RXSidebarItem item) {
        if (item != null && !item.canBeSelected()) {
            throw new IllegalArgumentException(
                    "only a selectable item without child items can be selected");
        }
        if (item != null && item.getSidebar() != null && item.getSidebar() != this) {
            throw new IllegalArgumentException("the item is in another sidebar");
        }
        selectedItem.set(item);
    }

    /**
     * Clears the selection, leaving no item selected.
     */
    public final void clearSelection() {
        selectedItem.set(null);
    }

    private void onSelectedItemInvalidated() {
        RXSidebarItem current = getSelectedItem();
        if (flaggedItem != null && flaggedItem != current) {
            RXSidebarItem previous = flaggedItem;
            flaggedItem = null;
            previous.setSelected(false);
            // A listener on the flag may have selected something else, and that
            // call has already settled the flags.
            if (getSelectedItem() != current) {
                return;
            }
        }
        if (current != null && current.getSidebar() == this) {
            flag(current);
        }
    }

    private void flag(RXSidebarItem item) {
        flaggedItem = item;
        item.setSelected(true);
        if (flaggedItem != item) {
            // A listener on the flag moved the selection on.
            return;
        }
        for (RXSidebarItem group = item.getParent(); group != null; group = group.getParent()) {
            if (!group.expandedProperty().isBound()) {
                group.setExpanded(true);
            }
        }
    }

    // Called after the tree changed and after an item's ability to be selected
    // changed: drops a selection that left the tree or can no longer be
    // selected, and adopts one that was selected before it was added.
    final void revalidateSelection() {
        RXSidebarItem current = getSelectedItem();
        if (current == null) {
            return;
        }
        if (current.getSidebar() == this) {
            if (!current.canBeSelected()) {
                selectedItem.set(null);
            } else if (flaggedItem != current) {
                flag(current);
            }
        } else if (flaggedItem == current) {
            selectedItem.set(null);
        }
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
     * The width in {@link SidebarMode#EXPANDED}, {@value #DEFAULT_EXPANDED_WIDTH}
     * by default. A value that is not finite or is smaller than the mini width
     * is not used: the sidebar then takes the default, or the mini width when
     * that is larger.
     *
     * @return the expanded-width property
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
     * The width in {@link SidebarMode#MINI}, {@value #DEFAULT_MINI_WIDTH} by
     * default. A negative or non-finite value is not used: the sidebar then
     * takes the default.
     *
     * @return the mini-width property
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

    // ==================== Flyout Trigger ====================

    private final ObjectProperty<FlyoutTrigger> flyoutTrigger =
            new SimpleObjectProperty<>(this, "flyoutTrigger", DEFAULT_FLYOUT_TRIGGER);

    /**
     * What opens a group's flyout in {@link SidebarMode#MINI}, where the child
     * items of a group are reached through flyouts that open level by level
     * beside the sidebar. {@code null} is treated as
     * {@link #DEFAULT_FLYOUT_TRIGGER}.
     *
     * @return the flyout trigger property
     */
    public final ObjectProperty<FlyoutTrigger> flyoutTriggerProperty() {
        return flyoutTrigger;
    }

    /**
     * @return the flyout trigger, possibly {@code null}
     */
    public final FlyoutTrigger getFlyoutTrigger() {
        return flyoutTrigger.get();
    }

    /**
     * @param value the flyout trigger
     */
    public final void setFlyoutTrigger(FlyoutTrigger value) {
        flyoutTrigger.set(value);
    }

    // ==================== Filter ====================

    private final ObjectProperty<Predicate<? super RXSidebarItem>> filter =
            new SimpleObjectProperty<>(this, "filter");

    /**
     * Narrows the three lists to the items that match. An item is shown when
     * it matches, when an item below it matches, or when an item above it
     * matches, so a matching group shows all of its child items. Captions and
     * separators are not asked: a caption is shown while an item between it and
     * the next caption is, and separators are hidden. An item whose
     * {@code visible} is false is never shown and never asked.
     *
     * <p>While a filter is set every group with shown child items is rendered
     * open whatever its {@code expanded} says, and the user cannot expand or
     * collapse groups. Writes to {@code expanded} still land on the property
     * and show once the filter is cleared. The selection is not affected by
     * filtering.</p>
     *
     * <p>The filter is evaluated when this property, the structure of the tree
     * or an item's {@code visible} changes. After changing an item's text or
     * keywords, set a new predicate; setting the same instance again changes
     * nothing. The predicate must not throw and must not modify the tree.
     * {@code null}, the default, shows everything.</p>
     *
     * @return the filter property
     * @see #textFilter(String)
     */
    public final ObjectProperty<Predicate<? super RXSidebarItem>> filterProperty() {
        return filter;
    }

    /**
     * @return the filter, or {@code null}
     */
    public final Predicate<? super RXSidebarItem> getFilter() {
        return filter.get();
    }

    /**
     * @param value the filter, or {@code null} to show everything
     */
    public final void setFilter(Predicate<? super RXSidebarItem> value) {
        filter.set(value);
    }

    /**
     * Builds the stock filter: an item matches when its text or one of its
     * keywords contains the query, ignoring case and the white space around
     * the query.
     *
     * @param query the search text
     * @return the filter, or {@code null} when the query is {@code null} or blank
     */
    public static Predicate<RXSidebarItem> textFilter(String query) {
        return SidebarVisibility.textFilter(query);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * The node shown in the scrolling area when the filter leaves nothing of
     * {@link #getItems() items} on display. It is not shown while the sidebar
     * rests in {@link SidebarMode#MINI}, which is too narrow for it.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * @return the placeholder node, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * @param value the placeholder node, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
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
     * Whether a mode change animates the width and expanding or collapsing a
     * group animates its height; {@code true} by default. Styleable as
     * {@code -rx-animated}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * @return whether the transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * @param value whether the transitions animate
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
     * The duration of a mode change and of a group expanding or collapsing in
     * full, 200 ms by default. {@code null} or a non-positive duration disables
     * the animation. Styleable as {@code -rx-animation-duration}.
     *
     * @return the animation-duration property
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

    // ==================== Indent ====================

    private final DoubleProperty indent = new StyleableDoubleProperty(DEFAULT_INDENT) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.INDENT;
        }

        @Override
        public Object getBean() {
            return RXSidebar.this;
        }

        @Override
        public String getName() {
            return "indent";
        }
    };

    /**
     * The distance each nesting level is indented by. A negative or non-finite
     * value is treated as {@link #DEFAULT_INDENT}. Styleable as
     * {@code -rx-indent}.
     *
     * @return the indent property
     */
    public final DoubleProperty indentProperty() {
        return indent;
    }

    /**
     * @return the indent per nesting level
     */
    public final double getIndent() {
        return indent.get();
    }

    /**
     * @param value the indent per nesting level
     */
    public final void setIndent(double value) {
        indent.set(value);
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
        private static final CssMetaData<RXSidebar, Number> INDENT =
                new CssMetaData<>("-rx-indent", SizeConverter.getInstance(), DEFAULT_INDENT) {
                    @Override
                    public boolean isSettable(RXSidebar node) {
                        return !node.indent.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSidebar node) {
                        return (StyleableProperty<Number>) node.indentProperty();
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            styleables.add(INDENT);
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
