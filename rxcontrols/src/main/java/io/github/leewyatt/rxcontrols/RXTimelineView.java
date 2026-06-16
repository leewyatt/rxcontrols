package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXTimelineItemEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTimelineViewSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders an ordered list of {@link RXTimelineItem}s as a vertical sequence of
 * dots, connector axis, and content. It visualizes <em>what happened and in
 * what order</em> — activity streams, order tracking, change logs, history —
 * not a measured time scale: adjacent items are spaced equally regardless of
 * how far apart their timestamps are.
 *
 * <p>This control is unrelated to {@code javafx.animation.Timeline}.
 *
 * <p>The control owns the observable item list and a few view-level knobs.
 * Layout, rendering, and the optional item-click interaction are handled by
 * {@link RXTimelineViewSkin}. Per-item visual configuration (title, type,
 * color, custom content) lives on {@link RXTimelineItem}.
 *
 * <p>Colors are styled purely through CSS looked-up colors
 * ({@code -rx-dot-fill}, {@code -rx-line-fill}); sizes ({@code -rx-dot-size},
 * {@code -rx-line-width}, {@code -rx-item-spacing}) are styleable properties the
 * skin reads and applies. The control does not embed a {@code ScrollPane}; wrap
 * it in {@code ScrollPane} with {@code setFitToWidth(true)} when scrolling is
 * needed.
 */
public class RXTimelineView extends Control {

    /**
     * Which side of the vertical timeline the axis column (dot and connector)
     * sits on.
     */
    public enum Position {
        /**
         * Axis on the leading side. In a vertical timeline this is the left
         * (content on the right); in a horizontal timeline this is the top
         * (content below). This is the default.
         */
        LEFT,
        /**
         * Axis on the trailing side. In a vertical timeline this is the right
         * (content on the left, right-aligned toward the axis); in a horizontal
         * timeline this is the bottom (content above).
         */
        RIGHT
    }

    // ==================== Constants ====================

    /**
     * Default style class for this control.
     */
    public static final String DEFAULT_STYLE_CLASS = "rx-timeline-view";

    /**
     * Default dot diameter in pixels.
     */
    public static final double DEFAULT_DOT_SIZE = 12.0;

    /**
     * Default connector line width in pixels.
     */
    public static final double DEFAULT_LINE_WIDTH = 2.0;

    /**
     * Default vertical spacing between items in pixels.
     */
    public static final double DEFAULT_ITEM_SPACING = 16.0;

    /**
     * Default horizontal spacing between the axis column and the content in pixels.
     */
    public static final double DEFAULT_AXIS_SPACING = 12.0;

    /**
     * Default display order.
     */
    public static final boolean DEFAULT_REVERSE = false;

    /**
     * Default axis position.
     */
    public static final Position DEFAULT_POSITION = Position.LEFT;

    /**
     * Default layout orientation.
     */
    public static final Orientation DEFAULT_ORIENTATION = Orientation.VERTICAL;

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // ==================== Items ====================

    private final ObservableList<RXTimelineItem> items = FXCollections.observableArrayList();

    // ==================== Reverse ====================

    private final BooleanProperty reverse =
            new SimpleBooleanProperty(this, "reverse", DEFAULT_REVERSE);

    /**
     * Whether to reverse the <em>display</em> order. This never sorts the model;
     * the list order remains authoritative. To order by time, sort
     * {@link #getItems()} yourself.
     *
     * @return the reverse property
     */
    public final BooleanProperty reverseProperty() {
        return reverse;
    }

    /**
     * Returns whether the display order is reversed.
     *
     * @return {@code true} if the display order is reversed
     */
    public final boolean isReverse() {
        return reverse.get();
    }

    /**
     * Sets whether the display order is reversed.
     *
     * @param value {@code true} to reverse the display order
     */
    public final void setReverse(boolean value) {
        reverse.set(value);
    }

    // ==================== Position ====================

    private final ObjectProperty<Position> position =
            new SimpleObjectProperty<>(this, "position", DEFAULT_POSITION);

    /**
     * Which side the axis column (dot and connector) sits on.
     * {@link Position#LEFT} (the default) places the axis on the left with
     * content on the right; {@link Position#RIGHT} mirrors it. {@code null} is
     * treated as {@link #DEFAULT_POSITION} by the skin.
     *
     * @return the position property
     */
    public final ObjectProperty<Position> positionProperty() {
        return position;
    }

    /**
     * Returns the axis position.
     *
     * @return the position, or {@code null}
     */
    public final Position getPosition() {
        return position.get();
    }

    /**
     * Sets the axis position.
     *
     * @param value the position, or {@code null} for the default
     */
    public final void setPosition(Position value) {
        position.set(value);
    }

    // ==================== Orientation ====================

    private final ObjectProperty<Orientation> orientation =
            new SimpleObjectProperty<>(this, "orientation", DEFAULT_ORIENTATION);

    /**
     * The layout orientation. {@link Orientation#VERTICAL} (the default) stacks
     * items top-to-bottom with a vertical axis; {@link Orientation#HORIZONTAL}
     * lays items left-to-right with a horizontal axis. {@code null} is treated as
     * {@link #DEFAULT_ORIENTATION} by the skin.
     *
     * <p>A horizontal timeline does not wrap item text by width (items take their
     * natural size); it suits short labels and is typically wrapped in a
     * horizontally-scrolling {@code ScrollPane}.
     *
     * @return the orientation property
     */
    public final ObjectProperty<Orientation> orientationProperty() {
        return orientation;
    }

    /**
     * Returns the layout orientation.
     *
     * @return the orientation, or {@code null}
     */
    public final Orientation getOrientation() {
        return orientation.get();
    }

    /**
     * Sets the layout orientation.
     *
     * @param value the orientation, or {@code null} for the default
     */
    public final void setOrientation(Orientation value) {
        orientation.set(value);
    }

    // ==================== Dot Size ====================

    private final DoubleProperty dotSize =
            new SimpleStyleableDoubleProperty(StyleableProperties.DOT_SIZE, this, "dotSize", DEFAULT_DOT_SIZE);

    /**
     * The dot diameter in pixels. Negative values are sanitized to {@code 0} by
     * the skin when applied.
     *
     * @return the dot size property
     */
    public final DoubleProperty dotSizeProperty() {
        return dotSize;
    }

    /**
     * Returns the dot diameter.
     *
     * @return the dot size
     */
    public final double getDotSize() {
        return dotSize.get();
    }

    /**
     * Sets the dot diameter.
     *
     * @param value the dot size
     */
    public final void setDotSize(double value) {
        dotSize.set(value);
    }

    // ==================== Line Width ====================

    private final DoubleProperty lineWidth =
            new SimpleStyleableDoubleProperty(StyleableProperties.LINE_WIDTH, this, "lineWidth", DEFAULT_LINE_WIDTH);

    /**
     * The connector line width in pixels. Negative values are sanitized to
     * {@code 0} by the skin when applied.
     *
     * @return the line width property
     */
    public final DoubleProperty lineWidthProperty() {
        return lineWidth;
    }

    /**
     * Returns the connector line width.
     *
     * @return the line width
     */
    public final double getLineWidth() {
        return lineWidth.get();
    }

    /**
     * Sets the connector line width.
     *
     * @param value the line width
     */
    public final void setLineWidth(double value) {
        lineWidth.set(value);
    }

    // ==================== Item Spacing ====================

    private final DoubleProperty itemSpacing =
            new SimpleStyleableDoubleProperty(StyleableProperties.ITEM_SPACING, this, "itemSpacing", DEFAULT_ITEM_SPACING);

    /**
     * The vertical spacing between items in pixels. Negative values are
     * sanitized to {@code 0} by the skin when applied.
     *
     * @return the item spacing property
     */
    public final DoubleProperty itemSpacingProperty() {
        return itemSpacing;
    }

    /**
     * Returns the vertical spacing between items.
     *
     * @return the item spacing
     */
    public final double getItemSpacing() {
        return itemSpacing.get();
    }

    /**
     * Sets the vertical spacing between items.
     *
     * @param value the item spacing
     */
    public final void setItemSpacing(double value) {
        itemSpacing.set(value);
    }

    // ==================== Axis Spacing ====================

    private final DoubleProperty axisSpacing =
            new SimpleStyleableDoubleProperty(StyleableProperties.AXIS_SPACING, this, "axisSpacing", DEFAULT_AXIS_SPACING);

    /**
     * The horizontal spacing between the axis column (dot and connector) and the
     * content column, in pixels. Negative values are sanitized to {@code 0} by
     * the skin when applied.
     *
     * @return the axis spacing property
     */
    public final DoubleProperty axisSpacingProperty() {
        return axisSpacing;
    }

    /**
     * Returns the horizontal spacing between the axis column and the content.
     *
     * @return the axis spacing
     */
    public final double getAxisSpacing() {
        return axisSpacing.get();
    }

    /**
     * Sets the horizontal spacing between the axis column and the content.
     *
     * @param value the axis spacing
     */
    public final void setAxisSpacing(double value) {
        axisSpacing.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder =
            new SimpleObjectProperty<>(this, "placeholder", null);

    /**
     * The node shown when the item list is empty. Defaults to {@code null},
     * meaning nothing is shown for an empty timeline.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the empty-state placeholder node.
     *
     * @return the placeholder node, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the empty-state placeholder node.
     *
     * @param value the placeholder node, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== On Item Clicked ====================

    private final ObjectProperty<EventHandler<RXTimelineItemEvent>> onItemClicked =
            new SimpleObjectProperty<>(this, "onItemClicked", null) {
                @Override
                protected void invalidated() {
                    setEventHandler(RXTimelineItemEvent.ITEM_CLICKED, get());
                }
            };

    /**
     * Convenience handler for item-click events. The control is purely
     * presentational until a handler is set.
     *
     * @return the item-click handler property
     */
    public final ObjectProperty<EventHandler<RXTimelineItemEvent>> onItemClickedProperty() {
        return onItemClicked;
    }

    /**
     * Returns the convenience handler for item-click events.
     *
     * @return the item-click handler, or {@code null}
     */
    public final EventHandler<RXTimelineItemEvent> getOnItemClicked() {
        return onItemClicked.get();
    }

    /**
     * Sets the convenience handler for item-click events.
     *
     * @param value the item-click handler, or {@code null}
     */
    public final void setOnItemClicked(EventHandler<RXTimelineItemEvent> value) {
        onItemClicked.set(value);
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty timeline view.
     */
    public RXTimelineView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        items.addListener((InvalidationListener) observable -> updateEmptyPseudoClass());
        updateEmptyPseudoClass();
    }

    /**
     * Creates a timeline view populated with the given items. {@code null}
     * elements are skipped.
     *
     * @param items the initial items
     */
    public RXTimelineView(RXTimelineItem... items) {
        this();
        if (items != null) {
            for (RXTimelineItem item : items) {
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
    }

    // ==================== Items API ====================

    /**
     * Returns the live, modifiable item list. The list is final and cannot be
     * replaced; mutate it in place.
     *
     * @return the item list
     */
    public final ObservableList<RXTimelineItem> getItems() {
        return items;
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTimelineViewSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * Content bias depends on orientation. A vertical timeline reports
     * {@link Orientation#HORIZONTAL} (wrapped item height depends on the
     * available width); a horizontal timeline reports {@code null} (items take
     * their natural size and do not wrap by width).
     *
     * @return the content bias, or {@code null} for a horizontal timeline
     */
    @Override
    public Orientation getContentBias() {
        return getOrientation() == Orientation.HORIZONTAL ? null : Orientation.HORIZONTAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * Returns the CSS metadata supported by this control.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    private void updateEmptyPseudoClass() {
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, items.isEmpty());
    }

    // ==================== Styleable Properties ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTimelineView, Number> DOT_SIZE =
                new CssMetaData<>("-rx-dot-size", SizeConverter.getInstance(), DEFAULT_DOT_SIZE) {

                    @Override
                    public boolean isSettable(RXTimelineView control) {
                        return !control.dotSize.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTimelineView control) {
                        return (StyleableProperty<Number>) control.dotSizeProperty();
                    }
                };

        private static final CssMetaData<RXTimelineView, Number> LINE_WIDTH =
                new CssMetaData<>("-rx-line-width", SizeConverter.getInstance(), DEFAULT_LINE_WIDTH) {

                    @Override
                    public boolean isSettable(RXTimelineView control) {
                        return !control.lineWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTimelineView control) {
                        return (StyleableProperty<Number>) control.lineWidthProperty();
                    }
                };

        private static final CssMetaData<RXTimelineView, Number> ITEM_SPACING =
                new CssMetaData<>("-rx-item-spacing", SizeConverter.getInstance(), DEFAULT_ITEM_SPACING) {

                    @Override
                    public boolean isSettable(RXTimelineView control) {
                        return !control.itemSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTimelineView control) {
                        return (StyleableProperty<Number>) control.itemSpacingProperty();
                    }
                };

        private static final CssMetaData<RXTimelineView, Number> AXIS_SPACING =
                new CssMetaData<>("-rx-axis-spacing", SizeConverter.getInstance(), DEFAULT_AXIS_SPACING) {

                    @Override
                    public boolean isSettable(RXTimelineView control) {
                        return !control.axisSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTimelineView control) {
                        return (StyleableProperty<Number>) control.axisSpacingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, DOT_SIZE, LINE_WIDTH, ITEM_SPACING, AXIS_SPACING);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
