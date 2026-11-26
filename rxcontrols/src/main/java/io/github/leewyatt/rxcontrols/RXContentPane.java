package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-content pane whose {@link #contentProperty() content} can be bound to
 * another node-valued property.
 *
 * <p>The pane owns one content slot. A managed content node drives content bias,
 * minimum size, preferred size and layout; an unmanaged content node remains
 * attached but is ignored by measurement and layout, matching JavaFX pane
 * semantics. Resizable content is sized to fill the padded area, clamped by its
 * own min / pref / max range, and then positioned by
 * {@link #alignmentProperty() alignment} when it does not fill the area.</p>
 */
@DefaultProperty("content")
public class RXContentPane extends Region {

    // ==================== Constants ====================

    /**
     * Default content alignment.
     */
    public static final Pos DEFAULT_ALIGNMENT = Pos.CENTER;

    private static final String DEFAULT_STYLE_CLASS = "rx-content-pane";

    // ==================== Internal State ====================

    private Node currentContent;
    private boolean updatingContent;

    // ==================== Constructors ====================

    /**
     * Creates an empty content pane.
     */
    public RXContentPane() {
        this(null);
    }

    /**
     * Creates a content pane with the given content.
     *
     * @param content the content node, or {@code null}
     */
    public RXContentPane(Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setContent(content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content =
            new SimpleObjectProperty<>(this, "content") {
                @Override
                protected void invalidated() {
                    updateContent();
                }
            };

    /**
     * Node displayed by this pane. May be {@code null}. A node that another
     * parent has taken is no longer measured or laid out by this pane, though
     * the property, which may be bound, still names it. A value set from a
     * listener that runs while the pane takes or releases its content is
     * applied once that change is complete, so the new node is not in the
     * pane yet when the call returns.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment =
            new StyleableObjectProperty<>(DEFAULT_ALIGNMENT) {
                @Override
                public void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Pos> getCssMetaData() {
                    return StyleableProperties.ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXContentPane.this;
                }

                @Override
                public String getName() {
                    return "alignment";
                }
            };

    /**
     * Alignment of the managed content within the pane's padded area. Initial
     * value is {@link #DEFAULT_ALIGNMENT}; {@code null} is treated as
     * {@link #DEFAULT_ALIGNMENT} at layout time.
     *
     * @return the alignment property
     */
    public final ObjectProperty<Pos> alignmentProperty() {
        return alignment;
    }

    /**
     * Returns the content alignment.
     *
     * @return the content alignment, or {@code null}
     */
    public final Pos getAlignment() {
        return alignment.get();
    }

    /**
     * Sets the content alignment.
     *
     * @param value the alignment, or {@code null} to use {@link #DEFAULT_ALIGNMENT}
     */
    public final void setAlignment(Pos value) {
        alignment.set(value);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        Node node = managedContent();
        return node == null ? null : node.getContentBias();
    }

    @Override
    protected double computeMinWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        Node node = managedContent();
        return snappedLeftInset() + (node == null ? 0.0 : childMinWidth(node, contentHeight)) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        Node node = managedContent();
        return snappedTopInset() + (node == null ? 0.0 : childMinHeight(node, contentWidth)) + snappedBottomInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        Node node = managedContent();
        return snappedLeftInset() + (node == null ? 0.0 : childPrefWidth(node, contentHeight)) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        Node node = managedContent();
        return snappedTopInset() + (node == null ? 0.0 : childPrefHeight(node, contentWidth)) + snappedBottomInset();
    }

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();

        Node node = managedContent();
        if (node == null) {
            return;
        }

        // Each axis on its own: a pane collapsed on one axis still gives the other.
        // The area may be negative when the padding is larger than the pane, which
        // keeps the content on the same point of the pane while it shrinks.
        double contentW = Double.isFinite(width) ? width - left - right : 0.0;
        double contentH = Double.isFinite(height) ? height - top - bottom : 0.0;
        Pos align = getAlignment();
        if (align == null) {
            align = DEFAULT_ALIGNMENT;
        }
        double baselineOffset = align.getVpos() == VPos.BASELINE
                ? areaBaselineOffset(node, width, contentH) : 0.0;
        layoutInArea(node, left, top, contentW, contentH, baselineOffset,
                align.getHpos(), align.getVpos());
    }

    // ==================== Helpers ====================

    // A listener may set the content again while the child list is changing. That call
    // returns at once, and the loop applies the newest value when the change it
    // interrupted is complete, so the child list is never changed from inside its own
    // change.
    private void updateContent() {
        if (updatingContent) {
            return;
        }
        updatingContent = true;
        try {
            for (Node next = getContent(); currentContent != next; next = getContent()) {
                currentContent = next;
                if (next == null) {
                    getChildren().clear();
                } else {
                    getChildren().setAll(next);
                }
                requestLayout();
            }
        } finally {
            updatingContent = false;
        }
    }

    // The field names the node this pane put into its child list, also while a listener
    // has already set the property again. A node that another parent has taken since,
    // or that the child list rejected, is not this pane's to measure or lay out.
    private Node managedContent() {
        Node node = currentContent;
        return node == null || node.getParent() != this || !node.isManaged() ? null : node;
    }

    // The baseline of the area for a single node, as a StackPane computes it: the node's
    // own baseline, or, for a resizable node that has none, the height the area gives it.
    private static double areaBaselineOffset(Node node, double width, double areaHeight) {
        double offset = node.getBaselineOffset();
        if (offset != BASELINE_OFFSET_SAME_AS_HEIGHT) {
            return Math.max(0.0, offset);
        }
        double alt = node.getContentBias() == Orientation.HORIZONTAL ? width : -1.0;
        return Math.max(0.0, boundedSize(node.minHeight(alt), areaHeight, node.maxHeight(alt)));
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

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXContentPane, Pos> ALIGNMENT =
                new CssMetaData<>("-fx-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXContentPane pane) {
                        return !pane.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXContentPane pane) {
                        return (StyleableProperty<Pos>) pane.alignmentProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(ALIGNMENT);
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
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
