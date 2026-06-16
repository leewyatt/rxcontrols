package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.TimelineItemType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.paint.Color;

/**
 * A single entry in an {@link RXTimelineView}.
 *
 * <p>This is a JavaFX property bean (not a record): each property is observable
 * so editing a single item re-renders only that item's node without rebuilding
 * the whole view. All getters and setters are pure pass-through.
 *
 * <p>Text properties default to the empty string so the skin can collapse an
 * empty line by testing {@code isEmpty()} rather than {@code null}. The
 * {@code content}, {@code dotGraphic}, {@code type}, and {@code dotFill}
 * properties default to {@code null}, which carries a fall-through meaning
 * (no custom content, no icon, no semantic level, no per-item color override).
 *
 * <p><b>Single-occupancy contract:</b> {@link #contentProperty() content} and
 * {@link #dotGraphicProperty() dotGraphic} are live scene-graph nodes, and a
 * JavaFX node may have only one parent. An {@code RXTimelineItem} instance (and
 * the nodes it carries) must therefore appear at most once in a single
 * {@link RXTimelineView#getItems()} list, just as the same node must not be
 * placed in two {@code Tab}s or {@code TreeItem}s. Reusing one instance in two
 * positions is a usage error: the second occupant steals the node, leaving the
 * first position blank.
 *
 * <p>Identity equality is intentional ({@code equals}/{@code hashCode} are not
 * overridden) — two distinct items are never "equal".
 */
public class RXTimelineItem {

    // ==================== Constructors ====================

    /**
     * Creates an empty timeline item.
     */
    public RXTimelineItem() {
    }

    /**
     * Creates a timeline item with the given title.
     *
     * @param title the item title
     */
    public RXTimelineItem(String title) {
        setTitle(title);
    }

    /**
     * Creates a timeline item with the given title and timestamp text.
     *
     * @param title         the item title
     * @param timestampText the timestamp display text
     */
    public RXTimelineItem(String title, String timestampText) {
        setTitle(title);
        setTimestampText(timestampText);
    }

    // ==================== Title ====================

    private final StringProperty title = new SimpleStringProperty(this, "title", "");

    /**
     * The primary (emphasized) content line, rendered when {@code content} is
     * {@code null}.
     *
     * @return the title property
     */
    public final StringProperty titleProperty() {
        return title;
    }

    /**
     * Returns the title.
     *
     * @return the title
     */
    public final String getTitle() {
        return title.get();
    }

    /**
     * Sets the title.
     *
     * @param value the title
     */
    public final void setTitle(String value) {
        title.set(value);
    }

    // ==================== Description ====================

    private final StringProperty description = new SimpleStringProperty(this, "description", "");

    /**
     * The secondary, wrappable body text.
     *
     * @return the description property
     */
    public final StringProperty descriptionProperty() {
        return description;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    public final String getDescription() {
        return description.get();
    }

    /**
     * Sets the description.
     *
     * @param value the description
     */
    public final void setDescription(String value) {
        description.set(value);
    }

    // ==================== Timestamp Text ====================

    private final StringProperty timestampText = new SimpleStringProperty(this, "timestampText", "");

    /**
     * The timestamp display text. This is a plain string; formatting a date or
     * time into it is the caller's responsibility (the view never sorts by it).
     *
     * @return the timestamp text property
     */
    public final StringProperty timestampTextProperty() {
        return timestampText;
    }

    /**
     * Returns the timestamp display text.
     *
     * @return the timestamp text
     */
    public final String getTimestampText() {
        return timestampText.get();
    }

    /**
     * Sets the timestamp display text.
     *
     * @param value the timestamp text
     */
    public final void setTimestampText(String value) {
        timestampText.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content", null);

    /**
     * A custom content node that wholly replaces the title, description, and
     * timestamp when set. This is the escape hatch for rich per-item content.
     *
     * <p>Subject to the single-occupancy contract described in the class
     * documentation.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the custom content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the custom content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Dot Graphic ====================

    private final ObjectProperty<Node> dotGraphic = new SimpleObjectProperty<>(this, "dotGraphic", null);

    /**
     * An icon node centered inside the dot (for example a check mark or a
     * {@code ProgressIndicator} for a loading row). Intended for graphics no
     * larger than the dot.
     *
     * <p>Subject to the single-occupancy contract described in the class
     * documentation.
     *
     * @return the dot graphic property
     */
    public final ObjectProperty<Node> dotGraphicProperty() {
        return dotGraphic;
    }

    /**
     * Returns the dot graphic node.
     *
     * @return the dot graphic node, or {@code null}
     */
    public final Node getDotGraphic() {
        return dotGraphic.get();
    }

    /**
     * Sets the dot graphic node.
     *
     * @param value the dot graphic node, or {@code null}
     */
    public final void setDotGraphic(Node value) {
        dotGraphic.set(value);
    }

    // ==================== Type ====================

    private final ObjectProperty<TimelineItemType> type =
            new SimpleObjectProperty<>(this, "type", null);

    /**
     * The semantic color level, mapped by the skin to a pseudo-class on the
     * item. {@code null} means no semantic level.
     *
     * @return the type property
     */
    public final ObjectProperty<TimelineItemType> typeProperty() {
        return type;
    }

    /**
     * Returns the semantic color level.
     *
     * @return the type, or {@code null}
     */
    public final TimelineItemType getType() {
        return type.get();
    }

    /**
     * Sets the semantic color level.
     *
     * @param value the type, or {@code null}
     */
    public final void setType(TimelineItemType value) {
        type.set(value);
    }

    // ==================== Dot Fill ====================

    private final ObjectProperty<Color> dotFill = new SimpleObjectProperty<>(this, "dotFill", null);

    /**
     * A per-item dot color that overrides both {@code type} and the view's
     * default dot color. {@code null} falls back to the {@code type} level or
     * the {@code -rx-dot-fill} default.
     *
     * <p>The type is {@link Color} (not {@link javafx.scene.paint.Paint}) so the
     * skin can losslessly serialize it into an inline CSS string; for a gradient
     * dot use {@link #dotGraphicProperty() dotGraphic} instead.
     *
     * @return the dot fill property
     */
    public final ObjectProperty<Color> dotFillProperty() {
        return dotFill;
    }

    /**
     * Returns the per-item dot color.
     *
     * @return the dot color, or {@code null}
     */
    public final Color getDotFill() {
        return dotFill.get();
    }

    /**
     * Sets the per-item dot color.
     *
     * @param value the dot color, or {@code null}
     */
    public final void setDotFill(Color value) {
        dotFill.set(value);
    }
}
