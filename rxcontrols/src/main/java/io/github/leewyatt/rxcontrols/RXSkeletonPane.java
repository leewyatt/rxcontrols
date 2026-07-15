package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Two-slot container that swaps between a {@linkplain #skeletonProperty()
 * skeleton} placeholder and the {@linkplain #contentProperty() real content}
 * based on {@link #loadingProperty() loading}. Convenience over hand-rolled
 * slot switching.
 *
 * <p>Use {@link RXSkeleton} units composed in a {@code VBox} / {@code
 * HBox} as the skeleton, then assign the corresponding real node as the
 * content. Toggling {@code loading} changes which slot is visible and laid out.
 *
 * <p><b>Attached-slot model.</b> The pane hosts non-null slots in internal
 * containers and keeps them attached while present. The inactive container is
 * invisible and mouse-transparent, which pauses tree-showing dependent visual
 * work while still allowing layout invalidations from the hidden slot to reach
 * this pane. The original slot node's visibility and mouse transparency are
 * not mutated.
 *
 * <p><b>Child-inference sizing.</b> The pane's {@code prefWidth} / {@code
 * prefHeight} fall back to the managed real content's preferred size when
 * present, so the pane's own layout bounds stay constant across the
 * {@code loading} flip. If no managed content is present, the managed skeleton
 * drives preferred size instead. Minimum size is the maximum of both managed
 * slots, so either state can fit without clipping.
 *
 * <p><b>Equally stretchable.</b> {@code maxWidth} / {@code maxHeight} report
 * {@link Double#MAX_VALUE}, so the pane behaves correctly under
 * {@code HBox.setHgrow(..., Priority.ALWAYS)} or inside a {@link Region}-based
 * layout that wants to fill the available space.
 */
public class RXSkeletonPane extends Region {

    private static final String DEFAULT_STYLE_CLASS = "rx-skeleton-pane";

    // ==================== Internal State ====================

    private final SlotPane skeletonSlot = new SlotPane();
    private final SlotPane contentSlot = new SlotPane();

    // ==================== Constructors ====================

    /**
     * Creates an empty pane with {@link #loadingProperty() loading} {@code true}.
     */
    public RXSkeletonPane() {
        this(null, null, true);
    }

    /**
     * Creates a pane preloaded with the given skeleton and content, starting
     * in the loading state.
     *
     * @param skeleton placeholder shown while loading; may be {@code null}
     * @param content  real content shown when loaded; may be {@code null}
     */
    public RXSkeletonPane(@NamedArg("skeleton") Node skeleton,
                          @NamedArg("content") Node content) {
        this(skeleton, content, true);
    }

    /**
     * Creates a pane with explicit initial slots and loading flag.
     *
     * @param skeleton placeholder; may be {@code null}
     * @param content  real content; may be {@code null}
     * @param loading  initial loading state
     */
    public RXSkeletonPane(@NamedArg("skeleton") Node skeleton,
                          @NamedArg("content") Node content,
                          @NamedArg(value = "loading", defaultValue = "true") boolean loading) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setSkeleton(skeleton);
        setContent(content);
        setLoading(loading);
    }

    // ==================== Skeleton ====================

    private final ObjectProperty<Node> skeleton = new SimpleObjectProperty<>(this, "skeleton") {
        @Override
        protected void invalidated() {
            syncSlots();
        }
    };

    /**
     * Placeholder node shown while {@link #loadingProperty() loading} is
     * {@code true}. May be {@code null} (no placeholder displayed even while
     * loading).
     *
     * @return the skeleton property
     */
    public final ObjectProperty<Node> skeletonProperty() {
        return skeleton;
    }

    /**
     * Gets the placeholder node shown while loading.
     *
     * @return the skeleton node, or {@code null}
     */
    public final Node getSkeleton() {
        return skeleton.get();
    }

    /**
     * Sets the placeholder node shown while loading.
     *
     * @param value the skeleton node, or {@code null}
     */
    public final void setSkeleton(Node value) {
        skeleton.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content") {
        @Override
        protected void invalidated() {
            syncSlots();
        }
    };

    /**
     * Real content shown when {@link #loadingProperty() loading} is
     * {@code false}. May be {@code null} (nothing shown after loading
     * finishes).
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Gets the real content node shown when loading is complete.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the real content node shown when loading is complete.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Loading ====================

    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", true) {
        @Override
        protected void invalidated() {
            syncSlots();
        }
    };

    /**
     * Whether the pane is currently in the loading state. When {@code true},
     * the {@link #skeletonProperty() skeleton} slot is active; when
     * {@code false}, the {@link #contentProperty() content} slot is active.
     * Defaults to {@code true} because most callers want the placeholder
     * visible before the first data arrives.
     *
     * @return the loading property
     */
    public final BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Returns whether the pane is showing the skeleton slot.
     *
     * @return {@code true} when loading
     */
    public final boolean isLoading() {
        return loading.get();
    }

    /**
     * Sets whether the pane shows the skeleton or content slot.
     *
     * @param value {@code true} to show the skeleton slot
     */
    public final void setLoading(boolean value) {
        loading.set(value);
    }

    // ==================== Slot Sync ====================

    private void syncSlots() {
        requestLayout();
        Node sk = getSkeleton();
        Node c = getContent();

        if (sk != null && sk == c) {
            contentSlot.setContent(null);
            skeletonSlot.setContent(sk);
        } else {
            if (skeletonSlot.getContent() == c) {
                skeletonSlot.setContent(null);
            }
            if (contentSlot.getContent() == sk) {
                contentSlot.setContent(null);
            }
            skeletonSlot.setContent(sk);
            contentSlot.setContent(c);
        }

        syncSlotChildren();
        syncSlotState();
    }

    private void syncSlotChildren() {
        boolean hasSkeleton = skeletonSlot.getContent() != null;
        boolean hasContent = contentSlot.getContent() != null;
        if (hasSkeleton && hasContent) {
            setChildrenIfNeeded(skeletonSlot, contentSlot);
        } else if (hasSkeleton) {
            setChildrenIfNeeded(skeletonSlot);
        } else if (hasContent) {
            setChildrenIfNeeded(contentSlot);
        } else if (!getChildren().isEmpty()) {
            getChildren().clear();
        }
    }

    private void setChildrenIfNeeded(Node... nodes) {
        if (childrenMatch(nodes)) {
            return;
        }
        getChildren().setAll(nodes);
    }

    private boolean childrenMatch(Node... nodes) {
        if (getChildren().size() != nodes.length) {
            return false;
        }
        for (int i = 0; i < nodes.length; i++) {
            if (getChildren().get(i) != nodes[i]) {
                return false;
            }
        }
        return true;
    }

    private void syncSlotState() {
        boolean sharedNode = getSkeleton() != null && getSkeleton() == getContent();
        boolean showSkeleton = sharedNode || isLoading() && skeletonSlot.getContent() != null;
        boolean showContent = !sharedNode && !isLoading() && contentSlot.getContent() != null;
        setSlotActive(skeletonSlot, showSkeleton);
        setSlotActive(contentSlot, showContent);
    }

    private static void setSlotActive(Node slot, boolean active) {
        slot.setVisible(active);
        slot.setMouseTransparent(!active);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double w = Math.max(0.0, getWidth() - x - snappedRightInset());
        double h = Math.max(0.0, getHeight() - y - snappedBottomInset());
        Node active = activeNode();
        if (active == null) {
            return;
        }

        SlotPane slot = activeSlot();
        if (active.isManaged()) {
            layoutInArea(slot, x, y, w, h, 0.0, HPos.LEFT, VPos.TOP);
        } else {
            layoutUnmanagedSlot(slot);
        }
    }

    /**
     * Returns the content-first measurement bias among managed slots.
     *
     * @return the delegated content bias, or {@code null}
     */
    @Override
    public Orientation getContentBias() {
        Node c = managedContent();
        if (c != null) {
            return c.getContentBias();
        }
        Node sk = managedSkeleton();
        if (sk != null) {
            return sk.getContentBias();
        }
        return null;
    }

    @Override
    protected double computeMinWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        return snappedLeftInset() + pickMinWidth(contentHeight) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        return snappedTopInset() + pickMinHeight(contentWidth) + snappedBottomInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        return snappedLeftInset() + pickPrefWidth(contentHeight) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        return snappedTopInset() + pickPrefHeight(contentWidth) + snappedBottomInset();
    }

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }

    /**
     * Prefers the real content's measurement so the pane's bounds do not
     * shift when {@code loading} flips. Falls back to the skeleton when the
     * content is not supplied or unmanaged.
     */
    private double pickPrefWidth(double height) {
        Node c = managedContent();
        if (c != null) {
            return childPrefWidth(c, height);
        }
        Node sk = managedSkeleton();
        if (sk != null) {
            return childPrefWidth(sk, height);
        }
        return 0.0;
    }

    private double pickPrefHeight(double width) {
        Node c = managedContent();
        if (c != null) {
            return childPrefHeight(c, width);
        }
        Node sk = managedSkeleton();
        if (sk != null) {
            return childPrefHeight(sk, width);
        }
        return 0.0;
    }

    private double pickMinWidth(double height) {
        return Math.max(measuredMinWidth(managedContent(), height),
                measuredMinWidth(managedSkeleton(), height));
    }

    private double pickMinHeight(double width) {
        return Math.max(measuredMinHeight(managedContent(), width),
                measuredMinHeight(managedSkeleton(), width));
    }

    private Node activeNode() {
        Node sk = getSkeleton();
        Node c = getContent();
        if (sk != null && sk == c) {
            return sk;
        }
        return isLoading() ? sk : c;
    }

    private SlotPane activeSlot() {
        Node sk = getSkeleton();
        Node c = getContent();
        if (sk != null && sk == c) {
            return skeletonSlot;
        }
        return isLoading() ? skeletonSlot : contentSlot;
    }

    private void layoutUnmanagedSlot(SlotPane slot) {
        double width = getWidth();
        double height = getHeight();
        if (!Double.isFinite(width) || width < 0.0) {
            width = 0.0;
        }
        if (!Double.isFinite(height) || height < 0.0) {
            height = 0.0;
        }
        slot.resizeRelocate(0.0, 0.0, width, height);
    }

    private Node managedSkeleton() {
        Node node = getSkeleton();
        return node == null || !node.isManaged() ? null : node;
    }

    private Node managedContent() {
        Node node = getContent();
        return node == null || !node.isManaged() ? null : node;
    }

    private double measuredMinWidth(Node node, double height) {
        return node == null ? 0.0 : childMinWidth(node, height);
    }

    private double measuredMinHeight(Node node, double width) {
        return node == null ? 0.0 : childMinHeight(node, width);
    }

    private double childMinWidth(Node node, double height) {
        double alt = -1.0;
        if (height != -1.0 && node.isResizable()
                && node.getContentBias() == Orientation.VERTICAL) {
            alt = snapSizeY(boundedSize(node.minHeight(-1.0), height, node.maxHeight(-1.0)));
        }
        return snapSizeX(node.minWidth(alt));
    }

    private double childMinHeight(Node node, double width) {
        double alt = -1.0;
        if (node.isResizable() && node.getContentBias() == Orientation.HORIZONTAL) {
            alt = snapSizeX(width == -1.0
                    ? node.maxWidth(-1.0)
                    : boundedSize(node.minWidth(-1.0), width, node.maxWidth(-1.0)));
        }
        return snapSizeY(node.minHeight(alt));
    }

    private double childPrefWidth(Node node, double height) {
        double alt = -1.0;
        if (height != -1.0 && node.isResizable()
                && node.getContentBias() == Orientation.VERTICAL) {
            alt = snapSizeY(boundedSize(node.minHeight(-1.0), height, node.maxHeight(-1.0)));
        }
        return snapSizeX(boundedSize(node.minWidth(alt), node.prefWidth(alt), node.maxWidth(alt)));
    }

    private double childPrefHeight(Node node, double width) {
        double alt = -1.0;
        if (node.isResizable() && node.getContentBias() == Orientation.HORIZONTAL) {
            alt = snapSizeX(boundedSize(node.minWidth(-1.0),
                    width == -1.0 ? node.prefWidth(-1.0) : width,
                    node.maxWidth(-1.0)));
        }
        return snapSizeY(boundedSize(node.minHeight(alt), node.prefHeight(alt), node.maxHeight(alt)));
    }

    private static double boundedSize(double min, double pref, double max) {
        double lowerBounded = pref >= min ? pref : min;
        double effectiveMax = min >= max ? min : max;
        return lowerBounded <= effectiveMax ? lowerBounded : effectiveMax;
    }

    // ==================== Slot Pane ====================

    private static final class SlotPane extends Region {

        private Node content;

        private Node getContent() {
            return content;
        }

        private void setContent(Node value) {
            if (content == value) {
                return;
            }
            content = value;
            if (value == null) {
                getChildren().clear();
            } else {
                getChildren().setAll(value);
            }
            requestLayout();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Orientation getContentBias() {
            Node node = managedContent();
            return node == null ? null : node.getContentBias();
        }

        @Override
        protected void layoutChildren() {
            Node node = managedContent();
            if (node == null) {
                return;
            }
            double w = Math.max(0.0, getWidth());
            double h = Math.max(0.0, getHeight());
            layoutInArea(node, 0.0, 0.0, w, h, 0.0, HPos.LEFT, VPos.TOP);
        }

        @Override
        protected double computeMaxWidth(double height) {
            return Double.MAX_VALUE;
        }

        @Override
        protected double computeMaxHeight(double width) {
            return Double.MAX_VALUE;
        }

        private Node managedContent() {
            Node node = getContent();
            return node == null || !node.isManaged() ? null : node;
        }
    }
}
