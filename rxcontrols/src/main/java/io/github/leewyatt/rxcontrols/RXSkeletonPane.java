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
 * {@code getChildren().setAll(...)} switching.
 *
 * <p>Use {@link RXSkeletonLoader} units composed in a {@code VBox} / {@code
 * HBox} as the skeleton, then assign the corresponding real node as the
 * content. Toggling {@code loading} swaps which one is in the scene graph.
 *
 * <p><b>Detach / attach model.</b> The pane holds references to both
 * {@code skeleton} and {@code content}; only one is in the scene graph at a
 * time. On {@code loading} flips it detaches the outgoing node and attaches
 * the incoming one — no node is destroyed, so a refresh cycle
 * ({@code loading: true → false → true}) reuses the same skeleton instance
 * with zero reconstruction. When the skeleton is out of the scene graph its
 * internal shimmer animation auto-pauses via
 * {@link io.github.leewyatt.rxcontrols.utils.TreeShowingProperty TreeShowingProperty}.
 *
 * <p><b>Child-inference sizing.</b> The pane's {@code prefWidth} / {@code
 * prefHeight} fall back to the real content's preferred size when present, so
 * the wrapper's own layout bounds stay constant across the {@code loading}
 * flip — preventing the surrounding layout from jumping when the swap occurs.
 * If only the skeleton is present (e.g. content not yet wired) the skeleton
 * drives the size instead.
 *
 * <p><b>Equally stretchable.</b> {@code maxWidth} / {@code maxHeight} report
 * {@link Double#MAX_VALUE}, so the pane behaves correctly under
 * {@code HBox.setHgrow(..., Priority.ALWAYS)} or inside a {@link Region}-based
 * layout that wants to fill the available space.
 */
public class RXSkeletonPane extends Region {

    private static final String DEFAULT_STYLE_CLASS = "rx-skeleton-pane";

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
        // Listeners installed last so the three setters above only trigger
        // one invalidateChildren() pass — done explicitly here.
        wireListeners();
        invalidateChildren();
    }

    private void wireListeners() {
        skeleton.addListener((obs, oldV, newV) -> invalidateChildren());
        content.addListener((obs, oldV, newV) -> invalidateChildren());
        loading.addListener(obs -> invalidateChildren());
    }

    // ==================== Skeleton ====================

    private final ObjectProperty<Node> skeleton = new SimpleObjectProperty<>(this, "skeleton");

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

    public final Node getSkeleton() {
        return skeleton.get();
    }

    public final void setSkeleton(Node value) {
        skeleton.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

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

    public final Node getContent() {
        return content.get();
    }

    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Loading ====================

    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", true);

    /**
     * Whether the pane is currently in the loading state. When {@code true}
     * the {@link #skeletonProperty() skeleton} is in the scene graph; when
     * {@code false} the {@link #contentProperty() content} is. Defaults to
     * {@code true} — most callers want the placeholder visible before the
     * first data arrives.
     *
     * @return the loading property
     */
    public final BooleanProperty loadingProperty() {
        return loading;
    }

    public final boolean isLoading() {
        return loading.get();
    }

    public final void setLoading(boolean value) {
        loading.set(value);
    }

    // ==================== Child swap ====================

    private void invalidateChildren() {
        Node target = isLoading() ? getSkeleton() : getContent();
        if (target == null) {
            // Clear instead of leaving the previous target in place — a null
            // assignment must visually take effect, not silently no-op.
            if (!getChildren().isEmpty()) {
                getChildren().clear();
            }
            return;
        }
        // setAll detaches the previous target (returning it to a pre-attach
        // state from JFX's perspective) and attaches the new one. The detached
        // node's reference is still held by skeleton / content properties, so
        // a refresh cycle reuses the same instance.
        if (getChildren().size() == 1 && getChildren().get(0) == target) {
            return;
        }
        getChildren().setAll(target);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double w = Math.max(0.0, getWidth() - x - snappedRightInset());
        double h = Math.max(0.0, getHeight() - y - snappedBottomInset());
        for (Node child : getChildren()) {
            layoutInArea(child, x, y, w, h, 0.0, HPos.LEFT, VPos.TOP);
        }
    }

    @Override
    public Orientation getContentBias() {
        Node c = getContent();
        if (c != null) {
            return c.getContentBias();
        }
        Node sk = getSkeleton();
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
     * content is not yet supplied.
     */
    private double pickPrefWidth(double height) {
        Node c = getContent();
        if (c != null) {
            return c.prefWidth(height);
        }
        Node sk = getSkeleton();
        if (sk != null) {
            return sk.prefWidth(height);
        }
        return 0.0;
    }

    private double pickPrefHeight(double width) {
        Node c = getContent();
        if (c != null) {
            return c.prefHeight(width);
        }
        Node sk = getSkeleton();
        if (sk != null) {
            return sk.prefHeight(width);
        }
        return 0.0;
    }

    private double pickMinWidth(double height) {
        Node c = getContent();
        Node sk = getSkeleton();
        if (c != null) {
            double contentMin = c.minWidth(height);
            return sk == null ? contentMin : Math.max(contentMin, sk.minWidth(height));
        }
        if (sk != null) {
            return sk.minWidth(height);
        }
        return 0.0;
    }

    private double pickMinHeight(double width) {
        Node c = getContent();
        Node sk = getSkeleton();
        if (c != null) {
            double contentMin = c.minHeight(width);
            return sk == null ? contentMin : Math.max(contentMin, sk.minHeight(width));
        }
        if (sk != null) {
            return sk.minHeight(width);
        }
        return 0.0;
    }
}
