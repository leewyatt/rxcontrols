package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXAccordionSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ModifiableObservableListBase;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A vertical group of {@link RXTitledPane}s that coordinates their expansion.
 *
 * <p>Each pane's {@link RXTitledPane#expandedProperty() expanded} property stays the
 * single writable state; the accordion observes it and, in
 * {@link ExpandMode#SINGLE SINGLE} mode, collapses the other panes when one expands.
 * Expansion is not selection, and collapsing every pane is allowed.
 * {@link #getExpandedPanes()} is a read-only view of the expanded panes in pane
 * order. Coordination lives in the control, so it works without a skin and across
 * skin changes.</p>
 *
 * <pre>{@code
 * RXAccordion accordion = new RXAccordion(general, network, advanced);
 * accordion.setExpandMode(RXAccordion.ExpandMode.MULTIPLE);
 * }</pre>
 *
 * <p>Rules, all deterministic:</p>
 * <ul>
 *   <li>Switching to {@code SINGLE} keeps the first expanded pane in list order.</li>
 *   <li>Adding expanded panes in {@code SINGLE} mode keeps an expanded pane that was
 *       already in the group; if there was none, the first expanded pane in list
 *       order.</li>
 *   <li>A removed pane keeps its own state and becomes independent again; no other
 *       pane is expanded in its place. Hidden or disabled panes keep their state, and
 *       coordination may still collapse a disabled pane.</li>
 *   <li>Coordination never writes a pane whose {@code expanded} is bound one-way, so
 *       the single-expansion rule holds only for unbound panes.</li>
 *   <li>A pane's own {@code expanded} listeners may observe two expanded panes for
 *       the duration of one coordination; the rule holds once it has finished, and a
 *       newer expansion made from such a listener wins over the older one.</li>
 * </ul>
 *
 * <p>The list rejects {@code null} and duplicates; a bulk change that would violate
 * this leaves the list unchanged. Like a node joining a new parent, a pane added to
 * another accordion is removed from its previous one, so a discarded accordion never
 * keeps a pane.
 * Up / Down / Home / End move focus between the titles of the group's enabled,
 * visible panes. Panes are stacked at their preferred heights, so the accordion is as
 * tall as its content; wrap it in a {@code ScrollPane} to scroll.</p>
 */
@DefaultProperty("panes")
public class RXAccordion extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-accordion";

    // ==================== Constants ====================

    /**
     * Default expansion mode, also the {@code null} fallback.
     */
    public static final ExpandMode DEFAULT_EXPAND_MODE = ExpandMode.SINGLE;

    private static final double DEFAULT_EXPANDED_SPACING = 0.0;

    // ==================== Internal State ====================

    private final ObservableList<RXTitledPane> panes = new PaneList();

    private final ObservableList<RXTitledPane> expandedPanesModel = FXCollections.observableArrayList();

    private final ObservableList<RXTitledPane> expandedPanes =
            FXCollections.unmodifiableObservableList(expandedPanesModel);

    private final InvalidationListener expandedListener = this::onPaneExpandedInvalidated;

    private int generation;

    private int depth;

    // ==================== Constructors ====================

    /**
     * Creates an empty accordion.
     */
    @SuppressWarnings("unchecked")
    public RXAccordion() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Focus stops are the pane titles; the group itself is not one.
        ((StyleableProperty<Boolean>) (StyleableProperty<?>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        panes.addListener((ListChangeListener<RXTitledPane>) this::onPanesChanged);
    }

    /**
     * Creates an accordion with the given panes.
     *
     * @param panes the initial panes
     */
    public RXAccordion(RXTitledPane... panes) {
        this();
        getPanes().addAll(panes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAccordionSkin(this);
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
     *
     * <p>Always {@link Orientation#HORIZONTAL}: pane heights depend on the width.</p>
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== Panes ====================

    /**
     * The panes of the group, top to bottom.
     *
     * @return the modifiable pane list
     * @throws NullPointerException     on insertion of {@code null}
     * @throws IllegalArgumentException on insertion of a duplicate
     */
    public final ObservableList<RXTitledPane> getPanes() {
        return panes;
    }

    /**
     * Read-only view of the expanded panes, in pane order. It is updated once per
     * coordination, after the group has settled.
     *
     * @return the unmodifiable list of expanded panes
     */
    public final ObservableList<RXTitledPane> getExpandedPanes() {
        return expandedPanes;
    }

    // ==================== Expand Mode ====================

    private final ObjectProperty<ExpandMode> expandMode = new ObjectPropertyBase<>(DEFAULT_EXPAND_MODE) {
        @Override
        protected void invalidated() {
            if (isSingle()) {
                coordinate(() -> collapseOthers(firstExpanded(null)));
            }
        }

        @Override
        public Object getBean() {
            return RXAccordion.this;
        }

        @Override
        public String getName() {
            return "expandMode";
        }
    };

    /**
     * Whether at most one pane or any number of panes may be expanded. Switching to
     * {@link ExpandMode#SINGLE SINGLE} keeps the first expanded pane in list order.
     * {@code null} is treated as {@link #DEFAULT_EXPAND_MODE}.
     *
     * @return the expand mode property
     */
    public final ObjectProperty<ExpandMode> expandModeProperty() {
        return expandMode;
    }

    /**
     * Returns the expansion mode.
     *
     * @return the expand mode, possibly {@code null}
     */
    public final ExpandMode getExpandMode() {
        return expandMode.get();
    }

    /**
     * Sets the expansion mode.
     *
     * @param value the expand mode, or {@code null} for the default
     */
    public final void setExpandMode(ExpandMode value) {
        expandMode.set(value);
    }

    // ==================== Expanded Spacing ====================

    private final DoubleProperty expandedSpacing = new StyleableDoubleProperty(DEFAULT_EXPANDED_SPACING) {
        @Override
        protected void invalidated() {
            updatePanePositions();
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.EXPANDED_SPACING;
        }

        @Override
        public Object getBean() {
            return RXAccordion.this;
        }

        @Override
        public String getName() {
            return "expandedSpacing";
        }
    };

    /**
     * Gap opened above and below an expanded pane, so that it stands out as a separate
     * card (as in the Material Design accordion); collapsed neighbours stay joined by
     * a divider, and no gap is added at the ends of the group. The gap grows and
     * shrinks with the expand / collapse animation. Defaults to {@code 0}, which keeps
     * every pane joined in one card. Negative or non-finite values are treated as zero.
     *
     * <p>A pane that has a gap above or below it gets the {@code :gap-before} /
     * {@code :gap-after} pseudo-class, following the logical expanded state.</p>
     *
     * @return the expanded spacing property
     */
    public final DoubleProperty expandedSpacingProperty() {
        return expandedSpacing;
    }

    /**
     * Returns the gap opened around an expanded pane.
     *
     * @return the expanded spacing
     */
    public final double getExpandedSpacing() {
        return expandedSpacing.get();
    }

    /**
     * Sets the gap opened around an expanded pane.
     *
     * @param value the expanded spacing
     */
    public final void setExpandedSpacing(double value) {
        expandedSpacing.set(value);
    }

    // ==================== Expand / Collapse All ====================

    /**
     * Expands every pane in {@link ExpandMode#MULTIPLE MULTIPLE} mode. Does nothing in
     * {@link ExpandMode#SINGLE SINGLE} mode, where expanding every pane is not a valid
     * state. Panes whose {@code expanded} is bound are skipped.
     */
    public final void expandAll() {
        if (isSingle()) {
            return;
        }
        coordinate(() -> setAllExpanded(true));
    }

    /**
     * Collapses every pane, in either mode. Panes whose {@code expanded} is bound are
     * skipped.
     */
    public final void collapseAll() {
        coordinate(() -> setAllExpanded(false));
    }

    private void setAllExpanded(boolean value) {
        int current = ++generation;
        for (RXTitledPane pane : new ArrayList<>(panes)) {
            if (pane.getAccordion() == this && pane.isExpanded() != value && !pane.expandedProperty().isBound()) {
                pane.setExpanded(value);
                if (current != generation) {
                    return;
                }
            }
        }
    }

    // ==================== Coordination ====================

    private boolean isSingle() {
        return getExpandMode() != ExpandMode.MULTIPLE;
    }

    private void coordinate(Runnable action) {
        depth++;
        try {
            action.run();
        } finally {
            depth--;
        }
        if (depth == 0) {
            refreshExpandedPanes();
        }
    }

    private void onPaneExpandedInvalidated(Observable observable) {
        Object bean = ((ReadOnlyProperty<?>) observable).getBean();
        if (!(bean instanceof RXTitledPane)) {
            return;
        }
        RXTitledPane pane = (RXTitledPane) bean;
        if (pane.getAccordion() != this) {
            return;
        }
        coordinate(() -> {
            if (pane.isExpanded() && isSingle()) {
                collapseOthers(pane);
            }
        });
    }

    private void collapseOthers(RXTitledPane keep) {
        int current = ++generation;
        for (RXTitledPane pane : new ArrayList<>(panes)) {
            if (pane != keep && pane.getAccordion() == this && pane.isExpanded()
                    && !pane.expandedProperty().isBound()) {
                pane.setExpanded(false);
                if (current != generation) {
                    return;
                }
            }
        }
    }

    private RXTitledPane firstExpanded(Set<RXTitledPane> excluded) {
        for (RXTitledPane pane : panes) {
            if (pane.isExpanded() && (excluded == null || !excluded.contains(pane))) {
                return pane;
            }
        }
        return null;
    }

    private void refreshExpandedPanes() {
        List<RXTitledPane> current = new ArrayList<>();
        for (RXTitledPane pane : panes) {
            if (pane.isExpanded()) {
                current.add(pane);
            }
        }
        if (!current.equals(expandedPanesModel)) {
            expandedPanesModel.setAll(current);
        }
        updatePanePositions();
    }

    private void updatePanePositions() {
        double spacing = getExpandedSpacing();
        boolean spaced = Double.isFinite(spacing) && spacing > 0;
        int last = panes.size() - 1;
        for (int i = 0; i <= last; i++) {
            RXTitledPane pane = panes.get(i);
            boolean gapBefore = spaced && i > 0 && (pane.isExpanded() || panes.get(i - 1).isExpanded());
            boolean gapAfter = spaced && i < last && (pane.isExpanded() || panes.get(i + 1).isExpanded());
            pane.setPositionInAccordion(i == 0, i == last, gapBefore, gapAfter);
        }
    }

    private void onPanesChanged(ListChangeListener.Change<? extends RXTitledPane> change) {
        Set<RXTitledPane> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<RXTitledPane> added = Collections.newSetFromMap(new IdentityHashMap<>());
        while (change.next()) {
            removed.addAll(change.getRemoved());
            added.addAll(change.getAddedSubList());
        }
        for (RXTitledPane pane : removed) {
            if (!panes.contains(pane)) {
                pane.expandedProperty().removeListener(expandedListener);
                pane.setAccordion(null);
            }
        }
        // Panes re-added by the same change (setAll, sort) are already attached and do
        // not count as newcomers for the SINGLE-mode keep rule.
        Set<RXTitledPane> newcomers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RXTitledPane pane : added) {
            if (pane.getAccordion() != this) {
                RXAccordion previous = pane.getAccordion();
                if (previous != null) {
                    // Like a scene-graph parent, joining a group leaves the old one.
                    previous.getPanes().remove(pane);
                }
                pane.setAccordion(this);
                pane.expandedProperty().addListener(expandedListener);
                newcomers.add(pane);
            }
        }
        coordinate(() -> {
            if (isSingle()) {
                RXTitledPane keep = firstExpanded(newcomers);
                collapseOthers(keep != null ? keep : firstExpanded(null));
            }
        });
    }

    // ==================== Pane List ====================

    /**
     * Pane list that validates every insertion before mutating, so a rejected bulk
     * change leaves the list unchanged.
     */
    private final class PaneList extends ModifiableObservableListBase<RXTitledPane> {

        private final List<RXTitledPane> backing = new ArrayList<>();

        @Override
        public RXTitledPane get(int index) {
            return backing.get(index);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        protected void doAdd(int index, RXTitledPane element) {
            checkInsertable(element);
            if (backing.contains(element)) {
                throw new IllegalArgumentException("Duplicate pane: " + element);
            }
            backing.add(index, element);
        }

        @Override
        protected RXTitledPane doSet(int index, RXTitledPane element) {
            checkInsertable(element);
            int existing = backing.indexOf(element);
            if (existing >= 0 && existing != index) {
                throw new IllegalArgumentException("Duplicate pane: " + element);
            }
            return backing.set(index, element);
        }

        @Override
        protected RXTitledPane doRemove(int index) {
            return backing.remove(index);
        }

        @Override
        public boolean addAll(Collection<? extends RXTitledPane> elements) {
            checkBulk(elements, true);
            return super.addAll(elements);
        }

        @Override
        public boolean addAll(int index, Collection<? extends RXTitledPane> elements) {
            checkBulk(elements, true);
            return super.addAll(index, elements);
        }

        @Override
        public boolean setAll(Collection<? extends RXTitledPane> elements) {
            checkBulk(elements, false);
            return super.setAll(elements);
        }

        private void checkBulk(Collection<? extends RXTitledPane> elements, boolean appending) {
            Objects.requireNonNull(elements, "elements");
            Set<RXTitledPane> seen = new HashSet<>();
            for (RXTitledPane pane : elements) {
                checkInsertable(pane);
                if (!seen.add(pane) || (appending && backing.contains(pane))) {
                    throw new IllegalArgumentException("Duplicate pane: " + pane);
                }
            }
        }

        private void checkInsertable(RXTitledPane pane) {
            Objects.requireNonNull(pane, "pane");
        }
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXAccordion, Number> EXPANDED_SPACING =
                new CssMetaData<>("-rx-expanded-spacing", SizeConverter.getInstance(), DEFAULT_EXPANDED_SPACING) {
                    @Override
                    public boolean isSettable(RXAccordion node) {
                        return !node.expandedSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXAccordion node) {
                        return (StyleableProperty<Number>) node.expandedSpacingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(EXPANDED_SPACING);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * How many panes of an {@link RXAccordion} may be expanded at once.
     */
    public enum ExpandMode {

        /**
         * At most one pane; expanding a pane collapses the others. All panes may be
         * collapsed.
         */
        SINGLE,

        /**
         * Any number of panes.
         */
        MULTIPLE
    }
}
